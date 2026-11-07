package dev.autostore.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolReserveTest {
    private ToolReserve.Candidate tool(int slot,int left,int max,double speed) {return new ToolReserve.Candidate(slot,true,true,left,max,speed);}
    @Test void fivePercentIsRoundedUpAcrossVanillaToolMaterials() {
        int[][] cases={{59,3},{131,7},{250,13},{32,2},{1561,79},{2031,102}};
        for(int[] c:cases) {
            assertEquals(c[1],ToolReserve.cutoff(c[0]));
            assertFalse(ToolReserve.usable(c[1],c[0]));
            assertTrue(ToolReserve.usable(c[1]+1,c[0]));
        }
    }
    @Test void wornHotbarShovelSelectsSpareFromFullMainInventory() {
        var candidates=new ArrayList<ToolReserve.Candidate>();
        for(int i=0;i<36;i++)candidates.add(tool(i,79,1561,8));
        candidates.set(27,tool(27,1561,1561,8));
        assertEquals(27,ToolReserve.choose(candidates).orElseThrow());
    }
    @Test void healthyHotbarToolIsPreferredOverInventorySwap() {
        assertEquals(4,ToolReserve.choose(List.of(tool(4,200,250,6),tool(12,2000,2031,9))).orElseThrow());
    }
    @Test void fastestSuitableSpareWinsWhenHotbarIsExhausted() {
        assertEquals(30,ToolReserve.choose(List.of(tool(0,2,59,2),tool(9,200,250,6),tool(30,1500,1561,8))).orElseThrow());
    }
    @Test void wrongToolProtectedSlotAndRetiredToolsAreNeverFallbacks() {
        var candidates=List.of(new ToolReserve.Candidate(2,false,true,2000,2031,100),
            new ToolReserve.Candidate(10,true,false,2000,2031,9),tool(11,102,2031,9));
        assertTrue(ToolReserve.choose(candidates).isEmpty());
    }
    @Test void noSpareMeansWaitUntilReplacementOrRepairAppears() {
        assertTrue(ToolReserve.choose(List.of(tool(0,79,1561,8),tool(20,10,250,6))).isEmpty());
        assertEquals(20,ToolReserve.choose(List.of(tool(0,79,1561,8),tool(20,250,250,6))).orElseThrow());
    }
    @Test void swapSlotMappingPreservesInventoryAndNeverTargetsArmorOrOffhand() {
        for(int i=0;i<9;i++)assertEquals(36+i,ToolReserve.menuSlot(i));
        for(int i=9;i<36;i++)assertEquals(i,ToolReserve.menuSlot(i));
        assertThrows(IllegalArgumentException.class,()->ToolReserve.menuSlot(-1));
        assertThrows(IllegalArgumentException.class,()->ToolReserve.menuSlot(36));
        assertTrue(ToolReserve.choose(List.of(tool(40,100,250,6))).isEmpty());
    }
    @Test void unbreakableItemsAreUsableOnlyWhenSuitableAndSelectionTiesAreStable() {
        assertTrue(ToolReserve.usable(0,0));
        assertTrue(ToolReserve.choose(List.of(new ToolReserve.Candidate(1,false,true,0,0,0))).isEmpty());
        assertEquals(9,ToolReserve.choose(List.of(tool(15,250,250,6),tool(9,250,250,6))).orElseThrow());
    }
    @Test void underFivePercentIsAHardStopSeparateFromEarlySwapBoundary() {
        assertTrue(ToolReserve.belowFloor(98,2031));
        assertTrue(ToolReserve.belowFloor(101,2031));
        assertFalse(ToolReserve.belowFloor(102,2031));
        assertFalse(ToolReserve.usable(102,2031));
        assertTrue(ToolReserve.usable(103,2031));
        assertFalse(ToolReserve.belowFloor(5,100));
        assertTrue(ToolReserve.belowFloor(4,100));
        assertFalse(ToolReserve.belowFloor(0,0));
    }

    @Test void delayedServerDamageCannotSpendTheSameRemainingDurabilityTwice() {
        var wear=new ToolWearBudget();
        assertFalse(wear.needsConfirmation(0,103,2031,1));wear.reserve(0,1);
        assertTrue(wear.needsConfirmation(0,103,2031,1));
        // Unbreaking may have prevented damage; only a server barrier releases that reservation.
        wear.confirmed();assertFalse(wear.needsConfirmation(0,103,2031,1));
        assertTrue(wear.needsConfirmation(0,102,2031,1));
    }
    @Test void changingHotbarSlotsDoesNotForgetUnconfirmedWear() {
        var wear=new ToolWearBudget();wear.reserve(0,5);
        assertTrue(wear.needsConfirmation(0,107,2031,1));
        assertFalse(wear.needsConfirmation(1,107,2031,1));
        assertTrue(wear.needsConfirmation(0,107,2031,1));
    }
    @Test void multiDamageToolsReserveTheFullWorstCaseCost() {
        var wear=new ToolWearBudget();
        assertTrue(wear.needsConfirmation(0,103,2031,2));
        assertFalse(wear.needsConfirmation(0,104,2031,2));wear.reserve(0,2);
        assertTrue(wear.needsConfirmation(0,104,2031,2));
    }

}
