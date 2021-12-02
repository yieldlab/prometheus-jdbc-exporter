package no.sysco.middleware.metrics.prometheus.jdbc.config;

import java.util.Set;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** A job that will be executed by the collector. */
@ImmutableConfigObject
@JacksonConfigObject
@Value.Immutable
@JsonDeserialize(builder = ImmutableJob.Builder.class)
public interface Job {

    /** Name of the job. */
    String name();

    /** List of connection details. May not be empty. */
    Set<ConnectionDef> connections();

    /** List of queries to execute. May not be empty. */
    Set<QueryDef> queries();

    @Value.Check
    default void validate() {
        if (connections().isEmpty()) {
            throw new IllegalArgumentException("no connections provided");
        }
        if (queries().isEmpty()) {
            throw new IllegalArgumentException("no queries provided");
        }
    }
}
