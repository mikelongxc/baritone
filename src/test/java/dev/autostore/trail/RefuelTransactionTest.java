package dev.autostore.trail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RefuelTransactionTest {
    @Test void staleOrLocalPredictionCannotConfirm(){var t=new RefuelTransaction();t.sent(7,128,32);assertEquals(RefuelTransaction.Result.WAIT,t.snapshot(7,64,96));assertTrue(t.pending());}
    @Test void pendingClickCannotBeReplayed(){var t=new RefuelTransaction();t.sent(7,128,32);assertThrows(IllegalStateException.class,()->t.sent(8,128,32));}
    @Test void laterSnapshotConfirmsWholeStack(){var t=new RefuelTransaction();t.sent(7,128,32);assertEquals(RefuelTransaction.Result.COMMITTED,t.snapshot(8,64,96));assertFalse(t.pending());}
    @Test void partialTransferUsesActualCounts(){var t=new RefuelTransaction();t.sent(7,128,32);assertEquals(RefuelTransaction.Result.COMMITTED,t.snapshot(8,108,52));}
    @Test void rejectedClickResolvesWithoutClaimingProgress(){var t=new RefuelTransaction();t.sent(7,128,32);assertEquals(RefuelTransaction.Result.REJECTED,t.snapshot(8,128,32));}
    @Test void changesRequireNewBaseline(){var t=new RefuelTransaction();t.sent(7,128,32);assertEquals(RefuelTransaction.Result.REBASED,t.snapshot(8,0,33));t.sent(8,0,33);assertTrue(t.pending());}
    @Test void duplicateSnapshotIsNotSecondCommit(){var t=new RefuelTransaction();t.sent(7,128,32);t.snapshot(8,64,96);assertEquals(RefuelTransaction.Result.WAIT,t.snapshot(8,64,96));}
    @Test void invalidSnapshotDoesNotClearPending(){var t=new RefuelTransaction();t.sent(7,128,32);assertThrows(IllegalArgumentException.class,()->t.snapshot(8,-1,32));assertTrue(t.pending());}
}
