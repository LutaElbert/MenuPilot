# On-device Qwen and Dokimos workflow

## Runtime contract

MenuPilot pins:

- LiteRT-LM Android `0.14.0`;
- `litert-community/Qwen3-0.6B`;
- file `qwen3_0_6b_mixed_int4.litertlm`;
- Hugging Face revision `dd97997951bb15a2a71f539ba17f604707c0b11a`;
- exact size `497,664,000` bytes; and
- SHA-256
  `b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9`.

The model is text-only. Android's on-device speech recognizer creates an
editable transcript first; Qwen interprets that text.

The published `0.14.0` AAR is the build-time authority. Its Kotlin API supports
engine/conversation configuration and streaming `Flow<Message>`, but the
published artifact does not yet expose the constrained-response classes found
in newer repository source. MenuPilot therefore supplies compact extraction
rules plus a non-empty format example and treats every result as untrusted.
The pure `:core:assistant-contract` parser is still the enforcement boundary.

## Safety and lifecycle

Qwen may extract language, supported allergens and reasons, vegetarian intent,
taste/speed/popularity preferences, a numeric peso limit, and unresolved
terms. It never sees authority to approve a dish.

Recognized allergies, diets, budgets, taste preferences, and bestseller
requests stay on the instant deterministic path. Qwen is reserved for
long-tail text that those rules cannot map to a supported constraint. This
keeps a slow or invalid model response out of the common restaurant flow.

Before a model draft can shape the menu:

1. Exact JSON fields and schema version are checked.
2. Canonical IDs, enum values, list sizes, price limits, and clarification
   invariants are checked.
3. Prohibited food-safety guarantees are rejected.
4. Model-authored clarification prose is replaced with app-owned neutral copy.
5. Deterministically recognized allergens, supported diets, and the stricter
   price ceiling are merged as a hard floor.
6. The guest reviews editable intent chips.
7. The deterministic menu policy engine evaluates restaurant-owned facts.

Inference initializes and runs away from the main thread, serializes requests,
has a 60-second bound, explicitly cancels native work, and closes each
conversation and CPU engine after the one-shot result. This trades repeated
initialization latency for safer memory behavior on restaurant tablets.

## Debug tablet provisioning

The APK has no `INTERNET` permission and contains no model. Install the debug
APK, then run:

```bash
./scripts/provision-qwen3-model.sh --serial <adb-device-serial>
```

The script downloads from the pinned revision, supports resuming its temporary
download, verifies size and SHA-256, stages the file over ADB, and copies it to:

```text
/data/user/0/com.menupilot.restaurant/files/models/
qwen3_0_6b_mixed_int4.litertlm
```

The `run-as` copy is for a debuggable managed tablet. A production Play build
should use a fast-follow [Play for On-device AI
pack](https://developer.android.com/google/play/on-device-ai) and resolve the
installed pack location at runtime.

## Dokimos evaluation

Fast CI runs use Dokimos `0.24.0` with versioned contract fixtures that pass
through the production parser:

```bash
./gradlew :core:assistant-contract:test :ai-evals:test
```

The checked-in `intent.v1` corpus contains 40 exact fixtures spanning every
supported allergen, allergy/celiac/intolerance/unspecified reasons, English,
Filipino, Taglish, Cebuano, budgets, preferences, negation, clarification, and
prompt-injection handling. A breadth test prevents those dimensions or the
minimum 32-case pilot floor from being removed accidentally. The Android
instant path has a separate 66-case multilingual exact-behavior corpus plus an
eight-phrase unknown-health-cue matrix that must always ask for clarification.

A physical-device run exports guest requests, raw Qwen output, pinned runtime
metadata, and expected canonical intent contracts. The host then evaluates
that artifact with the same Dokimos exact-match gate:

```bash
./scripts/run-qwen-dokimos-eval.sh --serial <physical-device-serial>
```

Device artifacts use the shared `menupilot-qwen-intent-device-v1` manifest.
Before any raw output is scored, the host requires the exact four stable
scenario IDs, count and ordered manifest set, pinned model ID, Hugging Face
revision, LiteRT-LM runtime, model checksum and size. It also rejects duplicate,
missing, extra or relabelled scenarios and mismatched per-example provenance.
Single-scenario instrumentation exports remain useful for debugging but are
deliberately incomplete and cannot pass qualification. The runner deletes any
stale device artifact before starting a full export.

The optional artifact test is skipped during ordinary CI when no model-run
path is supplied. It becomes a hard gate when invoked with:

```bash
./gradlew :ai-evals:test \
  -PmenupilotModelRuns=/absolute/path/qwen3-0.6b-intent-runs.json
```

Dokimos evaluates intent extraction and contract compliance. It does not judge
allergen eligibility; deterministic domain tests own that safety decision.
Device artifacts are evaluated with an aggregate assertion so every scenario
runs and appears in the report even when an earlier one fails.

The physical-model artifact is a strict qualification gate, not a guaranteed
green test. A failed exact match must remain visible and must not be relabelled
as production-ready. A 95% or 100% fixture pass rate measures only the pinned
corpus; it is not evidence of 95% open-world language accuracy.

## Device qualification

The model repository reports a large CPU memory footprint, so a 4 GB emulator
is not representative. The current ARM tablet emulator reached LiteRT/XNNPack
initialization but crashed in native code with `SIGILL`, indicating an
unsupported emulated CPU instruction. MenuPilot detects emulators and uses the
deterministic fallback instead of entering that native path.

### Redmi 13 qualification — 2026-07-20

The local release-mode proof ran on Xiaomi/Redmi model `2404ARN45A` (`tides`),
Android 16/API 36, arm64-v8a, with about 7.5 GiB RAM:

- the exact 497,664,000-byte model initialized through LiteRT-LM/XNNPack and
  completed real CPU inference without a native crash;
- inference reached roughly 2.5–2.7 GiB RSS;
- the first four-scenario run completed in 130.9 seconds, but standalone Qwen
  failed the strict intent contract in all four scenarios by copying the empty
  format object;
- a revised compact prompt completed one flagship scenario in 54.2 seconds,
  recognized peanut allergy and spicy intent, but still failed exact
  qualification because it emitted a Markdown fence, encoded schema version as
  a string, and omitted vegetarian and budget facts;
- the hardened four-scenario manifest was rerun after the strict-parser and
  prompt changes. The model initialized correctly and began producing outputs,
  but the third, Cebuano scenario exceeded the enforced 60-second generation
  limit. No complete artifact was written, and the host correctly refused to
  assign a partial Dokimos score;
- the release-mode common path therefore uses the deterministic safety parser,
  correctly produced Peanut allergy, Vegetarian, Spicy, and Under ₱500, and
  reached the recommendation screen with waiter confirmation required; and
- after that common flow the app used about 168 MiB PSS / 276 MiB RSS and
  Android reported thermal status 0.

Conclusion: the pinned model is integrated and runnable on this Xiaomi, but it
is not qualified for the safety- or latency-critical path. It remains a
long-tail experimental interpreter behind strict rejection and fail-closed
fallback. The installed proof APK is release-mode but locally debug-signed;
store production still requires the restaurant's release key and Play
on-device model-pack delivery.

Qualify each intended physical restaurant-tablet SKU for:

- successful engine initialization;
- peak private memory and process survival;
- first-token and total intent latency;
- thermal behavior over repeated sessions;
- cancellation and app-background behavior; and
- the complete multilingual Dokimos dataset.

Primary references:

- [LiteRT-LM Android guide](https://ai.google.dev/edge/litert-lm/android)
- [LiteRT-LM releases](https://github.com/google-ai-edge/LiteRT-LM/releases)
- [Qwen3-0.6B LiteRT model](https://huggingface.co/litert-community/Qwen3-0.6B)
- [Dokimos documentation](https://dokimos.dev/overview/)
