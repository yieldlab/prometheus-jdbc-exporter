package no.sysco.middleware.metrics.prometheus.jdbc;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.prometheus.client.CollectorRegistry;

class JdbcCollectorTest {

    @BeforeEach
    void setUp() {
        CollectorRegistry.defaultRegistry.clear();
    }

    @Test
    void testReloadConfig() throws URISyntaxException, IOException {
        final var config = Paths.get(getClass().getClassLoader().getResource("config.yml").toURI());
        final var collector = new JdbcCollector("jdbc", config);
        Files.setLastModifiedTime(config, FileTime.from(Instant.now()));
        collector.reloadConfigIfOutdated();
    }

    @Test
    void testMultiFileConfig() throws URISyntaxException, IOException {
        final var config = Paths.get(getClass().getClassLoader().getResource("multipleconfigs").toURI());
        final var collector = new JdbcCollector("jdbc", config);
        Files.setLastModifiedTime(config, FileTime.from(Instant.now()));
        collector.reloadConfigIfOutdated();
    }
}
