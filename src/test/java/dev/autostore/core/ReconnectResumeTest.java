package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectResumeTest {
    final Object old=new Object(),next=new Object();
    final ReconnectResume resume=new ReconnectResume();
    final ReconnectResume.Job job=new ReconnectResume.Job("same-session-scope",List.of("minecraft:sand","minecraft:red_sand"),0,true);
    void start(){resume.capture(job,old);}
    @Test void meteorReconnectRestoresOriginalMultiTargetJobAndStoragePriorityExactlyOnce() {
        start();resume.snapshot(next);
        assertNull(resume.poll(10,next,job.scope(),true));
        assertNull(resume.poll(1009,next,job.scope(),true));
        assertEquals(job,resume.poll(1010,next,job.scope(),true));
        assertFalse(resume.pending());assertNull(resume.poll(9999,next,job.scope(),true));
    }
    @Test void oldInventoryAndOldConnectionCannotAuthorizeResume() {
        start();resume.snapshot(old);
        assertNull(resume.poll(0,old,job.scope(),true));
        assertNull(resume.poll(1000,next,job.scope(),true));
        assertNull(resume.poll(9000,next,job.scope(),true));assertTrue(resume.pending());
    }
    @Test void loginRetriesNeedTheirOwnInventorySnapshot() {
        start();resume.snapshot(next);resume.poll(0,next,job.scope(),true);
        Object retry=new Object();assertNull(resume.poll(5000,retry,job.scope(),true));
        resume.snapshot(retry);assertNull(resume.poll(6000,retry,job.scope(),true));
        assertEquals(job,resume.poll(7000,retry,job.scope(),true));
    }
    @Test void unavailableChunkOrCursorDelaysResumeWithoutLosingJob() {
        start();resume.snapshot(next);resume.poll(0,next,job.scope(),true);
        assertNull(resume.poll(500,next,job.scope(),false));
        assertNull(resume.poll(2000,next,job.scope(),true));
        assertEquals(job,resume.poll(3000,next,job.scope(),true));
    }
    @Test void anotherServerAccountOrDimensionCancelsInsteadOfStartingMiningThere() {
        start();resume.snapshot(next);
        assertNull(resume.poll(0,next,"different-scope",true));assertFalse(resume.pending());
        assertNull(resume.poll(5000,next,job.scope(),true));
    }
    @Test void explicitCancellationCannotBeUndoneByLateSnapshot() {
        start();resume.cancel();resume.snapshot(next);
        assertNull(resume.poll(0,next,job.scope(),true));assertFalse(resume.pending());
    }
    @Test void finiteQuantityAndTargetSnapshotArePreserved() {
        var targets=new ArrayList<>(List.of("minecraft:stone"));
        var finite=new ReconnectResume.Job(job.scope(),targets,640,false);targets.clear();
        resume.capture(finite,old);resume.snapshot(next);resume.poll(0,next,job.scope(),true);
        var restored=resume.poll(1000,next,job.scope(),true);
        assertEquals(640,restored.quantity());assertEquals(List.of("minecraft:stone"),restored.targets());assertFalse(restored.storageFirst());
    }
    @Test void disconnectedNetworkWaitHasNoArbitraryTimeout() {
        start();assertNull(resume.poll(9_000_000,null,null,false));assertTrue(resume.pending());
        resume.snapshot(next);resume.poll(9_000_001,next,job.scope(),true);
        assertEquals(job,resume.poll(9_001_001,next,job.scope(),true));
    }
    @Test void eligibilityExcludesManualDisconnectPauseToolStopAndExistingRecovery() {
        assertTrue(ReconnectResume.eligible(true,true,false,true,false,false,false));
        assertFalse(ReconnectResume.eligible(false,true,false,true,false,false,false));
        assertFalse(ReconnectResume.eligible(true,false,false,true,false,false,false));
        assertFalse(ReconnectResume.eligible(true,true,true,true,false,false,false));
        assertFalse(ReconnectResume.eligible(true,true,false,false,false,false,false));
        assertFalse(ReconnectResume.eligible(true,true,false,true,true,false,false));
        assertFalse(ReconnectResume.eligible(true,true,false,true,false,true,false));
        assertFalse(ReconnectResume.eligible(true,true,false,true,false,false,true));
    }
}
