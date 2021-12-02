package no.sysco.middleware.metrics.prometheus.jdbc.config;

import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.immutables.value.Value;

/** Immutables meta annotation for Config interfaces. */
@Target({ PACKAGE, TYPE })
@Retention(CLASS)
@Value.Immutable
@Value.Style( //
    forceJacksonPropertyNames = false, // to make @JsonNaming work
    redactedMask = "***" // how to mask confidential stuff in toString()
)
@interface ImmutableConfigObject {
}
