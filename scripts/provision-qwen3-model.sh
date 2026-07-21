#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE_NAME="com.menupilot.restaurant"
readonly MODEL_FILE="qwen3_0_6b_mixed_int4.litertlm"
readonly MODEL_SIZE="497664000"
readonly MODEL_SHA256="b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9"
readonly MODEL_REVISION="dd97997951bb15a2a71f539ba17f604707c0b11a"
readonly MODEL_URL="https://huggingface.co/litert-community/Qwen3-0.6B/resolve/${MODEL_REVISION}/${MODEL_FILE}?download=true"

if [[ "${1:-}" != "--serial" || -z "${2:-}" ]]; then
  echo "Usage: $0 --serial <adb-device-serial> [cache-directory]" >&2
  exit 2
fi

readonly DEVICE_SERIAL="$2"
readonly CACHE_DIRECTORY="${3:-.model-cache}"
readonly LOCAL_MODEL="${CACHE_DIRECTORY}/${MODEL_FILE}"
readonly STAGING_MODEL="/data/local/tmp/${MODEL_FILE}"
readonly ADB="${ANDROID_HOME:-${HOME}/Library/Android/sdk}/platform-tools/adb"

mkdir -p "${CACHE_DIRECTORY}"

if [[ ! -f "${LOCAL_MODEL}" ]]; then
  curl \
    --fail \
    --location \
    --retry 3 \
    --continue-at - \
    --output "${LOCAL_MODEL}.partial" \
    "${MODEL_URL}"
  mv "${LOCAL_MODEL}.partial" "${LOCAL_MODEL}"
fi

actual_size="$(wc -c < "${LOCAL_MODEL}" | tr -d ' ')"
if [[ "${actual_size}" != "${MODEL_SIZE}" ]]; then
  echo "Model size mismatch: expected ${MODEL_SIZE}, got ${actual_size}." >&2
  exit 1
fi

actual_sha="$(shasum -a 256 "${LOCAL_MODEL}" | awk '{print $1}')"
if [[ "${actual_sha}" != "${MODEL_SHA256}" ]]; then
  echo "Model checksum mismatch." >&2
  exit 1
fi

"${ADB}" -s "${DEVICE_SERIAL}" shell pm path "${PACKAGE_NAME}" >/dev/null
"${ADB}" -s "${DEVICE_SERIAL}" push "${LOCAL_MODEL}" "${STAGING_MODEL}"
"${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" mkdir -p files/models
"${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" \
  cp "${STAGING_MODEL}" "files/models/${MODEL_FILE}"
"${ADB}" -s "${DEVICE_SERIAL}" shell rm "${STAGING_MODEL}"

device_size="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" \
    stat -c %s "files/models/${MODEL_FILE}" | tr -d '\r'
)"
if [[ "${device_size}" != "${MODEL_SIZE}" ]]; then
  echo "Provisioned device model has the wrong size." >&2
  exit 1
fi

echo "Provisioned verified Qwen model for ${PACKAGE_NAME} on ${DEVICE_SERIAL}."
