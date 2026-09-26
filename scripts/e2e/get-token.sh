#!/usr/bin/env bash
# Prints a BlueSnap sandbox payment-fields token for the bridge self-test.
# Same call the native SDKs' integration tests make:
#   POST /services/2/payment-fields-tokens -> 201, token = last segment of Location
# Needs sandbox API credentials in BS_API_USER / BS_API_PASSWORD (the secrets the
# native SDK CI already uses). BS_DOMAIN overrides the sandbox host.
set -euo pipefail

: "${BS_API_USER:?set BS_API_USER to a BlueSnap sandbox API user}"
: "${BS_API_PASSWORD:?set BS_API_PASSWORD to its password}"
url="${BS_DOMAIN:-https://sandbox.bluesnap.com}/services/2/payment-fields-tokens"

# curl config strings need \ and " escaped
config_escape() {
  local s="${1//\\/\\\\}"
  printf '%s' "${s//\"/\\\"}"
}

# credentials go through curl's stdin config, not argv, so they don't show in `ps`
headers=$(printf 'user = "%s:%s"\n' "$(config_escape "$BS_API_USER")" "$(config_escape "$BS_API_PASSWORD")" |
  curl -sS -K - -X POST -H 'Content-Type: text/xml' -D - -o /dev/null "$url" | tr -d '\r')

status=$(printf '%s\n' "$headers" | awk 'NR == 1 { print $2 }')
location=$(printf '%s\n' "$headers" | awk 'tolower($1) == "location:" { print $2 }')

if [[ "$status" != 2* || -z "$location" ]]; then
  echo "get-token: POST $url returned HTTP ${status:-none}; check BS_API_USER/BS_API_PASSWORD" >&2
  exit 1
fi
echo "${location##*/}"
