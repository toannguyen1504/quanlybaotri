# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/api-gateway/src services/api-gateway/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/api-gateway/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /application
COPY --from=build /workspace/services/api-gateway/target/api-gateway-*.jar application.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
