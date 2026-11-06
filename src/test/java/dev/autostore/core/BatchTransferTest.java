package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchTransferTest {
    private static BatchTransfer.Slot s(int key,int count){return new BatchTransfer.Slot(key,count);}
    private static BatchTransfer.Slot empty(){return s(-1,0);}
    @Test void completeInventoryOfSandIsVerifiedAsOneBatch() {
        List<BatchTransfer.Slot> before=new ArrayList<>(),after=new ArrayList<>();Set<Integer> clicked=new HashSet<>();
        for(int i=0;i<54;i++){before.add(empty());after.add(i<35?s(0,64):empty());}
        for(int i=0;i<35;i++){before.add(s(0,64));after.add(empty());clicked.add(54+i);}
        before.add(s(1,1));after.add(s(1,1)); // protected tool
        assertEquals(Policy.Transfer.MOVED,BatchTransfer.verify(before,after,54,clicked));
    }
    @Test void mixedOutputsPartialStacksAndPartialRejectionBalanceIndependently() {
        var before=List.of(s(0,48),empty(),s(0,64),s(1,64),s(0,32));
        var after=List.of(s(0,64),s(1,64),s(0,48),empty(),s(0,32));
        assertEquals(Policy.Transfer.MOVED,BatchTransfer.verify(before,after,2,Set.of(2,3,4)));
    }
    @Test void whollyRejectedBatchIsNotMistakenForSuccess() {
        var slots=List.of(empty(),s(0,64),s(0,64));
        assertEquals(Policy.Transfer.NO_CHANGE,BatchTransfer.verify(slots,slots,1,Set.of(1,2)));
    }
    @Test void lossOrCrossItemSubstitutionCannotCancelOutInTotalCount() {
        var before=List.of(empty(),empty(),s(0,64),s(1,64));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(0,64),s(1,32),empty(),empty()),2,Set.of(2,3)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(0,128),empty(),empty(),empty()),2,Set.of(2,3)));
    }
    @Test void untouchedPlayerSlotsIncludingOtherOutputsMustRemainExact() {
        var before=List.of(empty(),s(0,64),s(0,64),s(2,1));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(0,128),empty(),empty(),s(2,1)),1,Set.of(1)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(0,64),empty(),s(0,64),s(3,1)),1,Set.of(1)));
    }
    @Test void componentIdentityAndSourceGrowthAreRejected() {
        var before=List.of(empty(),s(0,64));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(1,64),empty()),1,Set.of(1)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(empty(),s(0,65)),1,Set.of(1)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(0,32),s(1,32)),1,Set.of(1)));
    }
    @Test void unrelatedChestItemsCannotDisappearDuringBatch() {
        var before=List.of(s(2,5),empty(),s(0,64));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(empty(),s(0,64),empty()),2,Set.of(2)));
    }
    @Test void malformedSnapshotsAndClickTargetsAreRejected() {
        var before=List.of(empty(),s(0,64));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(empty()),1,Set.of(1)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,before,1,Set.of(0)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,before,1,Set.of(2)));
        assertEquals(Policy.Transfer.INVALID,BatchTransfer.verify(before,List.of(s(0,-1),s(0,64)),1,Set.of(1)));
    }
}
