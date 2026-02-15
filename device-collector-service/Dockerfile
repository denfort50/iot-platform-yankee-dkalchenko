# Сборка из корня репозитория (контекст: ..). NEXUS_URL — для загрузки iot-avro-schemas.
ARG SERVICE_MODULE=device-collector-service

# ========== Этап сборки JAR ==========
FROM eclipse-temurin:24-jdk-alpine AS build
ARG SERVICE_MODULE
# Учётные данные Nexus передаются через build args из infrastructure/.env (docker-compose)
ARG NEXUS_URL
ARG NEXUS_USER
ARG NEXUS_PASSWORD
ENV NEXUS_URL=${NEXUS_URL} NEXUS_USER=${NEXUS_USER} NEXUS_PASSWORD=${NEXUS_PASSWORD}
WORKDIR /app
# Кэш слоя с Gradle: копируем только wrapper и скачиваем дистрибутив один раз
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version
# Копируем конфигурацию и исходники (часто меняются — не инвалидируют кэш Gradle)
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY iot-avro-schemas iot-avro-schemas
COPY device-collector-service device-collector-service
RUN ./gradlew --no-daemon :${SERVICE_MODULE}:clean :${SERVICE_MODULE}:bootJar

# ========== Этап запуска ==========
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY --from=build /app/device-collector-service/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 9090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]
