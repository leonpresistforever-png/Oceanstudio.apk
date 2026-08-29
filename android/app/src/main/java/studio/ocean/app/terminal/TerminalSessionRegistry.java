package studio.ocean.app.terminal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-backed session registry owned by the runtime service. */
final class TerminalSessionRegistry {
    private final Map<String,TerminalSession> sessions = new LinkedHashMap<>();
    synchronized void add(TerminalSession session) { sessions.put(session.id(),session); }
    synchronized TerminalSession get(String id) { return sessions.get(id); }
    synchronized Collection<TerminalSession> all() { return new java.util.ArrayList<>(sessions.values()); }
    synchronized void remove(String id) { TerminalSession session=sessions.remove(id); if(session!=null)session.close(); }
    synchronized void closeAll() { for(TerminalSession session:sessions.values())session.close(); sessions.clear(); }
}
