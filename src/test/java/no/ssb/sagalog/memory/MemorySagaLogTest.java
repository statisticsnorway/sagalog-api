package no.ssb.sagalog.memory;

import no.ssb.sagalog.SagaLogEntry;
import no.ssb.sagalog.SagaLogEntryBuilder;
import org.testng.annotations.Test;

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public class MemorySagaLogTest {

    @Test
    public void thatWriteAndReadEntriesWorks() {
        MemorySagaLog sagaLog = new MemorySagaLog();

        Deque<SagaLogEntry> expectedEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());

        assertEquals(sagaLog.readEntries(expectedEntries.getFirst().getExecutionId()).collect(Collectors.toList()), expectedEntries);
    }

    @Test
    public void thatTruncateWithReadIncompleteWorks() {
        MemorySagaLog sagaLog = new MemorySagaLog();

        Deque<SagaLogEntry> initialEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());
        sagaLog.truncate(initialEntries.getLast().getId());

        Deque<SagaLogEntry> expectedEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());

        List<SagaLogEntry> actualEntries = sagaLog.readIncompleteSagas().collect(Collectors.toList());
        assertEquals(actualEntries, expectedEntries);
    }

    @Test
    public void thatNoTruncateWithReadIncompleteWorks() {
        MemorySagaLog sagaLog = new MemorySagaLog();

        Deque<SagaLogEntry> firstEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());
        Deque<SagaLogEntry> secondEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());
        Deque<SagaLogEntry> expectedEntries = new LinkedList<>();
        expectedEntries.addAll(firstEntries);
        expectedEntries.addAll(secondEntries);

        List<SagaLogEntry> actualEntries = sagaLog.readIncompleteSagas().collect(Collectors.toList());
        assertEquals(actualEntries, expectedEntries);
    }

    @Test
    public void thatSnapshotOfSagaLogEntriesByNodeIdWorks() {
        MemorySagaLog sagaLog = new MemorySagaLog();

        Deque<SagaLogEntry> firstEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());
        Deque<SagaLogEntry> secondEntries = writeSuccessfulVanillaSagaExecutionEntries(sagaLog, UUID.randomUUID().toString());
        Deque<SagaLogEntry> expectedEntries = new LinkedList<>();
        expectedEntries.addAll(firstEntries);
        expectedEntries.addAll(secondEntries);

        Map<String, List<SagaLogEntry>> snapshotFirst = sagaLog.getSnapshotOfSagaLogEntriesByNodeId(firstEntries.getFirst().getExecutionId());
        Set<SagaLogEntry> firstFlattenedSnapshot = new LinkedHashSet<>();
        for (List<SagaLogEntry> collection : snapshotFirst.values()) {
            firstFlattenedSnapshot.addAll(collection);
        }
        Map<String, List<SagaLogEntry>> snapshotSecond = sagaLog.getSnapshotOfSagaLogEntriesByNodeId(secondEntries.getFirst().getExecutionId());
        Set<SagaLogEntry> secondFlattenedSnapshot = new LinkedHashSet<>();
        for (List<SagaLogEntry> collection : snapshotSecond.values()) {
            secondFlattenedSnapshot.addAll(collection);
        }

        assertEquals(firstFlattenedSnapshot, Set.copyOf(firstEntries));
        assertEquals(secondFlattenedSnapshot, Set.copyOf(secondEntries));
    }

    private Deque<SagaLogEntry> writeSuccessfulVanillaSagaExecutionEntries(MemorySagaLog sagaLog, String executionId) {
        Deque<SagaLogEntryBuilder> entryBuilders = new LinkedList<>();
        entryBuilders.add(sagaLog.builder().startSaga(executionId, "Vanilla-Saga", "{}"));
        entryBuilders.add(sagaLog.builder().startAction(executionId, "action1"));
        entryBuilders.add(sagaLog.builder().startAction(executionId, "action2"));
        entryBuilders.add(sagaLog.builder().endAction(executionId, "action1", "{}"));
        entryBuilders.add(sagaLog.builder().endAction(executionId, "action2", "{}"));
        entryBuilders.add(sagaLog.builder().endSaga(executionId));

        Deque<SagaLogEntry> entries = new LinkedList<>();
        for (SagaLogEntryBuilder builder : entryBuilders) {
            CompletableFuture<SagaLogEntry> entryFuture = sagaLog.write(builder);
            entries.add(entryFuture.join());
        }
        return entries;
    }
}
