package io.pravah.common.exception;

/** Thrown when a tenant exceeds their quota or rate limit. */
public class QuotaExceededException extends PravahException {

  private static final long serialVersionUID = 1L;

  private final String quotaType;
  private final long currentValue;
  private final long limit;

  /**
   * Creates a quota exceeded exception with quota details.
   *
   * @param quotaType type of quota exceeded (e.g., "pipelines", "executions")
   * @param currentValue current usage value
   * @param limit maximum allowed value
   */
  public QuotaExceededException(String quotaType, long currentValue, long limit) {
    super(
        "QUOTA_EXCEEDED",
        String.format(
            "Quota exceeded for %s: current=%d, limit=%d", quotaType, currentValue, limit));
    this.quotaType = quotaType;
    this.currentValue = currentValue;
    this.limit = limit;
  }

  /**
   * Creates a rate limit exception with a custom message.
   *
   * @param message custom error message (e.g., "Too many login attempts")
   */
  public QuotaExceededException(String message) {
    super("RATE_LIMIT_EXCEEDED", message);
    this.quotaType = "rate_limit";
    this.currentValue = -1;
    this.limit = -1;
  }

  public String getQuotaType() {
    return quotaType;
  }

  public long getCurrentValue() {
    return currentValue;
  }

  public long getLimit() {
    return limit;
  }

  @Override
  public int suggestedHttpStatus() {
    return 429; // Too Many Requests
  }
}
