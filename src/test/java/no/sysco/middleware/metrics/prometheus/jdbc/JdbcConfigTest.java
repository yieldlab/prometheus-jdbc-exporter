package no.sysco.middleware.metrics.prometheus.jdbc;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.BDDMockito.given;

import java.sql.ResultSet;
import java.time.Clock;
import java.util.Map;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentest4j.AssertionFailedError;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableConfig;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableConnectionDef;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableJob;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableQueryDef;
import no.sysco.middleware.metrics.prometheus.jdbc.config.QueryString;

@ExtendWith(MockitoExtension.class)
class JdbcConfigTest {

    @Test
    void exportsMetricsCorrectly(@Mock(answer = RETURNS_DEEP_STUBS) ConnectionProvider connProvider, @Mock Clock clock)
        throws Exception
    {
        // given
        final var config = ImmutableConfig.builder()
            .addJobs(
                ImmutableJob.builder()
                    .name("exportsMetricsCorrectly")
                    .addConnections(ImmutableConnectionDef.builder().url("test").build())
                    .addQueries(
                        ImmutableQueryDef.builder()
                            .name("q1")
                            .putStaticLabels("stat", "ic")
                            .addLabels("fromResultSet")
                            .addValues("value")
                            .query(QueryString.query("1337"))
                            .build())
                    .build())
            .build();

        final var rs = Mockito.mock(ResultSet.class);
        final var conn = connProvider.getConnection("test", Map.of());
        final var stmt = conn.prepareStatement("1337");
        given(stmt.executeQuery()).willReturn(rs);
        given(rs.next()).willReturn(true).willReturn(false);
        given(rs.getString("fromResultSet")).willReturn("foo").willThrow(AssertionFailedError.class);
        given(rs.getDouble("value")).willReturn(42d).willThrow(AssertionFailedError.class);

        // when
        final var underTest = new JdbcConfig("test", config, connProvider, clock);

        // then
        final var allSamples = underTest.runJobs().collect(toList());
        assertThat(
            allSamples,
            containsInAnyOrder(
                samplesNamed(equalTo("test_q1")),
                samplesNamed(equalTo("test_scrape_duration_seconds")),
                samplesNamed(equalTo("test_scrape_error"))));

        final var querySamples = allSamples.stream().filter(s -> "test_q1".equals(s.name)).findFirst().get();
        assertThat(querySamples.name, is("test_q1"));
        assertThat(querySamples.help, is("column value"));
        assertThat(querySamples.type, is(Collector.Type.GAUGE));
        assertThat(querySamples.samples, hasSize(1));

        final var querySample = querySamples.samples.iterator().next();
        assertThat(querySample.name, is("test_q1"));
        assertThat(querySample.labelNames, containsInAnyOrder("stat", "fromResultSet"));
        assertThat(querySample.labelValues, hasSize(querySample.labelNames.size()));
        assertThat(querySample.labelValues.get(querySample.labelNames.indexOf("stat")), is("ic"));
        assertThat(querySample.labelValues.get(querySample.labelNames.indexOf("fromResultSet")), is("foo"));
        assertThat(querySample.value, is(42d));

        final var errorSamples = allSamples.stream().filter(s -> "test_scrape_error".equals(s.name)).findFirst().get();
        assertThat(errorSamples.type, is(Collector.Type.GAUGE));
        assertThat(errorSamples.samples, hasSize(1));

        final var errorSample = errorSamples.samples.iterator().next();
        assertThat(errorSample.value, is(0d));

        final var inOrder = Mockito.inOrder(conn, stmt, rs);
        inOrder.verify(stmt).executeQuery();
        inOrder.verify(rs).next();
        inOrder.verify(rs).next();
        inOrder.verify(rs).close();
        inOrder.verify(stmt).close();
        inOrder.verify(conn).close();
        inOrder.verifyNoMoreInteractions();
    }

    private static final Matcher<MetricFamilySamples> samplesNamed(Matcher<? super String> name) {
        return new TypeSafeDiagnosingMatcher<Collector.MetricFamilySamples>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("samples named ").appendDescriptionOf(name);
            }

            @Override
            protected boolean matchesSafely(MetricFamilySamples item, Description mismatchDescription) {
                if (!name.matches(item.name)) {
                    mismatchDescription.appendText("name was ");
                    name.describeMismatch(item.name, mismatchDescription);
                    return false;
                }

                return true;
            }
        };
    }
}
