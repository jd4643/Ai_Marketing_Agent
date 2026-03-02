#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v docker >/dev/null || { echo "docker is required"; exit 1; }
if [ ! -f infra/.env ]; then cp infra/.env.example infra/.env; fi
for s in api-gateway strategy-service creative-service analytics-service; do
  (cd "$s" && mvn -q -DskipTests package)
done
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build
cat <<'EOT'
Sample bootstrap SQL:
docker exec -it $(docker ps --filter name=postgres --format '{{.ID}}' | head -n1) psql -U postgres -d marketing_ai -c "INSERT INTO business_profile(id,business_name,industry,created_at,updated_at) VALUES ('11111111-1111-1111-1111-111111111111','Acme Jewelry','jewelry',NOW(),NOW());"

curl -X POST localhost:8080/strategy/generate -H 'Content-Type: application/json' -d '{"businessId":"11111111-1111-1111-1111-111111111111","objective":"sales","monthlyBudget":2000}'

curl -X POST localhost:8080/creative/generate -H 'Content-Type: application/json' -d '{"businessId":"11111111-1111-1111-1111-111111111111","platform":"meta","format":"image","objective":"sales"}'

curl 'localhost:8080/trends/latest?industry=jewelry&days=7&limit=20'

PowerShell equivalent for postgres seed:
$pg = docker ps --filter "name=postgres" --format "{{.ID}}" | Select-Object -First 1
docker exec -it $pg psql -U postgres -d marketing_ai -c "INSERT INTO business_profile(id,business_name,industry,created_at,updated_at) VALUES ('11111111-1111-1111-1111-111111111111','Acme Jewelry','jewelry',NOW(),NOW()) ON CONFLICT (id) DO NOTHING;"
EOT
