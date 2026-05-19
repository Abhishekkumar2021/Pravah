package io.pravah.connect.connector.saas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Airtable connector for reading and writing data to Airtable bases and tables. */
@Component
public class AirtableConnector implements SourceConnector, SinkConnector {

  private static final String AIRTABLE_API_BASE = "https://api.airtable.com/v0";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder()
        .id("airtable")
        .name("Airtable")
        .description("Read and write data from Airtable bases and tables")
        .icon("airtable")
        .category("SaaS")
        .type(ConnectorType.SAAS)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .version("1.0.0")
        .configFields(getConfigFields())
        .capabilities(
            Map.of(
                "pagination", true,
                "filtering", true,
                "sorting", true,
                "attachments", true))
        .tags(List.of("airtable", "database", "spreadsheet", "saas", "no-code"))
        .build();
  }

  private List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder()
            .name("api_key")
            .label("Personal Access Token")
            .description("Airtable Personal Access Token or API Key")
            .type(ConfigField.FieldType.PASSWORD)
            .required(true)
            .placeholder("pat...")
            .group("Authentication")
            .order(1)
            .build(),
        ConfigField.builder()
            .name("base_id")
            .label("Base ID")
            .description("Airtable Base ID (starts with 'app')")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .placeholder("appXXXXXXXXXXXXXX")
            .group("Base")
            .order(2)
            .build(),
        ConfigField.builder()
            .name("table_name")
            .label("Table Name")
            .description("Name or ID of the table")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .group("Base")
            .order(3)
            .build(),
        ConfigField.builder()
            .name("view")
            .label("View")
            .description("Optional view to filter records")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .group("Options")
            .order(4)
            .build(),
        ConfigField.builder()
            .name("formula")
            .label("Filter Formula")
            .description("Airtable formula to filter records")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .placeholder("AND({Status}='Active', {Created}>TODAY()-30)")
            .group("Options")
            .order(5)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();

    String apiKey = (String) config.get("api_key");
    if (apiKey == null || apiKey.isBlank()) {
      errors.put("api_key", "API key/token is required");
    }

    String baseId = (String) config.get("base_id");
    if (baseId == null || baseId.isBlank()) {
      errors.put("base_id", "Base ID is required");
    } else if (!baseId.startsWith("app")) {
      errors.put("base_id", "Base ID should start with 'app'");
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();

    try {
      String apiKey = (String) config.get("api_key");
      String baseId = (String) config.get("base_id");

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://api.airtable.com/v0/meta/bases/" + baseId + "/tables"))
              .header("Authorization", "Bearer " + apiKey)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        int tableCount = json.path("tables").size();
        return new TestResult(
            true,
            String.format("Connected. Found %d tables.", tableCount),
            System.currentTimeMillis() - start,
            Map.of("table_count", tableCount));
      } else {
        return new TestResult(
            false, "API error: " + response.statusCode(), System.currentTimeMillis() - start, null);
      }
    } catch (Exception e) {
      return new TestResult(
          false, "Connection failed: " + e.getMessage(), System.currentTimeMillis() - start, null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    List<StreamInfo> streams = new ArrayList<>();

    try {
      String apiKey = (String) config.get("api_key");
      String baseId = (String) config.get("base_id");

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("https://api.airtable.com/v0/meta/bases/" + baseId + "/tables"))
              .header("Authorization", "Bearer " + apiKey)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode tables = json.path("tables");

        for (JsonNode table : tables) {
          String tableName = table.path("name").asText();
          String tableId = table.path("id").asText();

          List<FieldInfo> fields = new ArrayList<>();
          fields.add(new FieldInfo("id", "string", false, "Airtable record ID"));

          JsonNode fieldsNode = table.path("fields");
          for (JsonNode field : fieldsNode) {
            String fieldName = field.path("name").asText();
            String fieldType = field.path("type").asText();
            fields.add(new FieldInfo(fieldName, mapAirtableType(fieldType), true, null));
          }

          streams.add(
              new StreamInfo(tableName, null, fields, List.of("id"), Map.of("table_id", tableId)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to discover tables: " + e.getMessage(), e);
    }

    return streams;
  }

  private String mapAirtableType(String airtableType) {
    return switch (airtableType) {
      case "number", "currency", "percent", "duration", "rating" -> "number";
      case "checkbox" -> "boolean";
      case "date", "dateTime", "createdTime", "lastModifiedTime" -> "datetime";
      case "multipleAttachments" -> "array";
      case "multipleSelects", "multipleRecordLinks" -> "array";
      default -> "string";
    };
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String tableName, ReadOptions options) {
    return new AirtableRecordIterator(config, tableName, options);
  }

  @Override
  public WriteResult write(
      Map<String, Object> config,
      String tableName,
      List<Record> records,
      WriteOptions writeOptions) {
    long start = System.currentTimeMillis();
    int written = 0;

    try {
      String apiKey = (String) config.get("api_key");
      String baseId = (String) config.get("base_id");

      // Airtable allows max 10 records per request
      List<List<Record>> batches = partition(records, 10);

      for (List<Record> batch : batches) {
        List<Map<String, Object>> airtableRecords = new ArrayList<>();
        for (Record record : batch) {
          Map<String, Object> fields = new HashMap<>(record.data());
          fields.remove("id");
          airtableRecords.add(Map.of("fields", fields));
        }

        String body = objectMapper.writeValueAsString(Map.of("records", airtableRecords));

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        AIRTABLE_API_BASE + "/" + baseId + "/" + tableName.replace(" ", "%20")))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
          JsonNode json = objectMapper.readTree(response.body());
          written += json.path("records").size();
        } else {
          throw new RuntimeException("Write failed: " + response.body());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write to Airtable: " + e.getMessage(), e);
    }

    return new WriteResult(written, 0, System.currentTimeMillis() - start, Map.of());
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // Airtable tables must be created manually or via Metadata API
  }

  private <T> List<List<T>> partition(List<T> list, int size) {
    List<List<T>> result = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      result.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return result;
  }

  private class AirtableRecordIterator implements RecordIterator {
    private final String apiKey;
    private final String baseId;
    private final String tableName;
    private final String view;
    private final String formula;
    private final Queue<Record> buffer = new LinkedList<>();
    private String offset;
    private boolean hasMore = true;
    private boolean closed = false;

    AirtableRecordIterator(Map<String, Object> config, String tableName, ReadOptions options) {
      this.apiKey = (String) config.get("api_key");
      this.baseId = (String) config.get("base_id");
      this.tableName = tableName;
      this.view = (String) config.get("view");
      this.formula = (String) config.get("formula");

      fetchPage();
    }

    private void fetchPage() {
      if (!hasMore || closed) return;

      try {
        StringBuilder url =
            new StringBuilder(AIRTABLE_API_BASE)
                .append("/")
                .append(baseId)
                .append("/")
                .append(tableName.replace(" ", "%20"));

        List<String> params = new ArrayList<>();
        if (offset != null) {
          params.add("offset=" + offset);
        }
        if (view != null && !view.isBlank()) {
          params.add("view=" + view.replace(" ", "%20"));
        }
        if (formula != null && !formula.isBlank()) {
          params.add("filterByFormula=" + java.net.URLEncoder.encode(formula, "UTF-8"));
        }

        if (!params.isEmpty()) {
          url.append("?").append(String.join("&", params));
        }

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
          JsonNode json = objectMapper.readTree(response.body());
          offset = json.has("offset") ? json.path("offset").asText() : null;
          hasMore = offset != null;

          JsonNode records = json.path("records");
          for (JsonNode record : records) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", record.path("id").asText());

            JsonNode fields = record.path("fields");
            Iterator<Map.Entry<String, JsonNode>> fieldIter = fields.fields();
            while (fieldIter.hasNext()) {
              Map.Entry<String, JsonNode> field = fieldIter.next();
              data.put(field.getKey(), nodeToValue(field.getValue()));
            }

            String createdTime = record.path("createdTime").asText();
            long timestamp = System.currentTimeMillis();
            try {
              timestamp = java.time.Instant.parse(createdTime).toEpochMilli();
            } catch (Exception ignored) {
              // Use current time
            }

            buffer.add(new Record(data, record.path("id").asText(), timestamp));
          }
        } else {
          hasMore = false;
        }
      } catch (Exception e) {
        hasMore = false;
        throw new RuntimeException("Failed to fetch from Airtable: " + e.getMessage(), e);
      }
    }

    private Object nodeToValue(JsonNode node) {
      if (node.isNull()) return null;
      if (node.isBoolean()) return node.asBoolean();
      if (node.isNumber()) return node.numberValue();
      if (node.isTextual()) return node.asText();
      if (node.isArray()) {
        List<Object> list = new ArrayList<>();
        for (JsonNode item : node) {
          list.add(nodeToValue(item));
        }
        return list;
      }
      if (node.isObject()) return node.toString();
      return node.asText();
    }

    @Override
    public boolean hasNext() {
      if (closed) return false;
      if (buffer.isEmpty() && hasMore) {
        fetchPage();
      }
      return !buffer.isEmpty();
    }

    @Override
    public Record next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return buffer.poll();
    }

    @Override
    public String getCursor() {
      return offset;
    }

    @Override
    public void close() {
      closed = true;
      buffer.clear();
    }
  }
}
