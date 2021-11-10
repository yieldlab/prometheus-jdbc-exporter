# This is an example Dockerfile that shows how to package JDBC drivers and
# metrcis configuration into a ready-to-use Docker image.

FROM ghcr.io/yieldlab/prometheus-jdbc-exporter:main-SNAPSHOT

COPY download-list /app/download-list
USER root
RUN /docker-entrypoint.sh download
COPY config.yml /app/config.yml
CMD [ "0.0.0.0:5555", "/app/config.yml" ]

# nobody
USER 65534
