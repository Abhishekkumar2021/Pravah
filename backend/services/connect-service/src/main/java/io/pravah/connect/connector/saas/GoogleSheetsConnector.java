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

/**
 * Google Sheets connector for reading from and writing to Google Spreadsheets.
 *
 * <p>Requires a Google Cloud service account with Sheets API access.
 */
@Component
public class GoogleSheetsConnector implements SourceConnector, SinkConnector {

  private static final String SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder()
        .id("google-sheets")
        .name("Google Sheets")
        .description("Read and write data from Google Spreadsheets")
        .icon("google-sheets")
        .category("SaaS")
        .type(ConnectorType.SAAS)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .version("1.0.0")
        .configFields(getConfigFields())
        .capabilities(
            Map.of(
                "batch_read", true,
                "batch_write", true,
                "append", true,
                "sheets", true))
        .tags(List.of("google", "sheets", "spreadsheet", "saas", "cloud"))
        .build();
  }

  private List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder()
            .name("credentials_json")
            .label("Service Account Credentials")
            .description("Google Cloud service account JSON key")
            .type(ConfigField.FieldType.TEXTAREA)
            .required(true)
            .placeholder("{\"type\": \"service_account\", ...}")
            .group("Authentication")
            .order(1)
            .build(),
        ConfigField.builder()
            .name("spreadsheet_id")
            .label("Spreadsheet ID")
            .description("The ID from the spreadsheet URL")
            .type(ConfigField.FieldType.STRING)
            .required(true)
            .placeholder("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms")
            .group("Spreadsheet")
            .order(2)
            .build(),
        ConfigField.builder()
            .name("sheet_name")
            .label("Sheet Name")
            .description("Name of the sheet tab (default: first sheet)")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .defaultValue("Sheet1")
            .group("Spreadsheet")
            .order(3)
            .build(),
        ConfigField.builder()
            .name("range")
            .label("Range")
            .description("Cell range in A1 notation (e.g., A1:D100)")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .placeholder("A1:Z1000")
            .group("Spreadsheet")
            .order(4)
            .build(),
        ConfigField.builder()
            .name("header_row")
            .label("First Row is Header")
            .description("Treat first row as column headers")
            .type(ConfigField.FieldType.BOOLEAN)
            .required(false)
            .defaultValue(true)
            .group("Options")
            .order(5)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();

    String credentials = (String) config.get("credentials_json");
    if (credentials == null || credentials.isBlank()) {
      errors.put("credentials_json", "Service account credentials are required");
    } else {
      try {
        JsonNode json = objectMapper.readTree(credentials);
        if (!json.has("type") || !"service_account".equals(json.get("type").asText())) {
          errors.put("credentials_json", "Invalid service account JSON format");
        }
      } catch (Exception e) {
        errors.put("credentials_json", "Invalid JSON format");
      }
    }

    String spreadsheetId = (String) config.get("spreadsheet_id");
    if (spreadsheetId == null || spreadsheetId.isBlank()) {
      errors.put("spreadsheet_id", "Spreadsheet ID is required");
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();

    try {
      String accessToken = getAccessToken(config);
      String spreadsheetId = (String) config.get("spreadsheet_id");

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(SHEETS_API_BASE + "/" + spreadsheetId + "?fields=properties.title"))
              .header("Authorization", "Bearer " + accessToken)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        String title = json.path("properties").path("title").asText("Unknown");
        return new TestResult(
            true,
            "Connected to spreadsheet: " + title,
            System.currentTimeMillis() - start,
            Map.of("spreadsheet_title", title));
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
      String accessToken = getAccessToken(config);
      String spreadsheetId = (String) config.get("spreadsheet_id");

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(SHEETS_API_BASE + "/" + spreadsheetId + "?fields=sheets.properties"))
              .header("Authorization", "Bearer " + accessToken)
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode sheets = json.path("sheets");

        for (JsonNode sheet : sheets) {
          String sheetTitle = sheet.path("properties").path("title").asText();
          int sheetId = sheet.path("properties").path("sheetId").asInt();
          int rowCount = sheet.path("properties").path("gridProperties").path("rowCount").asInt();
          int colCount =
              sheet.path("properties").path("gridProperties").path("columnCount").asInt();

          streams.add(
              new StreamInfo(
                  sheetTitle,
                  null,
                  List.of(),
                  List.of(),
                  Map.of(
                      "sheet_id", sheetId,
                      "row_count", rowCount,
                      "column_count", colCount)));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to discover sheets: " + e.getMessage(), e);
    }

    return streams;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String sheetName, ReadOptions options) {
    return new SheetsRecordIterator(config, sheetName, options);
  }

  @Override
  public WriteResult write(
      Map<String, Object> config,
      String sheetName,
      List<Record> records,
      WriteOptions writeOptions) {
    long start = System.currentTimeMillis();

    try {
      String accessToken = getAccessToken(config);
      String spreadsheetId = (String) config.get("spreadsheet_id");
      String range = sheetName + "!A1";

      List<List<Object>> values = new ArrayList<>();

      if (!records.isEmpty()) {
        List<String> headers = new ArrayList<>(records.get(0).data().keySet());
        values.add(new ArrayList<>(headers));

        for (Record record : records) {
          List<Object> row = new ArrayList<>();
          for (String header : headers) {
            row.add(record.data().getOrDefault(header, ""));
          }
          values.add(row);
        }
      }

      Map<String, Object> body = Map.of("values", values);
      String jsonBody = objectMapper.writeValueAsString(body);

      String url =
          SHEETS_API_BASE
              + "/"
              + spreadsheetId
              + "/values/"
              + range
              + "?valueInputOption=USER_ENTERED";

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Authorization", "Bearer " + accessToken)
              .header("Content-Type", "application/json")
              .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        int updatedRows = json.path("updatedRows").asInt();
        return new WriteResult(
            updatedRows, 0, System.currentTimeMillis() - start, Map.of("range", range));
      } else {
        throw new RuntimeException("Write failed: " + response.body());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write to Google Sheets: " + e.getMessage(), e);
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // Google Sheets doesn't require schema pre-creation
  }

  private String getAccessToken(Map<String, Object> config) {
    // In production, use Google Auth Library for proper JWT-based auth
    // This is a simplified placeholder
    String credentials = (String) config.get("credentials_json");
    try {
      JsonNode json = objectMapper.readTree(credentials);
      // For actual implementation, use GoogleCredentials.fromStream()
      // This returns a placeholder for demonstration
      return "placeholder_access_token";
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse credentials", e);
    }
  }

  private class SheetsRecordIterator implements RecordIterator {
    private final List<Record> records = new ArrayList<>();
    private int index = 0;
    private boolean closed = false;

    SheetsRecordIterator(Map<String, Object> config, String sheetName, ReadOptions options) {
      try {
        String accessToken = getAccessToken(config);
        String spreadsheetId = (String) config.get("spreadsheet_id");
        String range = (String) config.getOrDefault("range", sheetName);
        Boolean headerRow = (Boolean) config.getOrDefault("header_row", true);

        String url = SHEETS_API_BASE + "/" + spreadsheetId + "/values/" + range;

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
          JsonNode json = objectMapper.readTree(response.body());
          JsonNode values = json.path("values");

          List<String> headers = new ArrayList<>();
          boolean firstRow = true;

          for (JsonNode row : values) {
            if (firstRow && Boolean.TRUE.equals(headerRow)) {
              for (JsonNode cell : row) {
                headers.add(cell.asText());
              }
              firstRow = false;
              continue;
            }

            if (headers.isEmpty()) {
              for (int i = 0; i < row.size(); i++) {
                headers.add("column_" + (i + 1));
              }
            }

            Map<String, Object> data = new HashMap<>();
            for (int i = 0; i < Math.min(headers.size(), row.size()); i++) {
              data.put(headers.get(i), row.get(i).asText());
            }

            records.add(
                new Record(data, String.valueOf(records.size()), System.currentTimeMillis()));
            firstRow = false;
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to read from Google Sheets: " + e.getMessage(), e);
      }
    }

    @Override
    public boolean hasNext() {
      return !closed && index < records.size();
    }

    @Override
    public Record next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return records.get(index++);
    }

    @Override
    public String getCursor() {
      return String.valueOf(index);
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
