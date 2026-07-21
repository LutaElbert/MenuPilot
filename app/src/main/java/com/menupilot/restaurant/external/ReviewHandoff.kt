package com.menupilot.restaurant.external

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class ReviewDestinationKind {
    DIRECT_REVIEW_REQUEST,
    GOOGLE_MAPS_BUSINESS,
    GOOGLE_MAPS_SHORT_LINK,
}

enum class ReviewDestinationReadiness {
    DIRECT_REVIEW_READY,
    BUSINESS_PROFILE_FALLBACK,
    UNVERIFIED_SHORT_LINK,
    INVALID,
}

enum class ReviewHandoffRejection {
    DRAFT_NOT_APPROVED,
    INVALID_DESTINATION,
}

data class ApprovedReviewHandoffPlan(
    val publicDraft: String,
    val configuredUrl: String,
    val destinationKind: ReviewDestinationKind,
)

sealed interface ReviewHandoffPreparation {
    data class Ready(
        val plan: ApprovedReviewHandoffPlan,
    ) : ReviewHandoffPreparation

    data class Rejected(
        val reason: ReviewHandoffRejection,
    ) : ReviewHandoffPreparation
}

sealed interface ReviewHandoffResult {
    data object Opened : ReviewHandoffResult
    data object InvalidDestination : ReviewHandoffResult
    data object NoHandler : ReviewHandoffResult
}

fun assessReviewDestination(configuredUrl: String): ReviewDestinationReadiness =
    when (configuredUrl.reviewDestinationKind()) {
        ReviewDestinationKind.DIRECT_REVIEW_REQUEST ->
            ReviewDestinationReadiness.DIRECT_REVIEW_READY
        ReviewDestinationKind.GOOGLE_MAPS_BUSINESS ->
            ReviewDestinationReadiness.BUSINESS_PROFILE_FALLBACK
        ReviewDestinationKind.GOOGLE_MAPS_SHORT_LINK ->
            ReviewDestinationReadiness.UNVERIFIED_SHORT_LINK
        null -> ReviewDestinationReadiness.INVALID
    }

/**
 * Authorizes only the exact draft the guest approved and classifies what the configured Google
 * URL can honestly promise. This prepares a user-controlled handoff; it cannot publish a review.
 */
fun prepareGoogleReviewHandoff(
    configuredUrl: String,
    currentPublicDraft: String,
    approvedPublicDraft: String?,
): ReviewHandoffPreparation {
    if (
        currentPublicDraft.isBlank() ||
        currentPublicDraft.length > MAX_PUBLIC_REVIEW_LENGTH ||
        approvedPublicDraft != currentPublicDraft
    ) {
        return ReviewHandoffPreparation.Rejected(
            ReviewHandoffRejection.DRAFT_NOT_APPROVED,
        )
    }
    val destinationKind = configuredUrl.reviewDestinationKind()
        ?: return ReviewHandoffPreparation.Rejected(
            ReviewHandoffRejection.INVALID_DESTINATION,
        )
    return ReviewHandoffPreparation.Ready(
        ApprovedReviewHandoffPlan(
            publicDraft = currentPublicDraft,
            configuredUrl = configuredUrl,
            destinationKind = destinationKind,
        ),
    )
}

fun copyReviewToClipboard(context: Context, review: String): Boolean {
    if (review.isBlank()) return false
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("MenuPilot feedback draft", review))
    return true
}

/**
 * Opens only an application-owned, allowlisted HTTPS review destination.
 *
 * The URL comes from the restaurant configuration, not from an incoming Intent or guest input.
 * MenuPilot never includes the generated review as an Intent extra and never posts on the guest's
 * behalf.
 */
fun openGoogleReviewDestination(
    context: Context,
    configuredUrl: String,
): ReviewHandoffResult {
    val destination = configuredUrl.toAllowlistedReviewUri()
        ?: return ReviewHandoffResult.InvalidDestination
    val intent = Intent(Intent.ACTION_VIEW, destination).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    if (intent.resolveActivity(context.packageManager) == null) {
        return ReviewHandoffResult.NoHandler
    }
    return try {
        context.startActivity(intent)
        ReviewHandoffResult.Opened
    } catch (_: ActivityNotFoundException) {
        ReviewHandoffResult.NoHandler
    } catch (_: SecurityException) {
        ReviewHandoffResult.NoHandler
    }
}

internal fun String.toAllowlistedReviewUri(): Uri? {
    if (!isAllowlistedReviewDestination()) return null
    return toUri()
}

internal fun String.isAllowlistedReviewDestination(): Boolean {
    return reviewDestinationKind() != null
}

internal fun String.reviewDestinationKind(): ReviewDestinationKind? {
    if (isBlank() || this != trim()) return null
    val uri = runCatching { URI(this) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (
        uri.isOpaque ||
        !uri.scheme.equals("https", ignoreCase = true) ||
        host !in allowedReviewHosts ||
        uri.userInfo != null ||
        uri.port !in listOf(-1, 443) ||
        uri.rawFragment != null
    ) {
        return null
    }

    return when (host) {
        "search.google.com" ->
            ReviewDestinationKind.DIRECT_REVIEW_REQUEST
                .takeIf { uri.isGoogleWriteReviewDestination() }
        "g.page" ->
            ReviewDestinationKind.DIRECT_REVIEW_REQUEST
                .takeIf { uri.isGoogleBusinessReviewShortLink() }
        "maps.app.goo.gl" ->
            ReviewDestinationKind.GOOGLE_MAPS_SHORT_LINK
                .takeIf { uri.isGoogleMapsShortLink() }
        "www.google.com",
        "google.com",
        "maps.google.com",
        -> ReviewDestinationKind.GOOGLE_MAPS_BUSINESS
            .takeIf { uri.isGoogleMapsBusinessDestination() }
        else -> null
    }
}

private val allowedReviewHosts = setOf(
    "www.google.com",
    "google.com",
    "maps.google.com",
    "search.google.com",
    "maps.app.goo.gl",
    "g.page",
)

private fun URI.isGoogleWriteReviewDestination(): Boolean {
    val path = rawPath?.removeSuffix("/") ?: return false
    val parameters = decodedQueryParameters() ?: return false
    return path == "/local/writereview" &&
        parameters.keys == setOf("placeid") &&
        parameters["placeid"].orEmpty().singleOrNull()?.isNotBlank() == true
}

private fun URI.isGoogleBusinessReviewShortLink(): Boolean {
    if (rawQuery != null) return false
    return rawPath.orEmpty().matches(Regex("""/r/[A-Za-z0-9_-]+/review/?"""))
}

private fun URI.isGoogleMapsShortLink(): Boolean {
    if (rawQuery != null) return false
    return rawPath.orEmpty().matches(Regex("""/[A-Za-z0-9_-]+/?"""))
}

private fun URI.isGoogleMapsBusinessDestination(): Boolean {
    val path = rawPath.orEmpty().removeSuffix("/")
    val parameters = decodedQueryParameters() ?: return false
    return when {
        path == "/maps/search" -> parameters.isMapsSearchQuery()
        path == "/maps" -> {
            (
                parameters.keys == setOf("cid") &&
                    parameters["cid"].orEmpty().singleOrNull()?.isNotBlank() == true
                ) || parameters.isMapsSearchQuery()
        }
        path.startsWith("/maps/place/") ->
            path.removePrefix("/maps/place/").isNotBlank() && parameters.isEmpty()
        else -> false
    }
}

private fun Map<String, List<String>>.isMapsSearchQuery(): Boolean =
    keys.all { it in setOf("api", "query", "query_place_id") } &&
        keys.containsAll(setOf("api", "query")) &&
        get("api").orEmpty().singleOrNull() == "1" &&
        get("query").orEmpty().singleOrNull()?.isNotBlank() == true &&
        get("query_place_id").orEmpty().let { it.isEmpty() || it.singleOrNull()?.isNotBlank() == true }

private fun URI.decodedQueryParameters(): Map<String, List<String>>? {
    if (rawQuery == null) return emptyMap()
    return runCatching {
        rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotEmpty)
            .map { parameter ->
                val separator = parameter.indexOf('=')
                val rawName = if (separator >= 0) {
                    parameter.substring(0, separator)
                } else {
                    parameter
                }
                val rawValue = if (separator >= 0) parameter.substring(separator + 1) else ""
                URLDecoder.decode(rawName, StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
            }
            .groupBy({ it.first }, { it.second })
            .takeIf { "" !in it }
    }.getOrNull()
}

private const val MAX_PUBLIC_REVIEW_LENGTH = 2_000
