package no.ssb.sagalog;

public class SagaLogAlreadyAquiredByOtherOwnerException extends RuntimeException {
    public SagaLogAlreadyAquiredByOtherOwnerException() {
        super();
    }

    public SagaLogAlreadyAquiredByOtherOwnerException(String message) {
        super(message);
    }
}
