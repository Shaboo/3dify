.DEFAULT_GOAL := help

GRADLE ?= ./gradlew
COMPOSE ?= docker compose

.PHONY: help format lint build test check clean run docker docker-up docker-down docker-logs docker-status migrate migrate-info jooq

help: ## Show available commands
	@awk 'BEGIN {FS = ":.*## "; printf "Commands:\n"} /^[a-zA-Z_-]+:.*## / {printf "  make %-15s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

format: ## Format Kotlin sources and Gradle scripts with ktlint
	$(GRADLE) spotlessApply

lint: ## Check Kotlin formatting without changing files
	$(GRADLE) spotlessCheck

build: ## Build and run all checks and tests
	$(GRADLE) build

test: ## Run all tests, including architecture and integration tests
	$(GRADLE) test

check: ## Run formatting checks and tests
	$(GRADLE) check

clean: ## Remove build output
	$(GRADLE) clean

run: ## Run the Spring Boot application
	$(GRADLE) bootRun

docker: docker-up ## Start local Docker services and wait for readiness

docker-up: ## Start local Docker services and wait for readiness
	$(COMPOSE) up -d --wait --wait-timeout 60

docker-down: ## Stop local Docker services, preserving volumes
	$(COMPOSE) down

docker-logs: ## Follow local Docker service logs
	$(COMPOSE) logs -f

docker-status: ## Show local Docker service status
	$(COMPOSE) ps

migrate: ## Apply Flyway migrations to the local database
	$(GRADLE) flywayMigrate

migrate-info: ## Show applied and pending Flyway migrations
	$(GRADLE) flywayInfo

jooq: ## Apply migrations, then generate jOOQ Kotlin sources
	$(GRADLE) flywayMigrate
	$(GRADLE) jooqCodegen
