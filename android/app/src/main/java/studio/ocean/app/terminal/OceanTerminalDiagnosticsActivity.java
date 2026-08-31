package studio.ocean.app.terminal;

import android.content.*;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** Temporary full black-box diagnostic viewer. */
public final class OceanTerminalDiagnosticsActivity extends AppCompatActivity {
 @Override protected void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);TextView title=new TextView(this);title.setText("Ocean Terminal Diagnostics");title.setTextSize(20);TextView report=new TextView(this);report.setTextIsSelectable(true);report.setTypeface(android.graphics.Typeface.MONOSPACE);report.setTextSize(11);report.setMovementMethod(new ScrollingMovementMethod());report.setText(TerminalDiagnosticBundle.combine(this));LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);Button copy=button("Copy all",v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Ocean Terminal Diagnostics",TerminalDiagnosticBundle.combine(this)));Toast.makeText(this,"Diagnostic copied",Toast.LENGTH_SHORT).show();});Button share=button("Share diagnostic",v->{Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,TerminalDiagnosticBundle.combine(this));startActivity(Intent.createChooser(i,"Share diagnostic"));});Button system=button("PTY test A · Android system shell",v->test("system"));Button ocean=button("PTY tests B/C · Ocean Bash + OCEAN_PTY_OK",v->test("ocean"));actions.addView(copy);actions.addView(share);actions.addView(system);actions.addView(ocean);root.addView(title);root.addView(actions);ScrollView scroll=new ScrollView(this);scroll.addView(report);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
 private Button button(String text,android.view.View.OnClickListener listener){Button b=new Button(this);b.setText(text);b.setOnClickListener(listener);return b;}
 private void test(String mode){startActivity(new Intent(this,OceanTerminalActivity.class).putExtra("diagnostic_shell",mode));}
}
