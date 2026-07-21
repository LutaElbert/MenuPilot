package com.menupilot.restaurant.data

import com.menupilot.domain.MenuItemId
import com.menupilot.domain.MenuSnapshot
import com.menupilot.domain.MerchandisingSnapshot
import com.menupilot.domain.VariantId

enum class DishCategory(
    val label: String,
    val catalogOrder: Int,
) {
    MAIN("Mains", 0),
    STARTER("Starters", 1),
    SIDE("Sides", 2),
    DESSERT("Desserts", 3),
    DRINK("Drinks", 4),
}

data class MenuDish(
    val id: MenuItemId,
    val variantId: VariantId,
    val recipeRevision: String,
    val name: String,
    val description: String,
    val category: DishCategory,
    val priceMinor: Long,
    val ingredients: List<String>,
    val listedAllergens: List<String>,
    val dietaryTags: List<String>,
    val flavorTags: List<String>,
    val preparationNote: String,
    val photoAccent: Long,
    val photoSymbol: String,
    val prepMinutes: Int,
)

enum class ServicePlacement {
    TABLE,
    COUNTER,
}

data class VenueTabletConfig(
    val locationLabel: String,
    val placement: ServicePlacement,
    val liveStaffChannelConnected: Boolean,
)

data class RestaurantMenuCatalog(
    val restaurantName: String,
    val snapshot: MenuSnapshot,
    val merchandisingSnapshot: MerchandisingSnapshot,
    val dishes: List<MenuDish>,
    val googleMapsReviewUrl: String,
    val tabletConfig: VenueTabletConfig,
) {
    fun dish(itemId: MenuItemId, variantId: VariantId): MenuDish? =
        dishes.firstOrNull { it.id == itemId && it.variantId == variantId }
}

interface MenuRepository {
    fun currentCatalog(): RestaurantMenuCatalog
}
