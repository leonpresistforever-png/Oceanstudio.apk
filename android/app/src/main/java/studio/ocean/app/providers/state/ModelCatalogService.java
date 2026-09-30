package studio.ocean.app.providers.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import studio.ocean.app.providers.ProviderRegistry;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.model.ProviderDescriptor;

/**
 * Manages model discovery, caching, and fallback catalogs across connected providers.
 */
public final class ModelCatalogService {

    public List<ModelDescriptor> discoverModels(ProviderConnection connection) {
        if (connection == null) return Collections.emptyList();

        // If connection already cached models, return them
        if (connection.models != null && !connection.models.isEmpty()) {
            return connection.models;
        }

        List<ModelDescriptor> list = new ArrayList<>();
        ProviderDescriptor descriptor = ProviderRegistry.find(connection.providerId);
        String defaultModel = descriptor != null ? descriptor.defaultModel : connection.selectedModel;

        if ("antigravity".equals(connection.providerId) || "google".equals(connection.providerId)) {
            list.add(new ModelDescriptor("gemini-2.5-flash", "Gemini 2.5 Flash", 1048576, true, true, true, "Available"));
            list.add(new ModelDescriptor("gemini-2.5-pro", "Gemini 2.5 Pro", 2097152, false, true, true, "Available"));
            list.add(new ModelDescriptor("gemini-1.5-flash", "Gemini 1.5 Flash", 1048576, false, true, true, "Available"));
        } else if ("openai".equals(connection.providerId)) {
            list.add(new ModelDescriptor("gpt-4o", "GPT-4o", 128000, true, true, true, "Available"));
            list.add(new ModelDescriptor("gpt-4o-mini", "GPT-4o Mini", 128000, false, true, true, "Available"));
            list.add(new ModelDescriptor("o1-mini", "o1 Mini (Reasoning)", 128000, false, false, false, "Available"));
        } else if ("anthropic".equals(connection.providerId)) {
            list.add(new ModelDescriptor("claude-sonnet-4-20250514", "Claude 3.7 Sonnet", 200000, true, true, true, "Available"));
            list.add(new ModelDescriptor("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", 200000, false, true, false, "Available"));
        } else if ("deepseek".equals(connection.providerId)) {
            list.add(new ModelDescriptor("deepseek-chat", "DeepSeek-V3", 64000, true, true, false, "Available"));
            list.add(new ModelDescriptor("deepseek-reasoner", "DeepSeek-R1", 64000, false, false, false, "Available"));
        } else if ("kimi".equals(connection.providerId)) {
            list.add(new ModelDescriptor("moonshot-v1-auto", "Moonshot Auto", 128000, true, true, false, "Available"));
            list.add(new ModelDescriptor("moonshot-v1-128k", "Moonshot 128k", 128000, false, true, false, "Available"));
        } else {
            if (defaultModel != null && !defaultModel.isEmpty()) {
                list.add(new ModelDescriptor(defaultModel, defaultModel, 128000, true, true, false, "Available"));
            }
        }

        return Collections.unmodifiableList(list);
    }
}
