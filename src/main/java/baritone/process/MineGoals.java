package baritone.process;

import baritone.api.pathing.goals.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import net.minecraft.tags.BlockTags;

/** Native miner goal decisions; independent of AutoStore and its process ownership. */
public final class MineGoals {
    private MineGoals() {}
    public static boolean surfaceSand(boolean legit,List<Block> blocks) {
        return !legit && !blocks.isEmpty() && blocks.stream().allMatch(b -> b==Blocks.SAND || b==Blocks.RED_SAND);
    }
    public static boolean nearbyMaterial(boolean legit,List<Block> blocks) {
        return !legit && !blocks.isEmpty() && blocks.stream().allMatch(b -> b==Blocks.SAND || b==Blocks.RED_SAND
            || b==Blocks.STONE || b==Blocks.END_STONE || b==Blocks.DEEPSLATE || b==Blocks.NETHERRACK
            || b==Blocks.BIRCH_LOG || b==Blocks.OAK_LOG || b==Blocks.SPRUCE_LOG || b==Blocks.JUNGLE_LOG
            || b==Blocks.ACACIA_LOG || b==Blocks.DARK_OAK_LOG || b==Blocks.MANGROVE_LOG || b==Blocks.CHERRY_LOG
            || b==Blocks.PALE_OAK_LOG || b==Blocks.CRIMSON_STEM || b==Blocks.WARPED_STEM
            || taggedLog(b));
    }
    private static boolean taggedLog(Block block) {
        // Registry tags may be unavailable during bootstrap/reload. Known vanilla logs
        // above still work; custom log tags become available after the world binds them.
        try {return block.defaultBlockState().is(BlockTags.LOGS);}
        catch(IllegalStateException tagsNotBound){return false;}
    }
    public static boolean recoverDepletedSand(boolean sandOnly,boolean alreadyExploring,boolean noTargets,boolean explorationEnabled) {
        return sandOnly && !alreadyExploring && noTargets && explorationEnabled;
    }
    public static Goal targetsOrNull(List<Goal> targets) {
        return targets.isEmpty()?null:new GoalComposite(targets.toArray(Goal[]::new));
    }
    public static GoalRunAway exploration(BlockPos origin,int preferredY) {
        return new GoalRunAway(1,preferredY,origin) {
            @Override public boolean isInGoal(int x,int y,int z) {return false;}
            @Override public double heuristic() {return Double.NEGATIVE_INFINITY;}
            @Override public String toString() {return "Mine exploration (location hidden)";}
        };
    }
}
