package dev.autostore.core;

import java.util.*;

/** The requested five-percent floor applies independently of old profile/module defaults. */
public final class ToolReserve {
    private ToolReserve() {}
    public static int cutoff(int maximum) {return Math.max(1,(int)Math.ceil(maximum*0.05));}
    public static boolean belowFloor(int remaining,int maximum){return maximum>0 && (long)remaining*100<(long)maximum*5;}
    public static boolean usable(int remaining,int maximum) {return maximum<=0 || remaining>cutoff(maximum);}
    public record Candidate(int slot,boolean suitable,boolean allowed,int remaining,int maximum,double speed) {}
    public static OptionalInt choose(List<Candidate> candidates) {
        return candidates.stream().filter(c -> c.slot>=0 && c.slot<36 && c.suitable && c.allowed && usable(c.remaining,c.maximum))
            .min(Comparator.<Candidate>comparingInt(c -> c.slot<9?0:1)
                .thenComparing(Comparator.comparingDouble(Candidate::speed).reversed())
                .thenComparing(Comparator.comparingInt(Candidate::remaining).reversed()).thenComparingInt(Candidate::slot))
            .map(c -> OptionalInt.of(c.slot)).orElseGet(OptionalInt::empty);
    }
    public static int menuSlot(int inventorySlot) {
        if(inventorySlot<0 || inventorySlot>=36)throw new IllegalArgumentException("Not a main inventory slot");
        return inventorySlot<9?36+inventorySlot:inventorySlot;
    }
}
