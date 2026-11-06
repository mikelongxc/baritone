package dev.autostore.core;

/** Client motion is deliberately not evidence of server progress. */
public final class DesyncWatch {
    public static final long WINDOW_MS=10_000;
    private boolean initialized;
    private long signalSince=-1,pausedAt=-1;
    private int attempts,confirmed,items,corrections;
    public void reset(){initialized=false;signalSince=-1;pausedAt=-1;}
    public long suspectMillis(long now){return signalSince<0?0:Math.max(0,(pausedAt<0?now:pausedAt)-signalSince);}
    public int attemptSamples(int tried){return initialized?Math.max(0,tried-attempts):0;}
    public boolean sample(long now,boolean eligible,int tried,int accepted,int collected,int corrected) {
        if(!eligible){if(pausedAt<0)pausedAt=now;return false;}
        if(pausedAt>=0){if(signalSince>=0)signalSince+=Math.max(0,now-pausedAt);pausedAt=-1;}
        if(!initialized || accepted>confirmed || collected>items) {
            initialized=true;signalSince=-1;attempts=tried;confirmed=accepted;items=collected;corrections=corrected;return false;
        }
        items=Math.min(items,collected);
        if(signalSince<0 && (tried>attempts || corrected>corrections))signalSince=now;
        return signalSince>=0 && now-signalSince>=WINDOW_MS && (tried-attempts>=8 || corrected-corrections>=5);
    }
}
