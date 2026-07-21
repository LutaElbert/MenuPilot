# FunctionGemma Dokimos evaluation report

Date: 2026-07-20

## Outcome

MenuPilot now has a versioned, fail-closed qualification gate for the
FunctionGemma routing agent. A complete 24/24 artifact from a physical Xiaomi
`2404ARN45A` running Android 16 / SDK 36 passed artifact-completeness and
provenance validation, but the captured raw deployment **failed
qualification**. Its installed debug APK SHA-256 was
`bfbed50bd67fa1c106d00cf0f3996c7081620de63ece1bddd8dbdcad24f656df`.

| Observed result | Value |
|---|---:|
| Complete scenario records | 24/24 |
| Accepted tool calls | 0/24 |
| Schema-valid calls | 0/24 |
| Exact expected routes | 0/24 |
| `MODEL_OUTPUT_REJECTED` / `unexpected_text_output` | 18 |
| `TIMED_OUT` | 6 |
| Minimum latency | 20,879 ms |
| Median latency | 25,768 ms |
| Nearest-rank p95 latency | 43,093 ms |
| Maximum latency | 214,110 ms |

The raw accuracy, schema, exact-route, and latency gates therefore fail. The
preserved artifact is:

`app/build/reports/device-proof/functiongemma-physical-2026-07-20/functiongemma-route-runs.json`

The engine-residency implementation changed after capture. The current debug
APK therefore has a different SHA-256 and remains unqualified until it produces
a new complete physical artifact. This does not erase or upgrade the captured
0/24 result.

The application boundary is intentionally stronger than the aggregate score:

| Gate | Requirement |
|---|---:|
| Complete pinned artifact | 100% |
| Strict one-call tool schema | 100% |
| Allergy/diet/negation/correction route cases | 100% |
| Correct routing from supplied state and paired budget amount/scope | 100% |
| Filipino, Taglish, and Cebuano safety routes | 100% |
| Prompt-injection resistance | 100% |
| Fact-dependent dish, bestseller, and pairing route selection | 100% |
| Physical Android and installed APK provenance | 100% |
| Emulator rejection | 100% |
| Raw generator-call p95 / maximum latency | ≤5,000 ms / ≤15,000 ms |
| Typed expected app fallback metadata | 100% |
| All exact routes | at least 95% |

With 24 scenarios, 23 exact routes score 95.83%; 22 score 91.67% and fail.
Any hard-gate miss fails regardless of the aggregate.

## Evidence added

- 24 stable, non-relabelable scenarios cover all eight route actions and all
  four bestseller periods.
- The production parser is evaluated directly rather than reimplemented in
  Dokimos.
- Dokimos `ExactMatchEvaluator`, `ToolCallValidityEvaluator`, and
  `ToolCorrectnessEvaluator` check canonical routing, strict JSON-schema tool
  validity, and exact tool arguments.
- The physical envelope pins the model ID, immutable revision, model file,
  manifest version, exact byte size, SHA-256, license, LiteRT-LM runtime,
  system-instruction hash, tool-schema hash, combined prompt-contract hash,
  ordered scenario manifest, exact request history, typed authoritative
  context, expected route, execution outcome, expected fallback metadata,
  latency, and bounded error code.
- Physical evidence additionally records manufacturer, model, device/product,
  OS build fingerprint, SDK, supported ABIs, app
  package/version/code/debuggable flag, installed base-APK SHA-256, capture
  time, and run ID. The shell exporter and instrumentation both reject common
  emulator signatures.
- When available, the host Git revision and dirty flag identify the checkout
  that launched instrumentation. They are not presented as cryptographic APK
  source provenance; the base-APK SHA-256 identifies the installed binary.
- The envelope is intentionally described as local reproducibility provenance,
  not hardware-backed attestation. A production evidence archive should retain
  the artifact, matching APK, and instrumentation logs together.
- Missing, duplicate, extra, reordered, relabelled, truncated, or
  provenance-mismatched artifacts are rejected before scoring.
- Timeout or rejected output is recorded with
  `APP_FAIL_CLOSED_EXPECTED_NOT_EXECUTED`. This is an expected app response,
  not an observed end-to-end fallback, and the raw model run still fails.
  Separate ViewModel regressions prove that the application retains confirmed
  constraints, catalog, and shortlist state.
- Budget amount and `PER_DISH`/`WHOLE_ORDER` scope are paired and serialized
  independently of the bounded transcript.
- The physical artifact is self-labelled `RAW_MODEL_ROUTING_ONLY`; all
  integrated workflow, state-mutation, catalog-grounding, and safety-policy
  observation flags are false. Dokimos runs the production parser on the host,
  but this artifact does not execute the full MenuPilot workflow.
- Nearest-rank p95 is computed across all 24 raw generator calls and must be at
  most 5,000 ms; the slowest call must be at most 15,000 ms. A model can pass
  route accuracy and still fail practical latency qualification.

## Pinned model

- Model: `ElLabs/mobile-actions-runtime`
- Revision: `43221886a868ef2459506791b56e4db8237a23cf`
- File: `mobile-actions_q8_ekv1024.litertlm`
- Manifest: `0.1.0`
- Exact size: `285,561,008` bytes
- SHA-256:
  `9a9c590143b6a88ecaf074c574c2bfe311ba4160559b9e105fa4ac4ef0b57aef`
- Runtime: `litertlm-android:0.14.0`
- License marker: `gemma`

## Verification status

The current host baseline discovered 282 JUnit tests: 280 passed and two
optional physical-artifact checks were skipped by design. Every executed
baseline test passed. The guarded integrated application Dokimos corpus passed
exactly 22/22. Those results validate MenuPilot's deterministic policy,
parsing, rejection, fallback, and workflow boundary; they do not convert the
raw model's 0/24 physical result into a pass.

Reproduce the host boundary checks with:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:assistant-contract:test :ai-evals:test
```

Re-evaluate the preserved physical artifact separately:

```bash
./scripts/run-functiongemma-dokimos-eval.sh \
  --artifact app/build/reports/device-proof/functiongemma-physical-2026-07-20/functiongemma-route-runs.json
```

## Interpretation

The pinned repository's model card describes a mobile-actions function-calling
specialization, not a MenuPilot restaurant-routing fine-tune.
The observed prose rejections and timeouts therefore demonstrate a
specialization/contract mismatch for this deployment. They do not establish a
general defect in the model or in FunctionGemma.

The defensible statement is: the **agent boundary, evaluator, and guarded
application path are working**, including a 22/22 integrated corpus, while the
pinned raw FunctionGemma deployment scored 0/24 and is not qualified for
MenuPilot routing. It must remain behind strict rejection, timeout, and
deterministic fallback. This raw result is separate from MenuPilot's scoped
95/100 application-pilot score.
