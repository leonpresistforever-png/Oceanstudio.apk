package studio.ocean.app.providers.model;

/**
 * Supported authentication and connection strategies for AI providers.
 * Differentiates official subscription CLIs, native OAuth, device codes, API keys, and local runtimes.
 */
public enum AuthStrategy {
    OFFICIAL_CLI("Official CLI Bridge"),
    OFFICIAL_OAUTH("Official OAuth"),
    DIRECT_OAUTH("Direct OAuth"),
    DEVICE_CODE("Device Code"),
    API_KEY("API Key"),
    ENTERPRISE("Enterprise"),
    LOCAL("Local Runtime"),
    GATEWAY("Ocean Gateway"),
    CUSTOM_ENDPOINT("Custom Endpoint");

    public final String displayName;

    AuthStrategy(String displayName) {
        this.displayName = displayName;
    }
}
