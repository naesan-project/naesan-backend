#!/bin/sh
set -eu

task_project_name="${NAESAN_COMPOSE_PROJECT_NAME:-naesan-compose-smoke}"
task_http_port="${NAESAN_HTTP_PORT:-18081}"
task_frontend_origin="http://localhost:${task_http_port}"
task_frontend_context="${NAESAN_FRONTEND_CONTEXT:-../naesan-frontend}"
task_failed=true

run_compose() {
  NAESAN_HTTP_PORT="$task_http_port" \
  NAESAN_FRONTEND_ORIGIN="$task_frontend_origin" \
  NAESAN_FRONTEND_CONTEXT="$task_frontend_context" \
    docker compose \
      --project-name "$task_project_name" \
      --env-file .env.example \
      --file compose.local.yaml \
      "$@"
}

clean_up() {
  if [ "$task_failed" = true ]; then
    run_compose logs --no-color application frontend || true
  fi
  run_compose down --volumes --remove-orphans || true
}

trap clean_up EXIT INT TERM

if [ ! -f "$task_frontend_context/package.json" ]; then
  echo "Frontend context를 찾을 수 없습니다: $task_frontend_context" >&2
  exit 1
fi

run_compose down --volumes --remove-orphans
run_compose up --build --detach --wait
NAESAN_E2E_BASE_URL="$task_frontend_origin" npm --prefix e2e run test:compose
task_failed=false
