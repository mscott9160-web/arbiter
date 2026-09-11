.PHONY: smoke dev

smoke:
	python scripts/smoke.py

dev:
	docker compose up -d
	@echo "Arbiter local dependencies are running. Run 'make smoke' for the offline contract check."

down:
	docker compose down
