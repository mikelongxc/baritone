package dev.autostore.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {
    @TempDir Path directory;
    @Test void scopeSeparatesServerAccountAndDimension() {
        String a=ProfileStore.scope("server-a","account-a","overworld");
        assertNotEquals(a,ProfileStore.scope("server-b","account-a","overworld"));
        assertNotEquals(a,ProfileStore.scope("server-a","account-b","overworld"));
        assertNotEquals(a,ProfileStore.scope("server-a","account-a","nether"));
        assertFalse(a.contains("server"));
    }
    @Test void atomicCheckpointAlwaysLoadsPausedAndKeepsSavedTargets() throws Exception {
        ProfileStore store=new ProfileStore(directory);String scope=ProfileStore.scope("test","user","dimension");
        Profile p=store.load(scope);p.savedTargets.add("minecraft:sand");p.checkpoint="VERIFYING";store.save(p);
        Profile loaded=store.load(scope);assertEquals("PAUSED",loaded.checkpoint);assertEquals(p.savedTargets,loaded.savedTargets);
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void corruptOrCrossScopeProfileFailsClosed() throws Exception {
        ProfileStore store=new ProfileStore(directory);String a=ProfileStore.scope("a","b","c"),b=ProfileStore.scope("b","b","c");
        Files.writeString(directory.resolve(a+".json"),"not json");assertThrows(Loop.SafetyException.class,()->store.load(a));
        Profile p=new Profile();p.scope=b;store.save(p);
        Files.copy(directory.resolve(b+".json"),directory.resolve(a+".json"),StandardCopyOption.REPLACE_EXISTING);
        assertThrows(Loop.SafetyException.class,()->store.load(a));
    }
    @Test void doubleChestHalvesCannotBeRegisteredTwice() {
        Profile p=valid();Profile.Depot first=p.depots.getFirst();first.size=54;first.otherHalf=new Policy.Pos(2,0,0);
        Profile.Depot duplicate=new Profile.Depot();duplicate.id="second";duplicate.position=first.otherHalf;duplicate.stand=new Policy.Pos(3,0,0);duplicate.block="minecraft:chest";duplicate.size=27;p.depots.add(duplicate);
        assertThrows(Loop.SafetyException.class,()->p.validate(p.scope));
    }
    @Test void invalidTimeoutsAndForeignScopeRejected() {
        Profile p=valid();p.validate(p.scope);p.openTimeoutMs=Long.MAX_VALUE;
        assertThrows(Loop.SafetyException.class,()->p.validate(p.scope));
        assertThrows(Loop.SafetyException.class,()->valid().validate("foreign"));
    }
    @Test void legacyProfileMigratesWithoutLosingDepotsOrCustomProtection() {
        ProfileStore store=new ProfileStore(directory);Profile old=valid();old.scope=ProfileStore.scope("a","b","c");old.schema=1;
        old.protectedSlots=new java.util.LinkedHashSet<>(java.util.List.of(0,1,2,3,4,5,6,7,8));old.reserveSlots=3;
        store.save(old);Profile p=store.load(old.scope);
        assertEquals(2,p.schema);assertEquals(1,p.depots.size());assertEquals(old.depots.getFirst().position,p.depots.getFirst().position);
        assertTrue(p.protectedSlots.isEmpty());assertNull(p.quarry);assertNull(p.corridor);assertEquals(1,p.reserveSlots);p.validate(p.scope);
        old.protectedSlots=java.util.Set.of(4,12);store.save(old);assertEquals(old.protectedSlots,store.load(old.scope).protectedSlots);
    }
    @Test void setupNeedsOnlyOneRegisteredContainer() {
        Profile p=valid();p.quarry=null;p.corridor=null;p.validate(p.scope);
        assertTrue(p.mayWalk(new Policy.Pos(1_000_000,0,-1_000_000)));
    }
    private Profile valid() {
        Profile p=new Profile();p.scope="scope";p.quarry=Policy.Box.between(new Policy.Pos(10,0,0),new Policy.Pos(20,10,10));p.corridor=Policy.Box.between(new Policy.Pos(0,0,0),new Policy.Pos(20,10,10));
        Profile.Depot d=new Profile.Depot();d.id="first";d.position=new Policy.Pos(1,0,0);d.stand=new Policy.Pos(0,0,0);d.size=27;d.block="minecraft:chest";p.depots.add(d);return p;
    }
}
