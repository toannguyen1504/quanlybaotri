# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/asset-service/pom.xml services/asset-service/pom.xml
COPY services/asset-service/src services/asset-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/asset-service/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /application
COPY --from=build /workspace/services/asset-service/target/asset-service-*.jar application.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "application.jar"]
