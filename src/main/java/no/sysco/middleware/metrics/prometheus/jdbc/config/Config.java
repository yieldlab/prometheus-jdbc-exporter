package no.sysco.middleware.metrics.prometheus.jdbc.config;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.immutables.value.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/** Configuration for the Prometheus JDBC Exporter. */
@ImmutableConfigObject
@JacksonConfigObject
@Value.Immutable
@JsonDeserialize(builder = ImmutableConfig.Builder.class)
public interface Config {

    static Config parseYaml(InputStream data) throws IOException {
        final var mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new Jdk8Module());
        try {
            return mapper.readValue(data, Config.class);
        } catch (InvalidDefinitionException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (JsonMappingException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /** List of jobs that will be executed by the collector. */
    Set<Job> jobs();

    /** Common queries that can be referenced from different {@linkplain Job jobs}. */
    Map<String, String> queries();

    @Value.Check
    default void validate() {
        if (jobs().isEmpty()) {
            throw new IllegalArgumentException("no jobs provided");
        }

        final var invalidQueryRefs = jobs().stream()
            .map(Job::queries)
            .flatMap(Collection::stream)
            .map(QueryDef::query)
            .filter(QueryString.Ref.class::isInstance)
            .map(QueryString.Ref.class::cast)
            .map(QueryString.Ref::queryRef)
            .filter(queryRef -> !queries().containsKey(queryRef))
            .collect(joining(", "));

        if (!invalidQueryRefs.isEmpty()) {
            throw new IllegalArgumentException("invalid query refs: " + invalidQueryRefs);
        }
    }
}
