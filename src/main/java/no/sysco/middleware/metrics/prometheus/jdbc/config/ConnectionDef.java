package no.sysco.middleware.metrics.prometheus.jdbc.config;

import java.util.Optional;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Connection details to connect to a database instance and execute queries. */
@ConfigObject
@Value.Immutable
@Value.Style(redactedMask = "***")
@JsonDeserialize(builder = ImmutableConnectionDef.Builder.class)
public interface ConnectionDef {

    /** JDBC URL to connect to database instances. */
    String url();

    /** Database user's name. */
    Optional<String> username();

    /** Database user's password. */
    @Value.Redacted
    Optional<String> password();

    /** Fully qualified name of the JDBC driver class. */
    Optional<String> driverClassName();
}
