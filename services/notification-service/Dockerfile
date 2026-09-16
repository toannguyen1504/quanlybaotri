# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml
COPY services/notification-service/src services/notification-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/notification-service/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /application
COPY --from=build /workspace/services/notification-service/target/notification-service-*.jar application.jar
USER app
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "application.jar"]
