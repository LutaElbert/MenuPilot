package com.menupilot.restaurant.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Curated restaurant-pilot language gate for the instant, safety-critical path.
 *
 * These are exact behavior fixtures, not a claim about open-world language accuracy. Every case
 * is exercised before the test reports failures so one regression cannot hide later mismatches.
 */
class DeterministicIntentQualificationTest {
    private val assistant = DeterministicIntentAssistant()

    @Test
    fun `restaurant pilot multilingual intent corpus passes every exact fixture`() {
        val failures = QualificationCases.mapNotNull { case ->
            val actual = assistant.interpret(case.query).snapshot()
            if (actual == case.expected) {
                null
            } else {
                "${case.name}\n  query=${case.query}\n  expected=${case.expected}\n  actual=$actual"
            }
        }

        assertTrue(
            "Failed ${failures.size}/${QualificationCases.size} qualification fixtures:\n" +
                failures.joinToString("\n\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `unknown health cue matrix always fails closed`() {
        val queries = listOf(
            "I shouldn't have mustard.",
            "I can't have mustard.",
            "I react to mustard.",
            "I am sensitive to mustard.",
            "I get hives from mustard.",
            "A rash after mustard.",
            "Hindi ako dapat kumain ng mustasa.",
            "Dili nako angay mokaon og mustard.",
        )
        val failures = queries.mapNotNull { query ->
            val result = assistant.interpret(query)
            if (result is AssistantInterpretation.NeedsClarification) {
                null
            } else {
                "$query -> ${result.snapshot()}"
            }
        }

        assertTrue(
            "Unknown health constraints must never default to popular:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private fun AssistantInterpretation.snapshot(): String = when (this) {
        is AssistantInterpretation.NeedsClarification -> "CLARIFY"
        is AssistantInterpretation.Ready -> with(summary) {
            val allergenSnapshot = allergens.joinToString(",") { allergen ->
                "$allergen:${avoidanceReasons.getValue(allergen).name}"
            }.ifEmpty { "NONE" }
            "READY|language=$detectedLanguage" +
                "|allergens=$allergenSnapshot" +
                "|diets=${diets.joinToString(",").ifEmpty { "NONE" }}" +
                "|preferences=${preferredAttributes.joinToString(",").ifEmpty { "NONE" }}" +
                "|budget=${maximumPriceMinor ?: "NONE"}"
        }
        is AssistantInterpretation.Routed ->
            "ROUTED|action=${route.action.name}"
    }

    private data class QualificationCase(
        val name: String,
        val query: String,
        val expected: String,
    )

    private companion object {
        const val Clarify = "CLARIFY"

        fun ready(
            language: String,
            allergens: String = "NONE",
            diets: String = "NONE",
            preferences: String = "NONE",
            budget: String = "NONE",
        ): String =
            "READY|language=$language|allergens=$allergens|diets=$diets" +
                "|preferences=$preferences|budget=$budget"

        val QualificationCases = listOf(
            QualificationCase(
                "english flagship",
                "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500.",
                ready(
                    language = "English",
                    allergens = "peanut:ALLERGY",
                    diets = "vegetarian",
                    preferences = "spicy",
                    budget = "50000",
                ),
            ),
            QualificationCase(
                "english shellfish intolerance",
                "I am intolerant to shrimp and need mild food.",
                ready("English", "shellfish:INTOLERANCE", preferences = "mild"),
            ),
            QualificationCase(
                "english coeliac",
                "I have coeliac disease and need something quick.",
                ready("English", "gluten:CELIAC", preferences = "quick"),
            ),
            QualificationCase(
                "english generic avoidance",
                "No milk, please.",
                ready("English", "dairy:UNSPECIFIED"),
            ),
            QualificationCase(
                "english reaction",
                "Sesame gives me hives. Show me something light.",
                ready("English", "sesame:UNSPECIFIED", preferences = "light"),
            ),
            QualificationCase(
                "english neutral ingredient question",
                "Does the salad contain peanuts?",
                Clarify,
            ),
            QualificationCase(
                "english unnamed allergy",
                "I have a food allergy. What can I order?",
                Clarify,
            ),
            QualificationCase(
                "english negated allergy",
                "I'm not allergic to peanuts and I want something spicy.",
                ready("English", preferences = "spicy"),
            ),
            QualificationCase(
                "english mixed disclaimer and avoidance",
                "I can safely eat peanuts but avoid dairy.",
                ready("English", "dairy:UNSPECIFIED"),
            ),
            QualificationCase(
                "english allergen is fine",
                "Egg is fine for me; I need something quick.",
                ready("English", preferences = "quick"),
            ),
            QualificationCase(
                "english vegetarian budget",
                "Vegetarian, light, and at most PHP 650.50.",
                ready(
                    language = "English",
                    diets = "vegetarian",
                    preferences = "light",
                    budget = "65050",
                ),
            ),
            QualificationCase(
                "english negated diet",
                "I'm not vegetarian. Show me the most ordered dish.",
                ready("English", preferences = "popular"),
            ),
            QualificationCase("english vegan fails closed", "I'm vegan.", Clarify),
            QualificationCase("english no pork fails closed", "No pork, please.", Clarify),
            QualificationCase(
                "english vague budget clarifies",
                "I need a budget-friendly meal.",
                Clarify,
            ),
            QualificationCase(
                "english peso shorthand",
                "No more than P400 and make it fast.",
                ready("English", preferences = "quick", budget = "40000"),
            ),
            QualificationCase(
                "english fish avoidance",
                "I don't eat fish and need something under 700 pesos.",
                ready("English", "fish:UNSPECIFIED", budget = "70000"),
            ),
            QualificationCase(
                "english tree nut allergy",
                "Cashews trigger my allergy. Something popular, please.",
                ready("English", "tree_nut:ALLERGY", preferences = "popular"),
            ),
            QualificationCase(
                "filipino peanut allergy",
                "May allergy ako sa mani. Gusto ko ng maanghang pero magaan.",
                ready("Filipino", "peanut:ALLERGY", preferences = "spicy,light"),
            ),
            QualificationCase(
                "filipino shellfish avoidance",
                "Bawal sa akin ang hipon. Mabilis sana.",
                ready("Filipino", "shellfish:UNSPECIFIED", preferences = "quick"),
            ),
            QualificationCase(
                "filipino cannot eat egg",
                "Hindi ako puwedeng kumain ng itlog.",
                ready("Filipino", "egg:UNSPECIFIED"),
            ),
            QualificationCase(
                "filipino negated allergy",
                "Hindi ako allergic sa mani; gusto ko ng maanghang.",
                ready("Taglish", preferences = "spicy"),
            ),
            QualificationCase(
                "filipino can eat disclaimer",
                "Pwede akong kumain ng hipon, gusto ko ng magaan.",
                ready("Filipino", preferences = "light"),
            ),
            QualificationCase(
                "filipino vegetarian mild budget",
                "Walang karne, hindi maanghang, hanggang ₱450.",
                ready(
                    language = "Filipino",
                    diets = "vegetarian",
                    preferences = "mild",
                    budget = "45000",
                ),
            ),
            QualificationCase(
                "filipino vegetarian quick",
                "Vegetarian ako at gusto ko ng mabilis.",
                ready("Filipino", diets = "vegetarian", preferences = "quick"),
            ),
            QualificationCase(
                "filipino budget and bestseller",
                "Budget ko ay 300 pesos, ano ang pinakasikat?",
                ready("Filipino", preferences = "popular", budget = "30000"),
            ),
            QualificationCase(
                "filipino neutral dairy question",
                "May gatas ba ito?",
                Clarify,
            ),
            QualificationCase("filipino vegan fails closed", "Vegan ako.", Clarify),
            QualificationCase("filipino no pork fails closed", "Walang baboy.", Clarify),
            QualificationCase(
                "filipino dairy intolerance",
                "Hindi ako hiyang sa gatas.",
                ready("Filipino", "dairy:INTOLERANCE"),
            ),
            QualificationCase(
                "taglish shellfish allergy",
                "Allergic ako sa hipon and I want something mabilis under ₱500.",
                ready(
                    language = "Taglish",
                    allergens = "shellfish:ALLERGY",
                    preferences = "quick",
                    budget = "50000",
                ),
            ),
            QualificationCase(
                "taglish vegetarian mild",
                "I'm vegetarian, ayoko ng maanghang.",
                ready("Taglish", diets = "vegetarian", preferences = "mild"),
            ),
            QualificationCase(
                "taglish avoidance and light",
                "No peanuts please, gusto ko ng magaan.",
                ready("Taglish", "peanut:UNSPECIFIED", preferences = "light"),
            ),
            QualificationCase(
                "taglish negated egg allergy",
                "Hindi ako allergic sa egg, popular please.",
                ready("Taglish", preferences = "popular"),
            ),
            QualificationCase(
                "taglish vague budget",
                "Allergic ako sa mani, budget-friendly sana.",
                Clarify,
            ),
            QualificationCase(
                "cebuano shellfish avoidance",
                "Dili ko pwede og shellfish. Ganahan ko ug halang ug dali.",
                ready("Cebuano", "shellfish:UNSPECIFIED", preferences = "spicy,quick"),
            ),
            QualificationCase(
                "cebuano shellfish allergy",
                "Alerdyi ko sa pasayan. Ganahan ko ug gaan.",
                ready("Cebuano", "shellfish:ALLERGY", preferences = "light"),
            ),
            QualificationCase(
                "cebuano english cannot eat egg",
                "Dili ko mokaon og itlog. Spicy please.",
                ready("Cebuano-English", "egg:UNSPECIFIED", preferences = "spicy"),
            ),
            QualificationCase(
                "cebuano vegetarian mild budget",
                "Walay karne, dili kaayo halang, hangtod ₱500.",
                ready(
                    language = "Cebuano",
                    diets = "vegetarian",
                    preferences = "mild",
                    budget = "50000",
                ),
            ),
            QualificationCase(
                "cebuano negated peanut allergy",
                "Dili ko allergic sa mani. Ganahan ko ug paspas.",
                ready("Cebuano-English", preferences = "quick"),
            ),
            QualificationCase(
                "cebuano can eat shellfish",
                "Makakaon ko og hipon; ganahan ko ug halang.",
                ready("Cebuano", preferences = "spicy"),
            ),
            QualificationCase(
                "cebuano neutral peanut question",
                "Naa bay mani ani?",
                Clarify,
            ),
            QualificationCase(
                "cebuano vegan fails closed",
                "Vegan ko, unsay naa?",
                Clarify,
            ),
            QualificationCase("cebuano no pork fails closed", "Walay baboy.", Clarify),
            QualificationCase(
                "cebuano budget and bestseller",
                "Budget nako kay ₱550, unsay pinakasikat?",
                ready("Cebuano", preferences = "popular", budget = "55000"),
            ),
            QualificationCase(
                "cebuano dairy intolerance",
                "Dili ko hiyang sa gatas.",
                ready("Cebuano", "dairy:INTOLERANCE"),
            ),
            QualificationCase(
                "english allergen-before-negated-condition",
                "I never had an egg allergy; egg is fine. Mild, please.",
                ready("English", preferences = "mild"),
            ),
            QualificationCase(
                "filipino no allergy form",
                "Wala akong allergy sa hipon, mabilis sana.",
                ready("Filipino", preferences = "quick"),
            ),
            QualificationCase(
                "cebuano no allergy form",
                "Wala koy allergy sa pasayan, ganahan ko ug dali.",
                ready("Cebuano", preferences = "quick"),
            ),
            QualificationCase(
                "english contrast keeps second allergy",
                "I am not allergic to peanuts but I am allergic to milk.",
                ready("English", "dairy:ALLERGY"),
            ),
            QualificationCase(
                "taglish contrast keeps second allergy",
                "Hindi ako allergic sa mani pero allergic ako sa hipon.",
                ready("Taglish", "shellfish:ALLERGY"),
            ),
            QualificationCase(
                "cebuano contrast keeps second allergy",
                "Dili ko allergic sa mani apan alerdyi ko sa pasayan.",
                ready("Cebuano-English", "shellfish:ALLERGY"),
            ),
            QualificationCase(
                "english should-not constraint",
                "I should not have peanuts.",
                ready("English", "peanut:UNSPECIFIED"),
            ),
            QualificationCase(
                "english contraction should-not constraint",
                "I shouldn't eat dairy.",
                ready("English", "dairy:UNSPECIFIED"),
            ),
            QualificationCase(
                "english cannot-have constraint",
                "I can't have shellfish.",
                ready("English", "shellfish:UNSPECIFIED"),
            ),
            QualificationCase(
                "english must-not constraint",
                "I must not eat eggs.",
                ready("English", "egg:UNSPECIFIED"),
            ),
            QualificationCase(
                "english symptom before ingredient",
                "I get hives from sesame.",
                ready("English", "sesame:UNSPECIFIED"),
            ),
            QualificationCase(
                "english symptom after eating",
                "I get a rash after eating cashews.",
                ready("English", "tree_nut:UNSPECIFIED"),
            ),
            QualificationCase(
                "unknown symptom ingredient fails closed",
                "I get hives from mustard.",
                Clarify,
            ),
            QualificationCase(
                "english negated symptom before ingredient",
                "I don't get hives from sesame; show me something quick.",
                ready("English", preferences = "quick"),
            ),
            QualificationCase(
                "english negated symptom after ingredient",
                "Sesame does not give me hives. Something light, please.",
                ready("English", preferences = "light"),
            ),
            QualificationCase(
                "filipino symptom phrasing",
                "Sumasakit ang tiyan ko sa gatas.",
                ready("Filipino", "dairy:UNSPECIFIED"),
            ),
            QualificationCase(
                "cebuano symptom phrasing",
                "Magsakit akong tiyan sa gatas.",
                ready("Cebuano", "dairy:UNSPECIFIED"),
            ),
            QualificationCase(
                "filipino should-not constraint",
                "Hindi ako dapat kumain ng mani.",
                ready("Filipino", "peanut:UNSPECIFIED"),
            ),
            QualificationCase(
                "cebuano should-not constraint",
                "Dili nako angay mokaon og hipon.",
                ready("Cebuano", "shellfish:UNSPECIFIED"),
            ),
            QualificationCase(
                "all nine supported allergen aliases remain bounded",
                "No peanuts, shrimp, wheat, milk, eggs, soy, sesame, cashews, or fish.",
                ready(
                    language = "English",
                    allergens =
                        "peanut:UNSPECIFIED,shellfish:UNSPECIFIED,gluten:UNSPECIFIED," +
                            "dairy:UNSPECIFIED,egg:UNSPECIFIED,soy:UNSPECIFIED," +
                            "sesame:UNSPECIFIED,tree_nut:UNSPECIFIED,fish:UNSPECIFIED",
                ),
            ),
        )
    }
}
