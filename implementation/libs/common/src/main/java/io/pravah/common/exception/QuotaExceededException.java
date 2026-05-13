package io.pravah.common.exception;

/**
 * Thrown when a tenant exceeds their quota or rate limit.
 */
public class QuotaExceededException extends PravahException {

    private static final long serialVersionUID = 1L;

    private final String quotaType;
    private final long currentValue;
    private final long limit;

    public QuotaExceededException(String quotaType, long currentValue, long limit) {
        super(
            "QUOTA_EXCEEDED",
            String.format(
                "Quota exceeded for %s: current=%d, limit=%d",
                quotaType, currentValue, limit
            )
        );
        this.quotaType = quotaType;
        this.currentValue = currentValue;
        this.limit = limit;
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
