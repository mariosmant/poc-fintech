# =============================================================================
# Hardened multi-stage container image.
#
# Stage 1 (builder)  — eclipse-temurin:25-jdk + Maven wrapper, builds the boot
#                       fat-jar. Layered with --layers=ALWAYS so the runtime
#                       stage can split the jar into cacheable layers.
# Stage 2 (extractor) — eclipse-temurin:25-jdk, runs Spring Boot's layertools
#                       to produce dependencies / spring-boot-loader / snapshot-
#                       dependencies / application directories.
# Stage 3 (runtime)   — gcr.io/distroless/java25-debian12:nonroot. No shell,
#                       no package manager, no busybox. Runs as UID 65532
#                       ('nonroot'); read-only root filesystem at runtime
#                       (set --read-only --tmpfs /tmp on `docker run`).
#
#   CIS Docker Benchmark v1.6 §4.1, §4.5, §4.6, §5.12, §5.31.
#   PCI DSS v4.0.1 §6.4.1 — separate, hardened public-facing application
#                            environment.
#   OWASP Container Security — defence-in-depth layering.
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — Builder
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

# Cache Maven dependencies independently of source changes.
COPY pom.xml ./
COPY poc-fintech-domain/pom.xml          poc-fintech-domain/pom.xml
COPY poc-fintech-application/pom.xml     poc-fintech-application/pom.xml
COPY poc-fintech-infrastructure/pom.xml  poc-fintech-infrastructure/pom.xml
COPY poc-fintech-boot/pom.xml            poc-fintech-boot/pom.xml

# Pre-fetch dependencies into the builder layer cache.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -ntp dependency:go-offline -DskipTests || true

# Now copy sources and build.
COPY poc-fintech-domain          poc-fintech-domain
COPY poc-fintech-application     poc-fintech-application
COPY poc-fintech-infrastructure  poc-fintech-infrastructure
COPY poc-fintech-boot            poc-fintech-boot

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests -pl poc-fintech-boot -am package \
        -Dspring-boot.repackage.layers.enabled=true

# -----------------------------------------------------------------------------
# Stage 2 — Layer extractor
# -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS extractor
WORKDIR /app
COPY --from=builder /workspace/poc-fintech-boot/target/poc-fintech-boot-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher

# -----------------------------------------------------------------------------
# Stage 3 — Distroless runtime (nonroot, no shell, no package manager)
# -----------------------------------------------------------------------------
FROM gcr.io/distroless/java25-debian12:nonroot AS runtime

# ── Build-time variant selection ────────────────────────────────────────────
# Baked into the image so the entrypoint picks up the right Spring profile.
# Override at runtime with `-e SPRING_PROFILES_ACTIVE=...` if needed.
ARG SPRING_PROFILES_ACTIVE=""
ARG APP_VARIANT="resource-server"

# OCI image labels — surface the variant in `docker inspect` and registries.
# License is proprietary / all rights reserved. SPDX expresses non-standard
# licenses via the `LicenseRef-` prefix (SPDX Spec §10.1); the human-readable
# notice ships in the LICENSE file at the repository root.
LABEL org.opencontainers.image.title="poc-fintech" \
      org.opencontainers.image.description="POC Fintech — hardened distroless image (variant=${APP_VARIANT})" \
      org.opencontainers.image.source="https://github.com/mariosmant/poc-fintech" \
      org.opencontainers.image.vendor="Marios Mantratzis" \
      org.opencontainers.image.authors="Marios Mantratzis" \
      org.opencontainers.image.licenses="LicenseRef-Proprietary" \
      com.mariosmant.fintech.copyright="Copyright (c) 2024-2026 Marios Mantratzis. All rights reserved." \
      com.mariosmant.fintech.variant="${APP_VARIANT}" \
      com.mariosmant.fintech.spring-profiles="${SPRING_PROFILES_ACTIVE}"

# Copy layered artefacts in cache-friendly order (least → most volatile).
WORKDIR /app
COPY --from=extractor --chown=nonroot:nonroot /app/poc-fintech-boot/dependencies/          ./
COPY --from=extractor --chown=nonroot:nonroot /app/poc-fintech-boot/spring-boot-loader/    ./
COPY --from=extractor --chown=nonroot:nonroot /app/poc-fintech-boot/snapshot-dependencies/ ./
COPY --from=extractor --chown=nonroot:nonroot /app/poc-fintech-boot/application/           ./

USER nonroot:nonroot

EXPOSE 8080
EXPOSE 8081

# Distroless ships tini-style PID-1 handling already; the org.springframework.boot
# loader entry point is the documented launcher for layered jars.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Dnetworkaddress.cache.ttl=30 -Dnetworkaddress.cache.negative.ttl=10" \
    SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE}" \
    APP_VARIANT="${APP_VARIANT}"

# Healthcheck reuses the actuator surface already configured under /actuator.
# (Distroless has no curl/wget; the JVM-side liveness probe is delegated to
#  the orchestrator's HTTP probe — Kubernetes / Compose / etc.)

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

