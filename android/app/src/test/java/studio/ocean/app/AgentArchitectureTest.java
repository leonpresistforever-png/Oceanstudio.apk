package studio.ocean.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Guards the boundary between conversation transport and terminal execution. */
public final class AgentArchitectureTest {
    private String source(String name) throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        Path file=root.resolve("src/main/java/studio/ocean/app/").resolve(name);
        if(!Files.isRegularFile(file))file=root.resolve("app/src/main/java/studio/ocean/app/").resolve(name);
        return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
    }
    private String projectFile(String name) throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        Path file=root.resolve(name);
        if(!Files.isRegularFile(file))file=root.resolve("app").resolve(name);
        return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
    }
    @Test public void agentCommandsUseTerminalRuntimeServiceNotProcessBuilder() throws Exception {
        String runner=source("OceanAgentRunner.java");
        assertTrue(runner.contains("OceanTerminalRuntimeService"));
        assertTrue(runner.contains("requestCommand(command"));
        assertTrue(runner.contains("OceanAgentConversation"));
        assertFalse(runner.contains("new ProcessBuilder"));
        assertFalse(runner.contains("/system/bin/sh"));
    }
    @Test public void modelOutputIsNotParsedAsImplicitShellScript() throws Exception {
        String runner=source("OceanAgentRunner.java");
        assertFalse(runner.contains("Internal Execution Result"));
        assertFalse(runner.contains("Pattern.compile"));
    }
    @Test public void byokRequiresARealVerifiedEncryptedConfiguration() throws Exception {
        String manager=source("OceanByokManager.java");
        String activity=source("MainActivity.java");
        assertTrue(manager.contains("AndroidKeyStore"));
        assertTrue(manager.contains("AES/GCM/NoPadding"));
        assertTrue(manager.contains("isVerified()"));
        assertTrue(activity.contains("Save & Test Connection"));
        assertFalse(manager.contains("Gemini 3.8"));
    }
    @Test public void controlCenterAndAppProfilesAreWiredIntoRuntime() throws Exception {
        String runner=source("OceanAgentRunner.java");
        String main=source("MainActivity.java");
        String settings=source("OceanAgentSettings.java");
        String root=projectFile("src/main/java/studio/ocean/app/device/DeviceControlService.java");
        String policy=projectFile("src/main/java/studio/ocean/app/device/AppAccessPolicy.java");
        assertTrue(runner.contains("agentSettings.signature()"));
        assertTrue(runner.contains("pluginConnected"));
        assertTrue(settings.contains("reasoningEffort()"));
        assertTrue(main.contains("openAgentControls()"));
        assertTrue(main.contains("PluginCenterActivity.class"));
        assertTrue(main.contains("OceanForgeActivity.class"));
        assertTrue(runner.contains("ocean-forge"));
        assertTrue(runner.contains("pluginConnected(\"forge\")"));
        assertTrue(root.contains("AppAccessPolicy"));
        assertTrue(root.contains("screenshotAllowed"));
        assertTrue(policy.contains("restrictionEnabled()"));
    }

    @Test public void debugOnlyAuthBypassIsExplicitlyBuildScoped() throws Exception {
        String gradle=projectFile("build.gradle");
        assertTrue(gradle.contains("debug {\n            // Debug APKs always expose"));
        assertTrue(gradle.contains("buildConfigField \"boolean\", \"OCEAN_DEV_AUTH_BYPASS\", \"true\""));
        assertTrue(gradle.contains("release {\n            // Hard-disabled"));
        assertTrue(gradle.contains("buildConfigField \"boolean\", \"OCEAN_DEV_AUTH_BYPASS\", \"false\""));
    }
}
