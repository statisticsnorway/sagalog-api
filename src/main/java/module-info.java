import no.ssb.sagalog.SagaLogInitializer;
import no.ssb.sagalog.memory.MemorySagaLogInitializer;

module no.ssb.sagalog {
    requires java.base;

    exports no.ssb.sagalog;

    provides SagaLogInitializer with MemorySagaLogInitializer;
}
