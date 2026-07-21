package com.menupilot.restaurant.staff

data class StaffIdentity(
    val id: String,
    val displayName: String,
)

interface StaffAuthorizer {
    fun authorize(pin: String): StaffIdentity?
}
