package io.pravah.connect.connector.protocol;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** REST API connector for consuming HTTP/JSON APIs. */
@Component
public class RestApiConnector implements SourceConnector {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RestTemplate restTemplate = new RestTemplate();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("rest-api")
        .name("REST API")
        .description("Connect to any REST/HTTP API with JSON responses.")
        .icon("api")
        .category("Protocol")
        .type(ConnectorType.PROTOCOL)
        .mode(ConnectorMode.SOURCE)
        .configFields(
            List.of(
                ConfigField.builder("baseUrl")
                    .label("Base URL")
                    .description("API base URL")
                    .type(URL)
                    .required()
                    .placeholder("https://api.example.com")
                    .group("Connection")
                    .order(1)
                    .build(),
                ConfigField.builder("authType")
                    .label("Authentication Type")
                    .description("How to authenticate with the API")
                    .type(SELECT)
                    .defaultValue("none")
                    .options(List.of("none", "basic", "bearer", "apiKey", "oauth2"))
                    .group("Authentication")
                    .order(2)
                    .build(),
                ConfigField.builder("username")
                    .label("Username")
                    .description("Basic auth username")
                    .type(STRING)
                    .group("Authentication")
                    .order(3)
                    .dependsOn("authType", "basic")
                    .build(),
                ConfigField.builder("password")
                    .label("Password")
                    .description("Basic auth password")
                    .type(PASSWORD)
                    .group("Authentication")
                    .order(4)
                    .dependsOn("authType", "basic")
                    .build(),
                ConfigField.builder("token")
                    .label("Bearer Token")
                    .description("Bearer token for authentication")
                    .type(PASSWORD)
                    .group("Authentication")
                    .order(5)
                    .dependsOn("authType", "bearer")
                    .build(),
                ConfigField.builder("apiKeyName")
                    .label("API Key Header Name")
                    .description("Header name for API key")
                    .type(STRING)
                    .defaultValue("X-API-Key")
                    .group("Authentication")
                    .order(6)
                    .dependsOn("authType", "apiKey")
                    .build(),
                ConfigField.builder("apiKeyValue")
                    .label("API Key")
                    .description("API key value")
                    .type(PASSWORD)
                    .group("Authentication")
                    .order(7)
                    .dependsOn("authType", "apiKey")
                    .build(),
                ConfigField.builder("headers")
                    .label("Custom Headers")
                    .description("Additional HTTP headers")
                    .type(KEY_VALUE)
                    .group("Advanced")
                    .order(8)
                    .build(),
                ConfigField.builder("dataPath")
                    .label("Data Path")
                    .description("JSON path to array of records (e.g., data.items)")
                    .type(STRING)
                    .placeholder("data")
                    .group("Data")
                    .order(9)
                    .build(),
                ConfigField.builder("paginationType")
                    .label("Pagination Type")
                    .description("How to handle pagination")
                    .type(SELECT)
                    .defaultValue("none")
                    .options(List.of("none", "offset", "page", "cursor", "link"))
                    .group("Pagination")
                    .order(10)
                    .build(),
                ConfigField.builder("pageSize")
                    .label("Page Size")
                    .description("Number of records per page")
                    .type(NUMBER)
                    .defaultValue(100)
                    .group("Pagination")
                    .order(11)
                    .build()))
        .capabilities(
            Map.of(
                "discover", false,
                "incremental", true,
                "fullRefresh", true))
        .tags(List.of("protocol", "rest", "http", "api", "json"))
        .build();
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new LinkedHashMap<>();

    if (isBlank(config.get("baseUrl"))) {
      errors.put("baseUrl", "Base URL is required");
    }

    String authType = getString(config, "authType");
    if ("basic".equals(authType)) {
      if (isBlank(config.get("username"))) {
        errors.put("username", "Username is required for basic auth");
      }
      if (isBlank(config.get("password"))) {
        errors.put("password", "Password is required for basic auth");
      }
    } else if ("bearer".equals(authType)) {
      if (isBlank(config.get("token"))) {
        errors.put("token", "Bearer token is required");
      }
    } else if ("apiKey".equals(authType)) {
      if (isBlank(config.get("apiKeyValue"))) {
        errors.put("apiKeyValue", "API key is required");
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    String baseUrl = getString(config, "baseUrl");

    try {
      HttpHeaders headers = buildHeaders(config);
      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(baseUrl, HttpMethod.GET, entity, String.class);

      long latency = System.currentTimeMillis() - start;

      if (response.getStatusCode().is2xxSuccessful()) {
        return TestResult.success(
            "Connected to API: " + baseUrl,
            latency,
            Map.of("statusCode", response.getStatusCode().value()));
      } else {
        return TestResult.failure("API returned status: " + response.getStatusCode());
      }

    } catch (Exception e) {
      return TestResult.failure("Connection failed", e);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    // REST APIs don't have discoverable schemas typically
    // Return a single "default" stream
    return List.of(new StreamInfo("data", null, List.of(), List.of(), Map.of("type", "api")));
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    String baseUrl = getString(config, "baseUrl");
    String dataPath = getString(config, "dataPath");
    String paginationType = getString(config, "paginationType");

    return new RestApiRecordIterator(
        restTemplate,
        objectMapper,
        baseUrl,
        buildHeaders(config),
        dataPath,
        paginationType,
        options);
  }

  private HttpHeaders buildHeaders(Map<String, Object> config) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));

    String authType = getString(config, "authType");

    if ("basic".equals(authType)) {
      String username = getString(config, "username");
      String password = getString(config, "password");
      headers.setBasicAuth(username, password);
    } else if ("bearer".equals(authType)) {
      String token = getString(config, "token");
      headers.setBearerAuth(token);
    } else if ("apiKey".equals(authType)) {
      String keyName = getString(config, "apiKeyName");
      String keyValue = getString(config, "apiKeyValue");
      headers.set(keyName != null ? keyName : "X-API-Key", keyValue);
    }

    // Add custom headers
    @SuppressWarnings("unchecked")
    Map<String, String> customHeaders = (Map<String, String>) config.get("headers");
    if (customHeaders != null) {
      customHeaders.forEach(headers::set);
    }

    return headers;
  }

  private static boolean isBlank(Object value) {
    return value == null || value.toString().isBlank();
  }

  private static String getString(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value != null ? value.toString() : null;
  }

  private static class RestApiRecordIterator implements RecordIterator {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final HttpHeaders headers;
    private final String dataPath;
    private final String paginationType;
    private final ReadOptions options;

    private List<Map<String, Object>> currentBatch = new ArrayList<>();
    private int batchIndex = 0;
    private int pageNumber = 0;
    private String nextCursor = null;
    private boolean hasMorePages = true;
    private long rowNum = 0;

    RestApiRecordIterator(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        String baseUrl,
        HttpHeaders headers,
        String dataPath,
        String paginationType,
        ReadOptions options) {
      this.restTemplate = restTemplate;
      this.objectMapper = objectMapper;
      this.baseUrl = baseUrl;
      this.headers = headers;
      this.dataPath = dataPath;
      this.paginationType = paginationType;
      this.options = options;

      // Initial cursor from options
      this.nextCursor = options.cursor();

      fetchNextBatch();
    }

    @Override
    public boolean hasNext() {
      if (batchIndex < currentBatch.size()) {
        return true;
      }

      if (hasMorePages) {
        fetchNextBatch();
        return batchIndex < currentBatch.size();
      }

      return false;
    }

    @Override
    public Record next() {
      if (!hasNext()) throw new NoSuchElementException();

      Map<String, Object> data = currentBatch.get(batchIndex++);
      rowNum++;

      return new Record(data, String.valueOf(rowNum), System.currentTimeMillis());
    }

    @Override
    public String getCursor() {
      return nextCursor != null ? nextCursor : String.valueOf(rowNum);
    }

    @Override
    public void close() {
      // Nothing to close
    }

    private void fetchNextBatch() {
      try {
        String url = buildPageUrl();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response =
            restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
          hasMorePages = false;
          return;
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        // Navigate to data path
        JsonNode dataNode = root;
        if (dataPath != null && !dataPath.isEmpty()) {
          for (String part : dataPath.split("\\.")) {
            dataNode = dataNode.get(part);
            if (dataNode == null) break;
          }
        }

        currentBatch.clear();
        batchIndex = 0;

        if (dataNode != null && dataNode.isArray()) {
          for (JsonNode item : dataNode) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.convertValue(item, Map.class);
            currentBatch.add(map);
          }
        }

        // Update pagination state
        updatePaginationState(root);

        if (currentBatch.isEmpty()) {
          hasMorePages = false;
        }

      } catch (Exception e) {
        hasMorePages = false;
        throw new RuntimeException("Failed to fetch from API", e);
      }
    }

    private String buildPageUrl() {
      StringBuilder url = new StringBuilder(baseUrl);

      if ("none".equals(paginationType) || paginationType == null) {
        return url.toString();
      }

      boolean hasParams = baseUrl.contains("?");
      String separator = hasParams ? "&" : "?";

      switch (paginationType) {
        case "offset" -> {
          url.append(separator).append("offset=").append(pageNumber * options.batchSize());
          url.append("&limit=").append(options.batchSize());
        }
        case "page" -> {
          url.append(separator).append("page=").append(pageNumber + 1);
          url.append("&per_page=").append(options.batchSize());
        }
        case "cursor" -> {
          if (nextCursor != null) {
            url.append(separator).append("cursor=").append(nextCursor);
          }
          url.append(hasParams || nextCursor != null ? "&" : "?")
              .append("limit=")
              .append(options.batchSize());
        }
      }

      return url.toString();
    }

    private void updatePaginationState(JsonNode root) {
      pageNumber++;

      switch (paginationType != null ? paginationType : "none") {
        case "cursor" -> {
          JsonNode cursorNode = root.get("next_cursor");
          if (cursorNode == null) cursorNode = root.get("cursor");
          if (cursorNode == null) cursorNode = root.path("meta").get("next_cursor");

          if (cursorNode != null && !cursorNode.isNull()) {
            nextCursor = cursorNode.asText();
          } else {
            hasMorePages = false;
          }
        }
        case "link" -> {
          JsonNode nextLink = root.path("links").get("next");
          hasMorePages = nextLink != null && !nextLink.isNull();
        }
        case "offset", "page" -> {
          JsonNode totalNode = root.get("total");
          if (totalNode == null) totalNode = root.path("meta").get("total");

          if (totalNode != null && !totalNode.isNull()) {
            long total = totalNode.asLong();
            hasMorePages = (long) pageNumber * options.batchSize() < total;
          } else {
            hasMorePages = !currentBatch.isEmpty() && currentBatch.size() >= options.batchSize();
          }
        }
        default -> hasMorePages = false;
      }
    }
  }
}
