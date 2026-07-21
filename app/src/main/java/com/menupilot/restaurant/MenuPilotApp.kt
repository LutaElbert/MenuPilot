package com.menupilot.restaurant

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.menupilot.restaurant.app.MenuPilotViewModel
import com.menupilot.restaurant.feature.catalog.BrowseMenuScreen
import com.menupilot.restaurant.feature.catalog.CatalogScreen
import com.menupilot.restaurant.feature.catalog.DishDetailScreen
import com.menupilot.restaurant.external.ReviewHandoffResult
import com.menupilot.restaurant.external.ReviewDestinationKind
import com.menupilot.restaurant.external.ReviewHandoffPreparation
import com.menupilot.restaurant.external.ReviewHandoffRejection
import com.menupilot.restaurant.external.copyReviewToClipboard
import com.menupilot.restaurant.external.openGoogleReviewDestination
import com.menupilot.restaurant.external.prepareGoogleReviewHandoff
import com.menupilot.restaurant.feature.feedback.FeedbackScreen
import com.menupilot.restaurant.feature.intake.VoiceIntakeHost
import com.menupilot.restaurant.feature.order.PairingsScreen
import com.menupilot.restaurant.feature.order.ShortlistScreen
import com.menupilot.restaurant.feature.order.WaiterHandoffScreen
import com.menupilot.restaurant.feature.welcome.WelcomeScreen
import com.menupilot.restaurant.navigation.BrowseMenu
import com.menupilot.restaurant.navigation.Catalog
import com.menupilot.restaurant.navigation.DishDetail
import com.menupilot.restaurant.navigation.Feedback
import com.menupilot.restaurant.navigation.Intake
import com.menupilot.restaurant.navigation.Pairings
import com.menupilot.restaurant.navigation.Shortlist
import com.menupilot.restaurant.navigation.WaiterHandoff
import com.menupilot.restaurant.navigation.Welcome

@Composable
fun MenuPilotApp(
    viewModel: MenuPilotViewModel,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handoffContext = viewModel.waiterHandoffContext()
    val backStack = rememberNavBackStack(Welcome)
    var confirmFreshAssistant by rememberSaveable { mutableStateOf(false) }
    val finishSession: () -> Unit = {
        viewModel.resetSession()
        backStack.clear()
        backStack.add(Welcome)
    }
    val startFreshAssistant: () -> Unit = {
        viewModel.beginSession()
        backStack.clear()
        backStack.add(Welcome)
        backStack.add(Intake)
    }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Welcome> {
                WelcomeScreen(
                    onStart = { prompt ->
                        viewModel.beginSession(prompt)
                        backStack.add(Intake)
                    },
                    onBrowseMenu = dropUnlessResumed {
                        if (viewModel.beginBrowseSession()) {
                            backStack.add(BrowseMenu)
                        }
                    },
                    onCallStaff = viewModel::requestStaff,
                    locationLabel = handoffContext.locationLabel,
                )
            }
            entry<Intake> {
                VoiceIntakeHost(
                    state = state,
                    onBack = { backStack.removeLastOrNull() },
                    onQueryChange = viewModel::updateQuery,
                    onSubmitQuery = viewModel::submitQuery,
                    onRemoveChip = viewModel::removeIntentChip,
                    onConfirmIntent = dropUnlessResumed {
                        if (viewModel.confirmIntent()) {
                            backStack.add(Catalog)
                        }
                    },
                    onNewSession = dropUnlessResumed {
                        viewModel.beginSession()
                    },
                    onCallStaff = viewModel::requestStaff,
                    locationLabel = handoffContext.locationLabel,
                )
            }
            entry<Catalog> {
                CatalogScreen(
                    state = state,
                    salesWindowLabel = viewModel.salesWindowLabel,
                    salesSourceLabel = viewModel.salesSourceLabel,
                    salesUpdatedLabel = viewModel.salesUpdatedLabel,
                    onBack = { backStack.removeLastOrNull() },
                    onBrowseMenu = dropUnlessResumed { backStack.add(BrowseMenu) },
                    onDishClick = { itemId, variantId ->
                        backStack.add(DishDetail(itemId, variantId))
                    },
                    onReviewOrder = dropUnlessResumed {
                        backStack.add(
                            if (state.orderSuggestions.isNotEmpty()) Pairings else Shortlist,
                        )
                    },
                    onCallStaff = viewModel::requestStaff,
                    locationLabel = handoffContext.locationLabel,
                )
            }
            entry<BrowseMenu> {
                BrowseMenuScreen(
                    state = state,
                    dishes = state.curatedDishes,
                    salesWindowLabel = viewModel.salesWindowLabel,
                    salesSourceLabel = viewModel.salesSourceLabel,
                    salesUpdatedLabel = viewModel.salesUpdatedLabel,
                    onBack = { backStack.removeLastOrNull() },
                    onAskAssistant = dropUnlessResumed {
                        if (state.cartEntries.isNotEmpty()) {
                            confirmFreshAssistant = true
                        } else if (state.intent?.chips?.isNotEmpty() == true) {
                            backStack.add(Intake)
                        } else {
                            startFreshAssistant()
                        }
                    },
                    onDishClick = { itemId, variantId ->
                        backStack.add(DishDetail(itemId, variantId))
                    },
                    onReviewPicks = dropUnlessResumed {
                        backStack.add(
                            if (state.orderSuggestions.isNotEmpty()) Pairings else Shortlist,
                        )
                    },
                    onCallStaff = viewModel::requestStaff,
                    locationLabel = handoffContext.locationLabel,
                )
            }
            entry<DishDetail> { key ->
                val result = state.curatedDishes.firstOrNull {
                    it.dish.id.value == key.itemId &&
                        it.dish.variantId.value == key.variantId
                }
                LaunchedEffect(key.itemId, key.variantId, result) {
                    if (result != null) {
                        viewModel.prepareDishInsight(key.itemId, key.variantId)
                    }
                }
                DishDetailScreen(
                    result = result,
                    dishInsight = state.dishInsights[
                        com.menupilot.restaurant.app.MenuDishKey(
                            key.itemId,
                            key.variantId,
                        )
                    ]?.text,
                    isInCart = com.menupilot.restaurant.app.MenuDishKey(
                        key.itemId,
                        key.variantId,
                    ) in state.cartVariantKeys,
                    salesWindowLabel = viewModel.salesWindowLabel,
                    salesSourceLabel = viewModel.salesSourceLabel,
                    salesUpdatedLabel = viewModel.salesUpdatedLabel,
                    onBack = { backStack.removeLastOrNull() },
                    onAddToCart = viewModel::addDishToShortlist,
                    onCallStaff = viewModel::requestStaff,
                    hasConfirmedNeeds = state.intent?.chips?.isNotEmpty() == true,
                )
            }
            entry<Pairings> {
                PairingsScreen(
                    state = state,
                    onAddSuggestion = viewModel::addPairingToShortlist,
                    onDismissSuggestion = viewModel::dismissSuggestion,
                    onContinue = dropUnlessResumed { backStack.add(Shortlist) },
                    onSkip = dropUnlessResumed { backStack.add(Shortlist) },
                    handoffContext = handoffContext,
                )
            }
            entry<Shortlist> {
                ShortlistScreen(
                    state = state,
                    dishes = viewModel.shortlistedDishes(),
                    onBack = { backStack.removeLastOrNull() },
                    onRemoveDish = viewModel::removeDishFromShortlist,
                    onAddSuggestion = viewModel::addPairingToShortlist,
                    onDismissSuggestion = viewModel::dismissSuggestion,
                    onRequestStaff = viewModel::requestStaff,
                    onStaffAcknowledge = viewModel::verifyStaffPin,
                    onCreateHandoff = dropUnlessResumed {
                        if (viewModel.createWaiterHandoff()) {
                            backStack.add(WaiterHandoff)
                        }
                    },
                    handoffContext = handoffContext,
                )
            }
            entry<WaiterHandoff> {
                val handoff = state.waiterHandoff
                if (handoff == null) {
                    LaunchedEffect(Unit) {
                        backStack.removeLastOrNull()
                    }
                } else {
                    WaiterHandoffScreen(
                        handoffReference = handoff.reference,
                        onShowPicks = { backStack.removeLastOrNull() },
                        onShareFeedback = dropUnlessResumed {
                            viewModel.beginFeedback()
                            backStack.add(Feedback)
                        },
                        onSkipFeedback = dropUnlessResumed { finishSession() },
                        handoffContext = handoffContext,
                    )
                }
            }
            entry<Feedback> {
                FeedbackScreen(
                    state = state.feedback,
                    onRatingChange = viewModel::updateFeedbackRating,
                    onTagToggle = viewModel::toggleFeedbackTag,
                    onPrivateNoteChange = viewModel::updatePrivateFeedbackNote,
                    onManagerFollowUpChange = viewModel::updateManagerFollowUpRequested,
                    onContinue = viewModel::continueFeedback,
                    onBack = {
                        if (!viewModel.previousFeedbackStage()) {
                            backStack.removeLastOrNull()
                        }
                    },
                    onReviewChange = viewModel::updateGeneratedReview,
                    onPublicDraftApprovalChange = viewModel::updatePublicDraftApproval,
                    onCopyReview = { review ->
                        val copied = copyReviewToClipboard(context, review)
                        viewModel.markReviewCopied(copied)
                        Toast.makeText(
                            context,
                            if (copied) "Review copied" else "Nothing to copy",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onOpenGoogleMaps = {
                        val approvedDraft = viewModel.approvedPublicReviewDraft()
                        val preparation = prepareGoogleReviewHandoff(
                            configuredUrl = viewModel.googleMapsReviewUrl,
                            currentPublicDraft = state.feedback.generatedReview,
                            approvedPublicDraft = approvedDraft,
                        )
                        val message = when (preparation) {
                            is ReviewHandoffPreparation.Rejected -> {
                                viewModel.markReviewCopied(false)
                                when (preparation.reason) {
                                    ReviewHandoffRejection.DRAFT_NOT_APPROVED ->
                                        "Approve the exact current public draft first"
                                    ReviewHandoffRejection.INVALID_DESTINATION ->
                                        "Restaurant review link is not configured"
                                }
                            }
                            is ReviewHandoffPreparation.Ready -> {
                                val plan = preparation.plan
                                val copied = copyReviewToClipboard(
                                    context,
                                    plan.publicDraft,
                                )
                                viewModel.markReviewCopied(copied)
                                when (
                                    openGoogleReviewDestination(
                                        context = context,
                                        configuredUrl = plan.configuredUrl,
                                    )
                                ) {
                                    ReviewHandoffResult.Opened -> when (plan.destinationKind) {
                                        ReviewDestinationKind.DIRECT_REVIEW_REQUEST ->
                                            if (copied) {
                                                "Draft copied. Opening Google review; you choose whether to post"
                                            } else {
                                                "Opening Google review; draft was not copied"
                                            }
                                        ReviewDestinationKind.GOOGLE_MAPS_BUSINESS ->
                                            if (copied) {
                                                "Draft copied. Opening the restaurant in Google Maps"
                                            } else {
                                                "Opening the restaurant in Google Maps; draft was not copied"
                                            }
                                        ReviewDestinationKind.GOOGLE_MAPS_SHORT_LINK ->
                                            if (copied) {
                                                "Draft copied. Opening the configured Google Maps link"
                                            } else {
                                                "Opening Google Maps; draft was not copied"
                                            }
                                    }
                                    ReviewHandoffResult.InvalidDestination ->
                                        "Restaurant review link is not configured"
                                    ReviewHandoffResult.NoHandler ->
                                        "No app can open the restaurant review link"
                                }
                            }
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                    onSendPrivateFeedback = { submission ->
                        val recorded = viewModel.recordPrivateFeedback(submission)
                        Toast.makeText(
                            context,
                            if (recorded) {
                                "Private feedback validated in this demo; it was not sent"
                            } else {
                                "Private feedback could not be validated"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (recorded) finishSession()
                    },
                    onDone = finishSession,
                )
            }
        },
    )

    state.staffRequestMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissStaffMessage,
            title = { Text("Staff assistance") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissStaffMessage) { Text("Done") }
            },
        )
    }

    if (confirmFreshAssistant) {
        AlertDialog(
            onDismissRequest = { confirmFreshAssistant = false },
            title = { Text("Start personalized help?") },
            text = {
                Text(
                    if (state.intent?.chips?.isNotEmpty() == true) {
                        "Editing or re-confirming your needs clears My Picks so every dish " +
                            "can be checked again."
                    } else {
                        "Changing from open browsing to a personalized request clears My Picks " +
                            "so every dish can be checked against the new needs."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFreshAssistant = false
                        if (state.intent?.chips?.isNotEmpty() == true) {
                            backStack.add(Intake)
                        } else {
                            startFreshAssistant()
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFreshAssistant = false }) {
                    Text("Keep browsing")
                }
            },
        )
    }
}
