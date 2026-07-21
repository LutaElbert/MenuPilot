package com.menupilot.restaurant.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Welcome : NavKey

@Serializable
data object Intake : NavKey

@Serializable
data object Catalog : NavKey

@Serializable
data object BrowseMenu : NavKey

@Serializable
data class DishDetail(
    val itemId: String,
    val variantId: String,
) : NavKey

@Serializable
data object Pairings : NavKey

@Serializable
data object Shortlist : NavKey

@Serializable
data object WaiterHandoff : NavKey

@Serializable
data object Feedback : NavKey
