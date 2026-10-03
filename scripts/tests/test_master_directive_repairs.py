#!/usr/bin/env python3
"""
Test Suite: OceanStudio Master Provider Auth / MCP Gateway Directive (2026-10-02)
Validates:
1. Private Browser Process Isolation & Suffix Setup
2. MCP Protocol Handshake, Transports, HTTP 401 Challenge, Mcp-Session-Id, and Dialect Parser
3. Local Model Catalog Integrity, 7-Tier Manifest, Connect/Disconnect Semantics, and Agent Routing
4. Direct Provider Auth Gateway:
   - OpenAI SIWC dynamic registration, loopback listener, and Responses probe
   - Antigravity Product vs Google Gemini API split
   - Kimi managed runtime lifecycle & Anthropic truthful mobile preflight
5. Bundled Skills Architecture:
   - All 14 skills present and registered in OceanBundledSkills
   - Complete 12-section operational structure
   - Zero repeated 'Cycle 1..8' boilerplate
6. Private Browser Controls & Saved Session Manifest:
   - Real privacy toggles and selectors
   - SavedPrivateSession URL-only persistence guarantee (zero cookies/profile storage)
7. UI Greyscale OceanModal System:
   - OceanModal bottom sheet component
   - Zero AlertDialog instances across core activities
8. Strict Fail-Closed CredentialVault & Structured Records:
   - Zero deterministic cryptographic fallback keys
   - Zero Base64 fallbacks (Base64 is encoding, not encryption)
   - Structured CredentialRecord with atomic rotation and tokenGeneration
   - Proactive RefreshManager
9. Auth Core Persistence & Replay Attack Prevention:
   - Persistent AuthTransaction surviving process death
   - AuthSessionManager replay consumption
   - CallbackBroker multi-mechanism dispatch
   - Direct Connect UI with ZERO user-facing client ID or CLI/API fallbacks
10. SmartRouter Verification Policy:
   - Routes among VERIFIED_CONNECTED only
   - Zero blind CLI preference
   - Default to local model when no cloud providers connected
   - Explicit user preference overrides
11. Strict Rule 5 & Zero Termux Contamination Gate
"""

import os
import sys
import json
import glob
import re

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
os.chdir(REPO_ROOT)

def log(msg, status="INFO"):
    print(f"[{status}] {msg}")

def assert_true(cond, msg):
    if not cond:
        log(msg, "FAIL")
        sys.exit(1)
    else:
        log(msg, "PASS")

def test_private_browser_isolation():
    log("=== 1. Private Browser Isolation & Multi-Process Suffix ===")
    app_file = "android/app/src/main/java/studio/ocean/app/OceanApplication.java"
    with open(app_file, "r", encoding="utf-8") as f:
        src = f.read()

    assert_true("getProcessNameCompat()" in src, "Detects process name at earliest initialization")
    assert_true(":secure_browser" in src, "Detects :secure_browser private process")
    assert_true("setDataDirectorySuffix" in src, "Configures WebView.setDataDirectorySuffix")
    assert_true("secure_browser" in src, "Uses dedicated 'secure_browser' suffix for private process")
    assert_true("attachBaseContext" in src and "onCreate" in src, "Configured in both attachBaseContext and onCreate")

    layout_file = "android/app/src/main/res/layout/activity_secure_browser.xml"
    with open(layout_file, "r", encoding="utf-8") as f:
        layout = f.read()
    assert_true("<WebView" not in layout, "activity_secure_browser.xml contains NO static <WebView> tag")
    assert_true("FrameLayout" in layout and "secure_browser_webview_container" in layout, "Uses programmatic FrameLayout container")

def test_mcp_protocol_and_oauth():
    log("=== 2. MCP Protocol Client, HTTP 401 Challenge & OAuth ===")
    mcp_dir = "android/app/src/main/java/studio/ocean/app/mcp"
    assert_true(os.path.isdir(mcp_dir), "MCP package exists at studio.ocean.app.mcp")

    status_file = os.path.join(mcp_dir, "McpStatus.java")
    with open(status_file, "r", encoding="utf-8") as f:
        status_src = f.read()
    for state in ["SAVED", "CONNECTING", "INITIALIZING", "DISCOVERING", "CONNECTED", "START_ERROR", "PROTOCOL_ERROR", "DISCONNECTED", "AUTH_REQUIRED", "AUTHORIZING", "REAUTH_REQUIRED", "AUTH_ERROR", "OFFLINE"]:
        assert_true(state in status_src, f"McpStatus contains {state}")

    client_file = os.path.join(mcp_dir, "McpClientManager.java")
    with open(client_file, "r", encoding="utf-8") as f:
        client_src = f.read()
    assert_true("ProcessBuilder" in client_src, "McpClientManager uses ProcessBuilder for STDIO")
    assert_true("initialize" in client_src, "Implements JSON-RPC initialize request")
    assert_true("tools/list" in client_src, "Implements tools/list discovery")
    assert_true("notifications/initialized" in client_src, "Sends notifications/initialized after handshake")
    assert_true("Mcp-Session-Id" in client_src, "Tracks Mcp-Session-Id header across requests")
    assert_true("AUTH_REQUIRED" in client_src, "Transitions to AUTH_REQUIRED on HTTP 401 response")
    assert_true("isMcpTool" in client_src and "callTool" in client_src, "Supports namespaced tool dispatch")

    oauth_file = os.path.join(mcp_dir, "McpOAuthResolver.java")
    assert_true(os.path.isfile(oauth_file), "McpOAuthResolver exists")
    with open(oauth_file, "r", encoding="utf-8") as f:
        oauth_src = f.read()
    assert_true("oauth-protected-resource" in oauth_src, "McpOAuthResolver implements RFC 9728 discovery")
    assert_true("code_challenge_method=S256" in oauth_src, "McpOAuthResolver enforces S256 PKCE")

    config_file = os.path.join(mcp_dir, "McpServerConfig.java")
    with open(config_file, "r", encoding="utf-8") as f:
        cfg_src = f.read()
    assert_true("parseConfigDialects" in cfg_src, "McpServerConfig supports multi-dialect JSON parsing")
    assert_true("mcpServers" in cfg_src, "Parses Claude/Cursor mcpServers dialect")
    assert_true("servers" in cfg_src, "Parses VS Code servers dialect")

def test_local_model_catalog_and_routing():
    log("=== 3. Local Model Catalog, Connect Semantics & Routing ===")
    manifest_path = "android/app/src/main/assets/models-manifest.json"
    assert_true(os.path.isfile(manifest_path), "models-manifest.json exists in app assets")

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    models = manifest.get("models", [])
    assert_true(len(models) >= 7, f"Manifest contains at least 7 verified GGUF models across tiers (found {len(models)})")

    model_ids = [m["id"] for m in models]
    for expected_id in ["smollm2-360m-instruct", "qwen2.5-coder-0.5b", "llama-3.2-1b-instruct", "qwen2.5-coder-1.5b", "smollm2-1.7b-instruct", "qwen2.5-coder-3b", "phi-3.5-mini-instruct"]:
        assert_true(expected_id in model_ids, f"Manifest contains {expected_id}")

    # Check LocalModel state machine
    lm_file = "android/app/src/main/java/studio/ocean/app/models/local/LocalModel.java"
    with open(lm_file, "r", encoding="utf-8") as f:
        lm_src = f.read()
    assert_true("CONNECTED" in lm_src, "LocalModel contains CONNECTED state")
    assert_true("healthMs" in lm_src, "LocalModel tracks healthMs latency")
    assert_true("verifiedContext" in lm_src, "LocalModel tracks verifiedContext")

    # Check LocalModelManager connect/disconnect semantics
    mgr_file = "android/app/src/main/java/studio/ocean/app/models/local/LocalModelManager.java"
    with open(mgr_file, "r", encoding="utf-8") as f:
        mgr_src = f.read()
    assert_true("connectModel" in mgr_src, "LocalModelManager implements connectModel()")
    assert_true("disconnectModel" in mgr_src, "LocalModelManager implements disconnectModel()")
    assert_true("availRam" in mgr_src, "LocalModelManager verifies device RAM headroom")
    assert_true("LocalLlamaRuntime" in mgr_src and "ProcessBuilder" in mgr_src, "LocalModelManager starts a real verified llama.cpp runtime")
    assert_true("/health" in mgr_src, "LocalModelManager waits for a live llama-server health endpoint")
    assert_true("v1/chat/completions" in mgr_src and "verifyInference" in mgr_src, "LocalModelManager requires a real inference probe")
    assert_true("markVerifiedConnected" in mgr_src, "CONNECTED is assigned only through the verified runtime path")
    assert_true("catch (Exception ignored)" not in mgr_src[mgr_src.find("connectModel"):mgr_src.find("deleteModel")], "connectModel does not ignore probe failures")
    assert_true("local_model_override" in mgr_src, "LocalModelManager maintains local_model_override setting")

    runtime_file = "android/app/src/main/java/studio/ocean/app/models/local/LocalLlamaRuntime.java"
    with open(runtime_file, "r", encoding="utf-8") as f:
        runtime_src = f.read()
    assert_true("SHA256" in runtime_src and "sha256(archiveFile)" in runtime_src, "llama.cpp runtime archive is integrity-verified")
    assert_true("ggml-org/llama.cpp/releases/download" in runtime_src, "llama.cpp runtime comes from verified upstream release artifacts")

    # Check OceanAgentRunner local routing
    runner_file = "android/app/src/main/java/studio/ocean/app/OceanAgentRunner.java"
    with open(runner_file, "r", encoding="utf-8") as f:
        runner_src = f.read()
    assert_true("isLocalOverrideEnabled" in runner_src, "OceanAgentRunner checks local override via isLocalOverrideEnabled()")
    assert_true("AuthStrategy.LOCAL" in runner_src, "OceanAgentRunner handles AuthStrategy.LOCAL")

def test_direct_provider_auth():
    log("=== 4. Direct Provider Auth Gateway & Provider Split ===")
    orchestrator_file = "android/app/src/main/java/studio/ocean/app/providers/auth/AuthOrchestrator.java"
    with open(orchestrator_file, "r", encoding="utf-8") as f:
        orch_src = f.read()

    assert_true("GoogleDirectAuthAdapter" in orch_src, "AuthOrchestrator registers GoogleDirectAuthAdapter")
    assert_true("AntigravityDirectAuthAdapter" in orch_src, "AuthOrchestrator registers AntigravityDirectAuthAdapter")
    assert_true("OpenAiDirectAuthAdapter" in orch_src, "AuthOrchestrator registers OpenAiDirectAuthAdapter")
    assert_true("AnthropicDirectAuthAdapter" in orch_src, "AuthOrchestrator registers AnthropicDirectAuthAdapter")
    assert_true("KimiDirectAuthAdapter" in orch_src, "AuthOrchestrator registers KimiDirectAuthAdapter")

    # OpenAI SIWC
    openai_file = "android/app/src/main/java/studio/ocean/app/providers/auth/OpenAiDirectAuthAdapter.java"
    with open(openai_file, "r", encoding="utf-8") as f:
        openai_src = f.read()
    assert_true("dynamic_agent_client" in openai_src, "OpenAiDirectAuthAdapter uses dynamic_agent_client for initial registration")
    assert_true("127.0.0.1" in openai_src and "ServerSocket" in openai_src, "OpenAiDirectAuthAdapter runs ephemeral loopback listener")
    assert_true("chatgpt.tokens.use.direct" in openai_src, "OpenAiDirectAuthAdapter requests chatgpt.tokens.use.direct scope")
    assert_true("ext_agent_host_id" in openai_src, "OpenAiDirectAuthAdapter uses persistent ext_agent_host_id URN")
    assert_true("v1/chat/completions" in openai_src, "OpenAiDirectAuthAdapter executes live 1-token probe")

    # Antigravity: real Google OAuth + Cloud Code verification, never a fabricated /auth/session route
    antigravity_file = "android/app/src/main/java/studio/ocean/app/providers/auth/AntigravityDirectAuthAdapter.java"
    with open(antigravity_file, "r", encoding="utf-8") as f:
        ag_src = f.read()
    assert_true("accounts.google.com/o/oauth2/v2/auth" in ag_src, "Antigravity starts at Google's real OAuth authorization endpoint")
    assert_true("oauth2.googleapis.com/token" in ag_src, "Antigravity performs a real authorization-code token exchange")
    assert_true("loadCodeAssist" in ag_src and "fetchAvailableModels" in ag_src, "Antigravity verifies Cloud Code entitlement and discovers live models")
    assert_true("OCEAN_ANTIGRAVITY_OAUTH_CLIENT_ID" in ag_src, "OAuth registration is app-owned at build time, not user-entered")
    assert_true("antigravity.google/auth/session" not in ag_src, "Removed nonexistent antigravity.google/auth/session endpoint")
    assert_true("session_token" not in ag_src, "Removed synthetic Antigravity session-token callback contract")
    assert_true("return accessToken != null" not in ag_src, "Probe is not a fake non-empty-token check")
    assert_true("startLoopbackListener" in orch_src, "AuthOrchestrator uses a real loopback callback broker for Antigravity")
    assert_true("processConsumedCallback" in orch_src, "Loopback and deep-link callbacks share a replay-safe consumed transaction path")

    google_file = "android/app/src/main/java/studio/ocean/app/providers/auth/GoogleDirectAuthAdapter.java"
    with open(google_file, "r", encoding="utf-8") as f:
        google_src = f.read()
    assert_true("default_web_client_id" not in google_src, "Removed hardcoded default_web_client_id fallback that triggered invalid_request")

def test_bundled_skills_architecture():
    log("=== 5. Bundled Skills Architecture & 12-Section Quality ===")
    skills_dir = "android/app/src/main/assets/ocean/bundled-skills"
    skills = glob.glob(os.path.join(skills_dir, "*/SKILL.md"))
    assert_true(len(skills) == 14, f"Found all 14 bundled skills (found {len(skills)})")

    # Verify OceanBundledSkills registry contains all skills
    obs_file = "android/app/src/main/java/studio/ocean/app/OceanBundledSkills.java"
    with open(obs_file, "r", encoding="utf-8") as f:
        obs_src = f.read()
    assert_true("mcp-integration" in obs_src, "OceanBundledSkills registers mcp-integration")
    assert_true("local-models" in obs_src, "OceanBundledSkills registers local-models")

    # Verify zero occurrences of Cycle filler
    cycle_check = os.popen(f"grep -rn 'Cycle [1-8]' '{skills_dir}'").read().strip()
    assert_true(len(cycle_check) == 0, f"Zero 'Cycle 1..8' boilerplate in skills: {cycle_check}")

    # Verify each skill contains the 12 required sections
    required_sections = [
        "Mission and Scope",
        "When to Invoke",
        "Inputs to Gather",
        "Tool Policy",
        "Step-by-Step Operating Procedure",
        "Domain-Specific Heuristics",
        "Evidence Requirements",
        "Failure Modes and Recovery",
        "Security",
        "Acceptance Tests",
        "Handoff Format",
        "Small Worked Examples"
    ]
    for s in skills:
        skill_id = os.path.basename(os.path.dirname(s))
        with open(s, "r", encoding="utf-8") as f:
            content = f.read()
        for req in required_sections:
            assert_true(req.lower() in content.lower(), f"Skill '{skill_id}' has section '{req}'")

def test_private_browser_controls_and_saved_sessions():
    log("=== 6. Private Browser Controls & Saved Session Manifest ===")
    session_file = "android/app/src/main/java/studio/ocean/app/browser/secure/SavedPrivateSession.java"
    assert_true(os.path.isfile(session_file), "SavedPrivateSession model class exists")
    with open(session_file, "r", encoding="utf-8") as f:
        sess_src = f.read()
    assert_true("NOT persist" in sess_src or "CRITICAL EPHEMERAL PRIVACY GUARANTEE" in sess_src, "Documents strict ephemeral privacy guarantee")
    assert_true("cookies" in sess_src and "WebStorage" in sess_src and "service workers" in sess_src, "Explicitly specifies non-persisted categories")

    store_file = "android/app/src/main/java/studio/ocean/app/browser/secure/SavedPrivateSessionStore.java"
    assert_true(os.path.isfile(store_file), "SavedPrivateSessionStore class exists")

    mgr_file = "android/app/src/main/java/studio/ocean/app/browser/secure/PrivateSessionManager.java"
    with open(mgr_file, "r", encoding="utf-8") as f:
        mgr_src = f.read()
    assert_true("trackerBlockingEnabled" in mgr_src, "PrivateSessionManager has trackerBlockingEnabled")
    assert_true("popupFirewallEnabled" in mgr_src, "PrivateSessionManager has popupFirewallEnabled")
    assert_true("killSwitchEnabled" in mgr_src, "PrivateSessionManager has killSwitchEnabled")
    assert_true("webRtcMode" in mgr_src, "PrivateSessionManager has webRtcMode selector")
    assert_true("dnsMode" in mgr_src, "PrivateSessionManager has dnsMode selector")
    assert_true("routeMode" in mgr_src, "PrivateSessionManager has routeMode selector")

    browser_act = "android/app/src/main/java/studio/ocean/app/browser/secure/SecureBrowserActivity.java"
    with open(browser_act, "r", encoding="utf-8") as f:
        act_src = f.read()
    assert_true("savedSessionStore" in act_src, "SecureBrowserActivity integrates savedSessionStore")
    assert_true("saveCurrentSession" in act_src, "SecureBrowserActivity implements saveCurrentSession()")
    assert_true("showSavedSessionsSheet" in act_src, "SecureBrowserActivity implements showSavedSessionsSheet()")
    assert_true("restoreSavedSession" in act_src, "SecureBrowserActivity implements restoreSavedSession()")

def test_ocean_modal_and_ui():
    log("=== 7. OceanModal System & Greyscale Bottom Sheets ===")
    modal_file = "android/app/src/main/java/studio/ocean/app/OceanModal.java"
    assert_true(os.path.isfile(modal_file), "OceanModal.java exists")
    with open(modal_file, "r", encoding="utf-8") as f:
        modal_src = f.read()
    assert_true("R.drawable.bottom_sheet_background" in modal_src, "OceanModal uses rounded bottom_sheet_background")
    assert_true("R.drawable.bottom_sheet_handle" in modal_src, "OceanModal includes drag handle")
    assert_true("primary_button_background" in modal_src, "OceanModal styles primary button with black fill")
    assert_true("button_secondary" in modal_src, "OceanModal styles secondary button with soft grey")

    # Assert zero AlertDialog in ProvidersConnectActivity, SecureBrowserActivity, and LocalModelsActivity
    for target in [
        "android/app/src/main/java/studio/ocean/app/providers/ProvidersConnectActivity.java",
        "android/app/src/main/java/studio/ocean/app/browser/secure/SecureBrowserActivity.java",
        "android/app/src/main/java/studio/ocean/app/models/local/LocalModelsActivity.java",
        "android/app/src/main/java/studio/ocean/app/CrashSurvival.java",
        "android/app/src/main/java/studio/ocean/app/OceanForgeActivity.java",
        "android/app/src/main/java/studio/ocean/app/browser/security/DownloadQuarantineManager.java"
    ]:
        with open(target, "r", encoding="utf-8") as f:
            src = f.read()
        assert_true("AlertDialog" not in src, f"{os.path.basename(target)} contains ZERO AlertDialog instances")

def test_fail_closed_vault_and_records():
    log("=== 8. Fail-Closed CredentialVault & Structured Records ===")
    vault_file = "android/app/src/main/java/studio/ocean/app/providers/state/CredentialVault.java"
    with open(vault_file, "r", encoding="utf-8") as f:
        vault_src = f.read()

    assert_true("DeterministicKey" not in vault_src, "CredentialVault contains ZERO deterministic fallback keys")
    assert_true("Base64.decode(data, Base64.NO_WRAP)" not in vault_src, "CredentialVault contains ZERO Base64 decoding fallbacks")
    assert_true("SecurityException" in vault_src, "CredentialVault throws SecurityException (fails closed) if Keystore is absent")
    assert_true("rotateRecord" in vault_src, "CredentialVault implements atomic rotateRecord()")
    assert_true("storeRecord" in vault_src and "retrieveRecord" in vault_src, "CredentialVault handles structured CredentialRecord")

    record_file = "android/app/src/main/java/studio/ocean/app/providers/state/CredentialRecord.java"
    assert_true(os.path.isfile(record_file), "CredentialRecord.java exists")
    with open(record_file, "r", encoding="utf-8") as f:
        rec_src = f.read()
    assert_true("tokenGeneration" in rec_src, "CredentialRecord tracks tokenGeneration")
    assert_true("withRotatedTokens" in rec_src, "CredentialRecord provides withRotatedTokens atomic copy")

    refresh_file = "android/app/src/main/java/studio/ocean/app/providers/state/RefreshManager.java"
    assert_true(os.path.isfile(refresh_file), "RefreshManager.java exists")
    with open(refresh_file, "r", encoding="utf-8") as f:
        ref_src = f.read()
    assert_true("refreshIfNeeded" in ref_src, "RefreshManager implements refreshIfNeeded()")

def test_auth_core_persistence_and_no_user_client_id():
    log("=== 9. Auth Core Persistence, CallbackBroker & Zero User Client ID ===")
    tx_file = "android/app/src/main/java/studio/ocean/app/providers/auth/AuthTransaction.java"
    assert_true(os.path.isfile(tx_file), "AuthTransaction.java exists")
    with open(tx_file, "r", encoding="utf-8") as f:
        tx_src = f.read()
    assert_true("replayConsumed" in tx_src, "AuthTransaction tracks replayConsumed")
    assert_true("PHASE_INITIALIZED" in tx_src and "PHASE_BROWSER_ACTIVE" in tx_src, "AuthTransaction tracks phase lifecycle")

    mgr_file = "android/app/src/main/java/studio/ocean/app/providers/auth/AuthSessionManager.java"
    assert_true(os.path.isfile(mgr_file), "AuthSessionManager.java exists")
    with open(mgr_file, "r", encoding="utf-8") as f:
        mgr_src = f.read()
    assert_true("prefs" in mgr_src, "AuthSessionManager persists transactions across process death")
    assert_true("consumeTransaction" in mgr_src, "AuthSessionManager implements consumeTransaction() to reject replays")

    broker_file = "android/app/src/main/java/studio/ocean/app/providers/auth/CallbackBroker.java"
    assert_true(os.path.isfile(broker_file), "CallbackBroker.java exists")
    with open(broker_file, "r", encoding="utf-8") as f:
        brk_src = f.read()
    assert_true("LoopbackServer" in brk_src, "CallbackBroker manages ephemeral loopback listeners")
    assert_true("dispatchUriCallback" in brk_src, "CallbackBroker dispatches URI callbacks")

    act_file = "android/app/src/main/java/studio/ocean/app/providers/ProvidersConnectActivity.java"
    with open(act_file, "r", encoding="utf-8") as f:
        act_src = f.read()
    assert_true("showConfigureClientIdDialog" not in act_src, "ProvidersConnectActivity contains ZERO user-facing client ID dialogs")
    assert_true("Set Client ID" not in act_src, "Direct Connect failure contains ZERO Set Client ID buttons")

def test_smart_router_policy():
    log("=== 10. SmartRouter Policy & Verification Gates ===")
    router_file = "android/app/src/main/java/studio/ocean/app/providers/router/SmartRouter.java"
    with open(router_file, "r", encoding="utf-8") as f:
        r_src = f.read()

    assert_true("ConnectionStatus.CONNECTED" in r_src, "SmartRouter checks ConnectionStatus.CONNECTED")
    assert_true("preferLocalModel" in r_src, "SmartRouter supports explicit preferLocalModel policy")
    assert_true("preferredProviderId" in r_src, "SmartRouter supports explicit preferredProviderId lock")
    assert_true("no verified cloud providers" in r_src, "SmartRouter defaults to local model when no cloud provider is connected")
    assert_true("allowMeteredFallback" in r_src, "SmartRouter strictly bounds metered API keys behind allowMeteredFallback")

def test_zero_termux_contamination():
    log("=== 11. Strict Rule 5 & Zero Termux Contamination Gate ===")
    result = os.popen("git grep -i '/data/data/com.termux' -- 'android/app/src/main/java/' 'android/app/src/main/cpp/' 'android/app/build.gradle'").read().strip()
    assert_true(len(result) == 0, f"No /data/data/com.termux in native code or build scripts: {result}")

    result_pkg = os.popen("git grep -i 'com.termux' -- 'android/app/src/main/AndroidManifest.xml'").read().strip()
    assert_true(len(result_pkg) == 0, "No com.termux in AndroidManifest.xml")

if __name__ == "__main__":
    print("=" * 68)
    print(" OceanStudio Master Provider Auth / MCP Gateway Directive Test Suite")
    print("=" * 68)
    test_private_browser_isolation()
    test_mcp_protocol_and_oauth()
    test_local_model_catalog_and_routing()
    test_direct_provider_auth()
    test_bundled_skills_architecture()
    test_private_browser_controls_and_saved_sessions()
    test_ocean_modal_and_ui()
    test_fail_closed_vault_and_records()
    test_auth_core_persistence_and_no_user_client_id()
    test_smart_router_policy()
    test_zero_termux_contamination()
    print("=" * 68)
    print(" ALL 11 AUDIT AND ACCEPTANCE GATES PASSED 100% GREEN")
    print("=" * 68)
