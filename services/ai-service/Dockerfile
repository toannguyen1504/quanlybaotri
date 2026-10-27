# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/ai-service/pom.xml services/ai-service/pom.xml
COPY services/ai-service/src services/ai-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/ai-service/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /application
COPY --from=build /workspace/services/ai-service/target/ai-service-*.jar application.jar
USER app
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "application.jar"]
