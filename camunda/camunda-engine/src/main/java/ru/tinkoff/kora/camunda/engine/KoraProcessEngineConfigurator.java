package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.camunda.engine.configurator.ProcessEngineConfigurator;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KoraProcessEngineConfigurator implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngineConfigurator.class);

    private final ProcessEngine processEngine;
    private final List<ProcessEngineConfigurator> camundaConfigurators;

    public KoraProcessEngineConfigurator(ProcessEngine processEngine,
                                         List<ProcessEngineConfigurator> camundaConfigurators) {
        this.processEngine = processEngine;
        this.camundaConfigurators = camundaConfigurators;
    }

    @Override
    public void init() {
        logger.debug("Camunda Engine configuring...");
        final long started = TimeUtils.started();

        var setups = camundaConfigurators.stream()
            .map(c -> CompletableFuture.runAsync(() -> {
                try {
                    c.setup(processEngine);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(setups).join();
        logger.info("Camunda Engine configured in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        // do nothing
    }
}