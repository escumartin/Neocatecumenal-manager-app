FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:3c35c595580ae3342eb34e5832e8e516ded8e767efa4a5fe61b8644d44847b8e AS build
ARG SERVICE_REVISION
RUN printf '%s' "${SERVICE_REVISION}" | grep -Eq '^[0-9a-f]{7,40}$' \
    || { echo "SERVICE_REVISION es obligatorio y debe cumplir ^[0-9a-f]{7,40}$" >&2; exit 1; }
WORKDIR /workspace/backend
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY --from=api openapi.yaml /workspace/api/openapi.yaml
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/neocatecumenal-manager-0.0.1-SNAPSHOT.jar extract --layers --destination /workspace/extracted

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:23481dc33a759b79d62814ceb1ec27a0d1a84da9d7aab0609577427176fbb1b7
ARG SERVICE_REVISION
LABEL org.opencontainers.image.revision="${SERVICE_REVISION}"
ENV SERVICE_REVISION=${SERVICE_REVISION}
RUN groupadd --system --gid 10001 neocatecumenal \
    && useradd --system --uid 10001 --gid neocatecumenal --no-create-home --shell /usr/sbin/nologin neocatecumenal
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && head -n 1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["java", "-jar", "/app/neocatecumenal-manager-0.0.1-SNAPSHOT.jar"]
