package dev.autostore.core;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayDeque;

/** Bounded, per-process diagnostics. No positions, server names, accounts or inventory contents. */
public final class RuntimeTrace {
    public enum Blocker { NONE, NO_WORLD, MANUAL_PAUSE, TOOL, BUILDER_PAUSE, CONTAINER, EATING, ITEM_USE, COMBAT }
    public record Sample(long time, long tickGap, Loop.State state, boolean focused, Blocker blocker,
                         boolean pathing, boolean calculating, boolean pathFailed, boolean menuOwned,
                         long travelProgress, long snapshots) {}
    private final Path file;
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private long lastTick=-1,lastWrite=Long.MIN_VALUE/2;
    private Loop.State lastState;
    private boolean lastFocus;
    public RuntimeTrace(Path directory,long pid){file=directory.resolve("runtime-"+pid+".json");}
    public boolean due(long now,Loop.State state,boolean focused) {
        return state!=lastState || focused!=lastFocus || now-lastWrite>=5_000;
    }
    public long tick(long now){long gap=lastTick<0?0:Math.max(0,now-lastTick);lastTick=now;return gap;}
    public void record(Sample sample) {
        samples.addLast(sample);while(samples.size()>160)samples.removeFirst();
        lastState=sample.state();lastFocus=sample.focused();lastWrite=sample.time();
        try {
            Files.createDirectories(file.getParent());
            Path temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,new Gson().toJson(samples));
            try {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        } catch(IOException | SecurityException ignored) { /* Diagnostics must never stop the bot. */ }
    }
}
