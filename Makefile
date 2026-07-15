PNPM_BIN := $(shell command -v pnpm 2>/dev/null)
COREPACK_BIN := $(shell command -v corepack 2>/dev/null)

PNPM ?= $(if $(PNPM_BIN),$(PNPM_BIN),$(if $(COREPACK_BIN),$(COREPACK_BIN) pnpm,pnpm))
MAVEN ?= ./scripts/use-java-21.sh mvn
MAVEN_REPO ?= .m2/repository
DEV_BACKEND_PORT ?= 8081

.PHONY: test backend-test frontend-test frontend-build dev-backend dev-frontend docker-up docker-down smoke

test: backend-test frontend-test frontend-build

backend-test:
	$(MAVEN) -Dmaven.repo.local=$(MAVEN_REPO) -f backend/pom.xml test

frontend-test:
	$(PNPM) --dir frontend test

frontend-build:
	$(PNPM) --dir frontend build

dev-backend:
	SERVER_PORT=$(DEV_BACKEND_PORT) $(MAVEN) -Dmaven.repo.local=$(MAVEN_REPO) -f backend/pom.xml spring-boot:run

dev-frontend:
	$(PNPM) --dir frontend dev

docker-up:
	docker compose up --build

docker-down:
	docker compose down

smoke:
	./scripts/smoke-test.sh
