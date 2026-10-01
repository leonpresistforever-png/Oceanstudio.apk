package studio.ocean.app.providers.model;

/**
 * Lifecycle state of a provider connection (Directive 2 §11, Directive 3 §7.1).
 * Follows state machine: UNCONFIGURED -> AUTHORIZING -> VERIFYING -> CONNECTED -> RATE_LIMITED / NEEDS_REAUTH / ERROR
 */
public enum ConnectionStatus {
    UNCONFIGURED("Not configured"),
    NOT_INSTALLED("Not installed"),
    NEEDS_LOGIN("Needs login"),
    AUTHORIZING("Authorizing"),
    VERIFYING("Verifying"),
    CONNECTED("Connected"),
    RATE_LIMITED("Rate limited"),
    NEEDS_REAUTH("Needs re-auth"),
    REAUTH_REQUIRED("Needs re-auth"), // backward-compatible alias
    DEGRADED("Degraded"),
    CONFIG_ERROR("Config error"),
    OFFLINE("Offline"),
    ERROR("Error"),
    DISCONNECTED("Disconnected");

    public final String label;

    ConnectionStatus(String label) {
        this.label = label;
    }
}
