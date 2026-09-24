$ErrorActionPreference = "Stop"

docker compose -p aerosense-mvp up -d postgres
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose -p aerosense-mvp --profile tools run --rm migrate
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Get-Content -Raw "scripts/migration-constraints-smoke.sql" |
    docker compose -p aerosense-mvp exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Output "Fresh schema migration and foreign-key/unique-constraint smoke checks passed."
