package com.menupilot.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuPolicyEngineTest {
    private val now: Instant = Instant.parse("2026-07-19T04:00:00Z")
    private val peanut = AllergenId("peanut")
    private val engine = MenuPolicyEngine()

    @Test
    fun `listed allergen is a known conflict`() {
        val result = evaluate(
            variant(
                allergenFacts = mapOf(
                    peanut to AllergenFact(Presence.PRESENT, CrossContact.NONE_REPORTED),
                ),
            ),
            allergyIntent(),
        )

        val evaluation = result.singleEvaluation()
        assertEquals(Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT, evaluation.eligibility)
        assertTrue(evaluation.hasReason(DecisionCode.ALLERGEN_PRESENT))
    }

    @Test
    fun `known or possible cross contact is a conflict`() {
        listOf(
            CrossContact.KNOWN_RISK to DecisionCode.KNOWN_CROSS_CONTACT_RISK,
            CrossContact.POSSIBLE_RISK to DecisionCode.POSSIBLE_CROSS_CONTACT_RISK,
        ).forEach { (crossContact, expectedReason) ->
            val result = evaluate(
                variant(
                    allergenFacts = mapOf(
                        peanut to AllergenFact(Presence.NOT_LISTED, crossContact),
                    ),
                ),
                allergyIntent(),
            )

            val evaluation = result.singleEvaluation()
            assertEquals(Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT, evaluation.eligibility)
            assertTrue(evaluation.hasReason(expectedReason))
        }
    }

    @Test
    fun `missing allergen fact fails closed`() {
        val result = evaluate(variant(allergenFacts = emptyMap()), allergyIntent())

        val evaluation = result.singleEvaluation()
        assertEquals(Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA, evaluation.eligibility)
        assertTrue(evaluation.hasReason(DecisionCode.ALLERGEN_DATA_UNKNOWN))
    }

    @Test
    fun `stale facts fail closed even when allergen is not listed`() {
        val result = evaluate(
            variant(
                verifiedAt = now.minus(Duration.ofDays(2)),
                allergenFacts = passingPeanutFacts(),
            ),
            allergyIntent(),
        )

        val evaluation = result.singleEvaluation()
        assertEquals(Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA, evaluation.eligibility)
        assertTrue(evaluation.hasReason(DecisionCode.FACTS_STALE))
    }

    @Test
    fun `current not-listed facts still require staff confirmation`() {
        val result = evaluate(
            variant(allergenFacts = passingPeanutFacts()),
            allergyIntent(),
        )

        val evaluation = result.singleEvaluation()
        assertEquals(Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION, evaluation.eligibility)
        assertTrue(evaluation.hasReason(DecisionCode.STAFF_CONFIRMATION_REQUIRED))
        assertTrue(result is MenuResolution.CatalogReady)
    }

    @Test
    fun `soft dislike lowers ranking but never excludes`() {
        val mushroom = IngredientId("mushroom")
        val intent = ConfirmedIntent(
            id = "intent",
            constraints = listOf(
                AvoidIngredient(
                    ingredientId = mushroom,
                    reason = AvoidanceReason.DISLIKE,
                    strength = ConstraintStrength.SOFT,
                ),
            ),
        )
        val result = evaluate(
            variant(ingredientFacts = mapOf(mushroom to Presence.PRESENT)),
            intent,
        )

        val evaluation = result.singleEvaluation()
        assertEquals(Eligibility.CANDIDATE, evaluation.eligibility)
        assertEquals(-1, evaluation.softScore)
        assertTrue(evaluation.hasReason(DecisionCode.SOFT_PREFERENCE_MISMATCH))
    }

    @Test
    fun `explicit hard attribute avoidance excludes while soft avoidance only reranks`() {
        val hardIntent = ConfirmedIntent(
            id = "hard",
            constraints = listOf(
                AvoidAttribute("spicy", strength = ConstraintStrength.HARD),
            ),
        )
        val softIntent = ConfirmedIntent(
            id = "soft",
            constraints = listOf(AvoidAttribute("spicy")),
        )
        val dish = variant(attributes = setOf("spicy"))

        assertEquals(
            Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT,
            evaluate(dish, hardIntent).singleEvaluation().eligibility,
        )
        assertEquals(
            Eligibility.CANDIDATE,
            evaluate(dish, softIntent).singleEvaluation().eligibility,
        )
    }

    @Test
    fun `hard constraints are never relaxed when no items match`() {
        val result = evaluate(
            variant(
                allergenFacts = mapOf(
                    peanut to AllergenFact(Presence.PRESENT, CrossContact.KNOWN_RISK),
                ),
                attributes = setOf("popular"),
            ),
            ConfirmedIntent(
                id = "intent",
                constraints = listOf(
                    AvoidAllergen(peanut),
                    PreferAttribute("popular", weight = 100),
                ),
            ),
        )

        assertTrue(result is MenuResolution.NoMatches)
        assertEquals(
            Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT,
            result.singleEvaluation().eligibility,
        )
    }

    @Test
    fun `hard price limit excludes while a soft limit only lowers ranking`() {
        val expensiveDish = variant(priceMinor = 65000)

        val hardResult = evaluate(
            expensiveDish,
            ConfirmedIntent(
                id = "hard-budget",
                constraints = listOf(
                    PriceLimit(
                        maximumPriceMinor = 50000,
                        strength = ConstraintStrength.HARD,
                    ),
                ),
            ),
        )
        assertEquals(
            Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT,
            hardResult.singleEvaluation().eligibility,
        )
        assertTrue(hardResult.singleEvaluation().hasReason(DecisionCode.PRICE_LIMIT_EXCEEDED))

        val softResult = evaluate(
            expensiveDish,
            ConfirmedIntent(
                id = "soft-budget",
                constraints = listOf(PriceLimit(maximumPriceMinor = 50000)),
            ),
        )
        assertEquals(Eligibility.CANDIDATE, softResult.singleEvaluation().eligibility)
        assertEquals(-1, softResult.singleEvaluation().softScore)
    }

    @Test
    fun `safety avoidance cannot be supplied as a soft preference`() {
        val result = evaluate(
            variant(allergenFacts = passingPeanutFacts()),
            ConfirmedIntent(
                id = "intent",
                constraints = listOf(
                    AvoidAllergen(
                        allergenId = peanut,
                        reason = AvoidanceReason.ALLERGY,
                        strength = ConstraintStrength.SOFT,
                    ),
                ),
            ),
        )

        assertTrue(result is MenuResolution.ClarificationRequired)
        val issues = (result as MenuResolution.ClarificationRequired).issues
        assertTrue(issues.any { it.code == ClarificationCode.SAFETY_CONSTRAINT_MUST_BE_HARD })
    }

    @Test
    fun `multi-allergen request fails when any fact is unknown`() {
        val sesame = AllergenId("sesame")
        val result = evaluate(
            variant(allergenFacts = passingPeanutFacts()),
            ConfirmedIntent(
                id = "intent",
                constraints = listOf(AvoidAllergen(peanut), AvoidAllergen(sesame)),
            ),
        )

        assertEquals(
            Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA,
            result.singleEvaluation().eligibility,
        )
    }

    @Test
    fun `evaluation is idempotent and input order independent`() {
        val first = menu(
            listOf(
                item("b", variant(id = "default")),
                item("a", variant(id = "default")),
            ),
        )
        val second = menu(first.items.reversed())
        val intent = ConfirmedIntent(
            id = "intent",
            constraints = listOf(PreferAttribute("popular")),
        )

        val resultOne = engine.evaluateMenu(first, intent, now)
        val resultTwo = engine.evaluateMenu(second, intent, now)

        assertEquals(resultOne, engine.evaluateMenu(first, intent, now))
        assertEquals(resultOne, resultTwo)
    }

    @Test
    fun `recipe revision change produces a result tied to the new menu revision`() {
        val intent = allergyIntent()
        val oldResult = engine.evaluateMenu(
            menu(
                items = listOf(item("dish", variant(allergenFacts = passingPeanutFacts()))),
                revision = "menu-1",
            ),
            intent,
            now,
        )
        val newResult = engine.evaluateMenu(
            menu(
                items = listOf(
                    item(
                        "dish",
                        variant(
                            recipeRevision = "recipe-2",
                            allergenFacts = mapOf(
                                peanut to AllergenFact(Presence.PRESENT, CrossContact.NONE_REPORTED),
                            ),
                        ),
                    ),
                ),
                revision = "menu-2",
            ),
            intent,
            now,
        )

        assertEquals("menu-1", (oldResult as MenuResolution.CatalogReady).menuRevision)
        assertEquals("menu-2", (newResult as MenuResolution.NoMatches).menuRevision)
        assertFalse(newResult.singleEvaluation().eligibility == Eligibility.CANDIDATE)
    }

    private fun evaluate(
        variant: MenuVariant,
        intent: ConfirmedIntent,
    ): MenuResolution = engine.evaluateMenu(
        snapshot = menu(listOf(item("dish", variant))),
        intent = intent,
        now = now,
    )

    private fun allergyIntent(): ConfirmedIntent = ConfirmedIntent(
        id = "intent",
        constraints = listOf(AvoidAllergen(peanut)),
    )

    private fun passingPeanutFacts(): Map<AllergenId, AllergenFact> = mapOf(
        peanut to AllergenFact(Presence.NOT_LISTED, CrossContact.NONE_REPORTED),
    )

    private fun menu(
        items: List<MenuItem>,
        revision: String = "menu-1",
    ): MenuSnapshot = MenuSnapshot(
        revision = revision,
        generatedAt = now,
        items = items,
    )

    private fun item(
        id: String,
        variant: MenuVariant,
    ): MenuItem = MenuItem(
        id = MenuItemId(id),
        name = id,
        variants = listOf(variant),
    )

    private fun variant(
        id: String = "default",
        recipeRevision: String = "recipe-1",
        verifiedAt: Instant? = now,
        allergenFacts: Map<AllergenId, AllergenFact> = emptyMap(),
        ingredientFacts: Map<IngredientId, Presence> = emptyMap(),
        attributes: Set<String> = emptySet(),
        priceMinor: Long = 0,
    ): MenuVariant = MenuVariant(
        id = VariantId(id),
        recipeRevision = recipeRevision,
        factsVerifiedAt = verifiedAt,
        allergenFacts = allergenFacts,
        ingredientFacts = ingredientFacts,
        attributes = attributes,
        priceMinor = priceMinor,
    )

    private fun MenuResolution.singleEvaluation(): ItemEvaluation = when (this) {
        is MenuResolution.CatalogReady -> evaluations.single()
        is MenuResolution.NoMatches -> evaluations.single()
        else -> error("Expected evaluated catalog, got $this")
    }

    private fun ItemEvaluation.hasReason(code: DecisionCode): Boolean =
        reasons.any { it.code == code }
}
