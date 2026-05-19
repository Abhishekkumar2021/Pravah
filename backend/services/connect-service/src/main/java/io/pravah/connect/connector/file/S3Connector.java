package io.pravah.connect.connector.file;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import io.pravah.connect.domain.ConfigField;
import java.io.*;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Amazon S3 / S3-compatible storage connector. */
@Component
public class S3Connector extends AbstractFileConnector {

  @Override
  protected String getConnectorId() {
    return "s3";
  }

  @Override
  protected String getDisplayName() {
    return "Amazon S3";
  }

  @Override
  protected String getDescription() {
    return "Amazon S3 and S3-compatible object storage (MinIO, DigitalOcean Spaces, etc.).";
  }

  @Override
  protected String getIconName() {
    return "s3";
  }

  @Override
  protected List<String> getTags() {
    return List.of("file", "object-storage", "s3", "aws", "cloud");
  }

  @Override
  protected List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder("bucket")
            .label("Bucket")
            .description("S3 bucket name")
            .type(STRING)
            .required()
            .group("Connection")
            .order(1)
            .build(),
        ConfigField.builder("region")
            .label("Region")
            .description("AWS region")
            .type(SELECT)
            .required()
            .defaultValue("us-east-1")
            .options(
                List.of(
                    "us-east-1",
                    "us-east-2",
                    "us-west-1",
                    "us-west-2",
                    "eu-west-1",
                    "eu-west-2",
                    "eu-west-3",
                    "eu-central-1",
                    "ap-south-1",
                    "ap-southeast-1",
                    "ap-southeast-2",
                    "ap-northeast-1",
                    "ap-northeast-2",
                    "ap-northeast-3",
                    "sa-east-1",
                    "ca-central-1"))
            .group("Connection")
            .order(2)
            .build(),
        ConfigField.builder("accessKeyId")
            .label("Access Key ID")
            .description("AWS access key ID")
            .type(STRING)
            .required()
            .group("Authentication")
            .order(3)
            .build(),
        ConfigField.builder("secretAccessKey")
            .label("Secret Access Key")
            .description("AWS secret access key")
            .type(PASSWORD)
            .required()
            .group("Authentication")
            .order(4)
            .build(),
        ConfigField.builder("endpoint")
            .label("Custom Endpoint")
            .description("Custom S3-compatible endpoint (for MinIO, etc.)")
            .type(URL)
            .placeholder("https://s3.example.com")
            .group("Advanced")
            .order(5)
            .build(),
        ConfigField.builder("pathPrefix")
            .label("Path Prefix")
            .description("Filter files by path prefix")
            .type(STRING)
            .placeholder("data/")
            .group("Data")
            .order(6)
            .build(),
        ConfigField.builder("fileFormat")
            .label("File Format")
            .description("Format of files to read")
            .type(SELECT)
            .defaultValue("csv")
            .options(List.of("csv", "json", "jsonl", "parquet", "avro"))
            .group("Data")
            .order(7)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new LinkedHashMap<>();

    if (isBlank(config.get("bucket"))) {
      errors.put("bucket", "Bucket is required");
    }
    if (isBlank(config.get("region"))) {
      errors.put("region", "Region is required");
    }
    if (isBlank(config.get("accessKeyId"))) {
      errors.put("accessKeyId", "Access Key ID is required");
    }
    if (isBlank(config.get("secretAccessKey"))) {
      errors.put("secretAccessKey", "Secret Access Key is required");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();

    try (S3Client s3 = createClient(config)) {
      String bucket = getString(config, "bucket");

      HeadBucketResponse response =
          s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());

      long latency = System.currentTimeMillis() - start;
      return TestResult.success(
          "Connected to bucket: " + bucket, latency, Map.of("bucket", bucket));
    } catch (NoSuchBucketException e) {
      return TestResult.failure("Bucket does not exist: " + getString(config, "bucket"));
    } catch (Exception e) {
      return TestResult.failure("Connection failed", e);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();
    String bucket = getString(config, "bucket");
    String prefix = getString(config, "pathPrefix");

    try (S3Client s3 = createClient(config)) {
      ListObjectsV2Request.Builder requestBuilder =
          ListObjectsV2Request.builder().bucket(bucket).delimiter("/");

      if (prefix != null && !prefix.isEmpty()) {
        requestBuilder.prefix(prefix);
      }

      ListObjectsV2Response response = s3.listObjectsV2(requestBuilder.build());

      for (CommonPrefix commonPrefix : response.commonPrefixes()) {
        String folderName = commonPrefix.prefix();
        streams.add(
            new StreamInfo(
                folderName,
                bucket,
                List.of(), // Schema inferred at read time
                List.of(),
                Map.of("type", "folder")));
      }

      for (S3Object object : response.contents()) {
        if (!object.key().endsWith("/")) {
          streams.add(
              new StreamInfo(
                  object.key(),
                  bucket,
                  List.of(),
                  List.of(),
                  Map.of(
                      "type", "file",
                      "size", object.size(),
                      "lastModified", object.lastModified().toString())));
        }
      }
    }

    return streams;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    S3Client s3 = createClient(config);
    String bucket = getString(config, "bucket");
    String format = getString(config, "fileFormat");

    try {
      GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(streamName).build();

      InputStream inputStream = s3.getObject(request);

      return switch (format != null ? format.toLowerCase() : "csv") {
        case "json", "jsonl" -> new JsonRecordIterator(s3, inputStream);
        case "csv" -> new CsvRecordIterator(s3, inputStream);
        default -> new CsvRecordIterator(s3, inputStream);
      };

    } catch (Exception e) {
      s3.close();
      throw new RuntimeException("Failed to read from S3: " + streamName, e);
    }
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String streamName, List<Record> records, WriteOptions options) {
    if (records.isEmpty()) {
      return new WriteResult(0, 0, 0, Map.of());
    }

    long start = System.currentTimeMillis();
    String bucket = getString(config, "bucket");
    String format = getString(config, "fileFormat");

    try (S3Client s3 = createClient(config)) {
      byte[] data = serializeRecords(records, format);

      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(streamName)
              .contentType(getContentType(format))
              .build();

      s3.putObject(request, software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));

      long duration = System.currentTimeMillis() - start;
      return new WriteResult(records.size(), data.length, duration, Map.of());
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // No schema for file storage
  }

  private S3Client createClient(Map<String, Object> config) {
    String region = getString(config, "region");
    String accessKeyId = getString(config, "accessKeyId");
    String secretAccessKey = getString(config, "secretAccessKey");
    String endpoint = getString(config, "endpoint");

    var builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)));

    if (endpoint != null && !endpoint.isEmpty()) {
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
    }

    return builder.build();
  }

  private byte[] serializeRecords(List<Record> records, String format) {
    // Simple CSV serialization for now
    StringBuilder sb = new StringBuilder();

    if (!records.isEmpty()) {
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
    }

    return sb.toString().getBytes();
  }

  private String getContentType(String format) {
    return switch (format != null ? format.toLowerCase() : "csv") {
      case "json", "jsonl" -> "application/json";
      case "parquet" -> "application/octet-stream";
      case "avro" -> "application/avro";
      default -> "text/csv";
    };
  }

  private static class CsvRecordIterator implements RecordIterator {
    private final S3Client s3;
    private final BufferedReader reader;
    private final String[] headers;
    private String nextLine;
    private long rowNum = 0;

    CsvRecordIterator(S3Client s3, InputStream inputStream) throws IOException {
      this.s3 = s3;
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
      s3.close();
    }
  }

  private static class JsonRecordIterator implements RecordIterator {
    private final S3Client s3;
    private final BufferedReader reader;
    private String nextLine;
    private long rowNum = 0;

    JsonRecordIterator(S3Client s3, InputStream inputStream) throws IOException {
      this.s3 = s3;
      this.reader = new BufferedReader(new InputStreamReader(inputStream));
      this.nextLine = reader.readLine();
    }

    @Override
    public boolean hasNext() {
      return nextLine != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Record next() {
      if (nextLine == null) throw new NoSuchElementException();

      // Simple JSON parsing - in production use Jackson
      Map<String, Object> data = new LinkedHashMap<>();
      String line = nextLine.trim();
      if (line.startsWith("{") && line.endsWith("}")) {
        // Very basic JSON parsing
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
      s3.close();
    }
  }
}
