package studio.ocean.app.providers.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable metadata and supported connection modes for a provider in the registry.
 */
public final class ProviderDescriptor {
    public final String id;
    public final String title;
    public final String subtitle;
    public final String defaultBaseUrl;
    public final String defaultModel;
    public final List<AuthStrategy> supportedStrategies;
    public final String credentialHint;
    public final int iconRes;
    public final boolean isExperimental;
    public final String officialCliName;

    public ProviderDescriptor(String id, String title, String subtitle, String defaultBaseUrl,
                              String defaultModel, List<AuthStrategy> supportedStrategies,
                              String credentialHint, int iconRes, boolean isExperimental,
                              String officialCliName) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.supportedStrategies = Collections.unmodifiableList(supportedStrategies);
        this.credentialHint = credentialHint;
        this.iconRes = iconRes;
        this.isExperimental = isExperimental;
        this.officialCliName = officialCliName;
    }

    public boolean supports(AuthStrategy strategy) {
        return supportedStrategies.contains(strategy);
    }
}
