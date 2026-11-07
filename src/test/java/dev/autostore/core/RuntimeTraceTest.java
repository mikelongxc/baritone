package dev.autostore.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeTraceTest {
    @TempDir Path directory;
    private RuntimeTrace.Sample sample(long time,boolean focused){return new RuntimeTrace.Sample(time,50,Loop.State.RETURNING,focused,RuntimeTrace.Blocker.NONE,true,false,false,false,2,3);}
    @Test void historyIsBoundedAndInstancesNeverOverwriteEachOther() throws Exception {
        var a=new RuntimeTrace(directory,1);var b=new RuntimeTrace(directory,2);
        for(int i=0;i<200;i++)a.record(sample(i,false));
        b.record(sample(999,true));
        var entries=JsonParser.parseString(Files.readString(directory.resolve("runtime-1.json"))).getAsJsonArray();
        assertEquals(160,entries.size());assertEquals(40,entries.get(0).getAsJsonObject().get("time").getAsInt());
        assertEquals(1,JsonParser.parseString(Files.readString(directory.resolve("runtime-2.json"))).getAsJsonArray().size());
        assertFalse(Files.exists(directory.resolve("runtime-1.json.tmp")));
    }
    @Test void samplesCaptureFocusAndStateTransitionsAndTickGaps() {
        var trace=new RuntimeTrace(directory,3);
        assertEquals(0,trace.tick(10));assertEquals(30_000,trace.tick(30_010));
        trace.record(sample(100,false));assertFalse(trace.due(200,Loop.State.RETURNING,false));
        assertTrue(trace.due(200,Loop.State.RETURNING,true));
        assertTrue(trace.due(200,Loop.State.OPENING,false));assertTrue(trace.due(5100,Loop.State.RETURNING,false));
    }
    @Test void unavailableDiagnosticDirectoryCannotStopGameplay() throws Exception {
        Path file=directory.resolve("file");Files.writeString(file,"occupied");
        assertDoesNotThrow(()->new RuntimeTrace(file,4).record(sample(0,false)));
    }
}
