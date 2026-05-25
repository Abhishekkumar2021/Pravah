package io.pravah.connect.connector.protocol;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.io.*;
import java.util.*;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Component;

/** FTP file transfer connector. */
@Component
public class FtpConnector implements SourceConnector, SinkConnector {

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("ftp")
        .name("FTP")
        .description("File Transfer Protocol (FTP) for accessing files on remote servers.")
        .icon("ftp")
        .category("Protocol")
        .type(ConnectorType.PROTOCOL)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .configFields(
            List.of(
                ConfigField.builder("host")
                    .label("Host")
                    .description("FTP server hostname")
                    .type(STRING)
                    .required()
                    .group("Connection")
                    .order(1)
                    .build(),
                ConfigField.builder("port")
                    .label("Port")
                    .description("FTP server port")
                    .type(NUMBER)
                    .defaultValue(21)
                    .group("Connection")
                    .order(2)
                    .build(),
                ConfigField.builder("username")
                    .label("Username")
                    .description("FTP username")
                    .type(STRING)
                    .required()
                    .group("Authentication")
                    .order(3)
                    .build(),
                ConfigField.builder("password")
                    .label("Password")
                    .description("FTP password")
                    .type(PASSWORD)
                    .required()
                    .group("Authentication")
                    .order(4)
                    .build(),
                ConfigField.builder("path")
                    .label("Path")
                    .description("Remote directory path")
                    .type(STRING)
                    .defaultValue("/")
                    .group("Data")
                    .order(5)
                    .build(),
                ConfigField.builder("passive")
                    .label("Passive Mode")
                    .description("Use passive FTP mode")
                    .type(BOOLEAN)
                    .defaultValue(true)
                    .group("Advanced")
                    .order(6)
                    .build()))
        .capabilities(
            Map.of(
                "discover", true,
                "incremental", false,
                "fullRefresh", true))
        .tags(List.of("protocol", "ftp", "file-transfer"))
        .build();
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new LinkedHashMap<>();

    if (isBlank(config.get("host"))) {
      errors.put("host", "Host is required");
    }
    if (isBlank(config.get("username"))) {
      errors.put("username", "Username is required");
    }
    if (isBlank(config.get("password"))) {
      errors.put("password", "Password is required");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    FTPClient ftp = new FTPClient();

    try {
      String host = getString(config, "host");
      int port = getInt(config, "port", 21);
      String username = getString(config, "username");
      String password = getString(config, "password");
      boolean passive = getBoolean(config, "passive", true);

      ftp.connect(host, port);
      if (!ftp.login(username, password)) {
        return TestResult.failure("Login failed: " + ftp.getReplyString());
      }

      if (passive) {
        ftp.enterLocalPassiveMode();
      }

      ftp.setFileType(FTP.BINARY_FILE_TYPE);

      long latency = System.currentTimeMillis() - start;
      return TestResult.success(
          "Connected to FTP server: " + host, latency, Map.of("serverType", ftp.getSystemType()));

    } catch (Exception e) {
      return TestResult.failure("Connection failed", e);
    } finally {
      try {
        if (ftp.isConnected()) {
          ftp.logout();
          ftp.disconnect();
        }
      } catch (IOException ignored) {
      }
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();
    FTPClient ftp = createClient(config);

    try {
      String path = getString(config, "path");
      if (path == null || path.isEmpty()) {
        path = "/";
      }

      FTPFile[] files = ftp.listFiles(path);
      for (FTPFile file : files) {
        streams.add(
            new StreamInfo(
                path + "/" + file.getName(),
                path,
                List.of(),
                List.of(),
                Map.of(
                    "type", file.isDirectory() ? "directory" : "file",
                    "size", file.getSize(),
                    "lastModified", file.getTimestamp().getTime().toString())));
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to list FTP directory", e);
    } finally {
      disconnect(ftp);
    }

    return streams;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    FTPClient ftp = createClient(config);

    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      if (!ftp.retrieveFile(streamName, outputStream)) {
        throw new RuntimeException("Failed to retrieve file: " + ftp.getReplyString());
      }

      String content = outputStream.toString();
      String[] lines = content.split("\n");

      return new FtpRecordIterator(ftp, lines);

    } catch (IOException e) {
      disconnect(ftp);
      throw new RuntimeException("Failed to read from FTP", e);
    }
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String streamName, List<Record> records, WriteOptions options) {
    if (records.isEmpty()) {
      return new WriteResult(0, 0, 0, Map.of());
    }

    FTPClient ftp = createClient(config);
    long start = System.currentTimeMillis();

    try {
      StringBuilder sb = new StringBuilder();
      Record first = records.get(0);
      sb.append(String.join(",", first.data().keySet())).append("\n");

      for (Record record : records) {
        sb.append(
                String.join(
                    ",",
                    record.data().values().stream()
                        .map(v -> v != null ? v.toString() : "")
                        .toList()))
            .append("\n");
      }

      byte[] data = sb.toString().getBytes();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

      if (!ftp.storeFile(streamName, inputStream)) {
        throw new RuntimeException("Failed to upload file: " + ftp.getReplyString());
      }

      long duration = System.currentTimeMillis() - start;
      return new WriteResult(records.size(), data.length, duration, Map.of());

    } catch (IOException e) {
      throw new RuntimeException("Failed to write to FTP", e);
    } finally {
      disconnect(ftp);
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // No schema for FTP
  }

  private FTPClient createClient(Map<String, Object> config) {
    FTPClient ftp = new FTPClient();

    try {
      String host = getString(config, "host");
      int port = getInt(config, "port", 21);
      String username = getString(config, "username");
      String password = getString(config, "password");
      boolean passive = getBoolean(config, "passive", true);

      ftp.connect(host, port);
      if (!ftp.login(username, password)) {
        throw new RuntimeException("Login failed: " + ftp.getReplyString());
      }

      if (passive) {
        ftp.enterLocalPassiveMode();
      }

      ftp.setFileType(FTP.BINARY_FILE_TYPE);
      return ftp;

    } catch (IOException e) {
      throw new RuntimeException("Failed to connect to FTP", e);
    }
  }

  private void disconnect(FTPClient ftp) {
    try {
      if (ftp.isConnected()) {
        ftp.logout();
        ftp.disconnect();
      }
    } catch (IOException ignored) {
    }
  }

  private static boolean isBlank(Object value) {
    return value == null || value.toString().isBlank();
  }

  private static String getString(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value != null ? value.toString() : null;
  }

  private static int getInt(Map<String, Object> config, String key, int defaultValue) {
    Object value = config.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Number n) return n.intValue();
    return Integer.parseInt(value.toString());
  }

  private static boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
    Object value = config.get(key);
    if (value == null) return defaultValue;
    if (value instanceof Boolean b) return b;
    return Boolean.parseBoolean(value.toString());
  }

  private static class FtpRecordIterator implements RecordIterator {
    private final FTPClient ftp;
    private final String[] lines;
    private final String[] headers;
    private int currentIndex = 1; // Skip header
    private long rowNum = 0;

    FtpRecordIterator(FTPClient ftp, String[] lines) {
      this.ftp = ftp;
      this.lines = lines;
      this.headers = lines.length > 0 ? lines[0].split(",") : new String[0];
    }

    @Override
    public boolean hasNext() {
      return currentIndex < lines.length;
    }

    @Override
    public Record next() {
      if (!hasNext()) throw new NoSuchElementException();

      String[] values = lines[currentIndex].split(",", -1);
      Map<String, Object> data = new LinkedHashMap<>();
      for (int i = 0; i < headers.length && i < values.length; i++) {
        data.put(headers[i].trim(), values[i].trim());
      }

      currentIndex++;
      rowNum++;

      return new Record(data, String.valueOf(rowNum), System.currentTimeMillis());
    }

    @Override
    public String getCursor() {
      return String.valueOf(rowNum);
    }

    @Override
    public void close() {
      try {
        if (ftp.isConnected()) {
          ftp.logout();
          ftp.disconnect();
        }
      } catch (IOException ignored) {
      }
    }
  }
}
