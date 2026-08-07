#!/bin/sh
set -eu

usage() {
  echo "Usage: $0 BASE_URL PHASE OUTPUT [POLL_INTERVAL_SECONDS] [TIMEOUT_SECONDS]" >&2
  echo "PHASE: baseline, outage, recovery" >&2
}

if [ "$#" -lt 3 ] || [ "$#" -gt 5 ]; then
  usage
  exit 64
fi

task_base_url="${1%/}"
task_phase="$2"
task_output_path="$3"
task_poll_interval="${4:-5}"
task_timeout="${5:-180}"

case "$task_base_url" in
  https://*|http://127.0.0.1:*|http://localhost:*) ;;
  *)
    echo "HTTPS URL 또는 로컬 HTTP URL만 사용할 수 있습니다." >&2
    exit 64
    ;;
esac

case "$task_base_url" in
  *'?'*|*'#'*|*'@'*)
    echo "BASE_URL에는 query, fragment, user-info를 포함할 수 없습니다." >&2
    exit 64
    ;;
esac

case "$task_phase" in
  baseline|outage|recovery) ;;
  *)
    echo "PHASE는 baseline, outage, recovery 중 하나여야 합니다." >&2
    exit 64
    ;;
esac

case "$task_poll_interval" in
  ''|*[!0-9]*|0)
    echo "POLL_INTERVAL_SECONDS는 양의 정수여야 합니다." >&2
    exit 64
    ;;
esac

case "$task_timeout" in
  ''|*[!0-9]*|0)
    echo "TIMEOUT_SECONDS는 양의 정수여야 합니다." >&2
    exit 64
    ;;
esac

if [ -e "$task_output_path" ]; then
  echo "기존 측정 파일을 덮어쓰지 않습니다: $task_output_path" >&2
  exit 73
fi

task_output_dir="$(dirname "$task_output_path")"
if [ ! -d "$task_output_dir" ]; then
  echo "측정 파일의 상위 디렉터리가 없습니다: $task_output_dir" >&2
  exit 73
fi

umask 077
task_temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/naesan-web3-readiness.XXXXXX")"
task_liveness_body="$task_temp_dir/liveness.json"
task_readiness_body="$task_temp_dir/readiness.json"

clean_up() {
  rm -rf "$task_temp_dir"
}

trap clean_up EXIT INT TERM

read_endpoint() {
  task_endpoint="$1"
  task_body_path="$2"
  task_http_status="$(curl \
    --max-time 10 \
    --silent \
    --output "$task_body_path" \
    --write-out '%{http_code}' \
    "$task_base_url$task_endpoint")" || task_http_status="000"
  case "$task_http_status" in
    ''|*[!0-9]*) task_http_status="000" ;;
  esac
  task_actuator_status="$(sed -n \
    's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    "$task_body_path" | head -n 1)"
  case "$task_actuator_status" in
    UP|DOWN|OUT_OF_SERVICE|UNKNOWN) ;;
    *) task_actuator_status="UNAVAILABLE" ;;
  esac
  printf '%s|%s\n' "$task_http_status" "$task_actuator_status"
}

matches_phase() {
  if [ "$task_liveness_http" != "200" ] \
      || [ "$task_liveness_status" != "UP" ]; then
    return 1
  fi
  case "$task_phase" in
    baseline|recovery)
      [ "$task_readiness_http" = "200" ] \
        && [ "$task_readiness_status" = "UP" ]
      ;;
    outage)
      [ "$task_readiness_http" = "503" ] \
        && { [ "$task_readiness_status" = "DOWN" ] \
          || [ "$task_readiness_status" = "OUT_OF_SERVICE" ]; }
      ;;
  esac
}

: > "$task_output_path"
task_started_at="$(date -u +%s)"

while true; do
  task_now="$(date -u +%s)"
  task_elapsed_seconds=$((task_now - task_started_at))
  task_observed_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

  task_liveness_result="$(read_endpoint /health "$task_liveness_body")"
  task_liveness_http="${task_liveness_result%%|*}"
  task_liveness_status="${task_liveness_result#*|}"
  task_readiness_result="$(read_endpoint /ready "$task_readiness_body")"
  task_readiness_http="${task_readiness_result%%|*}"
  task_readiness_status="${task_readiness_result#*|}"

  printf '%s\n' \
    "{\"observedAt\":\"$task_observed_at\",\"elapsedSeconds\":$task_elapsed_seconds,\"phase\":\"$task_phase\",\"liveness\":{\"httpStatus\":$task_liveness_http,\"status\":\"$task_liveness_status\"},\"readiness\":{\"httpStatus\":$task_readiness_http,\"status\":\"$task_readiness_status\"}}" \
    >> "$task_output_path"

  if matches_phase; then
    echo "$task_phase 상태를 ${task_elapsed_seconds}초 만에 확인했습니다."
    exit 0
  fi
  if [ "$task_elapsed_seconds" -ge "$task_timeout" ]; then
    echo "$task_phase 상태를 ${task_timeout}초 안에 확인하지 못했습니다." >&2
    exit 1
  fi
  sleep "$task_poll_interval"
done
