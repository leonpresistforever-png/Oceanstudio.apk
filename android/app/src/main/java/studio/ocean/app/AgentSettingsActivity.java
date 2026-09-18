package studio.ocean.app;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public final class AgentSettingsActivity extends AppCompatActivity {
    private OceanAgentSettings settings;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_agent_settings);
        settings = new OceanAgentSettings(this);
        load();

        findViewById(R.id.agent_save_settings).setOnClickListener(v -> save());
        findViewById(R.id.agent_reset_settings).setOnClickListener(v -> {
            settings.reset();
            load();
            Toast.makeText(this, "Agent settings reset", Toast.LENGTH_SHORT).show();
        });
    }

    private void load() {
        ((EditText)findViewById(R.id.agent_temperature)).setText(String.valueOf(settings.temperature()));
        ((EditText)findViewById(R.id.agent_top_p)).setText(String.valueOf(settings.topP()));
        ((EditText)findViewById(R.id.agent_max_tokens)).setText(String.valueOf(settings.maxTokens()));
        ((EditText)findViewById(R.id.agent_connect_timeout)).setText(String.valueOf(settings.connectTimeoutMs()));
        ((EditText)findViewById(R.id.agent_read_timeout)).setText(String.valueOf(settings.readTimeoutMs()));
        ((EditText)findViewById(R.id.agent_command_timeout)).setText(String.valueOf(settings.commandTimeoutSeconds()));
        ((Switch)findViewById(R.id.agent_keep_session)).setChecked(settings.keepSessionAlive());
        ((Switch)findViewById(R.id.agent_anti_timeout)).setChecked(settings.antiTimeout());
        ((EditText)findViewById(R.id.agent_instructions)).setText(settings.userInstructions());
    }

    private void save() {
        try {
            settings.save(
                    Float.parseFloat(((EditText)findViewById(R.id.agent_temperature)).getText().toString()),
                    Float.parseFloat(((EditText)findViewById(R.id.agent_top_p)).getText().toString()),
                    Integer.parseInt(((EditText)findViewById(R.id.agent_max_tokens)).getText().toString()),
                    Integer.parseInt(((EditText)findViewById(R.id.agent_connect_timeout)).getText().toString()),
                    Integer.parseInt(((EditText)findViewById(R.id.agent_read_timeout)).getText().toString()),
                    Integer.parseInt(((EditText)findViewById(R.id.agent_command_timeout)).getText().toString()),
                    settings.maxRounds(), settings.maxToolCalls(),
                    ((Switch)findViewById(R.id.agent_keep_session)).isChecked(),
                    ((Switch)findViewById(R.id.agent_anti_timeout)).isChecked(),
                    ((EditText)findViewById(R.id.agent_instructions)).getText().toString());
            Toast.makeText(this, "Agent settings saved", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "Check the numeric values", Toast.LENGTH_LONG).show();
        }
    }
}
