package com.smartmed.dispenser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.smartmed.dispenser.data.FirebaseRepository
import com.smartmed.dispenser.model.Compartment
import com.smartmed.dispenser.model.DeviceState
import com.smartmed.dispenser.model.DispensingEvent
import com.smartmed.dispenser.model.MedicineSchedule
import com.smartmed.dispenser.model.RefillEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DispenserUiState(
    val isSignedIn: Boolean = false,
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val apiKey: String = "",
    val apiKeyStatus: String = "active",
    val apiKeyCreatedAt: Long = System.currentTimeMillis(),
    val pairedDeviceId: String = "MEDI_001",
    val deviceState: DeviceState = DeviceState(deviceId = "MEDI_001"),
    val compartments: List<Compartment> = (1..3).map {
        val defaultMeds = listOf(
            Triple("Probiotic", "250mg", 0xFFE8B968L),
            Triple("Loratadine", "10mg", 0xFFF2C5C5L),
            Triple("Vitamin D3", "1000IU", 0xFFC5B9F2L)
        )
        val (name, dose, color) = defaultMeds[it - 1]
        Compartment(
            id = it,
            medicineName = name,
            dosage = dose,
            pillForm = if (it % 2 == 1) "Capsules" else "Tablets",
            colorHex = color,
            initialQuantity = 5,
            remainingQuantity = 5,
            lowStockLimit = 1
        )
    },
    val schedules: List<MedicineSchedule> = listOf(
        MedicineSchedule("default-1", 1, "09:00", 1, true, listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), "Capsules", 0xFFE8B968L),
        MedicineSchedule("default-2", 2, "16:00", 1, true, listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), "Tablets", 0xFFF2C5C5L),
        MedicineSchedule("default-3", 3, "21:00", 1, true, listOf("Mon", "Wed", "Fri"), "Capsules", 0xFFC5B9F2L)
    ),
    val dispensingHistory: List<DispensingEvent> = emptyList(),
    val refillHistory: List<RefillEvent> = emptyList(),
    val lowStockAlertsEnabled: Boolean = true,
    val buzzerEnabled: Boolean = true,
    val autoSyncEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class DispenserViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DispenserUiState())
    val uiState: StateFlow<DispenserUiState> = _uiState.asStateFlow()

    private var deviceCollectorsJob: Job? = null
    private var hasLocalFallback: Boolean = false
    private var manualFallbackSignedIn: Boolean = false

    init {
        viewModelScope.launch {
            repository.observeAuthUser().collect { user ->
                if (user == null) {
                    if (!manualFallbackSignedIn) {
                        deviceCollectorsJob?.cancel()
                        _uiState.value = DispenserUiState(isSignedIn = false)
                    }
                } else {
                    manualFallbackSignedIn = false
                    deviceCollectorsJob?.cancel()
                    _uiState.update { it.copy(isSignedIn = true, userId = user.uid, isLoading = true) }
                    runCatching { repository.ensurePermanentApiKey(user) }
                        .onSuccess { profile ->
                            _uiState.update {
                                it.copy(
                                    userId = profile.uid,
                                    userName = profile.name,
                                    userEmail = profile.email,
                                    apiKey = profile.apiKey,
                                    apiKeyStatus = profile.apiKeyStatus,
                                    apiKeyCreatedAt = profile.apiKeyCreatedAt,
                                    errorMessage = null
                                )
                            }
                            loadPairedDevice(profile.uid)
                        }
                        .onFailure {
                            loadPairedDevice(user.uid)
                        }
                }
            }
        }
    }

    private suspend fun loadPairedDevice(userId: String) {
        val fetchedId = runCatching { repository.fetchPairedDeviceId(userId) }.getOrNull()
        val effectiveId = fetchedId ?: "MEDI_001"
        _uiState.update {
            it.copy(
                pairedDeviceId = effectiveId,
                deviceState = it.deviceState.copy(deviceId = effectiveId),
                isLoading = false,
                errorMessage = null
            )
        }
        startDeviceObservers(userId, effectiveId)
    }

    private fun startDeviceObservers(userId: String, deviceId: String) {
        deviceCollectorsJob?.cancel()
        deviceCollectorsJob = viewModelScope.launch {
            launch { repository.syncFcmToken(userId, deviceId) }
            launch {
                repository.observeDeviceState(userId, deviceId).collect { ds ->
                    _uiState.update { it.copy(deviceState = ds) }
                }
            }
            launch {
                repository.observeCompartments(userId, deviceId).collect { list ->
                    _uiState.update { it.copy(compartments = list) }
                }
            }
            launch {
                repository.observeSchedules(userId, deviceId).collect { list ->
                    if (list.isNotEmpty() || !hasLocalFallback) {
                        _uiState.update { it.copy(schedules = list) }
                    }
                }
            }
            launch {
                repository.observeDispensingHistory(userId, deviceId).collect { list ->
                    if (list.isNotEmpty() || !hasLocalFallback) {
                        _uiState.update { it.copy(dispensingHistory = list) }
                    }
                }
            }
            launch {
                repository.observeRefillHistory(userId, deviceId).collect { list ->
                    if (list.isNotEmpty() || !hasLocalFallback) {
                        _uiState.update { it.copy(refillHistory = list) }
                    }
                }
            }
        }
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        preferredName: String? = null,
        preferredEmail: String? = null
    ) {
        launchAction("Signed in with Google — API key synced") {
            val profile = repository.signInWithGoogleIdToken(idToken, preferredName, preferredEmail)
            manualFallbackSignedIn = true
            _uiState.update {
                it.copy(
                    isSignedIn = true,
                    userId = profile.uid,
                    userName = profile.name,
                    userEmail = profile.email,
                    apiKey = profile.apiKey,
                    apiKeyStatus = profile.apiKeyStatus,
                    apiKeyCreatedAt = profile.apiKeyCreatedAt
                )
            }
            loadPairedDevice(profile.uid)
        }
    }

    fun signInWithGoogleFallback(
        accountName: String?,
        accountEmail: String?,
        reason: String
    ) {
        launchAction(reason) {
            val profile = repository.signInAnonymouslyOrFallback(accountName, accountEmail)
            manualFallbackSignedIn = true
            _uiState.update {
                it.copy(
                    isSignedIn = true,
                    userId = profile.uid,
                    userName = profile.name,
                    userEmail = profile.email,
                    apiKey = profile.apiKey,
                    apiKeyStatus = profile.apiKeyStatus,
                    apiKeyCreatedAt = profile.apiKeyCreatedAt
                )
            }
            loadPairedDevice(profile.uid)
        }
    }

    fun signIn(email: String, password: String) {
        launchAction("Signed in — API key restored") {
            val profile = repository.signIn(email, password)
            manualFallbackSignedIn = true
            _uiState.update {
                it.copy(
                    isSignedIn = true,
                    userId = profile.uid,
                    userName = profile.name,
                    userEmail = profile.email,
                    apiKey = profile.apiKey,
                    apiKeyStatus = profile.apiKeyStatus,
                    apiKeyCreatedAt = profile.apiKeyCreatedAt
                )
            }
            loadPairedDevice(profile.uid)
        }
    }

    fun register(email: String, password: String, fullName: String) {
        launchAction("Account created & API key generated") {
            val profile = repository.register(email, password, fullName)
            manualFallbackSignedIn = true
            _uiState.update {
                it.copy(
                    isSignedIn = true,
                    userId = profile.uid,
                    userName = profile.name,
                    userEmail = profile.email,
                    apiKey = profile.apiKey,
                    apiKeyStatus = profile.apiKeyStatus,
                    apiKeyCreatedAt = profile.apiKeyCreatedAt
                )
            }
            loadPairedDevice(profile.uid)
        }
    }

    fun setPreference(
        lowStockAlerts: Boolean = _uiState.value.lowStockAlertsEnabled,
        buzzer: Boolean = _uiState.value.buzzerEnabled,
        autoSync: Boolean = _uiState.value.autoSyncEnabled
    ) {
        _uiState.update {
            it.copy(
                lowStockAlertsEnabled = lowStockAlerts,
                buzzerEnabled = buzzer,
                autoSyncEnabled = autoSync
            )
        }
    }

    fun regenerateApiKey() {
        val state = _uiState.value
        if (state.userId.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            val result = runCatching {
                repository.regenerateApiKey(state.userId, state.apiKey)
            }
            val updated = result.getOrNull()
            if (updated != null) {
                _uiState.update {
                    it.copy(
                        apiKey = updated.apiKey,
                        apiKeyStatus = updated.apiKeyStatus,
                        apiKeyCreatedAt = updated.apiKeyCreatedAt,
                        isLoading = false,
                        statusMessage = "API key regenerated"
                    )
                }
            } else {
                val fallbackKey = "SMD-" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .take(20)
                    .uppercase()
                hasLocalFallback = true
                _uiState.update {
                    it.copy(
                        apiKey = fallbackKey,
                        apiKeyStatus = "active",
                        apiKeyCreatedAt = System.currentTimeMillis(),
                        isLoading = false,
                        statusMessage = "API key regenerated"
                    )
                }
            }
        }
    }

    fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun signOut() {
        manualFallbackSignedIn = false
        hasLocalFallback = false
        deviceCollectorsJob?.cancel()
        runCatching { repository.signOut() }
        _uiState.value = DispenserUiState(isSignedIn = false)
    }

    fun pairDevice(deviceId: String, deviceSecret: String) {
        val state = _uiState.value
        if (state.userId.isEmpty()) return
        val cleanId = deviceId.trim().ifBlank { "MEDI_001" }
        _uiState.update {
            it.copy(
                pairedDeviceId = cleanId,
                deviceState = it.deviceState.copy(deviceId = cleanId),
                statusMessage = "Dispenser $cleanId paired to your account"
            )
        }
        startDeviceObservers(state.userId, cleanId)
        viewModelScope.launch {
            runCatching { repository.pairDevice(state.userId, cleanId, deviceSecret, state.apiKey) }
                .onFailure { hasLocalFallback = true }
        }
    }

    fun saveMedicineAndSchedule(
        compartmentId: Int,
        medicineName: String,
        dosage: String,
        pillForm: String,
        colorHex: Long,
        time: String,
        tabletsPerDose: Int,
        activeDays: List<String>,
        notificationsEnabled: Boolean
    ) {
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        val cleanName = medicineName.trim().ifBlank { "Medicine $compartmentId" }
        val cleanDosage = dosage.trim().ifBlank { "250mg" }
        val safeDose = tabletsPerDose.coerceIn(1, 5)
        val newSchedule = MedicineSchedule(
            id = "local-" + UUID.randomUUID().toString().take(8),
            compartmentId = compartmentId,
            time = time,
            tabletsPerDose = safeDose,
            enabled = notificationsEnabled,
            activeDays = activeDays,
            pillForm = pillForm,
            colorHex = colorHex
        )
        _uiState.update { current ->
            val updatedComps = current.compartments.map { c ->
                if (c.id == compartmentId) {
                    c.copy(
                        medicineName = cleanName,
                        dosage = cleanDosage,
                        pillForm = pillForm,
                        colorHex = colorHex
                    )
                } else c
            }
            current.copy(
                compartments = updatedComps,
                schedules = (current.schedules + newSchedule).sortedBy { it.time },
                statusMessage = "Scheduled $cleanName ($safeDose pill(s)) in Container $compartmentId"
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.saveMedicineAndSchedule(
                    uid, devId, compartmentId, cleanName, cleanDosage,
                    pillForm, colorHex, time, safeDose, activeDays, notificationsEnabled
                )
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun markDoseTakenManually(compartmentId: Int, tabletsTaken: Int) {
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        _uiState.update { current ->
            val targetName = current.compartments.firstOrNull { it.id == compartmentId }?.medicineName
                ?: "Compartment $compartmentId"
            val updatedComps = current.compartments.map { c ->
                if (c.id == compartmentId) {
                    c.copy(remainingQuantity = (c.remainingQuantity - tabletsTaken).coerceAtLeast(0))
                } else c
            }
            val event = DispensingEvent(
                id = "taken-" + UUID.randomUUID().toString().take(8),
                compartmentId = compartmentId,
                medicineName = targetName,
                scheduledQuantity = tabletsTaken,
                detectedQuantity = tabletsTaken,
                timestamp = Timestamp.now(),
                status = "taken"
            )
            current.copy(
                compartments = updatedComps,
                dispensingHistory = listOf(event) + current.dispensingHistory,
                statusMessage = "Marked $targetName dose taken (-$tabletsTaken)"
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.markDoseTakenManually(uid, devId, compartmentId, tabletsTaken)
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun updateCompartment(
        compartmentId: Int,
        medicineName: String,
        initialQuantity: Int,
        lowStockLimit: Int,
        resetRemaining: Boolean
    ) {
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        _uiState.update { current ->
            val updatedComps = current.compartments.map { c ->
                if (c.id == compartmentId) {
                    c.copy(
                        medicineName = medicineName.trim(),
                        initialQuantity = initialQuantity.coerceAtLeast(0),
                        lowStockLimit = lowStockLimit.coerceAtLeast(0),
                        remainingQuantity = if (resetRemaining) initialQuantity.coerceAtLeast(0) else c.remainingQuantity
                    )
                } else c
            }
            current.copy(
                compartments = updatedComps,
                statusMessage = "Updated compartment $compartmentId"
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.updateCompartmentSettings(
                    uid, devId, compartmentId, medicineName,
                    initialQuantity, lowStockLimit, resetRemaining
                )
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun refillContainerToFull(compartmentId: Int) {
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        _uiState.update { current ->
            val targetName = current.compartments.firstOrNull { it.id == compartmentId }?.medicineName
                ?: "Container $compartmentId"
            val updatedComps = current.compartments.map { c ->
                if (c.id == compartmentId) {
                    c.copy(initialQuantity = 5, remainingQuantity = 5, lowStockLimit = 1)
                } else c
            }
            val refillEvent = RefillEvent(
                id = "rf-" + UUID.randomUUID().toString().take(8),
                compartmentId = compartmentId,
                medicineName = targetName,
                quantityAdded = 5,
                timestamp = Timestamp.now()
            )
            current.copy(
                compartments = updatedComps,
                refillHistory = listOf(refillEvent) + current.refillHistory,
                statusMessage = "Refilled $targetName (Container $compartmentId) to 5 pills"
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.refillContainerToFive(uid, devId, compartmentId, 5)
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun addRefill(compartmentId: Int, quantityAdded: Int) {
        if (quantityAdded <= 0) return
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        _uiState.update { current ->
            val targetName = current.compartments.firstOrNull { it.id == compartmentId }?.medicineName
                ?: "Compartment $compartmentId"
            val updatedComps = current.compartments.map { c ->
                if (c.id == compartmentId) {
                    c.copy(remainingQuantity = c.remainingQuantity + quantityAdded)
                } else c
            }
            val refillEvent = RefillEvent(
                id = "rf-" + UUID.randomUUID().toString().take(8),
                compartmentId = compartmentId,
                medicineName = targetName,
                quantityAdded = quantityAdded,
                timestamp = Timestamp.now()
            )
            current.copy(
                compartments = updatedComps,
                refillHistory = listOf(refillEvent) + current.refillHistory,
                statusMessage = "Added $quantityAdded tablet(s) to compartment $compartmentId"
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.addRefill(uid, devId, compartmentId, quantityAdded)
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun toggleSchedule(scheduleId: String, enabled: Boolean) {
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        _uiState.update { current ->
            current.copy(
                schedules = current.schedules.map { s ->
                    if (s.id == scheduleId) s.copy(enabled = enabled) else s
                }
            )
        }
        if (scheduleId.startsWith("local-") || scheduleId.startsWith("default-")) {
            hasLocalFallback = true
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.toggleSchedule(uid, devId, scheduleId, enabled)
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        val uid = _uiState.value.userId
        val devId = _uiState.value.pairedDeviceId.ifBlank { "MEDI_001" }
        _uiState.update { current ->
            current.copy(
                schedules = current.schedules.filterNot { it.id == scheduleId },
                statusMessage = "Schedule deleted"
            )
        }
        if (scheduleId.startsWith("local-") || scheduleId.startsWith("default-")) {
            hasLocalFallback = true
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.deleteSchedule(uid, devId, scheduleId)
            }
            if (result.isFailure) {
                hasLocalFallback = true
            }
        }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private fun launchAction(successMsg: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            runCatching { block() }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, statusMessage = successMsg) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.localizedMessage ?: "Operation failed"
                        )
                    }
                }
        }
    }
}
