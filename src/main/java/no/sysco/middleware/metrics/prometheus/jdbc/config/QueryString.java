package no.sysco.middleware.metrics.prometheus.jdbc.config;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Function;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@ConfigObject
@Value.Enclosing
@JsonDeserialize(builder = JacksonQueryStringBuilder.class)
public abstract class QueryString {
    QueryString() {
        // package private (try to "seal" this class)
    }

    public static QueryString.Plain query(String query) {
        return ImmutableQueryString.Plain.of(query);
    }

    public static QueryString.Ref queryRef(String queryRef) {
        return ImmutableQueryString.Ref.of(queryRef);
    }

    @ConfigObject
    @Value.Immutable(builder = false)
    @JsonDeserialize(as = ImmutableQueryString.Plain.class)
    public static abstract class Plain extends QueryString {
        @Value.Parameter
        abstract String query();

        @Override
        public final String resolve(Function<String, String> refResolver) {
            requireNonNull(refResolver);
            return query();
        }
    }

    @ConfigObject
    @Value.Immutable(builder = false)
    @JsonDeserialize(as = ImmutableQueryString.Ref.class)
    public static abstract class Ref extends QueryString {
        @Value.Parameter
        abstract String queryRef();

        @Override
        public final String resolve(Function<String, String> refResolver) {
            return requireNonNull(refResolver.apply(queryRef()));
        }
    }

    public abstract String resolve(Function<String, String> refResolver);
}

@ConfigObject
final class JacksonQueryStringBuilder {
    private static final String UNSET = "sentinel";

    @JsonProperty
    String query = UNSET, queryRef = UNSET;

    QueryString build() {
        if (query == UNSET) {
            if (queryRef == UNSET) {
                throw new IllegalArgumentException("either query or query ref need to be set");
            }
            return QueryString.queryRef(queryRef);
        } else if (queryRef == UNSET) {
            return QueryString.query(query);
        } else {
            throw new IllegalArgumentException("cannot use query and query ref at the same time");
        }
    }
}
