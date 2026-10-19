.PHONY: smoke gateway-test gateway-pgvector classifier-test cache-eval dev down

smoke:
	python scripts/smoke.py

gateway-test:
	$${MAVEN_CMD:-mvn} -f gateway/pom.xml test -B

gateway-pgvector:
	$${MAVEN_CMD:-mvn} -f gateway/pom.xml spring-boot:run "-Dspring-boot.run.profiles=pgvector"

classifier-test:
	python -m pytest -q classifier-svc/tests

cache-eval:
	python eval/cache_poisoning.py

dev:
	docker compose up -d
	@echo "Arbiter local dependencies are running. Run 'make smoke' for the offline contract check."

down:
	docker compose down
