package studio.ocean.app.providers.router;

import java.util.List;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ConnectionStatus;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.ProviderConnection;

/**
 * Routes requests to the optimal connected provider without surprise billing.
 * Prefers subscription / CLI-backed sessions before metered API keys.
 */
public final class SmartRouter {

    public static final class RouteDecision {
        public final ProviderConnection connection;
        public final String selectedModel;
        public final String rationale;

        public RouteDecision(ProviderConnection connection, String selectedModel, String rationale) {
            this.connection = connection;
            this.selectedModel = selectedModel;
            this.rationale = rationale;
        }
    }

    public RouteDecision selectRoute(List<ProviderConnection> activeConnections,
                                     boolean requiresTools,
                                     boolean requiresVision,
                                     boolean allowMeteredFallback) {
        if (activeConnections == null || activeConnections.isEmpty()) {
            return null;
        }

        // Priority 1: Subscription-backed Official CLI sessions (Antigravity, Codex, Claude Code, Kimi)
        for (ProviderConnection conn : activeConnections) {
            if (conn.status != ConnectionStatus.CONNECTED) continue;
            if (conn.strategy == AuthStrategy.OFFICIAL_CLI) {
                String model = chooseModel(conn, requiresTools, requiresVision);
                if (model != null) {
                    return new RouteDecision(conn, model, "Routed via subscription CLI (" + conn.providerId + ")");
                }
            }
        }

        // Priority 2: Local Models (free, zero billing)
        for (ProviderConnection conn : activeConnections) {
            if (conn.status != ConnectionStatus.CONNECTED) continue;
            if (conn.strategy == AuthStrategy.LOCAL) {
                String model = chooseModel(conn, requiresTools, requiresVision);
                if (model != null) {
                    return new RouteDecision(conn, model, "Routed via on-device/local model");
                }
            }
        }

        // Priority 3: Metered API keys (only if allowed)
        if (allowMeteredFallback) {
            for (ProviderConnection conn : activeConnections) {
                if (conn.status != ConnectionStatus.CONNECTED) continue;
                if (conn.strategy == AuthStrategy.API_KEY || conn.strategy == AuthStrategy.CUSTOM_ENDPOINT) {
                    String model = chooseModel(conn, requiresTools, requiresVision);
                    if (model != null) {
                        return new RouteDecision(conn, model, "Routed via metered API key (" + conn.providerId + ")");
                    }
                }
            }
        }

        // Fallback to first available connected connection
        for (ProviderConnection conn : activeConnections) {
            if (conn.status == ConnectionStatus.CONNECTED) {
                return new RouteDecision(conn, conn.selectedModel, "Fallback route");
            }
        }

        return null;
    }

    private String chooseModel(ProviderConnection conn, boolean tools, boolean vision) {
        if (conn.models != null) {
            for (ModelDescriptor m : conn.models) {
                if (tools && !m.supportsTools) continue;
                if (vision && !m.supportsVision) continue;
                return m.id;
            }
        }
        return conn.selectedModel != null && !conn.selectedModel.isEmpty() ? conn.selectedModel : "default";
    }
}
