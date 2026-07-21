package com.menupilot.restaurant.data

import com.menupilot.domain.MenuItemId
import com.menupilot.domain.VariantId

enum class CatalogIntegrityIssueCode {
    DUPLICATE_PRESENTATION,
    MISSING_PRESENTATION,
    UNKNOWN_PRESENTATION,
    PRICE_MISMATCH,
    RECIPE_REVISION_MISMATCH,
}

data class CatalogIntegrityIssue(
    val code: CatalogIntegrityIssueCode,
    val itemId: String,
    val variantId: String,
)

class CatalogIntegrityPolicy {

    fun validate(catalog: RestaurantMenuCatalog): List<CatalogIntegrityIssue> {
        val presentations = catalog.dishes.groupBy { DishKey(it.id, it.variantId) }
        val variants = catalog.snapshot.items.flatMap { item ->
            item.variants.map { variant ->
                DishKey(item.id, variant.id) to variant
            }
        }.toMap()

        return buildList {
            presentations.filterValues { it.size > 1 }.keys
                .sortedBy(DishKey::stableId)
                .forEach { add(it.issue(CatalogIntegrityIssueCode.DUPLICATE_PRESENTATION)) }

            variants.keys.sortedBy(DishKey::stableId).forEach { key ->
                val dishes = presentations[key]
                if (dishes == null) {
                    add(key.issue(CatalogIntegrityIssueCode.MISSING_PRESENTATION))
                    return@forEach
                }
                if (dishes.size != 1) return@forEach
                val dish = dishes.single()
                val variant = requireNotNull(variants[key])
                if (dish.priceMinor != variant.priceMinor) {
                    add(key.issue(CatalogIntegrityIssueCode.PRICE_MISMATCH))
                }
                if (dish.recipeRevision != variant.recipeRevision) {
                    add(key.issue(CatalogIntegrityIssueCode.RECIPE_REVISION_MISMATCH))
                }
            }

            presentations.keys
                .filterNot(variants::containsKey)
                .sortedBy(DishKey::stableId)
                .forEach { add(it.issue(CatalogIntegrityIssueCode.UNKNOWN_PRESENTATION)) }
        }.distinct()
    }

    private data class DishKey(
        val itemId: MenuItemId,
        val variantId: VariantId,
    ) {
        fun stableId(): String = "${itemId.value}:${variantId.value}"

        fun issue(code: CatalogIntegrityIssueCode) = CatalogIntegrityIssue(
            code = code,
            itemId = itemId.value,
            variantId = variantId.value,
        )
    }
}
