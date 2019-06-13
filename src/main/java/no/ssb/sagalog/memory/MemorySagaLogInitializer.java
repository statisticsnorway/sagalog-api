package no.ssb.sagalog.memory;

import no.ssb.sagalog.SagaLogInitializer;

import java.util.Collections;
import java.util.Map;

public class MemorySagaLogInitializer implements SagaLogInitializer {

    public MemorySagaLogInitializer() {
    }

    public MemorySagaLogPool initialize(Map<String, String> configuration) {
        return new MemorySagaLogPool();
    }

    public Map<String, String> configurationOptionsAndDefaults() {
        return Collections.emptyMap();
    }
}
