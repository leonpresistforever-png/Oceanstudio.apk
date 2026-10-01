package studio.ocean.app.providers.router;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ConnectionStatus;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.ProviderConnection;

public final class SmartRouterTest {

    @Test
    public void prefersOfficialCliBeforeMeteredApiKey() {
        SmartRouter router = new SmartRouter();

        ProviderConnection apiKeyConn = new ProviderConnection(
                "conn-api", "openai", "OpenAI Key", AuthStrategy.API_KEY,
                ConnectionStatus.CONNECTED, "", "gpt-4o", "vault_secret_123",
                null, null, null, null, null, System.currentTimeMillis()
        );

        ProviderConnection cliConn = new ProviderConnection(
                "conn-cli", "antigravity", "Antigravity CLI", AuthStrategy.OFFICIAL_CLI,
                ConnectionStatus.CONNECTED, "", "gemini-2.5-flash", null,
                "agy_active", null, null, null, null, System.currentTimeMillis()
        );

        List<ProviderConnection> list = Arrays.asList(apiKeyConn, cliConn);

        SmartRouter.RouteDecision decision = router.selectRoute(list, false, false, true);

        assertNotNull(decision);
        assertEquals("conn-cli", decision.connection.id);
        assertTrue(decision.rationale.contains("subscription CLI"));
        assertFalse("Rationale must never leak secrets", decision.rationale.contains("vault_"));
    }

    @Test
    public void filtersModelsByToolSupport() {
        SmartRouter router = new SmartRouter();

        ModelDescriptor noTools = new ModelDescriptor("model-chat", "Chat Only", 32000, false, false, false, "Available");
        ModelDescriptor withTools = new ModelDescriptor("model-agent", "Agent Tools", 128000, false, true, false, "Available");

        ProviderConnection conn = new ProviderConnection(
                "conn-tools", "custom", "Custom LLM", AuthStrategy.LOCAL,
                ConnectionStatus.CONNECTED, "", "model-chat", null,
                null, null, null, null, Arrays.asList(noTools, withTools), System.currentTimeMillis()
        );

        SmartRouter.RouteDecision decision = router.selectRoute(Collections.singletonList(conn), true, false, true);

        assertNotNull(decision);
        assertEquals("model-agent", decision.selectedModel);
    }

    @Test
    public void returnsNullWhenNoConnectionsConnected() {
        SmartRouter router = new SmartRouter();

        ProviderConnection disconnected = new ProviderConnection(
                "conn-disc", "anthropic", "Claude Disconnected", AuthStrategy.OFFICIAL_CLI,
                ConnectionStatus.DISCONNECTED, "", "claude-sonnet", null,
                null, null, null, null, null, System.currentTimeMillis()
        );

        SmartRouter.RouteDecision decision = router.selectRoute(Collections.singletonList(disconnected), false, false, true);
        assertNull(decision);
    }
}
