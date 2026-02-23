# Сборка JAR на хосте: из корня репозитория выполнить
#   ./gradlew :device-service:bootJar
# затем: docker build -f device-service/Dockerfile -t device-service:1.0.0 .
# Контекст сборки — корень репозитория (ожидается device-service/build/libs/*.jar).

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY device-service/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8080 9090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]
