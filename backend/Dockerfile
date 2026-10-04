# Multi-stage Dockerfile for GlucoTwin backend
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./
RUN chmod +x ./gradlew
# Download dependencies in a separate layer for better caching
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
# Non-root user for security
RUN addgroup -S glucotwin && adduser -S glucotwin -G glucotwin
WORKDIR /app
COPY --from=builder /build/build/libs/glucotwin-backend.jar app.jar
RUN chown glucotwin:glucotwin app.jar
USER glucotwin
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
    CMD wget -q --spider http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
