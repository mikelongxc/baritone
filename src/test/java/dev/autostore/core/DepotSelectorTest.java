package dev.autostore.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DepotSelectorTest {
    private Profile.Depot at(int x,int y,int z) { Profile.Depot d=new Profile.Depot();d.position=new Policy.Pos(x,y,z);return d; }
    private final Policy.Pos origin=new Policy.Pos(0,0,0);
    @Test void choosesNearestInsteadOfFirstRegistered() {
        DepotSelector s=new DepotSelector();
        assertEquals(1,s.nearest(List.of(at(90,0,0),at(4,0,0),at(20,0,0)),origin,Set.of(),0));
    }
    @Test void ranksFromCurrentLocationIncludingHeight() {
        DepotSelector s=new DepotSelector();var depots=List.of(at(1,100,0),at(5,0,0),at(40,0,0));
        assertEquals(1,s.nearest(depots,origin,Set.of(),0));
        assertEquals(2,s.nearest(depots,new Policy.Pos(42,0,0),Set.of(),0));
    }
    @Test void fullChestIsSkippedThenRecheckedWhenObservationIsStale() {
        DepotSelector s=new DepotSelector();var depots=List.of(at(2,0,0),at(10,0,0));s.observeCapacity(0,false,100);
        assertEquals(1,s.nearest(depots,origin,Set.of(),30_099));
        assertEquals(0,s.nearest(depots,origin,Set.of(),30_100));
    }
    @Test void confirmedNewSpaceRestoresNearestImmediately() {
        DepotSelector s=new DepotSelector();var depots=List.of(at(2,0,0),at(10,0,0));s.unavailable(0,100);s.observeCapacity(0,true,150);
        assertEquals(0,s.nearest(depots,origin,Set.of(),151));
    }
    @Test void attemptedAndUnavailableChestsAreNotVisitedAgainDuringAPass() {
        DepotSelector s=new DepotSelector();var depots=List.of(at(2,0,0),at(10,0,0),at(15,0,0));s.unavailable(1,0);
        assertEquals(2,s.nearest(depots,origin,Set.of(0),1));
        assertEquals(-1,s.nearest(depots,origin,Set.of(0,2),1));
        assertEquals(-1,s.nearest(List.of(),origin,Set.of(),1));
    }
    @Test void tiesAreStableAndLargeDifferencesDoNotOverflow() {
        DepotSelector s=new DepotSelector();
        assertEquals(0,s.nearest(List.of(at(-5,0,0),at(5,0,0)),origin,Set.of(),0));
        assertEquals(1,s.nearest(List.of(at(Integer.MIN_VALUE,0,0),at(Integer.MAX_VALUE-1,0,0)),new Policy.Pos(Integer.MAX_VALUE,0,0),Set.of(),0));
    }
}
