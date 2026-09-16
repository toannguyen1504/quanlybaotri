# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/maintenance-service/pom.xml services/maintenance-service/pom.xml
COPY services/maintenance-service/src services/maintenance-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/maintenance-service/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/uploads && chown -R app:app /data
WORKDIR /application
COPY --from=build /workspace/services/maintenance-service/target/maintenance-service-*.jar application.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "application.jar"]
