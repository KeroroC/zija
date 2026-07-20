ENV_FILE ?= .env

.PHONY: verify-layout backend-test backend-build frontend-test frontend-build verify dev-db dev-backend dev-frontend compose-smoke e2e-smoke recover-owner clean

verify-layout:
	./scripts/verify-layout.sh

backend-test:
	cd backend && ./mvnw -q test

backend-build:
	cd backend && ./mvnw -q -DskipTests package

frontend-test:
	npm --prefix frontend test

frontend-build:
	npm --prefix frontend run build

verify: verify-layout backend-test frontend-test backend-build frontend-build
	git diff --check

dev-db:
	docker compose --env-file $(ENV_FILE) up -d postgres

dev-backend:
	set -a; . ./$(ENV_FILE); set +a; cd backend && ./mvnw spring-boot:run

dev-frontend:
	npm --prefix frontend run dev

compose-smoke:
	./scripts/compose-smoke.sh

e2e-smoke:
	./scripts/e2e-smoke.sh

## 生成所有者恢复链接（在运行中的容器内执行非 Web 命令）
recover-owner:
	docker compose exec app java -jar /app/zija.jar \
		--spring.main.web-application-type=none \
		--zija.command=recover-owner

clean:
	cd backend && ./mvnw -q clean
	rm -rf frontend/dist frontend/coverage frontend/test-results frontend/playwright-report
