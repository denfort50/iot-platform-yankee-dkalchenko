# Сборка JAR на хосте: из корня репозитория выполнить
#   ./gradlew :simple-event-sender:clean :simple-event-sender:bootJar
# затем: docker build -f simple-event-sender/Dockerfile -t simple-event-sender:1.0.0 .
# Контекст сборки — корень репозитория (ожидается simple-event-sender/build/libs/*.jar).

FROM eclipse-temurin:24-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN adduser -D -g "" appuser
COPY simple-event-sender/build/libs/*.jar app.jar
RUN chown -R appuser:appuser /app
USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", \
  "--enable-native-access=ALL-UNNAMED", \
  "-Dio.netty.noUnsafe=true", \
  "-jar", "app.jar"]
