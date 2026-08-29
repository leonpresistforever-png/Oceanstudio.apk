package studio.ocean.app.terminal;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads kernel procfs for a service-owned child; no network or remote endpoint is involved. */
public final class LocalProcessDiagnostics {
    public static final class Snapshot {
        public final int pid, parentPid, uid;
        public final String executable, cwd, oceanPrefix;
        public final boolean executableInsideOceanPrefix;
        Snapshot(int pid,int parentPid,int uid,String executable,String cwd,String prefix){
            this.pid=pid;this.parentPid=parentPid;this.uid=uid;this.executable=executable;
            this.cwd=cwd;this.oceanPrefix=prefix;
            this.executableInsideOceanPrefix=isWithinPrefix(executable,prefix);
        }
        public String summary(){return "PID "+pid+" · PPID "+parentPid+" · UID "+uid+" · "+executable+" · cwd "+cwd+" · PREFIX "+oceanPrefix;}
    }
    private LocalProcessDiagnostics() {}
    public static Snapshot inspect(int pid,String prefix){
        if(pid<=0)return new Snapshot(pid,-1,-1,"unavailable","unavailable",prefix);
        try {
            Path proc=new File("/proc/"+pid).toPath();
            String exe=Files.readSymbolicLink(proc.resolve("exe")).toString();
            String cwd=Files.readSymbolicLink(proc.resolve("cwd")).toString();
            int parent=-1,uid=-1;
            List<String> status=Files.readAllLines(proc.resolve("status"),StandardCharsets.UTF_8);
            for(String line:status){
                if(line.startsWith("PPid:"))parent=parseFirstNumber(line);
                else if(line.startsWith("Uid:"))uid=parseFirstNumber(line);
            }
            return new Snapshot(pid,parent,uid,exe,cwd,prefix);
        } catch(Exception unavailable){return new Snapshot(pid,-1,-1,"unavailable","unavailable",prefix);}
    }
    static boolean isWithinPrefix(String executable,String prefix){
        if(executable==null||prefix==null)return false;
        return executable.equals(prefix)||executable.startsWith(prefix+File.separator);
    }
    private static int parseFirstNumber(String line){
        String[] parts=line.substring(line.indexOf(':')+1).trim().split("\\s+");
        return parts.length==0?-1:Integer.parseInt(parts[0]);
    }
}
