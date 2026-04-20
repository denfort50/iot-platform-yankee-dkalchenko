# Сборка: из корня репозитория
#   ./gradlew :event-service:bootJar
#   docker build -f event-service/Dockerfile -t event-service:1.0.0 .

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY event-service/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8080 9090
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "--sun-misc-unsafe-memory-access=allow", \
  "-jar", "app.jar"]
