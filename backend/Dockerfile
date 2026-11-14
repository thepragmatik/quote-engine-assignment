# ATTENTION(design): multi-stage Maven build. Alpine JRE (not distroless) so the
# compose healthcheck can use wget — deviation from MISSION_v2 §6 documented in ADR-005.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine
# ATTENTION(security): non-root UID 10001 per MISSION_v2 §6
RUN adduser -u 10001 -D appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 10001
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
