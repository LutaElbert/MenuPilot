package com.menupilot.restaurant.assistant

import com.menupilot.domain.AvoidanceReason
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicIntentAssistant @Inject constructor() : IntentAssistant {

    override fun interpret(query: String): AssistantInterpretation {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            return AssistantInterpretation.NeedsClarification(
                "Tell me what you feel like eating or what you need to avoid.",
            )
        }

        val mentionedAllergens =
            KnownAllergens.filter { terms ->
                terms.aliases.any { normalized.containsTerm(it) }
            }
        val mentionsHealthConstraint = normalized.hasUnnegatedHealthConstraint()
        val allergens = buildSet {
            mentionedAllergens
                .filterNot { terms ->
                    normalized.explicitlyDisclaimsAvoidanceOf(terms.aliases)
                }
                .filter { terms ->
                    normalized.expressesAvoidanceOf(terms.aliases)
                }
                .forEach { add(it.canonicalId) }
            if (
                normalized.hasUnnegatedTerm("coeliac") ||
                normalized.hasUnnegatedTerm("celiac")
            ) {
                add("gluten")
            }
            if (
                mentionsHealthConstraint &&
                mentionedAllergens.size == 1 &&
                !normalized.explicitlyDisclaimsAvoidanceOf(
                    mentionedAllergens.single().aliases,
                )
            ) {
                add(mentionedAllergens.single().canonicalId)
            }
        }.toList()

        if (mentionsHealthConstraint && allergens.isEmpty()) {
            return AssistantInterpretation.NeedsClarification(
                "Which ingredient or allergen should the kitchen avoid?",
                requirement = ClarificationRequirement.NAMED_HEALTH_CONSTRAINT,
            )
        }
        if (
            mentionedAllergens.isNotEmpty() &&
            allergens.isEmpty() &&
            (
                normalized.contains('?') ||
                    containsAny(
                        normalized,
                        "contain",
                        "contains",
                        "ingredient",
                        "ingredients",
                        "sangkap",
                        "made with",
                        "do you have",
                        "does it have",
                        "is there",
                        "mayroon bang",
                        "meron bang",
                        "may ",
                        "naa bay",
                        "aduna bay",
                    )
                )
        ) {
            return AssistantInterpretation.NeedsClarification(
                "You mentioned ${mentionedAllergens.joinToString { it.canonicalId }}. " +
                    "Is this an allergy or intolerance, an ingredient to avoid, or a menu question?",
                requirement = ClarificationRequirement.ALLERGEN_PURPOSE,
            )
        }

        val unsupportedRestriction = UnsupportedRestrictions.firstOrNull { restriction ->
            restriction.aliases.any { normalized.hasUnnegatedTerm(it) }
        }
        if (unsupportedRestriction != null) {
            return AssistantInterpretation.NeedsClarification(
                "This menu does not yet have verified facts for " +
                    "${unsupportedRestriction.displayName}. Please ask the waiter to confirm " +
                    "before choosing.",
                requirement = ClarificationRequirement.SUPPORTED_RESTRICTION,
            )
        }

        val avoidanceReasons = allergens.associateWith { canonicalId ->
            val aliases = KnownAllergens
                .first { it.canonicalId == canonicalId }
                .aliases
            normalized.avoidanceReasonFor(canonicalId, aliases)
        }

        val diets = buildList {
            if (normalized.expressesVegetarianIntent()) {
                add("vegetarian")
            }
        }

        val preferences = buildList {
            if (MildPreferenceCues.any { it.containsMatchIn(normalized) }) {
                add("mild")
            } else if (
                listOf("spicy", "maanghang", "halang")
                    .any { normalized.hasUnnegatedTerm(it) }
            ) {
                add("spicy")
            }
            if (
                listOf(
                    "light",
                    "not heavy",
                    "hindi mabigat",
                    "di mabigat",
                    "dili bug-at",
                    "magaan",
                    "gaan",
                ).any { normalized.hasUnnegatedTerm(it) }
            ) {
                add("light")
            }
            if (
                listOf("quick", "fast", "mabilis", "madali", "dali", "paspas")
                    .any { normalized.hasUnnegatedTerm(it) }
            ) {
                add("quick")
            }
            if (
                listOf(
                    "best seller",
                    "best-seller",
                    "bestseller",
                    "best selling",
                    "best-selling",
                    "popular",
                    "trending",
                    "most ordered",
                    "pinakasikat",
                    "sikat",
                    "patok",
                ).any { normalized.hasUnnegatedTerm(it) }
            ) {
                add("popular")
            }
        }.distinct()

        val maximumPriceMinor = parseBudgetMinor(normalized)
        if (normalized.hasBudgetLanguage() && maximumPriceMinor == null) {
            return AssistantInterpretation.NeedsClarification(
                "What maximum price in pesos should I use?",
                requirement = ClarificationRequirement.BUDGET_AMOUNT,
            )
        }
        val proposed = buildList {
            allergens.forEach {
                add(
                    proposedConstraint(
                        type = "AVOID",
                        targetType = "ALLERGEN",
                        canonicalId = it,
                        rawText = query,
                        reasonHint = avoidanceReasons.getValue(it).name,
                        modality = "MUST",
                    ),
                )
            }
            diets.forEach {
                add(
                    proposedConstraint(
                        type = "REQUIRE",
                        targetType = "DIET",
                        canonicalId = it,
                        rawText = query,
                        modality = "MUST",
                    ),
                )
            }
            preferences.forEach {
                add(
                    proposedConstraint(
                        type = "PREFER",
                        targetType = "ATTRIBUTE",
                        canonicalId = it,
                        rawText = query,
                        modality = "SHOULD",
                    ),
                )
            }
            maximumPriceMinor?.let {
                add(
                    proposedConstraint(
                        type = "LIMIT",
                        targetType = "PRICE_MINOR",
                        canonicalId = it.toString(),
                        rawText = query,
                        modality = "MUST",
                    ),
                )
            }
        }

        val summary = DiningIntentSummary(
            sourceQuery = query.trim(),
            detectedLanguage = detectLanguage(normalized),
            allergens = allergens,
            avoidanceReasons = avoidanceReasons,
            diets = diets,
            preferredAttributes = preferences.ifEmpty {
                if (proposed.isEmpty()) listOf("popular") else emptyList()
            },
            maximumPriceMinor = maximumPriceMinor,
            budgetScope = maximumPriceMinor?.let {
                normalized.explicitBudgetScope()
            },
            intentDraft = draft(proposed),
        )
        val clarification = if (allergens.isNotEmpty()) {
            "I’ll avoid listed ${allergens.joinToString()} and reported cross-contact risks. " +
                "The kitchen must still confirm before you choose."
        } else {
            "Here’s what I understood. You can edit it before I shape the menu."
        }
        return AssistantInterpretation.Ready(summary, clarification)
    }

    private fun parseBudgetMinor(query: String): Long? {
        if (!query.hasBudgetLanguage()) return null
        val amountText = CurrencyAmount.find(query)
            ?.groupValues
            ?.getOrNull(1)
            ?: BudgetAmount.find(query)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        val amountMinor = runCatching {
            java.math.BigDecimal(amountText.replace(",", ""))
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
        return amountMinor?.takeIf { it in MinimumPriceMinor..MaximumPriceMinor }
    }

    private fun String.hasBudgetLanguage(): Boolean =
        BudgetCue.containsMatchIn(this) || CurrencyCue.containsMatchIn(this)

    private fun String.explicitBudgetScope(): DiningBudgetScope =
        if (WholeOrderBudgetCue.containsMatchIn(this)) {
            DiningBudgetScope.WHOLE_ORDER
        } else {
            DiningBudgetScope.PER_DISH
        }

    private fun String.expressesVegetarianIntent(): Boolean {
        val meatFreeRequest = MeatFreeCues.any { it.containsMatchIn(this) }
        val namedDiet = listOf("vegetarian", "veggie").any { hasUnnegatedTerm(it) }
        return meatFreeRequest || namedDiet
    }

    private fun detectLanguage(query: String): String {
        val cebuano = containsAny(
            query,
            "unsa",
            "walay",
            "halang",
            "dili",
            "ganahan",
            "hangtod",
            "nako",
            "mokaon",
            "makakaon",
            "magsakit",
            "pasayan",
            "paspas",
            "naa bay",
            "alerdyi",
        )
        val filipino = containsAny(
            query,
            "ako",
            "gusto",
            "walang",
            "hindi",
            " mani",
            "maanghang",
            "mabilis",
            "hanggang",
            "ayoko",
            "bawal",
            "gatas",
            "itlog",
            "hipon",
            "pinakasikat",
        )
        val english =
            query.containsTerm("i") ||
                containsAny(
                    query,
                    "i'm",
                    "allergic",
                    "want",
                    "something",
                    "under",
                    "please",
                    "food",
                    "meal",
                    "quick",
                    "spicy",
                    "light",
                    "popular",
                )
        return when {
            cebuano && english -> "Cebuano-English"
            cebuano -> "Cebuano"
            filipino && english -> "Taglish"
            filipino -> "Filipino"
            else -> "English"
        }
    }

    private fun containsAny(value: String, vararg needles: String): Boolean =
        needles.any(value::contains)

    private fun String.containsTerm(term: String): Boolean =
        Regex(
            pattern = """(?<![\p{L}\p{N}_])${Regex.escape(term)}(?![\p{L}\p{N}_])""",
        ).containsMatchIn(this)

    private fun String.hasUnnegatedHealthConstraint(): Boolean =
        HealthConstraintCue.findAll(this).any { match ->
            !isNegatedBefore(match.range.first)
        }

    private fun String.hasUnnegatedTerm(term: String): Boolean {
        val pattern = Regex(
            """(?<![\p{L}\p{N}_])${Regex.escape(term)}(?![\p{L}\p{N}_])""",
        )
        return pattern.findAll(this).any { match ->
            !isNegatedBefore(match.range.first)
        }
    }

    private fun String.isNegatedBefore(index: Int): Boolean {
        val prefix = substring(maxOf(0, index - 56), index)
        return NegationBeforeCue.containsMatchIn(prefix)
    }

    private fun String.explicitlyDisclaimsAvoidanceOf(aliases: List<String>): Boolean =
        aliases.any { alias ->
            val term = """(?<![\p{L}\p{N}_])${Regex.escape(alias)}(?![\p{L}\p{N}_])"""
            val nonContrast =
                """(?:(?!\b(?:but|pero|apan|however)\b)[^.!?;])"""
            val deniedCondition =
                """(?:\bnot|\bnever|\bno\s+longer|\bhindi|\bdi|\bdili)\s+""" +
                    """(?:[\p{L}'-]+\s+){0,3}(?:allerg\w*|alerdyi|alerhiya|""" +
                    """intoleran\w*)$nonContrast{0,36}$term"""
            val deniedSuffixCondition =
                """(?:\bnot|\bnever|\bno\s+longer|\bhindi|\bdi|\bdili)""" +
                    """$nonContrast{0,36}$term$nonContrast{0,24}""" +
                    """(?:allerg\w*|alerdyi|alerhiya|intoleran\w*)"""
            val deniedSymptomBeforeIngredient =
                """(?:\bdon't|\bdont|\bdo\s+not|\bdoesn't|\bdoesnt|""" +
                    """\bdoes\s+not|\bnever)$nonContrast{0,28}$HealthSymptom""" +
                    """$nonContrast{0,28}$term"""
            val deniedSymptomAfterIngredient =
                """$term$nonContrast{0,28}(?:\bdoesn't|\bdoesnt|\bdoes\s+not|""" +
                    """\bnever)$nonContrast{0,28}$HealthSymptom"""
            val hasNoCondition =
                """(?:\bno|\bwalang|\bwala\s+akong|\bwala\s+koy|\bwala\s+akoy|""" +
                    """\bwalay)$nonContrast{0,24}(?:allerg\w*|alerdyi|alerhiya|""" +
                    """intoleran\w*)$nonContrast{0,36}$term"""
            val canEat =
                """(?:\bcan\s+(?:comfortably\s+|safely\s+)?eat|""" +
                    """\b(?:pwede|puwede)\s+(?:akong|ako|ko)?\s*(?:kumain)?|""" +
                    """\b(?:makakaon|makaon|mokaon)\s+ko\s+(?:og|ug|sa)?)""" +
                    """$nonContrast{0,24}$term"""
            val ingredientIsFine =
                """$term$nonContrast{0,20}(?:is|are)\s+(?:fine|okay|ok)\b"""
            val fineBeforeIngredient =
                """(?:okay|ok|fine)\s+(?:lang\s+sa\s+akin|ra\s+nako)?""" +
                    """$nonContrast{0,24}$term"""
            Regex(deniedCondition).containsMatchIn(this) ||
                Regex(deniedSuffixCondition).containsMatchIn(this) ||
                Regex(deniedSymptomBeforeIngredient).containsMatchIn(this) ||
                Regex(deniedSymptomAfterIngredient).containsMatchIn(this) ||
                Regex(hasNoCondition).containsMatchIn(this) ||
                Regex(canEat).findAll(this).any { !isNegatedBefore(it.range.first) } ||
                Regex(ingredientIsFine).containsMatchIn(this) ||
                Regex(fineBeforeIngredient).containsMatchIn(this)
        }

    private fun String.expressesAvoidanceOf(aliases: List<String>): Boolean {
        if (explicitlyDisclaimsAvoidanceOf(aliases)) return false
        return aliases.any { alias ->
            val term = """(?<![\p{L}\p{N}_])${Regex.escape(alias)}(?![\p{L}\p{N}_])"""
            val prefix =
                """(?:allerg\w*|alerdyi|alerhiya|anaphyla\w*|intoleran\w*|""" +
                    """cannot\s+eat|can't\s+eat|cant\s+eat|do\s+not\s+eat|""" +
                    """don't\s+eat|dont\s+eat|cannot\s+have|can't\s+have|""" +
                    """cant\s+have|should\s+not\s+(?:have|eat)|""" +
                    """shouldn['’]?t\s+(?:have|eat)|must\s+not\s+(?:have|eat)|""" +
                    """mustn['’]?t\s+(?:have|eat)|unable\s+to\s+(?:eat|have)|""" +
                    """react\w*\s+to|""" +
                    """sensitive\s+to|avoid(?:ing)?|without|free\s+of|""" +
                    """bawal(?:\s+sa|\s+para\s+sa)?|iwas(?:an)?|""" +
                    """hindi(?:\s+ako)?\s+(?:pwede|puwede|pwedeng|puwedeng|""" +
                    """kumakain|makakain)""" +
                    """(?:\s+(?:kumain))?(?:\s+(?:ng|sa))?|""" +
                    """di(?:\s+ako)?\s+(?:pwede|puwede|pwedeng|puwedeng|""" +
                    """kumakain|makakain)""" +
                    """(?:\s+(?:kumain))?(?:\s+(?:ng|sa))?|""" +
                    """hindi(?:\s+ako)?\s+hiyang(?:\s+(?:sa|ang))?|""" +
                    """di(?:\s+ako)?\s+hiyang(?:\s+(?:sa|ang))?|""" +
                    """dili(?:\s+ko)?\s+hiyang(?:\s+(?:sa|og|ug))?|""" +
                    """hindi(?:\s+ako)?\s+dapat(?:\s+(?:kumain|magkaroon))?""" +
                    """(?:\s+(?:ng|sa))?|""" +
                    """dili(?:\s+(?:ko|nako))?\s+angay(?:\s+(?:mokaon|makakaon))?""" +
                    """(?:\s+(?:og|ug|sa))?|""" +
                    """dili(?:\s+ko)?\s+pwede(?:\s+(?:og|sa))?|""" +
                    """dili(?:\s+ko)?\s+(?:mokaon|makakaon)(?:\s+(?:og|ug|sa))?|""" +
                    """(?:(?:get|gets|got|develop\w*|breaks?\s+out\s+in)\s+""" +
                    """$HealthSymptom|$HealthSymptom)\s+(?:from|after(?:\s+eating)?)|""" +
                    """(?:my\s+)?throat\s+swell\w*\s+after|""" +
                    """sumasakit[^.!?;]{0,24}tiyan[^.!?;]{0,16}(?:sa|pagkatapos)|""" +
                    """magsakit[^.!?;]{0,24}tiyan[^.!?;]{0,16}(?:sa|human)|""" +
                    """likay(?:i)?|walang|walay)[^.!?;]{0,48}$term"""
            val adjacentNo = """\bno\s+(?:listed\s+)?$term"""
            val scopedListAvoidance =
                """\b(?:no|without|walang|walay)""" +
                    """(?:(?!\b(?:but|pero|apan|however)\b)[^.!?;]){0,120}""" +
                    """(?:,\s*|\b(?:and|or|at|ug|og)\s+)$term"""
            val suffix =
                """$term(?:\s*[- ]\s*free|[^.!?;]{0,24}""" +
                    """(?:allerg\w*|alerdyi|alerhiya|intoleran\w*|""" +
                    """makes?\s+me\s+sick|gives?\s+me\s+(?:hives|a\s+rash)|""" +
                    """hindi\s+(?:ako\s+)?hiyang|dili\s+(?:ko\s+)?hiyang))"""
            Regex(prefix).containsMatchIn(this) ||
                Regex(adjacentNo).containsMatchIn(this) ||
                Regex(scopedListAvoidance).containsMatchIn(this) ||
                Regex(suffix).containsMatchIn(this)
        }
    }

    private fun String.avoidanceReasonFor(
        canonicalId: String,
        aliases: List<String>,
    ): AvoidanceReason {
        if (
            canonicalId == "gluten" &&
            (hasUnnegatedTerm("coeliac") || hasUnnegatedTerm("celiac"))
        ) {
            return AvoidanceReason.CELIAC
        }
        val term = aliases.joinToString("|") { Regex.escape(it) }
        val boundedTerm = """(?<![\p{L}\p{N}_])(?:$term)(?![\p{L}\p{N}_])"""
        val allergyCue = """(?:allerg\w*|alerdyi|alerhiya|anaphyla\w*)"""
        val intoleranceCue =
            """(?:intoleran\w*|hindi\s+(?:ako\s+)?hiyang|dili\s+(?:ko\s+)?hiyang)"""
        return when {
            Regex("""$allergyCue[^.!?;]{0,48}$boundedTerm""").containsMatchIn(this) ||
                Regex("""$boundedTerm[^.!?;]{0,32}$allergyCue""").containsMatchIn(this) ->
                AvoidanceReason.ALLERGY
            Regex("""$intoleranceCue[^.!?;]{0,48}$boundedTerm""").containsMatchIn(this) ||
                Regex("""$boundedTerm[^.!?;]{0,32}$intoleranceCue""").containsMatchIn(this) ->
                AvoidanceReason.INTOLERANCE
            else -> AvoidanceReason.UNSPECIFIED
        }
    }

    private data class AllergenTerms(
        val canonicalId: String,
        val aliases: List<String>,
    )

    private data class UnsupportedRestriction(
        val displayName: String,
        val aliases: List<String>,
    )

    private companion object {
        const val HealthSymptom =
            """(?:hives?|(?:a\s+)?rash(?:es)?|swelling|swollen\s+throat|""" +
                """throat\s+swelling|stomach\s+pain|sick)"""
        val HealthConstraintCue = Regex(
            """(?<![\p{L}\p{N}_])(?:allerg(?:y|ies|ic)|alerdyi|anaphyla\w*|""" +
                """alerhiya|coeliac|celiac|intoleran\w*|cannot\s+eat|can't\s+eat|""" +
                """cant\s+eat|cannot\s+have|can't\s+have|cant\s+have|""" +
                """do\s+not\s+eat|don't\s+eat|dont\s+eat|""" +
                """should\s+not\s+(?:have|eat)|shouldn['’]?t\s+(?:have|eat)|""" +
                """must\s+not\s+(?:have|eat)|mustn['’]?t\s+(?:have|eat)|""" +
                """unable\s+to\s+(?:eat|have)|react\w*\s+to|sensitive\s+to|""" +
                """(?:(?:get|gets|got|develop\w*|breaks?\s+out\s+in)\s+""" +
                """$HealthSymptom|$HealthSymptom)\s+(?:from|after(?:\s+eating)?)|""" +
                """(?:my\s+)?throat\s+swell\w*\s+after|""" +
                """sumasakit[^.!?;]{0,24}tiyan[^.!?;]{0,16}(?:sa|pagkatapos)|""" +
                """magsakit[^.!?;]{0,24}tiyan[^.!?;]{0,16}(?:sa|human)|""" +
                """hindi(?:\s+ako)?\s+(?:pwede|puwede|pwedeng|puwedeng|""" +
                """kumakain|makakain)|""" +
                """di(?:\s+ako)?\s+(?:pwede|puwede|pwedeng|puwedeng|""" +
                """kumakain|makakain)|""" +
                """hindi(?:\s+ako)?\s+dapat(?:\s+(?:kumain|magkaroon))?|""" +
                """dili(?:\s+(?:ko|nako))?\s+angay(?:\s+(?:mokaon|makakaon))?|""" +
                """hindi(?:\s+ako)?\s+hiyang|dili(?:\s+ko)?\s+hiyang|""" +
                """dili(?:\s+ko)?\s+(?:pwede|mokaon|makakaon)(?:\s+(?:og|ug|sa))?|""" +
                """bawal)""" +
                """(?![\p{L}\p{N}_])""",
        )
        val NegationBeforeCue = Regex(
            """(?:\bnot|\bnever|\bno|\bdon't|\bdont|\bdoesn't|\bdoesnt|""" +
                """\bdidn't|\bdidnt|\bhindi|\bdi|\bdili|\bwala)""" +
                """(?:\s+[\p{L}'-]+){0,3}\s+$""",
        )
        val MildPreferenceCues = listOf(
            Regex("""(?<![\p{L}\p{N}_])mild(?![\p{L}\p{N}_])"""),
            Regex(
                """(?<![\p{L}\p{N}_])(?:not\s+|no\s+|zero\s+|non[- ]?)""" +
                    """spic(?:e|y)(?![\p{L}\p{N}_])""",
            ),
            Regex("""(?<![\p{L}\p{N}_])less\s+spicy(?![\p{L}\p{N}_])"""),
            Regex("""(?<![\p{L}\p{N}_])(?:hindi|di)(?:\s+masyadong)?\s+maanghang"""),
            Regex("""(?<![\p{L}\p{N}_])(?:huwag|ayoko\s+ng)\s+maanghang"""),
            Regex("""(?<![\p{L}\p{N}_])dili(?:\s+kaayo)?\s+halang"""),
            Regex("""(?<![\p{L}\p{N}_])ayaw\s+(?:og|ug)\s+halang"""),
        )
        val MeatFreeCues = listOf(
            Regex("""(?<![\p{L}\p{N}_])(?:no|without)\s+meat(?![\p{L}\p{N}_])"""),
            Regex("""(?<![\p{L}\p{N}_])meat[- ]free(?![\p{L}\p{N}_])"""),
            Regex("""(?<![\p{L}\p{N}_])(?:walang|walay)\s+karne(?![\p{L}\p{N}_])"""),
            Regex(
                """(?<![\p{L}\p{N}_])(?:hindi|di)\s+(?:ako\s+)?""" +
                    """(?:kumakain|makakain)\s+ng\s+karne(?![\p{L}\p{N}_])""",
            ),
            Regex(
                """(?<![\p{L}\p{N}_])dili\s+(?:ko\s+)?(?:mokaon|makakaon)""" +
                    """\s+(?:og|ug)\s+karne(?![\p{L}\p{N}_])""",
            ),
        )
        val BudgetCue = Regex(
            """(?<![\p{L}\p{N}_])(?:under|below|less\s+than|up\s+to|at\s+most|""" +
                """no\s+more\s+than|budget(?:\s+(?:ko|nako|namin))?|max(?:imum)?|""" +
                """hanggang|hangtod|ubos|hindi\s+lalampas(?:\s+sa)?|""" +
                """di\s+lalampas(?:\s+sa)?|dili\s+molapas(?:\s+(?:og|ug))?)""" +
                """(?![\p{L}\p{N}_])""",
        )
        val WholeOrderBudgetCue = Regex(
            """(?<![\p{L}\p{N}_])(?:whole|entire|total)\s+(?:order|meal)|""" +
                """(?:order|meal)\s+total|buong\s+order|lahat\s+ng\s+order|""" +
                """tibuok\s+(?:order|kaon)(?![\p{L}\p{N}_])""",
        )
        val CurrencyCue = Regex(
            """(?:₱|(?<![\p{L}\p{N}_])php(?![\p{L}\p{N}_])|""" +
                """(?<![\p{L}\p{N}_])pesos?(?![\p{L}\p{N}_])|""" +
                """(?<![\p{L}\p{N}_])p(?=\s*\d))""",
        )
        val PesoAmount =
            """(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d{1,5}(?:\.\d{1,2})?)"""
        val CurrencyAmount = Regex(
            """(?:₱|(?<![\p{L}\p{N}_])php\s*|(?<![\p{L}\p{N}_])p\s*)$PesoAmount""",
        )
        val BudgetAmount = Regex(
            BudgetCue.pattern + """[^0-9]{0,24}$PesoAmount""",
        )
        const val MinimumPriceMinor = 100L
        const val MaximumPriceMinor = 10_000_000L
        val UnsupportedRestrictions = listOf(
            UnsupportedRestriction("vegan requests", listOf("vegan", "plant-based")),
            UnsupportedRestriction("halal preparation", listOf("halal")),
            UnsupportedRestriction("kosher preparation", listOf("kosher")),
            UnsupportedRestriction(
                "pork-free preparation",
                listOf("no pork", "pork-free", "without pork", "walang baboy", "walay baboy"),
            ),
            UnsupportedRestriction("pescatarian requests", listOf("pescatarian", "pescetarian")),
            UnsupportedRestriction("keto requests", listOf("keto", "ketogenic")),
            UnsupportedRestriction(
                "medical nutrition needs",
                listOf("low sodium", "low-sodium", "diabetic diet", "renal diet"),
            ),
        )
        val KnownAllergens = listOf(
            AllergenTerms(
                "peanut",
                listOf("peanut", "peanuts", "mani", "groundnut", "groundnuts"),
            ),
            AllergenTerms(
                "shellfish",
                listOf(
                    "shellfish",
                    "shrimp",
                    "shrimps",
                    "prawn",
                    "prawns",
                    "hipon",
                    "sugpo",
                    "pasayan",
                    "crab",
                    "crabs",
                    "alimango",
                    "alimasag",
                    "lobster",
                    "lobsters",
                    "scallop",
                    "scallops",
                    "mussel",
                    "mussels",
                    "oyster",
                    "oysters",
                ),
            ),
            AllergenTerms(
                "gluten",
                listOf("gluten", "wheat", "trigo", "coeliac", "celiac"),
            ),
            AllergenTerms(
                "dairy",
                listOf("milk", "dairy", "lactose", "gatas", "cheese", "keso"),
            ),
            AllergenTerms("egg", listOf("egg", "eggs", "itlog")),
            AllergenTerms("soy", listOf("soy", "soya", "soybean", "soybeans")),
            AllergenTerms("sesame", listOf("sesame", "linga")),
            AllergenTerms(
                "tree_nut",
                listOf(
                    "tree nut",
                    "tree nuts",
                    "almond",
                    "almonds",
                    "cashew",
                    "cashews",
                    "kasoy",
                    "walnut",
                    "walnuts",
                    "pistachio",
                    "pistachios",
                    "hazelnut",
                    "hazelnuts",
                    "pecan",
                    "pecans",
                    "macadamia",
                    "macadamias",
                    "pili nut",
                    "pili nuts",
                ),
            ),
            AllergenTerms(
                "fish",
                listOf("fish", "isda", "tuna", "salmon", "bangus", "tilapia"),
            ),
        )
    }
}
