package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JobRecoveryTest {
    @Test void selectionClearingFollowsBuilderNotMinerAndUnmanagedBuildsStayUnmanaged() {
        assertTrue(NativeJob.CLEAR_AREA.active(false,true));
        assertFalse(NativeJob.CLEAR_AREA.active(true,false));
        assertTrue(NativeJob.MINE.active(true,false));
        assertFalse(NativeJob.MINE.active(false,true));
        assertFalse(NativeJob.NONE.active(true,true));
    }
    private Policy.Stack sand(int count){return new Policy.Stack("sand",count,64,false);}
    @Test void selectionKeepsStartingSuppliesButDepositsNewWholeStacks() {
        var p=new ClearAreaInventory();p.begin(List.of(sand(32)),Set.of());
        assertFalse(p.mayDeposit(sand(64),List.of(sand(64)),Set.of()));
        assertTrue(p.mayDeposit(sand(64),List.of(sand(32),sand(64)),Set.of()));
        assertTrue(p.mayDeposit(new Policy.Stack("dirt",64,64,false),List.of(new Policy.Stack("dirt",64,64,false)),Set.of()));
    }
    @Test void protectedSlotsAndSpecialStacksCannotFundAWholeStackDeposit() {
        var p=new ClearAreaInventory();p.begin(List.of(sand(32)),Set.of());
        assertFalse(p.mayDeposit(sand(64),List.of(sand(32),sand(64)),Set.of(0)));
        var tool=new Policy.Stack("tool",1,1,true);
        assertFalse(p.mayDeposit(tool,List.of(tool),Set.of()));
    }
    @Test void newClearJobReplacesOldSupplyBaseline() {
        var p=new ClearAreaInventory();p.begin(List.of(sand(64)),Set.of());
        p.begin(List.of(),Set.of());assertTrue(p.mayDeposit(sand(64),List.of(sand(64)),Set.of()));
    }
    @Test void stalledMineRepathsAtFifteenSecondsAndRelogsAtThirty() {
        var w=new StallWatch();
        assertEquals(StallWatch.Action.NONE,w.sample(0,true,0,0,0,0));
        assertEquals(StallWatch.Action.NONE,w.sample(14_999,true,0,0,0,0));
        assertEquals(StallWatch.Action.REPATH,w.sample(15_000,true,0,0,0,0));
        assertEquals(StallWatch.Action.RELOG,w.sample(30_000,true,0,0,0,0));
        w.afterRelog();w.sample(100_000,true,0,0,0,0);
        assertEquals(StallWatch.Action.RELOG,w.sample(115_000,true,0,0,0,0));
    }
    @Test void blockedActivityFreezesRatherThanErasesIdleTime() {
        var w=new StallWatch();w.sample(0,true,0,0,0,0);
        w.sample(10_000,false,0,0,0,0);w.sample(1_000_000,false,0,0,0,0);
        assertEquals(10_000,w.idleMillis(1_000_000));
        assertEquals(StallWatch.Action.NONE,w.sample(1_000_000,true,0,0,0,0));
        assertEquals(StallWatch.Action.NONE,w.sample(1_004_999,true,0,0,0,0));
        assertEquals(StallWatch.Action.REPATH,w.sample(1_005_000,true,0,0,0,0));
    }
    @Test void movementOrPickupsResetStallButSmallOscillationDoesNot() {
        var w=new StallWatch();w.sample(0,true,0,0,0,0);
        assertEquals(StallWatch.Action.NONE,w.sample(10_000,true,2,0,0,0));
        assertEquals(StallWatch.Action.NONE,w.sample(20_000,true,2,0,0,1));
        assertEquals(StallWatch.Action.REPATH,w.sample(35_000,true,2.1,0,0,1));
    }
    @Test void progressAfterFirstRecoveryRestartsEscalationInsteadOfRelogging() {
        var w=new StallWatch();w.sample(0,true,0,0,0,0);w.sample(15_000,true,0,0,0,0);
        w.sample(16_000,true,0,0,0,1);
        assertEquals(StallWatch.Action.REPATH,w.sample(31_000,true,0,0,0,1));
    }
    @Test void localWalkingDoesNotMaskRepeatedUnconfirmedMining() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);
        assertFalse(d.sample(1_000,true,1,0,0,0));
        assertFalse(d.sample(10_999,true,20,0,0,0));
        assertTrue(d.sample(11_000,true,20,0,0,0));
    }
    @Test void realBlockChangesOrCollectedItemsClearDesyncSuspicion() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);d.sample(1,true,10,0,0,0);
        assertFalse(d.sample(50_000,true,10,1,0,0));
        assertFalse(d.sample(100_000,true,10,1,1,0));
    }
    @Test void ordinaryExplorationWithoutMiningOrCorrectionsDoesNotRelog() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);
        assertFalse(d.sample(1_000_000,true,0,0,0,0));
    }
    @Test void sustainedRubberbandCorrectionsQualifyAfterTenSeconds() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);d.sample(1,true,0,0,0,5);
        assertFalse(d.sample(10_000,true,0,0,0,5));
        assertTrue(d.sample(10_001,true,0,0,0,5));
    }
    @Test void eatingFreezesDesyncWindowWithoutStartingItOver() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);d.sample(1,true,8,0,0,0);
        d.sample(8_001,false,8,0,0,0);
        assertFalse(d.sample(60_001,false,8,0,0,0));
        assertEquals(8_000,d.suspectMillis(60_001));
        assertFalse(d.sample(60_001,true,8,0,0,0));
        assertFalse(d.sample(62_000,true,8,0,0,0));
        assertTrue(d.sample(62_001,true,8,0,0,0));
    }
    @Test void explicitPauseOrNewJobResetsBothWindowsAndEscalation() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);d.sample(1,true,8,0,0,0);
        d.reset();assertFalse(d.sample(1_000_000,true,8,0,0,0));
        assertEquals(0,d.suspectMillis(1_000_000));
        var w=new StallWatch();w.sample(0,true,0,0,0,0);w.sample(15_000,true,0,0,0,0);
        w.reset();assertEquals(StallWatch.Action.NONE,w.sample(1_000_000,true,0,0,0,0));
        assertEquals(StallWatch.Action.REPATH,w.sample(1_015_000,true,0,0,0,0));
    }
    @Test void oneIsolatedBreakAttemptDoesNotCauseFastDesyncRelog() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);d.sample(1,true,1,0,0,0);
        assertFalse(d.sample(20_000,true,1,0,0,0));
    }
    @Test void resumedProgressWhileBusyClearsPriorSuspicion() {
        var d=new DesyncWatch();d.sample(0,true,0,0,0,0);d.sample(1,true,8,0,0,0);
        d.sample(9_000,false,8,1,0,0);
        assertFalse(d.sample(60_000,true,8,1,0,0));
        assertEquals(0,d.suspectMillis(60_000));
    }
}
