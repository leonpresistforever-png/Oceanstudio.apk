package studio.ocean.app.providers.router;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ConnectionStatus;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.ProviderConnection;

/**
 * Routes requests to the optimal verified connected provider without surprise billing (Directive 2026-10-02 §11, §14).
 * Strictly enforces:
 *  1. Routes ONLY among VERIFIED_CONNECTED connections.
 *  2. Respects explicit user policy: local-only override, provider lock, cost preference.
 *  3. Never prefers CLI merely because auth mode is CLI (Direct OAuth subscriptions are first-class).
 *  4. If no cloud provider exists and a local model is verified, local becomes the default agent backend.
 *  5. Never silently switches billing sources without explicit metered fallback permission.
 */
public final class SmartRouter {

    public static final class RouteDecision {
        @NonNull public final ProviderConnection connection;
        @NonNull public final String selectedModel;
        @NonNull public final String rationale;

        public RouteDecision(@NonNull ProviderConnection connection,
                             @NonNull String selectedModel,
                             @NonNull String rationale) {
            this.connection = connection;
            this.selectedModel = selectedModel;
            this.rationale = rationale;
        }
    }

    @Nullable
    public RouteDecision selectRoute(@Nullable List<ProviderConnection> activeConnections,
                                     boolean requiresTools,
                                     boolean requiresVision,
                                     boolean allowMeteredFallback) {
        return selectRoute(activeConnections, requiresTools, requiresVision, allowMeteredFallback, null, false);
    }

    @Nullable
    public RouteDecision selectRoute(@Nullable List<ProviderConnection> activeConnections,
                                     boolean requiresTools,
                                     boolean requiresVision,
                                     boolean allowMeteredFallback,
                                     @Nullable String preferredProviderId,
                                     boolean preferLocalModel) {
        if (activeConnections == null || activeConnections.isEmpty()) {
            return null;
        }

        // Rule 1: Route among VERIFIED_CONNECTED connections only
        List<ProviderConnection> verified = new ArrayList<>();
        boolean hasCloudConnected = false;
        ProviderConnection localConnected = null;

        for (ProviderConnection conn : activeConnections) {
            if (conn.status == ConnectionStatus.CONNECTED) {
                verified.add(conn);
                if (conn.strategy == AuthStrategy.LOCAL) {
                    localConnected = conn;
                } else {
                    hasCloudConnected = true;
                }
            }
        }

        if (verified.isEmpty()) {
            return null;
        }

        // Rule 2A: Explicit Local Override / "Prefer Local"
        if (preferLocalModel && localConnected != null) {
            String model = chooseModel(localConnected, requiresTools, requiresVision);
            if (model != null) {
                return new RouteDecision(localConnected, model, "Routed via explicit user local-model preference");
            }
        }

        // Rule 2B: Explicit Provider Lock
        if (preferredProviderId != null && !preferredProviderId.isEmpty()) {
            for (ProviderConnection conn : verified) {
                if (preferredProviderId.equalsIgnoreCase(conn.providerId)) {
                    String model = chooseModel(conn, requiresTools, requiresVision);
                    if (model != null) {
                        return new RouteDecision(conn, model, "Routed via user-locked provider preference (" + conn.providerId + ")");
                    }
                }
            }
        }

        // Rule 4: If no cloud provider exists and a local model is verified, local becomes the default
        if (!hasCloudConnected && localConnected != null) {
            String model = chooseModel(localConnected, requiresTools, requiresVision);
            if (model != null) {
                return new RouteDecision(localConnected, model, "Defaulted to on-device local model (no verified cloud providers)");
            }
        }

        // The gateway's real priority combo handles account/model cooldown and fallback.
        for (ProviderConnection conn : verified) if (conn.strategy == AuthStrategy.GATEWAY && "gateway_auto".equals(conn.id))
            return new RouteDecision(conn, conn.selectedModel, "Ocean gateway · fallback among connected accounts");
        for (ProviderConnection conn : verified) if (conn.strategy == AuthStrategy.GATEWAY)
            return new RouteDecision(conn, conn.selectedModel, "Ocean gateway · " + conn.displayAccount);

        // Priority 1: Direct OAuth subscription accounts (zero per-token API meter)
        for (ProviderConnection conn : verified) {
            if (conn.strategy == AuthStrategy.DIRECT_OAUTH || conn.strategy == AuthStrategy.OFFICIAL_OAUTH || conn.strategy == AuthStrategy.DEVICE_CODE) {
                String model = chooseModel(conn, requiresTools, requiresVision);
                if (model != null) {
                    return new RouteDecision(conn, model, "Routed via Direct Connect subscription (" + conn.providerId + ")");
                }
            }
        }

        // Priority 2: Subscription-backed Official CLI sessions (Antigravity, Codex, Claude Code, Kimi)
        for (ProviderConnection conn : verified) {
            if (conn.strategy == AuthStrategy.OFFICIAL_CLI) {
                String model = chooseModel(conn, requiresTools, requiresVision);
                if (model != null) {
                    return new RouteDecision(conn, model, "Routed via subscription CLI (" + conn.providerId + ")");
                }
            }
        }

        // Priority 3: Local Models (free on-device inference)
        if (localConnected != null) {
            String model = chooseModel(localConnected, requiresTools, requiresVision);
            if (model != null) {
                return new RouteDecision(localConnected, model, "Routed via on-device local model");
            }
        }

        // Priority 4: Metered API keys (strictly requires allowMeteredFallback=true)
        if (allowMeteredFallback) {
            for (ProviderConnection conn : verified) {
                if (conn.strategy == AuthStrategy.API_KEY || conn.strategy == AuthStrategy.CUSTOM_ENDPOINT) {
                    String model = chooseModel(conn, requiresTools, requiresVision);
                    if (model != null) {
                        return new RouteDecision(conn, model, "Routed via metered API key (" + conn.providerId + ")");
                    }
                }
            }
        }

        // Safe fallback: First verified non-metered connection if any, else first verified
        for (ProviderConnection conn : verified) {
            if (conn.strategy != AuthStrategy.API_KEY && conn.strategy != AuthStrategy.CUSTOM_ENDPOINT) {
                return new RouteDecision(conn, chooseModel(conn, requiresTools, requiresVision), "Fallback non-metered route");
            }
        }

        if (allowMeteredFallback && !verified.isEmpty()) {
            ProviderConnection first = verified.get(0);
            return new RouteDecision(first, chooseModel(first, requiresTools, requiresVision), "Fallback route");
        }

        return null;
    }

    @NonNull
    private String chooseModel(@NonNull ProviderConnection conn, boolean tools, boolean vision) {
        if (!tools && !vision && conn.selectedModel != null && !conn.selectedModel.isEmpty()) return conn.selectedModel;
        for (ModelDescriptor m : conn.models) {
            if (m.id.equals(conn.selectedModel) && (!tools || m.supportsTools) && (!vision || m.supportsVision)) return m.id;
        }
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
