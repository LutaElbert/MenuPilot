#!/usr/bin/env bash
set -euo pipefail

readonly PACKAGE_NAME="com.menupilot.restaurant"
readonly MODEL_ID="ElLabs/mobile-actions-runtime"
readonly MODEL_FILE="mobile-actions_q8_ekv1024.litertlm"
readonly MODEL_SIZE="285561008"
readonly MODEL_SHA256="9a9c590143b6a88ecaf074c574c2bfe311ba4160559b9e105fa4ac4ef0b57aef"
readonly MODEL_REVISION="43221886a868ef2459506791b56e4db8237a23cf"
readonly MODEL_URL="https://huggingface.co/${MODEL_ID}/resolve/${MODEL_REVISION}/${MODEL_FILE}?download=true"

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

device_sha="$(
  "${ADB}" -s "${DEVICE_SERIAL}" shell run-as "${PACKAGE_NAME}" \
    sha256sum "files/models/${MODEL_FILE}" | awk '{print $1}' | tr -d '\r'
)"
if [[ "${device_sha}" != "${MODEL_SHA256}" ]]; then
  echo "Provisioned device model has the wrong checksum." >&2
  exit 1
fi

echo "Provisioned verified FunctionGemma model for ${PACKAGE_NAME} on ${DEVICE_SERIAL}."
