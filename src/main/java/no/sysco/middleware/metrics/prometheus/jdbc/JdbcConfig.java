package no.sysco.middleware.metrics.prometheus.jdbc;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.immutables.value.Value;

import io.prometheus.client.Collector;
import no.sysco.middleware.metrics.prometheus.jdbc.config.Config;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ConnectionDef;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableConfig;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableJob;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableQueryDef;
import no.sysco.middleware.metrics.prometheus.jdbc.config.Job;
import no.sysco.middleware.metrics.prometheus.jdbc.config.QueryDef;

class JdbcConfig {
    private static final Logger LOGGER = Logger.getLogger(JdbcConfig.class.getName());

    private final String prefix;
    private final Config config;
    private final ConnectionProvider connProvider;
    private final TemplateRenderer renderer;
    private final Clock clock;

    private Map<ImmutableCacheKey, SampleResult> sampleCache = new ConcurrentHashMap<>();

    JdbcConfig(String prefix, Config config, ConnectionProvider connProvider, TemplateRenderer renderer, Clock clock) {
        this.prefix = requireNonNull(prefix);
        this.config = ImmutableConfig.copyOf(config);
        this.connProvider = requireNonNull(connProvider);
        this.renderer = requireNonNull(renderer);
        this.clock = requireNonNull(clock);
    }

    Stream<Collector.MetricFamilySamples> runJobs() {
        return config.jobs().parallelStream().flatMap(job -> runJob(prefix, job).samples.stream());
    }

    private SampleResult runJob(String prefix, Job job) {
        final var startNanos = System.nanoTime();
        LOGGER.log(Level.INFO, "Running JDBC job: " + job.name());

        final var result = new SampleResult(clock);

        try (final var sampleStream = streamJobSamples(job)) {
            result.samples = sampleStream.flatMap(samples -> samples.samples.stream()).collect(toList());
        } catch (Exception e) {
            result.error = Optional.of(e);
            LOGGER.log(Level.WARNING, "Exception during execution of job " + job.name() + ": ", e);
        }

        result.scrapeDuration = Duration.ofNanos(System.nanoTime() - startNanos);

        result.samples.add(
            new Collector.MetricFamilySamples(
                prefix + "_scrape_duration_seconds",
                Collector.Type.GAUGE,
                "Time this JDBC scrape took, in seconds.",
                List.of(
                    new Collector.MetricFamilySamples.Sample(
                        prefix + "_scrape_duration_seconds",
                        List.of(),
                        List.of(),
                        result.scrapeDuration.toNanos() / (double) TimeUnit.SECONDS.toNanos(1) //
                    ) //
                ) //
            ) //
        );

        result.samples.add(
            new Collector.MetricFamilySamples(
                prefix + "_scrape_error",
                Collector.Type.GAUGE,
                "Non-zero if this scrape failed.",
                List.of(
                    new Collector.MetricFamilySamples.Sample(
                        prefix + "_scrape_error",
                        List.of(),
                        List.of(),
                        result.error.isPresent() ? 1 : 0 //
                    ) //
                ) //
            ) //
        );

        return result;
    }

    private Connection openConnection(ConnectionDef connDef) throws ClassNotFoundException, SQLException {
        final var url = renderer.render(connDef.url());
        LOGGER.info(String.format("JDBC Connection URL: %s", url));

        if (connDef.driverClassName().isPresent()) {
            Class.forName(renderer.render(connDef.driverClassName().get()));
        }

        final var props = new HashMap<String, String>();
        connDef.username().map(renderer::render).ifPresent(u -> props.put("user", u));
        connDef.password().map(renderer::render).ifPresent(p -> props.put("password", p));

        return connProvider.getConnection(url, props);
    }

    private static void closeConnection(final Connection conn) {
        try {
            conn.close();
            LOGGER.log(Level.FINE, "Closed connection " + conn);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing connection.", e);
        }
    }

    private Stream<SampleResult> streamJobSamples(Job job) {
        return job.connections().parallelStream().flatMap(connDef -> {
            final Connection conn;
            try {
                conn = openConnection(connDef);
            } catch (SQLException | ClassNotFoundException | RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error connecting to database for job " + job.name(), e);
                return Stream.empty();
            }

            return job.queries()
                .parallelStream()
                .onClose(() -> closeConnection(conn))
                .map(queryDef -> evaluateQuery(job, queryDef, conn));
        });
    }

    private SampleResult evaluateQuery(Job job, QueryDef queryDef, Connection conn) {
        final Supplier<SampleResult> queryRunner = () -> runQuery(queryDef, conn);

        return queryDef.cacheDuration()
            .map(cacheDuration -> sampleCache.compute(CacheKey.of(job, queryDef), (key, value) -> {
                if (value != null && value.sampleTime.plus(cacheDuration).isAfter(clock.instant())) {
                    return value;
                }

                return queryRunner.get();
            }))
            .orElseGet(queryRunner);
    }

    private SampleResult runQuery(QueryDef queryDef, Connection conn) {
        final var queryString = renderer.render(queryDef.query().resolve(config.queries()::get));
        final var result = new SampleResult(clock);
        final var start = System.nanoTime();
        try (final var stmt = conn.prepareStatement(queryString); final var rs = stmt.executeQuery()) {
            result.samples = collectSamples(queryDef, rs);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, String.format("Error executing query: %s", queryString), e);
            result.error = Optional.of(e);
        }
        result.scrapeDuration = Duration.ofNanos(System.nanoTime() - start);
        return result;
    }

    private List<Collector.MetricFamilySamples> collectSamples(QueryDef queryDef, ResultSet rs) throws SQLException {
        final var metricName = String.format("%s_%s", prefix, queryDef.name());
        List<Collector.MetricFamilySamples.Sample> samples = new ArrayList<>();

        // The configuration model is wrong as it's not possible to have more than one sample with
        // the same set of labels, so we're silently ignoring all but the first label value for now.
        final var valueColumn = queryDef.values().iterator().next();

        final var labelNames = new ArrayList<String>();
        final var staticLabelValues = new ArrayList<String>();
        final var resultLabelNames = new ArrayList<String>();

        queryDef.staticLabels().forEach((labelName, labelValue) -> {
            labelNames.add(labelName);
            staticLabelValues.add(labelValue);
        });
        queryDef.labels().forEach(label -> {
            resultLabelNames.add(label);
            labelNames.add(label);
        });

        while (rs.next()) {
            final var labelValues = new ArrayList<String>(staticLabelValues.size() + resultLabelNames.size());
            labelValues.addAll(staticLabelValues);
            resultLabelNames.forEach(labelName -> {
                var labelValue = "";
                try {
                    labelValue = rs.getString(labelName);
                } catch (SQLException e) {
                    LOGGER.log(
                        Level.WARNING,
                        String.format("Label %s not found as part of the query result set.", labelName));
                }
                labelValues.add(labelValue);
            });

            try {
                final var value = rs.getDouble(valueColumn);
                samples.add(new Collector.MetricFamilySamples.Sample(metricName, labelNames, labelValues, value));
            } catch (SQLException e) {
                LOGGER.log(
                    Level.SEVERE,
                    String.format("Sample value %s not found as part of the query result set.", valueColumn),
                    e);
            }
        }

        return List.of(
            new Collector.MetricFamilySamples(
                metricName,
                Collector.Type.GAUGE,
                queryDef.help().orElse("column " + valueColumn),
                samples));
    }
}

class SampleResult {
    final Instant sampleTime;
    Duration scrapeDuration = Duration.ZERO;
    Optional<Throwable> error = Optional.empty();
    List<Collector.MetricFamilySamples> samples = new ArrayList<>();

    SampleResult(Clock clock) {
        this.sampleTime = clock.instant();
    }
}

@Value.Immutable(builder = false, prehash = true)
abstract class CacheKey {
    @Value.Parameter
    abstract Job job();

    @Value.Parameter
    abstract QueryDef queryDef();

    static ImmutableCacheKey of(Job job, QueryDef queryDef) {
        return ImmutableCacheKey.of(ImmutableJob.copyOf(job), ImmutableQueryDef.copyOf(queryDef));
    }
}
