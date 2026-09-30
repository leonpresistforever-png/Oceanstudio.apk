package studio.ocean.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Postman-style Params / Body / Auth / Headers editor (greyscale). */
final class HttpRequestEditor {
    private final Context context;
    private final LinearLayout root;
    private final LinearLayout tabRow;
    private final FrameLayout pane;
    private final EditText method;
    private final EditText url;
    private final LinearLayout paramsPane;
    private final EditText bodyPane;
    private final Spinner bodyType;
    private final RadioGroup authGroup;
    private final EditText bearerToken;
    private final EditText basicUser;
    private final EditText basicPass;
    private final LinearLayout headersPane;
    private String active = "params";

    HttpRequestEditor(Context context) {
        this.context = context;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        method = new EditText(context);
        method.setHint("GET");
        method.setText("GET");
        method.setWidth(dp(72));
        url = new EditText(context);
        url.setHint("https://api.example.com/v1/resource");
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(method);
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
        paramsPane = buildKeyValuePane();
        bodyPane = new EditText(context);
        bodyPane.setMinLines(6);
        bodyPane.setHint("{\"key\":\"value\"}");
        bodyPane.setTypeface(Typeface.MONOSPACE);
        bodyType = new Spinner(context);
        bodyType.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"None", "JSON", "Form (url-encoded)", "XML", "Custom"}));
        bodyType.setSelection(1);
        LinearLayout bodyWrap = new LinearLayout(context);
        bodyWrap.setOrientation(LinearLayout.VERTICAL);
        bodyWrap.addView(bodyType);
        bodyWrap.addView(bodyPane);

        LinearLayout authWrap = new LinearLayout(context);
        authWrap.setOrientation(LinearLayout.VERTICAL);
        authGroup = new RadioGroup(context);
        authGroup.setOrientation(LinearLayout.VERTICAL);
        addRadio(authGroup, "None");
        addRadio(authGroup, "Bearer Token");
        addRadio(authGroup, "Basic Auth");
        addRadio(authGroup, "Custom");
        authWrap.addView(authGroup);
        bearerToken = new EditText(context);
        bearerToken.setHint("Bearer token");
        basicUser = new EditText(context);
        basicUser.setHint("Username");
        basicPass = new EditText(context);
        basicPass.setHint("Password");
        authWrap.addView(bearerToken);
        authWrap.addView(basicUser);
        authWrap.addView(basicPass);
        authGroup.check(authGroup.getChildAt(1).getId());

        headersPane = buildKeyValuePane();

        pane.addView(paramsPane);
        pane.addView(bodyWrap);
        pane.addView(authWrap);
        pane.addView(headersPane);
        bodyWrap.setVisibility(View.GONE);
        authWrap.setVisibility(View.GONE);
        headersPane.setVisibility(View.GONE);
        root.addView(pane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
    }

    View view() { return root; }

    JSONObject toJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put("method", method.getText().toString().trim().isEmpty() ? "GET" : method.getText().toString().trim().toUpperCase());
        json.put("url", url.getText().toString().trim());
        json.put("params", readPairs(paramsPane));
        json.put("headers", readPairs(headersPane));
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

    private JSONArray readPairs(LinearLayout pane) throws Exception {
        JSONArray arr = new JSONArray();
        for (int i = 1; i < pane.getChildCount(); i++) {
            View row = pane.getChildAt(i);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout lr = (LinearLayout) row;
            EditText k = (EditText) lr.getChildAt(0);
            EditText v = (EditText) lr.getChildAt(1);
            String key = k.getText().toString().trim();
            if (key.isEmpty()) continue;
            arr.put(new JSONObject().put("key", key).put("value", v.getText().toString()));
        }
        return arr;
    }

    private LinearLayout buildKeyValuePane() {
        LinearLayout pane = new LinearLayout(context);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.addView(headerRow("Key", "Value"));
        addKvRow(pane);
        addKvRow(pane);
        Button more = new Button(context);
        more.setText("+ Row");
        more.setAllCaps(false);
        more.setOnClickListener(v -> addKvRow(pane));
        pane.addView(more);
        return pane;
    }

    private void addKvRow(LinearLayout pane) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText k = new EditText(context);
        k.setHint("key");
        EditText v = new EditText(context);
        v.setHint("value");
        row.addView(k, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pane.addView(row);
    }

    private LinearLayout headerRow(String a, String b) {
        LinearLayout row = new LinearLayout(context);
        TextView ka = new TextView(context);
        ka.setText(a);
        ka.setTypeface(null, Typeface.BOLD);
        TextView kb = new TextView(context);
        kb.setText(b);
        kb.setTypeface(null, Typeface.BOLD);
        row.addView(ka, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(kb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void addTab(String id, String label) {
        TextView tab = new TextView(context);
        tab.setText(label);
        tab.setPadding(dp(10), dp(6), dp(10), dp(6));
        tab.setTag(id);
        tab.setOnClickListener(v -> selectTab(id));
        tabRow.addView(tab);
    }

    private void selectTab(String id) {
        active = id;
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            TextView t = (TextView) tabRow.getChildAt(i);
            boolean on = id.equals(t.getTag());
            t.setTextColor(on ? 0xFF111111 : 0xFF9CA3AF);
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        paramsPane.setVisibility("params".equals(id) ? View.VISIBLE : View.GONE);
        View body = (View) bodyPane.getParent();
        body.setVisibility("body".equals(id) ? View.VISIBLE : View.GONE);
        View auth = (View) bearerToken.getParent();
        auth.setVisibility("auth".equals(id) ? View.VISIBLE : View.GONE);
        headersPane.setVisibility("headers".equals(id) ? View.VISIBLE : View.GONE);
    }

    private void addRadio(RadioGroup group, String text) {
        RadioButton rb = new RadioButton(context);
        rb.setText(text);
        rb.setTextColor(0xFF111111);
        group.addView(rb);
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
