package studio.ocean.app;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Small durable local files, independent of Android so crash/restart behavior can be tested. */
final class CrashReportStore {
    static final int LIMIT = 64 * 1024;
    static void write(File file, String text) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.substring(0, Math.min(text.length(), LIMIT)).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }
    static String read(File file) {
        if (!file.isFile()) return "";
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048]; int n, remaining = LIMIT;
            while (remaining > 0 && (n = in.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                out.write(buffer, 0, n); remaining -= n;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ignored) { return ""; }
    }
    static void saveException(File file, String header, Thread thread, Throwable error) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            Writer limited = new Writer() {
                int remaining = LIMIT;
                public void write(char[] chars, int offset, int length) throws IOException {
                    int keep = Math.min(length, remaining); if (keep > 0) writer.write(chars, offset, keep); remaining -= keep;
                }
                public void flush() throws IOException { writer.flush(); }
                public void close() throws IOException { writer.close(); }
            };
            PrintWriter print = new PrintWriter(limited);
            print.println(header); print.println("Thread: " + thread.getName()); error.printStackTrace(print);
            print.flush(); out.getFD().sync();
        }
    }
    static String identity(String text) { return text.length() + ":" + Integer.toHexString(text.hashCode()); }
}
