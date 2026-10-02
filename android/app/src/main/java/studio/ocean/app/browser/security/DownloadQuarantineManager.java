package studio.ocean.app.browser.security;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.URLUtil;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import studio.ocean.app.OceanModal;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles genuine app-private download staging, cryptographic SHA-256 calculation,
 * and quarantine inspection before any user storage export (PDF 5 §11, §13.2).
 */
public final class DownloadQuarantineManager {

    public static final class QuarantineRecord {
        public final File file;
        public final String fileName;
        public final String sourceUrl;
        public final String mimeType;
        public final long sizeBytes;
        public final String sha256Hex;

        public QuarantineRecord(File file, String fileName, String sourceUrl,
                                String mimeType, long sizeBytes, String sha256Hex) {
            this.file = file;
            this.fileName = fileName;
            this.sourceUrl = sourceUrl;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.sha256Hex = sha256Hex;
        }
    }

    public interface QuarantineCallback {
        void onDownloadComplete(QuarantineRecord record);
        void onDownloadFailed(String error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private DownloadQuarantineManager() {
    }

    /**
     * Downloads file to app-private cache quarantine and calculates SHA-256.
     */
    public static void stageDownload(
            @NonNull Context context,
            @NonNull String url,
            @Nullable String contentDisposition,
            @Nullable String mimeType,
            @NonNull QuarantineCallback callback) {

        String guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;

            try {
                File quarantineDir = new File(context.getCacheDir(), "quarantine/" + UUID.randomUUID());
                if (!quarantineDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    quarantineDir.mkdirs();
                }

                File targetFile = new File(quarantineDir, guessedName);

                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) OceanQuarantine");
                conn.connect();

                int code = conn.getResponseCode();
                if (code >= 400) {
                    throw new RuntimeException("HTTP " + code + ": " + conn.getResponseMessage());
                }

                String resolvedMime = mimeType != null ? mimeType : conn.getContentType();
                if (resolvedMime == null) resolvedMime = "application/octet-stream";

                MessageDigest md = MessageDigest.getInstance("SHA-256");
                in = conn.getInputStream();
                out = new FileOutputStream(targetFile);

                byte[] buffer = new byte[8192];
                int read;
                long total = 0;

                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    md.update(buffer, 0, read);
                    total += read;
                }
                out.flush();

                byte[] digest = md.digest();
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }

                QuarantineRecord record = new QuarantineRecord(
                        targetFile, guessedName, url, resolvedMime, total, hex.toString()
                );

                mainHandler.post(() -> callback.onDownloadComplete(record));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onDownloadFailed(e.getMessage() != null ? e.getMessage() : "Download error"));
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Displays quarantine inspection dialog with SHA-256, origin, and size,
     * requiring explicit user action to export to public Downloads.
     */
    public static void showQuarantineDialog(@NonNull Context context, @NonNull QuarantineRecord record) {
        String sizeFormatted = formatFileSize(record.sizeBytes);
        String details = "File: " + record.fileName + "\n\n"
                + "Size: " + sizeFormatted + "\n"
                + "MIME: " + record.mimeType + "\n"
                + "Source: " + record.sourceUrl + "\n\n"
                + "SHA-256:\n" + record.sha256Hex + "\n\n"
                + "Status: Quarantined in app-private storage. Do you wish to export to your device Downloads?";

        OceanModal.create(context)
                .setTitle("Download Quarantined")
                .setExplanation("The downloaded file has been safely quarantined in app-private storage.")
                .setDetailsText(details)
                .setPositiveButton("Export to Downloads", btn -> exportToDownloads(context, record))
                .setNegativeButton("Discard", btn -> {
                    //noinspection ResultOfMethodCallIgnored
                    record.file.delete();
                })
                .show();
    }

    /**
     * Safely exports the quarantined file into device Downloads.
     */
    public static void exportToDownloads(@NonNull Context context, @NonNull QuarantineRecord record) {
        EXECUTOR.execute(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentResolver resolver = context.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, record.fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, record.mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        try (InputStream in = new FileInputStream(record.file);
                             OutputStream out = resolver.openOutputStream(uri)) {
                            if (out != null) {
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = in.read(buf)) != -1) {
                                    out.write(buf, 0, len);
                                }
                                out.flush();
                            }
                        }
                    }
                } else {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File dest = new File(downloadsDir, record.fileName);
                    try (InputStream in = new FileInputStream(record.file);
                         OutputStream out = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        out.flush();
                    }
                }
                // Discard quarantined temp file after export
                //noinspection ResultOfMethodCallIgnored
                record.file.delete();
            } catch (Exception ignored) {
            }
        });
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
