package com.menupilot.restaurant.data

import com.menupilot.domain.AllergenFact
import com.menupilot.domain.AllergenId
import com.menupilot.domain.ApprovedPairing
import com.menupilot.domain.ApprovedPairingReason
import com.menupilot.domain.BasketAffinitySignal
import com.menupilot.domain.Conformance
import com.menupilot.domain.CrossContact
import com.menupilot.domain.DietId
import com.menupilot.domain.MenuItem
import com.menupilot.domain.MenuItemId
import com.menupilot.domain.MenuSnapshot
import com.menupilot.domain.MenuVariantRef
import com.menupilot.domain.MenuVariant
import com.menupilot.domain.MerchandisingEvidenceSource
import com.menupilot.domain.MerchandisingEvidenceSourceKind
import com.menupilot.domain.MerchandisingSnapshot
import com.menupilot.domain.Presence
import com.menupilot.domain.SalesSignal
import com.menupilot.domain.VariantId
import java.time.Duration
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FixtureMenuRepository @Inject constructor(
    clock: Clock,
) : MenuRepository {

    private val verifiedAt = clock.instant()

    private val peanut = AllergenId("peanut")
    private val shellfish = AllergenId("shellfish")
    private val gluten = AllergenId("gluten")
    private val vegetarian = DietId("vegetarian")
    private val demoPosSource = MerchandisingEvidenceSource(
        id = "demo-pos",
        displayName = "Demo POS sales record",
        kind = MerchandisingEvidenceSourceKind.DEMO_FIXTURE,
    )
    private val demoSalesCounts = mapOf(
        "chili_lime_tofu" to 186L,
        "pumpkin_herb_salad" to 132L,
        "miso_eggplant" to 98L,
        "crispy_cauliflower" to 154L,
        "garden_kare_kare" to 171L,
        "mushroom_adobo" to 143L,
        "chicken_inasal" to 211L,
        "coconut_sago" to 121L,
        "garlic_rice" to 268L,
        "calamansi_iced_tea" to 244L,
    )

    private val presentations = listOf(
        MenuDish(
            id = MenuItemId("chili_lime_tofu"),
            variantId = VariantId("standard"),
            recipeRevision = "chili_lime_tofu-recipe-1",
            name = "Chili-Lime Tofu Bowl",
            description = "Charred tofu, brown rice, pickled vegetables, herbs, and a bright chili-lime dressing.",
            category = DishCategory.MAIN,
            priceMinor = 42000,
            ingredients = listOf("Tofu", "Brown rice", "Cucumber", "Pickled carrot", "Chili", "Lime"),
            listedAllergens = listOf("Soy"),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Spicy", "Light"),
            preparationNote = "Prepared to order on a cleaned station; kitchen acknowledgment required for allergy orders.",
            photoAccent = 0xFFD76445,
            photoSymbol = "🌶️",
            prepMinutes = 14,
        ),
        MenuDish(
            id = MenuItemId("pumpkin_herb_salad"),
            variantId = VariantId("standard"),
            recipeRevision = "pumpkin_herb_salad-recipe-1",
            name = "Roasted Pumpkin Herb Salad",
            description = "Roasted squash, leafy herbs, citrus, toasted seeds, and coconut vinaigrette.",
            category = DishCategory.MAIN,
            priceMinor = 36000,
            ingredients = listOf("Pumpkin", "Mixed greens", "Sunflower seed", "Orange", "Coconut"),
            listedAllergens = listOf("Seeds"),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Light", "Citrusy"),
            preparationNote = "Cold station preparation. Ask staff about current seed suppliers.",
            photoAccent = 0xFFE19A3B,
            photoSymbol = "🥗",
            prepMinutes = 10,
        ),
        MenuDish(
            id = MenuItemId("miso_eggplant"),
            variantId = VariantId("standard"),
            recipeRevision = "miso_eggplant-recipe-1",
            name = "Fire-Roasted Miso Eggplant",
            description = "Smoky eggplant, red miso glaze, spring onion, chili oil, and steamed rice.",
            category = DishCategory.MAIN,
            priceMinor = 48000,
            ingredients = listOf("Eggplant", "Miso", "Rice", "Spring onion", "Chili oil"),
            listedAllergens = listOf("Soy"),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Spicy", "Smoky"),
            preparationNote = "Current peanut cross-contact record is incomplete; staff must check before ordering.",
            photoAccent = 0xFF79506F,
            photoSymbol = "🍆",
            prepMinutes = 16,
        ),
        MenuDish(
            id = MenuItemId("crispy_cauliflower"),
            variantId = VariantId("standard"),
            recipeRevision = "crispy_cauliflower-recipe-1",
            name = "Crispy Chili Cauliflower",
            description = "Crisp cauliflower florets, calamansi, herbs, and fermented chili sauce.",
            category = DishCategory.STARTER,
            priceMinor = 29000,
            ingredients = listOf("Cauliflower", "Rice flour", "Calamansi", "Chili sauce"),
            listedAllergens = emptyList(),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Spicy", "Crisp"),
            preparationNote = "The fryer has a reported possible peanut cross-contact risk.",
            photoAccent = 0xFFB57231,
            photoSymbol = "🥦",
            prepMinutes = 12,
        ),
        MenuDish(
            id = MenuItemId("garden_kare_kare"),
            variantId = VariantId("standard"),
            recipeRevision = "garden_kare_kare-recipe-1",
            name = "Garden Kare-Kare",
            description = "Seasonal vegetables in a traditional roasted peanut sauce with bagoong-style mushroom relish.",
            category = DishCategory.MAIN,
            priceMinor = 45000,
            ingredients = listOf("Eggplant", "Long beans", "Bok choy", "Roasted peanut sauce"),
            listedAllergens = listOf("Peanut"),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Rich", "Savory"),
            preparationNote = "Contains peanut as a recipe ingredient.",
            photoAccent = 0xFFB98836,
            photoSymbol = "🥜",
            prepMinutes = 18,
        ),
        MenuDish(
            id = MenuItemId("mushroom_adobo"),
            variantId = VariantId("standard"),
            recipeRevision = "mushroom_adobo-recipe-1",
            name = "Mushroom Adobo Rice",
            description = "Soy-braised mushrooms, garlic rice, pepper, greens, and crispy shallots.",
            category = DishCategory.MAIN,
            priceMinor = 39000,
            ingredients = listOf("Mushroom", "Garlic rice", "Soy", "Black pepper", "Greens"),
            listedAllergens = listOf("Soy", "Gluten"),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Savory", "Garlicky"),
            preparationNote = "No peanut listed in the current recipe; staff confirmation is still required.",
            photoAccent = 0xFF6E5945,
            photoSymbol = "🍄",
            prepMinutes = 13,
        ),
        MenuDish(
            id = MenuItemId("chicken_inasal"),
            variantId = VariantId("standard"),
            recipeRevision = "chicken_inasal-recipe-1",
            name = "Chicken Inasal Plate",
            description = "Calamansi-annatto grilled chicken, garlic rice, pickled papaya, and chili vinegar.",
            category = DishCategory.MAIN,
            priceMinor = 52000,
            ingredients = listOf("Chicken", "Garlic rice", "Calamansi", "Annatto", "Papaya"),
            listedAllergens = emptyList(),
            dietaryTags = emptyList(),
            flavorTags = listOf("Smoky", "Tangy"),
            preparationNote = "Not compatible with a vegetarian request.",
            photoAccent = 0xFFC76536,
            photoSymbol = "🍗",
            prepMinutes = 20,
        ),
        MenuDish(
            id = MenuItemId("coconut_sago"),
            variantId = VariantId("standard"),
            recipeRevision = "coconut_sago-recipe-1",
            name = "Mango Coconut Sago",
            description = "Fresh mango, coconut cream, sago pearls, and calamansi granita.",
            category = DishCategory.DESSERT,
            priceMinor = 24000,
            ingredients = listOf("Mango", "Coconut", "Sago", "Calamansi"),
            listedAllergens = emptyList(),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Light", "Sweet"),
            preparationNote = "Prepared in the dessert station; verify current garnish before an allergy order.",
            photoAccent = 0xFFF0B84E,
            photoSymbol = "🥭",
            prepMinutes = 8,
        ),
        MenuDish(
            id = MenuItemId("garlic_rice"),
            variantId = VariantId("small"),
            recipeRevision = "garlic_rice-recipe-1",
            name = "Garlic Rice",
            description = "Steamed rice tossed with roasted garlic and spring onion.",
            category = DishCategory.SIDE,
            priceMinor = 6500,
            ingredients = listOf("Rice", "Garlic", "Spring onion", "Rice bran oil"),
            listedAllergens = emptyList(),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Savory", "Quick"),
            preparationNote = "Prepared in the rice station; verify current garnish before an allergy order.",
            photoAccent = 0xFFE4C45E,
            photoSymbol = "🍚",
            prepMinutes = 5,
        ),
        MenuDish(
            id = MenuItemId("calamansi_iced_tea"),
            variantId = VariantId("regular"),
            recipeRevision = "calamansi_iced_tea-recipe-1",
            name = "Calamansi Iced Tea",
            description = "Fresh calamansi, brewed black tea, mint, and light cane syrup.",
            category = DishCategory.DRINK,
            priceMinor = 9500,
            ingredients = listOf("Calamansi", "Black tea", "Mint", "Cane syrup"),
            listedAllergens = emptyList(),
            dietaryTags = listOf("Vegetarian"),
            flavorTags = listOf("Refreshing", "Light"),
            preparationNote = "Made at the beverage station with shared utensils; ask staff about current handling.",
            photoAccent = 0xFFCBD69A,
            photoSymbol = "🍹",
            prepMinutes = 3,
        ),
    )

    private val snapshot = MenuSnapshot(
        revision = "demo-menu-2026-07-19.1",
        generatedAt = verifiedAt,
        items = presentations.map(::domainItem),
    )

    private val merchandisingSnapshot = MerchandisingSnapshot(
        menuRevision = snapshot.revision,
        revision = "demo-merchandising-2026-07-19.1",
        evidenceSources = listOf(demoPosSource),
        approvedPairings = buildList {
            listOf(
                "chili_lime_tofu",
                "pumpkin_herb_salad",
                "miso_eggplant",
                "mushroom_adobo",
                "chicken_inasal",
            ).forEach { anchorId ->
                add(
                    ApprovedPairing(
                        anchor = variantRef(anchorId),
                        candidate = variantRef("calamansi_iced_tea"),
                        reason = ApprovedPairingReason.COMPLEMENTS_DISH,
                    ),
                )
                add(
                    ApprovedPairing(
                        anchor = variantRef(anchorId),
                        candidate = variantRef("coconut_sago"),
                        reason = ApprovedPairingReason.COMPLETES_MEAL,
                    ),
                )
            }
            add(
                ApprovedPairing(
                    anchor = variantRef("crispy_cauliflower"),
                    candidate = variantRef("calamansi_iced_tea"),
                    reason = ApprovedPairingReason.COMPLEMENTS_DISH,
                ),
            )
        },
        salesSignals = presentations.map { dish ->
            SalesSignal(
                variant = variantRef(dish.id.value),
                orderCount = requireNotNull(demoSalesCounts[dish.id.value]),
                windowStart = verifiedAt.minus(Duration.ofDays(7)),
                windowEnd = verifiedAt,
                observedAt = verifiedAt,
                sourceId = demoPosSource.id,
            )
        },
        affinitySignals = listOf(
            affinity("chili_lime_tofu", "calamansi_iced_tea", 62),
            affinity("chili_lime_tofu", "coconut_sago", 31),
            affinity("pumpkin_herb_salad", "calamansi_iced_tea", 47),
            affinity("mushroom_adobo", "calamansi_iced_tea", 38),
            affinity("chicken_inasal", "calamansi_iced_tea", 71),
        ),
    )

    private val catalog = RestaurantMenuCatalog(
        restaurantName = "MenuPilot Demo Kitchen",
        snapshot = snapshot,
        merchandisingSnapshot = merchandisingSnapshot,
        dishes = presentations,
        googleMapsReviewUrl =
            "https://www.google.com/maps/search/?api=1&query=MenuPilot%20Demo%20Kitchen",
        tabletConfig = VenueTabletConfig(
            locationLabel = "Table 12",
            placement = ServicePlacement.TABLE,
            liveStaffChannelConnected = false,
        ),
    )

    override fun currentCatalog(): RestaurantMenuCatalog = catalog

    private fun variantRef(itemId: String): MenuVariantRef {
        val item = snapshot.items.single { it.id.value == itemId }
        val variant = item.variants.single()
        return MenuVariantRef(
            itemId = item.id,
            variantId = variant.id,
            recipeRevision = variant.recipeRevision,
        )
    }

    private fun affinity(
        anchorId: String,
        candidateId: String,
        coOrderCount: Long,
    ) = BasketAffinitySignal(
        anchor = variantRef(anchorId),
        candidate = variantRef(candidateId),
        coOrderCount = coOrderCount,
        windowStart = verifiedAt.minus(Duration.ofDays(7)),
        windowEnd = verifiedAt,
        observedAt = verifiedAt,
        sourceId = demoPosSource.id,
    )

    private fun domainItem(dish: MenuDish): MenuItem {
        val dietConformance = when (dish.id.value) {
            "chicken_inasal" -> Conformance.DOES_NOT_CONFORM
            else -> Conformance.CONFORMS
        }
        val attributes = buildSet {
            when (dish.id.value) {
                "chili_lime_tofu", "miso_eggplant", "crispy_cauliflower" -> add("spicy")
                else -> add("mild")
            }
            when (dish.id.value) {
                "chili_lime_tofu",
                "pumpkin_herb_salad",
                "miso_eggplant",
                "coconut_sago",
                "calamansi_iced_tea",
                -> add("light")
            }
            if (dish.prepMinutes <= 12) add("quick")
        }

        return MenuItem(
            id = dish.id,
            name = dish.name,
            variants = listOf(
                MenuVariant(
                    id = dish.variantId,
                    name = "Standard",
                    recipeRevision = dish.recipeRevision,
                    factsVerifiedAt = verifiedAt,
                    allergenFacts = allergenFactsFor(dish.id.value),
                    dietFacts = mapOf(vegetarian to dietConformance),
                    attributes = attributes,
                    priceMinor = dish.priceMinor,
                ),
            ),
        )
    }

    private fun allergenFactsFor(dishId: String): Map<AllergenId, AllergenFact> {
        val base = mutableMapOf(
            peanut to notListed(),
            shellfish to notListed(),
            gluten to notListed(),
        )
        when (dishId) {
            "garden_kare_kare" -> {
                base[peanut] = AllergenFact(Presence.PRESENT, CrossContact.KNOWN_RISK)
            }
            "crispy_cauliflower" -> {
                base[peanut] = AllergenFact(Presence.NOT_LISTED, CrossContact.POSSIBLE_RISK)
            }
            "miso_eggplant" -> {
                base.remove(peanut)
            }
            "mushroom_adobo" -> {
                base[gluten] = AllergenFact(Presence.PRESENT, CrossContact.NONE_REPORTED)
            }
        }
        return base
    }

    private fun notListed() = AllergenFact(
        ingredientPresence = Presence.NOT_LISTED,
        crossContact = CrossContact.NONE_REPORTED,
    )
}
