#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEVICE_SERIAL=""
MODEL_RUNS=""
FUNCTIONGEMMA_RUNS=""

usage() {
  echo "Usage: $0 [--serial <adb-serial>] [--model-runs <absolute-json-path>] [--functiongemma-runs <absolute-json-path>]"
}

while (($#)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      DEVICE_SERIAL="$2"
      shift 2
      ;;
    --model-runs)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      MODEL_RUNS="$2"
      shift 2
      ;;
    --functiongemma-runs)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      FUNCTIONGEMMA_RUNS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if ! java -version >/dev/null 2>&1; then
  ANDROID_STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -x "$ANDROID_STUDIO_JDK/bin/java" ]]; then
    export JAVA_HOME="$ANDROID_STUDIO_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
  else
    echo "A working JDK 17+ is required. Set JAVA_HOME before running this gate." >&2
    exit 2
  fi
fi

cd "$ROOT_DIR"

./gradlew \
  :core:domain:test \
  :core:assistant-contract:test \
  :app:testDebugUnitTest \
  :ai-evals:test \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:compileDebugAndroidTestKotlin \
  :app:compileDebugScreenshotTestKotlin

if [[ -n "$MODEL_RUNS" ]]; then
  [[ "$MODEL_RUNS" = /* ]] || {
    echo "--model-runs must be an absolute path." >&2
    exit 2
  }
  [[ -f "$MODEL_RUNS" ]] || {
    echo "Model-run artifact not found: $MODEL_RUNS" >&2
    exit 2
  }
  ./gradlew :ai-evals:test -PmenupilotModelRuns="$MODEL_RUNS"
fi

if [[ -n "$FUNCTIONGEMMA_RUNS" ]]; then
  [[ "$FUNCTIONGEMMA_RUNS" = /* ]] || {
    echo "--functiongemma-runs must be an absolute path." >&2
    exit 2
  }
  [[ -f "$FUNCTIONGEMMA_RUNS" ]] || {
    echo "FunctionGemma model-run artifact not found: $FUNCTIONGEMMA_RUNS" >&2
    exit 2
  }
  ./gradlew :ai-evals:test \
    -PmenupilotFunctionGemmaRuns="$FUNCTIONGEMMA_RUNS"
fi

if [[ -n "$DEVICE_SERIAL" ]]; then
  adb -s "$DEVICE_SERIAL" get-state >/dev/null
  ANDROID_SERIAL="$DEVICE_SERIAL" ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.menupilot.restaurant.MenuPilotGuestJourneyTest,com.menupilot.restaurant.feature.catalog.CatalogAdaptiveLayoutTest
fi

echo "MenuPilot host confidence gates passed."
