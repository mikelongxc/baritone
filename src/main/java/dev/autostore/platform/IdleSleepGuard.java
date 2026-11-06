package dev.autostore.platform;

import java.io.IOException;

/** A process-scoped macOS idle-sleep assertion; no global power settings are changed. */
public final class IdleSleepGuard implements AutoCloseable {
    private final String os;
    private final long watchedPid;
    private Process process;
    public IdleSleepGuard() { this(System.getProperty("os.name",""),ProcessHandle.current().pid()); }
    IdleSleepGuard(String os,long watchedPid) {this.os=os;this.watchedPid=watchedPid;}
    public boolean supported() {return os.startsWith("Mac");}
    public boolean start() {
        if(isActive())return true;
        if(!supported())return false;
        try {
            process=new ProcessBuilder("/usr/bin/caffeinate","-i","-w",Long.toString(watchedPid))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return true;
        } catch(IOException | SecurityException failure) {process=null;return false;}
    }
    public boolean isActive() {return process!=null && process.isAlive();}
    long processId() {return process==null?-1:process.pid();}
    @Override public void close() {if(process!=null){process.destroy();process=null;}}
}
