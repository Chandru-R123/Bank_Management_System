# =============================================================================
#  Keycloak 24 + reCAPTCHA login plugin
#
#  Stage 1 compiles keycloak/recaptcha-login (Maven runs inside Docker —
#  nothing to install locally). Stage 2 is the stock Keycloak image with the
#  plugin jar in /opt/keycloak/providers; start-dev picks it up automatically.
#
#  Build context: the repo root (see docker-compose.yml).
# =============================================================================

# ── Stage 1: build the plugin ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, so they are cached between builds
COPY keycloak/recaptcha-login/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY keycloak/recaptcha-login/src ./src
RUN mvn -B -q package -DskipTests

# ── Stage 2: Keycloak with the plugin ────────────────────────────────────────
FROM quay.io/keycloak/keycloak:24.0.4

COPY --from=build /build/target/recaptcha-login.jar /opt/keycloak/providers/recaptcha-login.jar
