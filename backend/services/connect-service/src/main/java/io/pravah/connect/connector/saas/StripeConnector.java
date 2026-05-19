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

/**
 * Stripe connector for reading financial data from Stripe API.
 *
 * <p>Supports reading customers, charges, subscriptions, invoices, and other Stripe resources.
 */
@Component
public class StripeConnector implements SourceConnector {

  private static final String STRIPE_API_BASE = "https://api.stripe.com/v1";
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder()
        .id("stripe")
        .name("Stripe")
        .description("Read financial data from Stripe - customers, charges, subscriptions")
        .icon("stripe")
        .category("SaaS")
        .type(ConnectorType.SAAS)
        .mode(ConnectorMode.SOURCE)
        .version("1.0.0")
        .configFields(getConfigFields())
        .capabilities(
            Map.of(
                "pagination", true,
                "incremental", true,
                "resources",
                    List.of(
                        "customers",
                        "charges",
                        "subscriptions",
                        "invoices",
                        "products",
                        "prices",
                        "payment_intents")))
        .tags(List.of("stripe", "payments", "fintech", "saas", "billing"))
        .build();
  }

  private List<ConfigField> getConfigFields() {
    return List.of(
        ConfigField.builder()
            .name("api_key")
            .label("Secret API Key")
            .description("Stripe secret key (starts with sk_)")
            .type(ConfigField.FieldType.PASSWORD)
            .required(true)
            .placeholder("sk_live_...")
            .group("Authentication")
            .order(1)
            .build(),
        ConfigField.builder()
            .name("account_id")
            .label("Connected Account ID")
            .description("For Stripe Connect: the connected account ID (optional)")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .placeholder("acct_...")
            .group("Authentication")
            .order(2)
            .build(),
        ConfigField.builder()
            .name("start_date")
            .label("Start Date")
            .description("Fetch records created after this date (ISO 8601)")
            .type(ConfigField.FieldType.STRING)
            .required(false)
            .placeholder("2024-01-01")
            .group("Sync Options")
            .order(3)
            .build(),
        ConfigField.builder()
            .name("page_size")
            .label("Page Size")
            .description("Number of records per API request (max 100)")
            .type(ConfigField.FieldType.NUMBER)
            .required(false)
            .defaultValue(100)
            .group("Sync Options")
            .order(4)
            .build());
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new HashMap<>();

    String apiKey = (String) config.get("api_key");
    if (apiKey == null || apiKey.isBlank()) {
      errors.put("api_key", "API key is required");
    } else if (!apiKey.startsWith("sk_")) {
      errors.put("api_key", "API key should start with 'sk_'");
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();

    try {
      String apiKey = (String) config.get("api_key");

      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(STRIPE_API_BASE + "/balance"))
              .header("Authorization", "Bearer " + apiKey);

      String accountId = (String) config.get("account_id");
      if (accountId != null && !accountId.isBlank()) {
        requestBuilder.header("Stripe-Account", accountId);
      }

      HttpRequest request = requestBuilder.GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        JsonNode json = objectMapper.readTree(response.body());
        JsonNode available = json.path("available");
        String currency = "unknown";
        long amount = 0;

        if (available.isArray() && available.size() > 0) {
          currency = available.get(0).path("currency").asText();
          amount = available.get(0).path("amount").asLong();
        }

        return new TestResult(
            true,
            String.format("Connected. Balance: %d %s", amount, currency.toUpperCase()),
            System.currentTimeMillis() - start,
            Map.of("currency", currency, "balance", amount));
      } else {
        JsonNode error = objectMapper.readTree(response.body());
        String message = error.path("error").path("message").asText("Unknown error");
        return new TestResult(
            false, "API error: " + message, System.currentTimeMillis() - start, null);
      }
    } catch (Exception e) {
      return new TestResult(
          false, "Connection failed: " + e.getMessage(), System.currentTimeMillis() - start, null);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    return List.of(
        createStreamInfo("customers", "Customer records", "id", "email", "name", "created"),
        createStreamInfo(
            "charges", "Payment charges", "id", "amount", "currency", "status", "created"),
        createStreamInfo(
            "subscriptions",
            "Recurring subscriptions",
            "id",
            "status",
            "current_period_start",
            "current_period_end"),
        createStreamInfo(
            "invoices", "Invoice records", "id", "customer", "amount_due", "status", "created"),
        createStreamInfo("products", "Product catalog", "id", "name", "description", "active"),
        createStreamInfo(
            "prices", "Pricing records", "id", "product", "unit_amount", "currency", "type"),
        createStreamInfo(
            "payment_intents", "Payment intents", "id", "amount", "currency", "status", "created"));
  }

  private StreamInfo createStreamInfo(String name, String description, String... fieldNames) {
    List<FieldInfo> fields = new ArrayList<>();
    for (String fieldName : fieldNames) {
      fields.add(new FieldInfo(fieldName, "string", true, null));
    }
    return new StreamInfo(name, null, fields, List.of("id"), Map.of("description", description));
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String resource, ReadOptions options) {
    return new StripeRecordIterator(config, resource, options);
  }

  private class StripeRecordIterator implements RecordIterator {
    private final String apiKey;
    private final String accountId;
    private final String resource;
    private final int pageSize;
    private final Queue<Record> buffer = new LinkedList<>();
    private String cursor;
    private boolean hasMore = true;
    private boolean closed = false;

    StripeRecordIterator(Map<String, Object> config, String resource, ReadOptions options) {
      this.apiKey = (String) config.get("api_key");
      this.accountId = (String) config.get("account_id");
      this.resource = resource;
      this.pageSize = ((Number) config.getOrDefault("page_size", 100)).intValue();
      this.cursor = options != null ? options.cursor() : null;

      fetchPage();
    }

    private void fetchPage() {
      if (!hasMore || closed) return;

      try {
        StringBuilder url = new StringBuilder(STRIPE_API_BASE).append("/").append(resource);
        url.append("?limit=").append(pageSize);

        if (cursor != null) {
          url.append("&starting_after=").append(cursor);
        }

        HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", "Bearer " + apiKey);

        if (accountId != null && !accountId.isBlank()) {
          requestBuilder.header("Stripe-Account", accountId);
        }

        HttpResponse<String> response =
            httpClient.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
          JsonNode json = objectMapper.readTree(response.body());
          hasMore = json.path("has_more").asBoolean(false);
          JsonNode data = json.path("data");

          for (JsonNode item : data) {
            Map<String, Object> record = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
              Map.Entry<String, JsonNode> field = fields.next();
              record.put(field.getKey(), nodeToValue(field.getValue()));
            }

            String id = item.path("id").asText();
            cursor = id;

            long created = item.path("created").asLong(System.currentTimeMillis() / 1000) * 1000;
            buffer.add(new Record(record, id, created));
          }
        } else {
          hasMore = false;
        }
      } catch (Exception e) {
        hasMore = false;
        throw new RuntimeException("Failed to fetch from Stripe: " + e.getMessage(), e);
      }
    }

    private Object nodeToValue(JsonNode node) {
      if (node.isNull()) return null;
      if (node.isBoolean()) return node.asBoolean();
      if (node.isNumber()) return node.numberValue();
      if (node.isTextual()) return node.asText();
      if (node.isArray() || node.isObject()) return node.toString();
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
      return cursor;
    }

    @Override
    public void close() {
      closed = true;
      buffer.clear();
    }
  }
}
