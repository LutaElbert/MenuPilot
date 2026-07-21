package com.menupilot.restaurant

import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.RecommendationEngine
import com.menupilot.restaurant.app.MenuPilotViewModel
import com.menupilot.restaurant.data.FixtureMenuRepository
import com.menupilot.restaurant.feature.catalog.BrowseMenuScreen
import com.menupilot.restaurant.feature.catalog.CatalogScreen
import com.menupilot.restaurant.feature.catalog.DishDetailScreen
import com.menupilot.restaurant.feature.feedback.FeedbackScreen
import com.menupilot.restaurant.feature.intake.IntakeScreen
import com.menupilot.restaurant.feature.order.PairingsScreen
import com.menupilot.restaurant.feature.order.ShortlistScreen
import com.menupilot.restaurant.feature.order.WaiterHandoffScreen
import com.menupilot.restaurant.feature.welcome.WelcomeScreen
import com.menupilot.restaurant.feedback.FeedbackCommentGenerator
import com.menupilot.restaurant.feedback.FeedbackDimension
import com.menupilot.restaurant.staff.DemoStaffAuthorizer
import com.menupilot.restaurant.theme.MenuPilotTheme
import com.menupilot.restaurant.voice.VoiceInputState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "compact-400x400", widthDp = 400, heightDp = 400)
@Preview(name = "compact-400x500", widthDp = 400, heightDp = 500)
@Preview(name = "compact-400x1000", widthDp = 400, heightDp = 1000)
@Preview(name = "medium-610x400", widthDp = 610, heightDp = 400)
@Preview(name = "medium-610x500", widthDp = 610, heightDp = 500)
@Preview(name = "medium-610x1000", widthDp = 610, heightDp = 1000)
@Preview(name = "expanded-900x400", widthDp = 900, heightDp = 400)
@Preview(name = "expanded-900x500", widthDp = 900, heightDp = 500)
@Preview(name = "expanded-900x1000", widthDp = 900, heightDp = 1000)
private annotation class AdaptiveScreenshotPreviews

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(name = "expanded-stage", widthDp = 900, heightDp = 1000)
private annotation class ExpandedStagePreview

@PreviewTest
@AdaptiveScreenshotPreviews
@Composable
fun WelcomeAdaptiveScreenshot() {
    MenuPilotTheme {
        WelcomeScreen(
            onStart = {},
            onBrowseMenu = {},
            onCallStaff = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "expanded-large-font",
    widthDp = 900,
    heightDp = 1000,
    fontScale = 1.5f,
)
@Composable
fun WelcomeLargeFontScreenshot() {
    MenuPilotTheme {
        WelcomeScreen(
            onStart = {},
            onBrowseMenu = {},
            onCallStaff = {},
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun IntakeStageScreenshot() {
    val model = previewModel().apply {
        beginSession()
        updateQuery(FlagshipPrompt)
        submitQuery()
    }
    MenuPilotTheme {
        IntakeScreen(
            state = model.uiState.value,
            voiceState = VoiceInputState.Idle,
            onBack = {},
            onQueryChange = {},
            onSubmitQuery = {},
            onRemoveChip = {},
            onConfirmIntent = {},
            onNewSession = {},
            onCallStaff = {},
            onStartVoice = {},
            onStopVoice = {},
            onCancelVoice = {},
            onOpenVoiceSettings = {},
        )
    }
}

@PreviewTest
@AdaptiveScreenshotPreviews
@Composable
fun RecommendationsAdaptiveScreenshot() {
    val model = confirmedPreviewModel()
    MenuPilotTheme {
        CatalogScreen(
            state = model.uiState.value,
            salesWindowLabel = model.salesWindowLabel,
            salesSourceLabel = model.salesSourceLabel,
            salesUpdatedLabel = model.salesUpdatedLabel,
            onBack = {},
            onBrowseMenu = {},
            onDishClick = { _, _ -> },
            onReviewOrder = {},
            onCallStaff = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "compact-large-font",
    widthDp = 400,
    heightDp = 1000,
    fontScale = 1.5f,
)
@Composable
fun RecommendationsLargeFontScreenshot() {
    val model = confirmedPreviewModel()
    MenuPilotTheme {
        CatalogScreen(
            state = model.uiState.value,
            salesWindowLabel = model.salesWindowLabel,
            salesSourceLabel = model.salesSourceLabel,
            salesUpdatedLabel = model.salesUpdatedLabel,
            onBack = {},
            onBrowseMenu = {},
            onDishClick = { _, _ -> },
            onReviewOrder = {},
            onCallStaff = {},
        )
    }
}

@PreviewTest
@AdaptiveScreenshotPreviews
@Composable
fun BrowseMenuAdaptiveScreenshot() {
    val model = previewModel().apply { beginBrowseSession() }
    MenuPilotTheme {
        BrowseMenuScreen(
            state = model.uiState.value,
            dishes = model.uiState.value.curatedDishes,
            salesWindowLabel = model.salesWindowLabel,
            salesSourceLabel = model.salesSourceLabel,
            salesUpdatedLabel = model.salesUpdatedLabel,
            onBack = {},
            onAskAssistant = {},
            onDishClick = { _, _ -> },
            onReviewPicks = {},
            onCallStaff = {},
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun PersonalizedBrowseStageScreenshot() {
    val model = confirmedPreviewModel()
    MenuPilotTheme {
        BrowseMenuScreen(
            state = model.uiState.value,
            dishes = model.uiState.value.curatedDishes,
            salesWindowLabel = model.salesWindowLabel,
            salesSourceLabel = model.salesSourceLabel,
            salesUpdatedLabel = model.salesUpdatedLabel,
            onBack = {},
            onAskAssistant = {},
            onDishClick = { _, _ -> },
            onReviewPicks = {},
            onCallStaff = {},
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun DishDetailStageScreenshot() {
    val model = confirmedPreviewModel()
    val result = model.uiState.value.curatedDishes.first()
    MenuPilotTheme {
        DishDetailScreen(
            result = result,
            isInCart = false,
            salesWindowLabel = model.salesWindowLabel,
            salesSourceLabel = model.salesSourceLabel,
            salesUpdatedLabel = model.salesUpdatedLabel,
            onBack = {},
            onAddToCart = { _, _ -> },
            onCallStaff = {},
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun PairingsStageScreenshot() {
    val model = shortlistedPreviewModel()
    MenuPilotTheme {
        PairingsScreen(
            state = model.uiState.value,
            onAddSuggestion = { _, _ -> },
            onDismissSuggestion = { _, _ -> },
            onContinue = {},
            onSkip = {},
            handoffContext = model.waiterHandoffContext(),
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun HandoffReviewStageScreenshot() {
    val model = shortlistedPreviewModel()
    MenuPilotTheme {
        ShortlistScreen(
            state = model.uiState.value,
            dishes = model.shortlistedDishes(),
            onBack = {},
            onRemoveDish = { _, _ -> },
            onAddSuggestion = { _, _ -> },
            onDismissSuggestion = { _, _ -> },
            onRequestStaff = {},
            onStaffAcknowledge = { false },
            onCreateHandoff = {},
            handoffContext = model.waiterHandoffContext(),
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun WaitingStageScreenshot() {
    val model = shortlistedPreviewModel().apply {
        createWaiterHandoff()
    }
    MenuPilotTheme {
        WaiterHandoffScreen(
            handoffReference = model.uiState.value.handoffReference ?: "MP-H-PREVIEW",
            onShowPicks = {},
            onShareFeedback = {},
            onSkipFeedback = {},
            handoffContext = model.waiterHandoffContext(),
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun FeedbackStageScreenshot() {
    val model = previewModel().apply { beginFeedback() }
    MenuPilotTheme {
        FeedbackScreen(
            state = model.uiState.value.feedback,
            onRatingChange = { _, _ -> },
            onTagToggle = {},
            onContinue = {},
            onBack = {},
            onReviewChange = {},
            onCopyReview = {},
            onOpenGoogleMaps = {},
            onDone = {},
        )
    }
}

@PreviewTest
@ExpandedStagePreview
@Composable
fun ReviewDraftStageScreenshot() {
    val model = reviewDraftPreviewModel()
    MenuPilotTheme {
        FeedbackScreen(
            state = model.uiState.value.feedback,
            onRatingChange = { _, _ -> },
            onTagToggle = {},
            onContinue = {},
            onBack = {},
            onReviewChange = {},
            onCopyReview = {},
            onOpenGoogleMaps = {},
            onDone = {},
        )
    }
}

private fun confirmedPreviewModel(): MenuPilotViewModel = previewModel().apply {
    beginSession()
    updateQuery(FlagshipPrompt)
    submitQuery()
    confirmIntent()
}

private fun shortlistedPreviewModel(): MenuPilotViewModel = confirmedPreviewModel().apply {
    addDishToShortlist("chili_lime_tofu", "standard")
}

private fun reviewDraftPreviewModel(): MenuPilotViewModel = previewModel().apply {
    beginFeedback()
    FeedbackDimension.entries.forEach { dimension ->
        updateFeedbackRating(dimension, 5)
    }
    continueFeedback()
    toggleFeedbackTag("fresh_ingredients")
    continueFeedback()
}

private fun previewModel(): MenuPilotViewModel {
    val clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC)
    return MenuPilotViewModel(
        menuRepository = FixtureMenuRepository(clock),
        intentAssistant = com.menupilot.restaurant.assistant.DeterministicIntentAssistant(),
        menuPolicyEngine = MenuPolicyEngine(),
        recommendationEngine = RecommendationEngine(),
        clock = clock,
        staffAuthorizer = DemoStaffAuthorizer(),
        feedbackCommentGenerator = FeedbackCommentGenerator(),
        savedStateHandle = SavedStateHandle(),
    )
}

private const val FlagshipPrompt =
    "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500."
