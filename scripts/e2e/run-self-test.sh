#!/usr/bin/env bash
# Runs the end-to-end bridge self-test (e2e/bridge-self-test.yaml) with Maestro
# against the example app on a booted iOS simulator or Android emulator.
# Usage: scripts/e2e/run-self-test.sh ios|android
# Needs: the example app installed, Maestro, BS_API_USER / BS_API_PASSWORD.
set -euo pipefail

platform="${1:-}"
case "$platform" in
  ios) app_id="org.reactjs.native.example.BluesnapSdkReactNativeExample" ;;
  android) app_id="com.bluesnapsdkreactnativeexample" ;;
  *) echo "usage: $0 ios|android" >&2; exit 2 ;;
esac

if ! command -v maestro >/dev/null; then
  echo "Maestro is not installed: curl -Ls https://get.maestro.mobile.dev | bash" >&2
  exit 1
fi

root="$(cd "$(dirname "$0")/../.." && pwd)"
report="${E2E_REPORT:-$root/build/e2e/bridge-self-test-$platform.xml}"
mkdir -p "$(dirname "$report")"

token="$("$root/scripts/e2e/get-token.sh")"
maestro test \
  -e APP_ID="$app_id" \
  -e BS_TOKEN="$token" \
  --format junit --output "$report" \
  "$root/e2e/bridge-self-test.yaml"
