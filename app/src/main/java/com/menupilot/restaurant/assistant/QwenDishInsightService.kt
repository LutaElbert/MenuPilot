package com.menupilot.restaurant.assistant

import com.menupilot.assistant.contract.GroundedDishInsightAngle
import com.menupilot.assistant.contract.GroundedDishInsightEvidence
import com.menupilot.assistant.contract.QwenDishInsightContract
import com.menupilot.assistant.contract.QwenDishInsightModelInput
import com.menupilot.assistant.contract.QwenDishInsightParseResult
import com.menupilot.assistant.contract.QwenDishInsightSelection
import com.menupilot.assistant.contract.QwenDishInsightSelectionParser
import com.menupilot.restaurant.data.MenuDish
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Minimal restaurant-owned facts admitted to the optional insight model.
 *
 * Allergy facts, dietary tags, ingredients, preparation notes, price, availability, sales, and
 * recommendation rank are intentionally absent.
 */
data class GroundedDishInsightFacts(
    val dishToken: String,
    val dishName: String,
    val categoryLabel: String,
    val profileTags: List<String>,
)

fun MenuDish.toGroundedDishInsightFacts(): GroundedDishInsightFacts =
    GroundedDishInsightFacts(
        dishToken = "${id.value}:${variantId.value}",
        dishName = name,
        categoryLabel = category.label,
        profileTags = flavorTags,
    )

fun interface QwenDishInsightSelectionGenerator {
    suspend fun generate(input: QwenDishInsightModelInput): String
}

enum class DishInsightSource {
    QWEN_GROUNDED_SELECTION,
    DETERMINISTIC_GROUNDED,
}

enum class DishInsightFallbackReason {
    INPUT_REJECTED,
    NO_USABLE_EVIDENCE,
    MODEL_NOT_INSTALLED,
    MODEL_VERIFICATION_FAILED,
    MODEL_TIMEOUT,
    MODEL_RUNTIME_FAILED,
    MODEL_OUTPUT_REJECTED,
}

data class GroundedDishInsight(
    val text: String,
    val source: DishInsightSource,
    val evidenceIds: List<String>,
    val fallbackReason: DishInsightFallbackReason? = null,
)

class DishInsightModelTimeoutException(
    cause: Throwable? = null,
) : Exception("The optional insight model exceeded its latency budget.", cause)

/**
 * Uses Qwen only to select allowlisted evidence, then renders app-owned wording.
 *
 * Missing models, contention, timeouts, native failures, and rejected output trigger exactly one
 * immediate deterministic fallback. There is no retry and raw model text is never returned.
 */
class GroundedQwenDishInsightService(
    private val generator: QwenDishInsightSelectionGenerator,
    private val modelBudgetMillis: Long = DEFAULT_MODEL_BUDGET_MILLIS,
) {
    private val parser = QwenDishInsightSelectionParser()

    init {
        require(modelBudgetMillis > 0L) { "Model budget must be positive." }
    }

    /**
     * Returns app-owned copy synchronously so UI never has to wait for model initialization.
     *
     * A caller may then launch [insight] in the background and replace this copy only when the
     * returned source is [DishInsightSource.QWEN_GROUNDED_SELECTION].
     */
    fun immediateInsight(facts: GroundedDishInsightFacts): GroundedDishInsight =
        deterministicFallback(prepare(facts), reason = null)

    suspend fun insight(facts: GroundedDishInsightFacts): GroundedDishInsight {
        val prepared = prepare(facts)
        val modelInput = prepared.modelInput
            ?: return deterministicFallback(
                prepared = prepared,
                reason = if (prepared.evidence.isEmpty()) {
                    DishInsightFallbackReason.NO_USABLE_EVIDENCE
                } else {
                    DishInsightFallbackReason.INPUT_REJECTED
                },
            )

        return try {
            val rawOutput = withTimeout(modelBudgetMillis) {
                generator.generate(modelInput)
            }
            when (val parsed = parser.parse(rawOutput, modelInput)) {
                is QwenDishInsightParseResult.Accepted ->
                    renderAccepted(prepared, parsed.selection)
                is QwenDishInsightParseResult.Rejected ->
                    deterministicFallback(
                        prepared,
                        DishInsightFallbackReason.MODEL_OUTPUT_REJECTED,
                    )
            }
        } catch (_: ModelGenerationException.NotInstalled) {
            deterministicFallback(prepared, DishInsightFallbackReason.MODEL_NOT_INSTALLED)
        } catch (_: ModelGenerationException.VerificationFailed) {
            deterministicFallback(
                prepared,
                DishInsightFallbackReason.MODEL_VERIFICATION_FAILED,
            )
        } catch (_: ModelGenerationException.OutputRejected) {
            deterministicFallback(prepared, DishInsightFallbackReason.MODEL_OUTPUT_REJECTED)
        } catch (_: DishInsightModelTimeoutException) {
            deterministicFallback(prepared, DishInsightFallbackReason.MODEL_TIMEOUT)
        } catch (_: TimeoutCancellationException) {
            deterministicFallback(prepared, DishInsightFallbackReason.MODEL_TIMEOUT)
        } catch (error: CancellationException) {
            throw error
        } catch (_: ModelGenerationException.RuntimeFailed) {
            deterministicFallback(prepared, DishInsightFallbackReason.MODEL_RUNTIME_FAILED)
        } catch (_: Exception) {
            deterministicFallback(prepared, DishInsightFallbackReason.MODEL_RUNTIME_FAILED)
        }
    }

    private fun renderAccepted(
        prepared: PreparedInsight,
        selection: QwenDishInsightSelection,
    ): GroundedDishInsight {
        val evidenceById = prepared.evidence.associateBy(GroundedDishInsightEvidence::id)
        val selected = selection.evidenceIds.mapNotNull(evidenceById::get)
        if (
            selected.size != selection.evidenceIds.size ||
            selected.any { it.angle != selection.angle }
        ) {
            return deterministicFallback(
                prepared,
                DishInsightFallbackReason.MODEL_OUTPUT_REJECTED,
            )
        }
        return GroundedDishInsight(
            text = render(prepared.safeDishName, selection.angle, selected),
            source = DishInsightSource.QWEN_GROUNDED_SELECTION,
            evidenceIds = selection.evidenceIds,
        )
    }

    private fun deterministicFallback(
        prepared: PreparedInsight,
        reason: DishInsightFallbackReason?,
    ): GroundedDishInsight {
        val selected = prepared.evidence
            .filter { it.angle == GroundedDishInsightAngle.PROFILE }
            .take(QwenDishInsightContract.MAX_SELECTED_EVIDENCE_COUNT)
            .ifEmpty {
                prepared.evidence
                    .filter { it.angle == GroundedDishInsightAngle.CATEGORY }
                    .take(1)
            }
        return if (selected.isEmpty()) {
            GroundedDishInsight(
                text = GENERIC_FALLBACK,
                source = DishInsightSource.DETERMINISTIC_GROUNDED,
                evidenceIds = emptyList(),
                fallbackReason = reason,
            )
        } else {
            GroundedDishInsight(
                text = render(prepared.safeDishName, selected.first().angle, selected),
                source = DishInsightSource.DETERMINISTIC_GROUNDED,
                evidenceIds = selected.map(GroundedDishInsightEvidence::id),
                fallbackReason = reason,
            )
        }
    }

    private fun render(
        dishName: String?,
        angle: GroundedDishInsightAngle,
        selected: List<GroundedDishInsightEvidence>,
    ): String {
        val subject = dishName ?: "This dish"
        val values = selected.map { it.value.lowercase(Locale.ROOT) }
        val candidate = when (angle) {
            GroundedDishInsightAngle.PROFILE -> when (values.size) {
                1 -> "The restaurant describes $subject as ${values.first()}."
                else ->
                    "The restaurant describes $subject as " +
                        "${values.first()} and ${values[1]}."
            }
            GroundedDishInsightAngle.CATEGORY ->
                "$subject appears in the menu's ${values.first()} category."
        }
        if (candidate.length <= MAX_RENDERED_INSIGHT_CHARS) return candidate

        val shorter = when (angle) {
            GroundedDishInsightAngle.PROFILE ->
                "$subject is described as ${values.first()}."
            GroundedDishInsightAngle.CATEGORY ->
                "$subject is in ${values.first()}."
        }
        return shorter.take(MAX_RENDERED_INSIGHT_CHARS)
    }

    private fun prepare(facts: GroundedDishInsightFacts): PreparedInsight {
        val safeName = facts.dishName.normalizedFact(
            QwenDishInsightContract.MAX_DISH_NAME_CHARS,
            prohibitInsightDimensions = false,
        )
        val safeProfiles = facts.profileTags
            .mapNotNull {
                it.normalizedFact(
                    QwenDishInsightContract.MAX_EVIDENCE_VALUE_CHARS,
                    prohibitInsightDimensions = true,
                )
            }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(MAX_PROFILE_EVIDENCE)
        val safeCategory = facts.categoryLabel.normalizedFact(
            QwenDishInsightContract.MAX_EVIDENCE_VALUE_CHARS,
            prohibitInsightDimensions = true,
        )
        val evidence = buildList {
            safeProfiles.forEachIndexed { index, value ->
                add(
                    GroundedDishInsightEvidence(
                        id = "profile.$index",
                        angle = GroundedDishInsightAngle.PROFILE,
                        value = value,
                    ),
                )
            }
            safeCategory?.let { value ->
                add(
                    GroundedDishInsightEvidence(
                        id = "category.0",
                        angle = GroundedDishInsightAngle.CATEGORY,
                        value = value,
                    ),
                )
            }
        }
        val candidate = if (safeName != null && evidence.isNotEmpty()) {
            QwenDishInsightModelInput(
                dishToken = facts.dishToken,
                dishName = safeName,
                evidence = evidence,
            )
        } else {
            null
        }
        return PreparedInsight(
            safeDishName = safeName,
            evidence = evidence,
            modelInput = candidate?.takeIf {
                QwenDishInsightContract.validateInput(it) == null
            },
        )
    }

    private fun String.normalizedFact(
        maximumChars: Int,
        prohibitInsightDimensions: Boolean,
    ): String? {
        if (any(Char::isISOControl)) return null
        val normalized = trim().replace(WHITESPACE, " ")
        if (normalized.isBlank() || normalized.length > maximumChars) return null
        if (
            prohibitInsightDimensions &&
            !QwenDishInsightContract.isAllowedEvidenceValue(normalized)
        ) {
            return null
        }
        return normalized
    }

    private data class PreparedInsight(
        val safeDishName: String?,
        val evidence: List<GroundedDishInsightEvidence>,
        val modelInput: QwenDishInsightModelInput?,
    )

    private companion object {
        const val DEFAULT_MODEL_BUDGET_MILLIS = 3_000L
        const val MAX_PROFILE_EVIDENCE = 5
        const val MAX_RENDERED_INSIGHT_CHARS = 180
        const val GENERIC_FALLBACK =
            "Ask your waiter for the restaurant's description of this dish."
        val WHITESPACE = Regex("""\s+""")
    }
}
