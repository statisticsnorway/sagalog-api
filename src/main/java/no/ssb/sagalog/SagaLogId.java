package no.ssb.sagalog;

import java.util.Objects;

public class SagaLogId {
    private final String internalId;

    public SagaLogId(String internalId) {
        this.internalId = internalId;
    }

    public String getInternalId() {
        return internalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SagaLogId sagaLogId = (SagaLogId) o;
        return internalId.equals(sagaLogId.internalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(internalId);
    }

    @Override
    public String toString() {
        return "SagaLogId{" +
                "internalId='" + internalId + '\'' +
                '}';
    }
}
