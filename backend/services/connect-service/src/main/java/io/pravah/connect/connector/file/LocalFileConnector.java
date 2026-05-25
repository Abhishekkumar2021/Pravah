package io.pravah.connect.connector.file;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import io.pravah.connect.domain.ConfigField;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Local filesystem connector for development and testing. */
@Component
public class LocalFileConnector extends AbstractFileConnector {

  @Override
  protected String getConnectorId() {
    return "local-file";
  }

  @Override
  protected String getDisplayName() {
    return "Local File System";
  }

  @Override
  protected String getDescription() {
    return "Read and write files from local filesystem. Useful for development and testing.";
  }

  @Override
  protected String getIconName() {
    return "folder";
  }

  @Override
  protected List<String> getTags() {
    return List.of("file", "local", "filesystem", "development");
  }

  @Override
  protected List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder("basePath")
            .label("Base Path")
            .description("Root directory for file operations")
            .type(STRING)
            .required()
            .placeholder("/data")
            .group("Connection")
            .order(1)
            .build(),
        ConfigField.builder("filePattern")
            .label("File Pattern")
            .description("Glob pattern to filter files (e.g., *.csv)")
            .type(STRING)
            .defaultValue("*")
            .group("Data")
            .order(2)
            .build(),
        ConfigField.builder("fileFormat")
            .label("File Format")
            .description("Format of files to read")
            .type(SELECT)
            .defaultValue("csv")
            .options(List.of("csv", "json", "jsonl"))
            .group("Data")
            .order(3)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new LinkedHashMap<>();

    String basePath = getString(config, "basePath");
    if (isBlank(basePath)) {
      errors.put("basePath", "Base path is required");
    } else {
      Path path = Paths.get(basePath);
      if (!Files.exists(path)) {
        errors.put("basePath", "Path does not exist: " + basePath);
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    String basePath = getString(config, "basePath");

    try {
      Path path = Paths.get(basePath);

      if (!Files.exists(path)) {
        return TestResult.failure("Path does not exist: " + basePath);
      }

      if (!Files.isReadable(path)) {
        return TestResult.failure("Path is not readable: " + basePath);
      }

      long fileCount = 0;
      if (Files.isDirectory(path)) {
        try (var stream = Files.list(path)) {
          fileCount = stream.count();
        }
      }

      long latency = System.currentTimeMillis() - start;
      return TestResult.success(
          "Connected to local path: " + basePath,
          latency,
          Map.of(
              "path", basePath,
              "isDirectory", Files.isDirectory(path),
              "fileCount", fileCount));

    } catch (Exception e) {
      return TestResult.failure("Access failed", e);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();
    String basePath = getString(config, "basePath");
    String pattern = getString(config, "filePattern");

    try {
      Path base = Paths.get(basePath);

      if (!Files.isDirectory(base)) {
        streams.add(fileToStreamInfo(base));
        return streams;
      }

      PathMatcher matcher =
          FileSystems.getDefault().getPathMatcher("glob:" + (pattern != null ? pattern : "*"));

      try (var stream = Files.list(base)) {
        stream
            .filter(p -> matcher.matches(p.getFileName()))
            .forEach(p -> streams.add(fileToStreamInfo(p)));
      }

    } catch (IOException e) {
      throw new RuntimeException("Failed to discover streams", e);
    }

    return streams;
  }

  private StreamInfo fileToStreamInfo(Path path) {
    try {
      return new StreamInfo(
          path.toString(),
          path.getParent() != null ? path.getParent().toString() : "",
          List.of(),
          List.of(),
          Map.of(
              "type", Files.isDirectory(path) ? "directory" : "file",
              "size", Files.isRegularFile(path) ? Files.size(path) : 0,
              "lastModified", Files.getLastModifiedTime(path).toString()));
    } catch (IOException e) {
      return new StreamInfo(path.toString(), "", List.of(), List.of(), Map.of());
    }
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    String format = getString(config, "fileFormat");
    Path path = Paths.get(streamName);

    try {
      InputStream inputStream = Files.newInputStream(path);

      return switch (format != null ? format.toLowerCase() : "csv") {
        case "json", "jsonl" -> new JsonLineIterator(inputStream);
        case "csv" -> new CsvIterator(inputStream);
        default -> new CsvIterator(inputStream);
      };

    } catch (IOException e) {
      throw new RuntimeException("Failed to read file: " + streamName, e);
    }
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String streamName, List<Record> records, WriteOptions options) {
    if (records.isEmpty()) {
      return new WriteResult(0, 0, 0, Map.of());
    }

    long start = System.currentTimeMillis();
    Path path = Paths.get(streamName);

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
      Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

      long duration = System.currentTimeMillis() - start;
      return new WriteResult(records.size(), data.length, duration, Map.of());

    } catch (IOException e) {
      throw new RuntimeException("Failed to write file: " + streamName, e);
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // No schema for files
  }

  private static class CsvIterator implements RecordIterator {
    private final BufferedReader reader;
    private final String[] headers;
    private String nextLine;
    private long rowNum = 0;

    CsvIterator(InputStream inputStream) throws IOException {
      this.reader = new BufferedReader(new InputStreamReader(inputStream));
      String headerLine = reader.readLine();
      this.headers = headerLine != null ? headerLine.split(",") : new String[0];
      this.nextLine = reader.readLine();
    }

    @Override
    public boolean hasNext() {
      return nextLine != null;
    }

    @Override
    public Record next() {
      if (nextLine == null) throw new NoSuchElementException();

      String[] values = nextLine.split(",", -1);
      Map<String, Object> data = new LinkedHashMap<>();
      for (int i = 0; i < headers.length && i < values.length; i++) {
        data.put(headers[i].trim(), values[i].trim());
      }

      rowNum++;
      try {
        nextLine = reader.readLine();
      } catch (IOException e) {
        nextLine = null;
      }

      return new Record(data, String.valueOf(rowNum), System.currentTimeMillis());
    }

    @Override
    public String getCursor() {
      return String.valueOf(rowNum);
    }

    @Override
    public void close() {
      try {
        reader.close();
      } catch (IOException ignored) {
      }
    }
  }

  private static class JsonLineIterator implements RecordIterator {
    private final BufferedReader reader;
    private String nextLine;
    private long rowNum = 0;

    JsonLineIterator(InputStream inputStream) throws IOException {
      this.reader = new BufferedReader(new InputStreamReader(inputStream));
      this.nextLine = reader.readLine();
    }

    @Override
    public boolean hasNext() {
      return nextLine != null;
    }

    @Override
    public Record next() {
      if (nextLine == null) throw new NoSuchElementException();

      Map<String, Object> data = new LinkedHashMap<>();
      String line = nextLine.trim();
      if (line.startsWith("{") && line.endsWith("}")) {
        line = line.substring(1, line.length() - 1);
        for (String pair : line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
          String[] kv = pair.split(":", 2);
          if (kv.length == 2) {
            String key = kv[0].trim().replaceAll("^\"|\"$", "");
            String value = kv[1].trim().replaceAll("^\"|\"$", "");
            data.put(key, value);
          }
        }
      }

      rowNum++;
      try {
        nextLine = reader.readLine();
      } catch (IOException e) {
        nextLine = null;
      }

      return new Record(data, String.valueOf(rowNum), System.currentTimeMillis());
    }

    @Override
    public String getCursor() {
      return String.valueOf(rowNum);
    }

    @Override
    public void close() {
      try {
        reader.close();
      } catch (IOException ignored) {
      }
    }
  }
}
