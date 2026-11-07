package dev.autostore.core;

import java.util.List;

/** In-memory handoff across Meteor reconnects; never replays old inventory transactions. */
public final class ReconnectResume {
    public record Job(String scope,List<String> targets,int quantity,boolean storageFirst) {
        public Job {targets=List.copyOf(targets);}
    }
    private Job job;
    private Object departed,joined,snapshotConnection;
    private long readySince=-1;
    public boolean pending(){return job!=null;}
    public boolean matchesScope(String scope){return pending() && job.scope().equals(scope);}
    public void capture(Job job,Object connection){cancel();this.job=job;departed=connection;}
    public void cancel(){job=null;departed=joined=snapshotConnection=null;readySince=-1;}
    public boolean newConnection(Object connection){return pending() && connection!=null && connection!=departed;}
    public void snapshot(Object connection){if(newConnection(connection))snapshotConnection=connection;}
    public boolean confirmed(Object connection){return newConnection(connection) && snapshotConnection==connection;}
    public Job poll(long now,Object connection,String scope,boolean ready) {
        if(!pending())return null;
        if(!newConnection(connection)){readySince=-1;return null;}
        if(joined!=connection){joined=connection;readySince=-1;}
        if(!job.scope().equals(scope)){cancel();return null;}
        if(!ready || !confirmed(connection)){readySince=-1;return null;}
        if(readySince<0){readySince=now;return null;}
        if(now-readySince<1_000)return null;
        Job result=job;cancel();return result;
    }
    public static boolean eligible(boolean enabled,boolean disconnectedScreen,boolean transferring,
                                   boolean mineActive,boolean paused,boolean toolStop,boolean recovery) {
        return enabled && disconnectedScreen && !transferring && mineActive && !paused && !toolStop && !recovery;
    }
}
