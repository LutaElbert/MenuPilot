#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PACKAGE_NAME="com.menupilot.restaurant"
readonly TEST_PACKAGE_NAME="${PACKAGE_NAME}.test"
readonly TEST_CLASS="com.menupilot.restaurant.assistant.OnDeviceFunctionGemmaArtifactTest"
readonly TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
readonly MODEL_FILE="files/models/mobile-actions_q8_ekv1024.litertlm"
readonly DEVICE_ARTIFACT="files/evals/functiongemma-route-runs.json"
readonly APP_APK="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
readonly TEST_APK="${ROOT_DIR}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
readonly ADB="${ANDROID_HOME:-${HOME}/Library/Android/sdk}/platform-tools/adb"

if [[ "${1:-}" != "--serial" || -z "${2:-}" || $# -gt 3 ]]; then
  echo "Usage: $0 --serial <adb-device-serial> [host-artifact-path]" >&2
  exit 2
fi

readonly DEVICE_SERIAL="$2"
readonly HOST_ARTIFACT="${3:-${ROOT_DIR}/ai-evals/build/model-runs/functiongemma-route-runs.json}"

if ! "${ADB}" -s "${DEVICE_SERIAL}" get-state >/dev/null 2>&1; then
  echo "No reachable adb device with serial ${DEVICE_SERIAL}." >&2
  exit 2
fi

readonly DEVICE_QEMU="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell getprop ro.kernel.qemu | tr -d '\r'
)"
readonly DEVICE_FINGERPRINT="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell getprop ro.build.fingerprint | tr -d '\r'
)"
readonly DEVICE_MODEL="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell getprop ro.product.model | tr -d '\r'
)"
readonly DEVICE_PRODUCT="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell getprop ro.product.name | tr -d '\r'
)"
readonly DEVICE_HARDWARE="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell getprop ro.hardware | tr -d '\r'
)"
readonly EMULATOR_MARKERS="$(
  printf '%s\n' \
    "${DEVICE_SERIAL}" \
    "${DEVICE_FINGERPRINT}" \
    "${DEVICE_MODEL}" \
    "${DEVICE_PRODUCT}" \
    "${DEVICE_HARDWARE}" |
    tr '[:upper:]' '[:lower:]'
)"
if [[ "${DEVICE_QEMU}" == "1" ]] ||
  grep -Eq \
    '(^emulator-)|generic|emulator|sdk_gphone|google_sdk|goldfish|ranchu|vbox' \
    <<<"${EMULATOR_MARKERS}"; then
  echo "Physical FunctionGemma qualification rejects emulator ${DEVICE_SERIAL}." >&2
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
mkdir -p "$(dirname "${HOST_ARTIFACT}")"
readonly HOST_ARTIFACT_TEMP="${HOST_ARTIFACT}.tmp.$$"
trap 'rm -f "${HOST_ARTIFACT_TEMP}"' EXIT

HOST_HARNESS_REVISION=""
HOST_SOURCE_TREE_DIRTY=""
if HOST_HARNESS_REVISION="$(git rev-parse --verify HEAD 2>/dev/null)"; then
  HOST_SOURCE_TREE_DIRTY="false"
  if [[ -n "$(git status --porcelain=v1 --untracked-files=normal)" ]]; then
    HOST_SOURCE_TREE_DIRTY="true"
  fi
else
  HOST_HARNESS_REVISION=""
fi

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

if [[ ! -s "${APP_APK}" ]]; then
  echo "Debug APK was not produced at ${APP_APK}." >&2
  exit 1
fi
if [[ ! -s "${TEST_APK}" ]]; then
  echo "Android-test APK was not produced at ${TEST_APK}." >&2
  exit 1
fi

# Direct installs preserve the main package and its app-private model/evidence directory.
# Gradle's connectedAndroidTest lifecycle may uninstall packages before evidence extraction.
"${ADB}" -s "${DEVICE_SERIAL}" install -r -t "${APP_APK}"
"${ADB}" -s "${DEVICE_SERIAL}" install -r -t "${TEST_APK}"

if ! "${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" \
  test -f "${MODEL_FILE}"; then
  echo "The pinned FunctionGemma model is not provisioned at ${MODEL_FILE}." >&2
  echo "Run scripts/provision-functiongemma-model.sh --serial ${DEVICE_SERIAL} first." >&2
  exit 1
fi

"${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" \
  rm -f "${DEVICE_ARTIFACT}"

INSTRUMENTATION_ARGUMENTS=(
  shell am instrument
  -w
  -r
  -e class "${TEST_CLASS}"
)
if [[ -n "${HOST_HARNESS_REVISION}" ]]; then
  INSTRUMENTATION_ARGUMENTS+=(
    -e hostHarnessRevision "${HOST_HARNESS_REVISION}"
    -e hostSourceTreeDirty "${HOST_SOURCE_TREE_DIRTY}"
  )
fi
INSTRUMENTATION_ARGUMENTS+=(
  "${TEST_PACKAGE_NAME}/${TEST_RUNNER}"
)

if ! INSTRUMENTATION_OUTPUT="$(
  "${ADB}" -s "${DEVICE_SERIAL}" "${INSTRUMENTATION_ARGUMENTS[@]}" 2>&1
)"; then
  printf '%s\n' "${INSTRUMENTATION_OUTPUT}" >&2
  echo "FunctionGemma instrumentation command failed." >&2
  exit 1
fi
INSTRUMENTATION_OUTPUT="$(printf '%s' "${INSTRUMENTATION_OUTPUT}" | tr -d '\r')"
printf '%s\n' "${INSTRUMENTATION_OUTPUT}"
if grep -Eqi \
  'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=|INSTRUMENTATION_ABORTED' \
  <<<"${INSTRUMENTATION_OUTPUT}" ||
  ! grep -Eq '^OK \([1-9][0-9]* tests?\)$' <<<"${INSTRUMENTATION_OUTPUT}"; then
  echo "FunctionGemma instrumentation did not report a successful test run." >&2
  exit 1
fi

if ! "${ADB}" -s "${DEVICE_SERIAL}" exec-out run-as "${PACKAGE_NAME}" \
  cat "${DEVICE_ARTIFACT}" > "${HOST_ARTIFACT_TEMP}"; then
  echo "Could not extract ${DEVICE_ARTIFACT} before package teardown." >&2
  exit 1
fi

if [[ ! -s "${HOST_ARTIFACT_TEMP}" ]]; then
  echo "The FunctionGemma device artifact is empty; qualification cannot continue." >&2
  exit 1
fi
mv "${HOST_ARTIFACT_TEMP}" "${HOST_ARTIFACT}"

"${ROOT_DIR}/scripts/run-functiongemma-dokimos-eval.sh" \
  --artifact "$(cd "$(dirname "${HOST_ARTIFACT}")" && pwd)/$(basename "${HOST_ARTIFACT}")"

echo "Exported and evaluated FunctionGemma device evidence at ${HOST_ARTIFACT}."
