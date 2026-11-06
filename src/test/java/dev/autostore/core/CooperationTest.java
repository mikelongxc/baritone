package dev.autostore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CooperationTest {
    @Test void inventoryEscapeOrXaeroMapCanContinueThroughStorageWhileChatWaits() {
        for (Loop.State state:Loop.State.values()) {
            boolean needsMenu=state==Loop.State.OPENING || state==Loop.State.DEPOSITING || Cooperation.verifying(state);
            assertFalse(Cooperation.screenBlocksJob(state,false,true,true,false,false),state.name());
            assertEquals(needsMenu,Cooperation.screenBlocksJob(state,false,true,false,true,false),state.name());
        }
    }
    @Test void otherContainersStillBlockAndOwnedStorageIsNotMistakenForManualInventory() {
        assertTrue(Cooperation.screenBlocksJob(Loop.State.MINING,false,true,false,false,true));
        assertTrue(Cooperation.screenBlocksJob(Loop.State.RETURNING,false,true,false,false,false));
        assertFalse(Cooperation.screenBlocksJob(Loop.State.DEPOSITING,true,true,false,false,true));
        assertFalse(Cooperation.screenBlocksJob(Loop.State.OPENING,false,false,false,false,false));
    }
    @Test void enabledButIdleModulesDoNotSuspendTheJob() {
        assertFalse(Cooperation.yieldJob(true,false,false,false));
    }
    @Test void eatingAndCombatYieldWithoutRequiringModuleDisable() {
        assertTrue(Cooperation.yieldJob(true,false,true,false));
        assertTrue(Cooperation.yieldJob(true,false,false,true));
        assertTrue(Cooperation.yieldJob(true,true,false,false));
        assertFalse(Cooperation.yieldJob(false,true,true,true));
    }
    @Test void newEatingWaitsOnlyForAnOutstandingDeposit() {
        assertTrue(Cooperation.deferNewEating(true,true,false));
        assertFalse(Cooperation.deferNewEating(true,false,false));
        assertFalse(Cooperation.deferNewEating(true,true,true));
        assertFalse(Cooperation.deferNewEating(false,true,false));
    }
    @Test void containerAutomationCannotStealTheOwnedTransaction() {
        assertFalse(Cooperation.allowContainerClick(true,true,false,false));
        assertFalse(Cooperation.allowContainerClick(true,false,true,false));
        assertTrue(Cooperation.allowContainerClick(true,true,true,true));
        assertTrue(Cooperation.allowContainerClick(true,false,false,false));
        assertTrue(Cooperation.allowContainerClick(false,true,true,false));
    }
    @Test void onlyDamageOnAnObservedCombatSlotIsAllowed() {
        assertTrue(Cooperation.allowCombatWear(true,true,10,11,100));
        assertFalse(Cooperation.allowCombatWear(false,true,10,11,100));
        assertFalse(Cooperation.allowCombatWear(true,false,10,11,100));
        assertFalse(Cooperation.allowCombatWear(true,true,10,9,100));
        assertFalse(Cooperation.allowCombatWear(true,true,10,100,100));
    }
}
