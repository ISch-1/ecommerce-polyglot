# Convenience targets. Everything works without them via `docker compose` / `./mvnw`.

.PHONY: help up up-full down clean logs build test it run smoke seed

help:
	@echo "Targets:"
	@echo "  up        Start the catalog stack (postgres, redis, kafka, observability)"
	@echo "  up-full   Start every polyglot service (adds mongo, neo4j, cassandra)"
	@echo "  down      Stop containers (keeps volumes)"
	@echo "  clean     Stop and remove volumes"
	@echo "  logs      Tail logs of all running services"
	@echo "  build     Build the app jar"
	@echo "  test      Run unit tests"
	@echo "  it        Run integration tests (Testcontainers)"
	@echo "  run       Run the app with the dev profile"
	@echo "  smoke     Run the k6 smoke script against http://localhost:8080"
	@echo "  seed      Seed the catalog with sample products (curl)"

up:
	docker compose up -d
	@echo "→ Grafana:    http://localhost:3000  (anonymous viewer)"
	@echo "→ Prometheus: http://localhost:9090"
	@echo "→ Jaeger:     http://localhost:16686"

up-full:
	docker compose --profile full up -d

down:
	docker compose down

clean:
	docker compose down -v

logs:
	docker compose logs -f --tail=100

build:
	./mvnw -q -DskipTests package

test:
	./mvnw -q test

it:
	./mvnw -q verify

run:
	./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

smoke:
	k6 run load/catalog-smoke.js

seed:
	@for i in 1 2 3 4 5; do \
	  curl -sf -X POST http://localhost:8080/api/v1/products \
	    -H 'Content-Type: application/json' \
	    -d "{\"name\":\"Sample $$i\",\"description\":\"seeded\",\"price\":$$((i*100)).00,\"stock\":100}" | jq . ; \
	done
