package com.menupilot.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesEvidencePolicyTest {
    private val now = Instant.parse("2026-07-20T04:00:00Z")
    private val source = MerchandisingEvidenceSource(
        id = "venue-pos",
        displayName = "Venue POS",
        kind = MerchandisingEvidenceSourceKind.POS,
    )
    private val policy = SalesEvidencePolicy()

    @Test
    fun `resolves an exact comparable window and deterministic bestseller ties`() {
        val first = variant("first")
        val second = variant("second")
        val third = variant("third")
        val resolution = policy.resolve(
            snapshot(
                signal(first, 42),
                signal(second, 9),
                signal(third, 42),
            ),
            now,
        ) as SalesEvidenceResolution.Ready

        assertEquals(source, resolution.source)
        assertEquals(now.minus(Duration.ofDays(7)), resolution.windowStart)
        assertEquals(now, resolution.windowEnd)
        assertEquals(setOf(first, third), resolution.bestsellerVariants)
        assertEquals(9L, resolution.orderCount(second))
    }

    @Test
    fun `mixed aggregation windows cannot support a bestseller comparison`() {
        val resolution = policy.resolve(
            snapshot(
                signal(variant("week"), 100),
                signal(
                    variant = variant("day"),
                    count = 200,
                    windowStart = now.minus(Duration.ofDays(1)),
                ),
            ),
            now,
        )

        assertUnavailable(resolution, SalesEvidenceIssueCode.MIXED_SOURCE_OR_WINDOW)
    }

    @Test
    fun `fresh import time cannot make an old sales period current`() {
        val staleWindowEnd = now.minus(Duration.ofDays(5))
        val resolution = policy.resolve(
            snapshot(
                signal(
                    variant = variant("old"),
                    count = 900,
                    windowStart = staleWindowEnd.minus(Duration.ofDays(7)),
                    windowEnd = staleWindowEnd,
                    observedAt = now,
                ),
            ),
            now,
        )

        assertUnavailable(resolution, SalesEvidenceIssueCode.STALE_EVIDENCE)
    }

    @Test
    fun `unknown provenance and future-ended windows fail closed`() {
        val unknownSource = policy.resolve(
            snapshot(
                signal(variant("dish"), 5).copy(sourceId = "unregistered"),
            ),
            now,
        )
        assertUnavailable(unknownSource, SalesEvidenceIssueCode.UNKNOWN_SOURCE)

        val futureWindow = policy.resolve(
            snapshot(
                signal(variant("dish"), 5).copy(
                    windowEnd = now.plusSeconds(30),
                    observedAt = now,
                ),
            ),
            now,
        )
        assertUnavailable(futureWindow, SalesEvidenceIssueCode.WINDOW_AFTER_OBSERVATION)
    }

    @Test
    fun `duplicate variant counts cannot be double interpreted`() {
        val dish = variant("dish")
        val resolution = policy.resolve(
            snapshot(signal(dish, 4), signal(dish, 5)),
            now,
        )

        assertUnavailable(resolution, SalesEvidenceIssueCode.DUPLICATE_VARIANT_SIGNAL)
    }

    private fun snapshot(vararg signals: SalesSignal) = MerchandisingSnapshot(
        menuRevision = "menu-1",
        revision = "merch-1",
        evidenceSources = listOf(source),
        salesSignals = signals.toList(),
    )

    private fun variant(id: String) = MenuVariantRef(
        itemId = MenuItemId(id),
        variantId = VariantId("standard"),
        recipeRevision = "recipe-1",
    )

    private fun signal(
        variant: MenuVariantRef,
        count: Long,
        windowStart: Instant = now.minus(Duration.ofDays(7)),
        windowEnd: Instant = now,
        observedAt: Instant = now,
    ) = SalesSignal(
        variant = variant,
        orderCount = count,
        windowStart = windowStart,
        windowEnd = windowEnd,
        observedAt = observedAt,
        sourceId = source.id,
    )

    private fun assertUnavailable(
        resolution: SalesEvidenceResolution,
        code: SalesEvidenceIssueCode,
    ) {
        assertTrue(
            "Expected Unavailable but was $resolution",
            resolution is SalesEvidenceResolution.Unavailable,
        )
        val unavailable = resolution as SalesEvidenceResolution.Unavailable
        assertTrue(unavailable.issues.any { it.code == code })
    }
}
