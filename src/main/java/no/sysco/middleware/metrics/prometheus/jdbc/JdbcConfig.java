package no.sysco.middleware.metrics.prometheus.jdbc;

import io.prometheus.client.Collector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 *
 */
class JdbcConfig {
  private static final Logger LOGGER = Logger.getLogger(JdbcConfig.class.getName());

  private List<JdbcJob> jobs = new ArrayList<>();
  // <queryName, <expiration unixtimestamp in ms, previous samples>>
  private Map<String, Map.Entry<Long, List<Collector.MetricFamilySamples>>> sampleCache = new ConcurrentHashMap<>();
  private Clock clock = Clock.systemUTC();

  JdbcConfig(Map<String, Object> yamlConfig) {
    if (yamlConfig == null) {  // Yaml config empty, set config to empty map.
      yamlConfig = new HashMap<>();
      LOGGER.warning("JDBC Config file is empty.");
    }

    Map<String, String> queries = new TreeMap<>();

    if (yamlConfig.containsKey("queries")) {
      TreeMap<String, Object> labels =
          new TreeMap<>((Map<String, Object>) yamlConfig.get("queries"));
      for (Map.Entry<String, Object> entry : labels.entrySet()) {
        queries.put(entry.getKey(), (String) entry.getValue());
      }
    }

    if (yamlConfig.containsKey("jobs")) {
      final List<Map<String, Object>> jobList =
          Optional.ofNullable((List<Map<String, Object>>) yamlConfig.get("jobs"))
              .orElseThrow(() ->
                  new IllegalArgumentException("JDBC Config file does not have `jobs` defined. " +
                      "It will not collect any metric samples."));

      for (Map<String, Object> jobObject : jobList) {
        JdbcJob job = new JdbcJob();
        jobs.add(job);

        if (jobObject.containsKey("name")) {
          job.name = (String) jobObject.get("name");
        } else {
          throw new IllegalArgumentException("JDBC Job does not have a `name` defined. " +
              "This value is required to execute collector.");
        }

        if (jobObject.containsKey("connections")) {
          final List<Map<String, Object>> connections =
              Optional.ofNullable((List<Map<String, Object>>) jobObject.get("connections"))
                  .orElseThrow(() ->
                      new IllegalArgumentException("JDBC Job does not have `connections` defined. " +
                          "This value is required to execute collector."));

          for (Map<String, Object> connObject : connections) {
            JdbcConnection connection = new JdbcConnection();
            job.connections.add(connection);

            if (connObject.containsKey("url")) {
              connection.url = (String) connObject.get("url");
            } else {
              throw new IllegalArgumentException("JDBC Connection `url` is not defined. " +
                  "This value is required to execute collector.");
            }

            if (connObject.containsKey("username")) {
              connection.username = (String) connObject.get("username");
            }

            if (connObject.containsKey("password")) {
              connection.password = (String) connObject.get("password");
            }

            if (connObject.containsKey("driver_class_name")) {
              connection.driverClassName = (String) connObject.get("driver_class_name");
            }
          }
        } else {
          throw new IllegalArgumentException("JDBC Job does not have a `connections` defined. " +
              "This value is required to execute collector.");
        }

        if (jobObject.containsKey("queries")) {
          final List<Map<String, Object>> queriesList =
              Optional.ofNullable((List<Map<String, Object>>) jobObject.get("queries"))
                  .orElseThrow(() ->
                      new IllegalArgumentException("JDBC Job does not have `queries` defined. " +
                          "This value is required to execute collector."));

          for (Map<String, Object> queryObject : queriesList) {
            Query query = new Query();
            job.queries.add(query);

            if (queryObject.containsKey("name")) {
              query.name = (String) queryObject.get("name");
            } else {
              throw new IllegalArgumentException("JDBC Query does not have a `name` defined. " +
                  "This value is required to execute collector.");
            }

            if (queryObject.containsKey("help")) {
              query.help = (String) queryObject.get("help");
            }

            if (queryObject.containsKey("static_labels")) {
              final Map<String, Object> staticLabels = (Map<String, Object>) queryObject.get("static_labels");

              for (Map.Entry<String, Object> staticLabel : staticLabels.entrySet()) {
                query.staticLabels.put(staticLabel.getKey(), (String) staticLabel.getValue());
              }
            }

            if (queryObject.containsKey("labels")) {
              final List<Object> labels =
                  Optional.ofNullable((List<Object>) queryObject.get("labels"))
                      .orElse(new ArrayList<>());

              for (Object label : labels) {
                query.labels.add((String) label);
              }
            }

            if (queryObject.containsKey("values")) {
              final List<Object> values =
                  Optional.ofNullable((List<Object>) queryObject.get("values"))
                      .orElseThrow(() ->
                          new IllegalArgumentException("JDBC Query does not have `values` defined. " +
                              "This value is required to execute collector."));

              for (Object value : values) {
                query.values.add((String) value);
              }
            } else {
              throw new IllegalArgumentException("JDBC Query does not have `values` defined. " +
                  "This value is required to execute collector.");
            }

            if (queryObject.containsKey("query") && queryObject.containsKey("query_ref")) {
              throw new IllegalArgumentException("JDBC Query cannot have a `query` value and a `query_ref` at the same time.");
            }

            if (queryObject.containsKey("query")) {
              query.query = (String) queryObject.get("query");
            } else if (queryObject.containsKey("query_ref")) {
              query.queryRef = (String) queryObject.get("query_ref");
              if (queries.containsKey(query.queryRef)) {
                query.query = queries.get(query.queryRef);
              } else {
                throw new IllegalArgumentException("JDBC Query Reference does not exist as part of the JDBC Queries.");
              }
            } else {
              throw new IllegalArgumentException("JDBC Query must have a `query` value OR a `query_ref` defined.");
            }

            if (queryObject.containsKey("cache_seconds")) {
              try {
                query.cacheSeconds = (Integer) queryObject.get("cache_seconds");
                if (query.cacheSeconds < 0) {
                  throw new IllegalArgumentException("cache_seconds must be positive");
                }
              } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cache_seconds must be a valid number", e);
              }
            }
          }
        } else {
          throw new IllegalArgumentException("JDBC Job does not have `queries` defined. " +
              "This value is required to execute collector.");
        }
      }
    } else {
      throw new IllegalArgumentException("JDBC Config file does not have jobs defined. " +
          "It will not collect any metric samples.");
    }


  }

  List<Collector.MetricFamilySamples> runJobs(String prefix) {
    return
        jobs.parallelStream()
            .flatMap(job -> runJob(job, prefix).stream())
            .collect(toList());
  }

  private List<Collector.MetricFamilySamples> runJob(JdbcJob job, String prefix) {
    LOGGER.log(Level.INFO, "Running JDBC job: " + job.name);

    double error = 0;
    List<Collector.MetricFamilySamples> mfsList = new ArrayList<>();
    List<Connection> connections = new ArrayList<>();
    long start = System.nanoTime();

    try {
      List<Collector.MetricFamilySamples> mfsListFromJobs =
          job.connections
              .stream()
              .flatMap(connection -> {
                try {
                  LOGGER.info(String.format("JDBC Connection URL: %s", connection.url));

                  if (connection.driverClassName != null) {
                    Class.forName(connection.driverClassName);
                  }

                  final Connection conn =
                      DriverManager.getConnection(
                          connection.url, connection.username, connection.password);

                  connections.add(conn);

                  return
                      job.queries
                          .parallelStream()
                          .flatMap(query -> {
                            final String queryName = String.format("%s_%s", prefix, query.name);

                            if(sampleCache.containsKey(queryName)
                                    && sampleCache.get(queryName).getKey() > clock.millis()) {
                              return sampleCache.get(queryName).getValue().stream();
                            }

                            try {
                              PreparedStatement statement = conn.prepareStatement(query.query);
                              ResultSet rs = statement.executeQuery();
                              return getSamples(queryName, query, rs).stream();
                            } catch (SQLException e) {
                              LOGGER.log(Level.SEVERE, String.format("Error executing query: %s", query.query), e);
                              return Stream.empty();
                            }
                          });
                } catch (SQLException | ClassNotFoundException e) {
                  LOGGER.log(Level.SEVERE, "Error connecting to database", e);
                  return Stream.empty();
                }
              })
              .collect(toList());

      mfsList.addAll(mfsListFromJobs);
    } catch (Exception e) {
      error = 1;
    }

    connections.forEach(connection -> {
      try {
        connection.close();
      } catch (SQLException e) {
        LOGGER.log(Level.WARNING, "Error closing connection.", e);
      }
    });

    List<Collector.MetricFamilySamples.Sample> samples = new ArrayList<>();
    samples.add(
        new Collector.MetricFamilySamples.Sample(
            prefix + "_scrape_duration_seconds",
            new ArrayList<>(),
            new ArrayList<>(),
            (System.nanoTime() - start) / 1.0E9));
    mfsList.add(
        new Collector.MetricFamilySamples(
            prefix + "_scrape_duration_seconds",
            Collector.Type.GAUGE,
            "Time this JDBC scrape took, in seconds.",
            samples));

    samples = new ArrayList<>();
    samples.add(
        new Collector.MetricFamilySamples.Sample(
            prefix + "_scrape_error",
            new ArrayList<>(),
            new ArrayList<>(),
            error));
    mfsList.add(
        new Collector.MetricFamilySamples(
            prefix + "_scrape_error",
            Collector.Type.GAUGE,
            "Non-zero if this scrape failed.",
            samples));

    return mfsList;
  }

  private List<Collector.MetricFamilySamples> getSamples(String queryName,
                                                         JdbcConfig.Query query,
                                                         ResultSet rs)
      throws SQLException {
    List<Collector.MetricFamilySamples.Sample> samples = new ArrayList<>();

    while (rs.next()) {
      final List<String> labelNames = new ArrayList<>(query.labels);
      final List<String> labelValues = new ArrayList<>();

      for (String label : query.labels) {
        try {
          labelValues.add(rs.getString(label));
        } catch (SQLException e) {
          LOGGER.log(
              Level.WARNING,
              String.format("Label %s not found as part of the query result set.", label));

          labelValues.add("");
        }
      }

      for (Map.Entry<String, String> staticLabel : query.staticLabels.entrySet()) {
        labelNames.add(staticLabel.getKey());
        labelValues.add(staticLabel.getValue());
      }

      List<Collector.MetricFamilySamples.Sample> sample =
          query.values.stream()
              .map(value -> {
                try {
                  return rs.getFloat(value);
                } catch (SQLException e) {
                  LOGGER.log(
                      Level.SEVERE,
                      String.format("Sample value %s not found as part of the query result set.", value),
                      e);
                  return null;
                }
              })
              .map(value -> new Collector.MetricFamilySamples.Sample(queryName, labelNames, labelValues, value))
              .collect(toList());

      samples.addAll(sample);
    }

    List<Collector.MetricFamilySamples> samplesList = new ArrayList<>();
    samplesList.add(
        new Collector.MetricFamilySamples(queryName, Collector.Type.GAUGE, query.help, samples));

    if(query.cacheSeconds > 0) {
      sampleCache.put(queryName,
              new HashMap.SimpleImmutableEntry<>(clock.millis() + query.cacheSeconds * 1000L, samplesList));
    }

    return samplesList;
  }

  private static class JdbcConnection {
    String url;
    String username;
    String password;
    String driverClassName;
  }

  private static class JdbcJob {
    String name;
    List<JdbcConnection> connections = new ArrayList<>();
    List<Query> queries = new ArrayList<>();
  }

  private static class Query {
    String name;
    String help;
    Map<String, String> staticLabels = new HashMap<>();
    List<String> labels = new ArrayList<>();
    List<String> values = new ArrayList<>();
    int cacheSeconds;
    String query;
    String queryRef;
  }
}
