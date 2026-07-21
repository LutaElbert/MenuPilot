package com.menupilot.restaurant.di

import com.menupilot.domain.MenuPolicyEngine
import com.menupilot.domain.RecommendationEngine
import com.menupilot.restaurant.assistant.DeterministicIntentAssistant
import com.menupilot.restaurant.assistant.AdkFunctionGemmaAgentOrchestrator
import com.menupilot.restaurant.assistant.AgenticIntentAssistant
import com.menupilot.restaurant.assistant.FunctionGemmaAgentOrchestrator
import com.menupilot.restaurant.assistant.FunctionGemmaRouteGenerator
import com.menupilot.restaurant.assistant.FunctionGemmaRouter
import com.menupilot.restaurant.assistant.GroundedQwenDishInsightService
import com.menupilot.restaurant.assistant.HybridIntentAssistant
import com.menupilot.restaurant.assistant.IntentAssistant
import com.menupilot.restaurant.assistant.LiteRtLmFunctionGemmaRouteGenerator
import com.menupilot.restaurant.assistant.LiteRtLmQwenDishInsightSelectionGenerator
import com.menupilot.restaurant.assistant.LiteRtLmTextGenerator
import com.menupilot.restaurant.assistant.OnDeviceFunctionGemmaRouter
import com.menupilot.restaurant.assistant.OnDeviceTextGenerator
import com.menupilot.restaurant.data.FixtureMenuRepository
import com.menupilot.restaurant.data.MenuRepository
import com.menupilot.restaurant.staff.DemoStaffAuthorizer
import com.menupilot.restaurant.staff.StaffAuthorizer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @Singleton
    abstract fun bindMenuRepository(implementation: FixtureMenuRepository): MenuRepository

    @Binds
    @Singleton
    abstract fun bindIntentAssistant(implementation: AgenticIntentAssistant): IntentAssistant

    @Binds
    @Singleton
    abstract fun bindFunctionGemmaAgentOrchestrator(
        implementation: AdkFunctionGemmaAgentOrchestrator,
    ): FunctionGemmaAgentOrchestrator

    @Binds
    @Singleton
    abstract fun bindOnDeviceTextGenerator(
        implementation: LiteRtLmTextGenerator,
    ): OnDeviceTextGenerator

    @Binds
    @Singleton
    abstract fun bindFunctionGemmaRouter(
        implementation: OnDeviceFunctionGemmaRouter,
    ): FunctionGemmaRouter

    @Binds
    @Singleton
    abstract fun bindFunctionGemmaRouteGenerator(
        implementation: LiteRtLmFunctionGemmaRouteGenerator,
    ): FunctionGemmaRouteGenerator

    @Binds
    @Singleton
    abstract fun bindStaffAuthorizer(implementation: DemoStaffAuthorizer): StaffAuthorizer
}

@Module
@InstallIn(SingletonComponent::class)
object AppProviders {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideMenuPolicyEngine(): MenuPolicyEngine = MenuPolicyEngine()

    @Provides
    @Singleton
    fun provideRecommendationEngine(
        menuPolicyEngine: MenuPolicyEngine,
    ): RecommendationEngine = RecommendationEngine(menuPolicyEngine)

    @Provides
    @Singleton
    fun provideDishInsightService(
        generator: LiteRtLmQwenDishInsightSelectionGenerator,
    ): GroundedQwenDishInsightService = GroundedQwenDishInsightService(generator)
}
