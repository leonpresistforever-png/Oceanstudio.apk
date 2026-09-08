package studio.ocean.app.terminal;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Live crash and device diagnostic viewer which survives crashes and app restarts. */
public final class OceanTerminalDiagnosticsActivity extends AppCompatActivity {
    private boolean zipExport;
    private TextView report;
    private final ActivityResultLauncher<Intent> createDocument = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (zipExport) TerminalDiagnosticBundle.writeZip(this, out);
                    else TerminalDiagnosticBundle.writeText(this, out);
                    Toast.makeText(this, "Diagnostic downloaded", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Download failed: " + e, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Ocean Live Diagnostics & Crash Viewer");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        LinearLayout buttonsRow1 = new LinearLayout(this);
        buttonsRow1.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow1.addView(button("REFRESH LOGS", v -> refresh()));
        buttonsRow1.addView(button("COPY ALL TO CLIPBOARD", v -> copyToClipboard()));
        buttonsRow1.addView(button("OPEN TERMINAL", v -> test(null)));
        root.addView(buttonsRow1);

        LinearLayout buttonsRow2 = new LinearLayout(this);
        buttonsRow2.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow2.addView(button("DOWNLOAD TXT", v -> download(false)));
        buttonsRow2.addView(button("SHARE ZIP", v -> share()));
        buttonsRow2.addView(button("CLEAR CRASH LOG", v -> clearCrashLog()));
        root.addView(buttonsRow2);

        LinearLayout buttonsRow3 = new LinearLayout(this);
        buttonsRow3.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow3.addView(button("TEST A: /system/bin/sh", v -> test("A")));
        buttonsRow3.addView(button("TEST B: Ocean Bash", v -> test("B")));
        buttonsRow3.addView(button("TEST C: PTY Verification", v -> test("C")));
        root.addView(buttonsRow3);

        report = new TextView(this);
        report.setTypeface(android.graphics.Typeface.MONOSPACE);
        report.setTextIsSelectable(true);
        report.setTextSize(12);
        report.setPadding(8, 16, 8, 16);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(report);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);
        refresh();
    }

    private void refresh() {
        StringBuilder sb = new StringBuilder();

        File crashFile = new File(new File(getFilesDir(), "logs"), "last-crash.log");
        if (crashFile.isFile() && crashFile.length() > 0) {
            sb.append("**************************************************\n");
            sb.append("🔴 LAST CRASH REPORT RECORDED ON DEVICE:\n");
            sb.append("**************************************************\n");
            try {
                sb.append(new String(Files.readAllBytes(crashFile.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                sb.append("Error reading crash file: ").append(e).append("\n");
            }
            sb.append("\n\n");
        }

        File javaCrash = new File(getFilesDir(), "logs/terminal-diagnostics/java-crash.log");
        if (javaCrash.isFile() && javaCrash.length() > 0 && !crashFile.isFile()) {
            sb.append("**************************************************\n");
            sb.append("🔴 JAVA CRASH LOG:\n");
            sb.append("**************************************************\n");
            try {
                sb.append(new String(Files.readAllBytes(javaCrash.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                sb.append("Error reading java crash: ").append(e).append("\n");
            }
            sb.append("\n\n");
        }

        sb.append(TerminalDiagnosticBundle.combined(this));
        report.setText(sb.toString());
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Ocean Diagnostics", report.getText().toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Diagnostics copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCrashLog() {
        File crashFile = new File(new File(getFilesDir(), "logs"), "last-crash.log");
        if (crashFile.exists()) crashFile.delete();
        File javaCrash = new File(getFilesDir(), "logs/terminal-diagnostics/java-crash.log");
        if (javaCrash.exists()) javaCrash.delete();
        Toast.makeText(this, "Crash log cleared", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(11);
        b.setOnClickListener(l);
        return b;
    }

    private void download(boolean zip) {
        zipExport = zip;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType(zip ? "application/zip" : "text/plain")
                .putExtra(Intent.EXTRA_TITLE, TerminalDiagnosticBundle.fileName(zip ? "zip" : "txt"))
                .addCategory(Intent.CATEGORY_OPENABLE);
        createDocument.launch(i);
    }

    private void share() {
        try {
            File f = TerminalDiagnosticBundle.shareZip(this);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".diagnostics", f);
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share Ocean diagnostic"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void test(String test) {
        Intent intent = new Intent(this, OceanTerminalActivity.class);
        if (test != null) intent.putExtra("diagnostic_test", test);
        startActivity(intent);
    }
}
