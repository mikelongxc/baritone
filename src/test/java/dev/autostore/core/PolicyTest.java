package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PolicyTest {
    @Test void cutoffUsesCeilingAndStopsAtEquality() {
        assertEquals(31,Policy.durabilityCutoff(3001,30,1));
        assertEquals(30,Policy.durabilityCutoff(250,30,1));
        assertFalse(Policy.healthy(31,3001,30,1));
        assertTrue(Policy.healthy(32,3001,30,1));
    }
    @Test void partialStacksAndReservedSlotsCountCorrectly() {
        var slots=List.of(new Policy.Stack("sand",48,64,false),new Policy.Stack("air",0,64,false),new Policy.Stack("sand",1,64,true),new Policy.Stack("air",0,64,false));
        assertEquals(80,Policy.capacity(slots,"sand",64,Set.of(3)));
        assertEquals(64,Policy.capacity(slots,"gravel",64,Set.of(3)));
    }
    @Test void pendingDropsTriggerEarlyReturnAtExactThreshold() {
        assertTrue(Policy.nearlyFull(200,64,3,8));
        assertFalse(Policy.nearlyFull(201,64,3,8));
        assertTrue(Policy.nearlyFull(0,64,3,0));
    }
    @Test void protectionAlwaysWinsOverAllowlist() {
        var plain=new Policy.Stack("sand",64,64,false);
        assertTrue(Policy.depositable(plain,10,Set.of("sand"),Set.of(),Set.of(0)));
        assertFalse(Policy.depositable(plain,0,Set.of("sand"),Set.of(),Set.of(0)));
        assertFalse(Policy.depositable(plain,10,Set.of("sand"),Set.of("sand"),Set.of()));
        assertFalse(Policy.depositable(new Policy.Stack("sand",64,64,true),10,Set.of("sand"),Set.of(),Set.of()));
        assertFalse(Policy.depositable(plain,10,Set.of("gravel"),Set.of(),Set.of()));
    }
    @Test void confirmationRequiresEqualPositiveAuthoritativeDeltas() {
        assertEquals(Policy.Transfer.MOVED,Policy.verify(64,32,32,64,true));
        assertEquals(Policy.Transfer.NO_CHANGE,Policy.verify(64,64,32,32,true));
        assertEquals(Policy.Transfer.INVALID,Policy.verify(64,32,32,32,true));
        assertEquals(Policy.Transfer.INVALID,Policy.verify(64,32,32,64,false));
        assertEquals(Policy.Transfer.INVALID,Policy.verify(64,65,32,31,true));
    }
    @Test void noQuarryBoundaryButDepotSupportsAreProtected() {
        Profile p=new Profile();p.quarry=Policy.Box.between(new Policy.Pos(-10,-10,-10),new Policy.Pos(10,10,10));
        Profile.Depot d=new Profile.Depot();d.position=new Policy.Pos(0,0,0);p.depots.add(d);
        assertFalse(p.mayTarget(new Policy.Pos(0,-1,0)));
        assertFalse(p.mayTarget(new Policy.Pos(0,0,0)));
        assertTrue(p.mayTarget(new Policy.Pos(2,2,2)));
        assertTrue(p.mayTarget(new Policy.Pos(1,0,0))); // access wall beside storage can be removed
        d.otherHalf=new Policy.Pos(1,0,0);
        assertFalse(p.mayTarget(new Policy.Pos(1,0,0)));
        assertFalse(p.mayTarget(new Policy.Pos(1,-1,0)));
        assertTrue(p.mayTarget(new Policy.Pos(1,1,0))); // an obstructed lid can be cleared
        assertTrue(p.mayTarget(new Policy.Pos(3,0,0)));
        assertTrue(p.mayTarget(new Policy.Pos(10,0,0)));
        assertTrue(p.mayTarget(new Policy.Pos(11,0,0)));
    }
}
