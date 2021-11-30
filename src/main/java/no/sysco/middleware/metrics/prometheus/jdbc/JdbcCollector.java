package no.sysco.middleware.metrics.prometheus.jdbc;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import no.sysco.middleware.metrics.prometheus.jdbc.config.Config;

/**
 * Prometheus JDBC Collector
 */
public class JdbcCollector extends Collector implements Collector.Describable {

    private static final Logger LOGGER = Logger.getLogger(JdbcCollector.class.getName());

    private final String metricPrefix;
    private final Path configSource;

    private volatile Collection<JdbcConfig> configs = List.of();
    private volatile Instant lastUpdate = Instant.EPOCH;

    private final Counter configReloadSuccess;
    private final Counter configReloadFailure;

    private final Clock clock = Clock.systemUTC();

    JdbcCollector(String metricPrefix, Path configSource) throws IOException {
        this.configSource = requireNonNull(configSource);
        this.metricPrefix = requireNonNull(metricPrefix);

        this.configReloadSuccess = Counter.build()
                .name(metricPrefix + "_config_reload_success_total")
                .help("Number of times configuration have successfully been reloaded.")
                .register();

        this.configReloadFailure = Counter.build()
                .name(metricPrefix + "_config_reload_failure_total")
                .help("Number of times configuration have failed to be reloaded.")
                .register();

        final var lastUpdate = Files.getLastModifiedTime(configSource).toInstant();
        loadConfig();
        this.lastUpdate = lastUpdate;
    }

    private void loadConfig() throws IOException {
        final var configs = new ArrayList<JdbcConfig>();
        for (final var it = Files.walk(configSource).filter(Files::isRegularFile).iterator(); it.hasNext();) {
            final var file = it.next();
            try (final var configData = Files.newInputStream(file)) {
                configs.add(
                    new JdbcConfig(
                        metricPrefix,
                        Config.parseYaml(configData),
                        ConnectionProvider.DRIVER_MANAGER,
                        clock));
            }
        }

        if (configs.isEmpty()) {
            throw new IllegalArgumentException("No configuration in " + configSource);
        }

        this.configs = List.copyOf(configs);
    }

    @Override
    public List<MetricFamilySamples> describe() {
        List<MetricFamilySamples> sampleFamilies = new ArrayList<>();
        sampleFamilies.add(
            new MetricFamilySamples(
                metricPrefix + "_scrape_duration_seconds",
                Type.GAUGE,
                "Time this JDBC scrape took, in seconds.",
                new ArrayList<>()));
        sampleFamilies.add(
            new MetricFamilySamples(
                metricPrefix + "_scrape_error",
                Type.GAUGE,
                "Non-zero if this scrape failed.",
                new ArrayList<>()));
        return sampleFamilies;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        reloadConfigIfOutdated();
        return configs.parallelStream().flatMap(JdbcConfig::runJobs).collect(toList());
    }

    void reloadConfigIfOutdated() {
        try {
            final var lastUpdate = Files.getLastModifiedTime(configSource).toInstant();
            if (this.lastUpdate.equals(lastUpdate)) {
                return;
            }

            LOGGER.fine("Configuration changed, reloading...");
            loadConfig();
            this.lastUpdate = lastUpdate;
            configReloadSuccess.inc();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Configuration reload failed: " + e.getMessage(), e);
            configReloadFailure.inc();
        }
    }
}
