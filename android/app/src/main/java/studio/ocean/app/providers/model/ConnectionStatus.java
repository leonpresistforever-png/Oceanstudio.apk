package studio.ocean.app.providers.model;

/**
 * Lifecycle state of a provider connection.
 */
public enum ConnectionStatus {
    CONNECTED,
    DEGRADED,
    REAUTH_REQUIRED,
    DISCONNECTED
}
