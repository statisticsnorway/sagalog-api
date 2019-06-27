package no.ssb.sagalog;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;

public abstract class AbstractSagaLogPool implements SagaLogPool {

    private final String clusterInstanceId;
    private final Set<SagaLogId> instanceLocalSagaLogIds = new CopyOnWriteArraySet();
    private final Map<SagaLogId, SagaLog> sagaLogByLogId = new ConcurrentHashMap<>();
    private final Map<SagaLogId, SagaLogOwnership> ownershipByLogId = new ConcurrentHashMap<>();
    private final Map<SagaLogId, Semaphore> exclusiveLockByLogId = new ConcurrentHashMap<>();
    private final BlockingDeque<SagaLogId> availableInstanceLocalIds = new LinkedBlockingDeque<>();

    protected final Map<String, Pattern> idPatternByGroup = new ConcurrentHashMap<>();

    protected AbstractSagaLogPool(String clusterInstanceId) {
        this.clusterInstanceId = clusterInstanceId;
    }

    protected abstract SagaLog connectExternal(SagaLogId logId) throws SagaLogBusyException;

    @Override
    public boolean doesSagaLogIdMatchPattern(String group, SagaLogId logId) {
        return ofNullable(idPatternByGroup.get(group))
                .map(pattern -> pattern.matcher(logId.getLogName()).matches())
                .orElse(Boolean.FALSE);
    }

    @Override
    public String getLocalClusterInstanceId() {
        return clusterInstanceId;
    }

    @Override
    public SagaLog connect(SagaLogId logId) throws SagaLogBusyException {
        return sagaLogByLogId.computeIfAbsent(logId, lId -> connectExternal(logId));
    }

    @Override
    public SagaLogId registerInstanceLocalIdFor(String logName) {
        SagaLogId sagaLogId = idFor(clusterInstanceId, logName);
        if (instanceLocalSagaLogIds.add(sagaLogId)) {
            availableInstanceLocalIds.add(sagaLogId);
        }
        return sagaLogId;
    }

    @Override
    public void registerIdPattern(String group, Pattern deadLetterInternalIdRegex) {
        idPatternByGroup.put(group, deadLetterInternalIdRegex);
    }

    @Override
    public Set<SagaLogId> instanceLocalLogIds() {
        return Collections.unmodifiableSet(instanceLocalSagaLogIds);
    }

    @Override
    public Set<SagaLogOwnership> instanceLocalSagaLogOwnerships() {
        return new LinkedHashSet<>(ownershipByLogId.values());
    }

    @Override
    public void remove(SagaLogId logId) {
        SagaLog sagaLog = sagaLogByLogId.remove(logId);
        if (sagaLog != null) {
            try {
                sagaLog.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public SagaLog tryAcquire(SagaLogOwner owner) throws SagaLogBusyException, SagaLogAlreadyAquiredByOtherOwnerException {
        SagaLogId sagaLogId = availableInstanceLocalIds.poll();
        if (sagaLogId == null) {
            return null; // no available saga-logs right now
        }
        boolean lockAcquired = false;
        try {
            SagaLog sagaLog = tryTakeOwnership(owner, sagaLogId);
            if (sagaLog == null) {
                throw new IllegalStateException("instance-local saga-log-id was available, but lock was unavailable.");
            }
            lockAcquired = true;
            return sagaLog;
        } finally {
            if (!lockAcquired) {
                availableInstanceLocalIds.addFirst(sagaLogId);
            }
        }
    }

    @Override
    public SagaLog tryAcquire(SagaLogOwner owner, int timeout, TimeUnit unit) throws InterruptedException, SagaLogBusyException, SagaLogAlreadyAquiredByOtherOwnerException {
        SagaLogId sagaLogId = availableInstanceLocalIds.poll(timeout, unit);
        if (sagaLogId == null) {
            return null; // timeout
        }
        boolean lockAcquired = false;
        try {
            SagaLog sagaLog = tryTakeOwnership(owner, sagaLogId, timeout, unit);
            if (sagaLog == null) {
                throw new IllegalStateException("instance-local saga-log-id was available, but lock was unavailable.");
            }
            lockAcquired = true;
            return sagaLog;
        } finally {
            if (!lockAcquired) {
                availableInstanceLocalIds.addFirst(sagaLogId);
            }
        }
    }

    @Override
    public void release(SagaLogId logId) {
        try {
            releaseOwnership(logId);
        } finally {
            if (instanceLocalSagaLogIds.contains(logId)) {
                availableInstanceLocalIds.addLast(logId);
            }
        }
    }

    private SagaLog doAssignPoolLocalOwnership(SagaLogOwner owner, SagaLogId logId) {
        SagaLog sagaLog = connect(logId);
        SagaLogOwnership ownership = ownershipByLogId.computeIfAbsent(logId, id -> new SagaLogOwnership(owner, id, ZonedDateTime.now()));
        if (!owner.equals(ownership.getOwner())) {
            throw new SagaLogAlreadyAquiredByOtherOwnerException(String.format("SagaLogOwner %s was unable to acquire saga-log with id %s. Already owned by %s.", owner.getOwnerId(), logId, ownership.getOwner().getOwnerId()));
        }
        return sagaLog;
    }

    @Override
    public SagaLog tryTakeOwnership(SagaLogOwner owner, SagaLogId logId) throws SagaLogBusyException, SagaLogAlreadyAquiredByOtherOwnerException {
        Semaphore semaphore = exclusiveLockByLogId.computeIfAbsent(logId, id -> new Semaphore(1));
        if (!semaphore.tryAcquire()) {
            return null; // lock unavailable
        }
        SagaLog sagaLog = null;
        try {
            if (semaphore.availablePermits() > 0) {
                throw new IllegalStateException("More than one permit available. Most likely the saga-log was released when no ownership or lock/permit is held.");
            }
            return (sagaLog = doAssignPoolLocalOwnership(owner, logId));
        } finally {
            if (sagaLog == null) {
                exclusiveLockByLogId.get(logId).release();
            }
        }
    }

    @Override
    public SagaLog tryTakeOwnership(SagaLogOwner owner, SagaLogId logId, long timeout, TimeUnit unit) throws InterruptedException, SagaLogBusyException, SagaLogAlreadyAquiredByOtherOwnerException {
        Semaphore semaphore = exclusiveLockByLogId.computeIfAbsent(logId, id -> new Semaphore(1));
        if (!semaphore.tryAcquire(timeout, unit)) {
            return null; // timeout
        }
        SagaLog sagaLog = null;
        try {
            if (semaphore.availablePermits() > 0) {
                throw new IllegalStateException("Acquired permit, but there are still permits available. Someone has released this saga-log-id when no ownership or lock/permit is held.");
            }
            return (sagaLog = doAssignPoolLocalOwnership(owner, logId));
        } finally {
            if (sagaLog == null) {
                exclusiveLockByLogId.get(logId).release();
            }
        }
    }

    @Override
    public void releaseOwnership(SagaLogId logId) {
        try {
            ownershipByLogId.remove(logId);
        } finally {
            Semaphore semaphore = exclusiveLockByLogId.get(logId);
            if (semaphore.availablePermits() > 0) {
                throw new IllegalStateException("Lock already released");
            }
            semaphore.release();
        }
    }

    @Override
    public boolean delete(SagaLogId logId) {
        if (sagaLogByLogId.containsKey(logId)) {
            throw new RuntimeException("Saga-log with id %s must be removed from pool before it can be deleted.");
        }
        return deleteExternal(logId);
    }

    protected abstract boolean deleteExternal(SagaLogId logId);

    @Override
    public void shutdown() {
        for (SagaLog sagaLog : sagaLogByLogId.values()) {
            try {
                sagaLog.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        sagaLogByLogId.clear();
        ownershipByLogId.clear();
    }
}
