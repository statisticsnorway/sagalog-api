package no.ssb.sagalog;

import java.util.Set;

public interface SagaLogPool {

    /**
     * Generate and register a instance-local SagaLogId for the given internalId. The returned values are considered
     * instance-local ids and will be included by the instanceLocalLogIds method.
     *
     * @param internalId
     * @return
     */
    SagaLogId idFor(String internalId);

    /**
     * List all logIds cluster-wide that is relevant to a recovery procedure and available from this instance. This will
     * typically include all saga-logs in the cluster that might have incomplete entries. Saga-logs with a saga-log id
     * from this list that is not also in the instance-local id list should always be removed from the pool immediately
     * after recovery or other administrative use has completed, otherwise the saga-log could be blocked from being
     * used by other application cluster instances.
     *
     * @return the list of cluster-wide log ids.
     */
    Set<SagaLogId> clusterWideLogIds();

    /**
     * List all logIds in the cluster owned by this instance that is relevant to a recovery procedure. This will include
     * all saga-logs owned by this instance that might have incomplete entries. These entries are typically not removed
     * from this pool after being used in a recovery or administrative procedure.
     *
     * @return
     */
    Set<SagaLogId> instanceLocalLogIds();

    /**
     * List all saga-log ownerships currently registered with the pool. This is an unreliable snapshot of ownerships
     * typically only used for monitoring or health purposes.
     *
     * @return
     */
    Set<SagaLogOwnership> instanceLocalSagaLogOwnerships();

    /**
     * Connect to the saga-log with the given logId and add this saga-log to the pool. This will typically open a
     * reusable connection or io-channel backing the saga-log. If the saga-log is already present in the pool, that log
     * is returned immediately without registering or affecting ownership.
     *
     * @param logId
     * @return
     */
    SagaLog connect(SagaLogId logId);

    /**
     * Close and then remove the saga-log with the given logId from the pool. This will cause subsequent calls to acquire
     * on the same logId to re-connect to the log.
     *
     * @param logId
     */
    void remove(SagaLogId logId);

    /**
     * Acquire the saga-log with the given logId, obtaining  exclusive access and ownership to this log across calls to
     * this method in this saga-log pool. If the log is not already present in the pool, this method connects the
     * saga-log first and then add it to the pool.
     *
     * @param owner the owner that wants to acquire the saga-log.
     * @param logId the id of the saga-log to acquire.
     * @return the sagalog with the given logI.
     * @throws SagaLogAlreadyAquiredByOtherOwnerException if the saga-log with the given logId was already acquired without yet
     *                                                    being released.
     */
    SagaLog acquire(SagaLogOwner owner, SagaLogId logId) throws SagaLogAlreadyAquiredByOtherOwnerException;

    /**
     * Release ownership of the saga-logs owned by the given owner. This will ensure that the logs are available in this
     * pool after this method returns.
     *
     * @param owner
     */
    void release(SagaLogOwner owner);

    /**
     * Release ownership of the saga-log with the given logId. This will ensure that the log is available in this pool
     * after this method returns.
     *
     * @param logId
     */
    void release(SagaLogId logId);

    /**
     * Shutdown the pool closing and removing all logs from the pool. Shutting down an alread
     */
    void shutdown();
}
