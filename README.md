# MenuPilot

MenuPilot is a restaurant-owned Android tablet concierge that helps a guest answer:
**“What food is best for me at this restaurant?”** A guest can browse the full menu at their own
pace or ask MenuPilot for help. Both paths share dish details, a guest-controlled shortlist,
policy-gated pairings, and a human waiter handoff.

The current vertical slice supports:

- English, Filipino, Taglish, and Cebuano phrase recognition;
- tap-to-talk, on-device voice transcription on Android 12+ with an editable
  transcript and keyboard fallback;
- allergy, coeliac, intolerance, diet, taste, budget, speed, and bestseller queries;
- optional Qwen3-0.6B intent interpretation through pinned LiteRT-LM `0.14.0`,
  with verified local model discovery and deterministic fallback;
- deterministic fail-closed menu filtering from versioned restaurant facts;
- tablet-adaptive Compose UI implementing the complete Stitch concierge
  journey: Welcome, Browse Menu, Intake, Recommendations, Dish Detail, Pairings,
  My Picks, Waiting, Feedback, and Review Draft;
- a searchable, category-filtered full menu with prices, ingredients, dietary
  and flavor tags, preparation time, and weekly bestseller evidence;
- one shared safety context across both modes: confirmed assistant constraints
  continue to hide conflicts and flag uncertain dishes when the guest switches
  to manual browsing;
- a focused catalog of at most three dishes, labelled as the best match,
  an alternative, or something to ask the waiter about;
- enriched cards with preparation time, diet/flavor tags, match reasons, full
  price, named sales windows, and demo-sales provenance;
- at most two accepted optional, dismissible pairings that are re-evaluated by
  the same policy engine, always fully priced, and never preselected;
- an exact item + variant + recipe-revision shortlist;
- final policy re-evaluation before a local waiter-handoff reference is created;
- explicit “this is a shortlist, not an order” messaging throughout the handoff;
- venue-configured table or counter placement and location copy, with no claim
  that staff was notified unless a future delivery integration confirms it;
- five practical feedback questions covering food, service, wait time, order
  accuracy, and dietary confidence;
- optional highlights, a private draft, and an editable generated public
  comment based only on the guest's answers;
- equally available private-feedback and Google Maps choices for every score,
  with no rating gate, incentive, or automatic publishing; the local prototype
  labels private delivery as unconnected rather than pretending it was sent,
  and requires explicit guest approval before using the generated experience
  summary as a public draft;
- deterministic JUnit tests and Dokimos evaluation gates.

## Run the app

The project uses the Android Studio bundled JDK on this machine:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
android run \
  --apks=app/build/outputs/apk/debug/app-debug.apk \
  --activity=com.menupilot.restaurant.MainActivity
```

The flagship prompt is:

> I'm allergic to peanuts, vegetarian, and want something spicy under ₱500.

Tap **Speak** to try voice entry. MenuPilot asks for microphone permission only
after that tap and uses Android's on-device recognizer; it does not silently
fall back to a cloud speech service. On Android 11 or earlier, or when the
tablet has no compatible on-device language model, the same request remains
available through the keyboard.

If a waiter chooses to confirm an unresolved allergy or preparation question on
the prototype tablet, the local demo PIN is `2468`. This optional confirmation
does not place an order. A production venue must replace it with
per-restaurant staff identity and audit infrastructure.

## Verification

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew \
  :app:testDebugUnitTest \
  :core:assistant-contract:test \
  :core:domain:test \
  :ai-evals:test \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:compileDebugAndroidTestKotlin \
  :app:compileDebugScreenshotTestKotlin
```

The same host checks are available as one repeatable restaurant-pilot gate:

```bash
./scripts/verify-pilot-confidence.sh
```

See the [95% restaurant-pilot confidence gate](docs/pilot-confidence.md) for
the scope, critical invariants, weighted evidence, and the separate Qwen
qualification boundary.

The adaptive screenshot-test matrix compiles compact, medium, and expanded
Welcome and Browse Menu layouts, 1.5x font scale, and representative previews
for every Stitch journey stage. Reference images are intentionally generated
only after the Stitch translation is visually approved.

The modules are:

- `:app` — Compose UI, Navigation 3, Hilt, local fixtures, concierge flow,
  feedback, and integrations.
- `:core:domain` — pure Kotlin menu policy engine and safety models.
- `:core:assistant-contract` — pure Kotlin Qwen prompt, strict output parser,
  canonical IDs, and versioned evaluation contract.
- `:ai-evals` — secret-free Dokimos contract fixtures plus an optional gate for
  raw model-run artifacts exported from Android.

## Safety contract

The assistant interprets language but never approves allergy-sensitive dishes. The pure policy
engine owns menu eligibility. Known conflicts are hidden, missing or stale facts fail closed, and
the UI never describes a dish as “safe” or “guaranteed.”

Waiter acknowledgment is invalidated whenever the guest changes the shortlist
or dining intent. Creating the handoff re-evaluates the current snapshot and
checks exact variant and recipe identity. Unresolved ingredient or preparation
questions remain visible for the waiter; a handoff never treats them as
approved.

The demo does not place an order or contact a live waiter system, kitchen, POS,
private-feedback backend, or Google account. Those boundaries are labelled in
the UI. The guest can return from the waiting/reference screen to **My Picks**,
then shows the generated handoff reference to a waiter, who owns the final
conversation and order.

Popularity, pairing, and co-order evidence currently comes from a versioned
fixture labelled **Demo POS sales record**. Recommendation evidence can rank
only dishes already permitted by the policy engine. Known conflicts, missing or
stale safety facts, unavailable variants, current shortlist items, and dismissed
suggestions cannot become upsells.

## Qwen3-0.6B and LiteRT

`IntentAssistant` is the model boundary. The app now injects a hybrid
implementation. Recognized allergies, diets, budgets, taste preferences, and
bestseller requests resolve immediately through deterministic local rules.
Long-tail requests may use `litert-community/Qwen3-0.6B` through the pinned
LiteRT-LM Android runtime when the verified model is installed. The UI
identifies which local path produced the draft.

The app does not bundle the 497,664,000-byte model or request network access.
For a managed debug tablet, install the app and provision the pinned model:

```bash
./scripts/provision-qwen3-model.sh --serial <adb-device-serial>
```

The script downloads the pinned Hugging Face revision, checks the exact size
and SHA-256, and copies it into app-private storage. Production Google Play
delivery should use a fast-follow Play for On-device AI pack instead of the
debug `run-as` workflow.

Model output remains untrusted. A shared strict parser checks the schema
version, exact fields, enums, canonical IDs, sizes, price bounds,
clarification invariants, and prohibited safety claims. Missing/rejected model
output falls back only when deterministic constraints were actually
recognized; an otherwise empty interpretation asks for clarification rather
than exposing an unfiltered “popular” menu. The deterministic interpreter
also supplies a hard floor for allergens, supported diets, and price limits.
The menu policy engine—not Qwen—owns every eligibility and upsell decision.

To export real Android outputs and evaluate them with Dokimos:

```bash
./scripts/run-qwen-dokimos-eval.sh --serial <physical-device-serial>
```

Use representative physical hardware. The pinned mixed-INT4 CPU model has a
large mobile memory footprint, and the current ARM emulator produced a native
unsupported-instruction failure while XNNPack initialized. MenuPilot therefore
declines LiteRT inference on emulators and keeps the deterministic path usable.

Qwen is not the speech recognizer. Android produces editable transcript text,
then the `IntentAssistant` boundary interprets it. Actual Filipino, Taglish,
Cebuano, or dialect voice coverage therefore depends on both the installed
Android speech model and the versioned on-device assistant evaluation set.

## Google Maps review handoff

The fixture uses a Google Maps search URL. Each deployed restaurant should
provide the review-request link generated by its verified Google Business
Profile. MenuPilot offers the private and Google choices equally for every
rating, validates a narrow HTTPS Google-host allowlist, copies only the
guest-edited public draft, and opens the destination only after a tap. A
private note is never copied to Google, and MenuPilot never fills a Google
rating or publishes a review automatically. The generated text is presented
as an editable experience summary and the Google Maps action stays disabled
until the guest explicitly approves it as their public draft.

See Google’s guidance for [creating a review request link or QR code](https://support.google.com/business/answer/16816815).

## Project notes

- [Implementation plan](docs/implementation-plan.md)
- [Restaurant-pilot 95% readiness gates](docs/pilot-readiness-95.md)
- [On-device model and Dokimos workflow](docs/on-device-model.md)
- [Intent-security alignment](docs/security-alignment.md)
- [Stitch design reference](docs/stitch-design.md)
