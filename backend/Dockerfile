# ParkGrid backend — Spring Boot 3.3, Java 17.
# Multi-stage: Maven build -> slim JRE runtime.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Tests run in CI (.github/workflows/ci.yml); the image build just packages.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/smart-parking-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
