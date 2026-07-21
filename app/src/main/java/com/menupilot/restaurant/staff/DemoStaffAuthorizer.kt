package com.menupilot.restaurant.staff

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local prototype boundary for the future restaurant staff-auth service.
 *
 * Production builds must replace this fixture with per-venue credentials and an auditable staff
 * identity provider. The demo credential is documented in the repository README for testing.
 */
@Singleton
class DemoStaffAuthorizer @Inject constructor() : StaffAuthorizer {
    override fun authorize(pin: String): StaffIdentity? =
        if (pin == DEMO_STAFF_PIN) {
            StaffIdentity(id = "demo-shift-lead", displayName = "Demo shift lead")
        } else {
            null
        }

    private companion object {
        const val DEMO_STAFF_PIN = "2468"
    }
}
