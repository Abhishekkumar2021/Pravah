package io.pravah.connect.connector.protocol;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import com.jcraft.jsch.*;
import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.io.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** SFTP (SSH File Transfer Protocol) connector. */
@Component
public class SftpConnector implements SourceConnector, SinkConnector {

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("sftp")
        .name("SFTP")
        .description("Secure File Transfer Protocol (SFTP) over SSH.")
        .icon("sftp")
        .category("Protocol")
        .type(ConnectorType.PROTOCOL)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .configFields(
            List.of(
                ConfigField.builder("host")
                    .label("Host")
                    .description("SFTP server hostname")
                    .type(STRING)
                    .required()
                    .group("Connection")
                    .order(1)
                    .build(),
                ConfigField.builder("port")
                    .label("Port")
                    .description("SFTP server port")
                    .type(NUMBER)
                    .defaultValue(22)
                    .group("Connection")
                    .order(2)
                    .build(),
                ConfigField.builder("username")
                    .label("Username")
                    .description("SSH username")
                    .type(STRING)
                    .required()
                    .group("Authentication")
                    .order(3)
                    .build(),
                ConfigField.builder("authMethod")
                    .label("Authentication Method")
                    .description("How to authenticate")
                    .type(SELECT)
                    .defaultValue("password")
                    .options(List.of("password", "privateKey"))
                    .group("Authentication")
                    .order(4)
                    .build(),
                ConfigField.builder("password")
                    .label("Password")
                    .description("SSH password")
                    .type(PASSWORD)
                    .group("Authentication")
                    .order(5)
                    .dependsOn("authMethod", "password")
                    .build(),
                ConfigField.builder("privateKey")
                    .label("Private Key")
                    .description("SSH private key (PEM format)")
                    .type(TEXTAREA)
                    .group("Authentication")
                    .order(6)
                    .dependsOn("authMethod", "privateKey")
                    .build(),
                ConfigField.builder("passphrase")
                    .label("Passphrase")
                    .description("Private key passphrase")
                    .type(PASSWORD)
                    .group("Authentication")
                    .order(7)
                    .dependsOn("authMethod", "privateKey")
                    .build(),
                ConfigField.builder("path")
                    .label("Path")
                    .description("Remote directory path")
                    .type(STRING)
                    .defaultValue("/")
                    .group("Data")
                    .order(8)
                    .build()))
        .capabilities(
            Map.of(
                "discover", true,
                "incremental", false,
                "fullRefresh", true))
        .tags(List.of("protocol", "sftp", "ssh", "secure", "file-transfer"))
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

    String authMethod = getString(config, "authMethod");
    if ("password".equals(authMethod) && isBlank(config.get("password"))) {
      errors.put("password", "Password is required for password authentication");
    }
    if ("privateKey".equals(authMethod) && isBlank(config.get("privateKey"))) {
      errors.put("privateKey", "Private key is required for key authentication");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    Session session = null;
    ChannelSftp channel = null;

    try {
      session = createSession(config);
      session.connect(10000);

      channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect(10000);

      String pwd = channel.pwd();

      long latency = System.currentTimeMillis() - start;
      return TestResult.success(
          "Connected to SFTP server, current directory: " + pwd, latency, Map.of("pwd", pwd));

    } catch (Exception e) {
      return TestResult.failure("Connection failed", e);
    } finally {
      if (channel != null) channel.disconnect();
      if (session != null) session.disconnect();
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();
    Session session = null;
    ChannelSftp channel = null;

    try {
      session = createSession(config);
      session.connect(10000);

      channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect(10000);

      String path = getString(config, "path");
      if (path == null || path.isEmpty()) {
        path = "/";
      }

      @SuppressWarnings("unchecked")
      Vector<ChannelSftp.LsEntry> files = channel.ls(path);

      for (ChannelSftp.LsEntry entry : files) {
        if (".".equals(entry.getFilename()) || "..".equals(entry.getFilename())) {
          continue;
        }

        SftpATTRS attrs = entry.getAttrs();
        streams.add(
            new StreamInfo(
                path + "/" + entry.getFilename(),
                path,
                List.of(),
                List.of(),
                Map.of(
                    "type", attrs.isDir() ? "directory" : "file",
                    "size", attrs.getSize(),
                    "permissions", attrs.getPermissionsString())));
      }

    } catch (Exception e) {
      throw new RuntimeException("Failed to list SFTP directory", e);
    } finally {
      if (channel != null) channel.disconnect();
      if (session != null) session.disconnect();
    }

    return streams;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    try {
      Session session = createSession(config);
      session.connect(10000);

      ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect(10000);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      channel.get(streamName, outputStream);

      String content = outputStream.toString();
      String[] lines = content.split("\n");

      return new SftpRecordIterator(session, channel, lines);

    } catch (Exception e) {
      throw new RuntimeException("Failed to read from SFTP", e);
    }
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String streamName, List<Record> records, WriteOptions options) {
    if (records.isEmpty()) {
      return new WriteResult(0, 0, 0, Map.of());
    }

    Session session = null;
    ChannelSftp channel = null;
    long start = System.currentTimeMillis();

    try {
      session = createSession(config);
      session.connect(10000);

      channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect(10000);

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
      channel.put(inputStream, streamName);

      long duration = System.currentTimeMillis() - start;
      return new WriteResult(records.size(), data.length, duration, Map.of());

    } catch (Exception e) {
      throw new RuntimeException("Failed to write to SFTP", e);
    } finally {
      if (channel != null) channel.disconnect();
      if (session != null) session.disconnect();
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // No schema for SFTP
  }

  private Session createSession(Map<String, Object> config) throws JSchException {
    String host = getString(config, "host");
    int port = getInt(config, "port", 22);
    String username = getString(config, "username");
    String authMethod = getString(config, "authMethod");

    JSch jsch = new JSch();
    Session session = jsch.getSession(username, host, port);

    Properties properties = new Properties();
    properties.put("StrictHostKeyChecking", "no");
    session.setConfig(properties);

    if ("privateKey".equals(authMethod)) {
      String privateKey = getString(config, "privateKey");
      String passphrase = getString(config, "passphrase");

      if (passphrase != null && !passphrase.isEmpty()) {
        jsch.addIdentity("key", privateKey.getBytes(), null, passphrase.getBytes());
      } else {
        jsch.addIdentity("key", privateKey.getBytes(), null, null);
      }
    } else {
      String password = getString(config, "password");
      session.setPassword(password);
    }

    return session;
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

  private static class SftpRecordIterator implements RecordIterator {
    private final Session session;
    private final ChannelSftp channel;
    private final String[] lines;
    private final String[] headers;
    private int currentIndex = 1;
    private long rowNum = 0;

    SftpRecordIterator(Session session, ChannelSftp channel, String[] lines) {
      this.session = session;
      this.channel = channel;
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
      if (channel != null) channel.disconnect();
      if (session != null) session.disconnect();
    }
  }
}
