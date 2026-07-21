package com.menupilot.restaurant.feature.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.menupilot.restaurant.R
import com.menupilot.restaurant.design.IntentChip
import com.menupilot.restaurant.design.PrimaryAction
import com.menupilot.restaurant.feedback.FeedbackSurveyDimensions
import com.menupilot.restaurant.theme.MenuPilotDimensions
import com.menupilot.restaurant.theme.MenuPilotRadii
import com.menupilot.restaurant.theme.MenuPilotSpacing

private val TabletBreakpoint = 840.dp
private val FeedbackContentMaxWidth = 920.dp
private val ReviewContentMaxWidth = 1040.dp
private val RatingCardMaxWidth = 440.dp

@Composable
fun FeedbackScreen(
    state: FeedbackUiState,
    onRatingChange: (FeedbackDimension, Int) -> Unit,
    onTagToggle: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onReviewChange: (String) -> Unit,
    onCopyReview: (String) -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSendPrivateFeedback: ((PrivateFeedbackSubmission) -> Unit)? = null,
    onPrivateNoteChange: (String) -> Unit = {},
    onManagerFollowUpChange: (Boolean) -> Unit = {},
    onPublicDraftApprovalChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            FeedbackTopBar(
                stage = state.stage,
                restaurantName = state.restaurantName,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
        ) {
            val expanded = maxWidth >= TabletBreakpoint
            val horizontalPadding =
                if (expanded) MenuPilotSpacing.xxxl else MenuPilotSpacing.lg
            val contentModifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = if (expanded) MenuPilotSpacing.xxl else MenuPilotSpacing.xl,
                    )

            when (state.stage) {
                FeedbackStage.RATINGS ->
                    RatingsStage(
                        state = state,
                        expanded = expanded,
                        onRatingChange = onRatingChange,
                        onContinue = onContinue,
                        modifier = contentModifier,
                    )
                FeedbackStage.TAGS ->
                    TagsStage(
                        state = state,
                        onTagToggle = onTagToggle,
                        onContinue = onContinue,
                        modifier = contentModifier,
                    )
                FeedbackStage.REVIEW ->
                    ReviewStage(
                        state = state,
                        expanded = expanded,
                        onReviewChange = onReviewChange,
                        onPrivateNoteChange = onPrivateNoteChange,
                        onManagerFollowUpChange = onManagerFollowUpChange,
                        onPublicDraftApprovalChange = onPublicDraftApprovalChange,
                        onCopyReview = onCopyReview,
                        onOpenGoogleMaps = onOpenGoogleMaps,
                        onDone = onDone,
                        onSendPrivateFeedback = onSendPrivateFeedback,
                        modifier = contentModifier,
                    )
            }
        }
    }
}

@Composable
private fun FeedbackTopBar(
    stage: FeedbackStage,
    restaurantName: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = MenuPilotSpacing.xl, vertical = MenuPilotSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
    ) {
        if (stage != FeedbackStage.RATINGS) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics {
                    contentDescription = "Back to previous feedback step"
                },
            ) {
                Text("←", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MenuPilot",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = restaurantName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stage == FeedbackStage.RATINGS) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Close feedback" },
            ) {
                Text("×", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            Text(
                text = "${stage.ordinal + 1} of ${FeedbackStage.entries.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RatingsStage(
    state: FeedbackUiState,
    expanded: Boolean,
    onRatingChange: (FeedbackDimension, Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
        ) {
            Text(
                text = "How was your experience today?",
                style =
                    if (expanded) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    "Your honest answers help the restaurant improve its food, service, " +
                        "timing, accuracy, and dietary information.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        RatingCardGrid(
            state = state,
            expanded = expanded,
            onRatingChange = onRatingChange,
            modifier = Modifier.fillMaxWidth().widthIn(max = FeedbackContentMaxWidth),
        )

        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
            shape = RoundedCornerShape(MenuPilotRadii.medium),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text =
                    "Your score never changes your options: private feedback and Google " +
                        "review remain equally available.",
                modifier = Modifier.padding(MenuPilotSpacing.lg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        PrimaryAction(
            label = "Continue to highlights",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            enabled = state.allRatingsComplete,
            loading = state.isBusy,
        )
        if (!state.allRatingsComplete) {
            Text(
                text = "Answer all five questions to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RatingCardGrid(
    state: FeedbackUiState,
    expanded: Boolean,
    onRatingChange: (FeedbackDimension, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
        ) {
            FeedbackSurveyDimensions.chunked(2).forEach { rowDimensions ->
                if (rowDimensions.size == 1) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        RatingCard(
                            dimension = rowDimensions.single(),
                            selectedRating = state.ratingFor(rowDimensions.single()),
                            onRatingChange = {
                                onRatingChange(rowDimensions.single(), it)
                            },
                            modifier = Modifier.fillMaxWidth().widthIn(max = RatingCardMaxWidth),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
                    ) {
                        rowDimensions.forEach { dimension ->
                            RatingCard(
                                dimension = dimension,
                                selectedRating = state.ratingFor(dimension),
                                onRatingChange = { onRatingChange(dimension, it) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
        ) {
            FeedbackSurveyDimensions.forEach { dimension ->
                RatingCard(
                    dimension = dimension,
                    selectedRating = state.ratingFor(dimension),
                    onRatingChange = { onRatingChange(dimension, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RatingCard(
    dimension: FeedbackDimension,
    selectedRating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(MenuPilotSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = dimension.visualIcon(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (dimension == FeedbackDimension.DIETARY_CONFIDENCE) {
                    Surface(
                        shape = RoundedCornerShape(MenuPilotRadii.pill),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = "Important",
                            modifier =
                                Modifier.padding(
                                    horizontal = MenuPilotSpacing.md,
                                    vertical = MenuPilotSpacing.xs,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Text(
                text = dimension.label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = dimension.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                (1..5).forEach { rating ->
                    RatingChoice(
                        rating = rating,
                        selected = rating == selectedRating,
                        onClick = { onRatingChange(rating) },
                        modifier = Modifier.size(MenuPilotDimensions.minimumTouchTarget),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Needs work",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Excellent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RatingChoice(
    rating: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = MenuPilotDimensions.minimumTouchTarget)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .semantics {
                    contentDescription = "$rating out of 5, ${ratingDescription(rating)}"
                },
        shape = CircleShape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        border =
            BorderStroke(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rating.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun TagsStage(
    state: FeedbackUiState,
    onTagToggle: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
        ) {
            Text(
                text = "What stood out to you?",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    "Choose any details you want reflected in the editable draft. " +
                        "This step is optional.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            shape = RoundedCornerShape(MenuPilotRadii.large),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(MenuPilotSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
            ) {
                Text(
                    text = "Highlights and improvements",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
                ) {
                    state.availableTags.forEach { tag ->
                        IntentChip(
                            label = tag.label,
                            selected = tag.id in state.selectedTagIds,
                            onClick = { onTagToggle(tag.id) },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text =
                        if (state.selectedTagIds.isEmpty()) {
                            "No details selected. Your five ratings are enough to create a draft."
                        } else {
                            "${state.selectedTagIds.size} detail" +
                                if (state.selectedTagIds.size == 1) {
                                    " selected"
                                } else {
                                    "s selected"
                                }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PrimaryAction(
            label =
                if (state.selectedTagIds.isEmpty()) {
                    "Skip details and create draft"
                } else {
                    "Create my feedback draft"
                },
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            loading = state.isBusy,
        )
    }
}

@Composable
private fun ReviewStage(
    state: FeedbackUiState,
    expanded: Boolean,
    onReviewChange: (String) -> Unit,
    onPrivateNoteChange: (String) -> Unit,
    onManagerFollowUpChange: (Boolean) -> Unit,
    onPublicDraftApprovalChange: (Boolean) -> Unit,
    onCopyReview: (String) -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onDone: () -> Unit,
    onSendPrivateFeedback: ((PrivateFeedbackSubmission) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = ReviewContentMaxWidth),
                horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
                verticalAlignment = Alignment.Top,
            ) {
                ReviewContextPane(
                    state = state,
                    modifier = Modifier.width(280.dp),
                )
                ReviewDraftForm(
                    state = state,
                    expanded = true,
                    onReviewChange = onReviewChange,
                    onPrivateNoteChange = onPrivateNoteChange,
                    onManagerFollowUpChange = onManagerFollowUpChange,
                    onPublicDraftApprovalChange = onPublicDraftApprovalChange,
                    onCopyReview = onCopyReview,
                    onOpenGoogleMaps = onOpenGoogleMaps,
                    onDone = onDone,
                    onSendPrivateFeedback = onSendPrivateFeedback,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
            ) {
                ReviewContextPane(
                    state = state,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ReviewDraftForm(
                    state = state,
                    expanded = false,
                    onReviewChange = onReviewChange,
                    onPrivateNoteChange = onPrivateNoteChange,
                    onManagerFollowUpChange = onManagerFollowUpChange,
                    onPublicDraftApprovalChange = onPublicDraftApprovalChange,
                    onCopyReview = onCopyReview,
                    onOpenGoogleMaps = onOpenGoogleMaps,
                    onDone = onDone,
                    onSendPrivateFeedback = onSendPrivateFeedback,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReviewContextPane(
    state: FeedbackUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
    ) {
        Image(
            painter = painterResource(R.drawable.concierge_review_dining),
            contentDescription = "A restaurant table after a plated dining experience",
            modifier =
                Modifier.fillMaxWidth()
                    .height(if (compact) 148.dp else 210.dp)
                    .clip(RoundedCornerShape(MenuPilotRadii.large)),
            contentScale = ContentScale.Crop,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MenuPilotRadii.large),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        if (compact) MenuPilotSpacing.lg else MenuPilotSpacing.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
            ) {
                Text(
                    text = "YOUR DINING EXPERIENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = state.restaurantName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Review your words before choosing where they go.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MenuPilotRadii.medium),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(MenuPilotSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
            ) {
                Text(
                    text = "Your five answers",
                    style = MaterialTheme.typography.titleSmall,
                )
                RatingSummary(state = state)
                Text(
                    text = "“We value honest feedback, whether positive, mixed, or critical.”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

@Composable
private fun ReviewDraftForm(
    state: FeedbackUiState,
    expanded: Boolean,
    onReviewChange: (String) -> Unit,
    onPrivateNoteChange: (String) -> Unit,
    onManagerFollowUpChange: (Boolean) -> Unit,
    onPublicDraftApprovalChange: (Boolean) -> Unit,
    onCopyReview: (String) -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onDone: () -> Unit,
    onSendPrivateFeedback: ((PrivateFeedbackSubmission) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xs)) {
            Text(
                text = "Review draft",
                style =
                    if (expanded) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
            )
            Text(
                text =
                    "This draft uses only your selected answers. Edit and approve every word " +
                        "before sharing.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PublicReviewCard(
            state = state,
            onReviewChange = onReviewChange,
            onCopyReview = onCopyReview,
            onPublicDraftApprovalChange = onPublicDraftApprovalChange,
        )
        PrivateNoteCard(
            state = state,
            onPrivateNoteChange = onPrivateNoteChange,
            onManagerFollowUpChange = onManagerFollowUpChange,
        )
        AutoPostNotice()
        DestinationActions(
            state = state,
            expanded = expanded,
            onOpenGoogleMaps = onOpenGoogleMaps,
            onDone = onDone,
            onSendPrivateFeedback = onSendPrivateFeedback,
        )
    }
}

@Composable
private fun PublicReviewCard(
    state: FeedbackUiState,
    onReviewChange: (String) -> Unit,
    onCopyReview: (String) -> Unit,
    onPublicDraftApprovalChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(MenuPilotSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
            ) {
                Text("◎", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Google review draft",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Surface(
                    shape = RoundedCornerShape(MenuPilotRadii.pill),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "Editable",
                        modifier =
                            Modifier.padding(
                                horizontal = MenuPilotSpacing.md,
                                vertical = MenuPilotSpacing.xs,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            OutlinedTextField(
                value = state.generatedReview,
                onValueChange = onReviewChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Public draft") },
                supportingText = {
                    Text(
                        "${state.generatedReview.length} characters • Based only on your answers.",
                    )
                },
                minLines = 5,
                maxLines = 12,
                shape = RoundedCornerShape(MenuPilotRadii.medium),
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = MenuPilotDimensions.minimumTouchTarget)
                    .toggleable(
                        value = state.publicDraftApproved,
                        enabled = state.generatedReview.isNotBlank() && !state.isBusy,
                        role = Role.Checkbox,
                        onValueChange = onPublicDraftApprovalChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.publicDraftApproved,
                    onCheckedChange = null,
                    enabled = state.generatedReview.isNotBlank() && !state.isBusy,
                )
                Text(
                    text = "Use this edited experience summary as my public draft.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nothing has been posted or rated.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onCopyReview(state.generatedReview) },
                    enabled = state.generatedReview.isNotBlank() && !state.isBusy,
                ) {
                    Text(if (state.reviewCopied) "Copied" else "Copy draft")
                }
            }
        }
    }
}

@Composable
private fun PrivateNoteCard(
    state: FeedbackUiState,
    onPrivateNoteChange: (String) -> Unit,
    onManagerFollowUpChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(MenuPilotSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
            ) {
                Text("▣", style = MaterialTheme.typography.titleLarge)
                Column {
                    Text(
                        text = "Private note to restaurant",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "This note is never copied to Google Maps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = state.privateNote,
                onValueChange = onPrivateNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Private note (optional)") },
                placeholder = {
                    Text("Anything for the kitchen or manager that should remain private?")
                },
                supportingText = {
                    Text("${state.privateNote.length} characters")
                },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(MenuPilotRadii.medium),
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .defaultMinSize(minHeight = MenuPilotDimensions.minimumTouchTarget)
                        .toggleable(
                            value = state.managerFollowUpRequested,
                            role = Role.Checkbox,
                            onValueChange = onManagerFollowUpChange,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.managerFollowUpRequested,
                    onCheckedChange = null,
                )
                Text(
                    text =
                        "Include a manager follow-up request in this private draft " +
                            "(not sent in this demo).",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text =
                    "This prototype validates the private feedback locally. Restaurant delivery " +
                        "requires a connected venue feedback service.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AutoPostNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MenuPilotRadii.medium),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(MenuPilotSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Text("◇", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.xs)) {
                Text(
                    text = "Not an auto-post",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text =
                        "Choosing Google Maps copies only the public draft and opens a separate " +
                            "screen. MenuPilot never pre-fills a rating or publishes for you.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DestinationActions(
    state: FeedbackUiState,
    expanded: Boolean,
    onOpenGoogleMaps: () -> Unit,
    onDone: () -> Unit,
    onSendPrivateFeedback: ((PrivateFeedbackSubmission) -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
    ) {
        Text(
            text = "Choose where your feedback goes",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                "Both choices stay available for every score. There are no rewards or " +
                    "requested ratings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val privateAction: @Composable (Modifier) -> Unit = { actionModifier ->
            DestinationButton(
                label = "Validate private feedback (demo)",
                onClick = {
                    onSendPrivateFeedback?.invoke(state.toPrivateSubmission()) ?: onDone()
                },
                enabled = !state.isBusy,
                modifier = actionModifier,
            )
        }
        val googleAction: @Composable (Modifier) -> Unit = { actionModifier ->
            DestinationButton(
                label =
                    if (!state.publicDraftApproved) {
                        "Approve public draft to open Google Maps"
                    } else if (state.generatedReview.isBlank()) {
                        "Open Google Maps"
                    } else {
                        "Copy draft and open Google Maps"
                    },
                onClick = onOpenGoogleMaps,
                enabled = !state.isBusy && state.publicDraftApproved,
                modifier = actionModifier,
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
            ) {
                privateAction(Modifier.weight(1f))
                googleAction(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.md),
            ) {
                privateAction(Modifier.fillMaxWidth())
                googleAction(Modifier.fillMaxWidth())
            }
        }

        TextButton(
            onClick = onDone,
            enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Finish without sharing")
        }
    }
}

@Composable
private fun DestinationButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MenuPilotDimensions.actionHeight),
        enabled = enabled,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
            ),
        shape = RoundedCornerShape(MenuPilotRadii.medium),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RatingSummary(state: FeedbackUiState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MenuPilotSpacing.sm),
    ) {
        FeedbackSurveyDimensions.forEach { dimension ->
            Surface(
                shape = RoundedCornerShape(MenuPilotRadii.pill),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = "${dimension.label} ${state.ratingFor(dimension)}/5",
                    modifier =
                        Modifier.padding(
                            horizontal = MenuPilotSpacing.md,
                            vertical = MenuPilotSpacing.sm,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun FeedbackDimension.visualIcon(): String =
    when (this) {
        FeedbackDimension.FOOD -> "♨"
        FeedbackDimension.SERVICE -> "◡"
        FeedbackDimension.WAIT_TIME -> "◷"
        FeedbackDimension.ORDER_ACCURACY -> "✓"
        FeedbackDimension.DIETARY_CONFIDENCE -> "♢"
    }

private fun ratingDescription(rating: Int): String =
    when (rating) {
        1 -> "Needs work"
        2 -> "Fair"
        3 -> "Good"
        4 -> "Great"
        5 -> "Excellent"
        else -> "Not rated"
    }
