package dev.autostore.core;

/** Narrow ownership decisions; module enablement is deliberately not an input. */
public final class Cooperation {
    private Cooperation() {}
    public static boolean storageOwnsPath(Loop.State state) {
        return state!=Loop.State.MINING && state!=Loop.State.PAUSED && state!=Loop.State.FAULTED;
    }
    public static boolean needsStorageMenu(Loop.State state) {
        return state==Loop.State.OPENING || state==Loop.State.DEPOSITING || verifying(state);
    }
    public static boolean verifying(Loop.State state) {
        return state == Loop.State.REOPENING || state == Loop.State.VERIFYING;
    }
    /** Inventory, Esc and the Xaero map coexist with movement and can be temporarily replaced for storage. */
    public static boolean screenBlocksJob(Loop.State state, boolean ownedMenu, boolean screenOpen,
                                          boolean passiveScreen, boolean chat, boolean externalContainer) {
        if (ownedMenu) return false;
        if (state == Loop.State.OPENING || state == Loop.State.DEPOSITING || verifying(state))
            return externalContainer || screenOpen && !passiveScreen;
        return externalContainer || screenOpen && !passiveScreen && !chat;
    }
    public static boolean yieldJob(boolean active, boolean usingItem, boolean eating, boolean combat) {
        return active && (usingItem || eating || combat);
    }
    public static boolean deferNewEating(boolean active, boolean transferPending, boolean alreadyEating) {
        return active && transferPending && !alreadyEating;
    }
    public static boolean allowContainerClick(boolean active, boolean ownsScreen, boolean transferPending, boolean ownClick) {
        return !active || !(ownsScreen || transferPending) || ownClick;
    }
    public static boolean allowCombatWear(boolean authorizedSlot, boolean sameExceptDamage,
                                          int beforeDamage, int afterDamage, int maxDamage) {
        return authorizedSlot && sameExceptDamage && afterDamage >= beforeDamage && afterDamage < maxDamage;
    }
}
