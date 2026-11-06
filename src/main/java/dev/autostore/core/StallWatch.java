package dev.autostore.core;

/** No-progress escalation: refresh path/search, relog requests are rate-limited by the reconnect coordinator. */
public final class StallWatch {
    public enum Action { NONE, REPATH, RELOG }
    public static final long WINDOW_MS=15_000;
    private boolean initialized;
    private long since,pausedAt=-1;
    private double x,y,z;
    private int count,stage;
    public void reset(){initialized=false;stage=0;pausedAt=-1;}
    public void afterRelog(){reset();stage=1;}
    public long idleMillis(long now){return initialized?Math.max(0,(pausedAt<0?now:pausedAt)-since):0;}
    public Action sample(long now,boolean eligible,double px,double py,double pz,int items) {
        // Brief eating/combat/tool waits freeze time; they must not erase a nearly complete
        // recovery window on every occurrence. Explicit pause/stop resets the whole watch.
        if(!eligible){if(pausedAt<0)pausedAt=now;return Action.NONE;}
        if(!initialized){initialized=true;since=now;pausedAt=-1;x=px;y=py;z=pz;count=items;return Action.NONE;}
        if(pausedAt>=0){since+=Math.max(0,now-pausedAt);pausedAt=-1;}
        double dx=px-x,dy=py-y,dz=pz-z;
        if(dx*dx+dy*dy+dz*dz>=4 || items>count){since=now;x=px;y=py;z=pz;count=items;stage=0;return Action.NONE;}
        count=Math.min(count,items);
        if(now-since<WINDOW_MS)return Action.NONE;
        since=now;
        if(stage==0){stage=1;return Action.REPATH;}
        return Action.RELOG;
    }
}
