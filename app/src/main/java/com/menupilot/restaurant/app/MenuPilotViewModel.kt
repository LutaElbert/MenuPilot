package com.menupilot.restaurant.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.assistant.contract.FunctionGemmaRouteAction
import com.menupilot.assistant.contract.FunctionGemmaSalesPeriod
import com.menupilot.domain.AvoidanceReason
import com.menupilot.domain.Eligibility
import com.menupilot.domain.ItemEvaluation
import com.menupilot.domain.ApprovedPairingReason
import com.menupilot.domain.MenuItemId
import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.MenuRecommendation
import com.menupilot.domain.MenuResolution
import com.menupilot.domain.MenuVariantRef
import com.menupilot.domain.RecommendationEngine
import com.menupilot.domain.RecommendationPlacement
import com.menupilot.domain.RecommendationReason
import com.menupilot.domain.RecommendationRequest
import com.menupilot.domain.RecommendationResolution
import com.menupilot.domain.SalesEvidencePolicy
import com.menupilot.domain.SalesEvidenceResolution
import com.menupilot.domain.VariantId
import com.menupilot.restaurant.assistant.AssistantInterpretation
import com.menupilot.restaurant.assistant.AssistantFallbackReason
import com.menupilot.restaurant.assistant.AssistantSource
import com.menupilot.restaurant.assistant.ClarificationRequirement
import com.menupilot.restaurant.assistant.DishInsightSource
import com.menupilot.restaurant.assistant.DiningBudgetScope
import com.menupilot.restaurant.assistant.DiningIntentSummary
import com.menupilot.restaurant.assistant.GroundedDishInsight
import com.menupilot.restaurant.assistant.GroundedQwenDishInsightService
import com.menupilot.restaurant.assistant.IntentAssistant
import com.menupilot.restaurant.assistant.ModelGenerationException
import com.menupilot.restaurant.assistant.QwenDishInsightSelectionGenerator
import com.menupilot.restaurant.assistant.draft
import com.menupilot.restaurant.assistant.formatPeso
import com.menupilot.restaurant.assistant.rebuiltDraft
import com.menupilot.restaurant.assistant.toGroundedDishInsightFacts
import com.menupilot.restaurant.data.MenuDish
import com.menupilot.restaurant.data.MenuRepository
import com.menupilot.restaurant.data.CatalogIntegrityPolicy
import com.menupilot.restaurant.data.SalesEvidencePresentation
import com.menupilot.restaurant.data.ServicePlacement
import com.menupilot.restaurant.feature.feedback.FeedbackStage
import com.menupilot.restaurant.feature.feedback.FeedbackUiState
import com.menupilot.restaurant.feature.feedback.PrivateFeedbackSubmission
import com.menupilot.restaurant.feature.handoff.HandoffPlacement
import com.menupilot.restaurant.feature.handoff.HandoffDeliveryMode
import com.menupilot.restaurant.feature.handoff.HandoffBudgetScope
import com.menupilot.restaurant.feature.handoff.HandoffDiningNeed
import com.menupilot.restaurant.feature.handoff.HandoffDiningNeedKind
import com.menupilot.restaurant.feature.handoff.HandoffPick
import com.menupilot.restaurant.feature.handoff.HandoffPickOrigin
import com.menupilot.restaurant.feature.handoff.HandoffQuestion
import com.menupilot.restaurant.feature.handoff.HandoffQuestionCode
import com.menupilot.restaurant.feature.handoff.HandoffStaffReview
import com.menupilot.restaurant.feature.handoff.WaiterHandoffContext
import com.menupilot.restaurant.feature.handoff.WaiterHandoffPayload
import com.menupilot.restaurant.feedback.FeedbackCommentGenerator
import com.menupilot.restaurant.feedback.FeedbackDimension
import com.menupilot.restaurant.feedback.GuestFeedback
import com.menupilot.restaurant.staff.StaffAuthorizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class CuratedDish(
    val dish: MenuDish,
    val evaluation: ItemEvaluation,
    val matchReasons: List<String> = emptyList(),
    val salesEvidence: DishSalesEvidence? = null,
) {
    val canAddToOrder: Boolean
        get() = evaluation.eligibility == Eligibility.CANDIDATE ||
            evaluation.eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION

    val requiresStaffCheck: Boolean
        get() = evaluation.eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION ||
            evaluation.eligibility == Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA
}

data class DishSalesEvidence(
    val orderCount: Long,
    val windowStart: Instant,
    val windowEnd: Instant,
    val observedAt: Instant,
    val sourceId: String,
    val sourceLabel: String,
    val isBestseller: Boolean,
)

data class CartEntry(
    val itemId: String,
    val variantId: String,
    val recipeRevision: String,
    val eligibility: Eligibility,
) {
    val requiresStaffConfirmation: Boolean
        get() = eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION
}

data class MenuDishKey(
    val itemId: String,
    val variantId: String,
)

data class OrderSuggestion(
    val dish: MenuDish,
    val variant: MenuVariantRef,
    val eligibility: Eligibility,
    val reason: String,
    val evidence: String?,
) {
    val requiresStaffConfirmation: Boolean
        get() = eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION
}

data class StaffAcknowledgment(
    val staffId: String,
    val staffDisplayName: String,
    val acknowledgedAt: Instant,
    val menuRevision: String,
    val policyVersion: String,
    val intentFingerprint: String,
    val cartFingerprint: String,
)

data class MenuPilotUiState(
    val sessionId: String = "session-1",
    val query: String = "",
    val intent: DiningIntentSummary? = null,
    val conversationTurns: List<ConversationTurn> = emptyList(),
    val conversationNeedsClarification: Boolean = false,
    val pendingClarification: ClarificationRequirement? = null,
    val focusedDishName: String? = null,
    val assistantMessage: String = DEFAULT_ASSISTANT_MESSAGE,
    val isAssistantThinking: Boolean = false,
    val assistantSource: AssistantSource? = null,
    val assistantFallbackReason: AssistantFallbackReason? = null,
    val dishInsights: Map<MenuDishKey, GroundedDishInsight> = emptyMap(),
    val curatedDishes: List<CuratedDish> = emptyList(),
    val excludedDishCount: Int = 0,
    val cartEntries: List<CartEntry> = emptyList(),
    val orderSuggestions: List<OrderSuggestion> = emptyList(),
    val dismissedSuggestionRefs: Set<MenuVariantRef> = emptySet(),
    val acceptedPairingRefs: Set<MenuVariantRef> = emptySet(),
    val suggestionNotice: String? = null,
    val confirmedMenuRevision: String? = null,
    val confirmedPolicyVersion: String? = null,
    val confirmedIntentFingerprint: String? = null,
    val staffAcknowledgment: StaffAcknowledgment? = null,
    val staffRequestMessage: String? = null,
    val waiterHandoff: WaiterHandoffPayload? = null,
    val feedback: FeedbackUiState = FeedbackUiState(),
    val errorMessage: String? = null,
) {
    val handoffReference: String?
        get() = waiterHandoff?.reference

    val cartDishIds: List<String>
        get() = cartEntries.map(CartEntry::itemId)

    val cartVariantKeys: Set<MenuDishKey>
        get() = cartEntries.mapTo(mutableSetOf()) {
            MenuDishKey(it.itemId, it.variantId)
        }

    val hasConfirmedCatalog: Boolean
        get() = confirmedMenuRevision != null

    val requiresStaffAcknowledgment: Boolean
        get() = cartEntries.any(CartEntry::requiresStaffConfirmation)

    val cartFingerprint: String
        get() = cartEntries
            .sortedWith(compareBy(CartEntry::itemId, CartEntry::variantId))
            .joinToString("|") { "${it.itemId}:${it.variantId}:${it.recipeRevision}" }

    val staffAcknowledged: Boolean
        get() = staffAcknowledgment?.let { acknowledgment ->
            acknowledgment.menuRevision == confirmedMenuRevision &&
                acknowledgment.policyVersion == confirmedPolicyVersion &&
                acknowledgment.intentFingerprint == confirmedIntentFingerprint &&
                acknowledgment.cartFingerprint == cartFingerprint
        } == true

    val canCreateWaiterHandoff: Boolean
        get() = cartEntries.isNotEmpty() &&
            confirmedMenuRevision != null &&
            confirmedIntentFingerprint == intent?.fingerprint()

    val canConfirmIntent: Boolean
        get() = intent != null && !isAssistantThinking && !conversationNeedsClarification
}

@HiltViewModel
class MenuPilotViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val intentAssistant: IntentAssistant,
    private val menuPolicyEngine: MenuPolicyEngine,
    private val recommendationEngine: RecommendationEngine,
    private val clock: Clock,
    private val staffAuthorizer: StaffAuthorizer,
    private val feedbackCommentGenerator: FeedbackCommentGenerator,
    savedStateHandle: SavedStateHandle,
    private val dishInsightService: GroundedQwenDishInsightService =
        deterministicOnlyDishInsightService(),
) : ViewModel() {

    private val catalog = menuRepository.currentCatalog()
    private val catalogIntegrityIssues = CatalogIntegrityPolicy().validate(catalog)
    private val salesEvidencePolicy = SalesEvidencePolicy()
    private val conversationStore = ConversationSessionStore(savedStateHandle)
    private val state = MutableStateFlow(
        conversationStore.restore()?.toUiState()
            ?: newSessionState("session-1"),
    )
    private var assistantJob: Job? = null
    private var assistantRequestSequence = 0L
    private val requestedDishInsights = mutableSetOf<Pair<String, MenuDishKey>>()
    val uiState: StateFlow<MenuPilotUiState> = state.asStateFlow()

    init {
        persistConversationSession()
    }

    val allDishes: List<MenuDish>
        get() = catalog.dishes.takeIf { catalogIntegrityIssues.isEmpty() }.orEmpty()

    val salesWindowLabel: String
        get() = currentSalesPresentation().windowLabel

    val salesSourceLabel: String
        get() = currentSalesPresentation().sourceLabel

    val salesUpdatedLabel: String
        get() = currentSalesPresentation().updatedLabel

    val googleMapsReviewUrl: String
        get() = catalog.googleMapsReviewUrl

    fun conversationTranscript(): List<ConversationTurn> =
        state.value.conversationTurns.toList()

    /**
     * Shows app-rendered grounded copy immediately, then optionally swaps only the evidence angle
     * if Qwen returns a valid selection. The result never affects ranking, policy, or shortlist.
     */
    fun prepareDishInsight(itemId: String, variantId: String) {
        if (catalogIntegrityIssues.isNotEmpty()) return
        val key = MenuDishKey(itemId, variantId)
        val dish = catalog.dishes.firstOrNull {
            it.id.value == itemId && it.variantId.value == variantId
        } ?: return
        val sessionRequest = state.value.sessionId to key
        state.update { current ->
            if (current.sessionId != sessionRequest.first) current else {
                current.copy(focusedDishName = dish.name)
            }
        }
        if (!requestedDishInsights.add(sessionRequest)) return

        val facts = dish.toGroundedDishInsightFacts()
        val immediate = dishInsightService.immediateInsight(facts)
        state.update { current ->
            if (current.sessionId != sessionRequest.first) current else {
                current.copy(dishInsights = current.dishInsights + (key to immediate))
            }
        }
        viewModelScope.launch {
            val selected = dishInsightService.insight(facts)
            if (selected.source != DishInsightSource.QWEN_GROUNDED_SELECTION) return@launch
            state.update { current ->
                if (current.sessionId != sessionRequest.first) current else {
                    current.copy(dishInsights = current.dishInsights + (key to selected))
                }
            }
        }
    }

    fun boundedConversationContext(): BoundedConversationContext {
        val current = state.value
        val boundedTurns = current.conversationTurns.boundedForModel()
        return BoundedConversationContext(
            sessionId = current.sessionId,
            recentTurns = boundedTurns,
            authoritativeHardConstraints =
                current.intent.authoritativeHardConstraints(),
            confirmedPreferences = current.intent?.preferredAttributes
                .orEmpty()
                .distinct()
                .sorted(),
            omittedTurnCount = current.conversationTurns.size - boundedTurns.size,
            pendingClarification = current.pendingClarification,
            focusedDishName = current.focusedDishName,
        )
    }

    fun waiterHandoffContext(): WaiterHandoffContext = WaiterHandoffContext(
        locationLabel = catalog.tabletConfig.locationLabel,
        sessionLabel = state.value.sessionId,
        liveStaffChannelConnected = catalog.tabletConfig.liveStaffChannelConnected,
        placement = when (catalog.tabletConfig.placement) {
            ServicePlacement.TABLE -> HandoffPlacement.TABLE
            ServicePlacement.COUNTER -> HandoffPlacement.COUNTER
        },
    )

    fun beginSession(prefill: String? = null) {
        cancelAssistantRequest()
        forgetAssistantSession(state.value.sessionId)
        state.value = newSessionState(nextSessionId()).copy(
            query = prefill.orEmpty().take(MAX_QUERY_CHARS),
            assistantMessage = "Ask naturally—in English, Filipino, Taglish, or Cebuano.",
        )
        persistConversationSession()
    }

    fun beginBrowseSession(): Boolean {
        cancelAssistantRequest()
        forgetAssistantSession(state.value.sessionId)
        val sessionId = nextSessionId()
        val browseMessage =
            "Browse today’s full menu. No dietary filters or preferences are applied."
        val browseIntent = DiningIntentSummary(
            sourceQuery = "",
            detectedLanguage = "No language input",
            allergens = emptyList(),
            avoidanceReasons = emptyMap(),
            diets = emptyList(),
            preferredAttributes = emptyList(),
            maximumPriceMinor = null,
            intentDraft = draft(emptyList()),
        )
        state.value = newSessionState(sessionId).copy(
            intent = browseIntent,
            conversationTurns = listOf(
                ConversationTurn(ConversationRole.ASSISTANT, browseMessage),
            ),
            assistantMessage = browseMessage,
        )
        persistConversationSession()
        val confirmed = confirmIntent()
        if (confirmed) {
            state.update {
                it.copy(
                    assistantMessage = browseMessage,
                )
            }
        }
        return confirmed
    }

    fun updateQuery(query: String) {
        state.update {
            it.copy(
                query = query.take(MAX_QUERY_CHARS),
                errorMessage = null,
            )
        }
        persistConversationSession()
    }

    fun submitQuery() {
        val submittedQuery = state.value.query.trim().take(MAX_QUERY_CHARS)
        if (submittedQuery.isBlank() || state.value.isAssistantThinking) return
        if (
            state.value.conversationTurns.count { it.role == ConversationRole.GUEST } >=
            MAX_GUEST_TURNS
        ) {
            val limitMessage =
                "This dining session has reached its conversation limit. " +
                    "Confirm these needs or start a new session."
            state.update { current ->
                current.copy(
                    assistantMessage = limitMessage,
                    errorMessage = limitMessage,
                    conversationTurns = current.conversationTurns.appendAssistantTurnOnce(
                        limitMessage,
                    ),
                )
            }
            persistConversationSession()
            return
        }
        assistantJob?.cancel()
        val requestId = ++assistantRequestSequence
        val baselineIntent = state.value.intent
        val baselineNeedsClarification = state.value.conversationNeedsClarification
        val baselinePendingClarification = state.value.pendingClarification
        state.update {
            it.copy(
                query = "",
                isAssistantThinking = true,
                assistantMessage = "Understanding your request on this tablet…",
                assistantSource = null,
                assistantFallbackReason = null,
                conversationTurns = it.conversationTurns + ConversationTurn(
                    ConversationRole.GUEST,
                    submittedQuery,
                ),
                errorMessage = null,
            )
        }
        persistConversationSession()
        assistantJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val result = try {
                intentAssistant.interpretAsync(
                    submittedQuery,
                    boundedConversationContext(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                try {
                    intentAssistant.interpret(submittedQuery)
                } catch (_: Exception) {
                    AssistantInterpretation.NeedsClarification(
                        message =
                            "I couldn’t interpret that request on this tablet. " +
                                "Please rephrase it or ask the waiter.",
                    )
                }
            }
            if (requestId != assistantRequestSequence) return@launch
            applyAssistantInterpretation(
                result = result,
                submittedQuery = submittedQuery,
                baselineIntent = baselineIntent,
                baselineNeedsClarification = baselineNeedsClarification,
                baselinePendingClarification = baselinePendingClarification,
            )
        }
    }

    private fun applyAssistantInterpretation(
        result: AssistantInterpretation,
        submittedQuery: String,
        baselineIntent: DiningIntentSummary?,
        baselineNeedsClarification: Boolean,
        baselinePendingClarification: ClarificationRequirement?,
    ) {
        when (result) {
            is AssistantInterpretation.NeedsClarification -> {
                val pendingClarification = baselinePendingClarification
                    ?.preservingMoreProtectiveThan(result.requirement)
                    ?: result.requirement
                val response = if (baselineIntent == null) {
                    if (
                        baselineNeedsClarification &&
                        pendingClarification != result.requirement
                    ) {
                        "${result.message} I still need your answer to the earlier required " +
                            "clarification."
                    } else {
                        result.message
                    }
                } else {
                    "${result.message} Your earlier confirmed needs are still retained."
                }
                state.update {
                    it.copy(
                        intent = baselineIntent,
                        isAssistantThinking = false,
                        assistantSource = result.source,
                        assistantFallbackReason = result.fallbackReason,
                        assistantMessage = response,
                        conversationTurns = it.conversationTurns + ConversationTurn(
                            ConversationRole.ASSISTANT,
                            response,
                        ),
                        conversationNeedsClarification = true,
                        pendingClarification = pendingClarification,
                        curatedDishes = emptyList(),
                        excludedDishCount = 0,
                        cartEntries = emptyList(),
                        orderSuggestions = emptyList(),
                        dismissedSuggestionRefs = emptySet(),
                        acceptedPairingRefs = emptySet(),
                        suggestionNotice = null,
                        confirmedMenuRevision = null,
                        confirmedPolicyVersion = null,
                        confirmedIntentFingerprint = null,
                        staffAcknowledgment = null,
                        waiterHandoff = null,
                        errorMessage = response,
                    )
                }
            }
            is AssistantInterpretation.Ready -> {
                val merged = mergeConversationIntent(
                    previous = baselineIntent,
                    incoming = result.summary,
                    latestQuery = submittedQuery,
                )
                val resolvesEarlierClarification =
                    baselinePendingClarification?.isResolvedBy(result.summary) == true
                val keepsEarlierClarification =
                    baselineNeedsClarification && !resolvesEarlierClarification
                val understoodResponse = if (
                    baselineIntent != null &&
                    merged.authoritativeHardConstraintCount >
                    result.summary.authoritativeHardConstraintCount
                ) {
                    "${result.clarificationMessage} I kept your earlier safety and budget needs. " +
                        "Remove a chip only when you intend to change one."
                } else {
                    result.clarificationMessage
                }
                val response = if (keepsEarlierClarification) {
                    "$understoodResponse I still need your answer to the earlier clarification " +
                        "before I shape the menu."
                } else {
                    understoodResponse
                }
                state.update {
                    it.copy(
                        intent = merged,
                        isAssistantThinking = false,
                        assistantSource = result.source,
                        assistantFallbackReason = result.fallbackReason,
                        assistantMessage = response,
                        conversationTurns = it.conversationTurns + ConversationTurn(
                            ConversationRole.ASSISTANT,
                            response,
                        ),
                        conversationNeedsClarification = keepsEarlierClarification,
                        pendingClarification = if (keepsEarlierClarification) {
                            baselinePendingClarification
                                ?: ClarificationRequirement.GENERAL_REQUEST
                        } else {
                            null
                        },
                        curatedDishes = emptyList(),
                        excludedDishCount = 0,
                        cartEntries = emptyList(),
                        orderSuggestions = emptyList(),
                        dismissedSuggestionRefs = emptySet(),
                        acceptedPairingRefs = emptySet(),
                        suggestionNotice = null,
                        confirmedMenuRevision = null,
                        confirmedPolicyVersion = null,
                        confirmedIntentFingerprint = null,
                        staffAcknowledgment = null,
                        errorMessage = response.takeIf { keepsEarlierClarification },
                    )
                }
            }
            is AssistantInterpretation.Routed -> {
                val resolution = resolveConversationRoute(
                    route = result.route,
                    baselineIntent = baselineIntent,
                )
                val resolvesEarlierClarification =
                    baselinePendingClarification?.isResolvedBy(
                        route = result.route,
                        resolutionNeedsClarification = resolution.needsClarification,
                    ) == true
                val keepsEarlierClarification =
                    (
                        baselineNeedsClarification &&
                            !resolvesEarlierClarification
                        ) ||
                        resolution.needsClarification
                val pendingClarification = when {
                    baselineNeedsClarification && !resolvesEarlierClarification ->
                        baselinePendingClarification
                            ?: ClarificationRequirement.GENERAL_REQUEST
                    resolution.needsClarification ->
                        resolution.clarificationRequirement
                            ?: ClarificationRequirement.GENERAL_REQUEST
                    else -> null
                }
                val response = if (
                    baselineNeedsClarification &&
                    !resolvesEarlierClarification &&
                    !resolution.needsClarification
                ) {
                    "${resolution.message} I still need your answer to the earlier clarification " +
                        "before I shape the menu."
                } else {
                    resolution.message
                }
                state.update {
                    it.copy(
                        intent = resolution.intent,
                        isAssistantThinking = false,
                        assistantSource = result.source,
                        assistantFallbackReason = result.fallbackReason,
                        assistantMessage = response,
                        conversationTurns = it.conversationTurns + ConversationTurn(
                            ConversationRole.ASSISTANT,
                            response,
                        ),
                        conversationNeedsClarification = keepsEarlierClarification,
                        pendingClarification = pendingClarification,
                        errorMessage = response.takeIf { keepsEarlierClarification },
                    )
                }
            }
        }
        persistConversationSession()
    }

    private fun resolveConversationRoute(
        route: FunctionGemmaRoute,
        baselineIntent: DiningIntentSummary?,
    ): RoutedConversationResolution {
        if (
            catalogIntegrityIssues.isNotEmpty() &&
            route.action != FunctionGemmaRouteAction.REQUEST_WAITER &&
            route.action != FunctionGemmaRouteAction.CLARIFY
        ) {
            return RoutedConversationResolution(
                message =
                    "The displayed menu does not match the restaurant’s current recipe and price " +
                        "records, so I can’t answer from it. Please ask staff to review the menu.",
                intent = baselineIntent,
                needsClarification = true,
                clarificationRequirement =
                    ClarificationRequirement.CATALOG_STAFF_REVIEW,
            )
        }
        return when (route.action) {
        FunctionGemmaRouteAction.BROWSE_MENU -> {
            val retainedIntent = baselineIntent ?: neutralDiningIntent("Manual menu browse")
            val message = if (baselineIntent == null) {
                "You can browse the full listed menu with no filters. Nothing is being treated " +
                    "as an allergy-safe recommendation."
            } else {
                val retained = baselineIntent.chips.joinToString { it.label }
                "You can browse manually, and I kept your confirmed needs" +
                    retained.takeIf(String::isNotBlank)?.let { ": $it." }.orEmpty() +
                    " Start a New session or remove a chip only if you intend to clear one."
            }
            RoutedConversationResolution(message = message, intent = retainedIntent)
        }
        FunctionGemmaRouteAction.EXPLAIN_DISH ->
            resolveDishExplanation(route.subject, baselineIntent)
        FunctionGemmaRouteAction.COMPARE_DISHES ->
            resolveDishComparison(route.subject, baselineIntent)
        FunctionGemmaRouteAction.SHOW_BESTSELLERS ->
            resolveBestsellerAnswer(route.period, baselineIntent)
        FunctionGemmaRouteAction.SUGGEST_PAIRING ->
            resolvePairingAnswer(route.subject, baselineIntent)
        FunctionGemmaRouteAction.REQUEST_WAITER -> {
            val shortlistCount = state.value.cartEntries.size
            val message = if (shortlistCount == 0) {
                "Nothing was sent. Shortlist the dishes you want to discuss, then create the " +
                    "screen-only waiter handoff or call the waiter yourself."
            } else {
                "Nothing was sent. Your $shortlistCount-item shortlist is still here; use the " +
                    "explicit waiter handoff to show its exact needs and questions to staff."
            }
            RoutedConversationResolution(message = message, intent = baselineIntent)
        }
        FunctionGemmaRouteAction.SHAPE_MENU,
        FunctionGemmaRouteAction.CLARIFY,
        -> RoutedConversationResolution(
            message =
                "Tell me one more detail about what you want, or use Browse menu to look manually.",
            intent = baselineIntent,
            needsClarification = true,
            clarificationRequirement = ClarificationRequirement.GENERAL_REQUEST,
        )
    }
    }

    private fun resolveDishExplanation(
        subject: String?,
        intent: DiningIntentSummary?,
    ): RoutedConversationResolution {
        val dish = subject?.let(::findSingleDish)
        if (dish == null) {
            return RoutedConversationResolution(
                message =
                    "I couldn’t match that to one current dish. Use the exact dish name or open " +
                        "its card, then ask again.",
                intent = intent,
                needsClarification = true,
                clarificationRequirement = ClarificationRequirement.DISH_REFERENCE,
            )
        }
        val listedIngredients = dish.ingredients.take(6).joinToString()
        val policyStatus = dishPolicyStatus(dish, intent)
        val message = buildString {
            append(dish.name)
            append(": ")
            append(dish.description)
            append(" It is ")
            append(formatPeso(dish.priceMinor))
            append(" and the displayed prep estimate is about ")
            append(dish.prepMinutes)
            append(" minutes. Displayed ingredients: ")
            append(listedIngredients)
            append(". ")
            append(policyStatus)
            append(" Menu records are not a kitchen allergy guarantee.")
        }.take(MAX_TURN_CHARS)
        return RoutedConversationResolution(message = message, intent = intent)
    }

    private fun resolveDishComparison(
        subject: String?,
        intent: DiningIntentSummary?,
    ): RoutedConversationResolution {
        val dishes = subject?.let(::findComparedDishes).orEmpty()
        if (dishes.size < 2) {
            return RoutedConversationResolution(
                message =
                    "Name two current dishes to compare—for example, “compare Chicken Inasal " +
                        "Plate and Chili-Lime Tofu Bowl.”",
                intent = intent,
                needsClarification = true,
                clarificationRequirement = ClarificationRequirement.DISH_REFERENCE,
            )
        }
        val comparison = dishes.take(2).joinToString("  •  ") { dish ->
            val flavors = dish.flavorTags.take(2).joinToString("/")
            "${dish.name}: ${formatPeso(dish.priceMinor)}, about ${dish.prepMinutes} min, " +
                "$flavors. ${dishPolicyStatus(dish, intent)}"
        }
        return RoutedConversationResolution(
            message = (
                "$comparison Prices, preparation estimates, and tags come from the current " +
                    "restaurant menu; neither comparison is an allergy approval."
                ).take(MAX_TURN_CHARS),
            intent = intent,
        )
    }

    private fun resolveBestsellerAnswer(
        period: FunctionGemmaSalesPeriod?,
        intent: DiningIntentSummary?,
    ): RoutedConversationResolution {
        val presentation = currentSalesPresentation()
        if (
            period == FunctionGemmaSalesPeriod.LAST_WEEK ||
            period == FunctionGemmaSalesPeriod.ALL_TIME
        ) {
            val requested = if (period == FunctionGemmaSalesPeriod.LAST_WEEK) {
                "last week"
            } else {
                "all time"
            }
            return RoutedConversationResolution(
                message =
                    "I don’t have a verified $requested sales window on this tablet. The only " +
                        "qualified record is ${presentation.windowLabel.lowercase()} from " +
                        "${presentation.sourceLabel}; I won’t invent a $requested bestseller.",
                intent = intent,
            )
        }

        val evidence = currentSalesEvidence()
        if (evidence !is SalesEvidenceResolution.Ready) {
            return RoutedConversationResolution(
                message =
                    "The current sales record is unavailable or not comparable, so I can’t make " +
                        "a bestseller claim. You can still browse the menu.",
                intent = intent,
            )
        }
        val eligibleVariants = eligibleVariantsForConversation(intent)
        val ranked = catalog.dishes.mapNotNull { dish ->
            val variant = dish.variantRef()
            if (variant !in eligibleVariants) return@mapNotNull null
            evidence.signalsByVariant[variant]?.let { signal -> dish to signal.orderCount }
        }
        val maximum = ranked.maxOfOrNull { it.second }
        if (maximum == null) {
            return RoutedConversationResolution(
                message =
                    "No bestseller in the current verified window fits the needs recorded in " +
                        "this session. Ask the waiter to review alternatives.",
                intent = intent,
            )
        }
        val leaders = ranked
            .filter { it.second == maximum }
            .map { it.first.name }
            .sorted()
        val disclaimer = if (intent?.allergens.isNullOrEmpty()) {
            "Popularity is not a safety or personal-fit claim."
        } else {
            "I kept your recorded allergy filters, but the kitchen must still confirm."
        }
        return RoutedConversationResolution(
            message = (
                "For ${presentation.windowLabel.lowercase()}, " +
                    "${leaders.joinToString()} leads with $maximum orders in " +
                    "${presentation.sourceLabel}. $disclaimer"
                ).take(MAX_TURN_CHARS),
            intent = intent,
        )
    }

    private fun resolvePairingAnswer(
        subject: String?,
        intent: DiningIntentSummary?,
    ): RoutedConversationResolution {
        val anchor = subject?.let(::findSingleDish)
        if (anchor == null) {
            return RoutedConversationResolution(
                message =
                    "Which current dish should I pair with? Use its exact name. Suggestions stay " +
                        "optional and nothing will be added automatically.",
                intent = intent,
                needsClarification = true,
                clarificationRequirement = ClarificationRequirement.DISH_REFERENCE,
            )
        }
        val workingIntent = intent ?: neutralDiningIntent("Pairing request for ${anchor.name}")
        val anchorEvaluation = policyEvaluations(workingIntent)
            ?.firstOrNull {
                it.itemId == anchor.id && it.variantId == anchor.variantId
            }
        if (
            anchorEvaluation == null ||
            anchorEvaluation.eligibility !in setOf(
                Eligibility.CANDIDATE,
                Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION,
            )
        ) {
            return RoutedConversationResolution(
                message =
                    "I can’t offer an upsell for ${anchor.name} against the needs recorded in " +
                        "this session. Ask the waiter to review the dish first.",
                intent = intent,
            )
        }
        val recommendations = recommendationEngine.recommend(
            RecommendationRequest(
                menu = catalog.snapshot,
                intent = workingIntent.toConfirmedIntent(state.value.sessionId),
                merchandising = catalog.merchandisingSnapshot,
                now = clock.instant(),
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = setOf(anchor.variantRef()),
                dismissed = state.value.dismissedSuggestionRefs,
            ),
        )
        val suggestions = (recommendations as? RecommendationResolution.Ready)
            ?.recommendations
            .orEmpty()
            .mapNotNull { recommendation ->
                catalog.dish(
                    recommendation.variant.itemId,
                    recommendation.variant.variantId,
                )?.let { dish -> recommendation.toOrderSuggestion(dish) }
            }
            .filter { suggestion ->
                val maximum = workingIntent.wholeOrderBudgetLimit()
                maximum == null || anchor.priceMinor + suggestion.dish.priceMinor <= maximum
            }
            .take(MAX_ACCEPTED_PAIRINGS)
        return if (suggestions.isEmpty()) {
            RoutedConversationResolution(
                message =
                    "There isn’t a current, policy-qualified pairing for ${anchor.name} under " +
                        "the needs and budget recorded here. Nothing was added.",
                intent = intent,
            )
        } else {
            RoutedConversationResolution(
                message = pairingMessage(suggestions),
                intent = intent,
            )
        }
    }

    private fun pairingMessage(suggestions: List<OrderSuggestion>): String {
        val options = suggestions.joinToString("  •  ") { suggestion ->
            "${suggestion.dish.name} (${formatPeso(suggestion.dish.priceMinor)}; " +
                "${suggestion.reason})"
        }
        return (
            "Optional pairings: $options. These passed the current local policy check, but none " +
                "was added—choose one yourself if it suits you."
            ).take(MAX_TURN_CHARS)
    }

    private fun neutralDiningIntent(sourceQuery: String): DiningIntentSummary =
        DiningIntentSummary(
            sourceQuery = sourceQuery,
            detectedLanguage = "Conversation",
            allergens = emptyList(),
            avoidanceReasons = emptyMap(),
            diets = emptyList(),
            preferredAttributes = emptyList(),
            maximumPriceMinor = null,
            intentDraft = draft(emptyList()),
        )

    private fun policyEvaluations(
        intent: DiningIntentSummary,
    ): List<ItemEvaluation>? = when (
        val resolution = menuPolicyEngine.evaluateMenu(
            snapshot = catalog.snapshot,
            intent = intent.toConfirmedIntent(state.value.sessionId),
            now = clock.instant(),
        )
    ) {
        is MenuResolution.CatalogReady -> resolution.evaluations
        is MenuResolution.NoMatches -> resolution.evaluations
        is MenuResolution.ClarificationRequired,
        is MenuResolution.Blocked,
        -> null
    }

    private fun eligibleVariantsForConversation(
        intent: DiningIntentSummary?,
    ): Set<MenuVariantRef> {
        val workingIntent = intent ?: neutralDiningIntent("Unfiltered conversation policy check")
        return policyEvaluations(workingIntent)
            .orEmpty()
            .filter {
                it.eligibility == Eligibility.CANDIDATE ||
                    it.eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION
            }
            .mapTo(mutableSetOf()) { evaluation ->
                MenuVariantRef(
                    itemId = evaluation.itemId,
                    variantId = evaluation.variantId,
                    recipeRevision = recipeRevision(
                        evaluation.itemId,
                        evaluation.variantId,
                    ).orEmpty(),
                )
            }
    }

    private fun dishPolicyStatus(
        dish: MenuDish,
        intent: DiningIntentSummary?,
    ): String {
        val workingIntent = intent ?: neutralDiningIntent("Dish availability policy check")
        val evaluation = policyEvaluations(workingIntent)
            ?.firstOrNull { it.itemId == dish.id && it.variantId == dish.variantId }
            ?: return "The current menu facts require waiter review."
        return when (evaluation.eligibility) {
            Eligibility.CANDIDATE -> if (intent == null) {
                "It is listed as available, but no dining needs are confirmed yet."
            } else {
                "It matches the currently recorded filters."
            }
            Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION ->
                "It still requires waiter and kitchen confirmation."
            Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA ->
                "There is not enough verified information to recommend it."
            Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT ->
                "It does not fit the currently confirmed needs."
            Eligibility.NOT_CANDIDATE_UNAVAILABLE ->
                "It is not currently available as a candidate."
        }
    }

    private fun findSingleDish(subject: String): MenuDish? {
        val normalizedSubject = subject.normalizedDishText()
        if (normalizedSubject.isBlank()) return null
        val matches = catalog.dishes.filter { dish ->
            val normalizedName = dish.name.normalizedDishText()
            normalizedName == normalizedSubject ||
                normalizedName.contains(normalizedSubject) ||
                normalizedSubject.contains(normalizedName)
        }
        return matches.singleOrNull()
    }

    private fun findComparedDishes(subject: String): List<MenuDish> {
        val normalizedSubject = subject.normalizedDishText()
        val directlyNamed = catalog.dishes.filter { dish ->
            normalizedSubject.contains(dish.name.normalizedDishText())
        }
        if (directlyNamed.size >= 2) return directlyNamed
        return subject
            .split(Regex("""(?i)\s+(?:and|vs\.?|versus)\s+|,"""))
            .mapNotNull(::findSingleDish)
            .distinctBy { it.id to it.variantId }
    }

    private data class RoutedConversationResolution(
        val message: String,
        val intent: DiningIntentSummary?,
        val needsClarification: Boolean = false,
        val clarificationRequirement: ClarificationRequirement? = null,
    )

    fun removeIntentChip(key: String) {
        cancelAssistantRequest()
        state.update { current ->
            current.intent?.let { intent ->
                val removedLabel = intent.chips.firstOrNull { it.key == key }?.label
                    ?: return@let current
                val updated = intent.without(key).let {
                    it.copy(intentDraft = it.rebuiltDraft())
                }
                val message = "Removed $removedLabel from the needs I’ll use."
                current.copy(
                    intent = updated,
                    assistantMessage = message,
                    conversationTurns = current.conversationTurns + ConversationTurn(
                        ConversationRole.ASSISTANT,
                        message,
                    ),
                    isAssistantThinking = false,
                    curatedDishes = emptyList(),
                    excludedDishCount = 0,
                    cartEntries = emptyList(),
                    orderSuggestions = emptyList(),
                    dismissedSuggestionRefs = emptySet(),
                    acceptedPairingRefs = emptySet(),
                    suggestionNotice = null,
                    confirmedMenuRevision = null,
                    confirmedPolicyVersion = null,
                    confirmedIntentFingerprint = null,
                    staffAcknowledgment = null,
                    waiterHandoff = null,
                )
            } ?: current
        }
        persistConversationSession()
    }

    fun confirmIntent(): Boolean {
        if (!state.value.canConfirmIntent) return false
        if (catalogIntegrityIssues.isNotEmpty()) {
            state.update {
                it.copy(
                    curatedDishes = emptyList(),
                    cartEntries = emptyList(),
                    errorMessage =
                        "The restaurant menu presentation does not match the current recipe " +
                            "and price snapshot. Please ask staff.",
                )
            }
            return false
        }
        val summary = state.value.intent ?: return false
        state.update {
            it.copy(
                curatedDishes = emptyList(),
                excludedDishCount = 0,
                cartEntries = emptyList(),
                orderSuggestions = emptyList(),
                dismissedSuggestionRefs = emptySet(),
                acceptedPairingRefs = emptySet(),
                suggestionNotice = null,
                confirmedMenuRevision = null,
                confirmedPolicyVersion = null,
                confirmedIntentFingerprint = null,
                staffAcknowledgment = null,
                waiterHandoff = null,
            )
        }
        val resolution = menuPolicyEngine.evaluateMenu(
            snapshot = catalog.snapshot,
            intent = summary.toConfirmedIntent(state.value.sessionId),
            now = clock.instant(),
        )
        val resolved = when (resolution) {
            is MenuResolution.CatalogReady -> ResolvedCatalog(
                resolution.menuRevision,
                resolution.policyVersion,
                resolution.evaluations,
            )
            is MenuResolution.NoMatches -> ResolvedCatalog(
                resolution.menuRevision,
                resolution.policyVersion,
                resolution.evaluations,
            )
            is MenuResolution.ClarificationRequired -> {
                state.update {
                    it.copy(errorMessage = "Please review the highlighted dining needs.")
                }
                return false
            }
            is MenuResolution.Blocked -> {
                state.update {
                    it.copy(errorMessage = "The current menu information needs staff review.")
                }
                return false
            }
        }

        val salesEvidence = currentSalesEvidence()
        val visible = resolved.evaluations
            .filter {
                it.eligibility == Eligibility.CANDIDATE ||
                    it.eligibility == Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION ||
                    it.eligibility == Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA
            }
            .mapNotNull { evaluation ->
                catalog.dish(evaluation.itemId, evaluation.variantId)?.let {
                    CuratedDish(
                        dish = it,
                        evaluation = evaluation,
                        matchReasons = matchReasons(it, summary, salesEvidence),
                        salesEvidence = salesEvidence.factFor(it),
                    )
                }
            }
            .sortedWith(curatedDishComparator(summary, salesEvidence))
        val excluded = resolved.evaluations.size - visible.size
        state.update {
            it.copy(
                curatedDishes = visible,
                excludedDishCount = excluded,
                confirmedMenuRevision = resolved.menuRevision,
                confirmedPolicyVersion = resolved.policyVersion,
                confirmedIntentFingerprint = summary.fingerprint(),
                assistantMessage = catalogSummary(visible, excluded),
                errorMessage = null,
            )
        }
        return true
    }

    fun addDishToShortlist(
        itemId: String,
        variantId: String,
    ) {
        val result = state.value.curatedDishes.firstOrNull {
            it.dish.id.value == itemId && it.dish.variantId.value == variantId
        } ?: return
        addCuratedDishToShortlist(result)
    }

    fun addPairingToShortlist(
        itemId: String,
        variantId: String,
    ) {
        val current = state.value
        if (current.acceptedPairingRefs.size >= MAX_ACCEPTED_PAIRINGS) {
            state.update {
                it.copy(
                    orderSuggestions = emptyList(),
                    suggestionNotice = "You’ve added the maximum of 2 optional pairings.",
                )
            }
            return
        }
        val previouslyShown = current.orderSuggestions.firstOrNull {
            it.variant.itemId.value == itemId && it.variant.variantId.value == variantId
        } ?: return
        val currentSuggestions = when (val resolution = resolveOrderSuggestions(current)) {
            is SuggestionResolution.Ready -> resolution.suggestions
            SuggestionResolution.Blocked -> {
                state.update {
                    it.copy(
                        orderSuggestions = emptyList(),
                        suggestionNotice =
                            "Pairing suggestions are paused because the menu or sales record changed.",
                    )
                }
                return
            }
        }
        val suggestion = currentSuggestions.firstOrNull {
            it.variant == previouslyShown.variant
        }
        if (suggestion == null) {
            state.update {
                it.copy(
                    orderSuggestions = currentSuggestions,
                    suggestionNotice =
                        "That pairing is no longer supported by current menu and sales evidence.",
                )
            }
            return
        }
        val result = current.curatedDishes.firstOrNull {
            it.evaluation.itemId == suggestion.variant.itemId &&
                it.evaluation.variantId == suggestion.variant.variantId
        } ?: return
        addCuratedDishToShortlist(result, acceptedPairing = suggestion.variant)
    }

    fun dismissSuggestion(
        itemId: String,
        variantId: String,
    ) {
        val suggestion = state.value.orderSuggestions.firstOrNull {
            it.variant.itemId.value == itemId && it.variant.variantId.value == variantId
        } ?: return
        state.update {
            it.copy(
                orderSuggestions = it.orderSuggestions.filterNot { current ->
                    current.variant == suggestion.variant
                },
                dismissedSuggestionRefs = it.dismissedSuggestionRefs + suggestion.variant,
            )
        }
        refreshOrderSuggestions()
    }

    private fun addCuratedDishToShortlist(
        result: CuratedDish,
        acceptedPairing: MenuVariantRef? = null,
    ) {
        if (!result.canAddToOrder) {
            requestStaff("Staff must verify ${result.dish.name} before it can be shortlisted.")
            return
        }
        val wholeOrderBudget = state.value.intent?.wholeOrderBudgetLimit()
        if (wholeOrderBudget != null) {
            val currentTotal = shortlistedDishes().sumOf(MenuDish::priceMinor)
            if (result.dish.priceMinor > wholeOrderBudget - currentTotal) {
                state.update {
                    it.copy(
                        suggestionNotice =
                            "${result.dish.name} was not added because it would exceed the " +
                                "confirmed whole-order budget of ${formatPeso(wholeOrderBudget)}.",
                        errorMessage =
                            "Change My Picks or confirm a different whole-order budget.",
                    )
                }
                return
            }
        }
        val recipeRevision = recipeRevision(
            itemId = result.evaluation.itemId,
            variantId = result.evaluation.variantId,
        ) ?: return
        val entry = CartEntry(
            itemId = result.evaluation.itemId.value,
            variantId = result.evaluation.variantId.value,
            recipeRevision = recipeRevision,
            eligibility = result.evaluation.eligibility,
        )
        if (
            state.value.cartEntries.any {
                it.itemId == entry.itemId && it.variantId == entry.variantId
            }
        ) {
            return
        }
        state.update { current ->
            current.copy(
                cartEntries = current.cartEntries + entry,
                acceptedPairingRefs = acceptedPairing?.let {
                    current.acceptedPairingRefs + it
                } ?: current.acceptedPairingRefs,
                staffAcknowledgment = null,
                waiterHandoff = null,
                errorMessage = null,
            )
        }
        refreshOrderSuggestions()
    }

    fun removeDishFromShortlist(
        itemId: String,
        variantId: String,
    ) {
        state.update { current ->
            val removedRefs = current.cartEntries
                .filter { it.itemId == itemId && it.variantId == variantId }
                .mapTo(mutableSetOf()) {
                    MenuVariantRef(
                        itemId = MenuItemId(it.itemId),
                        variantId = VariantId(it.variantId),
                        recipeRevision = it.recipeRevision,
                    )
                }
            current.copy(
                cartEntries = current.cartEntries.filterNot { entry ->
                    entry.itemId == itemId && entry.variantId == variantId
                },
                dismissedSuggestionRefs = current.dismissedSuggestionRefs + removedRefs,
                acceptedPairingRefs = current.acceptedPairingRefs - removedRefs,
                staffAcknowledgment = null,
                waiterHandoff = null,
                errorMessage = null,
            )
        }
        refreshOrderSuggestions()
    }

    fun shortlistedDishes(): List<MenuDish> = state.value.cartEntries.mapNotNull { entry ->
        catalog.dish(MenuItemId(entry.itemId), VariantId(entry.variantId))
    }

    fun requestStaff(message: String = "Staff assistance requested.") {
        state.update {
            it.copy(
                staffRequestMessage =
                    "$message Demo only: no staff notification was sent from this tablet.",
            )
        }
    }

    fun dismissStaffMessage() {
        state.update { it.copy(staffRequestMessage = null) }
    }

    fun verifyStaffPin(pin: String): Boolean {
        val current = state.value
        val menuRevision = current.confirmedMenuRevision ?: return false
        val policyVersion = current.confirmedPolicyVersion ?: return false
        val intentFingerprint = current.confirmedIntentFingerprint ?: return false
        if (current.cartEntries.isEmpty()) return false
        val staff = staffAuthorizer.authorize(pin)
        if (staff == null) {
            state.update { it.copy(errorMessage = "Staff PIN was not recognized.") }
            return false
        }
        val acknowledgedAt = clock.instant()
        val acknowledgment = StaffAcknowledgment(
            staffId = staff.id,
            staffDisplayName = staff.displayName,
            acknowledgedAt = acknowledgedAt,
            menuRevision = menuRevision,
            policyVersion = policyVersion,
            intentFingerprint = intentFingerprint,
            cartFingerprint = current.cartFingerprint,
        )
        state.update {
            it.copy(
                staffAcknowledgment = acknowledgment,
                waiterHandoff = it.waiterHandoff?.copy(
                    staffReview = acknowledgment.toHandoffStaffReview(),
                ),
                staffRequestMessage = "Verification recorded by ${staff.displayName}.",
                errorMessage = null,
            )
        }
        return true
    }

    /**
     * Creates a guest-controlled waiter handoff after re-validating the exact shortlist against the
     * current menu and safety policy. This does not submit an order, contact a kitchen, or imply
     * that unresolved ingredient/preparation questions have been approved.
     */
    fun createWaiterHandoff(): Boolean {
        val current = state.value
        val summary = current.intent
            ?: return rejectHandoff("The dining request must be confirmed again.")
        if (current.cartEntries.isEmpty()) {
            return rejectHandoff("Shortlist at least one dish.")
        }
        if (current.confirmedIntentFingerprint != summary.fingerprint()) {
            return rejectHandoff("The dining request changed. Generate the matches again.")
        }

        val resolution = menuPolicyEngine.evaluateMenu(
            snapshot = catalog.snapshot,
            intent = summary.toConfirmedIntent(current.sessionId),
            now = clock.instant(),
        )
        val resolved = when (resolution) {
            is MenuResolution.CatalogReady -> ResolvedCatalog(
                resolution.menuRevision,
                resolution.policyVersion,
                resolution.evaluations,
            )
            is MenuResolution.NoMatches -> ResolvedCatalog(
                resolution.menuRevision,
                resolution.policyVersion,
                resolution.evaluations,
            )
            is MenuResolution.ClarificationRequired ->
                return rejectHandoff("The dining request needs clarification.")
            is MenuResolution.Blocked ->
                return rejectHandoff("Menu facts changed or require waiter review.")
        }
        if (
            resolved.menuRevision != current.confirmedMenuRevision ||
            resolved.policyVersion != current.confirmedPolicyVersion
        ) {
            return rejectHandoff("The menu or safety policy changed. Generate the matches again.")
        }

        val evaluations = resolved.evaluations.associateBy {
            it.itemId.value to it.variantId.value
        }
        val currentEntries = current.cartEntries.map { entry ->
            val evaluation = evaluations[entry.itemId to entry.variantId]
                ?: return rejectHandoff("A shortlisted menu variant is no longer available.")
            val currentRecipeRevision = recipeRevision(
                MenuItemId(entry.itemId),
                VariantId(entry.variantId),
            )
            if (currentRecipeRevision != entry.recipeRevision) {
                return rejectHandoff("A shortlisted recipe changed. Generate the matches again.")
            }
            if (
                evaluation.eligibility != Eligibility.CANDIDATE &&
                evaluation.eligibility != Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION
            ) {
                return rejectHandoff("A shortlisted dish no longer matches the confirmed request.")
            }
            entry.copy(eligibility = evaluation.eligibility)
        }
        summary.wholeOrderBudgetLimit()?.let { maximum ->
            val currentTotal = currentEntries.sumOf { entry ->
                catalog.dish(MenuItemId(entry.itemId), VariantId(entry.variantId))
                    ?.priceMinor
                    ?: return rejectHandoff("A shortlisted dish is missing current price data.")
            }
            if (currentTotal > maximum) {
                return rejectHandoff(
                    "My Picks exceed the confirmed whole-order budget. Change the shortlist first.",
                )
            }
        }

        val sequence = handoffSequence.incrementAndGet()
        val reference = "MP-H-${sequence.toString().padStart(4, '0')}"
        val createdAt = clock.instant()
        val waiterHandoff = buildWaiterHandoff(
            reference = reference,
            createdAt = createdAt,
            current = current,
            summary = summary,
            entries = currentEntries,
            menuRevision = resolved.menuRevision,
            policyVersion = resolved.policyVersion,
        )
        state.update {
            it.copy(
                cartEntries = currentEntries,
                waiterHandoff = waiterHandoff,
                errorMessage = null,
            )
        }
        return true
    }

    fun beginFeedback() {
        state.update {
            it.copy(feedback = FeedbackUiState(restaurantName = catalog.restaurantName))
        }
    }

    fun updateFeedbackRating(dimension: FeedbackDimension, rating: Int) {
        if (rating !in 1..5) return
        state.update {
            it.copy(
                feedback = it.feedback.copy(
                    ratings = it.feedback.ratings + (dimension to rating),
                    approvedPublicDraft = null,
                    reviewCopied = false,
                    privateSubmissionRecorded = false,
                ),
            )
        }
    }

    fun toggleFeedbackTag(tagId: String) {
        state.update {
            val selected = it.feedback.selectedTagIds
            it.copy(
                feedback = it.feedback.copy(
                    selectedTagIds = if (tagId in selected) selected - tagId else selected + tagId,
                    approvedPublicDraft = null,
                    reviewCopied = false,
                    privateSubmissionRecorded = false,
                ),
            )
        }
    }

    fun updatePrivateFeedbackNote(note: String) {
        state.update {
            it.copy(
                feedback = it.feedback.copy(
                    privateNote = note.take(MAX_PRIVATE_NOTE_LENGTH),
                    privateSubmissionRecorded = false,
                ),
            )
        }
    }

    fun updateManagerFollowUpRequested(requested: Boolean) {
        state.update {
            it.copy(
                feedback = it.feedback.copy(
                    managerFollowUpRequested = requested,
                    privateSubmissionRecorded = false,
                ),
            )
        }
    }

    /**
     * Records the guest-controlled private payload in the local prototype state. A production
     * venue must replace this with an authenticated restaurant feedback service.
     */
    fun recordPrivateFeedback(submission: PrivateFeedbackSubmission): Boolean {
        val expected = state.value.feedback.toPrivateSubmission()
        if (submission != expected || !state.value.feedback.allRatingsComplete) return false
        state.update {
            it.copy(
                feedback = it.feedback.copy(
                    privateSubmissionRecorded = true,
                ),
            )
        }
        return true
    }

    fun continueFeedback() {
        state.update { current ->
            when (current.feedback.stage) {
                FeedbackStage.RATINGS -> {
                    if (!current.feedback.allRatingsComplete) current
                    else current.copy(
                        feedback = current.feedback.copy(stage = FeedbackStage.TAGS),
                    )
                }
                FeedbackStage.TAGS -> {
                    val generated = feedbackCommentGenerator.generate(current.feedback.toGuestFeedback())
                    current.copy(
                        feedback = current.feedback.copy(
                            stage = FeedbackStage.REVIEW,
                            generatedReview = generated,
                            approvedPublicDraft = null,
                            reviewCopied = false,
                        ),
                    )
                }
                FeedbackStage.REVIEW -> current
            }
        }
    }

    fun previousFeedbackStage(): Boolean {
        val previous = when (state.value.feedback.stage) {
            FeedbackStage.RATINGS -> return false
            FeedbackStage.TAGS -> FeedbackStage.RATINGS
            FeedbackStage.REVIEW -> FeedbackStage.TAGS
        }
        state.update { it.copy(feedback = it.feedback.copy(stage = previous)) }
        return true
    }

    fun updateGeneratedReview(review: String) {
        state.update {
            it.copy(
                feedback = it.feedback.copy(
                    generatedReview = review.take(MAX_REVIEW_LENGTH),
                    approvedPublicDraft = null,
                    reviewCopied = false,
                    privateSubmissionRecorded = false,
                ),
            )
        }
    }

    fun updatePublicDraftApproval(approved: Boolean) {
        state.update {
            it.copy(
                feedback = it.feedback.copy(
                    approvedPublicDraft = it.feedback.generatedReview.takeIf { draft ->
                        approved &&
                            draft.isNotBlank() &&
                            it.feedback.allRatingsComplete &&
                            it.feedback.stage == FeedbackStage.REVIEW
                    },
                ),
            )
        }
    }

    fun approvedPublicReviewDraft(): String? =
        state.value.feedback.approvedPublicDraft
            ?.takeIf { state.value.feedback.publicDraftApproved }

    fun markReviewCopied(copied: Boolean) {
        state.update {
            it.copy(feedback = it.feedback.copy(reviewCopied = copied))
        }
    }

    fun resetSession() {
        cancelAssistantRequest()
        forgetAssistantSession(state.value.sessionId)
        state.value = newSessionState(nextSessionId())
        persistConversationSession()
    }

    private fun cancelAssistantRequest() {
        assistantRequestSequence += 1
        assistantJob?.cancel()
        assistantJob = null
    }

    private fun forgetAssistantSession(sessionId: String) {
        viewModelScope.launch {
            runCatching { intentAssistant.forgetSession(sessionId) }
        }
    }

    private fun refreshOrderSuggestions() {
        val current = state.value
        val intent = current.intent
        if (
            intent == null ||
            current.cartEntries.isEmpty() ||
            current.confirmedIntentFingerprint != intent.fingerprint()
        ) {
            state.update {
                it.copy(orderSuggestions = emptyList(), suggestionNotice = null)
            }
            return
        }
        if (current.acceptedPairingRefs.size >= MAX_ACCEPTED_PAIRINGS) {
            state.update {
                it.copy(
                    orderSuggestions = emptyList(),
                    suggestionNotice = "You’ve added the maximum of 2 optional pairings.",
                )
            }
            return
        }

        when (val resolution = resolveOrderSuggestions(current)) {
            is SuggestionResolution.Ready -> {
                state.update {
                    it.copy(
                        orderSuggestions = resolution.suggestions,
                        suggestionNotice = null,
                    )
                }
            }
            SuggestionResolution.Blocked -> {
                state.update {
                    it.copy(
                        orderSuggestions = emptyList(),
                        suggestionNotice =
                            "Pairing suggestions are paused because the menu or sales record changed.",
                    )
                }
            }
        }
    }

    private fun resolveOrderSuggestions(
        current: MenuPilotUiState,
    ): SuggestionResolution {
        val intent = current.intent ?: return SuggestionResolution.Ready(emptyList())
        if (
            current.cartEntries.isEmpty() ||
            current.confirmedIntentFingerprint != intent.fingerprint()
        ) {
            return SuggestionResolution.Ready(emptyList())
        }
        val cartRefs = current.cartEntries.mapTo(mutableSetOf()) {
            MenuVariantRef(
                itemId = MenuItemId(it.itemId),
                variantId = VariantId(it.variantId),
                recipeRevision = it.recipeRevision,
            )
        }
        val resolution = recommendationEngine.recommend(
            RecommendationRequest(
                menu = catalog.snapshot,
                intent = intent.toConfirmedIntent(current.sessionId),
                merchandising = catalog.merchandisingSnapshot,
                now = clock.instant(),
                placement = RecommendationPlacement.CART_COMPLEMENT,
                cart = cartRefs,
                dismissed = current.dismissedSuggestionRefs,
            ),
        )
        if (resolution is RecommendationResolution.Blocked) {
            return SuggestionResolution.Blocked
        }
        resolution as RecommendationResolution.Ready

        val cartTotal = current.cartEntries.sumOf { entry ->
            catalog.dish(MenuItemId(entry.itemId), VariantId(entry.variantId))?.priceMinor ?: 0L
        }
        val orderTotalBudget = intent.wholeOrderBudgetLimit()
        val suggestions = resolution.recommendations
            .mapNotNull { recommendation ->
                catalog.dish(
                    recommendation.variant.itemId,
                    recommendation.variant.variantId,
                )?.takeIf { dish ->
                    orderTotalBudget == null ||
                        cartTotal + dish.priceMinor <= orderTotalBudget
                }?.let { dish ->
                    recommendation.toOrderSuggestion(dish)
                }
            }
            .take(MAX_ACCEPTED_PAIRINGS - current.acceptedPairingRefs.size)
        return SuggestionResolution.Ready(suggestions)
    }

    private fun MenuRecommendation.toOrderSuggestion(dish: MenuDish): OrderSuggestion {
        val approved = reasons.filterIsInstance<RecommendationReason.ApprovedPairingEvidence>()
            .firstOrNull()
        val affinity = reasons.filterIsInstance<RecommendationReason.CurrentAffinityEvidence>()
            .firstOrNull()
        val preference = reasons
            .filterIsInstance<RecommendationReason.MatchesConfirmedPreferences>()
            .firstOrNull()
        val popular = reasons.filterIsInstance<RecommendationReason.PopularInWindow>()
            .firstOrNull()
        val anchorName = approved?.anchor?.let {
            catalog.dish(it.itemId, it.variantId)?.name
        } ?: affinity?.anchor?.let {
            catalog.dish(it.itemId, it.variantId)?.name
        }
        val reason = when (approved?.reason) {
            ApprovedPairingReason.COMPLEMENTS_DISH ->
                "Restaurant-approved pairing${anchorName?.let { " with $it" }.orEmpty()}"
            ApprovedPairingReason.COMPLETES_MEAL -> "Completes your meal"
            null -> when {
                affinity != null -> "Often ordered with ${anchorName ?: "your selection"}"
                preference != null -> "Matches your confirmed preferences"
                else -> "Restaurant suggestion"
            }
        }
        val evidence = when {
            affinity != null -> {
                val presentation = SalesEvidencePresentation.from(
                    source = affinity.source,
                    windowStart = affinity.windowStart,
                    windowEnd = affinity.windowEnd,
                    observedAt = affinity.observedAt,
                )
                "Ordered together ${affinity.coOrderCount} times • " +
                    "${presentation.windowLabel.lowercase()} • ${presentation.sourceLabel}"
            }
            popular != null -> {
                val presentation = SalesEvidencePresentation.from(
                    source = popular.source,
                    windowStart = popular.windowStart,
                    windowEnd = popular.windowEnd,
                    observedAt = popular.observedAt,
                )
                "${popular.orderCount} orders • ${presentation.windowLabel.lowercase()} • " +
                    presentation.sourceLabel
            }
            else -> null
        }
        return OrderSuggestion(
            dish = dish,
            variant = variant,
            eligibility = eligibility,
            reason = reason,
            evidence = evidence,
        )
    }

    private fun recipeRevision(itemId: MenuItemId, variantId: VariantId): String? =
        catalog.snapshot.items
            .firstOrNull { it.id == itemId }
            ?.variants
            ?.firstOrNull { it.id == variantId }
            ?.recipeRevision

    private fun buildWaiterHandoff(
        reference: String,
        createdAt: Instant,
        current: MenuPilotUiState,
        summary: DiningIntentSummary,
        entries: List<CartEntry>,
        menuRevision: String,
        policyVersion: String,
    ): WaiterHandoffPayload {
        val picks = entries.map { entry ->
            val dish = requireNotNull(
                catalog.dish(MenuItemId(entry.itemId), VariantId(entry.variantId)),
            )
            val ref = MenuVariantRef(
                itemId = MenuItemId(entry.itemId),
                variantId = VariantId(entry.variantId),
                recipeRevision = entry.recipeRevision,
            )
            HandoffPick(
                itemId = entry.itemId,
                variantId = entry.variantId,
                recipeRevision = entry.recipeRevision,
                name = dish.name,
                priceMinor = dish.priceMinor,
                eligibility = entry.eligibility,
                origin = if (ref in current.acceptedPairingRefs) {
                    HandoffPickOrigin.ACCEPTED_OPTIONAL_PAIRING
                } else {
                    HandoffPickOrigin.GUEST_SELECTION
                },
                requiresStaffConfirmation = entry.requiresStaffConfirmation,
            )
        }
        val questions = buildList {
            if (summary.allergens.isNotEmpty()) {
                add(
                    HandoffQuestion(
                        code = HandoffQuestionCode.CONFIRM_ALLERGY_AND_CROSS_CONTACT,
                        prompt =
                            "Confirm the guest's ${summary.allergens.sorted().joinToString()} " +
                                "avoidance, current recipe, cross-contact, and preparation with " +
                                "the kitchen.",
                    ),
                )
            }
            picks.filter(HandoffPick::requiresStaffConfirmation).forEach { pick ->
                add(
                    HandoffQuestion(
                        code =
                            HandoffQuestionCode.CONFIRM_CURRENT_INGREDIENTS_AND_PREPARATION,
                        prompt =
                            "Confirm current ingredients and preparation for ${pick.name}.",
                        itemId = pick.itemId,
                        variantId = pick.variantId,
                    ),
                )
            }
            add(
                HandoffQuestion(
                    code = HandoffQuestionCode.CONFIRM_AVAILABILITY_AND_FINAL_CHOICE,
                    prompt =
                        "Confirm availability, substitutions, and the guest's final choices " +
                            "before placing any order.",
                ),
            )
        }.distinct()
        val placement = when (catalog.tabletConfig.placement) {
            ServicePlacement.TABLE -> HandoffPlacement.TABLE
            ServicePlacement.COUNTER -> HandoffPlacement.COUNTER
        }
        return WaiterHandoffPayload(
            reference = reference,
            createdAt = createdAt,
            sessionId = current.sessionId,
            locationLabel = catalog.tabletConfig.locationLabel,
            placement = placement,
            deliveryMode = HandoffDeliveryMode.SCREEN_ONLY,
            liveStaffChannelConfigured = catalog.tabletConfig.liveStaffChannelConnected,
            menuRevision = menuRevision,
            menuGeneratedAt = catalog.snapshot.generatedAt,
            safetyPolicyVersion = policyVersion,
            merchandisingRevision = catalog.merchandisingSnapshot.revision,
            intentFingerprint = requireNotNull(current.confirmedIntentFingerprint),
            diningNeeds = summary.toHandoffDiningNeeds(),
            picks = picks,
            unresolvedQuestions = questions,
            estimatedTotalMinor = picks.sumOf(HandoffPick::priceMinor),
            staffReview = current.staffAcknowledgment
                ?.takeIf { current.staffAcknowledged }
                ?.toHandoffStaffReview(),
        )
    }

    private fun rejectHandoff(message: String): Boolean {
        state.update { it.copy(errorMessage = message) }
        return false
    }

    private fun catalogSummary(visible: List<CuratedDish>, excluded: Int): String {
        val orderable = visible.count(CuratedDish::canAddToOrder)
        val staffChecks = visible.count {
            it.evaluation.eligibility == Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA
        }
        return buildString {
            append("I found $orderable menu matches")
            if (staffChecks > 0) append(" and $staffChecks item that staff must check")
            if (excluded > 0) append(". $excluded conflicting or unavailable items are hidden")
            append(".")
        }
    }

    private fun matchReasons(
        dish: MenuDish,
        intent: DiningIntentSummary,
        salesEvidence: SalesEvidenceResolution,
    ): List<String> = buildList {
        if ("vegetarian" in intent.diets && "Vegetarian" in dish.dietaryTags) {
            add("Vegetarian")
        }
        intent.preferredAttributes.forEach { attribute ->
            val label = when (attribute) {
                "spicy" -> "Spice preference"
                "mild" -> "Mild spice"
                "light" -> "Light choice"
                "quick" -> "Quick to prepare"
                "popular" -> "Bestseller ${salesWindowLabel.lowercase()}"
                else -> null
            }
            if (label != null && dishMatchesAttribute(dish, attribute, salesEvidence)) {
                add(label)
            }
        }
        intent.maximumPriceMinor?.let { maximum ->
            if (dish.priceMinor <= maximum) add("Within ${formatPeso(maximum)}")
        }
        if (isEmpty()) {
            add(
                if (intent.isNeutralBrowseIntent) {
                    "Available on today’s menu"
                } else {
                    "Matches confirmed request"
                },
            )
        }
    }.distinct().take(3)

    private fun dishMatchesAttribute(
        dish: MenuDish,
        attribute: String,
        salesEvidence: SalesEvidenceResolution,
    ): Boolean = when (attribute) {
        "spicy" -> dish.flavorTags.any { it.equals("Spicy", ignoreCase = true) }
        "mild" -> dish.flavorTags.none { it.equals("Spicy", ignoreCase = true) }
        "light" -> dish.flavorTags.any { it.equals("Light", ignoreCase = true) }
        "quick" -> dish.prepMinutes <= 12
        "popular" -> salesEvidence is SalesEvidenceResolution.Ready &&
            salesEvidence.isBestseller(dish.variantRef())
        else -> false
    }

    private fun curatedDishComparator(
        intent: DiningIntentSummary,
        evidence: SalesEvidenceResolution,
    ): Comparator<CuratedDish> {
        val counts = if (evidence is SalesEvidenceResolution.Ready) {
            evidence.signalsByVariant.mapValues { it.value.orderCount }
        } else {
            emptyMap()
        }
        val popularityRequested = "popular" in intent.preferredAttributes
        return compareBy<CuratedDish> { it.evaluation.eligibility.displayOrder }
            .thenByDescending { it.evaluation.softScore }
            .thenByDescending {
                if (popularityRequested) counts[it.dish.variantRef()] ?: -1L else 0L
            }
            .thenBy { it.dish.category.catalogOrder }
            .thenByDescending { counts[it.dish.variantRef()] ?: -1L }
            .thenBy { it.dish.id.value }
            .thenBy { it.dish.variantId.value }
    }

    private fun currentSalesEvidence(): SalesEvidenceResolution =
        salesEvidencePolicy.resolve(
            snapshot = catalog.merchandisingSnapshot,
            now = clock.instant(),
        )

    private fun currentSalesPresentation(): SalesEvidencePresentation =
        SalesEvidencePresentation.from(currentSalesEvidence())

    private fun MenuDish.variantRef(): MenuVariantRef = MenuVariantRef(
        itemId = id,
        variantId = variantId,
        recipeRevision = recipeRevision(id, variantId).orEmpty(),
    )

    private fun SalesEvidenceResolution.factFor(
        dish: MenuDish,
    ): DishSalesEvidence? {
        if (this !is SalesEvidenceResolution.Ready) return null
        val variant = dish.variantRef()
        val signal = signalsByVariant[variant] ?: return null
        return DishSalesEvidence(
            orderCount = signal.orderCount,
            windowStart = signal.windowStart,
            windowEnd = signal.windowEnd,
            observedAt = signal.observedAt,
            sourceId = source.id,
            sourceLabel = source.displayName,
            isBestseller = isBestseller(variant),
        )
    }

    private fun newSessionState(sessionId: String) = MenuPilotUiState(
        sessionId = sessionId,
        conversationTurns = listOf(
            ConversationTurn(
                role = ConversationRole.ASSISTANT,
                message = DEFAULT_CONVERSATION_GREETING,
            ),
        ),
        feedback = FeedbackUiState(restaurantName = catalog.restaurantName),
    )

    private fun RestoredConversationSession.toUiState(): MenuPilotUiState {
        val interruptedTurn = turns.lastOrNull()?.takeIf {
            it.role == ConversationRole.GUEST
        }
        val interruptionMessage =
            "That response was interrupted when the tablet session was restored. " +
                "Send it again; your earlier confirmed needs are still retained."
        return newSessionState(sessionId).copy(
            query = if (interruptedTurn != null) {
                query.ifBlank { interruptedTurn.message.take(MAX_QUERY_CHARS) }
            } else {
                query
            },
            intent = intent,
            conversationTurns = (
                turns.ifEmpty {
                    listOf(
                        ConversationTurn(
                            ConversationRole.ASSISTANT,
                            DEFAULT_CONVERSATION_GREETING,
                        ),
                    )
                } + if (interruptedTurn != null) {
                    listOf(
                        ConversationTurn(
                            ConversationRole.ASSISTANT,
                            interruptionMessage,
                        ),
                    )
                } else {
                    emptyList()
                }
                ),
            conversationNeedsClarification = needsClarification || interruptedTurn != null,
            pendingClarification = if (interruptedTurn != null) {
                ClarificationRequirement.GENERAL_REQUEST
            } else {
                pendingClarification
            },
            focusedDishName = focusedDishName,
            assistantMessage = if (interruptedTurn != null) {
                interruptionMessage
            } else {
                assistantMessage
            },
            assistantSource = assistantSource,
            assistantFallbackReason = assistantFallbackReason,
            errorMessage = if (needsClarification || interruptedTurn != null) {
                if (interruptedTurn != null) interruptionMessage else assistantMessage
            } else {
                null
            },
        )
    }

    private fun persistConversationSession() {
        conversationStore.save(state.value)
    }

    private fun mergeConversationIntent(
        previous: DiningIntentSummary?,
        incoming: DiningIntentSummary,
        latestQuery: String,
    ): DiningIntentSummary {
        if (previous == null) return incoming

        val allergens = (previous.allergens + incoming.allergens).distinct()
        val reasons = allergens.associateWith { allergen ->
            strongerAvoidanceReason(
                previous.avoidanceReasons[allergen],
                incoming.avoidanceReasons[allergen],
            )
        }
        val incomingPreferences = incoming.preferredAttributes.toSet()
        val previousPreferences = previous.preferredAttributes
            .filterNot {
                it == "popular" &&
                    previous.intentDraft.constraints.none { constraint ->
                        constraint.canonicalId == "popular"
                    } &&
                    incomingPreferences.isNotEmpty()
            }
            .filterNot {
                (it == "spicy" && "mild" in incomingPreferences) ||
                    (it == "mild" && "spicy" in incomingPreferences)
            }
        val sourceQuery = listOf(previous.sourceQuery, latestQuery)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")
            .take(MAX_SESSION_SOURCE_CHARS)
        val mergedMaximumPrice = listOfNotNull(
            previous.maximumPriceMinor,
            incoming.maximumPriceMinor,
        ).minOrNull()
        val merged = incoming.copy(
            sourceQuery = sourceQuery,
            allergens = allergens,
            avoidanceReasons = reasons,
            diets = (previous.diets + incoming.diets).distinct(),
            preferredAttributes =
                (previousPreferences + incoming.preferredAttributes).distinct(),
            maximumPriceMinor = mergedMaximumPrice,
            budgetScope = when {
                mergedMaximumPrice == null -> null
                previous.budgetScope == DiningBudgetScope.WHOLE_ORDER ||
                    incoming.budgetScope == DiningBudgetScope.WHOLE_ORDER ->
                    DiningBudgetScope.WHOLE_ORDER
                else ->
                    incoming.budgetScope
                        ?: previous.budgetScope
                        ?: DiningBudgetScope.PER_DISH
            },
        )
        return merged.copy(intentDraft = merged.rebuiltDraft())
    }

    private fun List<ConversationTurn>.boundedForModel(): List<ConversationTurn> {
        val selected = ArrayDeque<ConversationTurn>()
        var usedCharacters = 0
        for (turn in asReversed()) {
            if (selected.size >= MAX_MODEL_CONTEXT_TURNS) break
            val sanitized = turn.message
                .map { character -> if (character.isISOControl()) ' ' else character }
                .joinToString("")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(MAX_MODEL_TURN_CHARS)
            if (sanitized.isBlank()) continue
            if (
                selected.isNotEmpty() &&
                usedCharacters + sanitized.length > MAX_MODEL_CONTEXT_CHARS
            ) {
                break
            }
            val remaining = MAX_MODEL_CONTEXT_CHARS - usedCharacters
            val boundedMessage = sanitized.take(remaining)
            if (boundedMessage.isBlank()) break
            selected.addFirst(turn.copy(message = boundedMessage))
            usedCharacters += boundedMessage.length
        }
        return selected.toList()
    }

    private fun nextSessionId(): String {
        val restoredOrdinal = state.value.sessionId
            .substringAfter("session-", missingDelimiterValue = "")
            .toIntOrNull()
            ?: 0
        val nextOrdinal = sessionSequence.updateAndGet { current ->
            maxOf(current, restoredOrdinal) + 1
        }
        return "session-$nextOrdinal"
    }

    private val Eligibility.displayOrder: Int
        get() = when (this) {
            Eligibility.CANDIDATE_REQUIRES_STAFF_CONFIRMATION -> 0
            Eligibility.CANDIDATE -> 1
            Eligibility.NOT_CANDIDATE_INSUFFICIENT_DATA -> 2
            Eligibility.NOT_CANDIDATE_CONSTRAINT_CONFLICT -> 3
            Eligibility.NOT_CANDIDATE_UNAVAILABLE -> 4
        }

    private data class ResolvedCatalog(
        val menuRevision: String,
        val policyVersion: String,
        val evaluations: List<ItemEvaluation>,
    )

    private sealed interface SuggestionResolution {
        data class Ready(
            val suggestions: List<OrderSuggestion>,
        ) : SuggestionResolution

        data object Blocked : SuggestionResolution
    }

    private companion object {
        const val MAX_REVIEW_LENGTH = 2_000
        const val MAX_PRIVATE_NOTE_LENGTH = 1_000
        const val MAX_ACCEPTED_PAIRINGS = 2
        const val MAX_MODEL_CONTEXT_TURNS = 8
        const val MAX_MODEL_CONTEXT_CHARS = 3_000
        const val MAX_MODEL_TURN_CHARS = 700
        val handoffSequence = AtomicInteger(1041)
        val sessionSequence = AtomicInteger(1)
    }
}

private val DiningIntentSummary.authoritativeHardConstraintCount: Int
    get() = allergens.size + diets.size + if (maximumPriceMinor != null) 1 else 0

private fun ClarificationRequirement.isResolvedBy(
    summary: DiningIntentSummary,
): Boolean = when (this) {
    ClarificationRequirement.NAMED_HEALTH_CONSTRAINT,
    ClarificationRequirement.ALLERGEN_PURPOSE,
    -> summary.allergens.isNotEmpty()
    ClarificationRequirement.BUDGET_AMOUNT -> summary.maximumPriceMinor != null
    ClarificationRequirement.GENERAL_REQUEST ->
        summary.intentDraft.constraints.isNotEmpty()
    ClarificationRequirement.SUPPORTED_RESTRICTION ->
        summary.allergens.isNotEmpty() || summary.diets.isNotEmpty()
    ClarificationRequirement.DISH_REFERENCE,
    ClarificationRequirement.CATALOG_STAFF_REVIEW,
    -> false
}

private fun ClarificationRequirement.preservingMoreProtectiveThan(
    incoming: ClarificationRequirement,
): ClarificationRequirement =
    if (protectionPriority >= incoming.protectionPriority) this else incoming

private val ClarificationRequirement.protectionPriority: Int
    get() = when (this) {
        ClarificationRequirement.CATALOG_STAFF_REVIEW -> 7
        ClarificationRequirement.NAMED_HEALTH_CONSTRAINT -> 6
        ClarificationRequirement.ALLERGEN_PURPOSE -> 5
        ClarificationRequirement.SUPPORTED_RESTRICTION -> 4
        ClarificationRequirement.BUDGET_AMOUNT -> 3
        ClarificationRequirement.DISH_REFERENCE -> 2
        ClarificationRequirement.GENERAL_REQUEST -> 1
    }

private fun ClarificationRequirement.isResolvedBy(
    route: FunctionGemmaRoute,
    resolutionNeedsClarification: Boolean,
): Boolean {
    if (resolutionNeedsClarification) return false
    return when (this) {
        ClarificationRequirement.GENERAL_REQUEST -> true
        ClarificationRequirement.DISH_REFERENCE ->
            route.action == FunctionGemmaRouteAction.EXPLAIN_DISH ||
                route.action == FunctionGemmaRouteAction.COMPARE_DISHES ||
                route.action == FunctionGemmaRouteAction.SUGGEST_PAIRING
        ClarificationRequirement.ALLERGEN_PURPOSE ->
            route.action == FunctionGemmaRouteAction.EXPLAIN_DISH
        ClarificationRequirement.NAMED_HEALTH_CONSTRAINT,
        ClarificationRequirement.SUPPORTED_RESTRICTION,
        ClarificationRequirement.BUDGET_AMOUNT,
        ClarificationRequirement.CATALOG_STAFF_REVIEW,
        -> false
    }
}

private fun DiningIntentSummary?.authoritativeHardConstraints(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        allergens.sorted().forEach { allergen ->
            add(
                "allergen:$allergen:" +
                    (avoidanceReasons[allergen] ?: AvoidanceReason.UNSPECIFIED).name,
            )
        }
        diets.sorted().forEach { diet -> add("diet:$diet") }
        maximumPriceMinor?.let { maximum ->
            add("maximum_price_minor:$maximum")
            add(
                "budget_scope:" +
                    when (budgetScope) {
                        DiningBudgetScope.WHOLE_ORDER -> "whole_order"
                        DiningBudgetScope.PER_DISH -> "per_dish"
                        null -> error("Budget scope is required with a budget amount")
                    },
            )
        }
    }
}

private fun deterministicOnlyDishInsightService(): GroundedQwenDishInsightService =
    GroundedQwenDishInsightService(
        generator = QwenDishInsightSelectionGenerator {
            throw ModelGenerationException.NotInstalled("No test insight model configured")
        },
    )

private fun strongerAvoidanceReason(
    first: AvoidanceReason?,
    second: AvoidanceReason?,
): AvoidanceReason {
    val left = first ?: AvoidanceReason.UNSPECIFIED
    val right = second ?: AvoidanceReason.UNSPECIFIED
    return if (left.safetyPriority >= right.safetyPriority) left else right
}

private val AvoidanceReason.safetyPriority: Int
    get() = when (this) {
        AvoidanceReason.ALLERGY -> 7
        AvoidanceReason.CELIAC -> 6
        AvoidanceReason.INTOLERANCE -> 5
        AvoidanceReason.RELIGIOUS -> 4
        AvoidanceReason.ETHICAL -> 3
        AvoidanceReason.DISLIKE -> 2
        AvoidanceReason.UNSPECIFIED -> 1
    }

private fun List<ConversationTurn>.appendAssistantTurnOnce(
    message: String,
): List<ConversationTurn> =
    if (lastOrNull()?.role == ConversationRole.ASSISTANT &&
        lastOrNull()?.message == message
    ) {
        this
    } else {
        this + ConversationTurn(ConversationRole.ASSISTANT, message)
    }

private fun String.normalizedDishText(): String =
    lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

private fun DiningIntentSummary.fingerprint(): String = buildString {
    append("allergens=")
    append(
        allergens.sorted().joinToString(",") {
            "$it:${avoidanceReasons[it]?.name ?: "UNSPECIFIED"}"
        },
    )
    append(";diets=")
    append(diets.sorted().joinToString(","))
    append(";preferences=")
    append(preferredAttributes.sorted().joinToString(","))
    append(";price=")
    append(maximumPriceMinor ?: "none")
    append(";priceScope=")
    append(budgetScope?.name ?: "none")
}

private fun DiningIntentSummary.toHandoffDiningNeeds(): List<HandoffDiningNeed> = buildList {
    allergens.sorted().forEach { allergen ->
        val reason = avoidanceReasons[allergen]?.name
            ?.lowercase()
            ?.replace('_', ' ')
            ?: "avoidance"
        add(
            HandoffDiningNeed(
                kind = HandoffDiningNeedKind.ALLERGEN,
                canonicalId = allergen,
                guestLabel = "Avoid $allergen ($reason)",
                safetyCritical = true,
            ),
        )
    }
    diets.sorted().forEach { diet ->
        add(
            HandoffDiningNeed(
                kind = HandoffDiningNeedKind.DIET,
                canonicalId = diet,
                guestLabel = diet.replaceFirstChar(Char::uppercase),
                safetyCritical = false,
            ),
        )
    }
    preferredAttributes.sorted().forEach { attribute ->
        add(
            HandoffDiningNeed(
                kind = HandoffDiningNeedKind.PREFERENCE,
                canonicalId = attribute,
                guestLabel = attribute.replaceFirstChar(Char::uppercase),
                safetyCritical = false,
            ),
        )
    }
    maximumPriceMinor?.let { maximum ->
        val scope = when (budgetScope) {
            DiningBudgetScope.WHOLE_ORDER -> HandoffBudgetScope.WHOLE_SHORTLIST
            DiningBudgetScope.PER_DISH -> HandoffBudgetScope.PER_DISH
            null -> error("Budget scope is required with a budget amount")
        }
        add(
            HandoffDiningNeed(
                kind = HandoffDiningNeedKind.PRICE_LIMIT,
                canonicalId = maximum.toString(),
                guestLabel = when (scope) {
                    HandoffBudgetScope.PER_DISH -> "Per-dish budget ${formatPeso(maximum)}"
                    HandoffBudgetScope.WHOLE_SHORTLIST ->
                        "Whole-order budget ${formatPeso(maximum)}"
                },
                safetyCritical = false,
                budgetScope = scope,
            ),
        )
    }
}

private fun StaffAcknowledgment.toHandoffStaffReview(): HandoffStaffReview =
    HandoffStaffReview(
        staffId = staffId,
        staffDisplayName = staffDisplayName,
        acknowledgedAt = acknowledgedAt,
        cartFingerprint = cartFingerprint,
    )

private val DiningIntentSummary.isNeutralBrowseIntent: Boolean
    get() = allergens.isEmpty() &&
        diets.isEmpty() &&
        preferredAttributes.isEmpty() &&
        maximumPriceMinor == null

private fun DiningIntentSummary.wholeOrderBudgetLimit(): Long? =
    maximumPriceMinor?.takeIf { budgetScope == DiningBudgetScope.WHOLE_ORDER }

private fun FeedbackUiState.toGuestFeedback() = GuestFeedback(
    foodRating = ratings[FeedbackDimension.FOOD],
    serviceRating = ratings[FeedbackDimension.SERVICE],
    waitTimeRating = ratings[FeedbackDimension.WAIT_TIME],
    orderAccuracyRating = ratings[FeedbackDimension.ORDER_ACCURACY],
    dietaryConfidenceRating = ratings[FeedbackDimension.DIETARY_CONFIDENCE],
    selectedTagIds = selectedTagIds,
)
