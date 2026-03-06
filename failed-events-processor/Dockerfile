# Сборка JAR на хосте: из корня репозитория выполнить
#   ./gradlew :failed-events-processor:clean :failed-events-processor:bootJar
# затем: docker build -f failed-events-processor/Dockerfile -t failed-events-processor:1.0.0 .
# Контекст сборки — корень репозитория.

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY failed-events-processor/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 9090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]
