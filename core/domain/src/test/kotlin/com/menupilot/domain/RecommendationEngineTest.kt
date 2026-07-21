package com.menupilot.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    private val now = Instant.parse("2026-07-20T04:00:00Z")
    private val peanut = AllergenId("peanut")
    private val engine = RecommendationEngine()
    private val posSource = MerchandisingEvidenceSource(
        id = "pos",
        displayName = "Test POS",
        kind = MerchandisingEvidenceSourceKind.POS,
    )

    @Test
    fun `high sales and approved pairing never rescue an allergen conflict`() {
        val menu = menu(
            item("anchor", passingVariant()),
            item("allowed", passingVariant()),
            item(
                "conflict",
                variant(
                    allergenFacts = mapOf(
                        peanut to AllergenFact(Presence.PRESENT, CrossContact.NONE_REPORTED),
                    ),
                ),
            ),
        )
        val anchor = menu.ref("anchor")
        val allowed = menu.ref("allowed")
        val conflict = menu.ref("conflict")
        val result = engine.recommend(
            request(
                menu = menu,
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = setOf(anchor),
                pairings = listOf(
                    ApprovedPairing(anchor, conflict, ApprovedPairingReason.COMPLEMENTS_DISH),
                    ApprovedPairing(anchor, allowed, ApprovedPairingReason.COMPLEMENTS_DISH),
                ),
                sales = listOf(sales(conflict, 10_000), sales(allowed, 1)),
            ),
        ).ready()

        assertEquals(listOf(allowed), result.recommendations.map(MenuRecommendation::variant))
        assertFalse(result.recommendations.any { it.variant == conflict })
    }

    @Test
    fun `unknown and stale allergen facts are never suggested`() {
        val menu = menu(
            item("current", passingVariant()),
            item("unknown", variant(allergenFacts = emptyMap())),
            item(
                "stale",
                passingVariant(verifiedAt = now.minus(Duration.ofDays(2))),
            ),
        )
        val result = engine.recommend(request(menu)).ready()

        assertEquals(listOf(menu.ref("current")), result.recommendations.map { it.variant })
    }

    @Test
    fun `cart and dismissed exact variants are excluded`() {
        val menu = menu(
            item("a", passingVariant()),
            item("b", passingVariant()),
            item("c", passingVariant()),
        )
        val result = engine.recommend(
            request(
                menu = menu,
                cart = setOf(menu.ref("a")),
                dismissed = setOf(menu.ref("b")),
            ),
        ).ready()

        assertEquals(listOf(menu.ref("c")), result.recommendations.map { it.variant })
    }

    @Test
    fun `cart complements require approved pairing or current affinity`() {
        val menu = menu(
            item("anchor", passingVariant()),
            item("affinity", passingVariant()),
            item("pairing", passingVariant()),
            item("popular_only", passingVariant()),
        )
        val anchor = menu.ref("anchor")
        val affinity = menu.ref("affinity")
        val pairing = menu.ref("pairing")
        val popularOnly = menu.ref("popular_only")
        val result = engine.recommend(
            request(
                menu = menu,
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = setOf(anchor),
                pairings = listOf(
                    ApprovedPairing(anchor, pairing, ApprovedPairingReason.COMPLETES_MEAL),
                ),
                sales = listOf(sales(popularOnly, 50_000)),
                affinities = listOf(affinity(anchor, affinity, 12)),
            ),
        ).ready()

        assertEquals(
            listOf(pairing, affinity),
            result.recommendations.map(MenuRecommendation::variant),
        )
        assertFalse(result.recommendations.any { it.variant == popularOnly })
    }

    @Test
    fun `stale sales are ignored as ranking and explanation evidence`() {
        val menu = menu(
            item("anchor", passingVariant()),
            item("a", passingVariant()),
            item("b", passingVariant()),
        )
        val anchor = menu.ref("anchor")
        val a = menu.ref("a")
        val b = menu.ref("b")
        val staleObservedAt = now.minus(Duration.ofDays(2))
        val result = engine.recommend(
            request(
                menu = menu,
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = setOf(anchor),
                pairings = listOf(
                    ApprovedPairing(anchor, a, ApprovedPairingReason.COMPLEMENTS_DISH),
                    ApprovedPairing(anchor, b, ApprovedPairingReason.COMPLEMENTS_DISH),
                ),
                sales = listOf(
                    sales(a, 100_000, observedAt = staleObservedAt),
                    sales(b, 2),
                ),
            ),
        ).ready()

        assertEquals(listOf(a, b), result.recommendations.map { it.variant })
        val staleRecommendation = result.recommendations.single { it.variant == a }
        assertFalse(
            staleRecommendation.reasons.any {
                it is RecommendationReason.PopularInWindow
            },
        )
        assertFalse(
            result.recommendations.single { it.variant == b }.reasons.any {
                it is RecommendationReason.PopularInWindow
            },
        )
    }

    @Test
    fun `stale affinity does not qualify a cart complement`() {
        val menu = menu(
            item("anchor", passingVariant()),
            item("candidate", passingVariant()),
        )
        val anchor = menu.ref("anchor")
        val candidate = menu.ref("candidate")
        val result = engine.recommend(
            request(
                menu = menu,
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = setOf(anchor),
                affinities = listOf(
                    affinity(
                        anchor = anchor,
                        candidate = candidate,
                        count = 99,
                        observedAt = now.minus(Duration.ofDays(2)),
                    ),
                ),
            ),
        ).ready()

        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun `duplicate source registry makes affinity unavailable`() {
        val menu = menu(
            item("anchor", passingVariant()),
            item("candidate", passingVariant()),
        )
        val anchor = menu.ref("anchor")
        val candidate = menu.ref("candidate")
        val base = request(
            menu = menu,
            placement = RecommendationPlacement.CART_COMPLEMENT,
            cart = setOf(anchor),
            affinities = listOf(affinity(anchor, candidate, 50)),
        )
        val result = engine.recommend(
            base.copy(
                merchandising = base.merchandising.copy(
                    evidenceSources = listOf(posSource, posSource.copy(displayName = "Other")),
                ),
            ),
        ).ready()

        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun `mixed affinity windows and duplicate pairs cannot qualify an upsell`() {
        val menu = menu(
            item("anchor", passingVariant()),
            item("candidate", passingVariant()),
        )
        val anchor = menu.ref("anchor")
        val candidate = menu.ref("candidate")
        val first = affinity(anchor, candidate, 8)
        val result = engine.recommend(
            request(
                menu = menu,
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = setOf(anchor),
                affinities = listOf(
                    first,
                    first.copy(
                        coOrderCount = 9,
                        windowStart = now.minus(Duration.ofDays(1)),
                    ),
                ),
            ),
        ).ready()

        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun `menu revision mismatch blocks recommendations`() {
        val menu = menu(item("dish", passingVariant()))
        val request = request(menu).copy(
            merchandising = MerchandisingSnapshot(
                menuRevision = "different-menu",
                revision = "merch-1",
            ),
        )

        val result = engine.recommend(request)

        assertTrue(result is RecommendationResolution.Blocked)
        val blocked = result as RecommendationResolution.Blocked
        assertTrue(
            blocked.reasons.any {
                it.code == RecommendationBlockCode.MENU_REVISION_MISMATCH
            },
        )
    }

    @Test
    fun `recipe revision mismatch in cart blocks recommendations`() {
        val menu = menu(item("dish", passingVariant()))
        val staleCartRef = menu.ref("dish").copy(recipeRevision = "old-recipe")

        val result = engine.recommend(
            request(menu = menu, cart = setOf(staleCartRef)),
        )

        assertTrue(result is RecommendationResolution.Blocked)
        val blocked = result as RecommendationResolution.Blocked
        assertTrue(
            blocked.reasons.any {
                it.code == RecommendationBlockCode.CART_VARIANT_MISMATCH
            },
        )
    }

    @Test
    fun `ordering is deterministic and capped at two`() {
        val firstMenu = menu(
            item("c", passingVariant()),
            item("a", passingVariant()),
            item("b", passingVariant()),
        )
        val secondMenu = firstMenu.copy(items = firstMenu.items.reversed())
        val firstSales = listOf(
            sales(firstMenu.ref("c"), 8),
            sales(firstMenu.ref("a"), 8),
            sales(firstMenu.ref("b"), 8),
        )
        val secondSales = firstSales.reversed()

        val first = engine.recommend(
            request(menu = firstMenu, sales = firstSales, requestedLimit = 99),
        ).ready()
        val second = engine.recommend(
            request(menu = secondMenu, sales = secondSales, requestedLimit = 99),
        ).ready()

        assertEquals(
            listOf(firstMenu.ref("a"), firstMenu.ref("b")),
            first.recommendations.map { it.variant },
        )
        assertEquals(first.recommendations, second.recommendations)
    }

    @Test
    fun `staff confirmation requirement is preserved in recommendation`() {
        val menu = menu(item("dish", passingVariant()))

        val recommendation = engine.recommend(request(menu))
            .ready()
            .recommendations
            .single()

        assertEquals(
            Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION,
            recommendation.eligibility,
        )
    }

    private fun request(
        menu: MenuSnapshot,
        placement: RecommendationPlacement = RecommendationPlacement.CATALOG_HIGHLIGHT,
        cart: Set<MenuVariantRef> = emptySet(),
        dismissed: Set<MenuVariantRef> = emptySet(),
        pairings: List<ApprovedPairing> = emptyList(),
        sales: List<SalesSignal> = emptyList(),
        affinities: List<BasketAffinitySignal> = emptyList(),
        requestedLimit: Int = 2,
    ) = RecommendationRequest(
        menu = menu,
        intent = allergyIntent(),
        merchandising = MerchandisingSnapshot(
            menuRevision = menu.revision,
            revision = "merch-1",
            evidenceSources = if (sales.isNotEmpty() || affinities.isNotEmpty()) {
                listOf(posSource)
            } else {
                emptyList()
            },
            approvedPairings = pairings,
            salesSignals = sales,
            affinitySignals = affinities,
        ),
        now = now,
        placement = placement,
        cart = cart,
        dismissed = dismissed,
        requestedLimit = requestedLimit,
    )

    private fun allergyIntent() = ConfirmedIntent(
        id = "intent",
        constraints = listOf(AvoidAllergen(peanut)),
    )

    private fun menu(vararg items: MenuItem) = MenuSnapshot(
        revision = "menu-1",
        generatedAt = now,
        items = items.toList(),
    )

    private fun item(
        id: String,
        variant: MenuVariant,
    ) = MenuItem(
        id = MenuItemId(id),
        name = id,
        variants = listOf(variant),
    )

    private fun passingVariant(
        verifiedAt: Instant = now,
    ) = variant(
        verifiedAt = verifiedAt,
        allergenFacts = mapOf(
            peanut to AllergenFact(Presence.NOT_LISTED, CrossContact.NONE_REPORTED),
        ),
    )

    private fun variant(
        verifiedAt: Instant = now,
        allergenFacts: Map<AllergenId, AllergenFact> = emptyMap(),
    ) = MenuVariant(
        id = VariantId("standard"),
        recipeRevision = "recipe-1",
        factsVerifiedAt = verifiedAt,
        allergenFacts = allergenFacts,
    )

    private fun MenuSnapshot.ref(itemId: String): MenuVariantRef {
        val item = items.single { it.id.value == itemId }
        val variant = item.variants.single()
        return MenuVariantRef(item.id, variant.id, variant.recipeRevision)
    }

    private fun sales(
        variant: MenuVariantRef,
        count: Long,
        observedAt: Instant = now,
    ) = SalesSignal(
        variant = variant,
        orderCount = count,
        windowStart = observedAt.minus(Duration.ofDays(7)),
        windowEnd = observedAt,
        observedAt = observedAt,
        sourceId = posSource.id,
    )

    private fun affinity(
        anchor: MenuVariantRef,
        candidate: MenuVariantRef,
        count: Long,
        observedAt: Instant = now,
    ) = BasketAffinitySignal(
        anchor = anchor,
        candidate = candidate,
        coOrderCount = count,
        windowStart = observedAt.minus(Duration.ofDays(7)),
        windowEnd = observedAt,
        observedAt = observedAt,
        sourceId = posSource.id,
    )

    private fun RecommendationResolution.ready(): RecommendationResolution.Ready {
        assertTrue("Expected Ready but was $this", this is RecommendationResolution.Ready)
        return this as RecommendationResolution.Ready
    }
}
