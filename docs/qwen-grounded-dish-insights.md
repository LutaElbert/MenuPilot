# Qwen grounded dish insights

## Purpose and status

Qwen can optionally vary which verified detail MenuPilot highlights about a
dish. It is not the source of menu facts and it does not write the sentence
shown to the guest.

This component is wired into the dish-detail screen through
`MenuPilotViewModel.prepareDishInsight`. The UI shows the deterministic result
immediately and treats a qualified model-selected variation as optional polish.

## Admitted facts

`MenuDish.toGroundedDishInsightFacts()` deliberately copies only:

- the stable item/variant token;
- the restaurant-owned dish name;
- the menu category label; and
- restaurant-owned flavor/profile tags.

It does not copy ingredients, listed allergens, dietary tags, preparation
notes, price, availability, sales counts, ranking, recommendation reasons, or
guest constraints. Evidence values containing safety, ingredient, dietary,
price, sales, or availability language are rejected before a prompt is built.

## Selection-only contract

The model receives at most six typed evidence items. It must return exactly one
small JSON object:

```json
{
  "schemaVersion": 1,
  "action": "select_grounded_dish_insight",
  "dishToken": "miso_eggplant:standard",
  "angle": "PROFILE",
  "evidenceIds": ["profile.0", "profile.1"]
}
```

The shared parser requires:

- the exact schema and action;
- the exact current dish token;
- no unknown fields;
- one supported angle;
- one or two distinct, allowlisted evidence IDs; and
- selected evidence whose typed angle matches the response.

Markdown, prose, stale dish tokens, invented IDs, mixed angles, duplicate
selections, oversized output, or malformed JSON are rejected. Raw model output
is never displayed. Android deterministically renders the selected values with
app-owned templates.

## Failure and latency behavior

The optional call has one three-second coroutine budget, including time waiting
for the process-wide engine lease. There is no retry. A missing model, bad
checksum, lock contention, initialization/generation timeout, native failure,
or rejected response returns a deterministic insight from the same allowlisted
facts. If there is no usable evidence, the app returns a neutral waiter prompt.

The LiteRT adapter additionally limits engine initialization to 1.8 seconds,
generation to 0.9 seconds, context to 1,024 tokens, output to 512 characters,
and CPU threads to four. Native cleanup is always attempted in a
`NonCancellable` context.

`OnDeviceInferenceCoordinator` owns a static process-wide mutex. Qwen intent,
Qwen dish insights, and FunctionGemma routing all acquire it before creating an
engine and close the engine before releasing it. The models are therefore not
resident concurrently, even if direct test construction creates more than one
coordinator instance.

## DI and UI wiring

The production graph provides the narrow service:

```kotlin
@Provides
@Singleton
fun provideDishInsightService(
    generator: LiteRtLmQwenDishInsightSelectionGenerator,
): GroundedQwenDishInsightService =
    GroundedQwenDishInsightService(generator)
```

The dish-detail presenter renders immediately, then optionally requests a
model-selected variation in the background:

```kotlin
val facts = dish.toGroundedDishInsightFacts()
val immediate = dishInsightService.immediateInsight(facts)
val optionalVariation = dishInsightService.insight(facts)
```

The result includes source, selected evidence IDs, and a typed fallback reason.
Those fields support diagnostics without exposing prompts or raw model text.
The result must remain descriptive copy only: it must not change eligibility,
ranking, upsells, waiter handoff, order state, or allergy messaging.

## Verification

Pure contract and service tests cover:

- exact valid selection;
- prose, extra fields, stale tokens, invented IDs, duplicate IDs, and
  angle/evidence mismatch;
- exclusion of safety, ingredients, dietary claims, price, sales, and
  availability evidence;
- missing-model, timeout, malformed-output, and empty-input fallbacks;
- raw-output non-disclosure; and
- cross-model engine serialization.

Run after the shared Android worktree is idle:

```bash
./gradlew \
  :core:assistant-contract:test \
  :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin
```

These tests prove the grounding boundary and deterministic fallback. They do
not qualify Qwen's physical-device selection accuracy. A 95 percent product
confidence claim must come from the complete pinned MenuPilot/Dokimos gate with
100 percent safety, schema, fallback, and provenance hard gates; it must not be
inferred from synthetic model output.
