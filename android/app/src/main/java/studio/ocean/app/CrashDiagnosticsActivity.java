package studio.ocean.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** Always-available local crash/session diagnostics viewer. No diagnostic data is uploaded. */
public final class CrashDiagnosticsActivity extends AppCompatActivity {
    private TextView report;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Crash Diagnostics");

        int pad=dp(20);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(0xFFFFFFFF);

        TextView title=new TextView(this);
        title.setText("Crash Diagnostics");
        title.setTextSize(24f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF191817);
        root.addView(title);

        TextView sub=new TextView(this);
        sub.setText("Local-only crash and previous-session information. Nothing is uploaded automatically.");
        sub.setTextSize(14f);
        sub.setTextColor(0xFF77736E);
        sub.setPadding(0,dp(8),0,dp(14));
        root.addView(sub);

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button refresh=new Button(this); refresh.setText("Refresh");
        Button copy=new Button(this); copy.setText("Copy");
        actions.addView(refresh); actions.addView(copy);
        root.addView(actions);

        ScrollView scroll=new ScrollView(this);
        report=new TextView(this);
        report.setTextSize(12f);
        report.setTypeface(Typeface.MONOSPACE);
        report.setTextColor(0xFF191817);
        report.setTextIsSelectable(true);
        report.setPadding(0,dp(12),0,dp(24));
        scroll.addView(report);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        refresh.setOnClickListener(v->refresh());
        copy.setOnClickListener(v->{
            String text=report.getText().toString();
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Ocean crash diagnostics",text));
            Toast.makeText(this,"Crash diagnostics copied",Toast.LENGTH_SHORT).show();
        });

        setContentView(root);
        refresh();
    }

    @Override protected void onResume(){ super.onResume(); refresh(); }

    private void refresh(){
        String text=CrashSurvival.diagnosticSnapshot(this);
        report.setText(text.isEmpty() ? "No crash/session diagnostic data is available yet." : text);
    }

    private int dp(int value){ return Math.round(value*getResources().getDisplayMetrics().density); }
}
