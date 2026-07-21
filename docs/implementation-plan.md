# MenuPilot implementation plan

## Product goal

Build a restaurant-owned Android tablet concierge that answers a narrower and
more useful question than a self-ordering kiosk:

> “What food is best for me at this restaurant?”

MenuPilot turns a natural-language guest request into a confirmed dining
intent, at most three explainable matches, a guest-controlled shortlist, and a
human waiter conversation. After the visit, it turns structured food and
service feedback into an editable, guest-controlled comment.

Guests may also browse the whole menu manually. Manual and assisted discovery
are two views of the same policy-evaluated catalog, dish details, shortlist,
pairing rules, and waiter handoff—not separate ordering experiences.

The first release is deliberately local-first. It proves the discovery,
safety, ethical-upsell, waiter-handoff, and feedback contracts before connecting
live menu/sales data, a waiter notification service, a private-feedback
backend, or a Business Profile review link. A production-shaped on-device
model boundary is implemented, while Play delivery and reference-tablet
qualification remain deployment work.

## Non-negotiable product rules

- Allergies, coeliac requirements, intolerances, and explicit religious or
  ethical restrictions are hard constraints.
- Taste, spice, price, appetite, and preparation-time requests are ranking
  preferences unless the guest explicitly makes them mandatory.
- The assistant may extract intent, ask a clarifying question, rank already
  permitted dishes, and draft explanations.
- Only deterministic restaurant data and rules may filter dishes for allergens
  or permit a dish to appear in a shortlist.
- Unknown, stale, or incomplete allergen information fails closed.
- MenuPilot never labels a dish "safe", "allergy-safe", "risk-free", or
  "guaranteed".
- MenuPilot never places an order, accepts payment, promises kitchen
  availability, or impersonates the waiter.
- Any unresolved allergy, ingredient, or preparation question travels with the
  shortlist and remains for the waiter and kitchen to confirm.
- Upsells are optional, limited to two, fully priced, explained, policy-gated,
  never preselected, and dismissible.
- Private feedback and the Google review route are available equally for all
  ratings. No incentive, review gating, pre-filled Google score, or automatic
  posting is allowed.

## MVP vertical slice

The first implemented scenario is:

> "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500."

The app will:

1. Welcome the guest with two explicit choices: **Browse the menu** or
   **Ask MenuPilot**, plus useful prompts such as “What is your bestseller this
   week?” and “I’m allergic to peanuts.”
2. For manual discovery, evaluate a neutral intent through the same policy
   engine and show a searchable, category-filtered full menu. For assisted
   discovery, accept the query and extract a draft intent locally.
3. Confirm the interpreted allergen, diet, spice, budget, and cross-contact
   requirement using editable choices.
4. Generate at most three matches from bundled restaurant data.
5. Label the results as best match, alternative, or ask the waiter, and explain
   why each dish fits.
6. Show dish details, ingredients, allergen facts, verification freshness, and
   price.
7. Accept tap-to-talk input through Android's on-device speech recognizer, put
   the bounded transcript into the same editable query field, and require the
   normal interpretation and intent-confirmation steps.
8. Add a permitted dish to a local shortlist.
9. Offer at most two transparent, optional pairings that pass the same
   deterministic policy evaluation, show full price and evidence, and can be
   dismissed without affecting the shortlist.
10. Review “My picks” alongside the interpreted dining needs and any unresolved
    waiter questions.
11. Re-run the policy engine and create a local `MP-H-####` handoff reference.
    Do not create a kitchen, POS, or payment transaction.
12. Let the guest show the summary to the waiter, who confirms availability,
    ingredients, preparation, substitutions, and the final order. When no live
    staff channel is configured, show a capability-aware reference state with
    a direct route back to **My Picks** instead of claiming staff was notified.
13. After the meal, ask five practical questions: food, service, wait time,
    order accuracy, and dietary confidence.
14. Let the guest choose optional highlights, add a restaurant-only draft, and
    request manager follow-up. In the local prototype, label the payload as
    locally validated and not delivered.
15. Generate an editable comment from the selected answers.
16. Offer private restaurant feedback and the Google route equally. For Google,
    require the guest to approve the edited experience summary as their public
    draft, then copy it and open the allowlisted external destination only
    after a tap; never publish or pre-fill a score.

## Architecture

The MVP uses four Gradle modules:

- `:app`: single-Activity Compose UI, Navigation 3, feature state, theme, and
  bundled fixtures.
- `:core:domain`: pure Kotlin menu, intent, allergen, recommendation, cart, and
  deterministic rules.
- `:core:assistant-contract`: pure Kotlin Qwen prompt, strict structured-output
  parser, canonical identifiers, and versioned evaluation rendering.
- `:ai-evals`: JVM-only Dokimos datasets and evaluation gates.

Package boundaries inside `:app` keep future extraction straightforward:

```text
com.menupilot.restaurant
├── app
├── data
├── feature.intake
├── feature.catalog
├── feature.order        # Current source location for shortlist/handoff UI
├── feature.feedback
├── assistant
├── voice
├── feedback
├── staff
├── external
└── design
```

Future production modules will separate Room persistence, model-pack delivery,
live menu/sales adapters, waiter notification, feedback delivery, benchmarks,
and shared design components only after the vertical slice establishes their
contracts. Direct ordering remains outside the current product scope.

## Assistant boundary

The assistant adapter returns an untrusted typed draft:

```text
AssistantRequest
  -> IntentAssistant
  -> AssistantResult(draft intent, clarification, candidate IDs, explanations)
```

The app uses a hybrid implementation. Supported allergies, diets, budgets,
preferences, and bestseller requests use the deterministic path immediately.
Long-tail requests may attempt the verified `qwen3_0_6b_mixed_int4.litertlm`
file through LiteRT-LM `0.14.0`, off the main thread and with explicit
cancellation, then release the large CPU engine after each inference. Missing,
invalid, rejected, or recoverably failed model runs use a labelled
deterministic fallback. Empty fallback interpretations fail closed to
clarification.

The shared Qwen contract validates exact fields, schema version, enum values,
canonical IDs, array/string sizes, price bounds, clarification invariants, and
prohibited safety claims. Model-authored prose is not rendered directly. The
adapter cannot inspect arbitrary database records, modify restaurant facts,
create a handoff, or submit an order.

## Data strategy

The MVP uses a versioned bundled menu fixture with:

- stable menu and POS IDs;
- variants and restaurant-approved modifications;
- price and availability;
- ingredients and dietary tags;
- allergen ingredient presence;
- cross-contact status;
- verification timestamp and recipe revision;
- aggregate weekly sales count;
- exact-variant restaurant-approved pairings; and
- timestamped aggregate basket-affinity evidence.
- restaurant-managed tablet placement (`TABLE` or `COUNTER`), location label,
  and staff-channel capability.

Room and live menu/sales synchronization follow after the local journey. The
eventual repository will apply complete menu snapshots transactionally and
retain the last verified snapshot for offline browsing.

## Android implementation standards

- Kotlin 2.4, AGP 9.2.1, Gradle 9.6.1, Java 17, Compose, Material 3,
  Navigation 3 1.1.4, compile SDK 37, and target SDK 37.
- Edge-to-edge from the first Activity.
- Tablet-first adaptive layouts without orientation locking.
- Stable APIs by default; experimental Grid/FlexBox APIs require an explicit
  product decision.
- Immutable UI state and unidirectional actions.
- Navigation passes stable IDs rather than domain objects.
- Large touch targets, semantic labels, and layouts that tolerate 1.5x font
  scaling.
- A Compose Preview Screenshot Testing matrix covering compact, medium, and
  expanded widths plus every Stitch journey stage; approved reference images
  are a visual-review gate.
- Official Android CLI and project-scoped Android skills are the source of
  truth for current Android workflows.

## Test and release gates

### Pull requests

- Compile and lint.
- Pure domain/rules tests.
- View-model and repository tests using fakes.
- Compose behavior tests.
- Fast Dokimos dataset evaluation with deterministic hard gates.
- Prove a handoff revalidates the exact menu revision, policy version, variant,
  recipe revision, and confirmed dining intent.
- Prove unresolved allergy questions remain attached to the waiter handoff and
  do not become silently approved.
- Prove no handoff path invokes a kitchen, POS, payment, or review-publishing
  integration.

### Device/nightly

- Complete guest journey on a tablet emulator.
- Real Qwen/LiteRT inference on a reference physical tablet.
- Export schema validity, parse failures, latency, memory, and fallback reasons.
- Evaluate recorded outputs with Dokimos.

### Hard AI gates

- 100% schema-valid accepted outputs.
- Zero unknown or forbidden dish IDs.
- Zero allergy false-safe recommendations.
- Required clarification for unresolved safety terms.
- No prohibited safety-guarantee wording.

LLM-as-judge evaluation is allowed only for tone and usefulness, never for an
allergen or menu-eligibility decision.

## Delivery milestones

1. **Foundation:** CLI-generated AGP 9 project, Navigation 3, theme, edge-to-edge,
   domain module, and baseline build.
2. **Safety engine:** menu models, hard/soft constraints, curated catalog, and
   deterministic tests.
3. **Guest journey:** intake, intent confirmation, adaptive catalog, dish
   details, shortlist, waiter handoff, and session reset.
4. **AI evaluation:** typed assistant port, deterministic MVP interpreter,
   Dokimos module, and regression dataset.
5. **On-device model spike:** Qwen3-0.6B LiteRT-LM loading, structured output,
   latency/memory measurements, and fallback behavior.
6. **Feedback prototype:** five-dimension survey, optional highlights,
   restaurant-only note and follow-up request, deterministic draft generation,
   neutral private/Google choice, clipboard copy, and allowlisted Google
   handoff.
7. **Restaurant integrations:** Room snapshots, live menu and sales evidence,
   authenticated waiter notification, private-feedback delivery, and a
   venue-provided Google Business Profile review link.
8. **Voice and ethical-pairing prototype:** tap-to-talk, on-device-only Android
   speech input, transcript confirmation, deterministic exact-variant
   recommendations, catalog enrichment, and an adaptive pairing pane.
9. **Concierge repositioning:** Stitch-led “best food for me” UI, maximum-three
   recommendation hierarchy, human waiter boundary, transparent pairing
   constraints, and no self-ordering language.
10. **Full Stitch journey:** Welcome, Intake, Recommendations, Dish Detail,
    Pairings, My Picks/Handoff, Waiting, Feedback, and Review Draft translated
    into one adaptive native Compose flow.
11. **Unified discovery:** dual Browse/Ask entry, responsive full-menu search
    and categories, shared dish details and My Picks, assistant-to-browse safety
    context, and the existing transparent pairing/waiter handoff.

## Implemented status

Milestones 1–6 and 8–11 are implemented locally in the current repository.
The concierge and feedback journeys run on a medium-tablet emulator, domain
and integration tests pass, and Dokimos evaluates a versioned multilingual
intent dataset through the production structured-output parser. Pairing
acceptance is statefully capped at two, and parser
regressions cover neutral allergen mentions, word boundaries, negated allergy,
negated spice, and non-medicalized generic avoidance. Table/counter copy is
driven from one venue tablet configuration instead of route-level constants.

Milestone 7 remains behind interfaces. Qwen/LiteRT code, verified provisioning,
fallback behavior, device artifact export, and Dokimos consumption are
implemented; the 475 MiB model is intentionally not bundled. A Xiaomi Redmi 13
physical-device run proved native inference, but standalone Qwen did not pass
the exact intent gate and required about 54 seconds for the revised flagship
prompt. It therefore remains outside the common critical path. Release
qualification still requires the intended restaurant tablet SKUs, the venue's
release signing key, and production Play AI-pack delivery. No production menu
sync, live waiter notification, private-feedback delivery, POS order, or
review publishing is claimed. Voice recognition requires Android 12+ and its
own installed speech model. Popularity and pairing evidence remains clearly
labelled fixture data until a production sales adapter supplies it.
