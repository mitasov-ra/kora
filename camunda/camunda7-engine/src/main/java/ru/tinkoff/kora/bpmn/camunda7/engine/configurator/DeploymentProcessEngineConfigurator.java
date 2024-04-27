package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.bpmn.camunda7.engine.Camunda7EngineConfig;
import ru.tinkoff.kora.bpmn.camunda7.engine.util.ClasspathResourceUtils;
import ru.tinkoff.kora.bpmn.camunda7.engine.util.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DeploymentProcessEngineConfigurator implements ProcessEngineConfigurator {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentProcessEngineConfigurator.class);

    private final Camunda7EngineConfig engineConfig;

    public DeploymentProcessEngineConfigurator(Camunda7EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public void setup(ProcessEngine engine) {
        try {
            var deployment = engineConfig.deployment();
            if (deployment != null && !deployment.resources().isEmpty()) {
                deployProcessModels(deployment, engine.getRepositoryService());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deployProcessModels(Camunda7EngineConfig.DeploymentConfig deploymentConfig, RepositoryService repositoryService) throws IOException {
        final List<String> locations = deploymentConfig.resources();
        logger.debug("Camunda7 Configurator deploying {} resources...", locations);
        final long started = System.nanoTime();

        final Set<String> normalizedLocations = locations.stream()
            .map(location -> {
                if (location.startsWith("classpath:")) {
                    return location;
                } else {
                    logger.warn("Only locations with `classpath:` prefix are supported, skipping unsupported resource location: {}", location);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        final List<Resource> resources = ClasspathResourceUtils.findResources(normalizedLocations);
        if (resources.isEmpty()) {
            logger.debug("Camunda7 Configurator found 0 resources");
        } else {
            DeploymentBuilder builder = repositoryService.createDeployment()
                .name(deploymentConfig.name())
                .enableDuplicateFiltering(deploymentConfig.deployChangedOnly());

            if (deploymentConfig.tenantId() != null) {
                builder = builder.tenantId(deploymentConfig.tenantId());
            }

            boolean deploy = false;
            for (var resource : resources) {
                builder.addInputStream(resource.name(), resource.asInputStream());
                deploy = true;
            }

            if (deploy) {
                builder.deploy();
                logger.info("Camunda7 Configurator deployed {} resources in {}", resources, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            }
        }
    }
}