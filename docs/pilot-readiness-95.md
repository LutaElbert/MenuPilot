# MenuPilot restaurant-pilot readiness

## Scope of the confidence target

The 95% target applies to MenuPilot as a **fixture-backed smart menu concierge
with a human waiter handoff**. It does not apply to autonomous ordering,
payment, kitchen submission, or an unqualified language model acting as the
safety authority.

The scoped gate reached **95/100 on 2026-07-20**, with every critical invariant
passing on the serialized host suite and the connected Xiaomi journey. The
score allocation and preserved evidence are in
[pilot-confidence.md](pilot-confidence.md).

The current host baseline discovered 282 JUnit tests: 280 passed and two
optional physical-artifact checks were skipped by design. Every executed
baseline test passed. The guarded integrated application Dokimos corpus passed
exactly 22/22.

The operational contract is:

1. interpret or manually collect a guest's needs;
2. evaluate exact menu variants using deterministic restaurant facts;
3. rank current, comparable restaurant sales evidence without weakening
   eligibility;
4. offer no more than two optional, policy-qualified pairings;
5. preserve exact needs, variants, recipe revisions, prices, and unresolved
   questions in a screen-only waiter handoff; and
6. generate an editable feedback draft while leaving any Google review action
   entirely to the guest.

## Closed operational gates

### Catalog integrity

- Every presented dish must map one-to-one to an exact item and variant in the
  policy snapshot.
- Presentation price and recipe revision must equal the policy snapshot.
- Duplicate, missing, unknown, price-mismatched, or recipe-mismatched
  presentations block catalog confirmation.
- Navigation, shortlist addition, removal, and handoff use item ID plus variant
  ID. Recipe revision is retained in the shortlist and rechecked at handoff.

### Bestseller and merchandising evidence

- Sales and co-order evidence references a registered source ID with a
  restaurant-facing source label and source kind.
- Bestseller comparison requires one exact source and aggregation window.
- Old data cannot become current merely because it was re-imported recently.
- Stale, future-dated, mixed-window, mixed-source, negative, duplicate, or
  unknown-source evidence becomes unavailable and cannot rank or explain.
- A bestseller is the exact highest count in the comparable window, including
  ties. The UI receives only resolved evidence; it no longer reads an
  independent weekly-count presentation field.
- Affinity evidence cannot qualify an upsell unless all compared signals share
  one source and window, with no duplicate anchor/candidate fact.

### Optional upsells

- A pairing must pass the same deterministic menu policy as the main catalog.
- It must have an exact restaurant-approved pairing or current comparable
  affinity fact.
- It is re-qualified when the guest taps **Add**, rather than trusting a
  previously rendered card.
- It remains fully priced, optional, dismissible, never preselected, and capped
  at two accepted pairings.
- An explicit whole-order budget is checked for direct selections, pairings,
  and again before handoff creation.

### Waiter handoff

The immutable handoff payload includes:

- reference, creation time, session, table/counter location, and placement;
- screen-only delivery mode and separately recorded channel capability;
- menu generation/revision, policy version, and merchandising revision;
- exact confirmed-intent fingerprint and structured dining needs;
- exact item, variant, recipe revision, price, eligibility, and selection
  origin for every pick;
- explicit unresolved allergy, cross-contact, ingredient, preparation,
  availability, substitution, and final-choice questions;
- per-dish versus whole-shortlist budget scope;
- estimated total; and
- staff identity and timestamp only when a staff review is actually recorded.

Creating a handoff reruns the policy. A stale fact, changed recipe, changed
intent, missing variant, price-integrity failure, or over-budget shortlist
fails closed. A restored handoff route without its payload navigates back
instead of displaying a fabricated pending reference. No code path claims that
staff, a POS, or a kitchen was notified.

### Feedback and Google review handoff

- All five feedback dimensions are required before draft generation.
- Private and Google destinations remain available for every score.
- Public approval is tied to the exact draft text; any edit invalidates it.
- The destination is validated and classified before the approved draft is
  copied.
- Direct review-request links, Maps business-profile fallbacks, and opaque Maps
  short links are distinguished rather than described as equivalent.
- No score is pre-filled and no review is posted by MenuPilot.
- The fixture intentionally uses a Maps business search fallback and therefore
  reports `BUSINESS_PROFILE_FALLBACK`, not production direct-review readiness.

### Agent and model boundary

- FunctionGemma and Qwen remain advisory and cannot override deterministic
  constraints, menu facts, sales evidence, upsell policy, budget checks,
  handoff integrity, or review behavior.
- The guarded integrated application Dokimos corpus passes exactly 22/22,
  including strict rejection and fail-closed fallback behavior.
- A complete 24/24 raw FunctionGemma artifact from the physical Xiaomi
  `2404ARN45A` running Android 16 / SDK 36 produced 18
  `MODEL_OUTPUT_REJECTED` / `unexpected_text_output` outcomes and six timeouts, with
  0/24 accepted, schema-valid, or exact routes.
- Raw latency was 20,879 ms minimum, 25,768 ms median, 43,093 ms nearest-rank
  p95, and 214,110 ms maximum. The raw gate fails and FunctionGemma cannot be
  promoted for restaurant routing.
- That artifact identifies installed debug APK SHA-256
  `bfbed50bd67fa1c106d00cf0f3996c7081620de63ece1bddd8dbdcad24f656df`.
  Engine-residency hardening changed the APK afterward, so the current binary
  needs a fresh complete physical artifact and remains unqualified.
- The model package is specialized for mobile-actions function calling, not
  MenuPilot restaurant routing. This is a specialization/contract mismatch
  for the pinned deployment, not a claim that the model itself is defective.
- Raw FunctionGemma and Qwen qualification are explicitly outside the scoped
  95/100 application score.

### Android release posture

- AGP remains pinned to Android Studio-compatible `9.2.1`.
- The release build is non-debuggable and enables R8 code optimization plus
  resource shrinking.
- Cleartext network traffic is disabled.
- Backup and device-transfer extraction remain disabled for app-private data.
- No production signing key or external credential is created in this
  repository.

## Verification commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew \
  :core:domain:test \
  :core:assistant-contract:test \
  :ai-evals:test \
  :app:testDebugUnitTest \
  :app:compileDebugScreenshotTestKotlin \
  :app:compileDebugAndroidTestKotlin

./gradlew \
  :app:lintRelease \
  :app:assembleRelease
```

The domain and app suites include focused regressions for sales-window
comparability, affinity provenance, duplicate evidence, exact variant
identity, whole-order budget enforcement, catalog price drift, stale-at-tap
upsells, stale-at-handoff facts, typed handoff contents, exact-draft review
approval, and Google destination readiness.

## External work still required

These items are intentionally not represented as complete:

- transactional live menu, ingredient, allergen, availability, and POS
  adapters;
- authenticated staff notification and delivery acknowledgment;
- a venue-provided direct review-request URL from its verified Google Business
  Profile;
- authenticated private-feedback delivery and manager follow-up;
- production signing, Play Console configuration, privacy disclosures, and
  crash/operations monitoring;
- optimized release smoke testing on every intended restaurant tablet SKU; and
- separate successful Qwen/LiteRT and FunctionGemma qualification. Qwen remains
  outside deterministic eligibility, budget, upsell, and handoff decisions;
  the current raw FunctionGemma artifact failed 0/24 and remains restricted to
  fail-closed experimentation.
