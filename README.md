# Prometheus JDBC Exporter

Exporter inspired in [sql_exporter](https://github.com/justwatchcom/sql_exporter) and [jmx_exporter](https://github.com/prometheus/jmx_exporter)

It uses JDBC libraries to execute a SQL query that returns a `Float` result and a set of labels.

<!-- TOC -->
- [Getting Started](#getting-started)
- [Startup](#startup)
- [Configuration](#configuration)
- [Override metric prefix](#override-metric-prefix)
- [JDBC drivers](#jdbc-drivers)
  - [`download-list` file format](#download-list-file-format)
- [Examples](#examples)
- [Licence](#licence)
<!-- /TOC -->

## Getting Started

A YAML configuration file or directory with multiple YAML configuration files is required. 

Here is a sample:

```yaml
jobs:
- name: "global"
  connections:
  - url: 'jdbc:oracle:thin:@db:1521/ORCLPDB1'
    username: 'system'
    password: 'welcome1'
  queries:
  - name: "db_users"
    help: "Database Users"
    values:
      - "count"
    query:  |
            select count(1) count from dba_users
```

This configuration contains a list of Jobs and a list of Queries.

The first important part here is the `connections` object. It has the JDBC URL, username, password.

These are used to create connections to execute the queries defined inside the Job.

This query will create a metric `sql_db_users`, where *sql_* is the exporter prefix.

## Startup

Startup syntax is 
```
java -Djava.security.egd=file:///dev/urandom \ 
    -cp "." \ 
    no.sysco.middleware.metrics.prometheus.jdbc.WebServer \ 
    <[ip:]port> \ 
    <configFileOrDirectory>
```                     

configFileOrDirectory can point to a directory with at least one valid yaml file or directly a valid one yaml file.

## Configuration

This is a list of all possible options:

**1. jobs**

Represents a list of jobs that will be executed by the collector.

Values:

*name*: Name of the `job`. Required.

*connections*: List of connection details. At least one.

*queries*: List of queries to execute. At least one.

```yaml
jobs:
  - name: "job1"
    connections: ...
    queries: ...
```

**1.1. connection**

Represents connection details to connect to a database instance and
execute queries.

Values:

*url*: JDBC URL to connect to database instances. Required.

*username*: database user's name. Optional.

*password*: database user's password. Optional.

*driver_class_name*: Fully qualified name of the JDBC driver class. Optional.

```yaml
connections:
  - url: 'jdbc:oracle:thin:@db:1521/ORCLPDB1'
    username: 'system'
    password: 'welcome1'
```

**1.2. query**

Represents query definition to collect metrics from database.

Values:

*name*: Query name, that will be part of the metric name: `jdbc_<query_name>`. Required.

*help*: Query description, that will be used as metric description also. Optional.

*static_labels*: Set of label names, and their static values. Optional.

*labels*: List of labels, that has to match a column value, that must be `string`. Optional.

*values*: List of values, that has to match a column value, that must be `float`. At least one.

*query*: SQL query to select rows that will represent a metric sample.

*query_ref*: Reference to common queries shared between jobs.

*cache_seconds*: How many seconds to cache query results until they are refreshed. Optional.

`query` and `query_ref` are mutually exclusive. At least one of those has to be defined.

```yaml
  queries:
  - name: "db_users"
    help: "Database Users"
    values:
      - "count"
    query:  |
            select count(1) count from dba_users
```

or with `query_ref`

```yaml
  queries:
  - name: "db_users"
    help: "Database Users"
    values:
      - "count"
    query_ref: "query1"
```

**2. queries**

Represents common queries that can be referenced from different `jobs`.

```yaml
queries:
  query1: |
    SELECT count(1) "COUNT" FROM users
```

Where `query1` is the key that will be used from `query` definition.

## Override metric prefix

The default `jdbc` prefix to all metrics can be overridden via the env variable
METRIC_PREFIX and will prefix all metrics with `<METRIC_PREFIX>_`.

## JDBC drivers

By default, the Docker image doesn't ship with any JDBC drivers. The image
offers a way to download drivers via HTTP(S), though. Create a volume and mount
it into the container at `/app/downloads`. Mount the file `/app/download-list`
into the container and run the container with the `download` argument. All the
listed files will be downloaded into `/app/downloads`. Subsequent starts of the
container will then find and use those downloaded files.
### `download-list` file format

A simple text file. Each line consists of two or three space-separated values.

1. URL to fetch, e.g. `https://jdbc.example.com/driver.jar`
2. The expected SHA256 checksum of the downloaded file, e.g.
   `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
3. File name to use inside the container. If omitted, this will be the last path
   segment of the URL to fetch (in the above example, this would be
   `driver.jar`).

An example file is located at
[`examples/docker-compose/download-list`](examples/docker-compose/download-list).

## Examples

Go to the [`examples`](examples) directory.

## Licence

MIT Licenced.
