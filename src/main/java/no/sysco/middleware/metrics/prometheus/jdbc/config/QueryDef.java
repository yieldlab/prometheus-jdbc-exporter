package no.sysco.middleware.metrics.prometheus.jdbc.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.util.StdConverter;

/** Query definition to collect metrics from a database. */
@ConfigObject
@Value.Immutable
@JsonDeserialize(builder = ImmutableQueryDef.Builder.class)
public interface QueryDef {

    /** The metric name. */
    String name();

    /** The metric's help text. */
    Optional<String> help();

    /** Static labels to be attached to the metric. */
    Map<String, String> staticLabels();

    /** List of labels that have to match a column name. Column values must be strings. */
    List<String> labels();

    /** List of values that have to match a column name. Column values must be numbers. May not be empty. */
    List<String> values();

    /** SQL query to select rows that will represent a metric sample. */
    @JsonUnwrapped
    QueryString query();

    /** How long to cache this metric until the next refresh. */
    @JsonProperty("cache_seconds")
    @JsonDeserialize(converter = JacksonQueryDefCacheSecondsConverter.class)
    Optional<Duration> cacheDuration();

    @Value.Check
    default void validate() {
        if (values().isEmpty()) {
            throw new IllegalArgumentException("no values provided");
        }
    }
}

@ConfigObject
final class JacksonQueryDefCacheSecondsConverter extends StdConverter<Long, Optional<Duration>> {
    @Override
    public Optional<Duration> convert(Long value) {
        if (value == null) {
            return Optional.empty();
        }

        if (value <= 0) {
            throw new IllegalArgumentException("must be positive: " + value);
        }

        return Optional.of(Duration.ofSeconds(value));
    }
}
