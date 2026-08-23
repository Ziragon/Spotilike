# Java Spring Multi-stage dockerfile
FROM eclipse-temurin:25-jdk AS builder
ARG SERVICE_NAME
WORKDIR /build

COPY . .
RUN chmod +x gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${SERVICE_NAME}:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
ARG SERVICE_NAME
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder --chown=app:app /build/${SERVICE_NAME}/build/libs/*.jar app.jar

USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
