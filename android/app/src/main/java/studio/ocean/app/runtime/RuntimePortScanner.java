package studio.ocean.app.runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads Linux TCP listener tables and returns only sockets owned by OceanStudio's UID. */
public final class RuntimePortScanner {
    private static final int[] DEFAULT_PROBES = {
            3000, 3001, 4000, 5000, 5173, 5174, 5800, 6080, 6901, 8000, 8001, 8080, 8081, 8888, 9000
    };

    private RuntimePortScanner() {}

    public static List<Integer> scan(android.content.Context context) {
        if (context == null) return scan((File) null);
        return scan(new studio.ocean.app.OceanPaths(context).home());
    }

    public static List<Integer> scan() {
        return scan((File) null);
    }

    public static List<Integer> scan(File homeDirectory) {
        Set<Integer> ports = new LinkedHashSet<>();
        int uid = android.os.Process.myUid();
        read("/proc/self/net/tcp", uid, ports);
        read("/proc/self/net/tcp6", uid, ports);
        for (int port : probePorts(homeDirectory)) {
            if (ports.contains(port)) continue;
            if (isLocalListener(port)) ports.add(port);
        }
        List<Integer> sorted = new ArrayList<>(ports);
        Collections.sort(sorted);
        return sorted;
    }

    private static int[] probePorts(File homeDirectory) {
        Set<Integer> values = new LinkedHashSet<>();
        for (int port : DEFAULT_PROBES) values.add(port);
        if (homeDirectory != null) {
            File hints = new File(new File(homeDirectory, ".ocean"), "runtime-port-hints");
            if (hints.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(hints))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        try {
                            int port = Integer.parseInt(line);
                            if (port > 0 && port <= 65535) values.add(port);
                        } catch (NumberFormatException ignored) { }
                    }
                } catch (IOException ignored) { }
            }
        }
        int[] merged = new int[values.size()];
        int index = 0;
        for (Integer port : values) merged[index++] = port;
        return merged;
    }

    private static boolean isLocalListener(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 60);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void read(String path, int uid, Set<Integer> ports) {
        try (Reader reader = new FileReader(path)) { ports.addAll(parse(reader, uid)); }
        catch (IOException ignored) { }
    }

    static List<Integer> parse(Reader source, int expectedUid) throws IOException {
        Set<Integer> ports = new LinkedHashSet<>();
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 8 || !"0A".equals(fields[3])) continue;
            String[] local = fields[1].split(":", 2);
            if (local.length != 2) continue;
            try {
                int uid = Integer.parseInt(fields[7]);
                int port = Integer.parseInt(local[1], 16);
                if (uid == expectedUid && port > 0 && port <= 65535) ports.add(port);
            } catch (NumberFormatException ignored) { }
        }
        List<Integer> result = new ArrayList<>(ports);
        Collections.sort(result);
        return result;
    }

    public static String kind(int port) {
        String http = httpKind(port);
        if (http != null) return http;
        if (port == 6080 || port == 5800 || port == 6901) return "Possible noVNC service";
        if (port == 3000 || port == 5173 || port == 8000 || port == 8080 || port == 8888)
            return "Possible web development server";
        return "TCP listener · protocol unverified";
    }

    private static String httpKind(int port) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/").openConnection();
            connection.setConnectTimeout(120);
            connection.setReadTimeout(120);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            String contentType = connection.getHeaderField("Content-Type");
            String lower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("text/html")) {
                if (port == 6080 || port == 5800 || port == 6901) return "noVNC or remote desktop page";
                return "HTTP development server";
            }
            if (status >= 200 && status < 500) return "HTTP service · status " + status;
        } catch (IOException ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    static int[] probePortListForTest(File homeDirectory) {
        return probePorts(homeDirectory);
    }
}
