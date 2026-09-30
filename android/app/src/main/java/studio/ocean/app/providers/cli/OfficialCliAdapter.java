package studio.ocean.app.providers.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/**
 * Robust process runner for official provider CLI tools.
 * Handles timeouts, process destruction, separated stdout/stderr streams,
 * and NDJSON / JSON streaming output.
 */
public class OfficialCliAdapter {

    public interface StreamCallback {
        void onLine(String rawLine);
        void onJson(JSONObject json);
        void onError(String error);
        void onComplete(int exitCode);
    }

    public static final class ExecutionResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;

        public ExecutionResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }

        public boolean isSuccess() {
            return exitCode == 0 && !timedOut;
        }
    }

    protected final String executableName;
    protected final File searchDirectory;

    public OfficialCliAdapter(String executableName, File searchDirectory) {
        this.executableName = executableName;
        this.searchDirectory = searchDirectory;
    }

    public boolean isInstalled() {
        return resolveExecutable() != null;
    }

    public File resolveExecutable() {
        // First check in application searchDirectory (e.g. files/usr/bin)
        if (searchDirectory != null) {
            File direct = new File(searchDirectory, executableName);
            if (direct.exists() && direct.canExecute()) return direct;
            File inBin = new File(new File(searchDirectory, "bin"), executableName);
            if (inBin.exists() && inBin.canExecute()) return inBin;
            File inUsrBin = new File(new File(new File(searchDirectory, "usr"), "bin"), executableName);
            if (inUsrBin.exists() && inUsrBin.canExecute()) return inUsrBin;
        }

        // Check system PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                File candidate = new File(dir, executableName);
                if (candidate.exists() && candidate.canExecute()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public ExecutionResult executeSync(List<String> args, Map<String, String> env, long timeoutSeconds) {
        File bin = resolveExecutable();
        if (bin == null) {
            return new ExecutionResult(127, "", executableName + " is not installed on this device.", false);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(bin.getAbsolutePath());
        if (args != null) cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (env != null) pb.environment().putAll(env);

        StringBuilder stdoutBuf = new StringBuilder();
        StringBuilder stderrBuf = new StringBuilder();

        try {
            Process process = pb.start();
            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdoutBuf.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuf.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                return new ExecutionResult(-1, stdoutBuf.toString().trim(), "Execution timed out after " + timeoutSeconds + "s", true);
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);

            return new ExecutionResult(process.exitValue(), stdoutBuf.toString().trim(), stderrBuf.toString().trim(), false);
        } catch (Exception e) {
            return new ExecutionResult(1, "", e.getMessage(), false);
        }
    }

    public Process executeStreaming(List<String> args, Map<String, String> env, StreamCallback callback) {
        File bin = resolveExecutable();
        if (bin == null) {
            callback.onError(executableName + " executable was not found.");
            callback.onComplete(127);
            return null;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(bin.getAbsolutePath());
        if (args != null) cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (env != null) pb.environment().putAll(env);

        try {
            Process process = pb.start();
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        callback.onLine(line);
                        String trimmed = line.trim();
                        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                            try {
                                callback.onJson(new JSONObject(trimmed));
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }

                try {
                    int exit = process.waitFor();
                    callback.onComplete(exit);
                } catch (Exception e) {
                    callback.onComplete(-1);
                }
            }).start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        callback.onError(line);
                    }
                } catch (Exception ignored) {}
            }).start();

            return process;
        } catch (Exception e) {
            callback.onError(e.getMessage());
            callback.onComplete(1);
            return null;
        }
    }
}
