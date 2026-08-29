package studio.ocean.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** The native Android workspace shell. No browser bridge or WebView is involved. */
public class MainActivity extends AppCompatActivity {
    private static final int[] TAB_IDS = {
            R.id.tab_agent, R.id.tab_editor, R.id.tab_terminal, R.id.tab_files, R.id.tab_tools
    };

    private TextView screenTitle;
    private TextView emptyTitle;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenTitle = findViewById(R.id.screen_title);
        emptyTitle = findViewById(R.id.empty_title);
        emptyMessage = findViewById(R.id.empty_message);

        for (int tabId : TAB_IDS) {
            findViewById(tabId).setOnClickListener(this::selectTab);
        }
        selectTab(findViewById(R.id.tab_agent));
    }

    private void selectTab(View selected) {
        for (int tabId : TAB_IDS) {
            findViewById(tabId).setSelected(tabId == selected.getId());
        }

        if (selected.getId() == R.id.tab_agent) {
            showScreen("Agent", "Your native coding agent", "Choose a model and ask Ocean to help with your workspace.");
        } else if (selected.getId() == R.id.tab_editor) {
            showScreen("Editor", "No file open", "Select a file from Files to start editing.");
        } else if (selected.getId() == R.id.tab_terminal) {
            showScreen("Terminal", "Terminal is next", "The native terminal runtime will be connected in the next milestone.");
        } else if (selected.getId() == R.id.tab_files) {
            showScreen("Files", "No workspace selected", "Open or create a local Android workspace.");
        } else {
            showScreen("Tools", "Native tools", "Providers, agents, integrations, skills and plugins will live here.");
        }
    }

    private void showScreen(String title, String heading, String message) {
        screenTitle.setText(title);
        emptyTitle.setText(heading);
        emptyMessage.setText(message);
    }
}
