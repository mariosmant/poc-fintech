# =============================================================================
# Docker Buildx Bake — build both image variants in one shot.
#
# Usage:
#   docker buildx bake                       # builds both variants
#   docker buildx bake resource-server       # OAuth2 Resource Server (default)
#   docker buildx bake bff                   # OAuth2 Login / BFF profile
#   docker buildx bake --push                # push both to registry (set REGISTRY)
#
# Tag prefix is configurable:
#   REGISTRY=ghcr.io/mariosmant TAG=1.0.0 docker buildx bake --push
# =============================================================================

variable "REGISTRY" {
  default = "poc-fintech"
}

variable "TAG" {
  default = "latest"
}

group "default" {
  targets = ["resource-server", "bff"]
}

target "_common" {
  context    = "."
  dockerfile = "Dockerfile"
  platforms  = ["linux/amd64", "linux/arm64"]
}

target "resource-server" {
  inherits = ["_common"]
  args = {
    SPRING_PROFILES_ACTIVE = ""
    APP_VARIANT            = "resource-server"
  }
  tags = [
    "${REGISTRY}:${TAG}",
    "${REGISTRY}:${TAG}-resource-server",
  ]
}

target "bff" {
  inherits = ["_common"]
  args = {
    SPRING_PROFILES_ACTIVE = "bff"
    APP_VARIANT            = "bff"
  }
  tags = [
    "${REGISTRY}:${TAG}-bff",
  ]
}
