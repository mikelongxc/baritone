package dev.autostore.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageWatchTest {
    @Test void sustainedDamageWithoutProgressRelogsAtTenSecondsNotBefore() {
        var w=new DamageWatch();
        for(int t=0;t<10_000;t+=1000)assertFalse(w.sample(t,true,true,0,0));
        assertTrue(w.sample(10_000,true,true,0,0));
    }
    @Test void twoConfirmedBlocksInTenSecondsStillCountsAsLittleProgress() {
        var w=new DamageWatch();
        for(int t=0;t<10_000;t+=1000)assertFalse(w.sample(t,true,true,t>=5000?2:0,0));
        assertTrue(w.sample(10_000,true,true,2,0));
    }
    @Test void sharpDropFromRecentRateTriggersEvenAboveAbsoluteMinimum() {
        var w=new DamageWatch();w.sample(0,true,false,0,0);
        for(int t=1000;t<=10_000;t+=1000)w.sample(t,true,t==10_000,t/1000*2,0);
        for(int t=11_000;t<20_000;t+=1000)assertFalse(w.sample(t,true,true,23,0));
        assertTrue(w.sample(20_000,true,true,23,0));
    }
    @Test void normalProgressDespiteDamageDoesNotRelog() {
        var w=new DamageWatch();
        for(int t=0;t<=30_000;t+=1000)assertFalse(w.sample(t,true,true,t/1000,0));
    }
    @Test void pickupsCountWhenBlockUpdateEvidenceIsMissing() {
        var w=new DamageWatch();
        for(int t=0;t<=30_000;t+=1000)assertFalse(w.sample(t,true,true,0,t/1000));
    }
    @Test void isolatedOrWidelySpacedHitsNeverBecomeSustainedDamage() {
        var w=new DamageWatch();w.sample(0,true,true,0,0);
        assertFalse(w.sample(10_000,true,false,0,0));assertEquals(0,w.damageMillis(10_000));
        for(int t=15_000;t<=60_000;t+=5000)assertFalse(w.sample(t,true,true,0,0));
    }
    @Test void silenceBetweenHitsDoesNotEraseAnActiveAttackWindow() {
        var w=new DamageWatch();
        for(int t=0;t<10_000;t+=50)assertFalse(w.sample(t,true,t%2000==0,0,0));
        assertTrue(w.sample(10_000,true,true,0,0));
    }
    @Test void pausedJobsAndTransactionsDiscardDamageWindow() {
        var w=new DamageWatch();
        for(int t=0;t<10_000;t+=1000)w.sample(t,true,true,0,0);
        assertFalse(w.sample(10_000,false,true,0,0));
        assertFalse(w.sample(30_000,true,true,0,0));assertEquals(0,w.damageMillis(30_000));
    }
    @Test void resetAfterRelogRequiresANewSustainedEpisode() {
        var w=new DamageWatch();
        for(int t=0;t<=10_000;t+=1000)w.sample(t,true,true,0,0);
        w.reset();assertFalse(w.sample(20_000,true,true,0,0));
        assertEquals(0,w.damageMillis(20_000));
    }    @Test void delayedDropsDoNotDoubleCountTheSameTwoBlocks() {
        var w=new DamageWatch();
        for(int t=0;t<10_000;t+=1000)assertFalse(w.sample(t,true,true,t>=4000?2:0,t>=6000?2:0));
        assertTrue(w.sample(10_000,true,true,2,2));
    }

}
