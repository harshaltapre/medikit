package com.smartmed.dispenser.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val apiKey: String = "",
    val apiKeyStatus: String = "active",
    val apiKeyCreatedAt: Long = 0L
)

data class DeviceState(
    val deviceId: String = "dispenser-01",
    val status: String = "offline",
    val lastSeen: Timestamp? = null,
    val dispensingStatus: String = "idle"
) {
    val isOnline: Boolean
        get() {
            val ts = lastSeen ?: return status == "online"
            val ageMs = System.currentTimeMillis() - ts.toDate().time
            return status == "online" && ageMs <= 120_000L
        }
}

data class Compartment(
    val id: Int = 1,
    val medicineName: String = "",
    val dosage: String = "250mg",
    val pillForm: String = "Capsules",   // "Tablets", "Capsules", "Drops"
    val colorHex: Long = 0xFFE8B968L,
    val initialQuantity: Int = 0,
    val remainingQuantity: Int = 0,
    val lowStockLimit: Int = 2
) {
    val isLowStock: Boolean
        get() = remainingQuantity <= lowStockLimit
}

data class MedicineSchedule(
    val id: String = "",
    val compartmentId: Int = 1,
    val time: String = "09:00",
    val tabletsPerDose: Int = 1,
    val enabled: Boolean = true,
    val activeDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    val pillForm: String = "Capsules",
    val colorHex: Long = 0xFFE8B968L
)

data class DispensingEvent(
    val id: String = "",
    val compartmentId: Int = 1,
    val medicineName: String = "",
    val scheduledQuantity: Int = 1,
    val detectedQuantity: Int = 0,
    val timestamp: Timestamp? = null,
    val status: String = "completed"
)

data class RefillEvent(
    val id: String = "",
    val compartmentId: Int = 1,
    val medicineName: String = "",
    val quantityAdded: Int = 0,
    val timestamp: Timestamp? = null
)
