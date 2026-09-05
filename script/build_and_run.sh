#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-run}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

is_java_17() {
  [ -n "${1:-}" ] && [ -x "$1/bin/java" ] && "$1/bin/java" -version 2>&1 | head -n 1 | grep -q '"17\.'
}

if ! is_java_17 "${JAVA_HOME:-}"; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if ! is_java_17 "${JAVA_HOME:-}" && [ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
  JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi
if ! is_java_17 "${JAVA_HOME:-}" && [ -d "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]; then
  JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
fi
if ! is_java_17 "${JAVA_HOME:-}"; then
  echo "JDK 17 не найден. Установи Temurin 17 или задай JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

cd "$ROOT_DIR"

case "$MODE" in
  run)
    # Compose создаёт нативный .app и передаёт его LaunchServices macOS.
    exec ./gradlew :desktopApp:runDistributable
    ;;
  --debug|debug)
    exec ./gradlew :desktopApp:run
    ;;
  --logs|logs|--telemetry|telemetry)
    exec ./gradlew :desktopApp:run
    ;;
  --verify|verify)
    ./gradlew :desktopApp:createDistributable
    test -d "desktopApp/build/compose/binaries/main/app/ScheduleApp.app"
    ;;
  *)
    echo "usage: $0 [run|--debug|--logs|--telemetry|--verify]" >&2
    exit 2
    ;;
esac
