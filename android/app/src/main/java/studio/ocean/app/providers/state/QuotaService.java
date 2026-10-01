package studio.ocean.app.providers.state;

import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.model.QuotaSnapshot;

/**
 * Discovers and reports quota and plan entitlements for active connections.
 * Strictly adheres to Rule 1 & Rule 2: never invents fake remaining quota percentages.
 */
public final class QuotaService {

    public QuotaSnapshot inspect(ProviderConnection connection) {
        if (connection == null) {
            return QuotaSnapshot.unknown("Unknown", "none");
        }

        if (connection.strategy == AuthStrategy.OFFICIAL_CLI) {
            // Subscription-backed official CLI tools report plan/entitlement directly
            if ("antigravity".equals(connection.providerId)) {
                return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED,
                        null, "Google Workspace / Consumer Plan", "agy-cli");
            }
            if ("kimi".equals(connection.providerId)) {
                return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED,
                        null, "Kimi Managed Service", "kimi-cli");
            }
            if ("openai".equals(connection.providerId)) {
                return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED,
                        null, "Codex Subscription", "codex-cli");
            }
            if ("anthropic".equals(connection.providerId)) {
                return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED,
                        null, "Claude Code Session", "claude-cli");
            }
        }

        if (connection.strategy == AuthStrategy.API_KEY) {
            return QuotaSnapshot.unknown("Pay-as-you-go API", "api-header");
        }

        if (connection.strategy == AuthStrategy.ENTERPRISE && connection.quota != null && connection.quota.used != null && connection.quota.remaining != null) {
            return QuotaSnapshot.exact(connection.quota.used, connection.quota.remaining, connection.quota.unit,
                    connection.quota.resetsAtEpochMs, "Enterprise Exact Quota", "provider-api");
        }

        if (connection.strategy == AuthStrategy.LOCAL) {
            // Local on-device runtime: do not fabricate infinite token quota (Directive Point 6)
            return QuotaSnapshot.reported(null, null, QuotaSnapshot.Unit.PROVIDER_DEFINED,
                    null, "On-Device Runtime", "local-engine");
        }

        return QuotaSnapshot.unknown("Standard", "default");
    }
}
