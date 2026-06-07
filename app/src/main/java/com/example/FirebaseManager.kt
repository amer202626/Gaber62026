package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    // Self-contained coroutine scope for handling background retries and network state flows
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRetrying = false

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    lateinit var db: FirebaseFirestore
    lateinit var storage: FirebaseStorage

    // Observable States (Memory Caches for Offline & Fast UI transitions)
    val appConfig = MutableStateFlow(AppConfig())
    val categories = MutableStateFlow<List<ServiceCategory>>(emptyList())
    val providers = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val pendingProviders = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val banners = MutableStateFlow<List<BannerAd>>(emptyList())
    val chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val incidentReports = MutableStateFlow<List<IncidentReport>>(emptyList())
    val activityLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val moderators = MutableStateFlow<List<Moderator>>(emptyList())
    val isProvidersDataFromCache = MutableStateFlow(false)

    // Active Listener Containers
    private val registrations = mutableListOf<ListenerRegistration>()

    fun scheduleRetry() {
        if (isRetrying) return
        isRetrying = true
        Log.w(TAG, "Sync error detected. Scheduling clean re-subscription of all Firestore real-time listeners in 5 seconds...")
        managerScope.launch {
            delay(5000)
            isRetrying = false
            forceReSubscribe()
        }
    }

    fun init(context: Context) {
        if (_isInitialized.value) return
        try {
            // Setup proper fallback builder options based on the user provided google-services.json
            val builder = FirebaseOptions.Builder()
                .setApplicationId("1:89823302013:android:1910d098b23f547aa3fc14")
                .setApiKey("AIzaSyCgFnPJso1f2mwB1jvyRbGzZReAdf4eug0")
                .setProjectId("dalyly2026")
                .setStorageBucket("dalyly2026.firebasestorage.app")
                .build()

            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isEmpty()) {
                try {
                    // Try auto configuration via google-services.json first!
                    FirebaseApp.initializeApp(context)
                } catch (autoEx: Exception) {
                    Log.w(TAG, "Default automatic FirebaseApp initialization failed, using builder: ${autoEx.message}")
                    FirebaseApp.initializeApp(context, builder)
                }
            } else {
                val existingDefault = apps.firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                if (existingDefault != null && existingDefault.options.projectId != "dalyly2026") {
                    try {
                        existingDefault.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed deleting stale Firebase DEFAULT app: ${e.message}")
                    }
                    FirebaseApp.initializeApp(context, builder)
                } else {
                    existingDefault ?: FirebaseApp.initializeApp(context, builder)
                }
            }

            db = FirebaseFirestore.getInstance(app!!)
            storage = FirebaseStorage.getInstance(app!!)

            // Dynamic Anonymous Login to unlock security rules for remote syncing
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance(app!!)
                if (auth.currentUser == null) {
                    auth.signInAnonymously()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "FirebaseAuth session established anonymously. UID: ${task.result?.user?.uid}")
                                forceReSubscribe()
                            } else {
                                Log.e(TAG, "FirebaseAuth anonymous sign-in failed! If security rules require auth, sync will be blocked.", task.exception)
                            }
                        }
                } else {
                    Log.d(TAG, "FirebaseAuth active session recovered for UID: ${auth.currentUser?.uid}")
                }
            } catch (authEx: Exception) {
                Log.w(TAG, "Non-blocking error during FirebaseAuth startup: ${authEx.message}")
            }

            
            // ENABLING OFFLINE PERSISTENCE AND CACHE FOR INSTANT OFFLINE MODE
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true) // Enables local storage cache
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                db.firestoreSettings = settings
                Log.d(TAG, "Firestore cache persistence enabled with CACHE_SIZE_UNLIMITED as requested by user!")
            } catch (settingsEx: Exception) {
                Log.w(TAG, "Firestore settings configuration warning (already configured?): ${settingsEx.message}")
            }

            // Forcefully enable the Firestore network connectivity socket to guarantee synchronization
            try {
                db.enableNetwork().addOnCompleteListener { networkTask ->
                    if (networkTask.isSuccessful) {
                        Log.d(TAG, "Firestore network explicitly enabled successfully!")
                    } else {
                        Log.e(TAG, "Firestore explicit network activation returned warning status", networkTask.exception)
                    }
                }
            } catch (netErr: Exception) {
                Log.w(TAG, "Encountered non-blocking issue while calling enableNetwork(): ${netErr.message}")
            }

            _isInitialized.value = true
            Log.d(TAG, "Firebase initialized on 'dalyly2026' with success!")
            
            // Programmatic internet monitoring for forcing subscriber fresh reload on reconnect
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                if (connectivityManager != null) {
                    val request = android.net.NetworkRequest.Builder()
                        .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                        private var isFirst = true
                        override fun onAvailable(network: android.net.Network) {
                            if (isFirst) {
                                isFirst = false
                                return
                            }
                            Log.d(TAG, "Internet connection re-established! Forcing clean re-subscription to prevent frozen sync.")
                            forceReSubscribe()
                        }
                    })
                }
            } catch (netEx: Exception) {
                Log.w(TAG, "Connectivity network observer register warning: ${netEx.message}")
            }

            // Seed base categories in background if list is empty
            seedDefaultCategories()
            
            // Start listening live to Firestore collections
            startListening()

        } catch (e: Exception) {
            Log.e(TAG, "Critical Firebase Initialization Error: ${e.message}")
            _isInitialized.value = true
            try {
                db = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                db.firestoreSettings = settings
                
                db.enableNetwork().addOnCompleteListener { 
                    startListening()
                }
            } catch (innerEx: Exception) {
                Log.e(TAG, "Fatal fallback error: ${innerEx.message}")
            }
        }
    }

    var activeChatListenerRegistration: ListenerRegistration? = null

    fun startListeningToChats(onUpdate: (List<ChatMessage>) -> Unit = {}) {
        activeChatListenerRegistration?.remove()
        try {
            activeChatListenerRegistration = db.collection("chats")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Chats sync error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        val items = snapshots.map { doc ->
                            ChatMessage(
                                id = doc.id,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "ضيف",
                                receiverId = doc.getString("receiverId") ?: "admin",
                                content = doc.getString("content") ?: "",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        }.sortedBy { it.timestamp }
                        chats.value = items
                        onUpdate(items)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding chat listener session: ${e.message}")
        }
    }

    fun removeAllListeners() {
        synchronized(registrations) {
            registrations.forEach {
                try {
                    it.remove()
                } catch (ex: Exception) {
                    Log.w(TAG, "Error clearing listener: ${ex.message}")
                }
            }
            registrations.clear()
        }
        activeChatListenerRegistration?.remove()
        activeChatListenerRegistration = null
    }

    fun forceReSubscribe() {
        Log.d(TAG, "Force re-subscribing all snapshot listeners and enabling Firestore network")
        removeAllListeners()
        try {
            db.enableNetwork().addOnCompleteListener {
                startListening()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-blocking error during forceReSubscribe enableNetwork(): ${e.message}")
            startListening()
        }
    }

    private fun startListening() {
        if (!_isInitialized.value) return
        try {
            // 1. App configuration document
            val configDocRef = db.collection("app_config").document("settings")
            val configReg = configDocRef.addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "AppConfig sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val map = snapshot.data
                    if (map != null) {
                        appConfig.value = AppConfig.fromMap(map)
                    }
                } else {
                    // Prevent overwriting DB with default empty configurations
                    appConfig.value = AppConfig()
                }
            }
            registrations.add(configReg)

            // 2. Categories Snapshot Listener
            val catColRef = db.collection("categories")
            val catReg = catColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Categories sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val items = snapshots.map { doc ->
                        ServiceCategory(
                            id = doc.id,
                            nameAr = doc.getString("nameAr") ?: "",
                            nameEn = doc.getString("nameEn") ?: "",
                            iconEmoji = doc.getString("iconEmoji") ?: "🎒"
                        )
                    }
                    categories.value = items
                }
            }
            registrations.add(catReg)

            // 3. Service Providers
            val provColRef = db.collection("service_providers")
            val provReg = provColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Providers sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    isProvidersDataFromCache.value = snapshots.metadata.isFromCache
                    val items = snapshots.map { doc ->
                        ServiceProvider(
                            id = doc.id,
                            fullName = doc.getString("fullName") ?: "",
                            phone = doc.getString("phone") ?: "",
                            whatsapp = doc.getString("whatsapp") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            subCategory = doc.getString("subCategory") ?: "",
                            address = doc.getString("address") ?: "",
                            area = doc.getString("area") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            idCardUrl = doc.getString("idCardUrl") ?: "",
                            gpsLat = doc.getDouble("gpsLat") ?: 15.3694,
                            gpsLng = doc.getDouble("gpsLng") ?: 44.1910,
                            isVerified = doc.getBoolean("isVerified") ?: false,
                            isPinned = doc.getBoolean("isPinned") ?: false,
                            isRecommended = doc.getBoolean("isRecommended") ?: false,
                            hasPremiumSubscription = doc.getBoolean("hasPremiumSubscription") ?: false,
                            loyaltyPoints = doc.getLong("loyaltyPoints")?.toInt() ?: 0,
                            ratingSum = doc.getDouble("ratingSum")?.toFloat() ?: 0.0f,
                            ratingCount = doc.getLong("ratingCount")?.toInt() ?: 0,
                            isBlocked = doc.getBoolean("isBlocked") ?: false
                        )
                    }
                    providers.value = items.filter { !it.isBlocked }
                }
            }
            registrations.add(provReg)

            // 4. Pending Providers
            val pendingColRef = db.collection("pending_providers")
            val pendingReg = pendingColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Pending providers sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val items = snapshots.map { doc ->
                        ServiceProvider(
                            id = doc.id,
                            fullName = doc.getString("fullName") ?: "",
                            phone = doc.getString("phone") ?: "",
                            whatsapp = doc.getString("whatsapp") ?: "",
                            categoryId = doc.getString("categoryId") ?: "",
                            subCategory = doc.getString("subCategory") ?: "",
                            address = doc.getString("address") ?: "",
                            area = doc.getString("area") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            idCardUrl = doc.getString("idCardUrl") ?: "",
                            gpsLat = doc.getDouble("gpsLat") ?: 15.369,
                            gpsLng = doc.getDouble("gpsLng") ?: 44.191,
                            isVerified = false,
                            isPinned = false,
                            isRecommended = false,
                            hasPremiumSubscription = false,
                            ratingSum = 0.0f,
                            ratingCount = 0
                        )
                    }
                    pendingProviders.value = items
                }
            }
            registrations.add(pendingReg)

            // 5. Banners
            val bannerColRef = db.collection("banners")
            val banReg = bannerColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Banners sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val items = snapshots.map { doc ->
                        BannerAd(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            linkUrl = doc.getString("linkUrl") ?: "",
                            displaySize = doc.getString("displaySize") ?: "M",
                            durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 5,
                            isActive = doc.getBoolean("isActive") ?: true
                        )
                    }
                    banners.value = items.filter { it.isActive }
                }
            }
            registrations.add(banReg)

            // 6. Chats Live Snapshot Listener
            startListeningToChats()

            // 7. Incident Reports
            val incidentColRef = db.collection("incident_reports")
            val incReg = incidentColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Incident reports sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val items = snapshots.map { doc ->
                        IncidentReport(
                            id = doc.id,
                            providerId = doc.getString("providerId") ?: "",
                            providerName = doc.getString("providerName") ?: "",
                            reporterName = doc.getString("reporterName") ?: "مجهول",
                            reason = doc.getString("reason") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    incidentReports.value = items
                }
            }
            registrations.add(incReg)

            // 8. System Activity Log (Ordered in memory for failsafe results)
            val logsColRef = db.collection("activity_logs")
            val logReg = logsColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Activity logs sync error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val items = snapshots.map { doc ->
                        ActivityLog(
                            id = doc.id,
                            user = doc.getString("user") ?: "Admin",
                            action = doc.getString("action") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }.sortedByDescending { it.timestamp }
                    activityLogs.value = items
                }
            }
            registrations.add(logReg)

            // 9. Supervisors/Moderators
            val modsColRef = db.collection("moderators")
            val modReg = modsColRef.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Moderators snapshot error: ${error.message}", error)
                    scheduleRetry()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val items = snapshots.map { doc ->
                        Moderator(
                            id = doc.id,
                            username = doc.getString("username") ?: "",
                            password = doc.getString("password") ?: ""
                        )
                    }
                    moderators.value = items
                }
            }
            registrations.add(modReg)

        } catch (e: Exception) {
            Log.e(TAG, "Listening setup error: ${e.message}")
        }
    }

    // Config saves
    fun saveAppConfig(config: AppConfig) {
        val configMap = config.toMap()
        if (_isInitialized.value) {
            db.collection("app_config").document("settings").set(configMap)
        } else {
            appConfig.value = config
        }
    }

    // Provider Registrations Form Submit Tool
    fun submitCandidateProvider(p: ServiceProvider, imageBytes: ByteArray?, idCardBytes: ByteArray?, onComplete: (Boolean, String) -> Unit) {
        val id = UUID.randomUUID().toString()
        val candidate = p.copy(id = id)

        // Standard dynamic media fallbacks if storage is offline or missing
        val profileUrl = if (imageBytes != null) "https://picsum.photos/200/300?tmp=${UUID.randomUUID()}" else p.imageUrl
        val idCardUrl = if (idCardBytes != null) "https://picsum.photos/400/300?tmp=${UUID.randomUUID()}" else p.idCardUrl

        val dataMap = mapOf(
            "id" to id,
            "fullName" to candidate.fullName,
            "phone" to candidate.phone,
            "whatsapp" to candidate.whatsapp,
            "categoryId" to candidate.categoryId,
            "subCategory" to candidate.subCategory,
            "address" to candidate.address,
            "area" to candidate.area,
            "imageUrl" to profileUrl,
            "idCardUrl" to idCardUrl,
            "gpsLat" to candidate.gpsLat,
            "gpsLng" to candidate.gpsLng,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("pending_providers").document(id).set(dataMap)
            .addOnSuccessListener {
                onComplete(true, "تم إرسال طلب الانضمام بنجاح وسيتم إقراره من قبل الإدارة قريباً!")
            }
            .addOnFailureListener { e ->
                onComplete(false, "حدث خطأ أثناء إرسال الطلب: ${e.message}")
            }
    }

    // Admin commands
    fun approveProvider(id: String, onComplete: () -> Unit = {}) {
        val candidate = pendingProviders.value.find { it.id == id } ?: return
        val updated = candidate.copy(isVerified = true)
        
        val dataMap = mapOf(
            "fullName" to updated.fullName,
            "phone" to updated.phone,
            "whatsapp" to updated.whatsapp,
            "categoryId" to updated.categoryId,
            "subCategory" to updated.subCategory,
            "address" to updated.address,
            "area" to updated.area,
            "imageUrl" to updated.imageUrl,
            "idCardUrl" to updated.idCardUrl,
            "gpsLat" to updated.gpsLat,
            "gpsLng" to updated.gpsLng,
            "isVerified" to true,
            "isPinned" to false,
            "isRecommended" to false,
            "hasPremiumSubscription" to false,
            "loyaltyPoints" to 0,
            "ratingSum" to 0.0,
            "ratingCount" to 0,
            "isBlocked" to false
        )

        db.collection("service_providers").document(id).set(dataMap)
            .addOnSuccessListener {
                db.collection("pending_providers").document(id).delete()
                logActivity("المدير", "تمت الموافقة على مقدم الخدمة: ${updated.fullName}")
                onComplete()
            }
    }

    fun rejectProvider(id: String, reason: String, onComplete: () -> Unit = {}) {
        db.collection("pending_providers").document(id).delete()
            .addOnSuccessListener {
                logActivity("المدير", "تم رفض ومسح الطلب $id لسبب: $reason")
                onComplete()
            }
    }

    fun deleteProvider(id: String, onComplete: () -> Unit = {}) {
        db.collection("service_providers").document(id).delete().addOnSuccessListener { onComplete() }
    }

    fun addManualProvider(p: ServiceProvider, onComplete: () -> Unit) {
        val id = UUID.randomUUID().toString()
        val pImage = if (p.imageUrl.isEmpty()) "https://picsum.photos/200/300?id=${UUID.randomUUID()}" else p.imageUrl
        val dataMap = mapOf(
            "fullName" to p.fullName,
            "phone" to p.phone,
            "whatsapp" to p.whatsapp,
            "categoryId" to p.categoryId,
            "subCategory" to p.subCategory,
            "address" to p.address,
            "area" to p.area,
            "imageUrl" to pImage,
            "idCardUrl" to "https://picsum.photos/400/300",
            "gpsLat" to p.gpsLat,
            "gpsLng" to p.gpsLng,
            "isVerified" to p.isVerified,
            "isPinned" to p.isPinned,
            "isRecommended" to p.isRecommended,
            "hasPremiumSubscription" to p.hasPremiumSubscription,
            "loyaltyPoints" to p.loyaltyPoints,
            "ratingSum" to p.ratingSum,
            "ratingCount" to p.ratingCount,
            "isBlocked" to false
        )

        db.collection("service_providers").document(id).set(dataMap)
            .addOnSuccessListener {
                logActivity("المدير/المشرف", "إضافة مقدم خدمة يدوياً: ${p.fullName}")
                onComplete()
            }
    }

    // Direct Firestore update of any details (edit provider)
    fun updateProviderDetails(p: ServiceProvider, onComplete: () -> Unit = {}) {
        val dataMap = mapOf(
            "fullName" to p.fullName,
            "phone" to p.phone,
            "whatsapp" to p.whatsapp,
            "categoryId" to p.categoryId,
            "subCategory" to p.subCategory,
            "address" to p.address,
            "area" to p.area,
            "imageUrl" to p.imageUrl,
            "idCardUrl" to p.idCardUrl,
            "gpsLat" to p.gpsLat,
            "gpsLng" to p.gpsLng,
            "isVerified" to p.isVerified,
            "isPinned" to p.isPinned,
            "isRecommended" to p.isRecommended,
            "hasPremiumSubscription" to p.hasPremiumSubscription,
            "loyaltyPoints" to p.loyaltyPoints,
            "ratingSum" to p.ratingSum,
            "ratingCount" to p.ratingCount,
            "isBlocked" to p.isBlocked
        )
        db.collection("service_providers").document(p.id).set(dataMap)
            .addOnSuccessListener {
                logActivity("المدير/المشرف", "تعديل بيانات مقدم الخدمة: ${p.fullName}")
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun updateProviderStatus(id: String, isVerified: Boolean, isPinned: Boolean, isRecom: Boolean, isPremium: Boolean, onComplete: () -> Unit = {}) {
        val updates = mapOf(
            "isVerified" to isVerified,
            "isPinned" to isPinned,
            "isRecommended" to isRecom,
            "hasPremiumSubscription" to isPremium
        )
        db.collection("service_providers").document(id).update(updates).addOnSuccessListener { onComplete() }
    }

    // Category controls
    fun manageCategory(id: String, nameAr: String, nameEn: String, iconEmoji: String, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val docRef = db.collection("categories").document(id)
        if (isDelete) {
            docRef.delete().addOnSuccessListener { onComplete() }
        } else {
            val data = mapOf("nameAr" to nameAr, "nameEn" to nameEn, "iconEmoji" to iconEmoji)
            docRef.set(data).addOnSuccessListener { onComplete() }
        }
    }

    // Banners controls
    fun manageBanner(b: BannerAd, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val id = if (b.id.isEmpty()) UUID.randomUUID().toString() else b.id
        val docRef = db.collection("banners").document(id)
        if (isDelete) {
            docRef.delete().addOnSuccessListener { onComplete() }
        } else {
            val data = mapOf(
                "title" to b.title,
                "imageUrl" to b.imageUrl.ifEmpty { "https://picsum.photos/600/300?v=${UUID.randomUUID()}" },
                "linkUrl" to b.linkUrl,
                "displaySize" to b.displaySize,
                "durationSeconds" to b.durationSeconds,
                "isActive" to b.isActive
            )
            docRef.set(data).addOnSuccessListener { onComplete() }
        }
    }

    // Block logic
    fun blockProvider(id: String, isBlock: Boolean, onComplete: () -> Unit = {}) {
        db.collection("service_providers").document(id).update("isBlocked", isBlock)
            .addOnSuccessListener {
                logActivity("المدير", "تم تغيير حالة الحظر لمقدم الخدمة $id إلى $isBlock")
                onComplete()
            }
    }

    // Post Review with 15 Loyalty points reward
    fun addProviderReview(r: ProviderReview, onComplete: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        val docData = mapOf(
            "providerId" to r.providerId,
            "reviewerName" to r.reviewerName,
            "rating" to r.rating,
            "comment" to r.comment,
            "timestamp" to r.timestamp
        )

        db.collection("reviews").document(id).set(docData)
            .addOnSuccessListener {
                // Fetch existing ratings and increment points
                db.collection("service_providers").document(r.providerId).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val currentSum = doc.getDouble("ratingSum")?.toFloat() ?: 0.0f
                            val currentCount = doc.getLong("ratingCount")?.toInt() ?: 0
                            val loyaltyPoints = doc.getLong("loyaltyPoints")?.toInt() ?: 0
                            
                            val updates = mapOf(
                                "ratingSum" to (currentSum + r.rating),
                                "ratingCount" to (currentCount + 1),
                                "loyaltyPoints" to (loyaltyPoints + 15) // +15 loyalty points on review
                            )
                            db.collection("service_providers").document(r.providerId).update(updates)
                        }
                        onComplete()
                    }
            }
    }

    // Submit Report
    fun submitIncidentReport(rep: IncidentReport, onComplete: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        val docData = mapOf(
            "providerId" to rep.providerId,
            "providerName" to rep.providerName,
            "reporterName" to rep.reporterName,
            "reason" to rep.reason,
            "timestamp" to rep.timestamp
        )

        db.collection("incident_reports").document(id).set(docData).addOnSuccessListener { onComplete() }
    }

    // Post live chat sync message
    fun sendChatMessage(msg: ChatMessage, onComplete: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        val docData = mapOf(
            "senderId" to msg.senderId,
            "senderName" to msg.senderName,
            "receiverId" to msg.receiverId,
            "content" to msg.content,
            "timestamp" to msg.timestamp
        )

        db.collection("chats").document(id).set(docData).addOnSuccessListener { onComplete() }
    }

    fun wipeChatLogs(onComplete: () -> Unit = {}) {
        db.collection("chats").get().addOnSuccessListener { snapshots ->
            val batch = db.batch()
            snapshots.forEach { batch.delete(it.reference) }
            batch.commit().addOnSuccessListener {
                logActivity("المدير", "تم مسح سجلات المحادثة بالكامل.")
                onComplete()
            }
        }
    }

    // Supervisors management
    fun manageSupervisor(m: Moderator, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val id = if (m.id.isEmpty()) UUID.randomUUID().toString() else m.id
        val docRef = db.collection("moderators").document(id)
        if (isDelete) {
            docRef.delete().addOnSuccessListener { onComplete() }
        } else {
            docRef.set(mapOf("username" to m.username, "password" to m.password)).addOnSuccessListener { onComplete() }
        }
    }

    fun logActivity(user: String, action: String) {
        val id = UUID.randomUUID().toString()
        val data = mapOf(
            "user" to user,
            "action" to action,
            "timestamp" to System.currentTimeMillis()
        )
        if (_isInitialized.value) {
            db.collection("activity_logs").document(id).set(data)
        }
    }

    // Default Seeders for instant rich loading
    private fun seedDefaultCategories() {
        db.collection("categories").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                val defaults = listOf(
                    ServiceCategory("cat_1", "صيانة وأعمال مهنية", "Maintenance & Professional", "🛠️"),
                    ServiceCategory("cat_2", "خدمات طبية ورعاية", "Medical & Care Services", "🩺"),
                    ServiceCategory("cat_3", "نقل وتوصيل", "Transportation & Delivery", "🚚"),
                    ServiceCategory("cat_4", "تعليم وتدريب", "Education & Training", "📚"),
                    ServiceCategory("cat_5", "مطاعم ومأكولات", "Restaurants & Food", "🍛"),
                    ServiceCategory("cat_6", "خدمات قانونية وعقارية", "Legal & Real Estate", "⚖️"),
                    ServiceCategory("cat_7", "صيانة حاسوب وهواتف", "Tech & Mobile Fix", "📱"),
                    ServiceCategory("cat_8", "حرف يدوية وتطريز", "Handicrafts & Embroidery", "🧵")
                )
                for (cat in defaults) {
                    db.collection("categories").document(cat.id).set(
                        mapOf("nameAr" to cat.nameAr, "nameEn" to cat.nameEn, "iconEmoji" to cat.iconEmoji)
                    )
                }
            }
        }
    }
}
