#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLEW="$ROOT_DIR/gradlew"

usage() {
  cat <<'EOF'
Usage:
  ./run-kmp.sh <target>

Targets:
  server           Run Ktor server (:server:run)
  web-wasm         Run Compose web Wasm dev server (:composeApp:wasmJsBrowserDevelopmentRun)
  web-js           Run Compose web JS dev server (:composeApp:jsBrowserDevelopmentRun)
  android-build    Build Android debug APK (:composeApp:assembleDebug)
  android-install  Install Android debug APK (:composeApp:installDebug)
EOF
}

ensure_java() {
  if command -v java >/dev/null 2>&1; then
    return 0
  fi

  if [[ -z "${JAVA_HOME:-}" && -x "/usr/lib/jvm/java-17-openjdk-amd64/bin/java" ]]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi

  if ! command -v java >/dev/null 2>&1; then
    echo "Java not found. Install JDK 17 and set JAVA_HOME."
    echo "Example (Ubuntu): sudo apt install -y openjdk-17-jdk"
    exit 1
  fi
}

ensure_gradlew() {
  if [[ ! -x "$GRADLEW" ]]; then
    chmod +x "$GRADLEW"
  fi
}

main() {
  if [[ $# -ne 1 ]]; then
    usage
    exit 1
  fi

  ensure_java
  ensure_gradlew

  case "$1" in
    server)
      "$GRADLEW" :server:run
      ;;
    web-wasm)
      "$GRADLEW" :composeApp:wasmJsBrowserDevelopmentRun
      ;;
    web-js)
      "$GRADLEW" :composeApp:jsBrowserDevelopmentRun
      ;;
    android-build)
      "$GRADLEW" :composeApp:assembleDebug
      ;;
    android-install)
      "$GRADLEW" :composeApp:installDebug
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
