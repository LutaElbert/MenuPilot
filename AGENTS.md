# MenuPilot Agent Setup Notes

This repository contains the MenuPilot Android tablet concierge app. It was
uploaded directly to `main` in `LutaElbert/MenuPilot`; there was no feature
branch or pull request to merge because the remote repository was empty at the
time of the initial push.

## Local Environment

- Use the Android Studio bundled JDK when running Gradle on this machine:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

- Android package: `com.menupilot.restaurant`
- Current Android Gradle Plugin target is pinned to the latest supported
  project version in `gradle/libs.versions.toml`.
- Do not commit generated build outputs or local machine state.

## Intentionally Untracked Files

The following paths are intentionally ignored and were not pushed to GitHub:

- `.idea/` - Android Studio local workspace and device state.
- `.agents/` - local Codex/Android skill cache copied into the workspace.
- `.model-cache/` - downloaded LiteRT model binaries.
- `*.litertlm` - large on-device model binaries.
- `local.properties` - local Android SDK path and machine settings.
- `build/`, `**/build/`, `.gradle/` - generated Gradle artifacts.

If a future agent needs these files, recreate them locally instead of expecting
them from the repository.

## Build And Verification

Run the full host confidence gate:

```bash
./scripts/verify-pilot-confidence.sh
```

Run the connected-device subset on a physical Android device:

```bash
./scripts/verify-pilot-confidence.sh --serial <adb-device-serial>
```

The project has also been tested on a Xiaomi physical device. If Xiaomi blocks
APK installation with `INSTALL_FAILED_USER_RESTRICTED`, unlock the device and
approve USB/app installation prompts, then rerun the connected tests.

## On-Device Model Setup

The app does not bundle model binaries. Provision models only after installing
the debug app on a physical device.

Provision Qwen3-0.6B:

```bash
./scripts/provision-qwen3-model.sh --serial <adb-device-serial>
```

Provision FunctionGemma / mobile-actions-runtime:

```bash
./scripts/provision-functiongemma-model.sh --serial <adb-device-serial>
```

Both scripts download pinned Hugging Face revisions into `.model-cache/`, verify
size and SHA-256, then copy the model into app-private storage with `run-as`.
These binaries are intentionally excluded from git.

## Dokimos And Model Qualification

The deterministic and guarded app path is the current trusted path. Host tests
and guarded Dokimos coverage previously passed, and Xiaomi UI journey coverage
was preserved in the local reports.

Raw FunctionGemma mobile-actions-runtime is not qualified as MenuPilot's
restaurant brain yet. The recorded physical baseline produced a valid artifact
but failed the strict tool-call contract: no valid tool calls, multiple rejected
text outputs, and latency above the app target. Treat FunctionGemma as requiring
a MenuPilot-specific fine-tune before it can be trusted for production routing.

Useful docs:

- `docs/pilot-confidence.md`
- `docs/pilot-readiness-95.md`
- `docs/functiongemma-dokimos-report.md`
- `docs/functiongemma-runtime.md`
- `docs/on-device-model.md`

## Git Notes

Remote:

```bash
git remote -v
```

Expected origin:

```text
git@github.com:LutaElbert/MenuPilot.git
```

Before committing future work, check ignored/local files:

```bash
git status --short --ignored
```

Keep commits focused. Do not stage `.model-cache/`, `.idea/`, `.agents/`,
`local.properties`, or generated build directories.
