# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/identity-service/src services/identity-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/identity-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /data/keys && chown -R app:app /data
WORKDIR /application
COPY --from=build /workspace/services/identity-service/target/identity-service-*.jar application.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "application.jar"]
