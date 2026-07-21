# FunctionGemma routing runtime

## Status

MenuPilot has a production-shaped, fail-closed FunctionGemma adapter. It is
advisory. The supplied model has now been measured on a physical device and
does not meet the independent restaurant-routing qualification gate.

The model proposes one conversation route. It never executes tools, changes
confirmed needs, decides food safety, ranks dishes, places an order, calls
staff, or submits a review. Deterministic MenuPilot policy and verified
restaurant data remain authoritative.

## Pinned artifact

- repository: `ElLabs/mobile-actions-runtime`
- immutable revision: `43221886a868ef2459506791b56e4db8237a23cf`
- file: `mobile-actions_q8_ekv1024.litertlm`
- manifest version: `0.1.0`
- exact size: `285,561,008` bytes
- SHA-256:
  `9a9c590143b6a88ecaf074c574c2bfe311ba4160559b9e105fa4ac4ef0b57aef`
- runtime: LiteRT-LM Android `0.14.0`
- declared license: `gemma` / Gemma Terms of Use

The Hugging Face repository is public and ungated as of 2026-07-20. Its model
card describes a mobile-actions function-calling package, not a
MenuPilot-specific restaurant fine-tune. The card supplies no MenuPilot-domain
accuracy result. Restaurants distributing the artifact must review and comply
with the Gemma license and prohibited-use terms.

Primary sources:

- [ElLabs artifact and model card](https://huggingface.co/ElLabs/mobile-actions-runtime)
- [Google FunctionGemma model card](https://huggingface.co/google/functiongemma-270m-it)
- [LiteRT-LM Kotlin tools guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.14.0/docs/api/kotlin/getting_started.md)
- [Android excessive-agency guidance](https://developer.android.com/privacy-and-security/risks/ai-risks/excessive-agency)

## Boundary

The model sees one read-only tool:

`route_menu_request(action, subject?, period?)`

Allowlisted actions are:

- `BROWSE_MENU`
- `SHAPE_MENU`
- `EXPLAIN_DISH`
- `COMPARE_DISHES`
- `SHOW_BESTSELLERS`
- `SUGGEST_PAIRING`
- `REQUEST_WAITER`
- `CLARIFY`

The parser requires exactly one tool call, the exact tool name, only the three
known fields, valid enums, bounded text, and action-specific arguments. Any
text response, unknown tool, duplicate call, extra field, malformed argument,
timeout, missing file, bad hash, or native failure returns an app-owned safe
clarification. Raw model prose is never displayed.

`REQUEST_WAITER` is a route suggestion. The existing explicit UI and staff
handoff flow still performs the real action. Pairings and bestsellers are also
resolved from verified catalog and sales evidence after routing.

## Conversational continuity

The app retains the full guest transcript and authoritative dining state. The
1,024-token model receives a bounded projection of the four most recent turns.
Confirmed allergens, diets, price ceiling, soft preferences, and focused dish
are repeated separately as typed app-owned context on every model call.

This split is intentional:

- the tablet can show and restore the whole conversation;
- the small local model cannot lose confirmed safety context when older prose
  is truncated;
- a prompt such as “forget my allergy” cannot mutate state through a tool call;
- only explicit deterministic correction logic may remove a hard constraint;
- timeouts and rejected output preserve the transcript and confirmed state.

## ADK and Qwen placement

ADK Kotlin `0.5.0` can sit above this adapter as the session, event, and
orchestration layer. The direct LiteRT-LM boundary remains useful because it
enforces manual tool handling, a 15-second deadline, a single-call parser, and
no automatic action execution. ADK's `google-adk-kotlin-litertlm-android`
module declares LiteRT-LM `0.13.1`; MenuPilot pins `0.14.0`, so the resolved
dependency and device build must be tested whenever ADK is enabled.

Recommended responsibility split:

- ADK: conversational session and deterministic workflow orchestration;
- FunctionGemma: advisory route selection;
- deterministic MenuPilot rules and restaurant data: constraints, safety,
  catalog eligibility, sales facts, pairings, upsells, and handoff authority;
- Qwen: optional grounded wording only after verified facts are selected.

Do not keep Qwen and FunctionGemma engines loaded concurrently on memory-limited
tablets. The process cache retains at most one FunctionGemma engine. Repeated
FunctionGemma requests reuse that engine, while starting Qwen evicts it first.
Native failure, timeout, or cancellation evicts the cached engine, as does
Android memory pressure. A schema/output rejection closes the request's
conversation but retains the already-verified engine. This keeps warm-path
latency available without retaining two large model runtimes at once.

## Provisioning

Build and install a debuggable app first, then run:

```bash
scripts/provision-functiongemma-model.sh --serial <adb-serial>
```

The script downloads from the immutable revision, verifies the host file's
exact size and SHA-256, copies it into app-private `files/models`, and verifies
the device copy again.

After provisioning, export all 24 physical runs and evaluate the resulting
artifact with Dokimos:

```bash
scripts/export-functiongemma-device-eval.sh --serial <adb-serial>
```

For one troubleshooting scenario, invoke the Android test directly with the
`scenarioIndex` instrumentation argument. A partial artifact is intentionally
rejected as qualification evidence.

## Qualification

The shared production parser and the 24 pinned multilingual,
conversation-state, injection, safety, and routing scenarios live in
`core/assistant-contract`. Android export and host Dokimos evaluation must use
that same manifest so queries, context, expected routes, and critical-gate
labels cannot be relabelled.

The acceptance rule is:

- 100 percent for schema, safety, state-retention, and prompt-injection hard
  gates;
- at least 95 percent aggregate route accuracy;
- raw generator-call nearest-rank p95 latency no greater than 5,000 ms and
  maximum latency no greater than 15,000 ms across the complete suite;
- a real non-emulator Android device plus recorded device, app-version, and
  installed base-APK SHA-256 provenance;
- no synthetic model output counted as physical evidence;
- timeout and invalid output still count as model failures. Their device
  records label fail-closed behavior as expected but not executed; separate
  application tests must prove real state preservation and clarification.

The current complete pinned artifact does not pass that gate. FunctionGemma is
experimental and must fall back without affecting MenuPilot's deterministic
pilot-confidence score unless a later pinned deployment passes on the target
restaurant tablet class.

The exported device envelope is scoped to `RAW_MODEL_ROUTING_ONLY`. It does
not execute or prove catalog grounding, application state mutation, allergy
policy, upsell selection, handoff, or feedback. Passing the physical model
gate therefore cannot be substituted for the integrated application gates.

## Physical qualification result — 2026-07-20

A complete 24/24 raw artifact was captured on a physical Xiaomi
`2404ARN45A`, Android 16 / SDK 36. The captured installed debug APK SHA-256 was
`bfbed50bd67fa1c106d00cf0f3996c7081620de63ece1bddd8dbdcad24f656df`:

`app/build/reports/device-proof/functiongemma-physical-2026-07-20/functiongemma-route-runs.json`

Eighteen scenarios returned prose or other non-call output rejected as
`MODEL_OUTPUT_REJECTED` / `unexpected_text_output`; six timed out. Accepted calls,
schema-valid calls, and exact expected routes were each 0/24. Raw generator
latency was 20,879 ms minimum, 25,768 ms median, 43,093 ms nearest-rank p95,
and 214,110 ms maximum. The pinned deployment therefore fails the raw schema,
route-accuracy, and latency gates.

The single-engine residency implementation was hardened after this artifact
was captured. The current debug APK has a different SHA-256, so it requires a
fresh complete Xiaomi artifact before raw qualification can be reconsidered.
The lifecycle change improves repeated initialization behavior; it does not
retroactively change the recorded calls or turn a failed artifact into a pass.

The artifact's repository describes a mobile-actions function-calling
specialization rather than a MenuPilot restaurant-routing fine-tune. The
observed result is evidence of a specialization/contract mismatch for this
pinned use, not evidence that the model or FunctionGemma family is defective.

The application still fails closed. The current host baseline is 282 tests:
280 passed, two optional physical-artifact checks skipped, and all executed
tests green. The guarded integrated application Dokimos corpus is exactly
22/22. Those application results preserve the scoped 95/100 pilot claim, which
explicitly excludes raw FunctionGemma and Qwen qualification.
