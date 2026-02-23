# Сборка JAR на хосте: из корня репозитория выполнить
#   ./gradlew :device-collector-service:clean :device-collector-service:bootJar
# затем: docker build -f device-collector-service/Dockerfile -t device-collector-service:1.0.0 .
# Контекст сборки — корень репозитория (ожидается device-collector-service/build/libs/*.jar).

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY device-collector-service/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 9090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]
