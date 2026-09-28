package studio.ocean.app.terminal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import jackpal.androidterm.emulatorview.ColorScheme;
import jackpal.androidterm.emulatorview.EmulatorView;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Native Ocean Terminal UI connected to a service-owned real PTY with multi-session support. */
public final class OceanTerminalActivity extends AppCompatActivity implements TerminalSession.Listener {
    private TextView status;
    private EmulatorView terminalView;
    private OceanEmulatorSession emulatorSession;
    private TerminalSession session;
    private OceanTerminalRuntimeService service;
    private boolean bound;
    private boolean keyboardVisible;
    private boolean toolsVisible = true;
    private View failureActions;
    private Thread.UncaughtExceptionHandler previousCrashHandler;
    private String diagnosticMode;
    private boolean scriptedExitSent;
    private final StringBuilder diagnosticOutput = new StringBuilder();

    // Multi-session sidebar components
    private View sidebarBackdrop;
    private View terminalSidebar;
    private LinearLayout sessionListContainer;
    private TextView sessionCountView;
    private TextView sessionBadgeView;
    private GestureDetector gestureDetector;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder binder) {
            TerminalDiagnosticBundle.log("startup.log", "[J04] service connected BEGIN");
            bound = true;
            service = ((OceanTerminalRuntimeService.LocalBinder) binder).service();
            TerminalStartupLog.stage("03", "TerminalActivity service connected");
            TerminalDiagnosticBundle.log("startup.log", "[J04] service connected END");
            if ("A".equals(diagnosticMode)) {
                startRecoverySession();
            } else if ("B".equals(diagnosticMode) || "C".equals(diagnosticMode)) {
                startDiagnosticOceanSession();
            } else {
                startOceanSession();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            bound = false;
            service = null;
            session = null;
            TerminalStartupLog.stage("17", "runtime service disconnected");
        }
    };

    private TextView outputView;
    private android.widget.EditText inputView;

    private int resId(String name) {
        try {
            return getResources().getIdentifier(name, "id", getPackageName());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findSafeView(String name) {
        int id = resId(name);
        return id != 0 ? (T) findViewById(id) : null;
    }

    private void safeClick(String name, View.OnClickListener l) {
        View v = findSafeView(name);
        if (v != null) v.setOnClickListener(l);
    }

    private String safeString(String name, String fallback, Object... args) {
        try {
            int id = getResources().getIdentifier(name, "string", getPackageName());
            if (id != 0) return args.length > 0 ? getString(id, args) : getString(id);
        } catch (Throwable ignored) {}
        return fallback;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            diagnosticMode = getIntent().getStringExtra("diagnostic_test");
            TerminalDiagnosticBundle.beginAttempt(this);
            String attemptName = diagnosticMode == null ? "PRODUCTION TERMINAL" : "PTY TEST " + diagnosticMode;
            TerminalDiagnosticBundle.setTest(this, attemptName);
            TerminalDiagnosticBundle.log("session-state.log", "activeDiagnosticTest=" + attemptName);
            TerminalStartupLog.initialize(this);
            previousCrashHandler = TerminalStartupLog.installCrashCapture();

            int layoutId = getResources().getIdentifier("activity_terminal", "layout", getPackageName());
            if (layoutId != 0) setContentView(layoutId);

            status = findSafeView("terminal_status");
            terminalView = findSafeView("terminal_emulator");
            failureActions = findSafeView("terminal_failure_actions");
            outputView = findSafeView("terminal_output");
            inputView = findSafeView("terminal_input");

            // Sidebar elements
            sidebarBackdrop = findSafeView("terminal_sidebar_backdrop");
            terminalSidebar = findSafeView("terminal_sidebar");
            sessionListContainer = findSafeView("terminal_session_list");
            sessionCountView = findSafeView("terminal_session_count");
            sessionBadgeView = findSafeView("terminal_session_badge");

            if (sidebarBackdrop != null) {
                sidebarBackdrop.setOnClickListener(v -> closeSessionSidebar());
            }
            safeClick("terminal_sidebar_close", v -> closeSessionSidebar());
            safeClick("terminal_new_session_btn", v -> createNewSession());
            if (sessionBadgeView != null) {
                sessionBadgeView.setOnClickListener(v -> openSessionSidebar());
            }

            // Swipe gesture detector: swipe left opens sidebar, swipe right closes
            gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                private static final int SWIPE_MIN_DISTANCE = 80;
                private static final int SWIPE_THRESHOLD_VELOCITY = 100;

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (e1 == null || e2 == null) return false;
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (diffX < -SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                            // User swiped left: show white sidebar
                            openSessionSidebar();
                            return true;
                        } else if (diffX > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                            // User swiped right: hide sidebar if open
                            if (isSidebarOpen()) {
                                closeSessionSidebar();
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });

            toolsVisible = state == null || state.getBoolean("terminal_tools_visible", true);
            safeClick("terminal_keyboard", v -> toggleKeyboard());
            safeClick("terminal_tools", v -> { toolsVisible = !toolsVisible; updateTools(); });
            safeClick("terminal_ports", v -> startActivity(new Intent(this, studio.ocean.app.runtime.RuntimePortsActivity.class)));
            updateTools();

            View root = findSafeView("terminal_root");
            if (root != null) {
                ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                    keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                    updateKeyboardButton();
                    return insets;
                });
                ViewCompat.requestApplyInsets(root);
            }

            if (terminalView == null) {
                try {
                    terminalView = new EmulatorView(this);
                    terminalView.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
                    terminalView.setFocusable(true);
                    terminalView.setFocusableInTouchMode(true);
                    if (outputView != null) {
                        android.view.ViewParent parent = outputView.getParent();
                        if (parent instanceof android.widget.ScrollView) {
                            android.view.ViewParent scrollParent = parent.getParent();
                            if (scrollParent instanceof ViewGroup) {
                                ViewGroup vg = (ViewGroup) scrollParent;
                                int index = vg.indexOfChild((View) parent);
                                vg.removeView((View) parent);
                                vg.addView(terminalView, index);
                            }
                        }
                    }
                    if (inputView != null) {
                        inputView.setVisibility(View.GONE);
                    }
                } catch (Throwable t) {
                    TerminalStartupLog.failure("Failed to create programmatic EmulatorView", t);
                }
            }

            safeClick("terminal_back", v -> finish());
            safeClick("terminal_ctrl", v -> { if (terminalView != null) terminalView.sendControlKey(); });
            safeClick("terminal_alt", v -> { if (terminalView != null) terminalView.sendAltKey(); });
            safeClick("terminal_ctrl_c", v -> { if (session != null) write("\u0003"); });
            safeClick("terminal_tab", v -> write("\t"));
            safeClick("terminal_escape", v -> write("\u001b"));
            safeClick("terminal_left", v -> sendTerminalKey(KeyEvent.KEYCODE_DPAD_LEFT));
            safeClick("terminal_down", v -> sendTerminalKey(KeyEvent.KEYCODE_DPAD_DOWN));
            safeClick("terminal_up", v -> sendTerminalKey(KeyEvent.KEYCODE_DPAD_UP));
            safeClick("terminal_right", v -> sendTerminalKey(KeyEvent.KEYCODE_DPAD_RIGHT));
            safeClick("terminal_home", v -> sendTerminalKey(KeyEvent.KEYCODE_MOVE_HOME));
            safeClick("terminal_end", v -> sendTerminalKey(KeyEvent.KEYCODE_MOVE_END));
            safeClick("terminal_page_up", v -> sendTerminalKey(KeyEvent.KEYCODE_PAGE_UP));
            safeClick("terminal_page_down", v -> sendTerminalKey(KeyEvent.KEYCODE_PAGE_DOWN));
            safeClick("terminal_slash", v -> write("/"));
            safeClick("terminal_dash", v -> write("-"));
            safeClick("terminal_pipe", v -> write("|"));
            safeClick("terminal_tilde", v -> write("~"));
            safeClick("terminal_retry", v -> startOceanSession());
            safeClick("terminal_details", v -> showDiagnosticLog());
            safeClick("terminal_recovery", v -> startRecoverySession());

            if (inputView != null) {
                inputView.setOnEditorActionListener((v, actionId, event) -> {
                    String cmd = inputView.getText().toString();
                    write(cmd + "\n");
                    inputView.setText("");
                    return true;
                });
            }

            try {
                java.io.File crashLog = new java.io.File(getFilesDir(), "logs/last-crash.log");
                java.io.File javaCrash = new java.io.File(getFilesDir(), "logs/terminal-diagnostics/java-crash.log");
                if (crashLog.exists()) crashLog.delete();
                if (javaCrash.exists()) javaCrash.delete();
            } catch (Throwable ignored) {}

            TerminalStartupLog.stage("02", "bind runtime service");
            TerminalDiagnosticBundle.log("startup.log", "[J02] service bind requested");
            startService(new Intent(this, OceanTerminalRuntimeService.class));
            if (!bindService(new Intent(this, OceanTerminalRuntimeService.class), connection, Context.BIND_AUTO_CREATE)) {
                showFailure("Runtime service binding failed", null);
            }
        } catch (Throwable t) {
            TerminalStartupLog.failure("Activity.onCreate crashed", t);
            showFailure("Terminal initialization failed: " + t.getMessage(), t);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        if (isSidebarOpen()) {
            closeSessionSidebar();
            return;
        }
        super.onBackPressed();
    }

    private boolean isSidebarOpen() {
        return terminalSidebar != null && terminalSidebar.getVisibility() == View.VISIBLE;
    }

    private void openSessionSidebar() {
        if (terminalSidebar == null) return;
        terminalSidebar.setVisibility(View.VISIBLE);
        if (sidebarBackdrop != null) sidebarBackdrop.setVisibility(View.VISIBLE);
        refreshSessionsList();
    }

    private void closeSessionSidebar() {
        if (terminalSidebar != null) terminalSidebar.setVisibility(View.GONE);
        if (sidebarBackdrop != null) sidebarBackdrop.setVisibility(View.GONE);
    }

    private void createNewSession() {
        if (service == null) return;
        Button btn = findSafeView("terminal_new_session_btn");
        if (btn != null) {
            btn.setEnabled(false);
            btn.setText("Creating session...");
        }
        service.requestNewTerminalSession(24, 80, new OceanTerminalRuntimeService.SessionCallback() {
            @Override
            public void onProgress(OceanTerminalRuntimeService.RuntimeState state, String detail, long completed, long total) {}

            @Override
            public void onReady(TerminalSession ready) {
                if (isFinishing() || isDestroyed()) return;
                attach(ready, true);
                if (btn != null) {
                    btn.setEnabled(true);
                    btn.setText("+ Create New Session");
                }
                closeSessionSidebar();
                Toast.makeText(OceanTerminalActivity.this, "New session started", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Throwable error) {
                if (isFinishing() || isDestroyed()) return;
                if (btn != null) {
                    btn.setEnabled(true);
                    btn.setText("+ Create New Session");
                }
                Toast.makeText(OceanTerminalActivity.this, "Failed: " + safeMessage(error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshSessionsList() {
        if (sessionListContainer == null || service == null) return;
        sessionListContainer.removeAllViews();
        List<TerminalSession> interactive = service.getInteractiveSessions();
        int count = interactive.size();
        if (sessionCountView != null) {
            sessionCountView.setText(count + (count == 1 ? " active session" : " active sessions"));
        }
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * density);
        int margin = (int) (8 * density);

        for (int i = 0; i < interactive.size(); i++) {
            final TerminalSession s = interactive.get(i);
            final int sessionNum = i + 1;
            final boolean isActive = (session != null && session.id.equals(s.id));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(pad, pad, pad, pad);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, margin);
            card.setLayoutParams(lp);

            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(8 * density);
            if (isActive) {
                shape.setColor(0xFFF0FDF4); // Subtle mint background
                shape.setStroke((int) (2 * density), 0xFF10B981); // Emerald border
            } else {
                shape.setColor(0xFFF8FAFC); // Clean slate
                shape.setStroke((int) (1 * density), 0xFFE2E8F0);
            }
            card.setBackground(shape);

            // Information block
            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            infoCol.setLayoutParams(infoLp);

            TextView title = new TextView(this);
            title.setText("Session " + sessionNum + (isActive ? " (Active)" : ""));
            title.setTextSize(14);
            title.setTextColor(isActive ? 0xFF047857 : 0xFF1E293B);
            title.setTypeface(null, Typeface.BOLD);

            TextView subtitle = new TextView(this);
            subtitle.setText("PID: " + s.pid() + " • " + s.state());
            subtitle.setTextSize(11);
            subtitle.setTextColor(0xFF64748B);

            infoCol.addView(title);
            infoCol.addView(subtitle);
            card.addView(infoCol);

            // Active or Switch Button
            if (isActive) {
                TextView activeBadge = new TextView(this);
                activeBadge.setText("ACTIVE");
                activeBadge.setTextSize(11);
                activeBadge.setTypeface(null, Typeface.BOLD);
                activeBadge.setTextColor(0xFF059669);
                card.addView(activeBadge);
            } else {
                Button switchBtn = new Button(this);
                switchBtn.setText("Switch");
                switchBtn.setTextSize(12);
                switchBtn.setAllCaps(false);
                switchBtn.setTextColor(0xFF1E293B);
                switchBtn.setOnClickListener(v -> {
                    attach(s, true);
                    closeSessionSidebar();
                });
                card.addView(switchBtn);
            }

            // Close session button if more than 1 session
            if (count > 1) {
                ImageButton closeBtn = new ImageButton(this);
                closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                closeBtn.setBackground(null);
                closeBtn.setColorFilter(0xFF94A3B8);
                int btnPad = (int) (6 * density);
                closeBtn.setPadding(btnPad, btnPad, btnPad, btnPad);
                closeBtn.setContentDescription("Terminate session " + sessionNum);
                closeBtn.setOnClickListener(v -> {
                    service.closeSession(s.id);
                    if (s == session) {
                        TerminalSession next = service.firstRunning();
                        if (next != null) attach(next, true);
                        else startOceanSession();
                    }
                    refreshSessionsList();
                });
                card.addView(closeBtn);
            }

            if (!isActive) {
                card.setOnClickListener(v -> {
                    attach(s, true);
                    closeSessionSidebar();
                });
            }

            sessionListContainer.addView(card);
        }
    }

    private void updateSessionBadge() {
        if (sessionBadgeView != null && service != null) {
            List<TerminalSession> list = service.getInteractiveSessions();
            int idx = (session != null) ? list.indexOf(session) : -1;
            if (idx >= 0) {
                sessionBadgeView.setText("TERM " + (idx + 1));
            } else {
                sessionBadgeView.setText(safeString("terminal_shell_badge", "TERM 1"));
            }
        }
    }

    private void startOceanSession() {
        if (service == null) return;
        if (failureActions != null) failureActions.setVisibility(View.GONE);
        if (status != null) {
            status.setText(safeString("terminal_connecting", "Connecting to Ocean Terminal..."));
            status.setVisibility(View.VISIBLE);
        }
        TerminalDiagnosticBundle.log("startup.log", "[J07] createSession requested");
        TerminalSession candidate = service.firstRunning();
        if (candidate != null) {
            attach(candidate, true);
            return;
        }
        service.requestTerminalSession(24, 80, new OceanTerminalRuntimeService.SessionCallback() {
            @Override
            public void onProgress(OceanTerminalRuntimeService.RuntimeState state, String detail, long completed, long total) {
                if (isFinishing() || isDestroyed()) return;
                String progress = total > 0 ? "\n" + completed + " / " + total : "";
                if (status != null) {
                    status.setText(detail + progress);
                    status.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReady(TerminalSession ready) {
                if (isFinishing() || isDestroyed()) return;
                attach(ready, true);
            }

            @Override
            public void onFailure(Throwable error) {
                if (isFinishing() || isDestroyed()) return;
                showFailure("Ocean runtime setup failed: " + safeMessage(error), error);
            }
        });
    }

    private void startRecoverySession() {
        if (service == null) return;
        if (failureActions != null) failureActions.setVisibility(View.GONE);
        try {
            attach(service.createRecoverySession(24, 80), false);
        } catch (Throwable error) {
            showFailure("Recovery shell failed: " + safeMessage(error), error);
        }
    }

    private void startDiagnosticOceanSession() {
        if (service == null) return;
        if (failureActions != null) failureActions.setVisibility(View.GONE);
        service.requestDiagnosticOceanSession(24, 80, new OceanTerminalRuntimeService.SessionCallback() {
            @Override
            public void onProgress(OceanTerminalRuntimeService.RuntimeState state, String detail, long done, long total) {
                if (!isFinishing() && !isDestroyed() && status != null) {
                    status.setText(detail);
                    status.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReady(TerminalSession ready) {
                if (isFinishing() || isDestroyed()) return;
                attach(ready, true);
                if ("C".equals(diagnosticMode)) {
                    TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log", "waiting for RUNNING");
                    writeWhenRunning("echo OCEAN_PTY_OK\r", 0);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                if (!isFinishing() && !isDestroyed()) {
                    showFailure("Ocean Bash diagnostic failed: " + safeMessage(error) + ". Use Install/Repair Ocean Runtime from the production terminal.", error);
                }
            }
        });
    }

    private void writeWhenRunning(String value, int attempt) {
        if (session == null || attempt > 50) return;
        if (session.state() == TerminalSession.State.RUNNING) {
            TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log", "script write echo exactly once");
            write(value);
        } else if (terminalView != null) {
            terminalView.postDelayed(() -> writeWhenRunning(value, attempt + 1), 50);
        }
    }

    private void attach(TerminalSession candidate, boolean installed) {
        if (session != null) session.removeListener(this);
        if (emulatorSession != null) emulatorSession.finish();
        session = candidate;
        if (terminalView != null) {
            ColorScheme colors = new ColorScheme(0xffe6edf1, 0xff0b1319, 0xff0b1319, 0xff8cdbc6);
            emulatorSession = new OceanEmulatorSession();
            emulatorSession.attachTransport(session);
            emulatorSession.setColorScheme(colors);
            terminalView.setDensity(getResources().getDisplayMetrics());
            terminalView.attachSession(emulatorSession);
            terminalView.setTextSize(14);
            terminalView.setUseCookedIME(true);
            terminalView.setAltSendsEsc(true);
            terminalView.setTermType("xterm-256color");
            terminalView.setColorScheme(colors);
            terminalView.requestFocus();
            terminalView.onResume();
        }
        session.addListener(this);
        if (status != null) status.setVisibility(View.GONE);
        updateSessionBadge();
    }

    private void showFailure(String message, Throwable error) {
        TerminalStartupLog.failure(message, error);
        if (status != null) {
            status.setText(message + "\n(Tap here to view full diagnostic logs)");
            status.setVisibility(View.VISIBLE);
            status.setOnClickListener(v -> showDiagnosticLog());
        }
        if (failureActions != null) failureActions.setVisibility(View.VISIBLE);
        try {
            String fullErr = message + (error != null ? "\n\nDetails:\n" + safeMessage(error) : "");
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Ocean Terminal Error")
                .setMessage(fullErr)
                .setPositiveButton("View Diagnostic Logs", (d, w) -> showDiagnosticLog())
                .setNegativeButton("Copy Error", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Terminal Error", fullErr));
                    Toast.makeText(this, "Error copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Dismiss", null)
                .show();
        } catch (Throwable ignored) {}
    }

    private void showDiagnosticLog() {
        try {
            startActivity(new Intent(this, OceanTerminalDiagnosticsActivity.class));
        } catch (Throwable t) {
            Toast.makeText(this, "Diagnostic log: " + safeMessage(t), Toast.LENGTH_SHORT).show();
        }
    }

    private void sendTerminalKey(int keyCode) {
        if (terminalView == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        terminalView.onKeyDown(keyCode, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        terminalView.onKeyUp(keyCode, new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        StringBuilder sb = new StringBuilder();
        Throwable curr = error;
        while (curr != null) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(curr.getClass().getSimpleName()).append(": ").append(curr.getMessage() != null ? curr.getMessage() : "");
            curr = curr.getCause();
        }
        return sb.toString();
    }

    private void write(String value) {
        if (session == null) return;
        if (session.state() == TerminalSession.State.RUNNING && "exit".equals(value.replace("\r", "").trim())) {
            TerminalDiagnosticBundle.markCritical(this, "EXIT_COMMAND_SENT");
        }
        TerminalSession.WriteResult result = session.write(value);
        if (result == TerminalSession.WriteResult.NATIVE_WRITE_FAILED && status != null) {
            status.setText(safeString("terminal_write_failed", "Terminal write failed"));
            status.setVisibility(View.VISIBLE);
        } else if (result == TerminalSession.WriteResult.SESSION_ALREADY_EXITED) {
            TerminalDiagnosticBundle.log("session-state.log", "late UI write safely rejected state=" + session.state());
        }
    }

    @Override
    public void onOutput(byte[] bytes, int length) {
        byte[] copy = java.util.Arrays.copyOf(bytes, length);
        String value = new String(copy, StandardCharsets.UTF_8);
        if ("C".equals(diagnosticMode) && !scriptedExitSent) {
            synchronized (diagnosticOutput) {
                diagnosticOutput.append(value);
                if (diagnosticOutput.length() > 4096) diagnosticOutput.delete(0, diagnosticOutput.length() - 4096);
                String normalized = diagnosticOutput.toString().replace("\r\n", "\n");
                if (normalized.contains("\nOCEAN_PTY_OK\n")) {
                    scriptedExitSent = true;
                    TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log", "OCEAN_PTY_OK result line observed; sending exit exactly once; no later writes scheduled");
                    if (terminalView != null) terminalView.post(() -> write("exit\r"));
                    else write("exit\r");
                }
            }
        }
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (emulatorSession != null) emulatorSession.feed(copy, copy.length);
            if (outputView != null) {
                outputView.append(value);
                View p = (View) outputView.getParent();
                if (p instanceof android.widget.ScrollView) ((android.widget.ScrollView) p).fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    public void onExit(int code) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed() && status != null) {
                status.setText(safeString("terminal_exited", "Process exited (" + code + ")", code));
                status.setVisibility(View.VISIBLE);
            }
            updateSessionBadge();
            if (isSidebarOpen()) refreshSessionsList();
        });
    }

    private void toggleKeyboard() {
        if (terminalView == null) return;
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(terminalView);
        boolean visible = insets != null ? insets.isVisible(WindowInsetsCompat.Type.ime()) : keyboardVisible;
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), terminalView);
        if (visible) controller.hide(WindowInsetsCompat.Type.ime());
        else {
            terminalView.requestFocus();
            terminalView.post(() -> controller.show(WindowInsetsCompat.Type.ime()));
        }
    }

    private void updateKeyboardButton() {
        View button = findSafeView("terminal_keyboard");
        if (button == null) return;
        button.setSelected(keyboardVisible);
        button.setContentDescription(safeString(keyboardVisible ? "terminal_hide_keyboard" : "terminal_show_keyboard",
                keyboardVisible ? "Hide keyboard" : "Show keyboard"));
        button.setAlpha(keyboardVisible ? 1f : 0.8f);
    }

    private void updateTools() {
        View shortcuts = findSafeView("terminal_shortcuts");
        if (shortcuts != null) shortcuts.setVisibility(toolsVisible ? View.VISIBLE : View.GONE);
        View button = findSafeView("terminal_tools");
        if (button != null) {
            button.setSelected(toolsVisible);
            button.setContentDescription(safeString(toolsVisible ? "terminal_hide_tools" : "terminal_show_tools",
                    toolsVisible ? "Hide terminal shortcuts" : "Show terminal shortcuts"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("terminal_tools_visible", toolsVisible);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (terminalView != null && emulatorSession != null) terminalView.onResume();
        updateSessionBadge();
    }

    @Override
    protected void onPause() {
        if (terminalView != null && emulatorSession != null) terminalView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (session != null) session.removeListener(this);
        if (emulatorSession != null) emulatorSession.finish();
        if (bound) unbindService(connection);
        TerminalStartupLog.restoreCrashCapture(previousCrashHandler);
        super.onDestroy();
    }
}
