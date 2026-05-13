package io.pravah.common.exception;

/**
 * Thrown when access to a resource is denied due to insufficient permissions.
 */
public class AccessDeniedException extends PravahException {

    private static final long serialVersionUID = 1L;

    private final String resource;
    private final String action;

    public AccessDeniedException(String resource, String action) {
        super(
            "ACCESS_DENIED",
            String.format("Access denied: cannot %s on %s", action, resource)
        );
        this.resource = resource;
        this.action = action;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    @Override
    public int suggestedHttpStatus() {
        return 403;
    }
}
