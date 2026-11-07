package dev.autostore.trail;

import java.util.*;

/** Chunk evidence is not terrain collision data. Baritone still owns each flight path. */
public final class TrailRoute {
    public record Cell(int x,int z) {
        public long key(){return ((long)x<<32)|(z&0xffffffffL);}
        public static Cell of(long key){return new Cell((int)(key>>32),(int)key);}
        public int distance(Cell other){return Math.max(Math.abs(x-other.x),Math.abs(z-other.z));}
    }
    public enum Kind { OLD, NEW }
    private final LinkedHashMap<Long,Kind> evidence=new LinkedHashMap<>();
    private final LinkedHashSet<Long> visited=new LinkedHashSet<>();
    public double headingX,headingZ;
    public TrailRoute(double x,double z){headingX=x;headingZ=z;}
    public void observe(Cell cell,Kind kind) {
        // Fresh terrain encountered by this run can never turn into our own "old trail".
        evidence.merge(cell.key(),kind,(a,b)->a==Kind.NEW?Kind.NEW:b);
        trim(evidence,16384);
    }
    public void visit(Cell cell){visited.add(cell.key());while(visited.size()>4096)visited.remove(visited.iterator().next());}
    public Map<Long,Kind> evidence(){return Map.copyOf(evidence);}
    public Set<Long> visited(){return Set.copyOf(visited);}
    public void restore(Map<Long,Kind> saved,Collection<Long> passed){saved.forEach((k,v)->observe(Cell.of(k),v));passed.forEach(k->visit(Cell.of(k)));}
    private static void trim(Map<?,?> map,int max){while(map.size()>max)map.remove(map.keySet().iterator().next());}
    public Optional<Cell> next(Cell here,int radius) {
        ArrayDeque<Cell> queue=new ArrayDeque<>();Set<Long> seen=new HashSet<>();
        // The player can be at the edge in a new/unknown chunk. Join only an old lead
        // within two chunks, rather than jumping to disconnected distant evidence.
        ArrayDeque<Cell> join=new ArrayDeque<>();Set<Long> joined=new HashSet<>();join.add(here);joined.add(here.key());
        while(!join.isEmpty()) {
            Cell c=join.remove();
            if(evidence.get(c.key())==Kind.OLD){queue.add(c);seen.add(c.key());}
            if(c.distance(here)>=2)continue;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Cell n=new Cell(c.x+d[0],c.z+d[1]);
                if(evidence.get(n.key())!=Kind.NEW && joined.add(n.key()))join.add(n);
            }
        }
        Cell best=null;double bestScore=-Double.MAX_VALUE;
        while(!queue.isEmpty()) {
            Cell c=queue.remove();int dx=c.x-here.x,dz=c.z-here.z;
            double distance=Math.hypot(dx,dz);
            int edges=0;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                Cell n=new Cell(c.x+d[0],c.z+d[1]);Kind kind=evidence.get(n.key());
                if(kind==Kind.NEW)edges++;
                if(kind==Kind.OLD && n.distance(here)<=radius && seen.add(n.key()))queue.add(n);
            }
            if(distance<3 || visited.contains(c.key()))continue;
            double forward=(dx*headingX+dz*headingZ)/distance;
            double score=distance+forward*8+edges*2;
            if(score>bestScore || score==bestScore && (best==null || c.key()<best.key())){best=c;bestScore=score;}
        }
        return Optional.ofNullable(best);
    }
    public void commit(Cell from,Cell to) {
        double dx=to.x-from.x,dz=to.z-from.z,n=Math.hypot(dx,dz);
        if(n>0){headingX=dx/n;headingZ=dz/n;}
    }
    /** Eight bounded probes around the last trail end. Never extend a search from its own endpoint. */
    public Cell search(Cell anchor,int attempt) {
        if(attempt<0 || attempt>=8)throw new IllegalArgumentException("Search exhausted");
        double[] turns={0,45,-45,90,-90,135,-135,180};
        double angle=Math.atan2(headingZ,headingX)+Math.toRadians(turns[attempt]);
        return new Cell(anchor.x+(int)Math.round(6*Math.cos(angle)),anchor.z+(int)Math.round(6*Math.sin(angle)));
    }
}
