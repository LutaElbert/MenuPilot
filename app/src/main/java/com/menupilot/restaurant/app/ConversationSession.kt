package com.menupilot.restaurant.app

import androidx.lifecycle.SavedStateHandle
import com.menupilot.domain.AvoidanceReason
import com.menupilot.restaurant.assistant.AssistantFallbackReason
import com.menupilot.restaurant.assistant.AssistantSource
import com.menupilot.restaurant.assistant.ClarificationRequirement
import com.menupilot.restaurant.assistant.DiningBudgetScope
import com.menupilot.restaurant.assistant.DiningIntentSummary
import com.menupilot.restaurant.assistant.rebuiltDraft

/**
 * One guest-visible turn in the current dining session.
 *
 * This transport-neutral shape can be populated from the local ViewModel today and from an ADK
 * session ledger later without coupling the UI to either persistence mechanism.
 */
data class ConversationTurn(
    val role: ConversationRole,
    val message: String,
)

enum class ConversationRole {
    ASSISTANT,
    GUEST,
}

/**
 * Context safe to pass to a conversational router.
 *
 * [recentTurns] is deliberately bounded. Safety-critical constraints are carried separately and
 * completely, so trimming conversational prose can never remove an allergen, diet, or price limit.
 */
data class BoundedConversationContext(
    val sessionId: String,
    val recentTurns: List<ConversationTurn>,
    val authoritativeHardConstraints: List<String>,
    val confirmedPreferences: List<String>,
    val omittedTurnCount: Int,
    val pendingClarification: ClarificationRequirement? = null,
    val focusedDishName: String? = null,
)

internal data class RestoredConversationSession(
    val sessionId: String,
    val query: String,
    val turns: List<ConversationTurn>,
    val intent: DiningIntentSummary?,
    val assistantMessage: String,
    val assistantSource: AssistantSource?,
    val assistantFallbackReason: AssistantFallbackReason?,
    val needsClarification: Boolean,
    val pendingClarification: ClarificationRequirement?,
    val focusedDishName: String?,
)

/**
 * Lightweight process-recreation backup only. An ADK/Room session ledger may own durable event
 * history later; this store intentionally persists no menu, cart, handoff, or generated catalog.
 */
internal class ConversationSessionStore(
    private val savedStateHandle: SavedStateHandle,
) {
    fun restore(): RestoredConversationSession? {
        if (savedStateHandle.get<Boolean>(KEY_ACTIVE) != true) return null
        val sessionId = savedStateHandle.get<String>(KEY_SESSION_ID)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val roles = savedStateHandle.get<ArrayList<String>>(KEY_TURN_ROLES).orEmpty()
        val messages = savedStateHandle.get<ArrayList<String>>(KEY_TURN_MESSAGES).orEmpty()
        val turns = roles.zip(messages)
            .take(MAX_SESSION_TURNS)
            .mapNotNull { (roleName, rawMessage) ->
                val role = enumValues<ConversationRole>()
                    .firstOrNull { it.name == roleName }
                    ?: return@mapNotNull null
                rawMessage
                    .trim()
                    .take(MAX_TURN_CHARS)
                    .takeIf(String::isNotBlank)
                    ?.let { ConversationTurn(role, it) }
            }
        val intent = restoreIntent()
        val needsClarification = savedStateHandle[KEY_NEEDS_CLARIFICATION] ?: false
        val pendingClarification = savedStateHandle.get<String>(KEY_CLARIFICATION_REQUIREMENT)
            ?.let { name ->
                enumValues<ClarificationRequirement>().firstOrNull { it.name == name }
            }
            ?: ClarificationRequirement.GENERAL_REQUEST.takeIf { needsClarification }
        return RestoredConversationSession(
            sessionId = sessionId,
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty().take(MAX_QUERY_CHARS),
            turns = turns,
            intent = intent,
            assistantMessage = savedStateHandle.get<String>(KEY_ASSISTANT_MESSAGE)
                .orEmpty()
                .take(MAX_TURN_CHARS)
                .ifBlank { DEFAULT_ASSISTANT_MESSAGE },
            assistantSource = savedStateHandle.get<String>(KEY_ASSISTANT_SOURCE)
                ?.let { name -> enumValues<AssistantSource>().firstOrNull { it.name == name } },
            assistantFallbackReason = savedStateHandle.get<String>(KEY_FALLBACK_REASON)
                ?.let { name ->
                    enumValues<AssistantFallbackReason>().firstOrNull { it.name == name }
                },
            needsClarification = needsClarification,
            pendingClarification = pendingClarification,
            focusedDishName = savedStateHandle.get<String>(KEY_FOCUSED_DISH)
                ?.trim()
                ?.take(MAX_FOCUSED_DISH_CHARS)
                ?.takeIf(String::isNotBlank),
        )
    }

    fun save(state: MenuPilotUiState) {
        savedStateHandle[KEY_ACTIVE] = true
        savedStateHandle[KEY_SESSION_ID] = state.sessionId
        savedStateHandle[KEY_QUERY] = state.query.take(MAX_QUERY_CHARS)
        savedStateHandle[KEY_TURN_ROLES] = ArrayList(
            state.conversationTurns.map { it.role.name },
        )
        savedStateHandle[KEY_TURN_MESSAGES] = ArrayList(
            state.conversationTurns.map { it.message.take(MAX_TURN_CHARS) },
        )
        savedStateHandle[KEY_ASSISTANT_MESSAGE] =
            state.assistantMessage.take(MAX_TURN_CHARS)
        savedStateHandle[KEY_ASSISTANT_SOURCE] = state.assistantSource?.name
        savedStateHandle[KEY_FALLBACK_REASON] = state.assistantFallbackReason?.name
        savedStateHandle[KEY_NEEDS_CLARIFICATION] = state.conversationNeedsClarification
        savedStateHandle[KEY_CLARIFICATION_REQUIREMENT] =
            state.pendingClarification?.name
        savedStateHandle[KEY_FOCUSED_DISH] = state.focusedDishName
        saveIntent(state.intent)
    }

    private fun saveIntent(intent: DiningIntentSummary?) {
        savedStateHandle[KEY_HAS_INTENT] = intent != null
        if (intent == null) {
            listOf(
                KEY_SOURCE_QUERY,
                KEY_DETECTED_LANGUAGE,
                KEY_ALLERGENS,
                KEY_AVOIDANCE_REASONS,
                KEY_DIETS,
                KEY_PREFERENCES,
                KEY_MAXIMUM_PRICE,
                KEY_BUDGET_SCOPE,
            ).forEach { key -> savedStateHandle.remove<Any>(key) }
            return
        }
        savedStateHandle[KEY_SOURCE_QUERY] =
            intent.sourceQuery.take(MAX_SESSION_SOURCE_CHARS)
        savedStateHandle[KEY_DETECTED_LANGUAGE] =
            intent.detectedLanguage.take(MAX_LANGUAGE_CHARS)
        savedStateHandle[KEY_ALLERGENS] = ArrayList(intent.allergens)
        savedStateHandle[KEY_AVOIDANCE_REASONS] = ArrayList(
            intent.avoidanceReasons.map { (id, reason) -> "$id=$reason" },
        )
        savedStateHandle[KEY_DIETS] = ArrayList(intent.diets)
        savedStateHandle[KEY_PREFERENCES] = ArrayList(intent.preferredAttributes)
        intent.maximumPriceMinor?.let {
            savedStateHandle[KEY_MAXIMUM_PRICE] = it
        } ?: savedStateHandle.remove<Long>(KEY_MAXIMUM_PRICE)
        savedStateHandle[KEY_BUDGET_SCOPE] = intent.budgetScope?.name
    }

    private fun restoreIntent(): DiningIntentSummary? {
        if (savedStateHandle.get<Boolean>(KEY_HAS_INTENT) != true) return null
        val allergens = savedStateHandle.get<ArrayList<String>>(KEY_ALLERGENS)
            .orEmpty()
            .filterCanonicalValues()
        val reasons = savedStateHandle.get<ArrayList<String>>(KEY_AVOIDANCE_REASONS)
            .orEmpty()
            .mapNotNull { encoded ->
                val id = encoded.substringBefore('=').takeIf { it in allergens }
                    ?: return@mapNotNull null
                val reasonName = encoded.substringAfter('=', missingDelimiterValue = "")
                val reason = enumValues<AvoidanceReason>()
                    .firstOrNull { it.name == reasonName }
                    ?: return@mapNotNull null
                id to reason
            }
            .toMap()
        val maximumPriceMinor = savedStateHandle.get<Long>(KEY_MAXIMUM_PRICE)
            ?.takeIf { it > 0L }
        val budgetScope = maximumPriceMinor?.let {
            savedStateHandle.get<String>(KEY_BUDGET_SCOPE)
                ?.let { name ->
                    enumValues<DiningBudgetScope>().firstOrNull { it.name == name }
                }
                ?: DiningBudgetScope.PER_DISH
        }
        val restored = DiningIntentSummary(
            sourceQuery = savedStateHandle.get<String>(KEY_SOURCE_QUERY)
                .orEmpty()
                .take(MAX_SESSION_SOURCE_CHARS),
            detectedLanguage = savedStateHandle.get<String>(KEY_DETECTED_LANGUAGE)
                .orEmpty()
                .take(MAX_LANGUAGE_CHARS)
                .ifBlank { "Restored session" },
            allergens = allergens,
            avoidanceReasons = reasons,
            diets = savedStateHandle.get<ArrayList<String>>(KEY_DIETS)
                .orEmpty()
                .filterCanonicalValues(),
            preferredAttributes = savedStateHandle.get<ArrayList<String>>(KEY_PREFERENCES)
                .orEmpty()
                .filterCanonicalValues(),
            maximumPriceMinor = maximumPriceMinor,
            budgetScope = budgetScope,
            intentDraft = com.menupilot.restaurant.assistant.draft(emptyList()),
        )
        return restored.copy(intentDraft = restored.rebuiltDraft())
    }

    private fun List<String>.filterCanonicalValues(): List<String> =
        asSequence()
            .map(String::trim)
            .filter { it.matches(CANONICAL_VALUE) }
            .distinct()
            .take(MAX_CONSTRAINT_VALUES)
            .toList()

    private companion object {
        const val KEY_ACTIVE = "conversation.active"
        const val KEY_SESSION_ID = "conversation.session_id"
        const val KEY_QUERY = "conversation.query"
        const val KEY_TURN_ROLES = "conversation.turn_roles"
        const val KEY_TURN_MESSAGES = "conversation.turn_messages"
        const val KEY_HAS_INTENT = "conversation.has_intent"
        const val KEY_SOURCE_QUERY = "conversation.intent.source_query"
        const val KEY_DETECTED_LANGUAGE = "conversation.intent.language"
        const val KEY_ALLERGENS = "conversation.intent.allergens"
        const val KEY_AVOIDANCE_REASONS = "conversation.intent.avoidance_reasons"
        const val KEY_DIETS = "conversation.intent.diets"
        const val KEY_PREFERENCES = "conversation.intent.preferences"
        const val KEY_MAXIMUM_PRICE = "conversation.intent.maximum_price"
        const val KEY_BUDGET_SCOPE = "conversation.intent.budget_scope"
        const val KEY_ASSISTANT_MESSAGE = "conversation.assistant_message"
        const val KEY_ASSISTANT_SOURCE = "conversation.assistant_source"
        const val KEY_FALLBACK_REASON = "conversation.fallback_reason"
        const val KEY_NEEDS_CLARIFICATION = "conversation.needs_clarification"
        const val KEY_CLARIFICATION_REQUIREMENT =
            "conversation.clarification_requirement"
        const val KEY_FOCUSED_DISH = "conversation.focused_dish"
        const val MAX_LANGUAGE_CHARS = 80
        const val MAX_CONSTRAINT_VALUES = 32
        const val MAX_FOCUSED_DISH_CHARS = 160
        val CANONICAL_VALUE = Regex("""[a-z][a-z0-9_]{0,63}""")
    }
}

internal const val MAX_QUERY_CHARS = 500
internal const val MAX_TURN_CHARS = 1_000
internal const val MAX_SESSION_TURNS = 96
internal const val MAX_GUEST_TURNS = 24
internal const val MAX_SESSION_SOURCE_CHARS = 24_000
internal const val DEFAULT_ASSISTANT_MESSAGE =
    "Tell me what you need, and I’ll shape the menu around you."
internal const val DEFAULT_CONVERSATION_GREETING =
    "Welcome to MenuPilot! What are you in the mood for today?"
