ARG SERVICE_MODULE=simple-event-sender

# ========== Этап сборки JAR ==========
# Учётные данные Nexus передаются через build args из infrastructure/.env (docker-compose)
FROM eclipse-temurin:24-jdk-alpine AS build
ARG SERVICE_MODULE
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
COPY simple-event-sender simple-event-sender
RUN ./gradlew --no-daemon :${SERVICE_MODULE}:clean :${SERVICE_MODULE}:bootJar

# ========== Этап запуска ==========
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY --from=build /app/simple-event-sender/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "-Dio.netty.noUnsafe=true", \
  "-jar", "app.jar"]
