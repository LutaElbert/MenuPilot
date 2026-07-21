package com.menupilot.restaurant.app

import androidx.lifecycle.SavedStateHandle
import com.menupilot.domain.AvoidanceReason
import com.menupilot.domain.MenuItemId
import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.MenuVariantRef
import com.menupilot.domain.RecommendationEngine
import com.menupilot.domain.VariantId
import com.menupilot.restaurant.assistant.DeterministicIntentAssistant
import com.menupilot.restaurant.assistant.DiningBudgetScope
import com.menupilot.assistant.contract.FunctionGemmaRoute
import com.menupilot.assistant.contract.FunctionGemmaRouteAction
import com.menupilot.assistant.contract.FunctionGemmaSalesPeriod
import com.menupilot.restaurant.assistant.AssistantInterpretation
import com.menupilot.restaurant.assistant.ClarificationRequirement
import com.menupilot.restaurant.assistant.IntentAssistant
import com.menupilot.restaurant.data.FixtureMenuRepository
import com.menupilot.restaurant.data.MenuRepository
import com.menupilot.restaurant.data.ServicePlacement
import com.menupilot.restaurant.data.VenueTabletConfig
import com.menupilot.restaurant.feature.feedback.FeedbackStage
import com.menupilot.restaurant.feature.handoff.HandoffBudgetScope
import com.menupilot.restaurant.feature.handoff.HandoffDeliveryMode
import com.menupilot.restaurant.feature.handoff.HandoffPlacement
import com.menupilot.restaurant.feature.handoff.HandoffQuestionCode
import com.menupilot.restaurant.external.ReviewDestinationReadiness
import com.menupilot.restaurant.external.assessReviewDestination
import com.menupilot.restaurant.feedback.FeedbackCommentGenerator
import com.menupilot.restaurant.feedback.FeedbackDimension
import com.menupilot.restaurant.staff.DemoStaffAuthorizer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuPilotViewModelTest {

    @Test
    fun `read only conversational route preserves evaluated catalog and shortlist`() {
        val deterministic = DeterministicIntentAssistant()
        val assistant = object : IntentAssistant {
            override fun interpret(query: String): AssistantInterpretation =
                deterministic.interpret(query)

            override suspend fun interpretAsync(
                query: String,
                context: BoundedConversationContext,
            ): AssistantInterpretation =
                if (query.contains("waiter", ignoreCase = true)) {
                    AssistantInterpretation.Routed(
                        route = FunctionGemmaRoute(
                            FunctionGemmaRouteAction.REQUEST_WAITER,
                        ),
                        message = "Nothing has been sent.",
                    )
                } else {
                    deterministic.interpret(query)
                }
        }
        val viewModel = createViewModel(intentAssistant = assistant)
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        val before = viewModel.uiState.value

        viewModel.updateQuery("Please call the waiter.")
        viewModel.submitQuery()

        val after = viewModel.uiState.value
        assertEquals(before.curatedDishes, after.curatedDishes)
        assertEquals(before.cartEntries, after.cartEntries)
        assertEquals(before.orderSuggestions, after.orderSuggestions)
        assertEquals(before.confirmedMenuRevision, after.confirmedMenuRevision)
        assertTrue(after.assistantMessage.contains("Nothing was sent"))
        assertNull(after.waiterHandoff)
    }

    @Test
    fun `routed bestseller answer uses the current verified sales record`() {
        val viewModel = createViewModel(
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.SHOW_BESTSELLERS,
                    period = FunctionGemmaSalesPeriod.THIS_WEEK,
                ),
            ),
        )

        viewModel.updateQuery("What was hot in the current sales window?")
        viewModel.submitQuery()

        val message = viewModel.uiState.value.assistantMessage
        assertTrue(message.contains("Garlic Rice"))
        assertTrue(message.contains("268 orders"))
        assertTrue(message.contains("Demo POS sales record"))
        assertFalse(message.contains("all-time", ignoreCase = true))
    }

    @Test
    fun `routed historical bestseller refuses to invent an unavailable period`() {
        val viewModel = createViewModel(
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.SHOW_BESTSELLERS,
                    period = FunctionGemmaSalesPeriod.LAST_WEEK,
                ),
            ),
        )

        viewModel.updateQuery("What was the bestseller last week?")
        viewModel.submitQuery()

        val message = viewModel.uiState.value.assistantMessage
        assertTrue(message.contains("don’t have a verified last week"))
        assertTrue(message.contains("won’t invent"))
    }

    @Test
    fun `neutral bestseller route excludes an unavailable sales leader`() {
        val clock = fixedClock()
        val base = FixtureMenuRepository(clock).currentCatalog()
        val unavailableLeader = object : MenuRepository {
            override fun currentCatalog() = base.copy(
                snapshot = base.snapshot.copy(
                    items = base.snapshot.items.map { item ->
                        if (item.id.value == "garlic_rice") {
                            item.copy(available = false)
                        } else {
                            item
                        }
                    },
                ),
            )
        }
        val viewModel = createViewModel(
            menuRepository = unavailableLeader,
            clock = clock,
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.SHOW_BESTSELLERS,
                    period = FunctionGemmaSalesPeriod.THIS_WEEK,
                ),
            ),
        )

        viewModel.updateQuery("What is your bestseller this week?")
        viewModel.submitQuery()

        val message = viewModel.uiState.value.assistantMessage
        assertFalse(message.contains("Garlic Rice"))
        assertTrue(message.contains("orders"))
    }

    @Test
    fun `routed dish explanation is grounded in the current catalog`() {
        val viewModel = createViewModel(
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.EXPLAIN_DISH,
                    subject = "Chicken Inasal",
                ),
            ),
        )

        viewModel.updateQuery("Tell me about Chicken Inasal.")
        viewModel.submitQuery()

        val message = viewModel.uiState.value.assistantMessage
        assertTrue(message.contains("Chicken Inasal Plate"))
        assertTrue(message.contains("₱520"))
        assertTrue(message.contains("20 minutes"))
        assertTrue(message.contains("not a kitchen allergy guarantee"))
    }

    @Test
    fun `routed pairing is optional priced and never auto added`() {
        val viewModel = createViewModel(
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.SUGGEST_PAIRING,
                    subject = "Chicken Inasal Plate",
                ),
            ),
        )
        assertTrue(viewModel.beginBrowseSession())
        viewModel.addDishToShortlist("chicken_inasal", "standard")
        val before = viewModel.uiState.value.cartEntries

        viewModel.updateQuery("What pairs with Chicken Inasal Plate?")
        viewModel.submitQuery()

        val message = viewModel.uiState.value.assistantMessage
        assertTrue(message.contains("Optional pairings"))
        assertTrue(message.contains("₱"))
        assertTrue(message.contains("none was added"))
        assertEquals(before, viewModel.uiState.value.cartEntries)
    }

    @Test
    fun `routed pairing recomputes for the requested anchor instead of cached cart suggestions`() {
        val viewModel = createViewModel(
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.SUGGEST_PAIRING,
                    subject = "Crispy Chili Cauliflower",
                ),
            ),
        )
        assertTrue(viewModel.beginBrowseSession())
        viewModel.addDishToShortlist("chicken_inasal", "standard")
        assertEquals(2, viewModel.uiState.value.orderSuggestions.size)

        viewModel.updateQuery("What pairs with Crispy Chili Cauliflower?")
        viewModel.submitQuery()

        val message = viewModel.uiState.value.assistantMessage
        assertTrue(message.contains("Calamansi Iced Tea"))
        assertFalse(message.contains("Coconut Sago"))
        assertEquals(
            listOf("chicken_inasal"),
            viewModel.uiState.value.cartEntries.map { it.itemId },
        )
    }

    @Test
    fun `unrelated routed answer cannot clear an unresolved health clarification`() {
        val deterministic = DeterministicIntentAssistant()
        val assistant = object : IntentAssistant {
            override fun interpret(query: String): AssistantInterpretation =
                deterministic.interpret(query)

            override suspend fun interpretAsync(
                query: String,
                context: BoundedConversationContext,
            ): AssistantInterpretation =
                if (query.contains("sales", ignoreCase = true)) {
                    AssistantInterpretation.Routed(
                        route = FunctionGemmaRoute(
                            action = FunctionGemmaRouteAction.SHOW_BESTSELLERS,
                            period = FunctionGemmaSalesPeriod.THIS_WEEK,
                        ),
                        message = "Advisory only.",
                    )
                } else {
                    deterministic.interpret(query)
                }
        }
        val viewModel = createViewModel(intentAssistant = assistant)
        viewModel.updateQuery("I have anaphylaxis to something in the sauce.")
        viewModel.submitQuery()
        assertTrue(viewModel.uiState.value.conversationNeedsClarification)

        viewModel.updateQuery("What is popular in the current sales window?")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertTrue(state.conversationNeedsClarification)
        assertFalse(state.canConfirmIntent)
        assertTrue(state.assistantMessage.contains("earlier clarification"))
    }

    @Test
    fun `unrelated ready preference cannot clear an unresolved health clarification`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("I react to something in the sauce.")
        viewModel.submitQuery()
        assertTrue(viewModel.uiState.value.conversationNeedsClarification)

        viewModel.updateQuery("I want something spicy and popular.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertTrue(state.conversationNeedsClarification)
        assertFalse(state.canConfirmIntent)
        assertTrue(state.assistantMessage.contains("earlier clarification"))
    }

    @Test
    fun `named allergy response resolves only the matching health clarification`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("I react to something in the sauce.")
        viewModel.submitQuery()

        viewModel.updateQuery("I am allergic to peanuts.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertFalse(state.conversationNeedsClarification)
        assertTrue(state.canConfirmIntent)
        assertEquals(listOf("peanut"), state.intent?.allergens)
    }

    @Test
    fun `vague second turn cannot weaken a pending health clarification`() {
        val deterministic = DeterministicIntentAssistant()
        val assistant = object : IntentAssistant {
            override fun interpret(query: String): AssistantInterpretation =
                deterministic.interpret(query)

            override suspend fun interpretAsync(
                query: String,
                context: BoundedConversationContext,
            ): AssistantInterpretation = when {
                query.contains("not sure", ignoreCase = true) ->
                    AssistantInterpretation.NeedsClarification(
                        message = "Please tell me a little more.",
                        requirement = ClarificationRequirement.GENERAL_REQUEST,
                    )
                else -> deterministic.interpret(query)
            }
        }
        val viewModel = createViewModel(intentAssistant = assistant)
        viewModel.updateQuery("I react to something in the sauce.")
        viewModel.submitQuery()
        viewModel.updateQuery("I’m not sure.")
        viewModel.submitQuery()
        viewModel.updateQuery("I want something spicy.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertTrue(state.conversationNeedsClarification)
        assertEquals(
            ClarificationRequirement.NAMED_HEALTH_CONSTRAINT,
            state.pendingClarification,
        )
        assertFalse(state.canConfirmIntent)
    }

    @Test
    fun `unsupported restriction is not cleared by an unrelated soft preference`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("I need a keto meal.")
        viewModel.submitQuery()
        assertTrue(viewModel.uiState.value.conversationNeedsClarification)

        viewModel.updateQuery("Make it spicy.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertTrue(state.conversationNeedsClarification)
        assertEquals(
            ClarificationRequirement.SUPPORTED_RESTRICTION,
            state.pendingClarification,
        )
    }

    @Test
    fun `allergy shortlist rejects unknown item and creates a waiter handoff`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)

        val catalog = viewModel.uiState.value.curatedDishes
        assertFalse(catalog.any { it.dish.id.value == "garden_kare_kare" })
        val unknown = catalog.single { it.dish.id.value == "miso_eggplant" }
        assertFalse(unknown.canAddToOrder)

        viewModel.addDishToShortlist(unknown.dish.id.value, unknown.dish.variantId.value)
        assertTrue(viewModel.uiState.value.cartEntries.isEmpty())

        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        val selected = viewModel.uiState.value
        assertEquals("standard", selected.cartEntries.single().variantId)
        assertTrue(selected.requiresStaffAcknowledgment)
        assertTrue(viewModel.createWaiterHandoff())
        val handedOff = viewModel.uiState.value
        val payload = requireNotNull(handedOff.waiterHandoff)
        assertEquals(handedOff.handoffReference, payload.reference)
        assertEquals("demo-menu-2026-07-19.1", payload.menuRevision)
        assertEquals("1", payload.safetyPolicyVersion)
        assertEquals("demo-merchandising-2026-07-19.1", payload.merchandisingRevision)
        assertEquals(handedOff.confirmedIntentFingerprint, payload.intentFingerprint)
        assertEquals(HandoffDeliveryMode.SCREEN_ONLY, payload.deliveryMode)
        assertEquals("chili_lime_tofu", payload.picks.single().itemId)
        assertEquals("standard", payload.picks.single().variantId)
        assertEquals("chili_lime_tofu-recipe-1", payload.picks.single().recipeRevision)
        assertTrue(payload.diningNeeds.any { it.canonicalId == "peanut" && it.safetyCritical })
        assertTrue(
            payload.unresolvedQuestions.any {
                it.code == HandoffQuestionCode.CONFIRM_ALLERGY_AND_CROSS_CONTACT
            },
        )
        assertFalse(handedOff.staffAcknowledged)

        // A waiter may still record that they reviewed the exact shortlist, but this is not
        // required merely to prepare the human handoff.
        assertFalse(viewModel.verifyStaffPin("0000"))
        assertTrue(viewModel.verifyStaffPin("2468"))
        assertTrue(viewModel.uiState.value.staffAcknowledged)
        assertNotNull(viewModel.uiState.value.waiterHandoff?.staffReview)
    }

    @Test
    fun `shortlist mutation invalidates acknowledgment without blocking waiter handoff`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        assertTrue(viewModel.verifyStaffPin("2468"))

        viewModel.addDishToShortlist("pumpkin_herb_salad", "standard")

        assertFalse(viewModel.uiState.value.staffAcknowledged)
        assertNull(viewModel.uiState.value.staffAcknowledgment)
        assertTrue(viewModel.createWaiterHandoff())
    }

    @Test
    fun `intent mutation clears evaluated catalog cart and acknowledgment`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        assertTrue(viewModel.verifyStaffPin("2468"))

        viewModel.removeIntentChip("allergen:peanut")

        val state = viewModel.uiState.value
        assertTrue(state.cartEntries.isEmpty())
        assertTrue(state.curatedDishes.isEmpty())
        assertNull(state.staffAcknowledgment)
        assertNull(state.confirmedMenuRevision)
    }

    @Test
    fun `removing budget clears its typed amount and scope`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("Keep my whole order under ₱500.")
        viewModel.submitQuery()
        assertEquals(
            DiningBudgetScope.WHOLE_ORDER,
            viewModel.uiState.value.intent?.budgetScope,
        )

        viewModel.removeIntentChip("budget")

        val intent = requireNotNull(viewModel.uiState.value.intent)
        assertNull(intent.maximumPriceMinor)
        assertNull(intent.budgetScope)
        assertFalse(
            viewModel.boundedConversationContext().authoritativeHardConstraints
                .any { it.startsWith("budget_scope:") },
        )
    }

    @Test
    fun `old whole order prose cannot reclassify a new explicit per dish budget`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("Keep my whole order under ₱500.")
        viewModel.submitQuery()
        viewModel.removeIntentChip("budget")

        viewModel.updateQuery("Use a per-dish budget of ₱700.")
        viewModel.submitQuery()

        val intent = requireNotNull(viewModel.uiState.value.intent)
        assertEquals(70_000L, intent.maximumPriceMinor)
        assertEquals(DiningBudgetScope.PER_DISH, intent.budgetScope)
        assertTrue(
            "budget_scope:per_dish" in
                viewModel.boundedConversationContext().authoritativeHardConstraints,
        )
    }

    @Test
    fun `eligible pairings are explainable optional and never auto added`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)

        viewModel.addDishToShortlist("chili_lime_tofu", "standard")

        val state = viewModel.uiState.value
        assertEquals(listOf("chili_lime_tofu"), state.cartDishIds)
        assertEquals(
            listOf("calamansi_iced_tea", "coconut_sago"),
            state.orderSuggestions.map { it.dish.id.value },
        )
        assertTrue(state.orderSuggestions.all { it.reason.isNotBlank() })
        assertTrue(state.orderSuggestions.all { it.requiresStaffConfirmation })
    }

    @Test
    fun `accepting an exact suggestion mutates the cart and invalidates staff verification`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        assertTrue(viewModel.verifyStaffPin("2468"))

        val suggestion = viewModel.uiState.value.orderSuggestions.first()
        viewModel.addPairingToShortlist(
            suggestion.variant.itemId.value,
            suggestion.variant.variantId.value,
        )

        val state = viewModel.uiState.value
        assertEquals(2, state.cartEntries.size)
        assertTrue(suggestion.variant.itemId.value in state.cartDishIds)
        assertNull(state.staffAcknowledgment)
        assertFalse(state.staffAcknowledged)
    }

    @Test
    fun `accepted optional pairings are capped at two`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")

        repeat(2) {
            val suggestion = requireNotNull(viewModel.uiState.value.orderSuggestions.firstOrNull())
            viewModel.addPairingToShortlist(
                suggestion.variant.itemId.value,
                suggestion.variant.variantId.value,
            )
        }

        val state = viewModel.uiState.value
        assertEquals(2, state.acceptedPairingRefs.size)
        assertEquals(3, state.cartEntries.size)
        assertTrue(state.orderSuggestions.isEmpty())
        assertTrue(state.suggestionNotice.orEmpty().contains("maximum of 2"))
    }

    @Test
    fun `dismissed suggestion does not return after later cart changes`() {
        val viewModel = createViewModel()
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        val dismissed = viewModel.uiState.value.orderSuggestions.first()

        viewModel.dismissSuggestion(
            dismissed.variant.itemId.value,
            dismissed.variant.variantId.value,
        )
        val remaining = viewModel.uiState.value.orderSuggestions.first()
        viewModel.addPairingToShortlist(
            remaining.variant.itemId.value,
            remaining.variant.variantId.value,
        )

        assertFalse(
            viewModel.uiState.value.orderSuggestions.any {
                it.variant == dismissed.variant
            },
        )
    }

    @Test
    fun `explicit whole-order budget conservatively suppresses over-budget pairings`() {
        val viewModel = createViewModel()
        viewModel.beginSession()
        viewModel.updateQuery(
            "I'm allergic to peanuts, vegetarian, spicy, whole order under ₱500.",
        )
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())

        viewModel.addDishToShortlist("chili_lime_tofu", "standard")

        assertTrue(viewModel.uiState.value.orderSuggestions.isEmpty())
    }

    @Test
    fun `second manual selection is rejected when it exceeds whole-order budget`() {
        val viewModel = createViewModel()
        viewModel.beginSession()
        viewModel.updateQuery("Vegetarian, whole order under ₱500.")
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())

        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        viewModel.addDishToShortlist("pumpkin_herb_salad", "standard")

        val state = viewModel.uiState.value
        assertEquals(
            setOf(MenuDishKey("chili_lime_tofu", "standard")),
            state.cartVariantKeys,
        )
        assertTrue(state.suggestionNotice.orEmpty().contains("whole-order budget"))
    }

    @Test
    fun `handoff records whole-shortlist budget scope`() {
        val viewModel = createViewModel()
        viewModel.beginSession()
        viewModel.updateQuery("Vegetarian, whole order under ₱500.")
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")

        assertTrue(viewModel.createWaiterHandoff())
        val budget = requireNotNull(viewModel.uiState.value.waiterHandoff)
            .diningNeeds.single { it.budgetScope != null }
        assertEquals(HandoffBudgetScope.WHOLE_SHORTLIST, budget.budgetScope)
        assertTrue(budget.guestLabel.contains("Whole-order budget"))
    }

    @Test
    fun `facts becoming stale between shortlist and handoff fail closed`() {
        val clock = MutableTestClock(Instant.parse("2026-07-19T12:00:00Z"))
        val viewModel = createViewModel(clock = clock)
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")

        clock.advance(Duration.ofDays(2))

        assertFalse(viewModel.createWaiterHandoff())
        assertNull(viewModel.uiState.value.waiterHandoff)
        assertTrue(
            viewModel.uiState.value.errorMessage.orEmpty()
                .contains("no longer matches"),
        )
    }

    @Test
    fun `stale suggestion cannot be accepted from a previously rendered card`() {
        val clock = MutableTestClock(Instant.parse("2026-07-19T12:00:00Z"))
        val viewModel = createViewModel(clock = clock)
        confirmFlagshipIntent(viewModel)
        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        val shown = requireNotNull(viewModel.uiState.value.orderSuggestions.firstOrNull())

        clock.advance(Duration.ofDays(2))
        viewModel.addPairingToShortlist(
            shown.variant.itemId.value,
            shown.variant.variantId.value,
        )

        assertEquals(1, viewModel.uiState.value.cartEntries.size)
        assertTrue(
            viewModel.uiState.value.suggestionNotice.orEmpty()
                .contains("no longer supported"),
        )
    }

    @Test
    fun `bestseller ranking and labels come from one comparable sales window`() {
        val viewModel = createViewModel()
        viewModel.beginSession()
        viewModel.updateQuery("What is your bestseller?")
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())

        val first = viewModel.uiState.value.curatedDishes.first()
        assertEquals("garlic_rice", first.dish.id.value)
        assertEquals(268L, first.salesEvidence?.orderCount)
        assertTrue(first.salesEvidence?.isBestseller == true)
        assertTrue(first.matchReasons.any { it.startsWith("Bestseller") })
        assertEquals("Trailing 7 days", viewModel.salesWindowLabel)
        assertEquals("Demo POS sales record", viewModel.salesSourceLabel)
        assertEquals("Observed 19 Jul 2026 12:00 UTC", viewModel.salesUpdatedLabel)
    }

    @Test
    fun `same item variants remain independently addressable`() {
        val clock = fixedClock()
        val viewModel = createViewModel(multiVariantRepository(clock), clock)
        assertTrue(viewModel.beginBrowseSession())

        viewModel.addDishToShortlist("chili_lime_tofu", "standard")
        viewModel.addDishToShortlist("chili_lime_tofu", "large")
        assertEquals(
            setOf(
                MenuDishKey("chili_lime_tofu", "standard"),
                MenuDishKey("chili_lime_tofu", "large"),
            ),
            viewModel.uiState.value.cartVariantKeys,
        )

        viewModel.removeDishFromShortlist("chili_lime_tofu", "standard")
        assertEquals(
            setOf(MenuDishKey("chili_lime_tofu", "large")),
            viewModel.uiState.value.cartVariantKeys,
        )
    }

    @Test
    fun `catalog price drift blocks browsing before policy output is shown`() {
        val clock = fixedClock()
        val base = FixtureMenuRepository(clock).currentCatalog()
        val mismatched = object : MenuRepository {
            override fun currentCatalog() = base.copy(
                dishes = base.dishes.mapIndexed { index, dish ->
                    if (index == 0) dish.copy(priceMinor = dish.priceMinor + 100) else dish
                },
            )
        }
        val viewModel = createViewModel(mismatched, clock)

        assertFalse(viewModel.beginBrowseSession())
        assertTrue(viewModel.allDishes.isEmpty())
        assertTrue(viewModel.uiState.value.curatedDishes.isEmpty())
        assertTrue(
            viewModel.uiState.value.errorMessage.orEmpty().contains("price snapshot"),
        )
    }

    @Test
    fun `catalog drift blocks routed facts and upsells before any dish data is shown`() {
        val clock = fixedClock()
        val base = FixtureMenuRepository(clock).currentCatalog()
        val mismatched = object : MenuRepository {
            override fun currentCatalog() = base.copy(
                dishes = base.dishes.mapIndexed { index, dish ->
                    if (index == 0) dish.copy(priceMinor = dish.priceMinor + 100) else dish
                },
            )
        }
        val viewModel = createViewModel(
            menuRepository = mismatched,
            clock = clock,
            intentAssistant = routedAssistant(
                FunctionGemmaRoute(
                    action = FunctionGemmaRouteAction.EXPLAIN_DISH,
                    subject = "Chicken Inasal Plate",
                ),
            ),
        )

        viewModel.updateQuery("Tell me about Chicken Inasal Plate.")
        viewModel.submitQuery()

        val state = viewModel.uiState.value
        assertTrue(state.conversationNeedsClarification)
        assertTrue(state.assistantMessage.contains("does not match"))
        assertFalse(state.assistantMessage.contains("₱"))
        assertFalse(state.assistantMessage.contains("20 minutes"))
    }

    @Test
    fun `fixture review destination is explicitly a non-production Maps fallback`() {
        val viewModel = createViewModel()

        assertEquals(
            ReviewDestinationReadiness.BUSINESS_PROFILE_FALLBACK,
            assessReviewDestination(viewModel.googleMapsReviewUrl),
        )
    }

    @Test
    fun `voice transcript stays editable and cannot bypass intent confirmation`() {
        val viewModel = createViewModel()
        viewModel.beginSession()

        viewModel.updateQuery("I’m allergic to peanuts and want something spicy")

        val state = viewModel.uiState.value
        assertEquals("I’m allergic to peanuts and want something spicy", state.query)
        assertNull(state.intent)
        assertFalse(state.hasConfirmedCatalog)
        assertTrue(state.curatedDishes.isEmpty())
        assertTrue(state.cartEntries.isEmpty())
    }

    @Test
    fun `health language never silently falls back to a popular preference`() {
        val viewModel = createViewModel()
        viewModel.updateQuery("I have anaphylaxis to something in the sauce.")
        viewModel.submitQuery()

        assertNull(viewModel.uiState.value.intent)
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.updateQuery("I have coeliac disease.")
        viewModel.submitQuery()
        val coeliac = requireNotNull(viewModel.uiState.value.intent)
        assertEquals(listOf("gluten"), coeliac.allergens)
        assertEquals(AvoidanceReason.CELIAC, coeliac.avoidanceReasons["gluten"])
    }

    @Test
    fun `mild request affects ranking and explanation`() {
        val viewModel = createViewModel()
        viewModel.beginSession()
        viewModel.updateQuery("I want something not spicy.")
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())

        val firstMatch = viewModel.uiState.value.curatedDishes.first()
        assertFalse(firstMatch.dish.flavorTags.any { it.equals("Spicy", ignoreCase = true) })
        assertTrue("Mild spice" in firstMatch.matchReasons)
    }

    @Test
    fun `handoff context comes from venue tablet configuration`() {
        val clock = fixedClock()
        val baseRepository = FixtureMenuRepository(clock)
        val counterRepository = object : MenuRepository {
            override fun currentCatalog() = baseRepository.currentCatalog().copy(
                tabletConfig = VenueTabletConfig(
                    locationLabel = "Pickup Counter A",
                    placement = ServicePlacement.COUNTER,
                    liveStaffChannelConnected = false,
                ),
            )
        }
        val context = createViewModel(counterRepository, clock).waiterHandoffContext()

        assertEquals("Pickup Counter A", context.locationLabel)
        assertEquals(HandoffPlacement.COUNTER, context.placement)
        assertFalse(context.liveStaffChannelConnected)
    }

    @Test
    fun `feedback answers generate an editable balanced review`() {
        val viewModel = createViewModel()
        viewModel.beginFeedback()
        FeedbackDimension.entries.forEach { dimension ->
            viewModel.updateFeedbackRating(
                dimension,
                if (dimension == FeedbackDimension.SERVICE) 3 else 5,
            )
        }

        viewModel.continueFeedback()
        assertEquals(FeedbackStage.TAGS, viewModel.uiState.value.feedback.stage)
        viewModel.toggleFeedbackTag("fresh_ingredients")
        viewModel.continueFeedback()

        val feedback = viewModel.uiState.value.feedback
        assertEquals(FeedbackStage.REVIEW, feedback.stage)
        assertTrue(feedback.generatedReview.contains("fresh ingredients"))
        assertFalse(feedback.publicDraftApproved)

        viewModel.updateGeneratedReview("My own final wording.")
        assertEquals("My own final wording.", viewModel.uiState.value.feedback.generatedReview)
        viewModel.updatePublicDraftApproval(true)
        assertTrue(viewModel.uiState.value.feedback.publicDraftApproved)
        viewModel.updateGeneratedReview("I edited it again.")
        assertFalse(viewModel.uiState.value.feedback.publicDraftApproved)

        viewModel.updatePrivateFeedbackNote("Please have a manager follow up.")
        viewModel.updateManagerFollowUpRequested(true)
        val privateSubmission = viewModel.uiState.value.feedback.toPrivateSubmission()
        assertTrue(viewModel.recordPrivateFeedback(privateSubmission))
        assertTrue(viewModel.uiState.value.feedback.privateSubmissionRecorded)
    }

    private fun confirmFlagshipIntent(viewModel: MenuPilotViewModel) {
        viewModel.beginSession()
        viewModel.updateQuery(
            "I'm allergic to peanuts, vegetarian, and want something spicy under ₱500.",
        )
        viewModel.submitQuery()
        assertTrue(viewModel.confirmIntent())
    }

    private fun createViewModel(
        menuRepository: MenuRepository? = null,
        clock: Clock = fixedClock(),
        intentAssistant: IntentAssistant = DeterministicIntentAssistant(),
    ): MenuPilotViewModel {
        return MenuPilotViewModel(
            menuRepository = menuRepository ?: FixtureMenuRepository(clock),
            intentAssistant = intentAssistant,
            menuPolicyEngine = MenuPolicyEngine(),
            recommendationEngine = RecommendationEngine(),
            clock = clock,
            staffAuthorizer = DemoStaffAuthorizer(),
            feedbackCommentGenerator = FeedbackCommentGenerator(),
            savedStateHandle = SavedStateHandle(),
        )
    }

    private fun routedAssistant(route: FunctionGemmaRoute): IntentAssistant =
        object : IntentAssistant {
            private val deterministic = DeterministicIntentAssistant()

            override fun interpret(query: String): AssistantInterpretation =
                deterministic.interpret(query)

            override suspend fun interpretAsync(
                query: String,
                context: BoundedConversationContext,
            ): AssistantInterpretation = AssistantInterpretation.Routed(
                route = route,
                message = "App-owned route.",
            )
        }

    private fun multiVariantRepository(clock: Clock): MenuRepository {
        val base = FixtureMenuRepository(clock).currentCatalog()
        val item = base.snapshot.items.single { it.id.value == "chili_lime_tofu" }
        val standard = item.variants.single()
        val large = standard.copy(
            id = VariantId("large"),
            name = "Large",
            recipeRevision = "chili_lime_tofu-recipe-large-1",
            priceMinor = 49000,
        )
        val snapshot = base.snapshot.copy(
            revision = "demo-menu-multi-variant",
            items = base.snapshot.items.map {
                if (it.id == item.id) it.copy(variants = listOf(standard, large)) else it
            },
        )
        val sourceSignal = base.merchandisingSnapshot.salesSignals
            .single { it.variant.itemId == item.id }
        val merchandising = base.merchandisingSnapshot.copy(
            menuRevision = snapshot.revision,
            revision = "demo-merchandising-multi-variant",
            salesSignals = base.merchandisingSnapshot.salesSignals + sourceSignal.copy(
                variant = MenuVariantRef(item.id, large.id, large.recipeRevision),
                orderCount = 7,
            ),
        )
        val standardDish = base.dishes.single { it.id == item.id }
        val catalog = base.copy(
            snapshot = snapshot,
            merchandisingSnapshot = merchandising,
            dishes = base.dishes + standardDish.copy(
                variantId = large.id,
                recipeRevision = large.recipeRevision,
                name = "Large Chili-Lime Tofu Bowl",
                priceMinor = large.priceMinor,
            ),
        )
        return object : MenuRepository {
            override fun currentCatalog() = catalog
        }
    }

    private fun fixedClock(): Clock =
        Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC)

    private class MutableTestClock(
        private var current: Instant,
        private val currentZone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun getZone(): ZoneId = currentZone

        override fun withZone(zone: ZoneId): Clock = MutableTestClock(current, zone)

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
