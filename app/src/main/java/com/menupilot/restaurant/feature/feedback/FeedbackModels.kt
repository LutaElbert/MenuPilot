package com.menupilot.restaurant.feature.feedback

import com.menupilot.restaurant.feedback.FeedbackSurveyDimensions
import com.menupilot.restaurant.feedback.MenuPilotFeedbackTags

enum class FeedbackStage {
    RATINGS,
    TAGS,
    REVIEW,
}

typealias FeedbackDimension = com.menupilot.restaurant.feedback.FeedbackDimension
typealias FeedbackTag = com.menupilot.restaurant.feedback.FeedbackTag

val DefaultFeedbackTags = MenuPilotFeedbackTags

enum class FeedbackDestination {
    PRIVATE_RESTAURANT,
    GOOGLE_REVIEW,
}

data class PrivateFeedbackSubmission(
    val ratings: Map<FeedbackDimension, Int>,
    val selectedTagIds: Set<String>,
    val editedDraft: String,
    val privateNote: String,
    val managerFollowUpRequested: Boolean,
)

data class FeedbackUiState(
    val stage: FeedbackStage = FeedbackStage.RATINGS,
    val ratings: Map<FeedbackDimension, Int> = emptyMap(),
    val availableTags: List<FeedbackTag> = DefaultFeedbackTags,
    val selectedTagIds: Set<String> = emptySet(),
    val generatedReview: String = "",
    val approvedPublicDraft: String? = null,
    val privateNote: String = "",
    val managerFollowUpRequested: Boolean = false,
    val restaurantName: String = "the restaurant",
    val reviewCopied: Boolean = false,
    val privateSubmissionRecorded: Boolean = false,
    val isBusy: Boolean = false,
) {
    val publicDraftApproved: Boolean
        get() = generatedReview.isNotBlank() && approvedPublicDraft == generatedReview

    fun ratingFor(dimension: FeedbackDimension): Int = ratings[dimension] ?: 0

    val allRatingsComplete: Boolean
        get() = FeedbackSurveyDimensions.all { ratingFor(it) in 1..5 }

    /**
     * Both destinations are always offered. Ratings never determine whether a guest can choose
     * private feedback or continue to Google's review surface.
     */
    val availableDestinations: List<FeedbackDestination>
        get() = FeedbackDestination.entries

    fun toPrivateSubmission(): PrivateFeedbackSubmission =
        PrivateFeedbackSubmission(
            ratings = FeedbackSurveyDimensions.associateWith(::ratingFor),
            selectedTagIds = selectedTagIds,
            editedDraft = generatedReview,
            privateNote = privateNote,
            managerFollowUpRequested = managerFollowUpRequested,
        )
}
