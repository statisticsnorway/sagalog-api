package no.ssb.sagalog.memory;


import no.ssb.sagalog.SagaLog;
import no.ssb.sagalog.SagaLogAlreadyAquiredByOtherOwnerException;
import no.ssb.sagalog.SagaLogId;
import no.ssb.sagalog.SagaLogOwner;
import no.ssb.sagalog.SagaLogOwnership;
import no.ssb.sagalog.SagaLogPool;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class MemorySagaLogPool implements SagaLogPool {

    private final Map<SagaLogId, MemorySagaLog> sagaLogByLogId = new ConcurrentHashMap<>();
    private final Map<SagaLogId, SagaLogOwnership> ownershipByLogId = new ConcurrentHashMap<>();

    MemorySagaLogPool() {
    }

    @Override
    public SagaLogId idFor(String internalId) {
        return new SagaLogId(internalId);
    }

    @Override
    public Set<SagaLogId> clusterWideLogIds() {
        return instanceLocalLogIds();
    }

    @Override
    public Set<SagaLogId> instanceLocalLogIds() {
        return new LinkedHashSet<>(sagaLogByLogId.keySet());
    }

    @Override
    public Set<SagaLogOwnership> instanceLocalSagaLogOwnerships() {
        return new LinkedHashSet<>(ownershipByLogId.values());
    }

    @Override
    public SagaLog connect(SagaLogId logId) {
        return sagaLogByLogId.computeIfAbsent(logId, lid -> new MemorySagaLog(logId));
    }

    @Override
    public void remove(SagaLogId logId) {
        release(logId);
        sagaLogByLogId.remove(logId);
    }

    @Override
    public SagaLog acquire(SagaLogOwner owner, SagaLogId logId) throws SagaLogAlreadyAquiredByOtherOwnerException {
        SagaLogOwnership ownership = ownershipByLogId.computeIfAbsent(logId, id -> new SagaLogOwnership(owner, id, ZonedDateTime.now()));
        if (!owner.equals(ownership.getOwner())) {
            throw new SagaLogAlreadyAquiredByOtherOwnerException(String.format("SagaLogOwner %s was unable to acquire saga-log with id %s. Already owned by %s.", owner.getOwnerId(), logId, ownership.getOwner().getOwnerId()));
        }
        return connect(logId);
    }

    @Override
    public void release(SagaLogOwner owner) {
        ownershipByLogId.values().removeIf(ownership -> owner.equals(ownership.getOwner()));
    }

    @Override
    public void release(SagaLogId logId) {
        ownershipByLogId.remove(logId);
    }

    @Override
    public void shutdown() {
        sagaLogByLogId.clear();
        ownershipByLogId.clear();
    }
}
