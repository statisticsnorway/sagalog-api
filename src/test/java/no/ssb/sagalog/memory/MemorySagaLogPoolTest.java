package no.ssb.sagalog.memory;

import no.ssb.sagalog.SagaLog;
import no.ssb.sagalog.SagaLogEntry;
import no.ssb.sagalog.SagaLogId;
import no.ssb.sagalog.SagaLogOwner;
import no.ssb.sagalog.SagaLogOwnership;
import no.ssb.sagalog.SagaLogPool;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class MemorySagaLogPoolTest {

    SagaLogPool pool;

    @BeforeMethod
    public void setup() {
        MemorySagaLogInitializer initializer = new MemorySagaLogInitializer();
        Map<String, String> configuration = initializer.configurationOptionsAndDefaults();
        this.pool = initializer.initialize(configuration);
    }

    @AfterMethod
    public void teardown() {
        pool.shutdown();
    }

    @Test
    public void thatIdForHasCorrectHashcodeEquals() {
        assertEquals(pool.idFor("somelogid"), pool.idFor("somelogid"));
        assertFalse(pool.idFor("somelogid") == pool.idFor("somelogid"));
        assertNotEquals(pool.idFor("somelogid"), pool.idFor("otherlogid"));
    }

    @Test
    public void thatConnectDoesProvideWorkingSagaLog() {
        SagaLogId logId = pool.idFor("testlog");
        SagaLog sagaLog = pool.connect(logId);
        try {
            sagaLog.truncate().join();
            CompletableFuture<SagaLogEntry> w1 = sagaLog.write(sagaLog.builder().startAction("e1", "n1"));
            CompletableFuture<SagaLogEntry> w2 = sagaLog.write(sagaLog.builder().startAction("e1", "n2"));
            CompletableFuture<SagaLogEntry> w3 = sagaLog.write(sagaLog.builder().endAction("e1", "n2", "{}"));
            w1.join();
            w2.join();
            w3.join();
            assertEquals(sagaLog.readIncompleteSagas().count(), 3);
            sagaLog.truncate().join();
        } finally {
            pool.remove(logId);
        }
    }

    @Test
    public void thatAcquireAndReleaseOwnershipWorks() {
        SagaLogId sagaLogId = pool.idFor("l1");
        SagaLog l1o1 = pool.acquire(new SagaLogOwner("o1"), sagaLogId);
        assertNotNull(l1o1);
        pool.release(sagaLogId);
        SagaLog l1o2 = pool.acquire(new SagaLogOwner("o2"), sagaLogId);
        assertTrue(l1o1 == l1o2);
        pool.release(new SagaLogOwner("o2"));
        SagaLog l1o3 = pool.acquire(new SagaLogOwner("o2"), sagaLogId);
        assertTrue(l1o1 == l1o3);
        pool.release(sagaLogId);
    }

    @Test
    public void thatInstanceLocalSagaLogOwnershipsWorks() {
        SagaLogId e1 = pool.idFor("e1");
        SagaLogId e2 = pool.idFor("e2");
        pool.connect(e1);
        pool.connect(e2);
        pool.remove(e1);
        pool.remove(e2);

        SagaLogId l1 = pool.idFor("l1");
        SagaLogId l2 = pool.idFor("l2");
        SagaLogId l3 = pool.idFor("l3");
        pool.acquire(new SagaLogOwner("o1"), l1);
        pool.acquire(new SagaLogOwner("o1"), l2);
        pool.acquire(new SagaLogOwner("o2"), l3);

        Set<SagaLogOwnership> sagaLogOwnerships = pool.instanceLocalSagaLogOwnerships();
        assertEquals(sagaLogOwnerships.size(), 3);

        pool.release(new SagaLogOwner("o1"));
        pool.release(new SagaLogOwner("o2"));

        Set<SagaLogOwnership> sagaLogOwnerships2 = pool.instanceLocalSagaLogOwnerships();
        assertEquals(sagaLogOwnerships2.size(), 0);

        Set<SagaLogId> logIds = pool.instanceLocalLogIds();
        assertEquals(logIds, Set.of(l1, l2, l3));
    }
}
