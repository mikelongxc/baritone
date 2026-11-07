package baritone.cache;

import java.util.*;

/** Failed paths are temporary evidence; geometry and server state can change. */
public final class TargetCooldown<T> {
    private final Map<T,Long> until=new LinkedHashMap<>();
    public synchronized void reject(T target,long now) {
        until.put(target,now+30_000);
        if(until.size()>2048)until.remove(until.keySet().iterator().next());
    }
    public synchronized List<T> snapshot(long now) {
        until.entrySet().removeIf(e->e.getValue()<=now);
        return List.copyOf(until.keySet());
    }
    public synchronized void clear(){until.clear();}
}
