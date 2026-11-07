package dev.autostore.core;

import baritone.cache.SearchCandidates;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchCandidatesTest {
    @Test void rejectedNearbySandDoesNotHideTheNextLoadedPatch() {
        var selected=SearchCandidates.take(IntStream.range(0,192).boxed(),i -> i>=128,64);
        assertEquals(IntStream.range(128,192).boxed().toList(),selected);
    }
    @Test void heightAndReachabilityRejectionsDoNotConsumeQuota() {
        record Candidate(int height,boolean reachable) {}
        var valid=new Candidate(70,true);
        var selected=SearchCandidates.take(Stream.of(new Candidate(-20,true),new Candidate(70,false),valid),
            c -> c.height()>=60 && c.reachable(),1);
        assertEquals(List.of(valid),selected);
    }
    @Test void depletedChunksAreSkippedAndClosestAcceptedOrderIsPreserved() {
        var chunks=List.of(List.<Integer>of(),List.of(1,2,3),List.of(4,5),List.of(6));
        assertEquals(List.of(2,4),SearchCandidates.take(chunks.stream().flatMap(List::stream),i -> i%2==0,2));
    }
    @Test void scanningStopsWhenEnoughAcceptedTargetsHaveBeenFound() {
        AtomicInteger visited=new AtomicInteger();
        assertEquals(List.of(10,11),SearchCandidates.take(IntStream.range(0,1000).boxed().peek(i -> visited.incrementAndGet()),i -> i>=10,2));
        assertEquals(12,visited.get());
    }
    @Test void emptyZeroAndUnlimitedSearchesRetainTheirMeaning() {
        assertEquals(List.of(),SearchCandidates.take(Stream.of(1,2,3),i -> false,64));
        assertEquals(List.of(),SearchCandidates.take(Stream.of(1,2,3),i -> true,0));
        assertEquals(List.of(2,3),SearchCandidates.take(Stream.of(1,2,3),i -> i>1,-1));
    }
    @Test void denseChunkOrderDoesNotHideCloserStoneFaces() {
        List<Integer> chunkOrder=new ArrayList<>(IntStream.range(100,300).boxed().toList());
        chunkOrder.addAll(List.of(2,1,3));
        assertEquals(List.of(1,2,3),SearchCandidates.nearest(chunkOrder.stream(),i->true,Comparator.naturalOrder(),3));
    }
    @Test void adjacentChunkFaceIsConsideredBeforeLocalCandidateQuota() {
        record Face(int x,int z){}
        List<Face> candidates=new ArrayList<>();for(int x=0;x<8;x++)for(int z=0;z<16;z++)candidates.add(new Face(x,z));
        Face nextChunk=new Face(16,8);candidates.add(nextChunk);
        var closest=SearchCandidates.nearest(candidates.stream(),p->true,
            Comparator.comparingInt(p->(p.x()-15)*(p.x()-15)+(p.z()-8)*(p.z()-8)),1);
        assertEquals(List.of(nextChunk),closest);
    }
    @Test void bulkFacesArePreferredToBuriedCandidatesAndExcludedTargetsDoNotConsumeQuota() {
        record Hit(int distance,boolean exposed,boolean excluded){}
        var face=new Hit(3,true,false);
        var result=SearchCandidates.nearest(Stream.of(new Hit(1,false,false),new Hit(2,true,true),face),
            h->!h.excluded(),Comparator.<Hit>comparingInt(h->h.exposed()?0:1).thenComparingInt(Hit::distance),1);
        assertEquals(List.of(face),result);
    }
    @Test void failedTargetsExpireWithoutRestartingMineAndNewFailuresDoNotExtendOthers() {
        var cooldown=new baritone.cache.TargetCooldown<String>();
        cooldown.reject("stone",0);cooldown.reject("end_stone",10_000);
        assertEquals(List.of("stone","end_stone"),cooldown.snapshot(29_999));
        assertEquals(List.of("end_stone"),cooldown.snapshot(30_000));
        assertEquals(List.of(),cooldown.snapshot(40_000));
    }
    @Test void cooldownSnapshotsAreStableAcrossLaterRejectionsAndResetClearsThem() {
        var cooldown=new baritone.cache.TargetCooldown<Integer>();cooldown.reject(1,0);
        var before=cooldown.snapshot(0);cooldown.reject(2,1);
        assertEquals(List.of(1),before);cooldown.clear();assertEquals(List.of(),cooldown.snapshot(2));
    }

}
