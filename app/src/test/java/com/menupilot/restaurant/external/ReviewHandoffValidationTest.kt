package com.menupilot.restaurant.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewHandoffValidationTest {

    @Test
    fun `accepts configured Google review and Maps destinations`() {
        assertTrue(
            "https://www.google.com/maps/search/?api=1&query=MenuPilot"
                .isAllowlistedReviewDestination(),
        )
        assertTrue("https://g.page/r/example/review".isAllowlistedReviewDestination())
        assertTrue(
            "https://search.google.com/local/writereview?placeid=ChIJexample"
                .isAllowlistedReviewDestination(),
        )
        assertTrue("https://maps.app.goo.gl/example".isAllowlistedReviewDestination())
    }

    @Test
    fun `classifies direct review links separately from business profile fallbacks`() {
        assertEquals(
            ReviewDestinationReadiness.DIRECT_REVIEW_READY,
            assessReviewDestination("https://g.page/r/example/review"),
        )
        assertEquals(
            ReviewDestinationReadiness.BUSINESS_PROFILE_FALLBACK,
            assessReviewDestination(
                "https://www.google.com/maps/search/?api=1&query=MenuPilot",
            ),
        )
        assertEquals(
            ReviewDestinationReadiness.UNVERIFIED_SHORT_LINK,
            assessReviewDestination("https://maps.app.goo.gl/example"),
        )
    }

    @Test
    fun `rejects non https deceptive and credentialed destinations`() {
        assertFalse("http://www.google.com/maps".isAllowlistedReviewDestination())
        assertFalse("https://google.com.example.test/review".isAllowlistedReviewDestination())
        assertFalse("https://user@www.google.com/maps".isAllowlistedReviewDestination())
        assertFalse("https://www.google.com:8443/maps".isAllowlistedReviewDestination())
        assertFalse("intent://maps.example.test".isAllowlistedReviewDestination())
        assertFalse(" https://g.page/r/example/review".isAllowlistedReviewDestination())
    }

    @Test
    fun `rejects generic Google pages and malformed review paths`() {
        assertFalse("https://www.google.com/".isAllowlistedReviewDestination())
        assertFalse("https://www.google.com/search?q=MenuPilot".isAllowlistedReviewDestination())
        assertFalse(
            "https://www.google.com/maps/search/?api=1&query="
                .isAllowlistedReviewDestination(),
        )
        assertFalse(
            "https://www.google.com/maps/search/?api=1&query=%20"
                .isAllowlistedReviewDestination(),
        )
        assertFalse(
            "https://www.google.com/maps/search/?api=1&query=MenuPilot&rating=5"
                .isAllowlistedReviewDestination(),
        )
        assertFalse("https://g.page/r/example/review/extra".isAllowlistedReviewDestination())
        assertFalse("https://g.page/r/example/review?draft=great".isAllowlistedReviewDestination())
        assertFalse(
            "https://search.google.com/local/writereview"
                .isAllowlistedReviewDestination(),
        )
        assertFalse(
            "https://search.google.com/local/writereview?placeid="
                .isAllowlistedReviewDestination(),
        )
        assertFalse(
            "https://search.google.com/local/writereview?placeid=example#five-stars"
                .isAllowlistedReviewDestination(),
        )
    }

    @Test
    fun `preparation requires approval of the exact current draft`() {
        val url = "https://g.page/r/example/review"
        val ready = prepareGoogleReviewHandoff(
            configuredUrl = url,
            currentPublicDraft = "My honest edited experience.",
            approvedPublicDraft = "My honest edited experience.",
        )
        assertTrue(ready is ReviewHandoffPreparation.Ready)
        val plan = (ready as ReviewHandoffPreparation.Ready).plan
        assertEquals("My honest edited experience.", plan.publicDraft)
        assertEquals(ReviewDestinationKind.DIRECT_REVIEW_REQUEST, plan.destinationKind)

        val editedAfterApproval = prepareGoogleReviewHandoff(
            configuredUrl = url,
            currentPublicDraft = "I changed this after approval.",
            approvedPublicDraft = "My honest edited experience.",
        )
        assertEquals(
            ReviewHandoffRejection.DRAFT_NOT_APPROVED,
            (editedAfterApproval as ReviewHandoffPreparation.Rejected).reason,
        )
    }

    @Test
    fun `invalid destination is rejected before a handoff plan exists`() {
        val result = prepareGoogleReviewHandoff(
            configuredUrl = "https://example.com/review",
            currentPublicDraft = "My review.",
            approvedPublicDraft = "My review.",
        )

        assertEquals(
            ReviewHandoffRejection.INVALID_DESTINATION,
            (result as ReviewHandoffPreparation.Rejected).reason,
        )
    }
}
