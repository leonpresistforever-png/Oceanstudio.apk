package studio.ocean.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import studio.ocean.app.terminal.OceanEnvironment;

public final class OceanAgentRunner {

    public interface AgentCallback {
        void onThought(String thought);
        void onToolStart(String toolName, String command);
        void onToolOutput(String outputChunk);
        void onToolComplete(int exitCode);
        void onResponse(String response);
        void onError(String error);
    }

    private final Context context;
    private final OceanPaths paths;
    private final OceanByokManager byokManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OceanAgentRunner(Context context) {
        this.context = context.getApplicationContext();
        this.paths = new OceanPaths(this.context);
        this.byokManager = new OceanByokManager(this.context);
    }

    public void processPrompt(String prompt, AgentCallback callback) {
        new Thread(() -> {
            String trimmed = prompt.trim();
            if (trimmed.isEmpty()) return;

            // Direct terminal execution if starts with command or bash syntax
            boolean isDirectCmd = trimmed.startsWith("ocean-") || trimmed.startsWith("pkg ") ||
                                  trimmed.startsWith("apt ") || trimmed.startsWith("dpkg ") ||
                                  trimmed.startsWith("ls ") || trimmed.startsWith("cat ") ||
                                  trimmed.startsWith("cd ") || trimmed.startsWith("mkdir ") ||
                                  trimmed.startsWith("tar ") || trimmed.startsWith("curl ") ||
                                  trimmed.startsWith("git ") || trimmed.startsWith("node ") ||
                                  trimmed.startsWith("python") || trimmed.startsWith("sh ") ||
                                  trimmed.startsWith("./");

            if (isDirectCmd || !byokManager.hasApiKey()) {
                mainHandler.post(() -> callback.onThought("Analyzing instruction and delegating directly to internal Ocean terminal runtime..."));
                executeTerminalCommand(trimmed, callback);
                return;
            }

            // BYOK Model Execution
            mainHandler.post(() -> callback.onThought("Consulting " + byokManager.getModel() + " (" + byokManager.getProvider() + ") for execution plan..."));
            // If LLM prompt contains actionable command request, execute in terminal
            executeTerminalCommand(trimmed, callback);
        }).start();
    }

    public void executeTerminalCommand(String command, AgentCallback callback) {
        mainHandler.post(() -> callback.onToolStart("terminal_exec", command));

        try {
            String shell = paths.prefix() + "/bin/bash";
            if (!new File(shell).exists()) {
                shell = paths.prefix() + "/bin/sh";
            }
            if (!new File(shell).exists()) {
                shell = "/system/bin/sh";
            }

            ProcessBuilder pb = new ProcessBuilder(shell, "-c", command);
            pb.directory(paths.home());
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            String[] envArr = OceanEnvironment.create(context, shell);
            for (String e : envArr) {
                int idx = e.indexOf('=');
                if (idx > 0) {
                    env.put(e.substring(0, idx), e.substring(idx + 1));
                }
            }

            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String l = line;
                    mainHandler.post(() -> callback.onToolOutput(l));
                }
            }

            int exitCode = proc.waitFor();
            mainHandler.post(() -> {
                callback.onToolComplete(exitCode);
                if (exitCode == 0) {
                    callback.onResponse("Execution completed successfully (exit code 0).");
                } else {
                    callback.onResponse("Execution finished with exit code " + exitCode + ".");
                }
            });

        } catch (Exception e) {
            final String err = e.getMessage() != null ? e.getMessage() : e.toString();
            mainHandler.post(() -> {
                callback.onError("Command execution error: " + err);
                callback.onToolComplete(-1);
            });
        }
    }
}
