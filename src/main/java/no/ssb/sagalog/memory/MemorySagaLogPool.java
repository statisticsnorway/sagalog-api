package no.ssb.sagalog.memory;


import no.ssb.sagalog.AbstractSagaLogPool;
import no.ssb.sagalog.SagaLog;
import no.ssb.sagalog.SagaLogBusyException;
import no.ssb.sagalog.SagaLogId;

import java.util.Set;

public class MemorySagaLogPool extends AbstractSagaLogPool {

    public MemorySagaLogPool(String clusterInstanceId) {
        super(clusterInstanceId);
    }

    @Override
    public MemorySagaLogId idFor(String clusterInstanceId, String logName) {
        return new MemorySagaLogId(clusterInstanceId, logName);
    }

    @Override
    public Set<SagaLogId> clusterWideLogIds() {
        return instanceLocalLogIds();
    }

    @Override
    protected boolean deleteExternal(SagaLogId logId) {
        return true;
    }

    @Override
    protected SagaLog connectExternal(SagaLogId logId) throws SagaLogBusyException {
        return new MemorySagaLog(logId);
    }
}
