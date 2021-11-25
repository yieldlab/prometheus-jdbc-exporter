package no.sysco.middleware.metrics.prometheus.jdbc.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void testConfigShouldFailIfEmpty() {
        assertThrows(IllegalArgumentException.class, () -> parseConfig(""));
    }

    @Test
    void testConfigShouldFailIfJobsEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobNameEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- something: \"global\"\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobConnectionsEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobConnectionUrlEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - username: system" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobConnectionUsernameEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobConnectionPasswordEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobQueriesEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobQueryNameEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - something: jdbc\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobQueryValuesEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    values:\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobQueryAndRefEmpty() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobQueryAndRef() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "    query: abc\n" + //
            "    query_ref: abc\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldFailIfJobQueryRefNonExistingQuery() {
        final var config = "---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "    query_ref: abc\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    @Test
    void testConfigShouldBuildWithQueryRef() throws IOException {
        final var parsed = parseConfig("---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "    query_ref: abc\n" + //
            "queries:\n" + //
            "  abc: \"select * from a\"\n" + //
            "");

        final var expected = ImmutableConfig.builder()
            .addJobs(
                ImmutableJob.builder()
                    .name("global")
                    .addConnections(
                        ImmutableConnectionDef.builder().url("jdbc").username("sys").password("sys").build())
                    .addQueries(
                        ImmutableQueryDef.builder()
                            .name("jdbc")
                            .addValues("v1")
                            .query(QueryString.queryRef("abc"))
                            .build())
                    .build())
            .queries(Map.of("abc", "select * from a"))
            .build();

        assertThat(parsed, is(equalTo(expected)));
    }

    @Test
    void testConfigShouldBuildWithoutQueryRef() throws IOException {
        final var parsed = parseConfig("---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "    query: abc\n" + //
            "");

        final var expected = ImmutableConfig.builder()
            .addJobs(
                ImmutableJob.builder()
                    .name("global")
                    .addConnections(
                        ImmutableConnectionDef.builder().url("jdbc").username("sys").password("sys").build())
                    .addQueries(
                        ImmutableQueryDef.builder()
                            .name("jdbc")
                            .addValues("v1")
                            .query(QueryString.query("abc"))
                            .build())
                    .build())
            .build();

        assertThat(parsed, is(equalTo(expected)));
    }

    @Test
    void testConfigShouldBuildWithCacheSeconds() throws IOException {
        final var parsed = parseConfig("---\n" + //
            "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    cache_seconds: 180\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "    query: abc\n" + //
            "");

        final var expected = ImmutableConfig.builder()
            .addJobs(
                ImmutableJob.builder()
                    .name("global")
                    .addConnections(
                        ImmutableConnectionDef.builder().url("jdbc").username("sys").password("sys").build())
                    .addQueries(
                        ImmutableQueryDef.builder()
                            .name("jdbc")
                            .cacheDuration(Duration.ofSeconds(180))
                            .addValues("v1")
                            .query(QueryString.query("abc"))
                            .build())
                    .build())
            .build();

        assertThat(parsed, is(equalTo(expected)));
    }

    @Test
    void testConfigShouldFailWithInvalidCacheSeconds() {
        final var config = "jobs:\n" + //
            "- name: \"global\"\n" + //
            "  connections:\n" + //
            "  - url: jdbc\n" + //
            "    username: sys\n" + //
            "    password: sys\n" + //
            "  queries:\n" + //
            "  - name: jdbc\n" + //
            "    cache_seconds: -999\n" + //
            "    values:\n" + //
            "    - v1\n" + //
            "    query: abc\n" + //
            "";

        assertThrows(IllegalArgumentException.class, () -> parseConfig(config));
    }

    private static final Config parseConfig(String config) throws IOException {
        try (final var data = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))) {
            return Config.parseYaml(data);
        }
    }
}
