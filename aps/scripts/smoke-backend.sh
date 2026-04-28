#!/usr/bin/env bash
set -euo pipefail

APS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-31010}"
HEALTH_URL="http://127.0.0.1:${PORT}/mgr/jvs-aps/health"
LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/jvs-aps-smoke.XXXXXX")"
LOG_FILE="${LOG_DIR}/backend.log"
PID=""

cleanup() {
  if [[ -n "${PID}" ]] && kill -0 "${PID}" >/dev/null 2>&1; then
    kill "${PID}" >/dev/null 2>&1 || true
    wait "${PID}" >/dev/null 2>&1 || true
  fi
  rm -rf "${LOG_DIR}"
}

trap cleanup EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

require_cmd java
require_cmd mvn
require_cmd curl

cd "${APS_DIR}"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  mvn -Dmaven.repo.local=.m2 clean package -DskipTests
fi

java -jar jvs-aps-mgr/target/jvs-aps-mgr-1.0.jar --server.port="${PORT}" >"${LOG_FILE}" 2>&1 &
PID=$!

for _ in $(seq 1 60); do
  if curl --silent --fail "${HEALTH_URL}" >/dev/null 2>&1; then
    echo "backend smoke passed: ${HEALTH_URL}"
    exit 0
  fi
  if ! kill -0 "${PID}" >/dev/null 2>&1; then
    echo "backend exited before health check passed" >&2
    tail -n 80 "${LOG_FILE}" >&2 || true
    exit 1
  fi
  sleep 1
done

echo "backend health check timed out: ${HEALTH_URL}" >&2
tail -n 80 "${LOG_FILE}" >&2 || true
exit 1
