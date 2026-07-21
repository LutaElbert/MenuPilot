package com.menupilot.restaurant.feature.feedback

import com.menupilot.restaurant.feedback.FeedbackSurveyDimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackUiStateTest {

    @Test
    fun `private and Google destinations are identical for every rating level`() {
        val lowRatings = FeedbackSurveyDimensions.associateWith { 1 }
        val highRatings = FeedbackSurveyDimensions.associateWith { 5 }

        assertEquals(
            listOf(
                FeedbackDestination.PRIVATE_RESTAURANT,
                FeedbackDestination.GOOGLE_REVIEW,
            ),
            FeedbackUiState(ratings = lowRatings).availableDestinations,
        )
        assertEquals(
            FeedbackUiState(ratings = lowRatings).availableDestinations,
            FeedbackUiState(ratings = highRatings).availableDestinations,
        )
    }

    @Test
    fun `all five structured questions are required before creating a draft`() {
        val incompleteRatings =
            FeedbackSurveyDimensions.dropLast(1).associateWith { 4 }
        val completeRatings =
            FeedbackSurveyDimensions.associateWith { 4 }

        assertFalse(FeedbackUiState(ratings = incompleteRatings).allRatingsComplete)
        assertTrue(FeedbackUiState(ratings = completeRatings).allRatingsComplete)
    }

    @Test
    fun `private submission contains structured answers and the guest edited draft`() {
        val ratings = FeedbackSurveyDimensions.associateWith { 3 }
        val submission =
            FeedbackUiState(
                ratings = ratings,
                selectedTagIds = setOf("long_wait"),
                generatedReview = "I edited this draft myself.",
                privateNote = "Please ask the manager to contact me.",
                managerFollowUpRequested = true,
            ).toPrivateSubmission()

        assertEquals(ratings, submission.ratings)
        assertEquals(setOf("long_wait"), submission.selectedTagIds)
        assertEquals("I edited this draft myself.", submission.editedDraft)
        assertEquals("Please ask the manager to contact me.", submission.privateNote)
        assertTrue(submission.managerFollowUpRequested)
    }

    @Test
    fun `public approval is tied to the exact edited draft`() {
        val approved = FeedbackUiState(
            generatedReview = "The wording I approved.",
            approvedPublicDraft = "The wording I approved.",
        )
        assertTrue(approved.publicDraftApproved)

        assertFalse(
            approved.copy(generatedReview = "Edited after approval.").publicDraftApproved,
        )
        assertFalse(
            approved.copy(approvedPublicDraft = null).publicDraftApproved,
        )
    }
}
