#!/bin/zsh
# Load credentials at runtime. Never copy keys into YAML, command-line flags or logs.
set +x
aps_script_dir="${0:A:h}"
source "${APS_MINICPM_ENV_FILE:-$HOME/.zshrc}" >/dev/null 2>&1
set +x
set -euo pipefail

case "${APS_MINICPM_VARIANT:-new}" in
  new)
    aps_model_base="${MINICPM_NEW_BASE_URL:?Missing MINICPM_NEW_BASE_URL}"
    export APS_SMALL_MODEL_API_KEY="${MINICPM_NEW_API_KEY:?Missing MINICPM_NEW_API_KEY}"
    export APS_SMALL_MODEL_NAME="${MINICPM_NEW_MODEL:?Missing MINICPM_NEW_MODEL}"
    ;;
  instruct)
    aps_model_base="${MINICPM_BASE_URL:?Missing MINICPM_BASE_URL}"
    export APS_SMALL_MODEL_API_KEY="${MINICPM_API_KEY:?Missing MINICPM_API_KEY}"
    export APS_SMALL_MODEL_NAME="${MINICPM_INSTRUCT_MODEL_ID:?Missing MINICPM_INSTRUCT_MODEL_ID}"
    ;;
  *)
    print -u2 'APS_MINICPM_VARIANT must be new or instruct'
    exit 2
    ;;
esac
aps_model_base="${aps_model_base%/}"
if [[ -z "${APS_SMALL_MODEL_ENDPOINT:-}" ]]; then
  case "$aps_model_base" in
    */chat/completions) export APS_SMALL_MODEL_ENDPOINT="$aps_model_base" ;;
    */llm) export APS_SMALL_MODEL_ENDPOINT="$aps_model_base/openai/v1/chat/completions" ;;
    */v1) export APS_SMALL_MODEL_ENDPOINT="$aps_model_base/chat/completions" ;;
    *) export APS_SMALL_MODEL_ENDPOINT="$aps_model_base/v1/chat/completions" ;;
  esac
fi
export APS_E2E_SMALL_MODEL_ENABLED=true
export APS_SMALL_MODEL_ENABLED=true
export APS_SMALL_MODEL_RESPONSE_FORMAT="${APS_SMALL_MODEL_RESPONSE_FORMAT:-JSON_OBJECT}"
export APS_SMALL_MODEL_TIMEOUT_SECONDS="${APS_SMALL_MODEL_TIMEOUT_SECONDS:-60}"
export APS_SMALL_MODEL_MAX_CONTEXT_TASKS="${APS_SMALL_MODEL_MAX_CONTEXT_TASKS:-200}"
aps_backend_jar="${APS_BACKEND_JAR:-$aps_script_dir/../jvs-aps-mgr/target/jvs-aps-mgr-1.0.jar}"
aps_java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
aps_java_options=()
if [[ -n "${APS_MINICPM_TLS_PROTOCOLS:-}" ]]; then
  aps_java_options+=("-Djdk.tls.client.protocols=$APS_MINICPM_TLS_PROTOCOLS")
fi
if [[ "${APS_MINICPM_PREFER_IPV4:-false}" == true ]]; then
  aps_java_options+=("-Djava.net.preferIPv4Stack=true")
fi
print "Starting isolated APS with MiniCPM model: $APS_SMALL_MODEL_NAME"
exec "$aps_java" "${aps_java_options[@]}" -Xmx768m -jar "$aps_backend_jar" \
  --spring.config.additional-location="file:$aps_script_dir/e2e-local.yml"
