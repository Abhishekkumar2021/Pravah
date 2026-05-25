package io.pravah.connect.connector.saas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** HubSpot CRM connector for reading contacts, companies, and deals. */
@Component
public class HubSpotConnector implements SourceConnector {

  private static final String API_BASE = "https://api.hubapi.com";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("hubspot")
        .name("HubSpot")
        .description("Read CRM data from HubSpot — contacts, companies, deals")
        .icon("hubspot")
        .category("SaaS")
        .type(ConnectorType.SAAS)
        .mode(ConnectorMode.SOURCE)
        .configFields(
            List.of(
                ConfigField.builder("access_token")
                    .label("Private App Access Token")
                    .description("HubSpot private app token")
                    .type(ConfigField.FieldType.PASSWORD)
                    .required(true)
                    .group("Authentication")
                    .order(1)
                    .build()))
        .capabilities(Map.of("objects", List.of("contacts", "companies", "deals")))
        .tags(List.of("hubspot", "crm", "saas", "marketing"))
        .build();
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();
    if (config.get("access_token") == null || ((String) config.get("access_token")).isBlank()) {
      errors.put("access_token", "Access token is required");
    }
    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    try {
      HttpResponse<String> response = apiGet(config, "/crm/v3/objects/contacts?limit=1");
      if (response.statusCode() == 200) {
        return new TestResult(
            true, "Connected to HubSpot API", System.currentTimeMillis() - start, null);
      }
      return new TestResult(
          false, "API error: " + response.statusCode(), System.currentTimeMillis() - start, null);
    } catch (Exception e) {
      return new TestResult(false, e.getMessage(), System.currentTimeMillis() - start, null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    return List.of(
        stream("contacts", "id", "email", "firstname", "lastname", "createdate"),
        stream("companies", "id", "name", "domain", "createdate"),
        stream("deals", "id", "dealname", "amount", "dealstage", "closedate"));
  }

  private StreamInfo stream(String name, String... fields) {
    List<FieldInfo> fieldInfos = new ArrayList<>();
    for (String f : fields) {
      fieldInfos.add(new FieldInfo(f, "string", true, null));
    }
    return new StreamInfo(name, null, fieldInfos, List.of("id"), Map.of());
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String objectType, ReadOptions options) {
    return new HubSpotRecordIterator(config, objectType, options);
  }

  private HttpResponse<String> apiGet(Map<String, Object> config, String path) throws Exception {
    String token = (String) config.get("access_token");
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(API_BASE + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private class HubSpotRecordIterator implements RecordIterator {
    private final Queue<Record> buffer = new LinkedList<>();
    private String after;
    private boolean hasMore = true;
    private boolean closed = false;
    private final Map<String, Object> config;
    private final String objectType;

    HubSpotRecordIterator(Map<String, Object> config, String objectType, ReadOptions options) {
      this.config = config;
      this.objectType = objectType;
      this.after = options != null ? options.cursor() : null;
      fetchPage();
    }

    private void fetchPage() {
      if (!hasMore || closed) return;
      try {
        String path =
            "/crm/v3/objects/"
                + objectType
                + "?limit=100"
                + (after != null ? "&after=" + after : "");
        HttpResponse<String> response = apiGet(config, path);
        if (response.statusCode() != 200) {
          hasMore = false;
          return;
        }
        JsonNode json = objectMapper.readTree(response.body());
        for (JsonNode result : json.path("results")) {
          Map<String, Object> data = new HashMap<>();
          data.put("id", result.path("id").asText());
          JsonNode props = result.path("properties");
          props.fields().forEachRemaining(e -> data.put(e.getKey(), e.getValue().asText()));
          buffer.add(new Record(data, result.path("id").asText(), System.currentTimeMillis()));
        }
        if (json.has("paging") && json.path("paging").has("next")) {
          after = json.path("paging").path("next").path("after").asText();
        } else {
          hasMore = false;
        }
      } catch (Exception e) {
        hasMore = false;
        throw new RuntimeException("HubSpot fetch failed: " + e.getMessage(), e);
      }
    }

    @Override
    public boolean hasNext() {
      if (closed) return false;
      if (buffer.isEmpty() && hasMore) fetchPage();
      return !buffer.isEmpty();
    }

    @Override
    public Record next() {
      if (!hasNext()) throw new NoSuchElementException();
      return buffer.poll();
    }

    @Override
    public String getCursor() {
      return after;
    }

    @Override
    public void close() {
      closed = true;
      buffer.clear();
    }
  }
}
