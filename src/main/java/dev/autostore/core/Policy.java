package dev.autostore.core;

import java.util.*;

/** Minecraft-independent safety decisions. Never serialize coordinates into status messages. */
public final class Policy {
    private Policy() {}
    public record Pos(int x, int y, int z) {}
    public record Box(Pos min, Pos max) {
        public boolean contains(Pos p) {
            return p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y && p.z >= min.z && p.z <= max.z;
        }
        public static Box between(Pos a, Pos b) {
            return new Box(new Pos(Math.min(a.x,b.x), Math.min(a.y,b.y), Math.min(a.z,b.z)),
                new Pos(Math.max(a.x,b.x), Math.max(a.y,b.y), Math.max(a.z,b.z)));
        }
        public boolean valid() { return min != null && max != null && min.x <= max.x && min.y <= max.y && min.z <= max.z; }
    }
    public record Stack(String item, int count, int max, boolean special) {
        public boolean empty() { return count == 0; }
    }
    public static int durabilityCutoff(int maximum, int absolute, double percent) {
        return Math.max(absolute, (int)Math.ceil(maximum * percent / 100.0));
    }
    public static boolean healthy(int remaining, int maximum, int absolute, double percent) {
        return remaining > durabilityCutoff(maximum, absolute, percent);
    }
    public static boolean depositable(Stack stack, int slot, Set<String> outputs, Set<String> protectedItems, Set<Integer> protectedSlots) {
        return !stack.empty() && !stack.special && outputs.contains(stack.item) && !protectedItems.contains(stack.item) && !protectedSlots.contains(slot);
    }
    /** Capacity for ONE output; use minimum over outputs, never count shared empty slots twice. */
    public static int capacity(List<Stack> inventory, String item, int max, Set<Integer> protectedSlots) {
        int capacity = 0;
        for (int i=0; i<inventory.size(); i++) {
            if (protectedSlots.contains(i)) continue;
            Stack s = inventory.get(i);
            if (s.empty()) capacity += max;
            else if (s.item.equals(item) && !s.special) capacity += Math.max(0, Math.min(max, s.max) - s.count);
        }
        return capacity;
    }
    public static boolean nearlyFull(int capacity, int stackSize, int reserveSlots, int pendingDrops) {
        return capacity <= (long)stackSize * reserveSlots + pendingDrops;
    }
    public enum Transfer { MOVED, NO_CHANGE, INVALID }
    public static Transfer verify(int playerBefore, int playerAfter, int chestBefore, int chestAfter, boolean protectedUnchanged) {
        int removed = playerBefore - playerAfter;
        int added = chestAfter - chestBefore;
        if (!protectedUnchanged || removed < 0 || added < 0 || removed != added) return Transfer.INVALID;
        return removed == 0 ? Transfer.NO_CHANGE : Transfer.MOVED;
    }
    public static boolean sameScope(String expected, String actual) { return expected != null && expected.equals(actual); }
}
