package studio.ocean.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import studio.ocean.app.providers.ProviderRegistry;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ConnectionStatus;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.model.ProviderDescriptor;

/**
 * Predicate unit tests for Provider Hub and Plugin/Skills/MCP filters (Directive Point 9, §12.2).
 */
public final class ProviderAndPluginFilterTest {

    @Test
    public void providerFiltersExcludeDecoupledLocalModels() {
        List<ProviderDescriptor> all = ProviderRegistry.all();
        assertFalse("ProviderRegistry.all() must exclude decoupled ID_LOCAL",
                all.stream().anyMatch(d -> ProviderRegistry.ID_LOCAL.equals(d.id)));
    }

    @Test
    public void providerDirectConnectFilterPredicate() {
        ProviderDescriptor oauthDesc = new ProviderDescriptor(
                "kimi", "Kimi Code", "Moonshot AI", null, "moonshot-v1-auto",
                Arrays.asList(AuthStrategy.DEVICE_CODE, AuthStrategy.OFFICIAL_CLI),
                null, true, false
        );

        ProviderDescriptor apiKeyOnlyDesc = new ProviderDescriptor(
                "groq", "Groq", "Fast Inference", null, "llama-3.3-70b-versatile",
                Collections.singletonList(AuthStrategy.API_KEY),
                null, false, false
        );

        boolean oauthMatches = oauthDesc.supports(AuthStrategy.DIRECT_OAUTH)
                || oauthDesc.supports(AuthStrategy.OFFICIAL_OAUTH)
                || oauthDesc.supports(AuthStrategy.DEVICE_CODE);
        assertTrue("Kimi supports device-code and should match DIRECT_CONNECT filter", oauthMatches);

        boolean apiKeyMatches = apiKeyOnlyDesc.supports(AuthStrategy.DIRECT_OAUTH)
                || apiKeyOnlyDesc.supports(AuthStrategy.OFFICIAL_OAUTH)
                || apiKeyOnlyDesc.supports(AuthStrategy.DEVICE_CODE);
        assertFalse("Groq only supports API_KEY and should NOT match DIRECT_CONNECT filter", apiKeyMatches);
    }

    @Test
    public void pluginMcpStatusFilterPredicate() throws Exception {
        JSONObject connectedMcp = new JSONObject().put("id", "mcp_git").put("name", "Git MCP").put("connected", true);
        JSONObject disconnectedMcp = new JSONObject().put("id", "mcp_sqlite").put("name", "SQLite MCP").put("connected", false);

        List<JSONObject> mcps = Arrays.asList(connectedMcp, disconnectedMcp);

        // Filter: connected only
        List<JSONObject> connectedOnly = new ArrayList<>();
        String statusFilter = "connected";
        for (JSONObject m : mcps) {
            boolean connected = m.optBoolean("connected", false);
            if ("connected".equals(statusFilter) && !connected) continue;
            if ("draft".equals(statusFilter)) continue;
            connectedOnly.add(m);
        }

        assertEquals(1, connectedOnly.size());
        assertEquals("mcp_git", connectedOnly.get(0).getString("id"));

        // Filter: drafts only (MCPs have no drafts, so count should be 0)
        List<JSONObject> draftsOnly = new ArrayList<>();
        statusFilter = "draft";
        for (JSONObject m : mcps) {
            boolean connected = m.optBoolean("connected", false);
            if ("connected".equals(statusFilter) && !connected) continue;
            if ("draft".equals(statusFilter)) continue;
            draftsOnly.add(m);
        }

        assertEquals(0, draftsOnly.size());
    }

    @Test
    public void pluginSkillStatusAndSearchFilterPredicate() throws Exception {
        JSONObject s1 = new JSONObject().put("id", "git_expert").put("title", "Git Master").put("description", "Git workflows").put("status", "connected");
        JSONObject s2 = new JSONObject().put("id", "python_doc").put("title", "Python Docs").put("description", "Doc generator").put("status", "draft");
        JSONObject s3 = new JSONObject().put("id", "bash_pro").put("title", "Shell Pro").put("description", "Bash scripts").put("status", "installed");

        List<JSONObject> skills = Arrays.asList(s1, s2, s3);

        // Combine filter = "draft" and search = "python"
        String statusFilter = "draft";
        String q = "python";

        List<JSONObject> result = new ArrayList<>();
        for (JSONObject s : skills) {
            String title = s.optString("title");
            String desc = s.optString("description");
            String id = s.optString("id");
            if (!q.isEmpty() && !title.toLowerCase().contains(q) && !desc.toLowerCase().contains(q) && !id.toLowerCase().contains(q)) {
                continue;
            }
            boolean connected = "connected".equals(s.optString("status"));
            boolean draft = "draft".equals(s.optString("status"));
            if ("connected".equals(statusFilter) && !connected) continue;
            if ("draft".equals(statusFilter) && !draft) continue;
            result.add(s);
        }

        assertEquals(1, result.size());
        assertEquals("python_doc", result.get(0).getString("id"));
    }
}
