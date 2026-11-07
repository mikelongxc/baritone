package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DepotApproachTest {
    @Test void blockedRegistrationSpotLeavesOtherSidesAndElevations() {
        var chest=new Policy.Pos(10,20,30);var oldStand=new Policy.Pos(9,20,30);
        var result=DepotApproach.alternatives(chest,null,Set.of(oldStand));
        assertFalse(result.contains(oldStand));assertFalse(result.contains(chest));
        assertTrue(result.contains(new Policy.Pos(11,20,30)));
        assertTrue(result.contains(new Policy.Pos(10,19,31)));
        assertTrue(result.contains(new Policy.Pos(10,21,30)));
        assertEquals(8,result.size());
    }
    @Test void doubleChestCanBeApproachedFromFarHalfWithoutTargetingEitherChestBlock() {
        var a=new Policy.Pos(10,20,30);var b=new Policy.Pos(11,20,30);
        var result=DepotApproach.alternatives(a,b,Set.of());
        assertTrue(result.contains(new Policy.Pos(12,20,30)));
        assertFalse(result.contains(a));assertFalse(result.contains(b));
        assertEquals(new HashSet<>(result).size(),result.size());
    }
    @Test void failedApproachesAreFiniteAndCannotBeSelectedAgain() {
        var chest=new Policy.Pos(10,20,30);Set<Policy.Pos> failed=new HashSet<>();
        for(int i=0;i<9;i++) {
            var candidates=DepotApproach.alternatives(chest,null,failed);
            assertEquals(9-i,candidates.size());assertTrue(Collections.disjoint(candidates,failed));
            failed.add(candidates.getFirst());
        }
        assertTrue(DepotApproach.alternatives(chest,null,failed).isEmpty());
    }
}
