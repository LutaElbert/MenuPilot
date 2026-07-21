package com.menupilot.restaurant.feedback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackCommentGeneratorTest {
    private val generator = FeedbackCommentGenerator()

    @Test
    fun `generates positive review from selected answers`() {
        val feedback = GuestFeedback(
            foodRating = 5,
            serviceRating = 5,
            waitTimeRating = 4,
            orderAccuracyRating = 5,
            dietaryConfidenceRating = 4,
            selectedTagIds = setOf("flavorful_food", "attentive_service"),
        )

        val review = generator.generate(feedback)

        assertTrue(review.contains("excellent experience"))
        assertTrue(review.contains("flavorful food"))
        assertTrue(review.contains("attentive service"))
        assertFalse(review.contains("Google"))
        assertFalse(review.contains("discount"))
    }

    @Test
    fun `generates balanced review and preserves improvement feedback`() {
        val feedback = GuestFeedback(
            foodRating = 4,
            serviceRating = 2,
            waitTimeRating = 2,
            orderAccuracyRating = 4,
            dietaryConfidenceRating = 3,
            selectedTagIds = setOf("fresh_ingredients", "long_wait"),
        )

        val review = generator.generate(feedback)

        assertTrue(review.contains("areas to improve"))
        assertTrue(review.contains("service could be more attentive"))
        assertTrue(review.contains("long wait"))
        assertFalse(review.contains("excellent experience"))
    }

    @Test
    fun `draft reflects low dietary confidence without claiming a meal was unsafe`() {
        val review = generator.generate(
            GuestFeedback(
                foodRating = 4,
                serviceRating = 4,
                waitTimeRating = 4,
                orderAccuracyRating = 4,
                dietaryConfidenceRating = 1,
            ),
        )

        assertTrue(review.contains("dietary and allergy information could be clearer"))
        assertFalse(review.contains("unsafe", ignoreCase = true))
        assertFalse(review.contains("allergy-safe", ignoreCase = true))
    }

    @Test
    fun `requires all survey questions before generation`() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(
                GuestFeedback(
                    foodRating = 5,
                    serviceRating = 5,
                    waitTimeRating = 5,
                    orderAccuracyRating = 5,
                ),
            )
        }
    }

    @Test
    fun `rating updates reject values outside the five point scale`() {
        assertThrows(IllegalArgumentException::class.java) {
            GuestFeedback().withRating(FeedbackDimension.FOOD, 6)
        }
    }

    @Test
    fun `withRating captures every structured survey dimension`() {
        val feedback = FeedbackSurveyDimensions.fold(GuestFeedback()) { current, dimension ->
            current.withRating(dimension, 4)
        }

        assertTrue(feedback.isComplete)
        assertTrue(feedback.ratings.values.all { it == 4 })
    }
}
