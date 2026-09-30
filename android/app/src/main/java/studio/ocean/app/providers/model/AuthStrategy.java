package studio.ocean.app.providers.model;

/**
 * Supported authentication and connection strategies for AI providers.
 * Differentiates official subscription CLIs, native OAuth, device codes, API keys, and local runtimes.
 */
public enum AuthStrategy {
    OFFICIAL_CLI,
    OFFICIAL_OAUTH,
    DEVICE_CODE,
    API_KEY,
    ENTERPRISE,
    LOCAL,
    CUSTOM_ENDPOINT
}
