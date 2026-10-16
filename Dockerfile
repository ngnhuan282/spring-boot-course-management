# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY backend/src/ src/
# Maven verify, including MySQL migration tests, runs in CI before delivery.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package && cp target/course-management-*.jar /build/application.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
ARG VCS_REF=unversioned
LABEL org.opencontainers.image.source="https://github.com/ngnhuan282/spring-boot-course-management" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.description="Independent Course experiment for chapters 4-8"
COPY --from=build --chown=app:app /build/application.jar /app/application.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=90s --retries=24 CMD curl --fail --silent http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
