# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/payment-service/src services/payment-service/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -q -f services/payment-service/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /application
COPY --from=build /workspace/services/payment-service/target/payment-service-*.jar application.jar
USER app
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "application.jar"]
