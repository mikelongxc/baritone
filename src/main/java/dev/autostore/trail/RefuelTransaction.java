package dev.autostore.trail;

/** One sent transfer at a time. Only a later complete server snapshot resolves it. */
public final class RefuelTransaction {
    public enum Result { WAIT, COMMITTED, REJECTED, REBASED }
    private boolean pending;
    private int revision,box,player;
    public boolean pending(){return pending;}
    public void clear(){pending=false;}
    public void sent(int revision,int box,int player) {
        if(pending)throw new IllegalStateException("Unconfirmed refuel click");
        if(box<0 || player<0)throw new IllegalArgumentException("Invalid inventory count");
        this.revision=revision;this.box=box;this.player=player;pending=true;
    }
    public Result snapshot(int revision,int box,int player) {
        if(!pending || revision<=this.revision)return Result.WAIT;
        if(box<0 || player<0)throw new IllegalArgumentException("Invalid authoritative inventory");
        pending=false;
        if(FlightSupplies.transferConfirmed(this.box,this.player,box,player))return Result.COMMITTED;
        return box==this.box && player==this.player?Result.REJECTED:Result.REBASED;
    }
}
