package com.menupilot.restaurant.feedback

import javax.inject.Inject

class FeedbackCommentGenerator @Inject constructor() {

    fun generate(feedback: GuestFeedback): String {
        require(feedback.isComplete) {
            "Every feedback dimension must be answered before generating a draft."
        }

        val ratings = feedback.ratings.mapValues { (_, rating) -> requireNotNull(rating) }
        val average = ratings.values.average()
        val sentences = mutableListOf(overallSentence(average))

        val strongest = ratings.maxByOrNull { it.value }
        val weakest = ratings.minByOrNull { it.value }
        strongest?.takeIf { it.value >= 4 }?.let {
            sentences += positiveDimensionSentence(it.key)
        }
        weakest?.takeIf {
            it.value <= 3 && (strongest == null || it.key != strongest.key)
        }?.let {
            sentences += improvementDimensionSentence(it.key)
        }

        val chosenTags = MenuPilotFeedbackTags.filter { it.id in feedback.selectedTagIds }
        val positives = chosenTags.filter { it.sentiment == FeedbackSentiment.POSITIVE }
        val improvements = chosenTags.filter { it.sentiment == FeedbackSentiment.IMPROVEMENT }
        if (positives.isNotEmpty()) {
            sentences += "What stood out to me: ${positives.joinLabels()}."
        }
        if (improvements.isNotEmpty()) {
            sentences += "Areas that could improve: ${improvements.joinLabels()}."
        }

        return sentences.joinToString(" ")
    }

    private fun overallSentence(average: Double): String = when {
        average >= 4.5 -> "Overall, I had an excellent experience."
        average >= 3.5 -> "Overall, I had a good experience."
        average >= 2.5 -> "My experience had both good points and areas to improve."
        else -> "My experience fell short of expectations."
    }

    private fun positiveDimensionSentence(dimension: FeedbackDimension): String = when (dimension) {
        FeedbackDimension.FOOD -> "The food was the highlight of my visit."
        FeedbackDimension.SERVICE -> "The service stood out in a positive way."
        FeedbackDimension.WAIT_TIME -> "The wait time worked especially well."
        FeedbackDimension.ORDER_ACCURACY -> "The meal matched what I requested."
        FeedbackDimension.DIETARY_CONFIDENCE ->
            "The dietary and allergy information was especially clear."
    }

    private fun improvementDimensionSentence(dimension: FeedbackDimension): String = when (dimension) {
        FeedbackDimension.FOOD -> "The food could use more consistency."
        FeedbackDimension.SERVICE -> "The service could be more attentive."
        FeedbackDimension.WAIT_TIME -> "The wait time could be improved."
        FeedbackDimension.ORDER_ACCURACY -> "The meal did not fully match what I requested."
        FeedbackDimension.DIETARY_CONFIDENCE ->
            "The dietary and allergy information could be clearer."
    }

    private fun List<FeedbackTag>.joinLabels(): String {
        val labels = map { it.label.lowercase() }
        return when (labels.size) {
            0 -> ""
            1 -> labels.single()
            2 -> labels.joinToString(" and ")
            else -> labels.dropLast(1).joinToString(", ") + ", and " + labels.last()
        }
    }
}
