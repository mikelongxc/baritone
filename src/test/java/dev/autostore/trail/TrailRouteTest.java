package dev.autostore.trail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class TrailRouteTest {
    private final TrailRoute.Cell origin=new TrailRoute.Cell(0,0);
    private TrailRoute straight(){TrailRoute r=new TrailRoute(1,0);for(int x=-12;x<=12;x++){r.observe(new TrailRoute.Cell(x,0),TrailRoute.Kind.OLD);r.observe(new TrailRoute.Cell(x,1),TrailRoute.Kind.NEW);r.observe(new TrailRoute.Cell(x,-1),TrailRoute.Kind.NEW);}return r;}
    @Test void followsExistingTrailInFacingDirection(){assertTrue(straight().next(origin,14).orElseThrow().x()>0);}
    @Test void newTerrainDoesNotBecomeOwnTrail(){TrailRoute r=straight();for(int x=1;x<=12;x++){var c=new TrailRoute.Cell(x,0);r.observe(c,TrailRoute.Kind.NEW);r.observe(c,TrailRoute.Kind.OLD);}assertTrue(r.next(origin,14).orElseThrow().x()<0);}
    @Test void disconnectedDistantOldChunkIsNotLead(){TrailRoute r=new TrailRoute(1,0);r.observe(new TrailRoute.Cell(10,0),TrailRoute.Kind.OLD);assertTrue(r.next(origin,14).isEmpty());}
    @Test void newChunkWallSplitsTrail(){TrailRoute r=straight();r.observe(new TrailRoute.Cell(3,0),TrailRoute.Kind.NEW);assertTrue(r.next(origin,14).orElseThrow().x()<0);}
    @Test void joiningTrailDoesNotJumpAcrossFreshWall(){TrailRoute r=straight();for(int z=-3;z<=3;z++)r.observe(new TrailRoute.Cell(1,z),TrailRoute.Kind.NEW);assertTrue(r.next(origin,14).orElseThrow().x()<0);}
    @Test void followsBendingCorridor(){TrailRoute r=new TrailRoute(1,0);for(int x=0;x<=4;x++)r.observe(new TrailRoute.Cell(x,0),TrailRoute.Kind.OLD);for(int z=1;z<=10;z++)r.observe(new TrailRoute.Cell(4,z),TrailRoute.Kind.OLD);assertTrue(r.next(origin,14).orElseThrow().z()>0);}
    @Test void noRevisitingAnExhaustedLoop(){TrailRoute r=straight();for(int x=-12;x<=12;x++)r.visit(new TrailRoute.Cell(x,0));assertTrue(r.next(origin,14).isEmpty());}
    @Test void searchRemainsAnchoredAndFinite(){TrailRoute r=straight();Set<TrailRoute.Cell> points=new HashSet<>();for(int i=0;i<8;i++){var p=r.search(origin,i);assertTrue(p.distance(origin)<=6);points.add(p);}assertEquals(8,points.size());assertThrows(IllegalArgumentException.class,()->r.search(origin,8));}
    @Test void checkpointPreservesHeadingEvidenceAndVisited(){TrailRoute r=straight();r.visit(new TrailRoute.Cell(12,0));r.commit(origin,new TrailRoute.Cell(0,5));TrailRoute restored=new TrailRoute(r.headingX,r.headingZ);restored.restore(r.evidence(),r.visited());assertEquals(r.next(origin,14),restored.next(origin,14));}
    @Test void signedChunkCoordinatesRoundTrip(){for(int x:new int[]{-30000000,-1,0,1,30000000})for(int z:new int[]{-2000000,-1,0,1,2000000}){var c=new TrailRoute.Cell(x,z);assertEquals(c,TrailRoute.Cell.of(c.key()));}}
    @Test void evidenceAndVisitMemoryAreBounded(){TrailRoute r=straight();for(int x=0;x<20000;x++){var c=new TrailRoute.Cell(x,2);r.observe(c,TrailRoute.Kind.NEW);r.visit(c);}assertEquals(16384,r.evidence().size());assertEquals(4096,r.visited().size());}
}
