package studio.ocean.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.widget.NestedScrollView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Postman-style Params / Body / Auth / Headers editor (Directive 3 §2.1).
 * Features dynamic KeyValue rows with compact remove controls, secondary + Row button,
 * scrollable wrap-content container, and duplicate/blank key validation.
 */
final class HttpRequestEditor {
    private final Context context;
    private final LinearLayout root;
    private final LinearLayout tabRow;
    private final FrameLayout pane;
    private final EditText method;
    private final EditText url;
    private final KeyValueEditor paramsEditor;
    private final KeyValueEditor headersEditor;
    private final EditText bodyPane;
    private final Spinner bodyType;
    private final RadioGroup authGroup;
    private final EditText bearerToken;
    private final EditText basicUser;
    private final EditText basicPass;
    private final LinearLayout bodyWrap;
    private final LinearLayout authWrap;

    static final class KeyValueRow {
        final String id;
        String key;
        String value;

        KeyValueRow(String id, String key, String value) {
            this.id = id;
            this.key = key;
            this.value = value;
        }
    }

    static JSONArray validateRows(List<KeyValueRow> rows) throws Exception {
        JSONArray arr = new JSONArray();
        Set<String> seenKeys = new HashSet<>();
        for (KeyValueRow r : rows) {
            String k = r.key != null ? r.key.trim() : "";
            String v = r.value != null ? r.value : "";
            if (k.isEmpty() && v.isEmpty()) continue;
            if (k.isEmpty() && !v.isEmpty()) {
                throw new IllegalArgumentException("Key cannot be empty for value: " + v);
            }
            if (seenKeys.contains(k)) {
                throw new IllegalArgumentException("Duplicate key: " + k);
            }
            seenKeys.add(k);
            arr.put(new JSONObject().put("key", k).put("value", v));
        }
        return arr;
    }

    private static final class KeyValueEditor {
        final Context context;
        final LinearLayout container;
        final LinearLayout rowsList;
        final List<KeyValueRow> rows = new ArrayList<>();
        final List<View> rowViews = new ArrayList<>();

        KeyValueEditor(Context context) {
            this.context = context;
            container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);

            container.addView(headerRow("Key", "Value"));

            rowsList = new LinearLayout(context);
            rowsList.setOrientation(LinearLayout.VERTICAL);
            container.addView(rowsList);

            addRow("", "");
            addRow("", "");

            TextView moreBtn = new TextView(context);
            moreBtn.setText("+ Row");
            moreBtn.setTextColor(0xFF111111);
            moreBtn.setTextSize(13f);
            moreBtn.setTypeface(null, Typeface.BOLD);
            moreBtn.setGravity(Gravity.CENTER);
            moreBtn.setMinHeight(dp(40));
            moreBtn.setPadding(dp(16), dp(8), dp(16), dp(8));

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(0xFFF9FAFB);
            btnBg.setCornerRadius(dp(12));
            btnBg.setStroke(dp(1), 0xFFE5E7EB);
            moreBtn.setBackground(btnBg);

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            btnLp.topMargin = dp(8);
            btnLp.bottomMargin = dp(8);
            moreBtn.setLayoutParams(btnLp);
            moreBtn.setOnClickListener(v -> addRow("", ""));
            container.addView(moreBtn);
        }

        View getView() {
            return container;
        }

        void addRow(String key, String value) {
            String rowId = UUID.randomUUID().toString();
            KeyValueRow model = new KeyValueRow(rowId, key, value);
            rows.add(model);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            EditText k = new EditText(context);
            k.setHint("key");
            k.setText(key);
            k.setBackgroundResource(R.drawable.auth_field_background);
            k.setPadding(dp(12), dp(10), dp(12), dp(10));
            k.addTextChangedListener(new SimpleTextWatcher(s -> model.key = s));

            EditText v = new EditText(context);
            v.setHint("value");
            v.setText(value);
            v.setBackgroundResource(R.drawable.auth_field_background);
            v.setPadding(dp(12), dp(10), dp(12), dp(10));
            v.addTextChangedListener(new SimpleTextWatcher(s -> model.value = s));

            TextView removeBtn = new TextView(context);
            removeBtn.setText("✕");
            removeBtn.setTextSize(16f);
            removeBtn.setTextColor(0xFF9CA3AF);
            removeBtn.setGravity(Gravity.CENTER);
            removeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
            removeBtn.setMinWidth(dp(48));
            removeBtn.setMinHeight(dp(48));
            removeBtn.setOnClickListener(click -> removeRow(model.id));

            LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            klp.rightMargin = dp(6);
            row.addView(k, klp);

            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            vlp.rightMargin = dp(6);
            row.addView(v, vlp);

            row.addView(removeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(4);
            rowsList.addView(row, rowLp);
            rowViews.add(row);
        }

        void removeRow(String rowId) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).id.equals(rowId)) {
                    rows.remove(i);
                    View v = rowViews.remove(i);
                    rowsList.removeView(v);
                    break;
                }
            }
        }

        JSONArray toJson() throws Exception {
            return validateRows(rows);
        }

        private LinearLayout headerRow(String a, String b) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView ka = new TextView(context);
            ka.setText(a);
            ka.setTextColor(0xFF737373);
            ka.setTextSize(11f);
            ka.setTypeface(null, Typeface.BOLD);
            TextView kb = new TextView(context);
            kb.setText(b);
            kb.setTextColor(0xFF737373);
            kb.setTextSize(11f);
            kb.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams kalp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            kalp.rightMargin = dp(6);
            row.addView(ka, kalp);
            LinearLayout.LayoutParams kblp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            kblp.rightMargin = dp(48);
            row.addView(kb, kblp);
            return row;
        }

        private int dp(int v) {
            return Math.round(v * context.getResources().getDisplayMetrics().density);
        }
    }

    private static final class SimpleTextWatcher implements TextWatcher {
        interface Consumer { void accept(String s); }
        private final Consumer consumer;
        SimpleTextWatcher(Consumer consumer) { this.consumer = consumer; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { consumer.accept(s != null ? s.toString() : ""); }
    }

    HttpRequestEditor(Context context) {
        this.context = context;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        method = new EditText(context);
        method.setHint("GET");
        method.setText("GET");
        method.setBackgroundResource(R.drawable.auth_field_background);
        method.setPadding(dp(12), dp(10), dp(12), dp(10));
        method.setTypeface(Typeface.MONOSPACE);

        url = new EditText(context);
        url.setHint("https://api.example.com/v1/resource");
        url.setBackgroundResource(R.drawable.auth_field_background);
        url.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams methodLp = new LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT);
        methodLp.rightMargin = dp(8);
        method.setGravity(Gravity.CENTER);
        top.addView(method, methodLp);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(url, urlLp);
        root.addView(top);

        tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(0, dp(12), 0, dp(4));
        root.addView(tabRow);
        addTab("params", "Params");
        addTab("body", "Body");
        addTab("auth", "Auth");
        addTab("headers", "Headers");

        pane = new FrameLayout(context);
        paramsEditor = new KeyValueEditor(context);
        headersEditor = new KeyValueEditor(context);

        bodyPane = new EditText(context);
        bodyPane.setMinLines(6);
        bodyPane.setHint("{\"key\":\"value\"}");
        bodyPane.setTypeface(Typeface.MONOSPACE);
        bodyPane.setBackgroundResource(R.drawable.auth_field_background);
        bodyPane.setPadding(dp(10), dp(10), dp(10), dp(10));

        bodyType = new Spinner(context);
        bodyType.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"None", "JSON", "Form (url-encoded)", "XML", "Custom"}));
        bodyType.setSelection(1);

        bodyWrap = new LinearLayout(context);
        bodyWrap.setOrientation(LinearLayout.VERTICAL);
        label(bodyWrap, "Body type");
        bodyWrap.addView(bodyType);
        bodyWrap.addView(bodyPane, matchWidth());

        authWrap = new LinearLayout(context);
        authWrap.setOrientation(LinearLayout.VERTICAL);
        authGroup = new RadioGroup(context);
        authGroup.setOrientation(LinearLayout.VERTICAL);
        addRadio(authGroup, "None");
        addRadio(authGroup, "Bearer Token");
        addRadio(authGroup, "Basic Auth");
        addRadio(authGroup, "Custom");
        authWrap.addView(authGroup);
        bearerToken = field("Bearer token");
        basicUser = field("Username");
        basicPass = field("Password");
        authWrap.addView(bearerToken);
        authWrap.addView(basicUser);
        authWrap.addView(basicPass);
        authGroup.check(authGroup.getChildAt(1).getId());

        pane.addView(paramsEditor.getView());
        pane.addView(bodyWrap);
        pane.addView(authWrap);
        pane.addView(headersEditor.getView());
        bodyWrap.setVisibility(View.GONE);
        authWrap.setVisibility(View.GONE);
        headersEditor.getView().setVisibility(View.GONE);

        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(pane, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.weight = 1f;
        root.addView(scroll, scrollLp);

        selectTab("params");
    }

    View view() { return root; }

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("method", method.getText().toString().trim().isEmpty() ? "GET" : method.getText().toString().trim().toUpperCase(Locale.ROOT));
        json.put("url", url.getText().toString().trim());
        json.put("params", paramsEditor.toJson());
        json.put("headers", headersEditor.toJson());
        String bodyKind = bodyType.getSelectedItem().toString();
        json.put("body_type", bodyKind);
        json.put("body", bodyPane.getText().toString());
        int checked = authGroup.getCheckedRadioButtonId();
        String auth = "none";
        for (int i = 0; i < authGroup.getChildCount(); i++) {
            RadioButton rb = (RadioButton) authGroup.getChildAt(i);
            if (rb.getId() == checked) auth = rb.getText().toString().toLowerCase(Locale.ROOT);
        }
        json.put("auth", auth);
        if (auth.contains("bearer")) json.put("bearer", bearerToken.getText().toString());
        if (auth.contains("basic")) {
            json.put("basic_user", basicUser.getText().toString());
            json.put("basic_pass", basicPass.getText().toString());
        }
        return json;
    }

    private EditText field(String hint) {
        EditText e = new EditText(context);
        e.setHint(hint);
        e.setBackgroundResource(R.drawable.auth_field_background);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private void label(LinearLayout parent, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextColor(0xFF9CA3AF);
        t.setTextSize(11f);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(6), 0, dp(4));
        parent.addView(t);
    }

    private void addTab(String id, String label) {
        TextView tab = new TextView(context);
        tab.setText(label);
        tab.setPadding(dp(12), dp(6), dp(12), dp(6));
        tab.setTag(id);
        tab.setOnClickListener(v -> selectTab(id));
        tabRow.addView(tab);
    }

    private void selectTab(String id) {
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView t = (TextView) tabRow.getChildAt(i);
            boolean on = id.equals(t.getTag());
            t.setTextColor(on ? 0xFF111111 : 0xFF737373);
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            t.setBackgroundResource(on ? R.drawable.tab_background : android.R.color.transparent);
        }
        paramsEditor.getView().setVisibility("params".equals(id) ? View.VISIBLE : View.GONE);
        bodyWrap.setVisibility("body".equals(id) ? View.VISIBLE : View.GONE);
        authWrap.setVisibility("auth".equals(id) ? View.VISIBLE : View.GONE);
        headersEditor.getView().setVisibility("headers".equals(id) ? View.VISIBLE : View.GONE);
    }

    private void addRadio(RadioGroup group, String text) {
        RadioButton rb = new RadioButton(context);
        rb.setText(text);
        rb.setTextColor(0xFF111111);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF111111));
        }
        group.addView(rb);
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
