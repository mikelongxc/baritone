package dev.autostore.core;

import java.util.ArrayDeque;

/** Sustained server-reported damage with little real mining progress, independent of combat pauses. */
public final class DamageWatch {
    public static final long WINDOW_MS=10_000,MAX_HIT_GAP_MS=4_000;
    private record Progress(long time,int blocks,int items) {}
    private final ArrayDeque<Progress> progress=new ArrayDeque<>();
    private boolean initialized;
    private int confirmed,items,hits,baseline;
    private long damageSince=-1,lastDamage=-1;
    public void reset(){progress.clear();initialized=false;damageSince=lastDamage=-1;hits=baseline=0;}
    public long damageMillis(long now){return damageSince<0 || now-lastDamage>MAX_HIT_GAP_MS?0:Math.max(0,now-damageSince);}
    public boolean sample(long now,boolean eligible,boolean hurt,int accepted,int collected) {
        if(!eligible){reset();return false;}
        if(initialized) {
            // Do not double-count the block and its drops. Client position/local block
            // destruction are deliberately not progress evidence for this watchdog.
            int blocks=Math.max(0,accepted-confirmed),gains=Math.max(0,collected-items);
            if(blocks>0 || gains>0)progress.addLast(new Progress(now,blocks,gains));
        }
        initialized=true;confirmed=accepted;items=collected;
        while(!progress.isEmpty() && progress.peekFirst().time()<=now-WINDOW_MS)progress.removeFirst();
        if(lastDamage>=0 && now-lastDamage>MAX_HIT_GAP_MS){damageSince=lastDamage=-1;hits=baseline=0;}
        int recent=Math.max(progress.stream().mapToInt(Progress::blocks).sum(),progress.stream().mapToInt(Progress::items).sum());
        if(hurt) {
            if(damageSince<0){damageSince=now;baseline=recent;}
            lastDamage=now;hits++;
        }
        boolean littleProgress=recent<=2 || baseline>=8 && (long)recent*4<baseline;
        return damageSince>=0 && now-damageSince>=WINDOW_MS && hits>=3 && littleProgress;
    }
}
