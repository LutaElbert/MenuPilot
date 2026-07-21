# MenuPilot 95% restaurant-pilot confidence gate

## Scope

The 95% target applies to MenuPilot as a **local-first, restaurant-owned menu
concierge** on a configured Android tablet:

- a guest browses or describes what fits them;
- restaurant-owned facts and deterministic policy decide which dishes may be
  shown;
- optional pairings are policy-gated and never preselected;
- the guest creates a shortlist and discusses it with a waiter; and
- feedback remains editable and guest-controlled.

It is not a claim that Qwen is 95% accurate, that MenuPilot autonomously places
orders, or that the fixture-backed prototype is ready for an unsupervised
production rollout. Live menu/POS sync, authenticated staff delivery, private
feedback delivery, Play model-pack delivery, and venue release signing remain
separate production gates.

## Critical invariants

Every invariant below must pass before a numerical score is reported:

1. Known allergen and diet conflicts never appear as selectable matches or
   pairings.
2. Missing, stale, or conflicting safety facts fail closed.
3. Unresolved health or preparation language asks for clarification or travels
   to the waiter; it never becomes a safety approval.
4. The guest confirms the extracted intent before the catalog is filtered.
5. Handoff revalidates the exact menu revision, policy version, item, variant,
   recipe revision, intent fingerprint, and shortlist.
6. MenuPilot never creates a POS, kitchen, payment, review-publishing, or
   automatic Google-rating action.
7. Private feedback and the Google route are offered equally for every rating.

## Weighted score

The score is evidence-based and can be awarded only after all critical
invariants pass.

| Area | Weight | Passing evidence |
|---|---:|---|
| Safety policy and fail-closed behavior | 25 | Domain and view-model safety suites pass with zero critical failures |
| Multilingual intent and clarification | 20 | Versioned English, Filipino, Taglish, and Cebuano fixtures pass; negation and vague health language are covered |
| Recommendations and ethical upsells | 15 | Current menu/sales evidence only, maximum two optional pairings, full price, budget and dismissal rules pass |
| Waiter handoff integrity | 15 | Exact revision/fingerprint revalidation and capability-aware staff messaging pass |
| Adaptive and accessible guest journey | 10 | Compact/medium/expanded previews compile, large-font cases are covered, and the target-device journey passes |
| Feedback and Google review ethics | 10 | Five dimensions, editable draft, equal destinations, allowlisted explicit handoff, and no auto-post pass |
| Build and device stability | 5 | Unit/eval/lint/build gates pass and the target device launches without a crash |

Passing threshold: **95/100**, with no failed critical invariant.

Qwen/LiteRT has its own stricter qualification report. A rejected or slow model
run may use the labelled deterministic fallback and does not reduce the
restaurant-pilot score when the common path remains correct. Qwen must not be
promoted to the safety-critical path until its physical-device Dokimos dataset
passes independently.

## Verified result — 2026-07-20

**Result: 95/100 achieved for the scoped fixture-backed restaurant pilot.**
All seven critical invariants passed.

| Area | Score | Evidence and confidence reserve |
|---|---:|---|
| Safety policy and fail-closed behavior | 25/25 | Known conflicts, stale facts, recipe drift, unresolved health language, and handoff revalidation pass deterministically |
| Multilingual intent and clarification | 19/20 | 66/66 English, Filipino, Taglish, and Cebuano deterministic cases pass, plus eight unknown-health phrases; one point remains reserved because a finite corpus is not open-world dialect accuracy |
| Recommendations and ethical upsells | 14/15 | Exact variant, current comparable evidence, tap-time revalidation, two-item cap, dismissal, and whole-order budget pass; one point remains reserved until a live restaurant sales adapter replaces demo records |
| Waiter handoff integrity | 15/15 | Exact typed payload, revision/fingerprint checks, stale-state rejection, and screen-only capability wording pass |
| Adaptive and accessible guest journey | 9/10 | Compact, medium, expanded, short-window, and 1.5× text geometry pass; one point remains reserved because only one physical restaurant-device SKU has been qualified |
| Feedback and Google review ethics | 9/10 | Five dimensions, editable exact draft, equal destinations, explicit guest posting, and URL classification pass; one point remains reserved for a venue-provided direct-review URL and private-feedback backend |
| Build and device stability | 4/5 | Debug and optimized release builds, lint, and Xiaomi runtime pass; one point remains reserved for production signing and a broader device matrix |
| **Total** | **95/100** | **Threshold met; no critical invariant failed** |

The five-point reserve is deliberate uncertainty, not five known product
defects. It prevents fixture coverage and one successful device from being
misrepresented as 100% production certainty.

### Evidence

- The serialized host gate passed domain, assistant-contract, app, and AI-eval
  suites; debug lint reported zero errors; debug and optimized release APKs
  assembled.
- The current host baseline discovered 282 JUnit tests: 280 passed and two
  optional physical-artifact checks were skipped by design. Every executed
  baseline test passed.
- The guarded, integrated application Dokimos corpus passed exactly 22/22.
  Checked-in Qwen and FunctionGemma contract fixtures remain synthetic parser
  and boundary evidence; they are not physical-model qualification results.
- Redmi/Xiaomi model `2404ARN45A`, Android 16, passed 6/6 connected tests with
  no skips: the full allergy-to-waiter guest journey plus five adaptive layout
  configurations. The XML and device-run records are preserved under
  `app/build/reports/device-proof/xiaomi-connected-2026-07-20/`.
- A later repeat exposed an immediate post-navigation assertion race in the
  journey harness. Bounded screen-transition waits were added, and the fixed
  journey then passed twice consecutively on the Xiaomi. A subsequent combined
  reinstall was rejected by Xiaomi's user-confirmation security gate before
  any tests ran; it is not counted as a product pass or failure.
- Manual Android CLI review confirmed separated recommendation badges, an
  opaque scrolled header, and final actions above the Xiaomi navigation bar.
  Evidence is saved in
  `app/build/reports/device-proof/xiaomi-95-top-matches.png` and
  `app/build/reports/device-proof/xiaomi-95-scrolled-header-fixed.png`.

### Separate Qwen/LiteRT result

The raw Qwen model is **not** included in the 95-point claim. The pinned
497,664,000-byte model initialized on the Redmi through LiteRT-LM/XNNPack, but
the complete four-scenario physical qualification did not produce an artifact:
the third, Cebuano scenario exceeded the enforced 60-second generation limit.
The run was rejected before Dokimos scoring, rather than being counted as a
partial pass. The preserved trace is under
`app/build/reports/device-proof/qwen-physical-2026-07-20/`.

MenuPilot therefore keeps Qwen as an experimental long-tail interpreter behind
strict parsing, timeout, rejection, and deterministic fallback. Raising the
timeout merely to obtain a score would make the restaurant experience worse
and would not establish correctness.

Android ADK now owns the local FunctionGemma session and invocation lifecycle.
It remains orchestration—not the safety authority—and is therefore evaluated
separately from raw model accuracy. Deterministic allergen, catalog, sales,
upsell, handoff, and feedback policies still own every consequential result.

### Separate FunctionGemma routing-agent gate

FunctionGemma is also outside the 95-point restaurant-pilot claim. A complete
24/24 raw artifact was captured on the physical Xiaomi `2404ARN45A` running
Android 16 / SDK 36. Its installed debug APK SHA-256 was
`bfbed50bd67fa1c106d00cf0f3996c7081620de63ece1bddd8dbdcad24f656df`.
That captured deployment failed its independent gate:
18 runs ended as `MODEL_OUTPUT_REJECTED` with `unexpected_text_output`, six timed out,
and 0/24 produced an accepted, schema-valid, exact route. Raw-call latency was
20,879 ms minimum, 25,768 ms median, 43,093 ms nearest-rank p95, and
214,110 ms maximum, exceeding both latency limits.

The artifact is preserved at
`app/build/reports/device-proof/functiongemma-physical-2026-07-20/functiongemma-route-runs.json`.
Engine-residency hardening was implemented after this capture, so the current
debug APK has a different SHA-256 and needs a new physical artifact before any
raw-model claim can apply to it. The prior failure remains evidence for its
recorded APK; it is not relabelled as a current-binary pass.
The repository describes a mobile-actions function-calling specialization
rather than a MenuPilot restaurant-routing fine-tune. This
result establishes a specialization/contract mismatch for the pinned
deployment; it is not a claim that the model or FunctionGemma family is
defective.

FunctionGemma remains restricted to exactly one read-only
`route_menu_request` proposal. Deterministic MenuPilot policies still own
safety, menu facts, sales evidence, upsell qualification, budget enforcement,
handoff, and review behavior, and the guarded integrated corpus passes 22/22.
The raw artifact explicitly does not execute those integrated behaviors. See
[functiongemma-dokimos-report.md](functiongemma-dokimos-report.md).

## Reproducible verification

Run the host gate:

```bash
./scripts/verify-pilot-confidence.sh
```

Add the focused guest-journey test on a connected device:

```bash
./scripts/verify-pilot-confidence.sh --serial <adb-serial>
```

Evaluate an exported physical-model artifact separately:

```bash
./scripts/verify-pilot-confidence.sh \
  --model-runs /absolute/path/qwen3-0.6b-intent-runs.json
```

Evaluate a complete FunctionGemma artifact separately:

```bash
./scripts/verify-pilot-confidence.sh \
  --functiongemma-runs /absolute/path/functiongemma-route-runs.json
```

The script deliberately compiles screenshot previews without accepting new
goldens. Reference images must be approved through visual review.
