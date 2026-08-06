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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private BufferedWriter stdin;
    private volatile boolean running = true;

    public ShellSession(String id, OceanLinuxSetup setup, String cwd, Listener listener) {
        this.id = id;
        this.setup = setup;
        this.cwd = cwd != null ? cwd : setup.getHomeDir().getAbsolutePath();
        this.listener = listener;
    }

    public void start() throws IOException {
        String prefix = setup.getPrefixDir().getAbsolutePath();
        String home = setup.getHomeDir().getAbsolutePath();
        String path = setup.getBinDir().getAbsolutePath() + ":" +
            prefix + "/usr/lib:" + System.getenv("PATH");

        ProcessBuilder pb;
        List<String> cmd = new ArrayList<>();

        String proot = setup.getProotPath();
        String shell = setup.getShellPath();

        if (proot != null && setup.isFullBootstrap()) {
            // Termux-style: proot with full prefix — pkg, apt, pip, git, curl all work
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
            pb = new ProcessBuilder(cmd);
        } else if (new File(shell).exists()) {
            pb = new ProcessBuilder(shell, "-l");
        } else {
            pb = new ProcessBuilder("/system/bin/sh", "-l");
        }

        File workDir = new File(cwd);
        if (!workDir.exists()) workDir.mkdirs();
        pb.directory(workDir);

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
        listener.onData("\u001b[36mOcean Native Terminal\u001b[0m — " + mode + "\r\n");
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
            char[] buf = new char[1024];
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
            int code = process.waitFor();
            listener.onExit(code);
        } catch (InterruptedException e) {
            listener.onExit(-1);
        }
    }

    public void write(String data) {
        try {
            stdin.write(data);
            stdin.flush();
        } catch (IOException e) {
            Log.e(TAG, "Write failed", e);
        }
    }

    public void clear() {
        write("clear\n");
    }

    public void kill() {
        running = false;
        if (process != null) process.destroyForcibly();
    }

    public void resize(int cols, int rows) {
        write(String.format("stty cols %d rows %d 2>/dev/null\n", cols, rows));
    }
}
