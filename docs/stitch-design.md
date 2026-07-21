# MenuPilot Stitch design reference

Google Stitch was used as the visual specification for MenuPilot's repositioning
from a self-ordering interface to a table-side personal menu concierge. The
Android app remains native Jetpack Compose; generated web code was not copied
into the project.

## Primary design project

- Project: **MenuPilot Concierge Tablet UI**
- Project ID: `11588478563526937804`
- Design-system asset:
  `asset-stub-assets_d2fb6eb4d3d84298950bdc62dce67cdd`
- Welcome: `f625f45fdc564a78b40f6dfac417d998`
- Intake: `b98fc7553c254eee9dfe2908ef8ff4a1`
- Recommendations: `dbaed04121ae4bd6a61a7905e38a4ee0`
- Dish detail: `bc672bfc2e9b41e189006b0fac9f0709`
- Pairings: `810e21f7ba934b58a4c628e98b221256`
- Waiter-review handoff: `be0cc7db7a86492e83f3d837025cf879`
- Waiting / show-reference state:
  `df3b0498c28745159d3b327511c90d8e`
- Feedback: `5ba212049517407888319070d17becd5`
- Editable review draft: `79b2c71b31da482a955707a267ee47df`

The approved design system uses Work Sans for interface text, Source Serif 4
for editorial headings, terracotta `#A43716`, warm background `#FFF8F6`, and
teal `#536162` for supporting confidence and safety states. The fonts are
bundled so the restaurant-owned tablet does not depend on a network font
provider. The private Stitch project uses a landscape desktop-sized canvas as
the visual reference; the Android implementation translates it into adaptive
tablet and compact layouts with 48 dp minimum touch targets.

The implemented screen order is:

```text
Welcome ─┬→ Browse Menu ───────────────┐
         └→ Intake → Recommendations ──┼→ Dish detail
                                      └→ Pairings → My Picks / waiter review
                                         → Waiting or show reference
                                         → Feedback → Editable review draft
```

The personalized results screen can open **Browse full menu** without dropping
the confirmed dining intent. Known conflicts remain hidden and uncertain menu
facts remain visibly subject to waiter and kitchen confirmation.

The waiting copy is capability-aware. It may say that a waiter was notified
only when a venue has configured a live staff channel. The local prototype
instead asks the guest to show the prominent handoff reference and provides a
direct **Show My Picks** action.

## Authoritative implementation rules

- Voice never listens continuously and never submits a transcript
  automatically.
- The guest verifies or edits transcript text before intent interpretation.
- The product question is “What food is best for me here?”, not “How quickly
  can I submit an order?”
- Results are focused to at most three: best match, alternative, and ask the
  waiter.
- Manual browsing remains available as a searchable, category-filtered catalog
  and carries forward any confirmed assistant constraints.
- Safety status is written in text and never communicated by color alone.
- No dish is described as “safe” or “guaranteed.”
- The guest builds “My picks,” not a cart. The final action creates a human
  waiter handoff, not a restaurant order.
- Pairings are generated only after an item is selected, capped at two, never
  preselected, show their full price and reason, and can be dismissed. The cap
  applies to accepted pairings, not only to the number visible at once.
- Sales claims identify their time window and fixture/live provenance.
- Recommendation evidence may rank only candidates permitted by the
  deterministic menu policy engine.
- The feedback draft is editable. Private restaurant feedback and the Google
  route are always equally visible, regardless of rating.
- A generated experience summary becomes a Google Maps public draft only after
  the guest explicitly approves it; any edit clears that approval.
- The fixture opens a Google Maps business search. Production venues must
  configure their verified Business Profile review-request link; private
  delivery likewise remains explicitly labelled as unconnected in the demo.
- Google receives no automatic post, rating, private note, incentive, or
  gated/selected guest cohort.

## Earlier exploration retained for provenance

- Earlier concierge project: `5491942606560868418`
- Earlier concierge design system: `assets/13889099789026581891`
- Earlier welcome: `f04bba1e66cc4d2f975f86faccdf9350`
- Earlier recommendations: `8f0b164179c842b3bcad2836187c59f4`
- Earlier handoff: `6bf66f3123a44e0c99456c3a37b8580d`
- Guided catalog + supporting pane: `b79d7b5f48814f3b92b6da6a52df1173`
- Actively listening voice state: `cc1f360a3dff437ba7a523c59d54b892`
- Editable transcript state: `7aca8e490c574b00855451d78d3b4b55`
- Parallel project: `10148448442685061664`
- Parallel design system: `assets/7854395667751038441`
- Parallel voice-guided catalog: `d1ab7e9420cd4d6281bddcefd853188a`
- Parallel dish detail + pairings: `55e9b2aaa31a4f8199264c50a292ddf5`
