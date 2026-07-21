package com.menupilot.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffinityEvidencePolicyTest {
    private val now = Instant.parse("2026-07-20T04:00:00Z")
    private val source = MerchandisingEvidenceSource(
        id = "venue-pos",
        displayName = "Venue POS",
        kind = MerchandisingEvidenceSourceKind.POS,
    )
    private val anchor = ref("anchor")
    private val candidate = ref("candidate")
    private val policy = AffinityEvidencePolicy()

    @Test
    fun `one registered comparable window is accepted`() {
        val signal = affinity(anchor, candidate, 12)
        val resolution = policy.resolve(snapshot(signal), now)

        assertEquals(
            listOf(signal),
            (resolution as AffinityEvidenceResolution.Ready).signals,
        )
    }

    @Test
    fun `different valid sources cannot be compared by raw count`() {
        val secondSource = source.copy(id = "delivery-pos", displayName = "Delivery POS")
        val resolution = policy.resolve(
            snapshot(
                affinity(anchor, candidate, 12),
                affinity(ref("other-anchor"), ref("other-candidate"), 500)
                    .copy(sourceId = secondSource.id),
                sources = listOf(source, secondSource),
            ),
            now,
        )

        assertUnavailable(resolution, AffinityEvidenceIssueCode.MIXED_SOURCE_OR_WINDOW)
    }

    @Test
    fun `different aggregation windows cannot be compared`() {
        val resolution = policy.resolve(
            snapshot(
                affinity(anchor, candidate, 12),
                affinity(ref("other-anchor"), ref("other-candidate"), 30)
                    .copy(windowStart = now.minus(Duration.ofDays(1))),
            ),
            now,
        )

        assertUnavailable(resolution, AffinityEvidenceIssueCode.MIXED_SOURCE_OR_WINDOW)
    }

    @Test
    fun `duplicate anchor candidate facts are rejected rather than best picked`() {
        val signal = affinity(anchor, candidate, 12)
        val resolution = policy.resolve(
            snapshot(signal, signal.copy(coOrderCount = 99)),
            now,
        )

        assertUnavailable(resolution, AffinityEvidenceIssueCode.DUPLICATE_PAIR_SIGNAL)
    }

    @Test
    fun `ambiguous source registry and old evidence fail closed`() {
        val duplicateRegistry = policy.resolve(
            snapshot(
                affinity(anchor, candidate, 12),
                sources = listOf(source, source.copy(displayName = "Other")),
            ),
            now,
        )
        assertUnavailable(duplicateRegistry, AffinityEvidenceIssueCode.DUPLICATE_SOURCE_ID)

        val stale = policy.resolve(
            snapshot(
                affinity(
                    anchor = anchor,
                    candidate = candidate,
                    count = 12,
                    windowEnd = now.minus(Duration.ofDays(2)),
                    observedAt = now.minus(Duration.ofDays(2)),
                ),
            ),
            now,
        )
        assertUnavailable(stale, AffinityEvidenceIssueCode.STALE_EVIDENCE)
    }

    private fun snapshot(
        vararg signals: BasketAffinitySignal,
        sources: List<MerchandisingEvidenceSource> = listOf(source),
    ) = MerchandisingSnapshot(
        menuRevision = "menu-1",
        revision = "merch-1",
        evidenceSources = sources,
        affinitySignals = signals.toList(),
    )

    private fun ref(id: String) = MenuVariantRef(
        itemId = MenuItemId(id),
        variantId = VariantId("standard"),
        recipeRevision = "recipe-1",
    )

    private fun affinity(
        anchor: MenuVariantRef,
        candidate: MenuVariantRef,
        count: Long,
        windowStart: Instant = now.minus(Duration.ofDays(7)),
        windowEnd: Instant = now,
        observedAt: Instant = now,
    ) = BasketAffinitySignal(
        anchor = anchor,
        candidate = candidate,
        coOrderCount = count,
        windowStart = windowStart,
        windowEnd = windowEnd,
        observedAt = observedAt,
        sourceId = source.id,
    )

    private fun assertUnavailable(
        resolution: AffinityEvidenceResolution,
        code: AffinityEvidenceIssueCode,
    ) {
        assertTrue(
            "Expected Unavailable but was $resolution",
            resolution is AffinityEvidenceResolution.Unavailable,
        )
        assertTrue(
            (resolution as AffinityEvidenceResolution.Unavailable)
                .issues.any { it.code == code },
        )
    }
}
