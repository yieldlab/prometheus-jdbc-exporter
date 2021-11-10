FROM docker.io/eclipse-temurin:11.0.13_8-jre-alpine@sha256:68a61bdf11c53dd0d2396bfe631877a300db41329847b487aa06ea5a4f51cecf as base

FROM base AS deps
RUN apk add --no-cache gettext

FROM base AS app
COPY --from=deps /usr/lib/libintl.so* /usr/lib/
COPY --from=deps /usr/bin/envsubst /usr/bin/
COPY maven/ /
ENTRYPOINT [ "/docker-entrypoint.sh" ]
# nobody
USER 65534
