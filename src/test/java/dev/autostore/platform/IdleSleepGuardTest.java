package dev.autostore.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class IdleSleepGuardTest {
    @Test void unsupportedPlatformsDoNotStartAProcess() {
        try(IdleSleepGuard guard=new IdleSleepGuard("Other OS",ProcessHandle.current().pid())) {
            assertFalse(guard.supported());assertFalse(guard.start());assertFalse(guard.isActive());
        }
    }
    @Test void macAssertionStartsOnceAndIsReleasedOnClose() throws Exception {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"));
        ProcessHandle child;
        try(IdleSleepGuard guard=new IdleSleepGuard()) {
            assertTrue(guard.start());long pid=guard.processId();
            Thread.sleep(100);
            assertTrue(guard.isActive());assertTrue(guard.start());assertEquals(pid,guard.processId());
            child=ProcessHandle.of(pid).orElseThrow();
        }
        long end=System.nanoTime()+2_000_000_000L;
        while(child.isAlive() && System.nanoTime()<end)Thread.sleep(10);
        assertFalse(child.isAlive(),"Idle sleep assertion must be released after the job stops");
    }
}
