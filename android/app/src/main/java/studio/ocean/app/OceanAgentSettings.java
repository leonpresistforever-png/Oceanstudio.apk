package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted user controls for model generation and session behavior. */
public final class OceanAgentSettings {
    public static final String PREFS="ocean_agent_settings";
    private final SharedPreferences prefs;

    public OceanAgentSettings(Context context){ prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }
    public float temperature(){ return clamp(prefs.getFloat("temperature",0.7f),0f,2f); }
    public float topP(){ return clamp(prefs.getFloat("top_p",1.0f),0.01f,1f); }
    public int maxTokens(){ return clamp(prefs.getInt("max_tokens",2048),128,32768); }
    public int connectTimeoutMs(){ return clamp(prefs.getInt("connect_timeout_ms",20000),5000,120000); }
    public int readTimeoutMs(){ return clamp(prefs.getInt("read_timeout_ms",60000),10000,600000); }
    public int commandTimeoutSeconds(){ return clamp(prefs.getInt("command_timeout_seconds",120),5,1800); }
    public int maxRounds(){ return clamp(prefs.getInt("max_rounds",16),1,64); }
    public int maxToolCalls(){ return clamp(prefs.getInt("max_tool_calls",24),1,128); }
    public boolean keepSessionAlive(){ return prefs.getBoolean("keep_session_alive",true); }
    public boolean antiTimeout(){ return prefs.getBoolean("anti_timeout",true); }
    public String userInstructions(){ return prefs.getString("user_instructions",""); }
    public String reasoningEffort(){ String v=prefs.getString("reasoning_effort","default"); return ("low".equals(v)||"medium".equals(v)||"high".equals(v))?v:"default"; }

    public void save(float temperature,float topP,int maxTokens,int connectMs,int readMs,int commandSeconds,
                     int maxRounds,int maxToolCalls,boolean keepAlive,boolean antiTimeout,String reasoningEffort,String instructions){
        prefs.edit()
                .putFloat("temperature",clamp(temperature,0f,2f))
                .putFloat("top_p",clamp(topP,0.01f,1f))
                .putInt("max_tokens",clamp(maxTokens,128,32768))
                .putInt("connect_timeout_ms",clamp(connectMs,5000,120000))
                .putInt("read_timeout_ms",clamp(readMs,10000,600000))
                .putInt("command_timeout_seconds",clamp(commandSeconds,5,1800))
                .putInt("max_rounds",clamp(maxRounds,1,64))
                .putInt("max_tool_calls",clamp(maxToolCalls,1,128))
                .putBoolean("keep_session_alive",keepAlive)
                .putBoolean("anti_timeout",antiTimeout)
                .putString("reasoning_effort",("low".equals(reasoningEffort)||"medium".equals(reasoningEffort)||"high".equals(reasoningEffort))?reasoningEffort:"default")
                .putString("user_instructions",instructions==null?"":instructions.trim())
                .apply();
    }

    public String signature(){ return temperature()+"|"+topP()+"|"+maxTokens()+"|"+connectTimeoutMs()+"|"+readTimeoutMs()+"|"+commandTimeoutSeconds()+"|"+maxRounds()+"|"+maxToolCalls()+"|"+keepSessionAlive()+"|"+antiTimeout()+"|"+reasoningEffort()+"|"+userInstructions().hashCode(); }

    public void reset(){ prefs.edit().clear().apply(); }
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
}
