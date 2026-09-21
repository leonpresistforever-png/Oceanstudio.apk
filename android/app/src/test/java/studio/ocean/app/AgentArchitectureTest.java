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
    private String repositoryFile(String name) throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for(int depth=0;depth<5&&root!=null;depth++,root=root.getParent()){
            Path file=root.resolve(name);
            if(Files.isRegularFile(file))return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        }
        throw new java.io.IOException("Repository file not found: "+name);
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
        assertFalse(main.contains("OceanForgeActivity.class"));
        assertTrue(runner.contains("ocean-forge"));
        assertFalse(runner.contains("pluginConnected(\"forge\")"));
        assertTrue(root.contains("AppAccessPolicy"));
        assertTrue(root.contains("screenshotAllowed"));
        assertTrue(policy.contains("restrictionEnabled()"));
    }

    @Test public void longForgeCommandsUseVisibleForegroundExecution() throws Exception {
        String service=projectFile("src/main/java/studio/ocean/app/terminal/OceanTerminalRuntimeService.java");
        String manifest=projectFile("src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"));
        assertTrue(service.contains("startForeground(TASK_NOTIFICATION_ID"));
        assertTrue(service.contains("leaveCommandForeground()"));
        assertTrue(service.contains("timeoutSeconds > 3600"));
        assertTrue(service.contains("taskWakeLock.acquire();"));
        assertTrue(service.contains("Ocean task running"));
    }

    @Test public void forgeCoreIsBundledAndWorkspaceIsConfined() throws Exception {
        String runner=source("OceanAgentRunner.java");
        String workspace=source("OceanForgeWorkspace.java");
        String installer=source("OceanForgeInstaller.java");
        assertTrue(runner.contains("OceanForgeInstaller.ensure(context)"));
        assertTrue(runner.contains("OceanForgeWorkspace.execute(context,args)"));
        assertTrue(workspace.contains("Forge paths must be relative"));
        assertTrue(workspace.contains("path escapes the workspace"));
        assertTrue(workspace.contains("Signing, credential, Git-internal"));
        assertTrue(installer.contains("ocean/forge/ocean-forge"));
        assertTrue(installer.contains("ocean/forge/source.zip"));
        assertTrue(runner.contains("bootstrap_sdk"));
        assertTrue(runner.contains("ensureSourceBundle(context)"));
    }


    @Test public void forgeBuildEmbedsTheNextSelfSourceSnapshot() throws Exception {
        String gradle=projectFile("build.gradle");
        assertTrue(gradle.contains("prepareForgeSourceBundle"));
        assertTrue(gradle.contains("prepareForgeRuntimeCommand"));
        assertTrue(gradle.contains("ocean-packages/packages/ocean-tools/**"));
        assertTrue(gradle.contains("source.zip"));
        assertTrue(gradle.contains("scripts/verify-native-only.sh"));
        assertTrue(gradle.contains("packages/ocean-prefix.env"));
    }

    @Test public void forgeCanBootstrapItsOwnAndroidBuildEnvironment() throws Exception {
        String gradle=projectFile("build.gradle");
        String installer=source("OceanForgeInstaller.java");
        String runner=source("OceanAgentRunner.java");
        String conversation=source("OceanAgentConversation.java");
        assertTrue(gradle.contains("OCEAN_FORGE_REUSE_NATIVE"));
        assertTrue(gradle.contains("buildToolsVersion \"34.0.4\""));
        assertTrue(gradle.contains("prepareForgeSourceBundle"));
        assertTrue(installer.contains("ensureSourceBundle"));
        assertTrue(installer.contains("ocean/forge/source.zip"));
        assertTrue(runner.contains("bootstrap_sdk"));
        assertTrue(runner.contains("ensureSourceBundle(context)"));
        assertTrue(conversation.contains("bootstrap its toolchain/SDK"));
        assertTrue(conversation.contains("patch it, and retry"));
    }

    @Test public void forgeNativeCoreCanBeRebuiltOnDevice() throws Exception {
        String gradle=projectFile("build.gradle");
        String runner=source("OceanAgentRunner.java");
        assertTrue(gradle.contains("src/forgeNative"));
        assertTrue(gradle.contains("OCEAN_FORGE_REUSE_NATIVE"));
        assertTrue(runner.contains("ocean-forge"));
    }

    @Test public void forgeShellSupportsSelfSeedSdkAndNativeCore() throws Exception {
        String forge=repositoryFile("ocean-packages/packages/ocean-tools/data/data/studio.ocean.app/files/usr/bin/ocean-forge");
        assertTrue(forge.contains("bootstrap-sdk)"));
        assertTrue(forge.contains("seed)"));
        assertTrue(forge.contains("prepare_native_library()"));
        assertTrue(forge.contains("pkg install -y git openjdk-21 kotlin ecj clang cmake ninja"));
        assertTrue(forge.contains("build-tools-34.0.4-aarch64.tar.xz"));
        assertTrue(forge.contains("llvm-readelf -h"));
        assertTrue(forge.contains("Machine:.*AArch64"));
    }

    @Test public void forgeSigningIdentityStaysPrivateAndUserFacing() throws Exception {
        String vault=source("OceanForgeSigningStore.java");
        String activity=source("OceanForgeActivity.java");
        String runner=source("OceanAgentRunner.java");
        assertTrue(vault.contains("getNoBackupFilesDir()"));
        assertTrue(vault.contains("AndroidKeyStore"));
        assertTrue(vault.contains("AES/GCM/NoPadding"));
        assertTrue(activity.contains("OceanForgeSigningStore"));
        assertTrue(activity.contains("Save signing identity privately") || activity.contains("saveSigningIdentity"));
        assertFalse(runner.contains("OceanForgeSigningStore"));
    }

    @Test public void dynamicPluginsStayInsideOceanToolRoots() throws Exception {
        String runtime=source("OceanPluginRuntime.java");
        String environment=projectFile("src/main/java/studio/ocean/app/terminal/OceanEnvironment.java");
        String installer=source("OceanForgeInstaller.java");
        String gradle=projectFile("build.gradle");
        assertTrue(runtime.contains("forge-tools/bin"));
        assertTrue(runtime.contains("usr/bin"));
        assertTrue(runtime.contains("Plugin command escapes Ocean tool roots"));
        assertTrue(environment.contains("forge-tools/bin"));
        assertTrue(installer.contains("ensureToolOverlay"));
        assertTrue(gradle.contains("prepareForgeToolOverlay"));
    }

    @Test public void dynamicPluginInputUsesPrivateStdinFiles() throws Exception {
        String runner=source("OceanAgentRunner.java");
        assertTrue(runner.contains("ocean-plugin-input"));
        assertTrue(runner.contains("File.createTempFile"));
        assertTrue(runner.contains(" < "));
        assertFalse(runner.contains("printf '%s' \"+shellQuote(input)"));
    }

    @Test public void debugOnlyAuthBypassIsExplicitlyBuildScoped() throws Exception {
        String gradle=projectFile("build.gradle");
        assertTrue(gradle.contains("debug {\n            // Debug APKs always expose"));
        assertTrue(gradle.contains("buildConfigField \"boolean\", \"OCEAN_DEV_AUTH_BYPASS\", \"true\""));
        assertTrue(gradle.contains("release {\n            // Hard-disabled"));
        assertTrue(gradle.contains("buildConfigField \"boolean\", \"OCEAN_DEV_AUTH_BYPASS\", \"false\""));
    }
}
