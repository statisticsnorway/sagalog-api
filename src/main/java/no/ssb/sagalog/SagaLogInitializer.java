package no.ssb.sagalog;

import java.util.Map;

public interface SagaLogInitializer {

    SagaLogPool initialize(Map<String, String> configuration);

    Map<String, String> configurationOptionsAndDefaults();
}
