# This is an example Dockerfile that shows how to package JDBC drivers and
# metrcis configuration into a ready-to-use Docker image.

FROM ghcr.io/yieldlab/prometheus-jdbc-exporter:main-SNAPSHOT

COPY download-list /app/libs/drivers/list
USER root
RUN /entrypoint.sh download /app/libs/drivers/list /app/libs/drivers/jars /app/libs/drivers/classpath
COPY config.yml /app/config.yml

ENV ADDITIONAL_CLASSPATH_FILES=/app/libs/drivers/classpath
CMD [ "0.0.0.0:5555", "/app/config.yml" ]

# nobody
USER 65534
