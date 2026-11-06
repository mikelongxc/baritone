package dev.autostore.core;

import java.util.*;

public final class Profile {
    public int schema = 2;
    public String scope;
    public Policy.Box quarry, corridor;
    public Set<String> targets = new LinkedHashSet<>(List.of("minecraft:sand"));
    public Set<String> outputs = new LinkedHashSet<>(List.of("minecraft:sand"));
    public Set<String> protectedItems = new LinkedHashSet<>(List.of("minecraft:ender_chest", "minecraft:firework_rocket", "minecraft:totem_of_undying"));
    public Set<Integer> protectedSlots = new LinkedHashSet<>();
    public Set<String> tools = new LinkedHashSet<>(List.of("minecraft:diamond_shovel", "minecraft:netherite_shovel", "minecraft:iron_shovel", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe", "minecraft:iron_pickaxe"));
    public List<Depot> depots = new ArrayList<>();
    public List<String> savedTargets = new ArrayList<>();
    public String checkpoint = "PAUSED", reason = "Manual start required";
    public int reserveSlots = 1, durabilityReserve = 30;
    public double durabilityPercent = 1;
    public long travelTimeoutMs = 120_000, openTimeoutMs = 8_000, progressTimeoutMs = 60_000;
    public static final class Depot {
        public String id, block;
        public Policy.Pos position, otherHalf, stand;
        public int size;
        public Set<String> outputs = new LinkedHashSet<>(); // empty inherits profile
        public Map<String,Integer> lastObserved = new TreeMap<>();
        public int lastEmptySlots;
        public long observedAt;
        public String status = "UNOBSERVED";
    }
    public void validate(String actualScope) {
        if (schema != 2 || !Policy.sameScope(scope, actualScope)) bad("Profile scope or schema mismatch");
        if (targets == null || targets.isEmpty() || outputs == null || outputs.isEmpty()) bad("Mining target and deposit items are required");
        if (protectedItems == null || protectedSlots == null || protectedSlots.stream().anyMatch(i -> i == null || i < 0 || i >= 36)) bad("Invalid protected slot configuration");
        if (reserveSlots < 1 || reserveSlots > 26 || reserveSlots >= 36-protectedSlots.size() || durabilityReserve < 1 || !Double.isFinite(durabilityPercent) || durabilityPercent < 0 || durabilityPercent > 100) bad("Invalid capacity or durability limits");
        if (travelTimeoutMs < 1000 || travelTimeoutMs > 600_000 || openTimeoutMs < 1000 || openTimeoutMs > 60_000) bad("Timeouts must be finite and bounded");
        if (depots == null || depots.isEmpty() || depots.size() > 32) bad("Register between one and 32 depots");
        Set<Policy.Pos> occupied = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Depot d : depots) {
            if (d == null || d.id == null || !d.id.matches("[a-zA-Z0-9_-]{1,32}") || !ids.add(d.id) || d.position == null || d.stand == null || d.block == null || d.outputs == null || !outputs.containsAll(d.outputs)) bad("Invalid depot definition");
            if (!occupied.add(d.position) || (d.otherHalf != null && !occupied.add(d.otherHalf))) bad("Duplicate container or double chest");
            if (d.size != 27 && d.size != 54 || d.size == 54 != (d.otherHalf != null)) bad("Invalid container size");
        }
    }
    public boolean mayTarget(Policy.Pos p) {
        for (Depot d : depots) if (storageOrSupport(p,d.position) || (d.otherHalf != null && storageOrSupport(p,d.otherHalf))) return false;
        return true;
    }
    private boolean storageOrSupport(Policy.Pos block,Policy.Pos chest) {
        return block.x()==chest.x() && block.z()==chest.z()
            && (block.y()==chest.y() || (long)block.y()==(long)chest.y()-1);
    }
    public boolean mayWalk(Policy.Pos p) { return true; }
    private static void bad(String text) { throw new Loop.SafetyException(text); }
}
