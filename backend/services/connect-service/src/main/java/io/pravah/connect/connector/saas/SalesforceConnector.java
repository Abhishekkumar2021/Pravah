package io.pravah.connect.connector.saas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Salesforce CRM connector (REST API v59). */
@Component
public class SalesforceConnector implements SourceConnector {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("salesforce")
        .name("Salesforce")
        .description("Read CRM objects from Salesforce via REST API")
        .icon("salesforce")
        .category("SaaS")
        .type(ConnectorType.SAAS)
        .mode(ConnectorMode.SOURCE)
        .configFields(
            List.of(
                ConfigField.builder("instance_url")
                    .label("Instance URL")
                    .description("e.g. https://yourcompany.my.salesforce.com")
                    .type(ConfigField.FieldType.URL)
                    .required(true)
                    .group("Connection")
                    .order(1)
                    .build(),
                ConfigField.builder("access_token")
                    .label("OAuth Access Token")
                    .description("Bearer token with API access")
                    .type(ConfigField.FieldType.PASSWORD)
                    .required(true)
                    .group("Authentication")
                    .order(2)
                    .build(),
                ConfigField.builder("api_version")
                    .label("API Version")
                    .description("Salesforce REST API version")
                    .type(ConfigField.FieldType.STRING)
                    .defaultValue("v59.0")
                    .group("Connection")
                    .order(3)
                    .build()))
        .capabilities(Map.of("objects", List.of("Account", "Contact", "Lead", "Opportunity")))
        .tags(List.of("salesforce", "crm", "saas"))
        .build();
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();
    if (blank(config.get("instance_url"))) {
      errors.put("instance_url", "Instance URL is required");
    }
    if (blank(config.get("access_token"))) {
      errors.put("access_token", "Access token is required");
    }
    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    try {
      HttpResponse<String> response =
          apiGet(config, "/services/data/" + apiVersion(config) + "/sobjects");
      boolean ok = response.statusCode() == 200;
      return new TestResult(
          ok,
          ok ? "Connected to Salesforce" : "API error: " + response.statusCode(),
          System.currentTimeMillis() - start,
          null);
    } catch (Exception e) {
      return new TestResult(false, e.getMessage(), System.currentTimeMillis() - start, null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    return List.of(
        stream("Account", "Id", "Name", "Industry"),
        stream("Contact", "Id", "FirstName", "LastName", "Email"),
        stream("Lead", "Id", "Company", "Status"),
        stream("Opportunity", "Id", "Name", "StageName", "Amount"));
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String objectType, ReadOptions options) {
    return new SalesforceRecordIterator(config, objectType);
  }

  private StreamInfo stream(String name, String... fields) {
    List<FieldInfo> fieldInfos = new ArrayList<>();
    for (String f : fields) {
      fieldInfos.add(new FieldInfo(f, "string", true, null));
    }
    return new StreamInfo(name, null, fieldInfos, List.of("Id"), Map.of());
  }

  private HttpResponse<String> apiGet(Map<String, Object> config, String path) throws Exception {
    String base = trimSlash((String) config.get("instance_url"));
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(base + path))
            .header("Authorization", "Bearer " + config.get("access_token"))
            .header("Accept", "application/json")
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String apiVersion(Map<String, Object> config) {
    Object v = config.get("api_version");
    return v != null && !v.toString().isBlank() ? v.toString() : "v59.0";
  }

  private static String trimSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static boolean blank(Object v) {
    return v == null || v.toString().isBlank();
  }

  private class SalesforceRecordIterator implements RecordIterator {
    private final Map<String, Object> config;
    private final String objectType;
    private String nextUrl;
    private final Queue<Record> buffer = new LinkedList<>();
    private boolean closed;

    SalesforceRecordIterator(Map<String, Object> config, String objectType) {
      this.config = config;
      this.objectType = objectType;
      String base = trimSlash((String) config.get("instance_url"));
      this.nextUrl =
          base
              + "/services/data/"
              + apiVersion(config)
              + "/query?q="
              + URLEncoder.encode(
                  "SELECT Id, Name FROM " + objectType + " LIMIT 200", StandardCharsets.UTF_8);
      fetchPage();
    }

    private void fetchPage() {
      if (nextUrl == null || closed) {
        return;
      }
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(nextUrl))
                .header("Authorization", "Bearer " + config.get("access_token"))
                .GET()
                .build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          nextUrl = null;
          return;
        }
        JsonNode json = objectMapper.readTree(response.body());
        for (JsonNode record : json.path("records")) {
          Map<String, Object> data = new HashMap<>();
          record
              .fields()
              .forEachRemaining(
                  e -> data.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText()));
          buffer.add(new Record(data, record.path("Id").asText(), System.currentTimeMillis()));
        }
        if (json.has("nextRecordsUrl") && !json.path("nextRecordsUrl").isNull()) {
          nextUrl =
              trimSlash((String) config.get("instance_url")) + json.path("nextRecordsUrl").asText();
        } else {
          nextUrl = null;
        }
      } catch (Exception e) {
        nextUrl = null;
        throw new RuntimeException("Salesforce fetch failed: " + e.getMessage(), e);
      }
    }

    @Override
    public boolean hasNext() {
      if (closed) {
        return false;
      }
      if (buffer.isEmpty() && nextUrl != null) {
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
      return nextUrl;
    }

    @Override
    public void close() {
      closed = true;
      buffer.clear();
    }
  }
}
