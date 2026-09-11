# Stage 1: build the jar with Maven + JDK 17
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: run it with just a lightweight JRE (much smaller final image)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/interview-booth-backend-1.0.0.jar app.jar

# Render sets $PORT automatically; our application.properties already reads it
# via server.port=${PORT:8080}, so no extra config needed here.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
