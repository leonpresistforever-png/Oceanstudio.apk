package studio.ocean.app.render;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ocean's native X11/RFB renderer.
 *
 * The terminal owns session startup. This activity behaves like a display:
 * while no backend is listening it shows display preferences and continuously
 * waits for the selected localhost RFB port. Once a session appears the
 * framebuffer takes over the whole screen and controls become transient.
 */
public final class OceanX11Activity extends AppCompatActivity {
    public static final int DEFAULT_RFB_PORT = 5901;
    private static final AtomicReference<OceanX11Activity> ACTIVE = new AtomicReference<>();

    private static final int BLACK = 0xff050505;
    private static final int SURFACE = 0xff111111;
    private static final int SURFACE_2 = 0xff191919;
    private static final int BORDER = 0xff2b2b2b;
    private static final int TEXT = 0xfff3f3f3;
    private static final int MUTED = 0xff9b9b9b;
    private static final int SOFT = 0xff707070;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout root;
    private RfbSurface surface;
    private ScrollView menuScroll;
    private LinearLayout transientControls;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView dpiValue;
    private TextView resolutionValue;
    private TextView displayValue;
    private TextView scaleValue;
    private TextView orientationValue;
    private Switch fullscreenSwitch;
    private SeekBar dpiSeek;
    private boolean sessionConnected;
    private boolean destroyed;
    private boolean manualMenu;
    private int port = DEFAULT_RFB_PORT;
    private int displayNumber = 1;
    private int dpi = 120;
    private int resolutionIndex = 0;
    private int orientationIndex = 0;
    private int scaleMode = 0;

    private final String[] resolutions = {"1280 × 720", "1600 × 900", "1920 × 1080", "2560 × 1440"};
    private final String[] geometryValues = {"1280x720", "1600x900", "1920x1080", "2560x1440"};
    private final String[] orientationNames = {"Auto rotate", "Landscape", "Portrait"};
    private final String[] scaleNames = {"Fit screen", "Pixel 1:1", "Fill screen"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ACTIVE.set(this);
        prefs = getSharedPreferences("ocean_x11_display", MODE_PRIVATE);
        displayNumber = prefs.getInt("display", Math.max(1, getIntent().getIntExtra("port", DEFAULT_RFB_PORT) - 5900));
        port = getIntent().hasExtra("port") ? getIntent().getIntExtra("port", 5900 + displayNumber) : 5900 + displayNumber;
        dpi = prefs.getInt("dpi", 120);
        resolutionIndex = clamp(prefs.getInt("resolution", 0), 0, resolutions.length - 1);
        orientationIndex = clamp(prefs.getInt("orientation", 0), 0, orientationNames.length - 1);
        scaleMode = clamp(prefs.getInt("scale", 0), 0, scaleNames.length - 1);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        applyOrientation();
        applyFullscreen();
        writeRuntimeConfig();
        connect();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (surface != null) surface.close();
        ACTIVE.compareAndSet(this, null);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (sessionConnected) {
            if (transientControls.getVisibility() == View.VISIBLE) {
                fadeOut(transientControls);
                manualMenu = false;
            } else {
                fadeIn(transientControls);
            }
            return;
        }
        super.onBackPressed();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private int clamp(int n, int a, int b) { return Math.max(a, Math.min(b, n)); }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        setContentView(root);

        surface = new RfbSurface(this);
        surface.setScaleMode(scaleMode);
        surface.setMenuToggle(() -> runOnUiThread(() -> {
            if (!sessionConnected) return;
            if (transientControls.getVisibility() == View.VISIBLE) fadeOut(transientControls);
            else fadeIn(transientControls);
        }));
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        menuScroll = new ScrollView(this);
        menuScroll.setFillViewport(true);
        menuScroll.setBackgroundColor(BLACK);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(22), dp(28), dp(22), dp(40));
        menuScroll.addView(menu, new ScrollView.LayoutParams(-1, -2));
        root.addView(menuScroll, new FrameLayout.LayoutParams(-1, -1));

        TextView eyebrow = text("OCEAN DISPLAY", 11, true, SOFT);
        eyebrow.setLetterSpacing(.12f);
        menu.addView(eyebrow);

        TextView title = text("X11", 31, true, TEXT);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(-1, -2);
        titleP.topMargin = dp(7);
        menu.addView(title, titleP);

        TextView intro = text("A native full-screen display for graphical Linux apps running inside Ocean. Start the X11 session from Terminal; this screen waits for it and switches directly to the framebuffer when the display becomes available.", 14, false, MUTED);
        intro.setLineSpacing(dp(2), 1.08f);
        LinearLayout.LayoutParams introP = new LinearLayout.LayoutParams(-1, -2);
        introP.topMargin = dp(8);
        introP.bottomMargin = dp(20);
        menu.addView(intro, introP);

        LinearLayout stateCard = card();
        menu.addView(stateCard, cardParams());
        statusTitle = text("Waiting for session", 16, true, TEXT);
        stateCard.addView(statusTitle);
        statusDetail = text("Display :" + displayNumber + " · localhost:" + port + "\nRun the session from Ocean Terminal. You do not need a Start button on this screen.", 13, false, MUTED);
        statusDetail.setLineSpacing(dp(2), 1.08f);
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(-1, -2);
        statusP.topMargin = dp(6);
        stateCard.addView(statusDetail, statusP);

        LinearLayout stateActions = new LinearLayout(this);
        stateActions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stateActionsP = new LinearLayout.LayoutParams(-1, dp(46));
        stateActionsP.topMargin = dp(14);
        stateCard.addView(stateActions, stateActionsP);
        TextView reconnect = actionButton("Reconnect", false);
        reconnect.setOnClickListener(v -> connect());
        stateActions.addView(reconnect, weightButtonParams());
        TextView stop = actionButton("Stop session", true);
        stop.setOnClickListener(v -> runBackendStop());
        LinearLayout.LayoutParams stopP = weightButtonParams(); stopP.leftMargin = dp(8);
        stateActions.addView(stop, stopP);

        addSection(menu, "Display geometry", "These values become the defaults used by Ocean's X11 backend the next time you launch it from Terminal. Larger framebuffers give apps more workspace; DPI changes their logical scale rather than simply zooming the Android view.");

        LinearLayout displayCard = card();
        menu.addView(displayCard, cardParams());
        displayValue = valueRow(displayCard, "Display number", "Maps :1 to RFB 5901, :2 to 5902, and so on.", ":" + displayNumber);
        displayValue.setOnClickListener(v -> {
            displayNumber = displayNumber >= 3 ? 1 : displayNumber + 1;
            port = 5900 + displayNumber;
            prefs.edit().putInt("display", displayNumber).apply();
            displayValue.setText(":" + displayNumber);
            writeRuntimeConfig();
            setWaiting("Display changed", "The next terminal session will use :" + displayNumber + " on localhost:" + port + ". Reconnect after the backend is running.");
            connect();
        });
        divider(displayCard);
        resolutionValue = valueRow(displayCard, "Framebuffer size", "Controls the X server workspace. Tap to cycle through common desktop sizes.", resolutions[resolutionIndex]);
        resolutionValue.setOnClickListener(v -> {
            resolutionIndex = (resolutionIndex + 1) % resolutions.length;
            prefs.edit().putInt("resolution", resolutionIndex).apply();
            resolutionValue.setText(resolutions[resolutionIndex]);
            writeRuntimeConfig();
        });
        divider(displayCard);

        LinearLayout dpiBlock = new LinearLayout(this);
        dpiBlock.setOrientation(LinearLayout.VERTICAL);
        dpiBlock.setPadding(0, dp(14), 0, dp(10));
        displayCard.addView(dpiBlock, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout dpiTop = new LinearLayout(this);
        dpiTop.setGravity(Gravity.CENTER_VERTICAL);
        dpiBlock.addView(dpiTop);
        LinearLayout dpiCopy = new LinearLayout(this);
        dpiCopy.setOrientation(LinearLayout.VERTICAL);
        dpiTop.addView(dpiCopy, new LinearLayout.LayoutParams(0, -2, 1));
        dpiCopy.addView(text("Logical DPI", 15, true, TEXT));
        TextView dpiHelp = text("Increase this when desktop text and controls feel too small. Applied on the next backend start.", 12, false, MUTED);
        LinearLayout.LayoutParams dpiHelpP = new LinearLayout.LayoutParams(-1, -2); dpiHelpP.topMargin = dp(3);
        dpiCopy.addView(dpiHelp, dpiHelpP);
        dpiValue = text(dpi + " dpi", 13, true, TEXT);
        dpiTop.addView(dpiValue);
        dpiSeek = new SeekBar(this);
        dpiSeek.setMax(168);
        dpiSeek.setProgress(clamp(dpi - 72, 0, 168));
        tintSeek(dpiSeek);
        LinearLayout.LayoutParams seekP = new LinearLayout.LayoutParams(-1, dp(42)); seekP.topMargin = dp(8);
        dpiBlock.addView(dpiSeek, seekP);
        dpiSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                dpi = 72 + value;
                dpiValue.setText(dpi + " dpi");
                if (fromUser) {
                    prefs.edit().putInt("dpi", dpi).apply();
                    writeRuntimeConfig();
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        addSection(menu, "Renderer behavior", "These controls affect the Android display itself. They can be changed without restarting the X11 backend.");

        LinearLayout behavior = card();
        menu.addView(behavior, cardParams());
        scaleValue = valueRow(behavior, "Scaling", "Fit preserves the whole desktop; 1:1 keeps native pixels; Fill uses the entire phone screen.", scaleNames[scaleMode]);
        scaleValue.setOnClickListener(v -> {
            scaleMode = (scaleMode + 1) % scaleNames.length;
            prefs.edit().putInt("scale", scaleMode).apply();
            scaleValue.setText(scaleNames[scaleMode]);
            surface.setScaleMode(scaleMode);
        });
        divider(behavior);
        orientationValue = valueRow(behavior, "Screen orientation", "Auto follows the device. Landscape is usually best for desktop environments and wide app windows.", orientationNames[orientationIndex]);
        orientationValue.setOnClickListener(v -> {
            orientationIndex = (orientationIndex + 1) % orientationNames.length;
            prefs.edit().putInt("orientation", orientationIndex).apply();
            orientationValue.setText(orientationNames[orientationIndex]);
            applyOrientation();
        });
        divider(behavior);
        addFullscreenRow(behavior);

        TextView gestures = text("While a session is active the framebuffer is intentionally chrome-free. Press Android Back to reveal controls, or use a three-finger tap. Two-finger vertical movement sends mouse-wheel scrolling.", 12, false, SOFT);
        gestures.setLineSpacing(dp(2), 1.08f);
        LinearLayout.LayoutParams gesturesP = new LinearLayout.LayoutParams(-1, -2);
        gesturesP.topMargin = dp(18);
        menu.addView(gestures, gesturesP);

        transientControls = new LinearLayout(this);
        transientControls.setOrientation(LinearLayout.VERTICAL);
        transientControls.setPadding(dp(18), dp(16), dp(18), dp(18));
        transientControls.setBackground(roundRect(0xf2111111, 22, BORDER));
        transientControls.setVisibility(View.GONE);
        FrameLayout.LayoutParams overlayP = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        overlayP.setMargins(dp(12), dp(12), dp(12), dp(12));
        root.addView(transientControls, overlayP);
        buildTransientControls();

        surface.setVisibility(View.INVISIBLE);
        menuScroll.setVisibility(View.VISIBLE);
    }

    private void buildTransientControls() {
        transientControls.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        transientControls.addView(top);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView h = text("X11  :" + displayNumber, 15, true, TEXT);
        copy.addView(h);
        TextView d = text("Back hides controls · three-finger tap toggles this panel", 11, false, MUTED);
        LinearLayout.LayoutParams dP = new LinearLayout.LayoutParams(-1, -2); dP.topMargin = dp(2); copy.addView(d, dP);
        TextView close = actionButton("Hide", false);
        close.setOnClickListener(v -> fadeOut(transientControls));
        top.addView(close, new LinearLayout.LayoutParams(dp(72), dp(40)));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowP = new LinearLayout.LayoutParams(-1, dp(46)); rowP.topMargin = dp(14);
        transientControls.addView(row1, rowP);
        TextView keyboard = actionButton("Keyboard", false);
        keyboard.setOnClickListener(v -> surface.showKeyboard());
        row1.addView(keyboard, weightButtonParams());
        TextView scale = actionButton("Scale", false);
        scale.setOnClickListener(v -> {
            scaleMode = (scaleMode + 1) % scaleNames.length;
            prefs.edit().putInt("scale", scaleMode).apply();
            surface.setScaleMode(scaleMode);
            if (scaleValue != null) scaleValue.setText(scaleNames[scaleMode]);
        });
        LinearLayout.LayoutParams scaleP = weightButtonParams(); scaleP.leftMargin = dp(8); row1.addView(scale, scaleP);
        TextView reconnect = actionButton("Reconnect", false);
        reconnect.setOnClickListener(v -> connect());
        LinearLayout.LayoutParams reconnectP = weightButtonParams(); reconnectP.leftMargin = dp(8); row1.addView(reconnect, reconnectP);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2P = new LinearLayout.LayoutParams(-1, dp(46)); row2P.topMargin = dp(8);
        transientControls.addView(row2, row2P);
        TextView settings = actionButton("Display settings", false);
        settings.setOnClickListener(v -> {
            manualMenu = true;
            fadeIn(menuScroll);
            surface.setVisibility(View.VISIBLE);
            fadeOut(transientControls);
        });
        row2.addView(settings, weightButtonParams());
        TextView stop = actionButton("Stop", true);
        stop.setOnClickListener(v -> runBackendStop());
        LinearLayout.LayoutParams stopP = weightButtonParams(); stopP.leftMargin = dp(8);
        row2.addView(stop, stopP);
    }

    private void addSection(LinearLayout root, String title, String detail) {
        TextView h = text(title, 18, true, TEXT);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.topMargin = dp(26);
        root.addView(h, hp);
        TextView d = text(detail, 13, false, MUTED);
        d.setLineSpacing(dp(2), 1.06f);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, -2); dpv.topMargin = dp(5); dpv.bottomMargin = dp(10);
        root.addView(d, dpv);
    }

    private void addFullscreenRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        copy.addView(text("Immersive fullscreen", 15, true, TEXT));
        TextView detail = text("Hide Android status and navigation bars while rendering so the X11 desktop owns the entire display.", 12, false, MUTED);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(3); copy.addView(detail, p);
        fullscreenSwitch = new Switch(this);
        fullscreenSwitch.setChecked(prefs.getBoolean("fullscreen", true));
        tintSwitch(fullscreenSwitch);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(58), dp(42)); sp.leftMargin = dp(12);
        row.addView(fullscreenSwitch, sp);
        fullscreenSwitch.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("fullscreen", on).apply();
            applyFullscreen();
        });
    }

    private TextView valueRow(LinearLayout parent, String title, String detail, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        copy.addView(text(title, 15, true, TEXT));
        TextView d = text(detail, 12, false, MUTED);
        d.setLineSpacing(dp(1), 1.04f);
        LinearLayout.LayoutParams dP = new LinearLayout.LayoutParams(-1, -2); dP.topMargin = dp(3); copy.addView(d, dP);
        TextView v = text(value, 13, true, TEXT);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackground(roundRect(SURFACE_2, 12, BORDER));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, dp(38)); vp.leftMargin = dp(12);
        row.addView(v, vp);
        row.setOnClickListener(x -> v.performClick());
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(roundRect(SURFACE, 18, BORDER));
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams weightButtonParams() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private TextView actionButton(String label, boolean destructive) {
        TextView b = text(label, 13, true, destructive ? 0xffffb6ae : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundRect(destructive ? 0xff271616 : SURFACE_2, 13, destructive ? 0xff52231e : BORDER));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if ((stroke >>> 24) != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(BORDER);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void tintSwitch(Switch s) {
        if (Build.VERSION.SDK_INT >= 21) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_checked}
            };
            s.setThumbTintList(new ColorStateList(states, new int[]{0xffededed, 0xff8b8b8b}));
            s.setTrackTintList(new ColorStateList(states, new int[]{0xff525252, 0xff303030}));
        }
    }

    private void tintSeek(SeekBar s) {
        if (Build.VERSION.SDK_INT >= 21) {
            s.setThumbTintList(ColorStateList.valueOf(0xffd9d9d9));
            s.setProgressTintList(ColorStateList.valueOf(0xff777777));
            s.setProgressBackgroundTintList(ColorStateList.valueOf(0xff292929));
        }
    }

    private void fadeIn(View view) {
        if (view == null) return;
        view.animate().cancel();
        if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
        }
        view.animate().alpha(1f).setDuration(170).start();
    }

    private void fadeOut(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            view.setVisibility(View.GONE);
            view.setAlpha(1f);
        }).start();
    }

    private void applyFullscreen() {
        boolean full = prefs == null || prefs.getBoolean("fullscreen", true);
        View decor = getWindow().getDecorView();
        if (full) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void applyOrientation() {
        if (orientationIndex == 1) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        else if (orientationIndex == 2) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }

    private void writeRuntimeConfig() {
        try {
            File dir = new File(getFilesDir(), "usr/etc");
            if (!dir.exists()) dir.mkdirs();
            File cfg = new File(dir, "ocean-x11.conf");
            try (BufferedWriter w = new BufferedWriter(new FileWriter(cfg, false))) {
                w.write("DISPLAY=:" + displayNumber + "\n");
                w.write("GEOMETRY=" + geometryValues[resolutionIndex] + "\n");
                w.write("DPI=" + dpi + "\n");
                w.write("RFB_PORT=" + port + "\n");
            }
        } catch (Throwable ignored) {}
    }

    private void setWaiting(String title, String detail) {
        sessionConnected = false;
        statusTitle.setText(title);
        statusDetail.setText(detail);
        surface.setVisibility(View.INVISIBLE);
        fadeIn(menuScroll);
        fadeOut(transientControls);
    }

    private void showConnected(String detail) {
        sessionConnected = true;
        statusTitle.setText("Display connected");
        statusDetail.setText(detail);
        if (surface.getVisibility() != View.VISIBLE) {
            surface.setAlpha(0f);
            surface.setVisibility(View.VISIBLE);
            surface.animate().alpha(1f).setDuration(180).start();
        }
        if (!manualMenu) fadeOut(menuScroll);
        fadeOut(transientControls);
        applyFullscreen();
    }

    private void connect() {
        if (surface == null || destroyed) return;
        handler.removeCallbacks(reconnectRunnable);
        if (!sessionConnected) {
            statusTitle.setText("Looking for display :" + displayNumber);
            statusDetail.setText("Waiting on localhost:" + port + ". Start the backend from Ocean Terminal with ocean-x11-start :" + displayNumber + ".");
        }
        surface.connect("127.0.0.1", port, message -> runOnUiThread(() -> {
            if (destroyed) return;
            if (message.startsWith("CONNECTED|")) {
                manualMenu = false;
                showConnected(message.substring("CONNECTED|".length()));
            } else if (message.startsWith("WAITING|")) {
                String reason = message.substring("WAITING|".length());
                setWaiting("Waiting for terminal session", "Display :" + displayNumber + " is not active yet. " + reason + "\nOcean will attach automatically when the local RFB backend appears.");
                scheduleReconnect();
            } else {
                statusDetail.setText(message);
            }
        }));
    }

    private final Runnable reconnectRunnable = new Runnable() {
        @Override public void run() {
            if (!destroyed && !sessionConnected) connect();
        }
    };

    private void scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable);
        handler.postDelayed(reconnectRunnable, 1500);
    }

    private void runBackendStop() {
        new Thread(() -> {
            File command = new File(getFilesDir(), "usr/bin/ocean-x11-stop");
            if (!command.canExecute()) {
                runOnUiThread(() -> setWaiting("Stop command unavailable", "Install ocean-x11-runtime / ocean-x11-stop, or stop the backend process from Terminal."));
                return;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(command.getAbsolutePath());
                java.util.Map<String,String> env = pb.environment();
                for (String entry : studio.ocean.app.terminal.OceanEnvironment.create(this, "/system/bin/sh", false)) {
                    int eq = entry.indexOf('=');
                    if (eq > 0) env.put(entry.substring(0, eq), entry.substring(eq + 1));
                }
                pb.redirectErrorStream(true);
                java.lang.Process p = pb.start();
                StringBuilder out = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line; while ((line = r.readLine()) != null) out.append(line).append(' ');
                }
                int code = p.waitFor();
                runOnUiThread(() -> {
                    surface.close();
                    setWaiting(code == 0 ? "Session stopped" : "Stop returned " + code,
                            code == 0 ? "The renderer is idle. Start another display from Terminal when you are ready." : out.toString().trim());
                    scheduleReconnect();
                });
            } catch (Throwable t) {
                runOnUiThread(() -> setWaiting("Could not stop session", String.valueOf(t.getMessage())));
            }
        }, "ocean-x11-stop").start();
    }

    public static JSONObject control(String action, JSONObject args) {
        JSONObject o = new JSONObject();
        try {
            OceanX11Activity a = ACTIVE.get();
            if ("status".equals(action)) {
                o.put("open", a != null);
                o.put("connected", a != null && a.surface != null && a.surface.isConnected());
                o.put("port", a != null ? a.port : DEFAULT_RFB_PORT);
                o.put("display", a != null ? ":" + a.displayNumber : ":1");
                return o;
            }
            if (a == null) return o.put("success", false).put("error", "X11 renderer is not open");
            a.runOnUiThread(() -> {
                if ("fit".equals(action)) { a.scaleMode = 0; a.surface.setScaleMode(0); }
                else if ("pixel".equals(action)) { a.scaleMode = 1; a.surface.setScaleMode(1); }
                else if ("keyboard".equals(action)) a.surface.showKeyboard();
                else if ("reconnect".equals(action)) a.connect();
                else if ("stop".equals(action)) a.runBackendStop();
                else if ("settings".equals(action)) { a.manualMenu = true; a.menuScroll.setVisibility(View.VISIBLE); }
            });
            return o.put("success", true);
        } catch (Throwable t) {
            try { return o.put("success", false).put("error", String.valueOf(t.getMessage())); }
            catch (Throwable ignored) { return new JSONObject(); }
        }
    }

    public static void open(Context context, int port) {
        Intent i = new Intent(context, OceanX11Activity.class)
                .putExtra("port", port)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    private interface StatusSink { void update(String message); }

    private static final class RfbSurface extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint bg = new Paint();
        private final Object frameLock = new Object();
        private volatile Bitmap frame;
        private volatile RfbClient client;
        private volatile int scaleMode = 0;
        private float drawScale = 1f, drawLeft, drawTop;
        private Thread thread;
        private Runnable menuToggle;
        private float twoStartY;
        private boolean twoMoved;

        RfbSurface(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            bg.setColor(BLACK);
        }

        void setMenuToggle(Runnable r) { menuToggle = r; }

        void connect(String host, int port, StatusSink sink) {
            close();
            thread = new Thread(() -> {
                try {
                    RfbClient c = new RfbClient(host, port, (w, h, pixels) -> {
                        Bitmap next = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
                        synchronized (frameLock) {
                            Bitmap old = frame;
                            frame = next;
                            if (old != null && old != next && !old.isRecycled()) old.recycle();
                        }
                        postInvalidate();
                    }, message -> sink.update("CONNECTED|" + message));
                    client = c;
                    c.run();
                } catch (Throwable t) {
                    client = null;
                    String reason = String.valueOf(t.getMessage());
                    if (reason == null || reason.equals("null")) reason = "No local RFB server is listening.";
                    sink.update("WAITING|" + reason);
                }
            }, "ocean-x11-rfb");
            thread.setDaemon(true);
            thread.start();
        }

        boolean isConnected() { RfbClient c = client; return c != null && c.connected; }

        void close() {
            RfbClient c = client;
            client = null;
            if (c != null) c.close();
            Thread t = thread;
            thread = null;
            if (t != null) t.interrupt();
        }

        void setScaleMode(int mode) { scaleMode = Math.max(0, Math.min(2, mode)); invalidate(); }

        void showKeyboard() {
            requestFocus();
            InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bg);
            Bitmap b;
            synchronized (frameLock) { b = frame; }
            if (b == null || b.isRecycled()) return;
            if (scaleMode == 0) {
                drawScale = Math.min(getWidth() / (float)b.getWidth(), getHeight() / (float)b.getHeight());
            } else if (scaleMode == 2) {
                drawScale = Math.max(getWidth() / (float)b.getWidth(), getHeight() / (float)b.getHeight());
            } else drawScale = 1f;
            float dw = b.getWidth() * drawScale, dh = b.getHeight() * drawScale;
            drawLeft = (getWidth() - dw) * .5f;
            drawTop = (getHeight() - dh) * .5f;
            RectF dst = new RectF(drawLeft, drawTop, drawLeft + dw, drawTop + dh);
            canvas.drawBitmap(b, null, dst, paint);
        }

        private int remoteX(float x) {
            Bitmap b = frame;
            if (b == null) return 0;
            return Math.max(0, Math.min(b.getWidth() - 1, Math.round((x - drawLeft) / drawScale)));
        }
        private int remoteY(float y) {
            Bitmap b = frame;
            if (b == null) return 0;
            return Math.max(0, Math.min(b.getHeight() - 1, Math.round((y - drawTop) / drawScale)));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            RfbClient c = client;
            if (c == null) return true;
            int count = e.getPointerCount();
            if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && count >= 3) {
                if (menuToggle != null) menuToggle.run();
                return true;
            }
            if (count == 2) {
                float y = (e.getY(0) + e.getY(1)) * .5f;
                if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    twoStartY = y; twoMoved = false; return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dy = y - twoStartY;
                    if (Math.abs(dy) > 28) {
                        twoMoved = true;
                        int x = remoteX(e.getX(0)), ry = remoteY(e.getY(0));
                        c.pointer(dy < 0 ? 16 : 8, x, ry); c.pointer(0, x, ry);
                        twoStartY = y;
                    }
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP && !twoMoved) {
                    int x = remoteX(e.getX(0)), y0 = remoteY(e.getY(0));
                    c.pointer(4, x, y0); c.pointer(0, x, y0);
                    return true;
                }
            }
            int x = remoteX(e.getX()), y = remoteY(e.getY());
            int mask = (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) ? 0 : 1;
            c.pointer(mask, x, y);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) requestFocus();
            return true;
        }

        @Override public boolean onCheckIsTextEditor() { return true; }

        @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return new BaseInputConnection(this, false) {
                @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                    RfbClient c = client;
                    if (c != null && text != null) {
                        for (int i = 0; i < text.length(); i++) c.type(text.charAt(i));
                    }
                    return true;
                }
                @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    RfbClient c = client;
                    if (c != null) c.key(0xff08, true, true);
                    return true;
                }
            };
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            RfbClient c = client;
            if (c == null) return super.onKeyDown(keyCode, event);
            int sym = keysym(keyCode, event);
            if (sym != 0) { c.key(sym, true, false); return true; }
            return super.onKeyDown(keyCode, event);
        }

        @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
            RfbClient c = client;
            if (c == null) return super.onKeyUp(keyCode, event);
            int sym = keysym(keyCode, event);
            if (sym != 0) { c.key(sym, false, false); return true; }
            return super.onKeyUp(keyCode, event);
        }

        private int keysym(int keyCode, KeyEvent e) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER: return 0xff0d;
                case KeyEvent.KEYCODE_DEL: return 0xff08;
                case KeyEvent.KEYCODE_TAB: return 0xff09;
                case KeyEvent.KEYCODE_ESCAPE: return 0xff1b;
                case KeyEvent.KEYCODE_DPAD_LEFT: return 0xff51;
                case KeyEvent.KEYCODE_DPAD_UP: return 0xff52;
                case KeyEvent.KEYCODE_DPAD_RIGHT: return 0xff53;
                case KeyEvent.KEYCODE_DPAD_DOWN: return 0xff54;
                default:
                    int u = e.getUnicodeChar();
                    return u > 0 ? u : 0;
            }
        }
    }

    private interface FrameSink { void frame(int width, int height, int[] pixels); }

    private static final class RfbClient {
        private final String host;
        private final int port;
        private final FrameSink sink;
        private final StatusSink status;
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private int width, height;
        private int[] pixels;
        volatile boolean connected;

        RfbClient(String host, int port, FrameSink sink, StatusSink status) {
            this.host = host;
            this.port = port;
            this.sink = sink;
            this.status = status;
        }

        void run() throws Exception {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 1600);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            byte[] serverVersion = new byte[12];
            in.readFully(serverVersion);
            String version = new String(serverVersion, StandardCharsets.US_ASCII);
            if (!version.startsWith("RFB ")) throw new IOException("Not an RFB server");
            out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII)); out.flush();

            int count = in.readUnsignedByte();
            if (count == 0) throw new IOException(readReason());
            byte[] types = new byte[count]; in.readFully(types);
            boolean none = false;
            for (byte t : types) if ((t & 0xff) == 1) none = true;
            if (!none) throw new IOException("Local RFB backend requires authentication");
            out.writeByte(1); out.flush();
            int sec = in.readInt();
            if (sec != 0) throw new IOException("RFB security failed: " + readReason());

            out.writeByte(1); out.flush();
            width = in.readUnsignedShort();
            height = in.readUnsignedShort();
            byte[] pf = new byte[16]; in.readFully(pf);
            int nameLen = in.readInt();
            if (nameLen < 0 || nameLen > 1024 * 1024) throw new IOException("Bad desktop name length");
            byte[] name = new byte[nameLen]; in.readFully(name);
            pixels = new int[Math.max(1, width * height)];

            setPixelFormat();
            setEncodings();
            connected = true;
            status.update(width + " × " + height + " · " + new String(name, StandardCharsets.UTF_8));
            framebufferRequest(false);

            while (connected && !socket.isClosed()) {
                int type = in.readUnsignedByte();
                if (type == 0) readFramebufferUpdate();
                else if (type == 2) { }
                else if (type == 3) {
                    in.skipBytes(3);
                    int len = in.readInt();
                    if (len < 0 || len > 16 * 1024 * 1024) throw new IOException("Bad server clipboard");
                    byte[] clip = new byte[len]; in.readFully(clip);
                } else throw new IOException("Unsupported RFB server message " + type);
                framebufferRequest(true);
            }
        }

        private String readReason() throws IOException {
            int n = in.readInt();
            if (n < 0 || n > 1024 * 1024) return "unknown";
            byte[] b = new byte[n]; in.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }

        private void setPixelFormat() throws IOException {
            synchronized (this) {
                out.writeByte(0); out.write(new byte[3]);
                out.writeByte(32); out.writeByte(24); out.writeByte(0); out.writeByte(1);
                out.writeShort(255); out.writeShort(255); out.writeShort(255);
                out.writeByte(16); out.writeByte(8); out.writeByte(0);
                out.write(new byte[3]); out.flush();
            }
        }

        private void setEncodings() throws IOException {
            synchronized (this) {
                out.writeByte(2); out.writeByte(0); out.writeShort(2);
                out.writeInt(0);
                out.writeInt(-223);
                out.flush();
            }
        }

        private void framebufferRequest(boolean incremental) throws IOException {
            synchronized (this) {
                out.writeByte(3); out.writeByte(incremental ? 1 : 0);
                out.writeShort(0); out.writeShort(0); out.writeShort(width); out.writeShort(height);
                out.flush();
            }
        }

        private void readFramebufferUpdate() throws IOException {
            in.readUnsignedByte();
            int rects = in.readUnsignedShort();
            for (int r = 0; r < rects; r++) {
                int x=in.readUnsignedShort(), y=in.readUnsignedShort(),
                        w=in.readUnsignedShort(), h=in.readUnsignedShort();
                int enc=in.readInt();
                if (enc == -223) {
                    width=w; height=h; pixels=new int[Math.max(1,w*h)];
                    continue;
                }
                if (enc != 0) throw new IOException("RFB encoding " + enc + " not supported");
                byte[] row = new byte[w * 4];
                for (int yy=0; yy<h; yy++) {
                    in.readFully(row);
                    int dst=(y+yy)*width+x;
                    for (int xx=0; xx<w && dst+xx<pixels.length; xx++) {
                        int i=xx*4;
                        int b=row[i]&255,g=row[i+1]&255,rr=row[i+2]&255;
                        pixels[dst+xx]=0xff000000|(rr<<16)|(g<<8)|b;
                    }
                }
            }
            sink.frame(width,height,pixels.clone());
        }

        void pointer(int mask,int x,int y) {
            try {
                synchronized (this) {
                    if (!connected || out == null) return;
                    out.writeByte(5); out.writeByte(mask); out.writeShort(x); out.writeShort(y); out.flush();
                }
            } catch (Throwable ignored) {}
        }

        void type(char c) { key(c,true,true); }

        void key(int sym, boolean down, boolean tap) {
            try {
                synchronized (this) {
                    if (!connected || out == null) return;
                    out.writeByte(4); out.writeByte(down?1:0); out.writeShort(0); out.writeInt(sym);
                    if (tap) { out.writeByte(4); out.writeByte(0); out.writeShort(0); out.writeInt(sym); }
                    out.flush();
                }
            } catch (Throwable ignored) {}
        }

        void close() {
            connected=false;
            try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        }
    }
}
