# prometheus-jdbc-exporter in Docker Compose

This example shows on prometheus-jdbc-exporter may be used from within Docker
Compose. The exporter will scrape a MySQL and a PostgreSQL database. Metrics
will be collected by Prometheus.

In order to run this example, a Docker daemon and Docker Compose are required.
Bring up the example via `docker-compose up`. After some time, scraped metric
samples can be inspected in Prometheus via `http://(docker-host ip):9090`. Hit
`Ctrl+C` when done with the example, and use `docker-compose down -v --rmi
local` to cleanup all resources.

Let's inspect [`docker-compose.yml`](docker-compose.yml). This example shows two
ways of getting the JDBC drivers and configuration files into the running
container.

1. **Download drivers at service startup**  
   The `prometheus-jdbc-exporter` service relies on the successful completion of
   the `prometheus-jdbc-exporter-download` service, which fetches the JDBC
   drivers listed in [`download-list`](download-list) into the `/app/downloads`
   folder. That folder is shared between both services. The exporter service
   will then pick up all the downloaded files and add them to the Java
   classpath. The configuration file is also added via a volume.

2. **Use a custom Docker image**  
   The `prometheus-jdbc-exporter-prepackaged` service builds and runs a custom
   image where all drivers and configuration is embedded at build time. Please
   have a look at the [`Dockerfile`](Dockerfile).
