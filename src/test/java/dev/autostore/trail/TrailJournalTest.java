package dev.autostore.trail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class TrailJournalTest {
    @TempDir Path temp;
    String scope="a".repeat(64);
    @Test void atomicallyRetainsPlacedBoxAndRoute()throws Exception {var j=TrailJournal.load(temp,scope);j.boxX=1;j.boxY=2;j.boxZ=3;j.boxType="minecraft:shulker_box";j.boxIdentity="test";j.boxesBefore=1;j.placed=true;j.headingX=1;j.evidence.put(4L,TrailRoute.Kind.NEW);j.save(temp);var got=TrailJournal.load(temp,scope);assertTrue(got.hasBox());assertTrue(got.placed);assertEquals(TrailRoute.Kind.NEW,got.evidence.get(4L));assertEquals(1,Files.list(temp).count());}
    @Test void scopedFilesDoNotShareRecovery()throws Exception{var j=TrailJournal.load(temp,scope);j.searches=7;j.save(temp);assertEquals(0,TrailJournal.load(temp,"b".repeat(64)).searches);}
    @Test void malformedCheckpointFailsClosed()throws Exception{Files.writeString(temp.resolve(scope+".json"),"{}");assertThrows(Exception.class,()->TrailJournal.load(temp,scope));}
    @Test void rejectsPathTraversal(){assertThrows(Exception.class,()->TrailJournal.load(temp,"../private"));}
    @Test void clearingRecoveredBoxPreservesTrail()throws Exception{var j=TrailJournal.load(temp,scope);j.searches=2;j.boxX=1;j.clearBox();j.save(temp);var got=TrailJournal.load(temp,scope);assertFalse(got.hasBox());assertEquals(2,got.searches);}
    @Test void invalidSearchCountDoesNotResume()throws Exception{var j=TrailJournal.load(temp,scope);j.searches=9;j.save(temp);assertThrows(Exception.class,()->TrailJournal.load(temp,scope));}
}
