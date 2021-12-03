package no.sysco.middleware.metrics.prometheus.jdbc;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

import java.sql.ResultSet;
import java.time.Clock;
import java.util.HashMap;
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
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableConfig;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableConnectionDef;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableJob;
import no.sysco.middleware.metrics.prometheus.jdbc.config.ImmutableQueryDef;
import no.sysco.middleware.metrics.prometheus.jdbc.config.QueryString;

@ExtendWith(MockitoExtension.class)
class JdbcConfigTest {

    @Test
    void exportsMetricsCorrectly(
        @Mock(answer = RETURNS_DEEP_STUBS) ConnectionProvider connProvider,
        @Mock TemplateRenderer renderer,
        @Mock Clock clock) throws Exception
    {
        // given
        final var config = ImmutableConfig.builder()
            .addJobs(
                ImmutableJob.builder()
                    .name("exportsMetricsCorrectly")
                    .addConnections(
                        ImmutableConnectionDef.builder().url("test").username("user").password("pass").build())
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

        given(renderer.render("test")).willReturn("db");
        given(renderer.render("user")).willReturn("nobody");
        given(renderer.render("pass")).willReturn("nothing");
        given(renderer.render("1337")).willReturn("leet");
        final var rs = Mockito.mock(ResultSet.class);
        final var conn = connProvider.getConnection("db", Map.of("user", "nobody", "password", "nothing"));
        final var stmt = conn.prepareStatement("leet");
        given(stmt.executeQuery()).willReturn(rs);
        given(rs.next()).willReturn(true).willReturn(true).willReturn(false);
        given(rs.getString("fromResultSet")).willReturn("foo").willReturn("bar").willThrow(AssertionFailedError.class);
        given(rs.getDouble("value")).willReturn(42d).willReturn(43d).willThrow(AssertionFailedError.class);

        // when
        final var underTest = new JdbcConfig("test", config, connProvider, renderer, clock);

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
        assertThat(querySamples.samples, hasSize(2));
        assertThat(
            querySamples.samples,
            containsInAnyOrder(
                sampleWith(
                    equalTo(querySamples.name),
                    equalTo(42d),
                    equalTo(Map.of("stat", "ic", "fromResultSet", "foo"))),
                sampleWith(
                    equalTo(querySamples.name),
                    equalTo(43d),
                    equalTo(Map.of("stat", "ic", "fromResultSet", "bar")))));

        final var errorSamples = allSamples.stream().filter(s -> "test_scrape_error".equals(s.name)).findFirst().get();
        assertThat(errorSamples.type, is(Collector.Type.GAUGE));
        assertThat(errorSamples.samples, hasSize(1));

        final var errorSample = errorSamples.samples.iterator().next();
        assertThat(errorSample.value, is(0d));

        final var inOrder = Mockito.inOrder(conn, stmt, rs);
        inOrder.verify(stmt).executeQuery();
        inOrder.verify(rs, times(3)).next();
        inOrder.verify(rs).close();
        inOrder.verify(stmt).close();
        inOrder.verify(conn).close();
        inOrder.verifyNoMoreInteractions();
    }

    private static final Matcher<Collector.MetricFamilySamples> samplesNamed(Matcher<? super String> name) {
        return new TypeSafeDiagnosingMatcher<Collector.MetricFamilySamples>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("samples named ").appendDescriptionOf(name);
            }

            @Override
            protected boolean matchesSafely(Collector.MetricFamilySamples item, Description mismatchDescription) {
                if (!name.matches(item.name)) {
                    mismatchDescription.appendText("name ");
                    name.describeMismatch(item.name, mismatchDescription);
                    return false;
                }

                return true;
            }
        };
    }

    private static final Matcher<Collector.MetricFamilySamples.Sample> sampleWith(
        final Matcher<? super String> name,
        final Matcher<? super Double> value,
        final Matcher<? super Map<String, String>> labels)
    {
        return new TypeSafeDiagnosingMatcher<Collector.MetricFamilySamples.Sample>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("a sample with name ")
                    .appendDescriptionOf(name)
                    .appendText(", value ")
                    .appendDescriptionOf(value)
                    .appendText(" and labels ")
                    .appendDescriptionOf(labels);
            }

            @Override
            protected boolean matchesSafely(
                Collector.MetricFamilySamples.Sample item,
                Description mismatchDescription)
            {
                if (!name.matches(item.name)) {
                    mismatchDescription.appendText("name ");
                    name.describeMismatch(item.name, mismatchDescription);
                    return false;
                }
                if (!value.matches(item.value)) {
                    mismatchDescription.appendText("value ");
                    value.describeMismatch(item.value, mismatchDescription);
                    return false;
                }

                if (item.labelNames == null) {
                    mismatchDescription.appendText("labelNames was ").appendValue(item.labelNames);
                    return false;
                }
                if (item.labelValues == null) {
                    mismatchDescription.appendText("labelValues was ").appendValue(item.labelValues);
                    return false;
                }

                final var itemLabels = new HashMap<String, String>();
                final var names = item.labelNames.iterator();
                final var values = item.labelValues.iterator();
                while (names.hasNext() && values.hasNext()) {
                    final var label = Map.entry(names.next(), values.next());
                    final var prevValue = itemLabels.put(label.getKey(), label.getValue());
                    if (prevValue != null) {
                        mismatchDescription.appendText("duplicate label named ")
                            .appendValue(label.getKey())
                            .appendText(": ")
                            .appendValue(prevValue)
                            .appendText(" vs. ")
                            .appendValue(label.getValue());
                        return false;
                    }
                }

                if (names.hasNext()) {
                    mismatchDescription.appendText("there were more label names than label values");
                    return false;
                } else if (values.hasNext()) {
                    mismatchDescription.appendText("there were more label values than label names");
                    return false;
                }

                if (!labels.matches(itemLabels)) {
                    mismatchDescription.appendText("labels ");
                    labels.describeMismatch(labels, mismatchDescription);
                    return false;
                }

                return true;
            }
        };
    }
}
