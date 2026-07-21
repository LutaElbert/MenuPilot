package com.menupilot.restaurant.feature.handoff

/**
 * Restaurant-owned context for a guest's temporary menu-discovery session.
 *
 * The default values keep the prototype source-compatible while making the eventual table,
 * counter, or QR session integration explicit.
 */
data class WaiterHandoffContext(
    val locationLabel: String = "Table 12",
    val sessionLabel: String = "Session active",
    val liveStaffChannelConnected: Boolean = false,
    val placement: HandoffPlacement = HandoffPlacement.TABLE,
)

enum class HandoffPlacement {
    TABLE,
    COUNTER,
}

enum class WaiterHandoffStatus {
    BUILDING_SHORTLIST,
    READY_TO_CALL,
    WAITER_CONFIRMATION_NEEDED,
    READY_TO_SHOW,
}
