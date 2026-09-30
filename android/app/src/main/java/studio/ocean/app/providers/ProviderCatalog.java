package studio.ocean.app.providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.OceanByokManager;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ProviderDescriptor;

/**
 * Backward compatibility adapter delegating to ProviderRegistry.
 * Single source of truth is ProviderRegistry.
 */
public final class ProviderCatalog {
    public enum AuthMode { API_KEY, OAUTH_PKCE }

    public static final class Entry {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String defaultBaseUrl;
        public final String defaultModel;
        public final AuthMode authMode;
        public final String credentialHint;

        public Entry(String id, String title, String subtitle, String defaultBaseUrl, String defaultModel,
                AuthMode authMode, String credentialHint) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultModel = defaultModel;
            this.authMode = authMode;
            this.credentialHint = credentialHint;
        }

        public String byokProviderId() {
            if (OceanByokManager.PROVIDER_GOOGLE.equals(id) || OceanByokManager.PROVIDER_ANTHROPIC.equals(id)
                    || OceanByokManager.PROVIDER_OPENAI.equals(id)) return id;
            return OceanByokManager.PROVIDER_CUSTOM;
        }
    }

    private ProviderCatalog() {}

    public static List<Entry> all() {
        List<Entry> list = new ArrayList<>();
        for (ProviderDescriptor desc : ProviderRegistry.all()) {
            AuthMode mode = desc.supports(AuthStrategy.DIRECT_OAUTH) ? AuthMode.OAUTH_PKCE : AuthMode.API_KEY;
            list.add(new Entry(desc.id, desc.title, desc.subtitle, desc.defaultBaseUrl, desc.defaultModel,
                    mode, desc.credentialHint));
        }
        return Collections.unmodifiableList(list);
    }

    public static Entry find(String id) {
        if (id == null) return null;
        ProviderDescriptor desc = ProviderRegistry.find(id);
        if (desc == null) return null;
        AuthMode mode = desc.supports(AuthStrategy.DIRECT_OAUTH) ? AuthMode.OAUTH_PKCE : AuthMode.API_KEY;
        return new Entry(desc.id, desc.title, desc.subtitle, desc.defaultBaseUrl, desc.defaultModel,
                mode, desc.credentialHint);
    }
}
