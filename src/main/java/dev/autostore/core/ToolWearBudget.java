package dev.autostore.core;

import java.util.Arrays;

/** Worst-case local wear since our last full server inventory barrier; ignores Unbreaking savings. */
public final class ToolWearBudget {
    private final int[] pending=new int[9];
    public void confirmed(){Arrays.fill(pending,0);}
    public boolean needsConfirmation(int slot,int remaining,int maximum,int cost) {
        return maximum>0 && (long)remaining-pending[slot]-cost<ToolReserve.cutoff(maximum);
    }
    public void reserve(int slot,int cost){pending[slot]=Math.addExact(pending[slot],Math.max(0,cost));}
}
