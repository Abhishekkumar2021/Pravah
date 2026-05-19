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

/** Shopify Admin REST API connector. */
@Component
public class ShopifyConnector implements SourceConnector {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("shopify")
        .name("Shopify")
        .description("Read store data from Shopify Admin API")
        .icon("shopify")
        .category("SaaS")
        .type(ConnectorType.SAAS)
        .mode(ConnectorMode.SOURCE)
        .configFields(
            List.of(
                ConfigField.builder("shop")
                    .label("Shop subdomain")
                    .description("Store name without .myshopify.com")
                    .type(ConfigField.FieldType.STRING)
                    .required(true)
                    .placeholder("my-store")
                    .group("Connection")
                    .order(1)
                    .build(),
                ConfigField.builder("access_token")
                    .label("Admin API access token")
                    .type(ConfigField.FieldType.PASSWORD)
                    .required(true)
                    .group("Authentication")
                    .order(2)
                    .build(),
                ConfigField.builder("api_version")
                    .label("API Version")
                    .defaultValue("2024-01")
                    .type(ConfigField.FieldType.STRING)
                    .group("Connection")
                    .order(3)
                    .build()))
        .capabilities(Map.of("resources", List.of("products", "orders", "customers")))
        .tags(List.of("shopify", "ecommerce", "saas"))
        .build();
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();
    if (blank(config.get("shop"))) {
      errors.put("shop", "Shop name is required");
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
      HttpResponse<String> response = apiGet(config, "/shop.json");
      boolean ok = response.statusCode() == 200;
      return new TestResult(
          ok,
          ok ? "Connected to Shopify" : "API error: " + response.statusCode(),
          System.currentTimeMillis() - start,
          null);
    } catch (Exception e) {
      return new TestResult(false, e.getMessage(), System.currentTimeMillis() - start, null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    return List.of(
        stream("products", "id", "title", "vendor", "status"),
        stream("orders", "id", "name", "total_price", "financial_status"),
        stream("customers", "id", "email", "first_name", "last_name"));
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String resource, ReadOptions options) {
    return new ShopifyRecordIterator(config, resource);
  }

  private StreamInfo stream(String name, String... fields) {
    List<FieldInfo> fieldInfos = new ArrayList<>();
    for (String f : fields) {
      fieldInfos.add(new FieldInfo(f, "string", true, null));
    }
    return new StreamInfo(name, null, fieldInfos, List.of("id"), Map.of());
  }

  private HttpResponse<String> apiGet(Map<String, Object> config, String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl(config) + path))
            .header("X-Shopify-Access-Token", (String) config.get("access_token"))
            .header("Accept", "application/json")
            .GET()
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String baseUrl(Map<String, Object> config) {
    String shop = config.get("shop").toString().replace(".myshopify.com", "");
    String version =
        config.get("api_version") != null ? config.get("api_version").toString() : "2024-01";
    return "https://" + shop + ".myshopify.com/admin/api/" + version;
  }

  private static boolean blank(Object v) {
    return v == null || v.toString().isBlank();
  }

  private class ShopifyRecordIterator implements RecordIterator {
    private final Map<String, Object> config;
    private final String resource;
    private String pageInfo;
    private final Queue<Record> buffer = new LinkedList<>();
    private boolean hasMore = true;
    private boolean closed;

    ShopifyRecordIterator(Map<String, Object> config, String resource) {
      this.config = config;
      this.resource = resource;
      fetchPage();
    }

    private void fetchPage() {
      if (!hasMore || closed) {
        return;
      }
      try {
        String path = "/" + resource + ".json?limit=100";
        if (pageInfo != null) {
          path += "&page_info=" + pageInfo;
        }
        HttpResponse<String> response = apiGet(config, path);
        if (response.statusCode() != 200) {
          hasMore = false;
          return;
        }
        JsonNode json = objectMapper.readTree(response.body());
        String rootKey = resource;
        for (JsonNode item : json.path(rootKey)) {
          Map<String, Object> data = new HashMap<>();
          item.fields().forEachRemaining(e -> data.put(e.getKey(), jsonValue(e.getValue())));
          buffer.add(new Record(data, item.path("id").asText(), System.currentTimeMillis()));
        }
        Optional<String> link = response.headers().firstValue("Link");
        if (link.isPresent() && link.get().contains("rel=\"next\"")) {
          pageInfo = extractPageInfo(link.get());
        } else {
          hasMore = false;
        }
      } catch (Exception e) {
        hasMore = false;
        throw new RuntimeException("Shopify fetch failed: " + e.getMessage(), e);
      }
    }

    private static Object jsonValue(JsonNode node) {
      if (node.isNull()) {
        return null;
      }
      if (node.isNumber()) {
        return node.numberValue();
      }
      if (node.isBoolean()) {
        return node.booleanValue();
      }
      return node.asText();
    }

    private static String extractPageInfo(String linkHeader) {
      int idx = linkHeader.indexOf("page_info=");
      if (idx < 0) {
        return null;
      }
      int end = linkHeader.indexOf('>', idx);
      String segment = end > idx ? linkHeader.substring(idx, end) : linkHeader.substring(idx);
      return segment.replace("page_info=", "").split("&")[0];
    }

    @Override
    public boolean hasNext() {
      if (closed) {
        return false;
      }
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
      return pageInfo;
    }

    @Override
    public void close() {
      closed = true;
      buffer.clear();
    }
  }
}
