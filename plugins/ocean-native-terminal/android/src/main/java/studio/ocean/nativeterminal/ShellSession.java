package studio.ocean.nativeterminal;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive shell session with real PTY allocation when available (openpty via JNI),
 * falling back to ProcessBuilder pipes for minimal environments.
 */
public class ShellSession implements Runnable {
    private static final String TAG = "ShellSession";

    public interface Listener {
        void onData(String data);
        void onExit(int code);
        void onPortDetected(int port, String url);
    }

    private final String id;
    private final OceanLinuxSetup setup;
    private final String cwd;
    private final Listener listener;
    private Process process;
    private PtySession ptySession;
    private BufferedWriter stdin;
    private volatile boolean running = true;
    private int cols = 80;
    private int rows = 24;

    public ShellSession(String id, OceanLinuxSetup setup, String cwd, Listener listener) {
        this.id = id;
        this.setup = setup;
        this.cwd = cwd != null ? cwd : setup.getHomeDir().getAbsolutePath();
        this.listener = listener;
    }

    public void start() throws IOException {
        List<String> cmd = buildCommand();
        String[] cmdArray = cmd.toArray(new String[0]);
        String[] envArray = buildEnvArray();
        File workDir = new File(cwd);
        if (!workDir.exists()) workDir.mkdirs();

        if (PtyBridge.isAvailable()) {
            ptySession = PtyBridge.createSession(cmdArray, envArray, workDir.getAbsolutePath(), rows, cols);
        }

        if (ptySession != null) {
            startPtyMode();
            return;
        }

        startPipeMode(cmd, workDir);
    }

    private List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        String prefix = setup.getPrefixDir().getAbsolutePath();
        String proot = setup.getProotPath();
        String shell = setup.getShellPath();

        if (proot != null && setup.isFullBootstrap()) {
            cmd.add(proot);
            cmd.add("-0");
            cmd.add("-r");
            cmd.add(prefix);
            cmd.add("-b");
            cmd.add(prefix + "/dev:/dev");
            cmd.add("-b");
            cmd.add("/proc:/proc");
            cmd.add("-b");
            cmd.add(prefix + "/sys:/sys");
            cmd.add("-w");
            cmd.add(cwd);
            cmd.add(shell);
            cmd.add("-l");
        } else if (new File(shell).exists()) {
            cmd.add(shell);
            cmd.add("-l");
        } else {
            cmd.add("/system/bin/sh");
            cmd.add("-l");
        }
        return cmd;
    }

    private String[] buildEnvArray() {
        String prefix = setup.getPrefixDir().getAbsolutePath();
        String home = setup.getHomeDir().getAbsolutePath();
        String path = setup.getBinDir().getAbsolutePath() + ":" +
            prefix + "/usr/lib:" + System.getenv("PATH");

        Map<String, String> env = new HashMap<>();
        env.put("HOME", home);
        env.put("PREFIX", prefix);
        env.put("PATH", path);
        env.put("TERM", "xterm-256color");
        env.put("OCEAN_TERMINAL_TYPE", "native");
        env.put("LANG", "en_US.UTF-8");
        env.put("COLORTERM", "truecolor");

        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            pairs.add(e.getKey() + "=" + e.getValue());
        }
        return pairs.toArray(new String[0]);
    }

    private void startPtyMode() throws IOException {
        String mode = setup.isFullBootstrap() ? "PTY + Termux (proot + pkg/apt)" : "PTY + Busybox";
        listener.onData("\u001b[36mOcean Native Terminal\u001b[0m — " + mode + "\r\n");
        listener.onData("\u001b[90mReal pseudoterminal — interactive bash, ssh, htop, nano supported\u001b[0m\r\n");
        listener.onData("PREFIX=" + setup.getPrefixDir().getAbsolutePath() + "\r\n");
        if (setup.isFullBootstrap()) {
            listener.onData("\u001b[90mTry: pkg install python git nodejs openssh | apt install curl\u001b[0m\r\n");
        }

        new Thread(() -> readStream(ptySession.getInput()), "pty-read-" + id).start();
        new Thread(this, "pty-wait-" + id).start();
    }

    private void startPipeMode(List<String> cmd, File workDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);

        String prefix = setup.getPrefixDir().getAbsolutePath();
        String home = setup.getHomeDir().getAbsolutePath();
        String path = setup.getBinDir().getAbsolutePath() + ":" +
            prefix + "/usr/lib:" + System.getenv("PATH");

        pb.environment().put("HOME", home);
        pb.environment().put("PREFIX", prefix);
        pb.environment().put("PATH", path);
        pb.environment().put("TERM", "xterm-256color");
        pb.environment().put("OCEAN_TERMINAL_TYPE", "native");
        pb.environment().put("LANG", "en_US.UTF-8");
        pb.redirectErrorStream(true);

        process = pb.start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        String mode = setup.isFullBootstrap() ? "Termux (proot + pkg/apt)" : "Busybox";
        listener.onData("\u001b[36mOcean Native Terminal\u001b[0m — " + mode + " (pipe mode)\r\n");
        listener.onData("PREFIX=" + prefix + "\r\n");
        if (setup.isFullBootstrap()) {
            listener.onData("\u001b[90mTry: pkg install python git nodejs | apt install curl\u001b[0m\r\n");
        }
        listener.onData("$ ");

        new Thread(this, "shell-wait-" + id).start();
        new Thread(() -> readStream(process.getInputStream()), "shell-read-" + id).start();
    }

    private void readStream(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while (running && (n = reader.read(buf)) != -1) {
                String chunk = new String(buf, 0, n);
                listener.onData(chunk);
                detectPorts(chunk);
            }
        } catch (IOException e) {
            if (running) Log.w(TAG, "Read error: " + e.getMessage());
        }
    }

    private void detectPorts(String data) {
        Pattern[] patterns = {
            Pattern.compile("localhost:(\\d{4,5})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("127\\.0\\.0\\.1:(\\d{4,5})"),
            Pattern.compile("http://localhost:(\\d{4,5})", Pattern.CASE_INSENSITIVE),
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(data);
            while (m.find()) {
                int port = Integer.parseInt(m.group(1));
                if (port > 1024 && port < 65536) {
                    listener.onPortDetected(port, "http://localhost:" + port);
                }
            }
        }
    }

    @Override
    public void run() {
        try {
            int code;
            if (ptySession != null) {
                code = ptySession.waitFor();
            } else {
                code = process.waitFor();
            }
            listener.onExit(code);
        } catch (InterruptedException e) {
            listener.onExit(-1);
        }
    }

    public void write(String data) {
        try {
            if (ptySession != null) {
                ptySession.write(data);
            } else if (stdin != null) {
                stdin.write(data);
                stdin.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Write failed", e);
        }
    }

    public void clear() {
        write("clear\n");
    }

    public void kill() {
        running = false;
        if (ptySession != null) {
            ptySession.kill();
        }
        if (process != null) process.destroyForcibly();
    }

    public void resize(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        if (ptySession != null) {
            ptySession.resize(cols, rows);
        } else {
            write(String.format("stty cols %d rows %d 2>/dev/null\n", cols, rows));
        }
    }
}
