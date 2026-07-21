package com.menupilot.restaurant.data

import com.menupilot.domain.MerchandisingEvidenceSource
import com.menupilot.domain.SalesEvidenceResolution
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SalesEvidencePresentation(
    val windowLabel: String,
    val sourceLabel: String,
    val updatedLabel: String,
    val available: Boolean,
) {
    companion object {
        fun from(resolution: SalesEvidenceResolution): SalesEvidencePresentation =
            when (resolution) {
                is SalesEvidenceResolution.Ready -> from(
                    source = resolution.source,
                    windowStart = resolution.windowStart,
                    windowEnd = resolution.windowEnd,
                    observedAt = resolution.observedAt,
                )
                is SalesEvidenceResolution.Unavailable -> SalesEvidencePresentation(
                    windowLabel = "No comparable sales window",
                    sourceLabel = "Sales evidence unavailable",
                    updatedLabel = "Ask the restaurant for current popularity",
                    available = false,
                )
            }

        fun from(
            source: MerchandisingEvidenceSource,
            windowStart: Instant,
            windowEnd: Instant,
            observedAt: Instant,
        ): SalesEvidencePresentation {
            val duration = Duration.between(windowStart, windowEnd)
            val windowLabel = when (duration) {
                Duration.ofHours(24) -> "Trailing 24 hours"
                Duration.ofDays(7) -> "Trailing 7 days"
                else -> "${dateFormatter.format(windowStart)}–${dateFormatter.format(windowEnd)} UTC"
            }
            return SalesEvidencePresentation(
                windowLabel = windowLabel,
                sourceLabel = source.displayName,
                updatedLabel = "Observed ${timestampFormatter.format(observedAt)} UTC",
                available = true,
            )
        }

        private val dateFormatter = DateTimeFormatter.ofPattern("d MMM uuuu")
            .withZone(ZoneOffset.UTC)
        private val timestampFormatter = DateTimeFormatter.ofPattern("d MMM uuuu HH:mm")
            .withZone(ZoneOffset.UTC)
    }
}
