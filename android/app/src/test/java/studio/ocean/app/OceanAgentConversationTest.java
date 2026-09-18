package studio.ocean.app;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class OceanAgentConversationTest {
    private static JSONObject json(String text) throws Exception { return new JSONObject(text); }
    private static JSONObject gemini(String parts) throws Exception { return json("{candidates:[{content:{role:'model',parts:" + parts + "},finishReason:'STOP'}]}"); }
    private static JSONObject success() throws Exception { return json("{output:'pip 24.3 from Ocean',exit_code:0}"); }

    @Test public void geminiCallsNativeToolAndReceivesRealResultWithSignatureAndId() throws Exception {
        List<JSONObject> requests = new ArrayList<>(); AtomicInteger calls = new AtomicInteger();
        String answer = new OceanAgentConversation("google", "gemini-2.5-flash").run("Run terminal commands pip", body -> {
            requests.add(new JSONObject(body.toString()));
            if (requests.size() == 1) {
                assertEquals("run_terminal_command", body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0).getString("name"));
                JSONObject schema = body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0).getJSONObject("parameters");
                assertEquals("OBJECT", schema.getString("type"));
                assertEquals("STRING", schema.getJSONObject("properties").getJSONObject("command").getString("type"));
                assertFalse(body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(1).has("parameters"));
                return gemini("[{text:'Checking pip.'},{thoughtSignature:'opaque-signature',functionCall:{id:'call-7',name:'run_terminal_command',args:{command:'pip --version'}}}]");
            }
            JSONArray contents = body.getJSONArray("contents");
            JSONObject signed = contents.getJSONObject(1).getJSONArray("parts").getJSONObject(1);
            assertEquals("opaque-signature", signed.getString("thoughtSignature"));
            JSONObject result = contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse");
            assertEquals("call-7", result.getString("id"));
            assertEquals(0, result.getJSONObject("response").getInt("exit_code"));
            assertEquals("pip 24.3 from Ocean", result.getJSONObject("response").getString("output"));
            return gemini("[{thought:true,text:'private reasoning'},{text:'pip 24.3'},{text:' is installed.'}]");
        }, (name, args) -> { calls.incrementAndGet(); assertEquals("run_terminal_command", name); assertEquals("pip --version", args.getString("command")); return success(); }, status -> {});
        assertEquals("pip 24.3 is installed.", answer); assertEquals(1, calls.get()); assertEquals(2, requests.size());
    }

    @Test public void geminiNoArgumentToolWorksWithoutArgsField() throws Exception {
        AtomicInteger network = new AtomicInteger(), executed = new AtomicInteger();
        new OceanAgentConversation("google", "test").run("Open the terminal", request -> {
            if (network.getAndIncrement() == 0) return gemini("[{functionCall:{name:'open_terminal'}}]");
            assertTrue(request.getJSONArray("contents").getJSONObject(2).getJSONArray("parts").getJSONObject(0)
                    .getJSONObject("functionResponse").getJSONObject("response").getBoolean("opened"));
            return gemini("[{text:'Opened'}]");
        }, (name, args) -> { assertEquals("open_terminal", name); assertEquals(0, args.length()); executed.incrementAndGet(); return json("{opened:true,exit_code:0}"); }, status -> {});
        assertEquals(1, executed.get());
    }

    @Test public void plainTextAndCodeFencesNeverExecute() throws Exception {
        String response = "Opening terminal. ```bash rm -rf example ```";
        String answer = new OceanAgentConversation("google", "gemini-2.5-flash").run("Explain a shell", request ->
                gemini(new JSONArray().put(new JSONObject().put("text", response)).toString()),
                (name, args) -> { fail("Prose must never become executable"); return null; }, status -> {});
        assertEquals(response, answer);
    }

    @Test public void twoGeminiCallsReturnOneResultBlockContainingBothIds() throws Exception {
        AtomicInteger network = new AtomicInteger(), executed = new AtomicInteger();
        new OceanAgentConversation("google", "test-model").run("Check tools", request -> {
            if (network.getAndIncrement() == 0) return gemini("[{functionCall:{id:'a',name:'run_terminal_command',args:{command:'pwd'}}},{functionCall:{id:'b',name:'run_terminal_command',args:{command:'ls'}}}]");
            JSONArray parts = request.getJSONArray("contents").getJSONObject(2).getJSONArray("parts");
            assertEquals(2, parts.length()); assertEquals("b", parts.getJSONObject(1).getJSONObject("functionResponse").getString("id"));
            return gemini("[{text:'Done'}]");
        }, (name, args) -> { executed.incrementAndGet(); return success(); }, status -> {});
        assertEquals(2, executed.get());
    }

    @Test public void toolFailureReachesClaudeAsErrorWithOriginalToolUseId() throws Exception {
        AtomicInteger network = new AtomicInteger();
        new OceanAgentConversation("anthropic", "claude-test").run("Run a check", request -> {
            if (network.getAndIncrement() == 0) {
                assertTrue(request.getJSONArray("tools").getJSONObject(0).has("input_schema"));
                return json("{content:[{type:'thinking',thinking:'internal',signature:'opaque'},{type:'tool_use',id:'use-1',name:'run_terminal_command',input:{command:'false'}}]}");
            }
            JSONArray messages = request.getJSONArray("messages");
            assertEquals("opaque", messages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("signature"));
            JSONObject result = messages.getJSONObject(2).getJSONArray("content").getJSONObject(0);
            assertEquals("use-1", result.getString("tool_use_id")); assertTrue(result.getBoolean("is_error"));
            assertEquals(2, new JSONObject(result.getString("content")).getInt("exit_code"));
            return json("{content:[{type:'text',text:'The command failed with exit 2.'}]}");
        }, (name, args) -> json("{exit_code:2,output:'actual failure'}"), status -> {});
    }

    @Test public void openAiCompatibleCallsUseToolRoleAndExactIds() throws Exception {
        AtomicInteger network = new AtomicInteger();
        new OceanAgentConversation("custom", "provider/model").run("Check path", request -> {
            if (network.getAndIncrement() == 0) {
                assertEquals("function", request.getJSONArray("tools").getJSONObject(0).getString("type"));
                return json("{choices:[{message:{role:'assistant',content:null,tool_calls:[{id:'call-id',type:'function',function:{name:'run_terminal_command',arguments:'{\"command\":\"pwd\"}'}}]}}]}");
            }
            JSONObject result = request.getJSONArray("messages").getJSONObject(3);
            assertEquals("tool", result.getString("role")); assertEquals("call-id", result.getString("tool_call_id"));
            assertEquals(0, new JSONObject(result.getString("content")).getInt("exit_code"));
            return json("{choices:[{message:{role:'assistant',content:'Path checked'}}]}");
        }, (name, args) -> success(), status -> {});
    }

    @Test public void malformedSecondOpenAiCallDoesNotExecuteFirst() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        try {
            new OceanAgentConversation("openai", "test").run("Check", request -> json("{choices:[{message:{role:'assistant',tool_calls:[{id:'a',type:'function',function:{name:'run_terminal_command',arguments:'{\"command\":\"pwd\"}'}},{id:'b',type:'function',function:{name:'run_terminal_command',arguments:'broken JSON'}}]}}]}"),
                    (name, args) -> { executed.incrementAndGet(); return success(); }, status -> {});
            fail("Malformed calls must be rejected");
        } catch (org.json.JSONException expected) { assertEquals(0, executed.get()); }
    }

    @Test public void unknownToolAndInvalidArgumentsDoNotReachExecutor() throws Exception {
        for (String call : new String[]{"{name:'erase_everything',args:{}}", "{name:'run_terminal_command',args:{command:22}}", "{name:'run_terminal_command',args:{command:'pwd',timeout_seconds:301}}"}) {
            AtomicInteger network = new AtomicInteger();
            new OceanAgentConversation("google", "test").run("Check", request -> {
                if (network.getAndIncrement() == 0) return gemini("[{functionCall:" + call + "}]");
                assertTrue(request.getJSONArray("contents").getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse").getJSONObject("response").has("error"));
                return gemini("[{text:'Tool rejected'}]");
            }, (name, args) -> { fail("Invalid tool dispatch"); return null; }, status -> {});
        }
    }

    @Test public void oversizedCallBatchIsRejectedBeforeSideEffects() throws Exception {
        JSONArray parts = new JSONArray();
        for (int i = 0; i < OceanAgentConversation.MAX_TOOL_CALLS + 1; i++) parts.put(json("{functionCall:{name:'run_terminal_command',args:{command:'pwd'}}}"));
        try {
            new OceanAgentConversation("google", "test").run("Check", request -> gemini(parts.toString()),
                    (name, args) -> { fail("Over-budget batch executed"); return null; }, status -> {});
            fail("Expected budget error");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("limit")); }
    }

    @Test public void runtimeToolsAreDeclaredAndValidatedBeforeExecution() throws Exception {
        JSONObject request = new OceanAgentConversation("google", "gemini-2.5-flash").request(new JSONArray(), true);
        JSONArray tools = request.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations");
        assertEquals(11, tools.length());
        assertEquals("list_runtime_ports", tools.getJSONObject(2).getString("name"));
        assertFalse(tools.getJSONObject(2).has("parameters"));
        assertEquals("open_runtime_port", tools.getJSONObject(3).getString("name"));
        assertEquals("INTEGER", tools.getJSONObject(3).getJSONObject("parameters").getJSONObject("properties").getJSONObject("port").getString("type"));
        OceanAgentConversation.validateTool("open_runtime_port", json("{port:6080,path:'/vnc.html'}"));
        OceanAgentConversation.validateTool("interact_runtime_page", json("{action:'click',x:40,y:80}"));
        for (JSONObject invalid : new JSONObject[]{json("{port:0}"), json("{port:6080,path:'http://outside'}")}) {
            try { OceanAgentConversation.validateTool("open_runtime_port", invalid); fail("Invalid local target accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { OceanAgentConversation.validateTool("interact_runtime_page", json("{action:'click'}")); fail("Coordinate-free click accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void runtimeScreenshotReachesGeminiAsImageWithoutDuplicatingBase64InFunctionJson() throws Exception {
        AtomicInteger network = new AtomicInteger();
        new OceanAgentConversation("google", "gemini-2.5-flash").run("Inspect the desktop", request -> {
            if (network.getAndIncrement() == 0) return gemini("[{functionCall:{id:'shot',name:'interact_runtime_page',args:{action:'screenshot'}}}]");
            JSONArray parts = request.getJSONArray("contents").getJSONObject(2).getJSONArray("parts");
            assertEquals(2, parts.length());
            JSONObject response = parts.getJSONObject(0).getJSONObject("functionResponse").getJSONObject("response");
            assertFalse(response.has("image_base64"));
            assertEquals("abc123", parts.getJSONObject(1).getJSONObject("inlineData").getString("data"));
            return gemini("[{text:'Desktop inspected'}]");
        }, (name, args) -> json("{image_base64:'abc123',media_type:'image/jpeg',image_width:720,image_height:1000,exit_code:0}"), status -> {});
    }

    @Test public void runtimeScreenshotUsesClaudeImageToolResult() throws Exception {
        AtomicInteger network = new AtomicInteger();
        new OceanAgentConversation("anthropic", "claude-test").run("Inspect", request -> {
            if (network.getAndIncrement() == 0) return json("{content:[{type:'tool_use',id:'shot',name:'interact_runtime_page',input:{action:'screenshot'}}]}");
            JSONObject toolResult = request.getJSONArray("messages").getJSONObject(2).getJSONArray("content").getJSONObject(0);
            JSONArray content = toolResult.getJSONArray("content");
            assertEquals("image", content.getJSONObject(0).getString("type"));
            assertEquals("abc123", content.getJSONObject(0).getJSONObject("source").getString("data"));
            assertFalse(content.getJSONObject(1).getString("text").contains("abc123"));
            return json("{content:[{type:'text',text:'Inspected'}]}");
        }, (name, args) -> json("{image_base64:'abc123',media_type:'image/jpeg',exit_code:0}"), status -> {});
    }

    @Test public void runtimeScreenshotUsesOpenAiUserImageAfterToolResult() throws Exception {
        AtomicInteger network = new AtomicInteger();
        new OceanAgentConversation("openai", "vision-test").run("Inspect", request -> {
            if (network.getAndIncrement() == 0) return json("{choices:[{message:{role:'assistant',content:null,tool_calls:[{id:'shot',type:'function',function:{name:'interact_runtime_page',arguments:'{\"action\":\"screenshot\"}'}}]}}]}");
            JSONArray messages = request.getJSONArray("messages");
            assertEquals("tool", messages.getJSONObject(3).getString("role"));
            assertFalse(messages.getJSONObject(3).getString("content").contains("abc123"));
            assertTrue(messages.getJSONObject(4).getJSONArray("content").getJSONObject(0).getJSONObject("image_url").getString("url").endsWith("abc123"));
            return json("{choices:[{message:{role:'assistant',content:'Inspected'}}]}");
        }, (name, args) -> json("{image_base64:'abc123',media_type:'image/jpeg',exit_code:0}"), status -> {});
    }

    @Test public void repetitiveModelIsStoppedAtRoundBudget() throws Exception {
        AtomicInteger network = new AtomicInteger(), executed = new AtomicInteger();
        try {
            new OceanAgentConversation("google", "test").run("Check", request -> { network.incrementAndGet(); return gemini("[{functionCall:{name:'run_terminal_command',args:{command:'pwd'}}}]"); },
                    (name, args) -> { executed.incrementAndGet(); return success(); }, status -> {});
            fail("Unbounded tool loop");
        } catch (IOException expected) { assertEquals(OceanAgentConversation.MAX_ROUNDS, network.get()); assertTrue(executed.get() <= OceanAgentConversation.MAX_TOOL_CALLS); }
    }

    @Test public void connectionTestNeverAdvertisesToolsOrExecutes() throws Exception {
        for (String provider : new String[]{"google", "anthropic", "openai"}) {
            new OceanAgentConversation(provider, "test").testConnection(request -> {
                assertFalse(request.has("tools"));
                if (provider.equals("google")) return gemini("[{text:'OCEAN_CONNECTION_OK'}]");
                if (provider.equals("anthropic")) return json("{content:[{type:'text',text:'OCEAN_CONNECTION_OK'}]}");
                return json("{choices:[{message:{role:'assistant',content:'OCEAN_CONNECTION_OK'}}]}");
            });
        }
    }

    @Test public void followupContainsPreviousTurnAndEmptyCandidatesAreErrors() throws Exception {
        OceanAgentConversation conversation = new OceanAgentConversation("google", "test");
        conversation.run("Hello", request -> gemini("[{text:'Hello back'}]"), (n, a) -> null, s -> {});
        conversation.run("Remember?", request -> {
            assertEquals(3, request.getJSONArray("contents").length());
            assertEquals("Hello back", request.getJSONArray("contents").getJSONObject(1).getJSONArray("parts").getJSONObject(0).getString("text"));
            return gemini("[{text:'Yes'}]");
        }, (n, a) -> null, s -> {});
        try { conversation.run("Blocked", request -> json("{candidates:[]}"), (n,a) -> null, s -> {}); fail("Expected empty response error"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("no candidate")); }
    }

    @Test public void configuredGenerationAndReasoningSettingsReachProviderRequests() throws Exception {
        OceanAgentConversation google=new OceanAgentConversation("google","gemini-3.1-pro-preview",0.2f,0.8f,4096,8,12,false,"high","Keep replies concise.");
        JSONObject g=google.request(new JSONArray(),false);
        JSONObject generation=g.getJSONObject("generationConfig");
        assertEquals(0.2, generation.getDouble("temperature"), 0.0001);
        assertEquals(0.8, generation.getDouble("topP"), 0.0001);
        assertEquals(4096, generation.getInt("maxOutputTokens"));
        assertEquals("high",generation.getJSONObject("thinkingConfig").getString("thinkingLevel"));
        assertTrue(g.getJSONObject("systemInstruction").toString().contains("Keep replies concise."));

        JSONObject a=new OceanAgentConversation("anthropic","claude-test",0.3f,0.9f,5000,8,12,true,"medium","").request(new JSONArray(),false);
        assertEquals("adaptive",a.getJSONObject("thinking").getString("type"));
        assertEquals("medium",a.getJSONObject("output_config").getString("effort"));

        JSONObject o=new OceanAgentConversation("openai","reasoning-test",1f,1f,2048,8,12,true,"low","").request(new JSONArray(),false);
        assertEquals("low",o.getString("reasoning_effort"));
    }

    @Test public void disabledSessionContextDoesNotReplayPreviousTurn() throws Exception {
        OceanAgentConversation conversation=new OceanAgentConversation("google","test",1f,1f,2048,8,12,false,"default","");
        conversation.run("First",request->gemini("[{text:'One'}]"),(n,a)->null,s->{});
        conversation.run("Second",request->{assertEquals(1,request.getJSONArray("contents").length());return gemini("[{text:'Two'}]");},(n,a)->null,s->{});
    }

    @Test public void deviceToolsRejectInvalidTargetsBeforeExecution() throws Exception {
        OceanAgentConversation.validateTool("open_android_app",json("{package_name:'com.android.settings'}"));
        OceanAgentConversation.validateTool("interact_android_screen",json("{action:'swipe',x:10,y:20,to_x:30,to_y:40}"));
        OceanAgentConversation.validateTool("interact_android_screen",json("{action:'long_press',x:10,y:20}"));
        OceanAgentConversation.validateTool("interact_android_screen",json("{action:'recents'}"));
        OceanAgentConversation.validateTool("interact_android_screen",json("{action:'notifications'}"));
        OceanAgentConversation.validateTool("interact_android_screen",json("{action:'quick_settings'}"));
        for(String value:new String[]{"{action:'tap',x:-1,y:2}","{action:'swipe',x:1,y:2}","{action:'type',ref:'1:1'}","{action:'unknown'}"}){
            try{OceanAgentConversation.validateTool("interact_android_screen",json(value));fail("Invalid device action accepted");}catch(IllegalArgumentException expected){}
        }
        try{OceanAgentConversation.validateTool("open_android_app",json("{package_name:'browser'}"));fail("Ambiguous app accepted");}catch(IllegalArgumentException expected){}
    }
}
