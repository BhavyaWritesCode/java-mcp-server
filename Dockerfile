# Stage 1 — Build
FROM maven:3.9.9-eclipse-temurin-23 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -q

# Stage 2 — Run
FROM eclipse-temurin:23-jre-alpine

WORKDIR /app

COPY --from=build /app/target/java-mcp-server-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]