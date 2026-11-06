package dev.autostore.core;

import java.util.*;

/** Alternate feet positions around either chest half; Baritone decides which are pathable. */
public final class DepotApproach {
    private DepotApproach() {}
    public static List<Policy.Pos> alternatives(Policy.Pos chest,Policy.Pos otherHalf,Set<Policy.Pos> failed) {
        Set<Policy.Pos> result=new LinkedHashSet<>();
        add(result,chest);
        if(otherHalf!=null)add(result,otherHalf);
        result.remove(chest);result.remove(otherHalf);result.removeAll(failed);
        return List.copyOf(result);
    }
    private static void add(Set<Policy.Pos> result,Policy.Pos chest) {
        for(int dy:new int[]{0,-1})for(int[] side:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
            result.add(new Policy.Pos(chest.x()+side[0],chest.y()+dy,chest.z()+side[1]));
        result.add(new Policy.Pos(chest.x(),chest.y()+1,chest.z()));
    }
}
