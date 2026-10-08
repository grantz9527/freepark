# FreePark console + API (Vue dist baked into the Spring Boot jar).
# Pins: Java 21, Node 22.18, Spring Boot 4.1.1 — see ai-build/VERSIONS.md
#
# Build from repo root:
#   docker build -f docker/local/Dockerfile -t freepark-local:0.0.1 .

FROM node:22.18.0-bookworm-slim AS frontend
WORKDIR /fe
COPY local_frontend/package.json local_frontend/package-lock.json ./
RUN npm ci
COPY local_frontend/ ./
RUN npm run build-only

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /src
COPY driver-api ./driver-api
COPY zhensi-driver ./zhensi-driver
COPY local_server ./local_server
COPY docker/local/maven-settings.xml /root/.m2/settings.xml
COPY --from=frontend /fe/dist ./local_server/src/main/resources/static
RUN chmod +x local_server/mvnw \
 && ./local_server/mvnw -f driver-api/pom.xml -DskipTests install \
 && ./local_server/mvnw -f zhensi-driver/pom.xml -DskipTests install \
 && ./local_server/mvnw -f local_server/pom.xml -DskipTests package

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
RUN mkdir -p /app/data
COPY --from=backend /src/local_server/target/local-server-0.0.1-SNAPSHOT.jar /app/app.jar
ENV JAVA_OPTS="" \
    MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    MYSQL_DATABASE=freepark_local \
    MYSQL_USER=freepark_local \
    SERVER_PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=8 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
