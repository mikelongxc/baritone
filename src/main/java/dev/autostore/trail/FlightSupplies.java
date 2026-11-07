package dev.autostore.trail;

/** Conservative thresholds leave fuel for landing and prevent refuel/relaunch loops. */
public final class FlightSupplies {
    public static final int LAND_ROCKETS=32,LAUNCH_ROCKETS=64,TARGET_ROCKETS=192;
    private FlightSupplies(){}
    public static boolean land(int rockets,int remaining,int maximum) {
        return rockets<=LAND_ROCKETS || maximum<=0 || remaining<=Math.max(32,(int)Math.ceil(maximum*0.10));
    }
    public static boolean launch(int rockets,int remaining,int maximum) {
        return rockets>=LAUNCH_ROCKETS && maximum>0 && remaining>Math.max(32,(int)Math.ceil(maximum*0.10));
    }
    public static boolean toolUsable(int remaining,int maximum) {
        return maximum<=0 || remaining>Math.max(1,(int)Math.ceil(maximum*0.05));
    }
    public static boolean transferConfirmed(int beforeBox,int beforePlayer,int afterBox,int afterPlayer) {
        int moved=beforeBox-afterBox;
        return moved>0 && afterPlayer-beforePlayer==moved;
    }
}
