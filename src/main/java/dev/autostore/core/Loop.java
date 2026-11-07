package dev.autostore.core;

/** Watches a native mining job. Only the storage trip owns navigation; it never starts mining. */
public final class Loop {
    public enum State { PAUSED, MINING, QUIESCING, RETURNING, OPENING, DEPOSITING, REOPENING, VERIFYING, RESUMING, WAITING_STORAGE, FAULTED }
    public enum Click { SENT, EMPTY, FULL, PENDING, RECONCILE, REBASE }
    public interface Port {
        void validate();
        void safety();
        default boolean busy() { return false; }
        boolean nearlyFull();
        boolean miningActive();
        /** Release temporary storage control/settings; do not issue a mining command. */
        void resumeMining();
        /** Cancel only the current path segment, preserving the native mine process. */
        void cancel();
        /** Explicit stop/fault may terminate the underlying job. */
        void stop();
        boolean stopped();
        int chooseDepot(java.util.Set<Integer> attempted);
        default void depotUnavailable(int depot) {}
        default void storageFailed(int depot,String reason) {}
        default String storageWaitReason() { return "Storage unavailable; retrying in 30 seconds"; }
        void travel(int depot);
        /** Try another approach to this container before declaring its route unavailable. */
        default boolean retryTravel(int depot) { return false; }
        default void travelForConfirmation(int depot){travel(depot);}
        boolean arrived(int depot);
        default boolean travelFailed() { return false; }
        default long travelProgress() { return 0; }
        default boolean canOpenStorage() { return true; }
        boolean open(int depot);
        boolean ready(int depot);
        void close();
        default boolean transferPending(){return false;}
        default boolean inlineConfirmation(){return false;}
        Click click(int depot);
        Policy.Transfer verify();
        default boolean rebaseTransfer(){return false;}
        void checkpoint(State state, String reason);
    }
    private final Port port;
    public volatile State state = State.PAUSED;
    public String reason = "Waiting for your #mine command";
    private long entered, lastTravelProgressAt, travelProgress;
    private long yieldedAt = -1;
    private int depot = -1;
    private final java.util.Set<Integer> attempted = new java.util.HashSet<>();
    private boolean opened, reconciling, confirmationRouting;
    private long retryConfirmationAt;
    public long travelTimeout = 120_000, openTimeout = 8_000;
    public Loop(Port port) { this.port = port; }
    public boolean active() { return state != State.PAUSED && state != State.FAULTED; }
    public void observeMining(long now) {
        if(active()) throw new IllegalStateException("Already observing a mining job");
        port.validate();
        if(!port.miningActive()) return;
        yieldedAt=-1;reconciling=false; beginStoragePass();
        transition(State.MINING,"Watching native Baritone job",now);
    }
    /** Rejoined with authoritative inventory: storage takes control before any resumed mining tick. */
    public void retryStorage(long now) {
        if(state!=State.MINING || !port.miningActive())return;
        port.cancel();beginStoragePass();
        transition(State.QUIESCING,"Reconnected; retrying registered storage before mining",now);
    }
    public void pause(String why) { halt(State.PAUSED, why); }
    public void fault(String why) { halt(State.FAULTED, why); }
    private void halt(State state,String why) {
        yieldedAt=-1; this.state=state; reason=why;
        port.stop(); port.close(); port.checkpoint(state,why);
    }
    private void transition(State next,String why,long now) {
        port.checkpoint(next,why);
        state=next; reason=why; entered=now; opened=false;
    }
    private void beginStoragePass() { depot=-1; attempted.clear(); }
    private void nextDepot(long now) {
        port.close(); port.cancel();
        if(depot>=0) port.depotUnavailable(depot);
        depot=port.chooseDepot(java.util.Set.copyOf(attempted));
        if(depot<0) { transition(State.WAITING_STORAGE,port.storageWaitReason(),now);return; }
        if(!attempted.add(depot)) throw new SafetyException("Storage selector repeated a container");
        transition(State.RETURNING,"Travelling to registered storage",now);
    }
    private void retryRouteOrNext(long now,String failure) {
        if(port.retryTravel(depot)) {
            travelProgress=port.travelProgress();lastTravelProgressAt=now;
            reason="Trying another approach to registered storage";
        } else {port.storageFailed(depot,failure);nextDepot(now);}
    }
    private void reconcile(long now,String why) {
        port.close();port.cancel();reconciling=true;confirmationRouting=false;retryConfirmationAt=now+350;
        transition(State.REOPENING,why,now);
    }
    private void refreshTransfer(long now) {
        if(port.rebaseTransfer()) {
            reconciling=false;transition(State.DEPOSITING,"Inventory refreshed from server; continuing deposit",now);
        } else reconcile(now,"Refreshing changed storage before retrying deposit");
    }
    public void yieldForActivity(long now) {
        if(!active() || Cooperation.verifying(state) || port.transferPending() || yieldedAt>=0) return;
        port.close();
        if(state==State.OPENING || state==State.DEPOSITING)
            transition(State.OPENING,"Waiting before opening storage",now);
        yieldedAt=now;
    }
    public boolean yielding() { return yieldedAt>=0; }
    private void finishYield(long now) {
        if(yieldedAt<0) return;
        long elapsed=Math.max(0,now-yieldedAt);
        entered+=elapsed; lastTravelProgressAt+=elapsed; yieldedAt=-1;
    }
    public void tick(long now) {
        if(!active()) return;
        try {
            port.safety();
            if(!port.miningActive()) {
                if(Cooperation.verifying(state) || port.transferPending()) { fault("Baritone stopped during an unconfirmed deposit; inspect inventory");return; }
                if(state!=State.MINING) port.cancel();
                port.close();port.resumeMining();yieldedAt=-1;
                transition(State.PAUSED,"Baritone job ended; waiting for your next command",now);
                return;
            }
            if(port.busy() && !Cooperation.verifying(state) && !port.transferPending()) { yieldForActivity(now);return; }
            finishYield(now);
            switch(state) {
                case MINING -> {
                    if(port.nearlyFull()) {
                        port.cancel();beginStoragePass();
                        transition(State.QUIESCING,"Pausing native mining for storage",now);
                    }
                }
                case QUIESCING -> {
                    if(port.stopped() && now-entered>=250) nextDepot(now);
                    else if(now-entered>5_000) fault("Could not stop movement safely");
                }
                case RETURNING -> {
                    if(!opened) {
                        if(port.stopped()) { port.travel(depot);opened=true;travelProgress=port.travelProgress();lastTravelProgressAt=now; }
                        else if(now-entered>5_000) fault("Previous movement did not stop before fallback");
                    } else if(port.arrived(depot)) { port.cancel();transition(State.OPENING,"Opening registered storage",now); }
                    else if(port.travelFailed()) { retryRouteOrNext(now,"NO_ROUTE"); }
                    else {
                        long current=port.travelProgress();
                        if(current!=travelProgress) {travelProgress=current;lastTravelProgressAt=now;}
                        if(now-lastTravelProgressAt>travelTimeout) retryRouteOrNext(now,"TRAVEL_STALLED");
                    }
                }
                case OPENING -> {
                    if(!port.stopped()) {if(now-entered>5_000) fault("Movement did not stop at storage");}
                    else if(!opened) {if(!port.open(depot)) nextDepot(now);else opened=true;}
                    else if(port.ready(depot)) transition(State.DEPOSITING,"Selecting mined items",now);
                    else if(now-entered>openTimeout) {port.storageFailed(depot,"OPEN_TIMEOUT");nextDepot(now);}
                }
                case DEPOSITING -> {
                    switch(port.click(depot)) {
                        case REBASE -> refreshTransfer(now);
                        case PENDING -> {} // wait for slot replies without closing the owned menu
                        case RECONCILE -> {
                            if(port.transferPending())reconcile(now,"Rechecking pending deposit at the same storage");
                            else {port.close();transition(State.OPENING,"Reopening interrupted storage",now);}
                        }
                        case SENT -> {
                            if(port.inlineConfirmation())transition(State.VERIFYING,"Verifying server-confirmed load",now);
                            else {port.close();transition(State.REOPENING,"Awaiting fresh server inventory",now);}
                        }
                        case FULL -> nextDepot(now);
                        case EMPTY -> {
                            port.close();
                            if(port.nearlyFull()) nextDepot(now);else transition(State.RESUMING,"Deposit complete",now);
                        }
                    }
                }
                case REOPENING -> {
                    if(now<retryConfirmationAt || now-entered<350)break;
                    if(!port.canOpenStorage()){reason="Waiting for the other screen before deposit recheck";retryConfirmationAt=now+1_000;break;}
                    if(reconciling && !port.arrived(depot)) {
                        if(!confirmationRouting){port.travelForConfirmation(depot);confirmationRouting=true;lastTravelProgressAt=now;travelProgress=port.travelProgress();}
                        long current=port.travelProgress();
                        if(current!=travelProgress){travelProgress=current;lastTravelProgressAt=now;}
                        if(port.travelFailed() || now-lastTravelProgressAt>travelTimeout){
                            port.cancel();confirmationRouting=false;retryConfirmationAt=now+30_000;
                            reason="Pending deposit: retrying route to the same storage in 30 seconds";
                        }
                        break;
                    }
                    if(confirmationRouting){port.cancel();confirmationRouting=false;}
                    if(!port.stopped())break;
                    if(!port.open(depot)) {
                        retryConfirmationAt=now+30_000;
                        reason="Storage temporarily unavailable; pending deposit retained, retrying in 30 seconds";
                        port.storageFailed(depot,"CONFIRMATION_RETRY");break;
                    }
                    transition(State.VERIFYING,"Verifying server-confirmed transfer",now);
                }
                case VERIFYING -> {
                    if(port.ready(depot)) {
                        switch(port.verify()) {
                            case MOVED -> {reconciling=false;transition(State.DEPOSITING,"Transfer verified",now);}
                            case NO_CHANGE -> {
                                if(reconciling){reconciling=false;transition(State.DEPOSITING,"Confirmed no transfer; retrying safely",now);}
                                else {port.storageFailed(depot,"TRANSFER_REJECTED");nextDepot(now);}
                            }
                            case INVALID -> refreshTransfer(now);
                        }
                    } else if(now-entered>openTimeout)reconcile(now,"No confirmation yet; rechecking the same storage");
                }
                case WAITING_STORAGE -> {
                    if(!port.nearlyFull()) transition(State.RESUMING,"Inventory has room again",now);
                    else if(now-entered>=30_000) {beginStoragePass();nextDepot(now);}
                }
                case RESUMING -> {
                    if(!port.stopped()) {if(now-entered>5_000) fault("Storage movement did not stop");break;}
                    port.validate();port.resumeMining();
                    transition(State.MINING,"Control returned to the same Baritone job",now);
                }
                default -> {}
            }
        } catch(RuntimeException failure) {
            try {fault("Safety check failed; "+(failure instanceof SafetyException?failure.getMessage():"inspect local configuration and dependencies"));}
            catch(RuntimeException ignored) {state=State.FAULTED;reason="Persistence or controller failure; stopped";}
        }
    }
    public static final class SafetyException extends RuntimeException {
        public SafetyException(String reason) {super(reason);}
    }
}
