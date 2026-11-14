package com.smartmed.dispenser.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.smartmed.dispenser.model.Compartment
import com.smartmed.dispenser.model.DeviceState
import com.smartmed.dispenser.model.DispensingEvent
import com.smartmed.dispenser.model.MedicineSchedule
import com.smartmed.dispenser.model.RefillEvent
import com.smartmed.dispenser.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun createMemoryFirestore(): FirebaseFirestore {
    val firestore = FirebaseFirestore.getInstance()
    runCatching {
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
        firestore.firestoreSettings = settings
    }
    return firestore
}

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = createMemoryFirestore(),
    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance("https://medkit-1-default-rtdb.firebaseio.com"),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthUser(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        preferredName: String? = null,
        preferredEmail: String? = null
    ): UserProfile {
        val firebaseResult = runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            result.user
        }.getOrNull()

        return if (firebaseResult != null) {
            ensurePermanentApiKey(firebaseResult, preferredName)
        } else {
            signInAnonymouslyOrFallback(preferredName, preferredEmail)
        }
    }

    suspend fun signIn(email: String, password: String): UserProfile {
        val cleanEmail = email.trim()
        val existingUser = runCatching {
            auth.signInWithEmailAndPassword(cleanEmail, password).await().user
        }.getOrNull()
        if (existingUser != null) {
            return ensurePermanentApiKey(existingUser)
        }

        val createdUser = runCatching {
            auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
        }.getOrNull()
        if (createdUser != null) {
            return ensurePermanentApiKey(createdUser)
        }

        val fallbackName = cleanEmail.substringBefore("@")
            .replaceFirstChar { it.uppercase(Locale.US) }
            .ifBlank { "Friend" }
        return signInAnonymouslyOrFallback(fallbackName, cleanEmail)
    }

    suspend fun register(email: String, password: String, fullName: String): UserProfile {
        val cleanEmail = email.trim()
        val cleanName = fullName.trim()

        val createdUser = runCatching {
            val user = auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
            if (user != null && cleanName.isNotEmpty()) {
                runCatching {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                    ).await()
                }
            }
            user
        }.getOrNull()
        if (createdUser != null) {
            return ensurePermanentApiKey(createdUser, cleanName)
        }

        val existingUser = runCatching {
            auth.signInWithEmailAndPassword(cleanEmail, password).await().user
        }.getOrNull()
        if (existingUser != null) {
            return ensurePermanentApiKey(existingUser, cleanName)
        }

        return signInAnonymouslyOrFallback(cleanName.ifBlank { null }, cleanEmail)
    }

    suspend fun signInAnonymouslyOrFallback(
        preferredName: String? = null,
        preferredEmail: String? = null
    ): UserProfile {
        val anonUser = runCatching { auth.signInAnonymously().await().user }.getOrNull()
        if (anonUser != null) {
            return ensurePermanentApiKey(anonUser, preferredName)
        }

        val seed = preferredEmail?.trim()?.takeIf { it.isNotEmpty() } ?: "medikit_local_user"
        val uid = "uid_" + UUID.nameUUIDFromBytes(seed.toByteArray()).toString().replace("-", "").take(20)
        val displayName = preferredName?.takeIf { it.isNotBlank() }
            ?: preferredEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase(Locale.US) }
            ?: "Lay"
        val email = preferredEmail?.takeIf { it.isNotBlank() } ?: "user@medikit.app"

        val rtdbKeySnap = runCatching { rtdb.getReference("users/$uid/apiKey").get().await() }.getOrNull()
        var apiKey = runCatching {
            rtdbKeySnap?.child("key")?.getValue(String::class.java)?.trim().orEmpty()
        }.getOrDefault("")
        if (apiKey.isEmpty()) {
            apiKey = generateSecureApiKey(uid = uid, isRegeneration = false)
        }
        val status = runCatching {
            rtdbKeySnap?.child("status")?.getValue(String::class.java)?.trim().orEmpty()
        }.getOrDefault("").ifEmpty { "active" }
        val createdAt = runCatching {
            rtdbKeySnap?.child("createdAt")?.getValue(Long::class.java) ?: 0L
        }.getOrDefault(0L).let { if (it > 0L) it else System.currentTimeMillis() }

        runCatching {
            rtdb.reference.updateChildren(
                mapOf(
                    "users/$uid/apiKey/key" to apiKey,
                    "users/$uid/apiKey/status" to status,
                    "users/$uid/apiKey/createdAt" to createdAt,
                    "users/$uid/profile/name" to displayName,
                    "users/$uid/profile/email" to email,
                    "users/$uid/profile/lastLoginAt" to ServerValue.TIMESTAMP,
                    "apiKeys/$apiKey/uid" to uid,
                    "apiKeys/$apiKey/email" to email,
                    "apiKeys/$apiKey/status" to status,
                    "apiKeys/$apiKey/createdAt" to createdAt
                )
            ).await()
        }

        return UserProfile(
            uid = uid,
            name = displayName,
            email = email,
            apiKey = apiKey,
            apiKeyStatus = status,
            apiKeyCreatedAt = createdAt
        )
    }

    /**
     * Retrieves the user's existing permanent API key from Firebase Realtime Database
     * (or Firestore fallback) so logging out and logging back in always assigns the
     * exact same API key to the user. Generates and stores one only on first login.
     */
    suspend fun ensurePermanentApiKey(
        user: FirebaseUser,
        preferredName: String? = null
    ): UserProfile {
        val uid = user.uid
        val displayName = preferredName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase(Locale.US) }
            ?: "User"
        val email = user.email ?: ""

        val userRtdbRef = rtdb.getReference("users").child(uid)
        val profileDocRef = db.collection("users").document(uid)
            .collection("profile").document("info")

        // 1. Check Realtime Database (/users/{uid}/apiKey) — supports both structured object & legacy string
        val rtdbKeySnap = runCatching { userRtdbRef.child("apiKey").get().await() }.getOrNull()
        val structuredKey = runCatching {
            rtdbKeySnap?.child("key")?.getValue(String::class.java)?.trim().orEmpty()
        }.getOrDefault("")
        val legacyKey = if (structuredKey.isEmpty()) {
            runCatching { rtdbKeySnap?.getValue(String::class.java)?.trim().orEmpty() }.getOrDefault("")
        } else ""

        var apiKey = structuredKey.ifEmpty { legacyKey }
        var apiKeyStatus = runCatching {
            rtdbKeySnap?.child("status")?.getValue(String::class.java)?.trim().orEmpty()
        }.getOrDefault("").ifEmpty { "active" }
        var createdAt = runCatching {
            rtdbKeySnap?.child("createdAt")?.getValue(Long::class.java) ?: 0L
        }.getOrDefault(0L)

        // 2. Fallback check in Firestore profile if RTDB had none
        if (apiKey.isEmpty() || createdAt == 0L) {
            val fsSnap = runCatching { profileDocRef.get().await() }.getOrNull()
            if (apiKey.isEmpty()) {
                apiKey = fsSnap?.getString("apiKey")?.trim().orEmpty()
                apiKeyStatus = fsSnap?.getString("apiKeyStatus")?.trim().orEmpty().ifEmpty { "active" }
            }
            if (createdAt == 0L) {
                createdAt = fsSnap?.getLong("apiKeyCreatedAt")
                    ?: fsSnap?.getTimestamp("createdAt")?.toDate()?.time
                    ?: 0L
            }
        }

        // 3. First-time user: generate idempotent persistent API key tied to Firebase UID
        val isNewKey = apiKey.isEmpty()
        if (isNewKey) {
            apiKey = generateSecureApiKey(uid = uid, isRegeneration = false)
            apiKeyStatus = "active"
        }
        if (createdAt == 0L) {
            createdAt = System.currentTimeMillis()
        }

        // 4. Persist structured API key & profile in Realtime Database
        val rtdbUpdates = mutableMapOf<String, Any>(
            "users/$uid/apiKey/key" to apiKey,
            "users/$uid/apiKey/status" to apiKeyStatus,
            "users/$uid/apiKey/createdAt" to createdAt,
            "users/$uid/profile/name" to displayName,
            "users/$uid/profile/email" to email,
            "users/$uid/profile/lastLoginAt" to ServerValue.TIMESTAMP,
            "apiKeys/$apiKey/uid" to uid,
            "apiKeys/$apiKey/email" to email,
            "apiKeys/$apiKey/status" to apiKeyStatus,
            "apiKeys/$apiKey/createdAt" to createdAt
        )
        runCatching { rtdb.reference.updateChildren(rtdbUpdates).await() }

        // 5. Mirror in Firestore profile (best-effort so uninitialized DB never blocks login)
        val fsProfile = mutableMapOf<String, Any>(
            "name" to displayName,
            "email" to email,
            "apiKey" to apiKey,
            "apiKeyStatus" to apiKeyStatus,
            "apiKeyCreatedAt" to createdAt,
            "lastLoginAt" to FieldValue.serverTimestamp()
        )
        if (isNewKey) {
            fsProfile["createdAt"] = FieldValue.serverTimestamp()
        }
        runCatching { profileDocRef.set(fsProfile, SetOptions.merge()).await() }

        return UserProfile(
            uid = uid,
            name = displayName,
            email = email,
            apiKey = apiKey,
            apiKeyStatus = apiKeyStatus,
            apiKeyCreatedAt = createdAt
        )
    }

    suspend fun regenerateApiKey(userId: String, currentApiKey: String): UserProfile {
        val user = auth.currentUser
        val displayName = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase(Locale.US) }
            ?: "User"
        val email = user?.email ?: ""

        val newKey = generateSecureApiKey(uid = userId, isRegeneration = true)
        val now = System.currentTimeMillis()

        val rtdbUpdates = mutableMapOf<String, Any>(
            "users/$userId/apiKey/key" to newKey,
            "users/$userId/apiKey/status" to "active",
            "users/$userId/apiKey/createdAt" to now,
            "apiKeys/$newKey/uid" to userId,
            "apiKeys/$newKey/email" to email,
            "apiKeys/$newKey/status" to "active",
            "apiKeys/$newKey/createdAt" to now
        )
        if (currentApiKey.isNotBlank() && currentApiKey != newKey) {
            rtdbUpdates["apiKeys/$currentApiKey/status"] = "revoked"
            rtdbUpdates["apiKeys/$currentApiKey/revokedAt"] = ServerValue.TIMESTAMP
        }
        runCatching { rtdb.reference.updateChildren(rtdbUpdates).await() }

        val profileDocRef = db.collection("users").document(userId)
            .collection("profile").document("info")
        runCatching {
            profileDocRef.set(
                mapOf(
                    "apiKey" to newKey,
                    "apiKeyStatus" to "active",
                    "apiKeyCreatedAt" to now,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }

        return UserProfile(
            uid = userId,
            name = displayName,
            email = email,
            apiKey = newKey,
            apiKeyStatus = "active",
            apiKeyCreatedAt = now
        )
    }

    private fun generateSecureApiKey(uid: String, isRegeneration: Boolean): String {
        if (!isRegeneration) {
            return "SMD-" + UUID.nameUUIDFromBytes(uid.toByteArray()).toString()
                .replace("-", "")
                .take(20)
                .uppercase(Locale.US)
        }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val sb = StringBuilder("SMD-")
        repeat(20) {
            sb.append(alphabet[random.nextInt(alphabet.length)])
        }
        return sb.toString()
    }

    fun signOut() {
        auth.signOut()
    }

    private fun Long?.toFirebaseTimestamp(): Timestamp? =
        this?.takeIf { it > 0L }?.let { Timestamp(Date(it)) }

    private fun defaultSpecFor(slot: Int): Triple<String, String, Long> {
        val specs = listOf(
            Triple("Probiotic", "250mg", 0xFFE8B968L),
            Triple("Loratadine", "10mg", 0xFFF2C5C5L),
            Triple("Vitamin D3", "1000IU", 0xFFC5B9F2L)
        )
        return specs[(slot - 1).coerceIn(0, specs.lastIndex)]
    }

    suspend fun fetchPairedDeviceId(userId: String): String? {
        val fsId = runCatching {
            db.collection("users").document(userId)
                .collection("devices").limit(1).get().await()
                .documents.firstOrNull()?.id
        }.getOrNull()
        if (!fsId.isNullOrBlank()) return fsId

        return runCatching {
            rtdb.getReference("users/$userId/device/deviceId").get().await()
                .getValue(String::class.java)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun pairDevice(
        userId: String,
        deviceId: String,
        deviceSecret: String,
        apiKey: String
    ) {
        val cleanId = deviceId.trim()
        val cleanSecret = deviceSecret.trim()
        require(cleanId.isNotEmpty() && cleanSecret.isNotEmpty()) {
            "Device ID and secret cannot be empty"
        }

        val registryRef = db.collection("deviceRegistry").document(cleanId)
        val deviceRef = db.collection("users").document(userId)
            .collection("devices").document(cleanId)

        val fsTxResult = runCatching {
            db.runTransaction { tx ->
                val regSnap = tx.get(registryRef)
                if (regSnap.exists()) {
                    val existingOwner = regSnap.getString("ownerUid")
                    if (!existingOwner.isNullOrEmpty() && existingOwner != userId) {
                        throw IllegalStateException("This dispenser is already paired to another owner account.")
                    }
                }

                tx.set(
                    registryRef,
                    mapOf(
                        "ownerUid" to userId,
                        "apiKey" to apiKey,
                        "pairedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                tx.set(
                    deviceRef,
                    mapOf(
                        "deviceSecret" to cleanSecret,
                        "apiKey" to apiKey,
                        "status" to "offline",
                        "dispensingStatus" to "idle",
                        "pairedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                null
            }.await()
        }
        fsTxResult.exceptionOrNull()?.let { err ->
            if (err is IllegalStateException) throw err
        }

        // Mirror pairing into Realtime Database under both /users/{uid}/device and /users/{uid}/devices/{deviceId}
        val rtdbResult = runCatching {
            rtdb.reference.updateChildren(
                mapOf(
                    "apiKeys/$apiKey/defaultDeviceId" to cleanId,
                    "users/$userId/device/deviceId" to cleanId,
                    "users/$userId/device/connectionStatus" to "offline",
                    "users/$userId/device/lastSeen" to ServerValue.TIMESTAMP,
                    "users/$userId/devices/$cleanId/deviceSecret" to cleanSecret,
                    "users/$userId/devices/$cleanId/apiKey" to apiKey
                )
            ).await()
        }
        if (fsTxResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsTxResult.exceptionOrNull()!!)
        }

        val rtdbCompRoot = rtdb.getReference("users/$userId/devices/$cleanId/compartments")
        val existingRtdbComps = runCatching { rtdbCompRoot.get().await() }.getOrNull()

        for (slot in 1..3) {
            val (med, dose, color) = defaultSpecFor(slot)
            val compPayload = mapOf(
                "medicineName" to med,
                "dosage" to dose,
                "pillForm" to if (slot % 2 == 1) "Capsules" else "Tablets",
                "colorHex" to color,
                "initialQuantity" to 5,
                "remainingQuantity" to 5,
                "lowStockLimit" to 1
            )

            runCatching {
                val compRef = deviceRef.collection("compartments").document("compartment$slot")
                val snap = compRef.get().await()
                if (!snap.exists()) {
                    compRef.set(compPayload).await()
                }
            }

            runCatching {
                val childExists = existingRtdbComps?.child("compartment$slot")?.exists() == true
                if (!childExists) {
                    rtdbCompRoot.child("compartment$slot").setValue(compPayload).await()
                }
            }
        }

        syncFcmToken(userId, cleanId)
    }

    suspend fun syncFcmToken(userId: String, deviceId: String) {
        runCatching {
            val token = messaging.token.await()
            if (token.isNotBlank()) {
                runCatching {
                    db.collection("users").document(userId)
                        .collection("devices").document(deviceId)
                        .set(mapOf("fcmToken" to token), SetOptions.merge())
                        .await()
                }
                runCatching {
                    rtdb.getReference("users/$userId/devices/$deviceId/fcmToken")
                        .setValue(token)
                        .await()
                }
            }
        }
    }

    fun observeDeviceState(userId: String, deviceId: String): Flow<DeviceState> = callbackFlow {
        var latestState = DeviceState(deviceId = deviceId)

        val reg: ListenerRegistration? = runCatching {
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .addSnapshotListener { snap, err ->
                    if (err != null) return@addSnapshotListener
                    if (snap != null && snap.exists()) {
                        latestState = latestState.copy(
                            deviceId = deviceId,
                            status = snap.getString("status") ?: latestState.status,
                            lastSeen = snap.getTimestamp("lastSeen") ?: latestState.lastSeen,
                            dispensingStatus = snap.getString("dispensingStatus") ?: latestState.dispensingStatus
                        )
                        trySend(latestState)
                    }
                }
        }.getOrNull()

        val rtdbDeviceRef = rtdb.getReference("users/$userId/device")
        val rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val id = snapshot.child("deviceId").getValue(String::class.java) ?: deviceId
                val connStatus = snapshot.child("connectionStatus").getValue(String::class.java)
                    ?: snapshot.child("status").getValue(String::class.java)
                    ?: latestState.status
                val lastSeenMs = snapshot.child("lastSeen").getValue(Long::class.java)
                val ts = lastSeenMs.toFirebaseTimestamp() ?: latestState.lastSeen

                latestState = latestState.copy(
                    deviceId = id,
                    status = connStatus,
                    lastSeen = ts
                )
                trySend(latestState)
            }

            override fun onCancelled(error: DatabaseError) {
                // Keep existing state if RTDB listener is restricted
            }
        }
        runCatching { rtdbDeviceRef.addValueEventListener(rtdbListener) }

        awaitClose {
            runCatching { reg?.remove() }
            runCatching { rtdbDeviceRef.removeEventListener(rtdbListener) }
        }
    }

    fun observeCompartments(userId: String, deviceId: String): Flow<List<Compartment>> = callbackFlow {
        val reg: ListenerRegistration? = runCatching {
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .collection("compartments")
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null || snap.isEmpty) return@addSnapshotListener
                    val byId = snap.documents.associateBy { it.id }
                    val list = (1..3).map { idx ->
                        val doc = byId["compartment$idx"]
                        val (defaultName, defaultDose, defaultColor) = defaultSpecFor(idx)
                        Compartment(
                            id = idx,
                            medicineName = doc?.getString("medicineName") ?: defaultName,
                            dosage = doc?.getString("dosage") ?: defaultDose,
                            pillForm = doc?.getString("pillForm")
                                ?: if (idx % 2 == 1) "Capsules" else "Tablets",
                            colorHex = doc?.getLong("colorHex") ?: defaultColor,
                            initialQuantity = (doc?.getLong("initialQuantity") ?: 5L).toInt(),
                            remainingQuantity = (doc?.getLong("remainingQuantity") ?: 5L).toInt(),
                            lowStockLimit = (doc?.getLong("lowStockLimit") ?: 1L).toInt()
                        )
                    }
                    trySend(list)
                }
        }.getOrNull()

        val rtdbCompRef = rtdb.getReference("users/$userId/devices/$deviceId/compartments")
        val rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val list = (1..3).map { idx ->
                    val child = snapshot.child("compartment$idx")
                    val (defaultName, defaultDose, defaultColor) = defaultSpecFor(idx)
                    Compartment(
                        id = idx,
                        medicineName = child.child("medicineName").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: defaultName,
                        dosage = child.child("dosage").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() } ?: defaultDose,
                        pillForm = child.child("pillForm").getValue(String::class.java)
                            ?.takeIf { it.isNotBlank() }
                            ?: if (idx % 2 == 1) "Capsules" else "Tablets",
                        colorHex = child.child("colorHex").getValue(Long::class.java) ?: defaultColor,
                        initialQuantity = (child.child("initialQuantity").getValue(Long::class.java) ?: 5L).toInt(),
                        remainingQuantity = (child.child("remainingQuantity").getValue(Long::class.java) ?: 5L).toInt(),
                        lowStockLimit = (child.child("lowStockLimit").getValue(Long::class.java) ?: 1L).toInt()
                    )
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        runCatching { rtdbCompRef.addValueEventListener(rtdbListener) }

        awaitClose {
            runCatching { reg?.remove() }
            runCatching { rtdbCompRef.removeEventListener(rtdbListener) }
        }
    }

    fun observeSchedules(userId: String, deviceId: String): Flow<List<MedicineSchedule>> = callbackFlow {
        val reg: ListenerRegistration? = runCatching {
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .collection("schedules")
                .orderBy("time")
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    val list = snap.documents.map { doc ->
                        val rawDays = doc.get("activeDays") as? List<*>
                        val days = rawDays?.mapNotNull { it as? String }?.takeIf { it.isNotEmpty() }
                            ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        MedicineSchedule(
                            id = doc.id,
                            compartmentId = (doc.getLong("compartmentId") ?: 1L).toInt(),
                            time = doc.getString("time") ?: "09:00",
                            tabletsPerDose = (doc.getLong("tabletsPerDose") ?: 1L).toInt(),
                            enabled = doc.getBoolean("enabled") ?: true,
                            activeDays = days,
                            pillForm = doc.getString("pillForm") ?: "Capsules",
                            colorHex = doc.getLong("colorHex") ?: 0xFFE8B968L
                        )
                    }
                    trySend(list)
                }
        }.getOrNull()

        val rtdbSchedRef = rtdb.getReference("users/$userId/devices/$deviceId/schedules")
        var rtdbHadData = false
        val rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    if (rtdbHadData) {
                        rtdbHadData = false
                        trySend(emptyList())
                    }
                    return
                }
                rtdbHadData = true
                val list = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    val days = child.child("activeDays").children
                        .mapNotNull { it.getValue(String::class.java) }
                        .takeIf { it.isNotEmpty() }
                        ?: listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    val compId = (child.child("compartmentId").getValue(Long::class.java) ?: 1L).toInt()
                    val (_, _, defaultColor) = defaultSpecFor(compId)
                    MedicineSchedule(
                        id = id,
                        compartmentId = compId,
                        time = child.child("time").getValue(String::class.java) ?: "09:00",
                        tabletsPerDose = (child.child("tabletsPerDose").getValue(Long::class.java) ?: 1L).toInt(),
                        enabled = child.child("enabled").getValue(Boolean::class.java) ?: true,
                        activeDays = days,
                        pillForm = child.child("pillForm").getValue(String::class.java) ?: "Capsules",
                        colorHex = child.child("colorHex").getValue(Long::class.java) ?: defaultColor
                    )
                }.sortedBy { it.time }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        runCatching { rtdbSchedRef.addValueEventListener(rtdbListener) }

        awaitClose {
            runCatching { reg?.remove() }
            runCatching { rtdbSchedRef.removeEventListener(rtdbListener) }
        }
    }

    fun observeDispensingHistory(userId: String, deviceId: String): Flow<List<DispensingEvent>> =
        callbackFlow {
            val reg: ListenerRegistration? = runCatching {
                db.collection("users").document(userId)
                    .collection("devices").document(deviceId)
                    .collection("dispensingHistory")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener { snap, err ->
                        if (err != null || snap == null) return@addSnapshotListener
                        val list = snap.documents.map { doc ->
                            DispensingEvent(
                                id = doc.id,
                                compartmentId = (doc.getLong("compartmentId") ?: 1L).toInt(),
                                medicineName = doc.getString("medicineName") ?: "",
                                scheduledQuantity = (doc.getLong("scheduledQuantity") ?: 1L).toInt(),
                                detectedQuantity = (doc.getLong("detectedQuantity") ?: 0L).toInt(),
                                timestamp = doc.getTimestamp("timestamp"),
                                status = doc.getString("status") ?: "completed"
                            )
                        }
                        trySend(list)
                    }
            }.getOrNull()

            val rtdbHistRef = rtdb.getReference("users/$userId/devices/$deviceId/dispensingHistory")
            val rtdbListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val list = snapshot.children.mapNotNull { child ->
                        val id = child.key ?: return@mapNotNull null
                        val tsMs = child.child("timestamp").getValue(Long::class.java)
                        DispensingEvent(
                            id = id,
                            compartmentId = (child.child("compartmentId").getValue(Long::class.java) ?: 1L).toInt(),
                            medicineName = child.child("medicineName").getValue(String::class.java) ?: "",
                            scheduledQuantity = (child.child("scheduledQuantity").getValue(Long::class.java) ?: 1L).toInt(),
                            detectedQuantity = (child.child("detectedQuantity").getValue(Long::class.java) ?: 0L).toInt(),
                            timestamp = tsMs.toFirebaseTimestamp(),
                            status = child.child("status").getValue(String::class.java) ?: "completed"
                        )
                    }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }.take(50)
                    trySend(list)
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            runCatching { rtdbHistRef.addValueEventListener(rtdbListener) }

            awaitClose {
                runCatching { reg?.remove() }
                runCatching { rtdbHistRef.removeEventListener(rtdbListener) }
            }
        }

    fun observeRefillHistory(userId: String, deviceId: String): Flow<List<RefillEvent>> =
        callbackFlow {
            val reg: ListenerRegistration? = runCatching {
                db.collection("users").document(userId)
                    .collection("devices").document(deviceId)
                    .collection("refillHistory")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .addSnapshotListener { snap, err ->
                        if (err != null || snap == null) return@addSnapshotListener
                        val list = snap.documents.map { doc ->
                            RefillEvent(
                                id = doc.id,
                                compartmentId = (doc.getLong("compartmentId") ?: 1L).toInt(),
                                medicineName = doc.getString("medicineName") ?: "",
                                quantityAdded = (doc.getLong("quantityAdded") ?: 0L).toInt(),
                                timestamp = doc.getTimestamp("timestamp")
                            )
                        }
                        trySend(list)
                    }
            }.getOrNull()

            val rtdbRefillRef = rtdb.getReference("users/$userId/devices/$deviceId/refillHistory")
            val rtdbListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val list = snapshot.children.mapNotNull { child ->
                        val id = child.key ?: return@mapNotNull null
                        val tsMs = child.child("timestamp").getValue(Long::class.java)
                        RefillEvent(
                            id = id,
                            compartmentId = (child.child("compartmentId").getValue(Long::class.java) ?: 1L).toInt(),
                            medicineName = child.child("medicineName").getValue(String::class.java) ?: "",
                            quantityAdded = (child.child("quantityAdded").getValue(Long::class.java) ?: 0L).toInt(),
                            timestamp = tsMs.toFirebaseTimestamp()
                        )
                    }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }.take(50)
                    trySend(list)
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            runCatching { rtdbRefillRef.addValueEventListener(rtdbListener) }

            awaitClose {
                runCatching { reg?.remove() }
                runCatching { rtdbRefillRef.removeEventListener(rtdbListener) }
            }
        }

    suspend fun saveMedicineAndSchedule(
        userId: String,
        deviceId: String,
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
        val deviceFsRef = db.collection("users").document(userId)
            .collection("devices").document(deviceId)
        val compRef = deviceFsRef.collection("compartments").document("compartment$compartmentId")
        val schedRef = deviceFsRef.collection("schedules").document()
        val scheduleId = schedRef.id
        val safePillsPerDose = tabletsPerDose.coerceIn(1, 5)
        val rtdbCompRef = rtdb.getReference("users/$userId/devices/$deviceId/compartments/compartment$compartmentId")
        val existingSnap = runCatching { rtdbCompRef.get().await() }.getOrNull()
        val needsInitialStock = existingSnap?.child("initialQuantity")?.exists() != true

        val fsResult = runCatching {
            val compFields = mutableMapOf<String, Any>(
                "medicineName" to medicineName.trim(),
                "dosage" to dosage.trim(),
                "pillForm" to pillForm,
                "colorHex" to colorHex,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (needsInitialStock) {
                compFields["initialQuantity"] = 5
                compFields["remainingQuantity"] = 5
                compFields["lowStockLimit"] = 1
            }
            compRef.set(compFields, SetOptions.merge()).await()

            schedRef.set(
                mapOf(
                    "compartmentId" to compartmentId,
                    "time" to time,
                    "tabletsPerDose" to safePillsPerDose,
                    "enabled" to notificationsEnabled,
                    "activeDays" to activeDays,
                    "pillForm" to pillForm,
                    "colorHex" to colorHex,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val rtdbResult = runCatching {
            val rtdbCompFields = mutableMapOf<String, Any>(
                "medicineName" to medicineName.trim(),
                "dosage" to dosage.trim(),
                "pillForm" to pillForm,
                "colorHex" to colorHex,
                "updatedAt" to ServerValue.TIMESTAMP
            )
            if (needsInitialStock) {
                rtdbCompFields["initialQuantity"] = 5
                rtdbCompFields["remainingQuantity"] = 5
                rtdbCompFields["lowStockLimit"] = 1
            }
            rtdbCompRef.updateChildren(rtdbCompFields).await()
            rtdb.getReference("users/$userId/devices/$deviceId/schedules/$scheduleId")
                .setValue(
                    mapOf(
                        "compartmentId" to compartmentId,
                        "time" to time,
                        "tabletsPerDose" to safePillsPerDose,
                        "enabled" to notificationsEnabled,
                        "activeDays" to activeDays,
                        "pillForm" to pillForm,
                        "colorHex" to colorHex,
                        "createdAt" to ServerValue.TIMESTAMP
                    )
                ).await()
        }

        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }

    suspend fun markDoseTakenManually(
        userId: String,
        deviceId: String,
        compartmentId: Int,
        tabletsTaken: Int
    ) {
        val deviceRef = db.collection("users").document(userId)
            .collection("devices").document(deviceId)
        val compRef = deviceRef.collection("compartments").document("compartment$compartmentId")
        val histRef = deviceRef.collection("dispensingHistory").document()
        val histId = histRef.id

        val rtdbCompRef = rtdb.getReference("users/$userId/devices/$deviceId/compartments/compartment$compartmentId")
        val rtdbSnap = runCatching { rtdbCompRef.get().await() }.getOrNull()
        val rtdbPrev = (rtdbSnap?.child("remainingQuantity")?.getValue(Long::class.java) ?: 5L).toInt()
        val rtdbMedName = rtdbSnap?.child("medicineName")?.getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: defaultSpecFor(compartmentId).first

        var resolvedMedName = rtdbMedName
        var updatedQty = (rtdbPrev - tabletsTaken).coerceAtLeast(0)

        val fsResult = runCatching {
            db.runTransaction { tx ->
                val snap = tx.get(compRef)
                val prev = (snap.getLong("remainingQuantity") ?: rtdbPrev.toLong()).toInt()
                val medName = snap.getString("medicineName") ?: rtdbMedName
                val updated = (prev - tabletsTaken).coerceAtLeast(0)
                resolvedMedName = medName
                updatedQty = updated

                tx.set(
                    compRef,
                    mapOf(
                        "initialQuantity" to (snap.getLong("initialQuantity") ?: 5L).toInt(),
                        "remainingQuantity" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                tx.set(
                    histRef,
                    mapOf(
                        "compartmentId" to compartmentId,
                        "medicineName" to medName,
                        "scheduledQuantity" to tabletsTaken,
                        "detectedQuantity" to tabletsTaken,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "status" to "taken"
                    )
                )
                updated
            }.await()
        }

        val rtdbResult = runCatching {
            rtdbCompRef.updateChildren(
                mapOf(
                    "remainingQuantity" to updatedQty,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            ).await()
            rtdb.getReference("users/$userId/devices/$deviceId/dispensingHistory/$histId")
                .setValue(
                    mapOf(
                        "compartmentId" to compartmentId,
                        "medicineName" to resolvedMedName,
                        "scheduledQuantity" to tabletsTaken,
                        "detectedQuantity" to tabletsTaken,
                        "timestamp" to ServerValue.TIMESTAMP,
                        "status" to "taken"
                    )
                ).await()
            rtdb.getReference("users/$userId/devices/$deviceId/commands/lastDispense")
                .setValue(
                    mapOf(
                        "compartmentId" to compartmentId,
                        "medicineName" to resolvedMedName,
                        "pillsToDrop" to tabletsTaken,
                        "source" to "manual_app",
                        "requestedAt" to ServerValue.TIMESTAMP
                    )
                ).await()
        }

        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }

    suspend fun refillContainerToFive(
        userId: String,
        deviceId: String,
        compartmentId: Int,
        capacity: Int = 5
    ) {
        val deviceRef = db.collection("users").document(userId)
            .collection("devices").document(deviceId)
        val compRef = deviceRef.collection("compartments").document("compartment$compartmentId")
        val refillRef = deviceRef.collection("refillHistory").document()
        val refillId = refillRef.id

        val rtdbCompRef = rtdb.getReference("users/$userId/devices/$deviceId/compartments/compartment$compartmentId")
        val rtdbSnap = runCatching { rtdbCompRef.get().await() }.getOrNull()
        val medName = rtdbSnap?.child("medicineName")?.getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: defaultSpecFor(compartmentId).first

        val fsResult = runCatching {
            compRef.set(
                mapOf(
                    "initialQuantity" to capacity,
                    "remainingQuantity" to capacity,
                    "lowStockLimit" to 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            refillRef.set(
                mapOf(
                    "compartmentId" to compartmentId,
                    "medicineName" to medName,
                    "quantityAdded" to capacity,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val rtdbResult = runCatching {
            rtdbCompRef.updateChildren(
                mapOf(
                    "initialQuantity" to capacity,
                    "remainingQuantity" to capacity,
                    "lowStockLimit" to 1,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            ).await()
            rtdb.getReference("users/$userId/devices/$deviceId/refillHistory/$refillId")
                .setValue(
                    mapOf(
                        "compartmentId" to compartmentId,
                        "medicineName" to medName,
                        "quantityAdded" to capacity,
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                ).await()
        }

        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }

    suspend fun updateCompartmentSettings(
        userId: String,
        deviceId: String,
        compartmentId: Int,
        medicineName: String,
        initialQuantity: Int,
        lowStockLimit: Int,
        resetRemainingToInitial: Boolean
    ) {
        val compRef = db.collection("users").document(userId)
            .collection("devices").document(deviceId)
            .collection("compartments").document("compartment$compartmentId")

        val data = mutableMapOf<String, Any>(
            "medicineName" to medicineName.trim(),
            "initialQuantity" to initialQuantity.coerceAtLeast(0),
            "lowStockLimit" to lowStockLimit.coerceAtLeast(0),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (resetRemainingToInitial) {
            data["remainingQuantity"] = initialQuantity.coerceAtLeast(0)
        }
        val fsResult = runCatching { compRef.set(data, SetOptions.merge()).await() }

        val rtdbData = mutableMapOf<String, Any>(
            "medicineName" to medicineName.trim(),
            "initialQuantity" to initialQuantity.coerceAtLeast(0),
            "lowStockLimit" to lowStockLimit.coerceAtLeast(0),
            "updatedAt" to ServerValue.TIMESTAMP
        )
        if (resetRemainingToInitial) {
            rtdbData["remainingQuantity"] = initialQuantity.coerceAtLeast(0)
        }
        val rtdbResult = runCatching {
            rtdb.getReference("users/$userId/devices/$deviceId/compartments/compartment$compartmentId")
                .updateChildren(rtdbData).await()
        }

        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }

    suspend fun addRefill(
        userId: String,
        deviceId: String,
        compartmentId: Int,
        quantityAdded: Int
    ) {
        require(quantityAdded > 0) { "Refill quantity must be greater than zero" }

        val deviceRef = db.collection("users").document(userId)
            .collection("devices").document(deviceId)
        val compRef = deviceRef.collection("compartments").document("compartment$compartmentId")
        val refillRef = deviceRef.collection("refillHistory").document()
        val refillId = refillRef.id

        val rtdbCompRef = rtdb.getReference("users/$userId/devices/$deviceId/compartments/compartment$compartmentId")
        val rtdbSnap = runCatching { rtdbCompRef.get().await() }.getOrNull()
        val rtdbCurrent = (rtdbSnap?.child("remainingQuantity")?.getValue(Long::class.java) ?: 0L).toInt()
        val rtdbMedName = rtdbSnap?.child("medicineName")?.getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: defaultSpecFor(compartmentId).first

        var resolvedMedName = rtdbMedName
        var updatedRemaining = rtdbCurrent + quantityAdded

        val fsResult = runCatching {
            db.runTransaction { tx ->
                val snap = tx.get(compRef)
                val currentRemaining = (snap.getLong("remainingQuantity") ?: rtdbCurrent.toLong()).toInt()
                val medName = snap.getString("medicineName") ?: rtdbMedName
                val next = currentRemaining + quantityAdded
                resolvedMedName = medName
                updatedRemaining = next

                tx.set(
                    compRef,
                    mapOf(
                        "remainingQuantity" to next,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                tx.set(
                    refillRef,
                    mapOf(
                        "compartmentId" to compartmentId,
                        "medicineName" to medName,
                        "quantityAdded" to quantityAdded,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                )
                next
            }.await()
        }

        val rtdbResult = runCatching {
            rtdbCompRef.updateChildren(
                mapOf(
                    "remainingQuantity" to updatedRemaining,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            ).await()
            rtdb.getReference("users/$userId/devices/$deviceId/refillHistory/$refillId")
                .setValue(
                    mapOf(
                        "compartmentId" to compartmentId,
                        "medicineName" to resolvedMedName,
                        "quantityAdded" to quantityAdded,
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                ).await()
        }

        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }

    suspend fun toggleSchedule(
        userId: String,
        deviceId: String,
        scheduleId: String,
        enabled: Boolean
    ) {
        val fsResult = runCatching {
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .collection("schedules").document(scheduleId)
                .update("enabled", enabled)
                .await()
        }
        val rtdbResult = runCatching {
            rtdb.getReference("users/$userId/devices/$deviceId/schedules/$scheduleId/enabled")
                .setValue(enabled)
                .await()
        }
        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }

    suspend fun deleteSchedule(userId: String, deviceId: String, scheduleId: String) {
        val fsResult = runCatching {
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .collection("schedules").document(scheduleId)
                .delete()
                .await()
        }
        val rtdbResult = runCatching {
            rtdb.getReference("users/$userId/devices/$deviceId/schedules/$scheduleId")
                .removeValue()
                .await()
        }
        if (fsResult.isFailure && rtdbResult.isFailure) {
            throw (rtdbResult.exceptionOrNull() ?: fsResult.exceptionOrNull()!!)
        }
    }
}
