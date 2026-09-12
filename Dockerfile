# =============================================================================
#  Backend — Multi-stage build
#  Stage 1: Build Spring Boot fat JAR with Maven
#  Stage 2: Run on minimal JRE image
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first so Maven dependencies are cached as a layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build (skip tests — they need H2 + mock KC which is CI-only)
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/target/*.jar app.jar

# Spring Boot default port
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
