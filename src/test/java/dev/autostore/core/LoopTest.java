package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LoopTest {
    static class Fake implements Loop.Port {
        boolean rebaseReady;
        boolean active=true,full,stopped=true,arrived,ready,available=true,unsafe,persistenceFails,pathFailed,busy,inventoryOpen,cursorOccupied;
        final Object nativeJob=new Object();
        Object resumedJob;
        int cancels,stops,opens,clicks,travels,depots=2,verified,resumes;
        long routeProgress;
        Loop loop;
        List<Integer> preference=List.of(0,1,2);
        List<String> calls=new ArrayList<>();
        Loop.Click click=Loop.Click.SENT;
        Policy.Transfer transfer=Policy.Transfer.MOVED;
        public void validate(){if(unsafe)throw new Loop.SafetyException("unsafe");}
        public void safety(){validate();}
        public boolean busy(){return busy || loop!=null && (Cooperation.needsStorageMenu(loop.state) && cursorOccupied || Cooperation.screenBlocksJob(loop.state,false,inventoryOpen,inventoryOpen,false,false));}
        public boolean nearlyFull(){return full;}
        public boolean miningActive(){return active;}
        public void resumeMining(){resumes++;resumedJob=nativeJob;calls.add("release");}
        public void cancel(){cancels++;calls.add("cancelPath");}
        public void stop(){stops++;active=false;calls.add("stopJob");}
        public boolean stopped(){return stopped;}
        public int chooseDepot(Set<Integer> attempted){return preference.stream().filter(i -> i<depots && !attempted.contains(i)).findFirst().orElse(-1);}
        public void depotUnavailable(int d){calls.add("unavailable"+d);}
        public void storageFailed(int d,String why){calls.add("failure"+d+":"+why);}
        public String storageWaitReason(){return "Check #store list for the failed routes";}
        public void travel(int d){travels++;calls.add("travel"+d);}
        public boolean arrived(int d){return arrived;}
        public boolean travelFailed(){return pathFailed;}
        public long travelProgress(){return routeProgress;}
        public boolean canOpenStorage(){return !cursorOccupied;}
        public boolean open(int d){opens++;calls.add("open"+d);return available;}
        public boolean ready(int d){return ready;}
        public void close(){calls.add("close");}
        public Loop.Click click(int d){clicks++;calls.add("click"+d);return click;}
        public Policy.Transfer verify(){verified++;return transfer;}
        public boolean rebaseTransfer(){calls.add("rebase");return rebaseReady;}
        public void checkpoint(Loop.State s,String why){calls.add("save:"+s);if(persistenceFails)throw new IllegalStateException();}
    }
    static class Run {
        Fake p=new Fake();Loop l=new Loop(p);long t;
        Run(){p.loop=l;l.observeMining(t);}
        void tick(long delta){t+=delta;l.tick(t);}
        void trip(){p.full=true;tick(50);tick(300);tick(50);}
        void storage(){trip();p.arrived=true;tick(50);tick(50);p.ready=true;tick(50);assertEquals(Loop.State.DEPOSITING,l.state);}
        void confirm(){tick(50);assertEquals(Loop.State.REOPENING,l.state);tick(400);assertEquals(Loop.State.VERIFYING,l.state);tick(50);}
    }
    @Test void observationDoesNotCancelRestartOrWaitForNativeMovement() {
        Fake p=new Fake();p.stopped=false;Loop l=new Loop(p);l.observeMining(0);
        assertEquals(Loop.State.MINING,l.state);assertEquals(0,p.cancels);assertEquals(0,p.stops);assertEquals(0,p.resumes);
        l.tick(1_000_000);assertEquals(Loop.State.MINING,l.state);assertEquals(0,p.cancels);
    }
    @Test void inactiveNativeMinerIsNeverStartedByObservation() {
        Fake p=new Fake();p.active=false;Loop l=new Loop(p);l.observeMining(0);l.tick(1_000_000);
        assertEquals(Loop.State.PAUSED,l.state);assertEquals(0,p.resumes);assertEquals(0,p.travels);
    }
    @Test void fullInventoryPreservesNativeJobAndVerifiedDepositReleasesThatSameJob() {
        Run r=new Run();r.storage();assertTrue(r.p.active);assertEquals(0,r.p.stops);r.confirm();
        assertEquals(0,r.p.resumes);r.p.full=false;r.p.click=Loop.Click.EMPTY;r.tick(50);r.tick(50);
        assertEquals(Loop.State.MINING,r.l.state);assertEquals(1,r.p.resumes);assertSame(r.p.nativeJob,r.p.resumedJob);assertEquals(0,r.p.stops);
        assertTrue(r.p.calls.indexOf("cancelPath")<r.p.calls.indexOf("travel0"));
    }
    @Test void endedNativeMiningIsNotRestartedOrSentToStorageEvenIfInventoryIsFull() {
        Run r=new Run();r.p.active=false;r.p.full=true;r.tick(50);r.tick(1_000_000);
        assertEquals(Loop.State.PAUSED,r.l.state);assertEquals(0,r.p.travels);assertEquals(0,r.p.stops);
    }
    @Test void manualStopDuringRetryOrCombatStaysStopped() {
        for(Loop.State state:List.of(Loop.State.WAITING_STORAGE,Loop.State.MINING)) {
            Run r=new Run();r.l.state=state;r.p.busy=true;r.tick(50);r.l.pause("#stop");r.p.busy=false;r.tick(1_000_000);
            assertEquals(Loop.State.PAUSED,r.l.state);assertFalse(r.p.active);assertEquals(0,r.p.resumes);
        }
    }
    @Test void combatOrManualPauseYieldsWithoutTerminatingNativeMine() {
        Run r=new Run();r.p.busy=true;r.tick(50);r.tick(200_000);
        assertTrue(r.p.active);assertTrue(r.l.yielding());assertEquals(0,r.p.stops);assertEquals(0,r.p.cancels);
        r.p.busy=false;r.tick(50);assertEquals(Loop.State.MINING,r.l.state);assertEquals(0,r.p.resumes);
    }
    @Test void combatFreezesTravelDeadlineAndRetainsItsGoal() {
        Run r=new Run();r.trip();r.p.busy=true;r.tick(50);r.tick(180_000);r.p.busy=false;r.tick(50);
        assertEquals(Loop.State.RETURNING,r.l.state);assertEquals(1,r.p.travels);assertTrue(r.p.active);
    }
    @Test void fullNearestChestFallsBackOncePerPassAndRetriesLater() {
        Run r=new Run();r.p.depots=3;r.p.preference=List.of(2,0,1);r.storage();r.p.click=Loop.Click.FULL;
        for(int i=0;i<20;i++)r.tick(50);
        assertEquals(Loop.State.WAITING_STORAGE,r.l.state);
        assertEquals(List.of("travel2","travel0","travel1"),r.p.calls.stream().filter(v -> v.startsWith("travel")).toList());
        r.tick(30_000);r.tick(50);assertEquals(4,r.p.travels);assertEquals(0,r.p.stops);assertTrue(r.p.active);
    }
    @Test void changedDistanceDoesNotChangeContainerDuringTransferVerification() {
        Run r=new Run();r.p.depots=3;r.p.preference=List.of(2,0,1);r.storage();r.p.preference=List.of(0,1,2);r.confirm();
        assertEquals(List.of("open2","open2"),r.p.calls.stream().filter(v -> v.startsWith("open")).toList());
        assertEquals(List.of("click2"),r.p.calls.stream().filter(v -> v.startsWith("click")).toList());
    }
    @Test void partialAndRejectedTransfersNeverResumeUnverified() {
        Run r=new Run();r.storage();r.confirm();r.confirm();assertEquals(2,r.p.verified);assertEquals(0,r.p.resumes);
        Run rejected=new Run();rejected.storage();rejected.p.transfer=Policy.Transfer.NO_CHANGE;rejected.confirm();
        assertEquals(Loop.State.RETURNING,rejected.l.state);assertEquals(1,rejected.p.clicks);assertEquals(0,rejected.p.resumes);
        assertTrue(rejected.p.calls.contains("failure0:TRANSFER_REJECTED"));
    }
    @Test void changedTransferAndMissingConfirmationReconcileWithoutReplaying() {
        Run r=new Run();r.storage();r.p.transfer=Policy.Transfer.INVALID;r.confirm();assertEquals(Loop.State.REOPENING,r.l.state);assertTrue(r.p.active);assertEquals(1,r.p.clicks);
        Run delayed=new Run();delayed.storage();delayed.tick(50);delayed.p.ready=false;delayed.tick(400);delayed.tick(9000);
        assertEquals(Loop.State.REOPENING,delayed.l.state);assertEquals(1,delayed.p.clicks);assertEquals(0,delayed.p.resumes);
        delayed.p.ready=true;delayed.tick(400);delayed.tick(50);
        assertEquals(Loop.State.DEPOSITING,delayed.l.state);assertEquals(1,delayed.p.clicks);assertTrue(delayed.p.active);
    }
    @Test void combatDuringPendingClickStillVerifiesOnceThenYields() {
        Run r=new Run();r.storage();r.tick(50);r.p.busy=true;r.tick(400);r.tick(50);r.tick(50);r.tick(20_000);
        assertEquals(1,r.p.verified);assertEquals(1,r.p.clicks);assertEquals(Loop.State.OPENING,r.l.state);assertTrue(r.p.active);
    }
    @Test void nativeCancellationDuringUnconfirmedTransferRequiresInspection() {
        Run r=new Run();r.storage();r.tick(50);r.p.active=false;r.tick(400);
        assertEquals(Loop.State.FAULTED,r.l.state);assertEquals(0,r.p.resumes);assertEquals(1,r.p.clicks);
    }
    @Test void deathDisconnectAndPersistenceFailuresStillStopTheOriginalJob() {
        for(Loop.State state:Loop.State.values()) {
            if(!Cooperation.storageOwnsPath(state) && state!=Loop.State.MINING)continue;
            Run r=new Run();r.l.state=state;r.p.unsafe=true;r.p.busy=true;r.tick(50);
            assertEquals(Loop.State.FAULTED,r.l.state);assertFalse(r.p.active);
        }
        Run r=new Run();r.p.persistenceFails=true;r.p.full=true;r.tick(50);
        assertEquals(Loop.State.FAULTED,r.l.state);assertEquals(0,r.p.travels);assertFalse(r.p.active);
    }
    @Test void inventoryKeepsMiningAndTravelRunningAndOnlyHeldCursorWaitsAtChest() {
        Run r=new Run();r.p.inventoryOpen=true;r.tick(2000);assertFalse(r.l.yielding());assertEquals(0,r.p.cancels);
        r.trip();assertEquals(Loop.State.RETURNING,r.l.state);assertEquals(1,r.p.travels);
        r.p.arrived=true;r.p.cursorOccupied=true;r.tick(50);r.tick(50);r.tick(20_000);
        assertEquals(Loop.State.OPENING,r.l.state);assertEquals(0,r.p.opens);assertTrue(r.p.active);
        r.p.cursorOccupied=false;r.tick(50);r.p.ready=true;r.tick(50);assertEquals(Loop.State.DEPOSITING,r.l.state);assertTrue(r.p.inventoryOpen);
    }
    @Test void screenDuringConfirmationWaitsWithoutReplayingAndContinuesWhenCleared() {
        Run r=new Run();r.storage();r.tick(50);r.p.inventoryOpen=true;r.p.cursorOccupied=true;r.tick(500);assertEquals(1,r.p.opens);
        r.p.cursorOccupied=false;r.tick(1000);r.tick(50);assertEquals(1,r.p.verified);assertEquals(1,r.p.clicks);
        Run blocked=new Run();blocked.storage();blocked.tick(50);blocked.p.inventoryOpen=true;blocked.p.cursorOccupied=true;blocked.tick(9000);
        assertEquals(Loop.State.REOPENING,blocked.l.state);assertEquals(1,blocked.p.clicks);
        blocked.p.cursorOccupied=false;blocked.tick(1000);blocked.tick(50);
        assertEquals(1,blocked.p.verified);assertTrue(blocked.p.active);
    }
    @Test void progressingLongRouteIsNotCancelledByAnElapsedTimeLimit() {
        Run r=new Run();r.trip();for(int i=0;i<5;i++){r.p.routeProgress++;r.tick(100_000);}
        assertEquals(Loop.State.RETURNING,r.l.state);assertEquals(1,r.p.travels);assertTrue(r.p.active);
        r.tick(121_000);assertTrue(r.p.calls.contains("failure0:TRAVEL_STALLED"));r.tick(50);assertEquals(2,r.p.travels);
    }
    @Test void reachableChestWinsOverFailedExactStandGoalOtherwiseFailureIsReported() {
        Run r=new Run();r.trip();r.p.arrived=true;r.p.pathFailed=true;r.tick(50);assertEquals(Loop.State.OPENING,r.l.state);
        Run blocked=new Run();blocked.trip();blocked.p.pathFailed=true;blocked.tick(50);
        assertTrue(blocked.p.calls.contains("failure0:NO_ROUTE"));assertTrue(blocked.p.active);
    }
    @Test void missingBlockedAndExhaustedStorageWaitsWithoutTerminatingMiner() {
        Run r=new Run();r.p.available=false;r.p.full=true;r.p.arrived=true;for(int i=0;i<25;i++)r.tick(500);
        assertEquals(Loop.State.WAITING_STORAGE,r.l.state);assertEquals(2,r.p.opens);assertTrue(r.p.active);
        Run blocked=new Run();blocked.p.full=true;blocked.p.arrived=true;for(int i=0;i<40;i++)blocked.tick(1000);
        assertEquals(Loop.State.WAITING_STORAGE,blocked.l.state);assertTrue(blocked.p.calls.contains("failure0:OPEN_TIMEOUT"));
    }
    @Test void freeingSpaceWhileWaitingReleasesSameJobAndFreshLoopDoesNotAutostart() {
        Run r=new Run();r.l.state=Loop.State.WAITING_STORAGE;r.p.full=false;r.tick(50);r.tick(50);
        assertSame(r.p.nativeJob,r.p.resumedJob);assertEquals(Loop.State.MINING,r.l.state);
        Loop fresh=new Loop(r.p);fresh.tick(1_000_000);assertEquals(Loop.State.PAUSED,fresh.state);
    }
    @Test void confirmedWholeLoadUsesOneOpenEvenAcrossManyPendingTicks() {
        class Inline extends Fake {
            boolean pending;
            public boolean inlineConfirmation(){return true;}
            public boolean transferPending(){return pending;}
        }
        Inline p=new Inline();Loop l=new Loop(p);p.loop=l;l.observeMining(0);
        p.full=true;l.tick(50);l.tick(350);l.tick(400);p.arrived=true;l.tick(450);
        l.tick(500);p.ready=true;l.tick(550);
        p.pending=true;p.click=Loop.Click.PENDING;
        for(int i=0;i<35;i++)l.tick(600+i*100);
        assertEquals(Loop.State.DEPOSITING,l.state);assertEquals(1,p.opens);
        p.busy=true;l.yieldForActivity(4100);l.tick(4200);
        assertEquals(Loop.State.DEPOSITING,l.state);assertEquals(1,p.opens);
        p.busy=false;p.pending=false;p.click=Loop.Click.SENT;l.tick(4300);
        assertEquals(Loop.State.VERIFYING,l.state);l.tick(4400);
        p.full=false;p.click=Loop.Click.EMPTY;l.tick(4500);l.tick(4600);
        assertEquals(Loop.State.MINING,l.state);assertEquals(1,p.opens);
        assertSame(p.nativeJob,p.resumedJob);
    }
    @Test void nativeJobEndingDuringPendingTransferNeverResumesOrRetries() {
        Fake p=new Fake(){public boolean transferPending(){return true;}};
        Loop l=new Loop(p);p.loop=l;l.observeMining(0);p.active=false;l.tick(100);
        assertEquals(Loop.State.FAULTED,l.state);assertEquals(0,p.resumes);assertEquals(0,p.clicks);
    }

    @Test void unavailableStorageDuringConfirmationRetriesTheSameDepotWithoutFaultOrAnotherClick() {
        Run r=new Run();r.storage();r.tick(50);r.p.available=false;r.tick(400);
        assertEquals(Loop.State.REOPENING,r.l.state);assertEquals(1,r.p.clicks);assertTrue(r.p.active);
        r.tick(29_000);assertEquals(2,r.p.opens);
        r.p.available=true;r.tick(1_000);r.tick(50);
        assertEquals(Loop.State.DEPOSITING,r.l.state);assertEquals(1,r.p.clicks);assertEquals(1,r.p.verified);
        assertEquals(List.of("open0","open0","open0"),r.p.calls.stream().filter(v -> v.startsWith("open")).toList());
    }
    @Test void reconciledNoChangeCanRetryOnlyAfterAuthoritativeVerification() {
        Run r=new Run();r.storage();r.tick(50);r.p.ready=false;r.tick(400);r.tick(9000);
        assertEquals(Loop.State.REOPENING,r.l.state);r.p.ready=true;r.p.transfer=Policy.Transfer.NO_CHANGE;
        r.tick(400);r.tick(50);assertEquals(Loop.State.DEPOSITING,r.l.state);
        assertEquals(1,r.p.clicks);assertEquals(1,r.p.verified);r.tick(50);assertEquals(2,r.p.clicks);
    }

    @Test void changedInventoryWithFreshFullSnapshotContinuesInSameMenu() {
        Run r=new Run();r.storage();r.p.rebaseReady=true;r.p.click=Loop.Click.REBASE;r.tick(50);
        assertEquals(Loop.State.DEPOSITING,r.l.state);assertEquals(1,r.p.opens);assertTrue(r.p.active);
        assertEquals(1,r.p.clicks);assertTrue(r.p.calls.contains("rebase"));
        r.p.click=Loop.Click.EMPTY;r.p.full=false;r.tick(50);r.tick(50);
        assertEquals(Loop.State.MINING,r.l.state);assertEquals(0,r.p.stops);
    }
    @Test void missingFullSnapshotReopensBeforeAnyRetryClick() {
        Run r=new Run();r.storage();r.p.click=Loop.Click.REBASE;r.tick(50);
        assertEquals(Loop.State.REOPENING,r.l.state);assertEquals(1,r.p.clicks);
        r.p.ready=false;r.tick(400);r.tick(50);assertEquals(1,r.p.clicks);
        r.p.ready=true;r.p.transfer=Policy.Transfer.INVALID;r.p.rebaseReady=true;r.tick(50);
        assertEquals(Loop.State.DEPOSITING,r.l.state);assertEquals(1,r.p.clicks);assertEquals(0,r.p.stops);
    }
    @Test void contentsChangingWhileOpeningWaitsThenFallsBackWithoutFault() {
        Run r=new Run();r.trip();r.p.arrived=true;r.tick(50);r.tick(50);
        r.p.ready=false;r.tick(100);assertEquals(Loop.State.OPENING,r.l.state);
        r.tick(9_000);assertEquals(Loop.State.RETURNING,r.l.state);assertEquals(0,r.p.clicks);
        r.tick(50);r.tick(50);r.tick(50);r.p.ready=true;r.tick(50);
        assertEquals(Loop.State.DEPOSITING,r.l.state);assertEquals(0,r.p.stops);
        assertEquals(List.of("travel0","travel1"),r.p.calls.stream().filter(x->x.startsWith("travel")).toList());
    }

    @Test void startupScreenWaitKeepsSupervisionAndContinuesSameJobWhenScreenClears() {
        Fake p=new Fake();p.busy=true;Loop l=new Loop(p);p.loop=l;l.observeMining(0);
        assertEquals(Loop.State.MINING,l.state);assertTrue(l.active());
        l.tick(50);l.tick(120_000);assertTrue(l.yielding());assertTrue(p.active);assertEquals(0,p.stops);
        p.busy=false;l.tick(120_050);
        assertEquals(Loop.State.MINING,l.state);assertFalse(l.yielding());assertEquals(0,p.resumes);
        p.full=true;l.tick(120_100);l.tick(120_400);l.tick(120_450);
        assertEquals(Loop.State.RETURNING,l.state);assertEquals(1,p.travels);assertEquals(0,p.stops);
    }
    @Test void explicitStopWhileStartupScreenWaitsDoesNotRestartLater() {
        Fake p=new Fake();p.busy=true;Loop l=new Loop(p);p.loop=l;l.observeMining(0);l.tick(50);
        l.pause("#stop");p.busy=false;l.tick(30_000);
        assertEquals(Loop.State.PAUSED,l.state);assertFalse(p.active);assertEquals(0,p.resumes);
    }

    @Test void failedRegistrationRouteTriesAlternateApproachBeforeSkippingChest() {
        Fake p=new Fake(){
            boolean retried;
            public boolean retryTravel(int d){if(retried)return false;retried=true;pathFailed=false;calls.add("alternate"+d);return true;}
        };
        Loop l=new Loop(p);p.loop=l;l.observeMining(0);p.full=true;
        l.tick(50);l.tick(350);l.tick(400);p.pathFailed=true;l.tick(450);
        assertEquals(Loop.State.RETURNING,l.state);assertTrue(p.calls.contains("alternate0"));
        assertFalse(p.calls.contains("unavailable0"));
        p.arrived=true;l.tick(500);l.tick(550);p.ready=true;l.tick(600);
        assertEquals(Loop.State.DEPOSITING,l.state);assertEquals(1,p.opens);assertEquals(0,p.stops);
    }
    @Test void exhaustedAlternateRouteStillAdvancesToNextRegisteredChest() {
        Fake p=new Fake(){
            int retries;
            public boolean retryTravel(int d){return retries++==0;}
        };
        Loop l=new Loop(p);p.loop=l;l.observeMining(0);p.full=true;
        l.tick(50);l.tick(350);l.tick(400);p.pathFailed=true;l.tick(450);l.tick(500);l.tick(550);
        assertEquals(List.of("travel0","travel1"),p.calls.stream().filter(x->x.startsWith("travel")).toList());
        assertTrue(p.calls.contains("failure0:NO_ROUTE"));assertEquals(0,p.stops);
    }
    @Test void fillsPartialChestSkipsFullChestAndFinishesLoadInThirdWithoutReopening() {
        class Load extends Fake {
            int remaining=128;
            int[] contents={120,128,0}; // two-slot containers; first accepts only eight items
            List<BatchTransfer.Slot> before,after;
            Load(){depots=3;full=true;arrived=true;ready=true;}
            public boolean inlineConfirmation(){return true;}
            public boolean transferPending(){return before!=null;}
            public boolean nearlyFull(){return remaining>0;}
            public Loop.Click click(int d){
                calls.add("click"+d);
                if(before!=null)return Loop.Click.SENT;
                if(remaining==0)return Loop.Click.EMPTY;
                int moved=Math.min(remaining,128-contents[d]);
                if(moved==0)return Loop.Click.FULL;
                before=slots(contents[d],remaining);
                remaining-=moved;contents[d]+=moved;after=slots(contents[d],remaining);
                return Loop.Click.PENDING;
            }
            List<BatchTransfer.Slot> slots(int stored,int inventory){
                return List.of(slot(Math.min(64,stored)),slot(Math.max(0,stored-64)),
                    slot(Math.min(64,inventory)),slot(Math.max(0,inventory-64)),new BatchTransfer.Slot(2,1)); // protected tool remains
            }
            BatchTransfer.Slot slot(int count){return new BatchTransfer.Slot(count==0?-1:1,count);}
            public Policy.Transfer verify(){
                assertNotNull(before);
                Policy.Transfer result=BatchTransfer.verify(before,after,2,Set.of(2,3));
                assertEquals(Policy.Transfer.MOVED,result);before=null;verified++;return result;
            }
            public void travel(int d){assertNull(before,"Never leave a chest with a pending transaction");super.travel(d);}
        }
        Load p=new Load();Loop l=new Loop(p);p.loop=l;l.observeMining(0);
        for(long now=50;now<=5000 && p.resumes==0;now+=50)l.tick(now);
        assertEquals(Loop.State.MINING,l.state);assertEquals(0,p.remaining);
        assertArrayEquals(new int[]{128,128,120},p.contents);assertEquals(2,p.verified);
        assertEquals(List.of("travel0","travel1","travel2"),p.calls.stream().filter(x->x.startsWith("travel")).toList());
        assertEquals(List.of("open0","open1","open2"),p.calls.stream().filter(x->x.startsWith("open")).toList());
        assertSame(p.nativeJob,p.resumedJob);assertEquals(0,p.stops);
    }

    @Test void rejoinedStorageFailureRetriesEvenIfRollbackMadeInventoryLessThanFull() {
        Run r=new Run();r.p.full=false;r.l.retryStorage(100);
        assertEquals(Loop.State.QUIESCING,r.l.state);assertEquals(1,r.p.cancels);
        r.l.tick(350);r.l.tick(400);
        assertEquals(Loop.State.RETURNING,r.l.state);assertEquals(1,r.p.travels);
        assertEquals(0,r.p.resumes);assertEquals(0,r.p.stops);
    }
    @Test void storageRetryAfterReconnectCannotRestartStoppedOrPendingTransferJob() {
        for(Loop.State state:List.of(Loop.State.PAUSED,Loop.State.FAULTED,Loop.State.REOPENING,Loop.State.VERIFYING)) {
            Run r=new Run();r.l.state=state;r.l.retryStorage(100);
            assertEquals(state,r.l.state);assertEquals(0,r.p.cancels);assertEquals(0,r.p.travels);
        }
        Run ended=new Run();ended.p.active=false;ended.l.retryStorage(100);
        assertEquals(Loop.State.MINING,ended.l.state);assertEquals(0,ended.p.cancels);
    }

}
