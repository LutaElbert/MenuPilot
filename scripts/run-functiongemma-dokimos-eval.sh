#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  echo "Usage: $0 --artifact <absolute-functiongemma-json-path>" >&2
}

if [[ "${1:-}" != "--artifact" || -z "${2:-}" || $# -ne 2 ]]; then
  usage
  exit 2
fi

readonly ARTIFACT_PATH="$2"
if [[ "${ARTIFACT_PATH}" != /* ]]; then
  echo "--artifact must be an absolute path." >&2
  exit 2
fi
if [[ ! -s "${ARTIFACT_PATH}" ]]; then
  echo "FunctionGemma artifact is missing or empty: ${ARTIFACT_PATH}" >&2
  exit 2
fi

if ! java -version >/dev/null 2>&1; then
  readonly ANDROID_STUDIO_JDK="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -x "${ANDROID_STUDIO_JDK}/bin/java" ]]; then
    export JAVA_HOME="${ANDROID_STUDIO_JDK}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  else
    echo "A working JDK 17+ is required. Set JAVA_HOME before running this gate." >&2
    exit 2
  fi
fi

cd "${ROOT_DIR}"
./gradlew :ai-evals:test \
  -PmenupilotFunctionGemmaRuns="${ARTIFACT_PATH}"

echo "Dokimos evaluated the complete FunctionGemma artifact at ${ARTIFACT_PATH}."
