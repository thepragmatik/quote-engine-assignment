# ATTENTION(design): alpine JRE for wget healthcheck; deviation from distroless in ADR-005.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine
# ATTENTION(security): non-root UID 10001
RUN adduser -u 10001 -D appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 10001
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]
