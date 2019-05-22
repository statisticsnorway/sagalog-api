package no.ssb.sagalog.memory;


import no.ssb.sagalog.SagaLog;
import no.ssb.sagalog.SagaLogPool;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

class MemorySagaLogPool implements SagaLogPool {

    private final Map<String, MemorySagaLog> sagaLogByLogId = new ConcurrentHashMap<>();
    private final Set<String> occupied = new ConcurrentSkipListSet<>();

    MemorySagaLogPool() {
    }

    @Override
    public SagaLog connect(String logId) {
        if (!occupied.add(logId)) {
            throw new RuntimeException(String.format("saga-log with logId \"%s\" already connected."));
        }
        MemorySagaLog sagaLog = sagaLogByLogId.computeIfAbsent(logId, lid -> new MemorySagaLog());
        return sagaLog;
    }

    @Override
    public void release(String logId) {
        occupied.remove(logId);
    }

    @Override
    public void shutdown() {
        sagaLogByLogId.clear();
        occupied.clear();
    }
}
