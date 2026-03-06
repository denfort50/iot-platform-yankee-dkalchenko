# Сборка JAR на хосте: из корня репозитория выполнить
#   ./gradlew :events-collector-service:clean :events-collector-service:bootJar
# затем: docker build -f events-collector-service/Dockerfile -t events-collector-service:1.0.0 .
# Контекст сборки — корень репозитория (ожидается events-collector-service/build/libs/*.jar).

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY events-collector-service/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 9090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]
