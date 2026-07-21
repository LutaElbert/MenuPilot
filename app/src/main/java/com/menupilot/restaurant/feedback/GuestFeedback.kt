package com.menupilot.restaurant.feedback

enum class FeedbackDimension(
    val label: String,
    val question: String,
) {
    FOOD("Food", "How was the food?"),
    SERVICE("Service", "How was the service?"),
    WAIT_TIME("Wait time", "How satisfied were you with the wait time?"),
    ORDER_ACCURACY("Order accuracy", "How accurately did your meal match what you requested?"),
    DIETARY_CONFIDENCE(
        "Dietary confidence",
        "How clear was the dietary or allergy information?",
    ),
}

val FeedbackSurveyDimensions: List<FeedbackDimension> = FeedbackDimension.entries

data class GuestFeedback(
    val foodRating: Int? = null,
    val serviceRating: Int? = null,
    val selectedTagIds: Set<String> = emptySet(),
    val waitTimeRating: Int? = null,
    val orderAccuracyRating: Int? = null,
    val dietaryConfidenceRating: Int? = null,
) {
    val isComplete: Boolean
        get() = FeedbackSurveyDimensions.all { ratings[it] != null }

    val ratings: Map<FeedbackDimension, Int?>
        get() = mapOf(
            FeedbackDimension.FOOD to foodRating,
            FeedbackDimension.SERVICE to serviceRating,
            FeedbackDimension.WAIT_TIME to waitTimeRating,
            FeedbackDimension.ORDER_ACCURACY to orderAccuracyRating,
            FeedbackDimension.DIETARY_CONFIDENCE to dietaryConfidenceRating,
        )

    fun withRating(dimension: FeedbackDimension, rating: Int): GuestFeedback {
        require(rating in 1..5) { "Feedback ratings must be between 1 and 5." }
        return when (dimension) {
            FeedbackDimension.FOOD -> copy(foodRating = rating)
            FeedbackDimension.SERVICE -> copy(serviceRating = rating)
            FeedbackDimension.WAIT_TIME -> copy(waitTimeRating = rating)
            FeedbackDimension.ORDER_ACCURACY -> copy(orderAccuracyRating = rating)
            FeedbackDimension.DIETARY_CONFIDENCE -> copy(dietaryConfidenceRating = rating)
        }
    }

    fun toggleTag(tagId: String): GuestFeedback = copy(
        selectedTagIds = if (tagId in selectedTagIds) {
            selectedTagIds - tagId
        } else {
            selectedTagIds + tagId
        },
    )
}

data class FeedbackTag(
    val id: String,
    val label: String,
    val sentiment: FeedbackSentiment,
)

enum class FeedbackSentiment {
    POSITIVE,
    IMPROVEMENT,
}

val MenuPilotFeedbackTags = listOf(
    FeedbackTag("flavorful_food", "Flavorful food", FeedbackSentiment.POSITIVE),
    FeedbackTag("fresh_ingredients", "Fresh ingredients", FeedbackSentiment.POSITIVE),
    FeedbackTag("attentive_service", "Attentive service", FeedbackSentiment.POSITIVE),
    FeedbackTag("quick_service", "Reasonable wait", FeedbackSentiment.POSITIVE),
    FeedbackTag("accurate_order", "Order as requested", FeedbackSentiment.POSITIVE),
    FeedbackTag(
        "clear_dietary_information",
        "Clear dietary information",
        FeedbackSentiment.POSITIVE,
    ),
    FeedbackTag("long_wait", "Long wait", FeedbackSentiment.IMPROVEMENT),
    FeedbackTag("food_temperature", "Food temperature", FeedbackSentiment.IMPROVEMENT),
    FeedbackTag("order_accuracy", "Order needed correction", FeedbackSentiment.IMPROVEMENT),
    FeedbackTag(
        "unclear_dietary_information",
        "Unclear dietary information",
        FeedbackSentiment.IMPROVEMENT,
    ),
)
