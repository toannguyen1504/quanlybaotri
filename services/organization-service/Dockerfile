# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/organization-service/pom.xml services/organization-service/pom.xml
COPY services/organization-service/src services/organization-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/organization-service/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /application
COPY --from=build /workspace/services/organization-service/target/organization-service-*.jar application.jar
USER app
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "application.jar"]
