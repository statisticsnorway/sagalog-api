package no.ssb.sagalog;

public interface SagaLogPool {

    SagaLog connect(String logId);

    void release(String logId);

    void shutdown();
}
