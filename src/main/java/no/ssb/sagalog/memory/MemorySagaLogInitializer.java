package no.ssb.sagalog.memory;

import no.ssb.sagalog.SagaLogInitializer;
import no.ssb.sagalog.SagaLogPool;

import java.util.Collections;
import java.util.Map;

public class MemorySagaLogInitializer implements SagaLogInitializer {

    public MemorySagaLogInitializer() {
    }

    public SagaLogPool initialize(Map<String, String> configuration) {
        return new MemorySagaLogPool();
    }

    public Map<String, String> configurationOptionsAndDefaults() {
        return Collections.emptyMap();
    }
}
