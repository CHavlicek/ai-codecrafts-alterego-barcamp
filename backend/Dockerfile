# Stage 1 — build with the official Gradle image.
# We deliberately don't bundle a Gradle wrapper in the repo, so the build
# image brings its own Gradle on PATH instead of relying on ./gradlew.
FROM gradle:9.4-jdk21-alpine AS build
WORKDIR /workspace
# Build files first for dependency-resolution layer caching
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle dependencies --no-daemon || true
# Copy sources and build
COPY config ./config
COPY src ./src
RUN gradle bootJar --no-daemon

# Stage 2 — minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
