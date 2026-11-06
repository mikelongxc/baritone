package dev.autostore.core;

import java.util.*;

/** Distance is a routing hint; opening the container remains the source of truth for capacity. */
public final class DepotSelector {
    public static final long RETRY_MS = 30_000;
    private final Map<Integer,Long> retryAfter = new HashMap<>();
    public void clear() { retryAfter.clear(); }
    public void unavailable(int depot,long now) { retryAfter.put(depot,now+RETRY_MS); }
    public void observeCapacity(int depot,boolean hasRoom,long now) {
        if (hasRoom) retryAfter.remove(depot); else unavailable(depot,now);
    }
    public int nearest(List<Profile.Depot> depots,Policy.Pos origin,Set<Integer> excluded,long now) {
        int best=-1; double distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<depots.size();i++) {
            if(excluded.contains(i) || retryAfter.getOrDefault(i,Long.MIN_VALUE)>now) continue;
            Policy.Pos p=depots.get(i).position;
            double dx=(double)p.x()-origin.x(),dy=(double)p.y()-origin.y(),dz=(double)p.z()-origin.z();
            double candidate=dx*dx+dy*dy+dz*dz;
            if(candidate<distance) { distance=candidate; best=i; }
        }
        return best;
    }
}
