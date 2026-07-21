#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE_NAME="com.menupilot.restaurant"
readonly TEST_CLASS="com.menupilot.restaurant.assistant.OnDeviceQwenArtifactTest"
readonly DEVICE_ARTIFACT="files/evals/qwen3-0.6b-intent-runs.json"
readonly ADB="${ANDROID_HOME:-${HOME}/Library/Android/sdk}/platform-tools/adb"

if [[ "${1:-}" != "--serial" || -z "${2:-}" ]]; then
  echo "Usage: $0 --serial <adb-device-serial> [host-artifact-path]" >&2
  exit 2
fi

readonly DEVICE_SERIAL="$2"
readonly HOST_ARTIFACT="${3:-ai-evals/build/model-runs/qwen3-0.6b-intent-runs.json}"

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

mkdir -p "$(dirname "${HOST_ARTIFACT}")"

"${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" \
  rm -f "${DEVICE_ARTIFACT}"

ANDROID_SERIAL="${DEVICE_SERIAL}" ./gradlew \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="${TEST_CLASS}"

"${ADB}" -s "${DEVICE_SERIAL}" exec-out run-as "${PACKAGE_NAME}" \
  cat "${DEVICE_ARTIFACT}" > "${HOST_ARTIFACT}"

if [[ ! -s "${HOST_ARTIFACT}" ]]; then
  echo "The device eval artifact is empty; qualification cannot continue." >&2
  exit 1
fi

./gradlew :ai-evals:test -PmenupilotModelRuns="$(cd "$(dirname "${HOST_ARTIFACT}")" && pwd)/$(basename "${HOST_ARTIFACT}")"

echo "Dokimos evaluated the on-device artifact at ${HOST_ARTIFACT}."
