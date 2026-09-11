.PHONY: smoke gateway-test classifier-test dev down

smoke:
	python scripts/smoke.py

gateway-test:
	$${MAVEN_CMD:-mvn} -f gateway/pom.xml test -B

classifier-test:
	python -m pytest -q classifier-svc/tests

dev:
	docker compose up -d
	@echo "Arbiter local dependencies are running. Run 'make smoke' for the offline contract check."

down:
	docker compose down
