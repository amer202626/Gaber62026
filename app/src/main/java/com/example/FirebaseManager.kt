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
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRetrying = false

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    lateinit var db: FirebaseFirestore
    lateinit var storage: FirebaseStorage

    // State flows representing actual synchronized memory caches for instant offline rendering
    val appConfig = MutableStateFlow(AppConfig())
    val categories = MutableStateFlow<List<ServiceCategory>>(emptyList())
    val providers = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val pendingProviders = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val banners = MutableStateFlow<List<BannerAd>>(emptyList())
    val chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val incidentReports = MutableStateFlow<List<IncidentReport>>(emptyList())
    val activityLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val moderators = MutableStateFlow<List<Moderator>>(emptyList())
    val citiesList = MutableStateFlow(listOf("صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب"))
    val isProvidersDataFromCache = MutableStateFlow(false)

    private val registrations = mutableListOf<ListenerRegistration>()
    private var activeChatListenerRegistration: ListenerRegistration? = null

    fun init(context: Context) {
        if (_isInitialized.value) return
        try {
            val builder = FirebaseOptions.Builder()
                .setApplicationId("1:89823302013:android:1910d098b23f547aa3fc14")
                .setApiKey("AIzaSyCgFnPJso1f2mwB1jvyRbGzZReAdf4eug0")
                .setProjectId("dalyly2026")
                .setStorageBucket("dalyly2026.firebasestorage.app")
                .build()

            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context)
                } catch (autoEx: Exception) {
                    FirebaseApp.initializeApp(context, builder)
                }
            } else {
                apps.firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME } ?: FirebaseApp.initializeApp(context, builder)
            }

            db = FirebaseFirestore.getInstance(app!!)
            storage = FirebaseStorage.getInstance(app!!)

            // Dynamic Firebase Anonymous Auth
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance(app!!)
                if (auth.currentUser == null) {
                    auth.signInAnonymously().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "FirebaseAuth active: ${task.result?.user?.uid}")
                            forceReSubscribe()
                        }
                    }
                } else {
                    Log.d(TAG, "FirebaseAuth recovered: ${auth.currentUser?.uid}")
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Auth initialization warning: ${ex.message}")
            }

            // Setup offline storage
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                db.firestoreSettings = settings
            } catch (ex: Exception) {
                Log.w(TAG, "Firestore settings already applied")
            }

            db.enableNetwork().addOnCompleteListener {
                startListening()
            }

            _isInitialized.value = true
            seedDefaultCategories()

        } catch (e: Exception) {
            Log.e(TAG, "Critical Firebase Initialization Error: ${e.message}")
            _isInitialized.value = true
        }
    }

    private fun startListening() {
        if (!_isInitialized.value) return
        try {
            // 1. App configuration Document listener
            val configReg = db.collection("app_config").document("settings")
                .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                    if (snapshot != null && snapshot.exists()) {
                        val map = snapshot.data
                        if (map != null) {
                            appConfig.value = AppConfig.fromMap(map)
                        }
                    } else {
                        appConfig.value = AppConfig()
                    }
                }
            registrations.add(configReg)

            // 2. Categories Snapshot Listener
            val catReg = db.collection("categories")
                .addSnapshotListener { snapshots, error ->
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

            // 3. Service Providers Profile listener
            val provReg = db.collection("service_providers")
                .addSnapshotListener { snapshots, error ->
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
                                isBlocked = doc.getBoolean("isBlocked") ?: false,
                                previewPrice = doc.getDouble("previewPrice") ?: 0.0
                            )
                        }
                        providers.value = items.filter { !it.isBlocked }
                    }
                }
            registrations.add(provReg)

            // 4. Pending Candidates Approval listener
            val pendingReg = db.collection("pending_providers")
                .addSnapshotListener { snapshots, error ->
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
                                ratingCount = 0,
                                previewPrice = doc.getDouble("previewPrice") ?: 0.0
                            )
                        }
                        pendingProviders.value = items
                    }
                }
            registrations.add(pendingReg)

            // 5. Banners Promo Ads
            val banReg = db.collection("banners")
                .addSnapshotListener { snapshots, error ->
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

            // 6. Direct Messenger Sync Listener
            startListeningToChats()

            // 7. Incident Reports for moderators
            val incReg = db.collection("incident_reports")
                .addSnapshotListener { snapshots, error ->
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

            // 8. Security audit Logs listener
            val logReg = db.collection("activity_logs")
                .addSnapshotListener { snapshots, error ->
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

            // 9. Managers/Moderators list
            val modReg = db.collection("moderators")
                .addSnapshotListener { snapshots, error ->
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
            Log.e(TAG, "Error registering listeners: ${e.message}")
        }
    }

    fun startListeningToChats(onUpdate: (List<ChatMessage>) -> Unit = {}) {
        activeChatListenerRegistration?.remove()
        try {
            activeChatListenerRegistration = db.collection("chats")
                .addSnapshotListener { snapshots, error ->
                    if (snapshots != null) {
                        val items = snapshots.map { doc ->
                            ChatMessage(
                                id = doc.id,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "ضيف يمني",
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
            Log.e(TAG, "Chat listener error: ${e.message}")
        }
    }

    fun removeAllListeners() {
        synchronized(registrations) {
            registrations.forEach {
                try { it.remove() } catch (ignored: Exception) {}
            }
            registrations.clear()
        }
        activeChatListenerRegistration?.remove()
        activeChatListenerRegistration = null
    }

    fun forceReSubscribe() {
        removeAllListeners()
        try {
            db.enableNetwork().addOnCompleteListener {
                startListening()
            }
        } catch (e: Exception) {
            startListening()
        }
    }

    fun saveAppConfig(config: AppConfig) {
        if (_isInitialized.value) {
            db.collection("app_config").document("settings").set(config.toMap())
        } else {
            appConfig.value = config
        }
    }

    fun submitCandidateProvider(p: ServiceProvider, imageBytes: ByteArray?, idCardBytes: ByteArray?, onComplete: (Boolean, String) -> Unit) {
        val id = UUID.randomUUID().toString()
        val profileUrl = if (p.imageUrl.isEmpty()) "https://picsum.photos/200/300?tmp=${UUID.randomUUID()}" else p.imageUrl
        val idCardUrl = if (p.idCardUrl.isEmpty()) "https://picsum.photos/400/300?tmp=${UUID.randomUUID()}" else p.idCardUrl

        val dataMap = mapOf(
            "id" to id,
            "fullName" to p.fullName,
            "phone" to p.phone,
            "whatsapp" to p.whatsapp,
            "categoryId" to p.categoryId,
            "subCategory" to p.subCategory,
            "address" to p.address,
            "area" to p.area,
            "imageUrl" to profileUrl,
            "idCardUrl" to idCardUrl,
            "gpsLat" to p.gpsLat,
            "gpsLng" to p.gpsLng,
            "previewPrice" to p.previewPrice,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("pending_providers").document(id).set(dataMap)
            .addOnSuccessListener {
                logActivity("طلب إنضمام جديد", "مقدم جديد: ${p.fullName}")
                onComplete(true, "تم إرسال طلب الانضمام بنجاح! سيتم مراجعته وتوثيقه من قبل المشرفين قريباً جداً.")
            }
            .addOnFailureListener { e ->
                onComplete(false, "عذراً تعذر إدخال الطلب: ${e.message}")
            }
    }

    fun approveProvider(id: String, onComplete: () -> Unit = {}) {
        val candidate = pendingProviders.value.find { it.id == id } ?: return
        val dataMap = mapOf(
            "fullName" to candidate.fullName,
            "phone" to candidate.phone,
            "whatsapp" to candidate.whatsapp,
            "categoryId" to candidate.categoryId,
            "subCategory" to candidate.subCategory,
            "address" to candidate.address,
            "area" to candidate.area,
            "imageUrl" to candidate.imageUrl,
            "idCardUrl" to candidate.idCardUrl,
            "gpsLat" to candidate.gpsLat,
            "gpsLng" to candidate.gpsLng,
            "isVerified" to true,
            "isPinned" to false,
            "isRecommended" to false,
            "hasPremiumSubscription" to false,
            "loyaltyPoints" to 50, // bonus start points
            "ratingSum" to 5.0,
            "ratingCount" to 1,
            "isBlocked" to false,
            "previewPrice" to candidate.previewPrice
        )

        db.collection("service_providers").document(id).set(dataMap)
            .addOnSuccessListener {
                db.collection("pending_providers").document(id).delete()
                logActivity("المشرف", "الموافقة على الحرفي: ${candidate.fullName}")
                onComplete()
            }
    }

    fun rejectProvider(id: String, reason: String, onComplete: () -> Unit = {}) {
        db.collection("pending_providers").document(id).delete().addOnSuccessListener {
            logActivity("المشرف", "رفض الطلب $id للسبب: $reason")
            onComplete()
        }
    }

    fun deleteProvider(id: String, onComplete: () -> Unit = {}) {
        db.collection("service_providers").document(id).delete().addOnSuccessListener {
            logActivity("المشرف", "حزف مقدم الخدمة ذو المعرف [$id]")
            onComplete()
        }
    }

    fun addManualProvider(p: ServiceProvider, onComplete: () -> Unit) {
        val id = UUID.randomUUID().toString()
        val dataMap = mapOf(
            "fullName" to p.fullName,
            "phone" to p.phone,
            "whatsapp" to p.whatsapp,
            "categoryId" to p.categoryId,
            "subCategory" to p.subCategory,
            "address" to p.address,
            "area" to p.area,
            "imageUrl" to p.imageUrl.ifEmpty { "https://picsum.photos/200/300?id=${UUID.randomUUID()}" },
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
            "isBlocked" to false,
            "previewPrice" to p.previewPrice
        )

        db.collection("service_providers").document(id).set(dataMap)
            .addOnSuccessListener {
                logActivity("المشرف", "إضافة مهني يدوياً: ${p.fullName}")
                onComplete()
            }
    }

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
            "isBlocked" to p.isBlocked,
            "previewPrice" to p.previewPrice
        )
        db.collection("service_providers").document(p.id).set(dataMap)
            .addOnSuccessListener {
                logActivity("المشرف", "تعديل بيانات الحرفي المهني: ${p.fullName}")
                onComplete()
            }
            .addOnFailureListener { onComplete() }
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

    fun manageCategory(id: String, nameAr: String, nameEn: String, iconEmoji: String, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val docRef = db.collection("categories").document(id)
        if (isDelete) {
            docRef.delete().addOnSuccessListener { onComplete() }
        } else {
            val data = mapOf("nameAr" to nameAr, "nameEn" to nameEn, "iconEmoji" to iconEmoji)
            docRef.set(data).addOnSuccessListener { onComplete() }
        }
    }

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
                logActivity("المدير", "تم تفريغ المحادثات بالكامل.")
                onComplete()
            }
        }.addOnFailureListener { onComplete() }
    }

    fun manageBanner(b: BannerAd, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val id = b.id.ifEmpty { UUID.randomUUID().toString() }
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

    fun blockProvider(id: String, isBlock: Boolean, onComplete: () -> Unit = {}) {
        db.collection("service_providers").document(id).update("isBlocked", isBlock)
            .addOnSuccessListener {
                logActivity("المشرف", "تغيير حالة حظر المهني $id إلى $isBlock")
                onComplete()
            }
    }

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
                db.collection("service_providers").document(r.providerId).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val currentSum = doc.getDouble("ratingSum")?.toFloat() ?: 0.0f
                            val currentCount = doc.getLong("ratingCount")?.toInt() ?: 0
                            val loyaltyPoints = doc.getLong("loyaltyPoints")?.toInt() ?: 0
                            
                            val updates = mapOf(
                                "ratingSum" to (currentSum + r.rating),
                                "ratingCount" to (currentCount + 1),
                                "loyaltyPoints" to (loyaltyPoints + 15)
                             )
                            db.collection("service_providers").document(r.providerId).update(updates)
                        }
                        onComplete()
                    }
            }
    }

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

    private fun seedDefaultCategories() {
        db.collection("categories").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                val defaults = listOf(
                    ServiceCategory("cat_1", "صيانة وأعمال مهنية", "Maintenance & Professional", "🛠️"),
                    ServiceCategory("cat_2", "خدمات طبية ورعاية", "Medical & Care Services", "🩺"),
                    ServiceCategory("cat_3", "صيانة حاسوب وهواتف", "Tech & Mobile Fix", "📱"),
                    ServiceCategory("cat_4", "نقل وتوصيل وتكاسي", "Transportation & Delivery", "🚚"),
                    ServiceCategory("cat_5", "تعليم وتدريب", "Education & Training", "📚"),
                    ServiceCategory("cat_6", "مطاعم ومأكولات يمنية", "Restaurants & Food", "🍛"),
                    ServiceCategory("cat_7", "طوارئ وخدمات عامة", "Emergency Services", "🚨"),
                    ServiceCategory("cat_8", "عقارات ومقاولات وبناء", "Real Estate & Housing", "🧱")
                )
                for (cat in defaults) {
                    db.collection("categories").document(cat.id).set(
                        mapOf("id" to cat.id, "nameAr" to cat.nameAr, "nameEn" to cat.nameEn, "iconEmoji" to cat.iconEmoji)
                    )
                }
            }
        }
    }

    // WIPE DATABASE AND REBUILD METHOD TO RESET EVERYTHING TO AN EXTREMELY CLEAN PRISTINE STATE
    fun wipeDatabaseAndRebuild(onComplete: (Boolean, String) -> Unit) {
        if (!_isInitialized.value) {
            onComplete(false, "عذراً، محرك المزامنة غير جاهز حالياً")
            return
        }

        managerScope.launch {
            try {
                // Collections list to clear
                val collections = listOf(
                    "service_providers",
                    "pending_providers",
                    "categories",
                    "banners",
                    "chats",
                    "app_config",
                    "incident_reports",
                    "activity_logs",
                    "reviews"
                )

                // Delete all documents sequentially in background
                for (col in collections) {
                    val snapshots = db.collection(col).get().addOnCompleteListener { /* sync deletion wait */ }
                    // await resolution
                    val docs = try {
                        val taskResult = snapshots.addOnSuccessListener {}.addOnFailureListener {}
                        delay(120) // minor grace delay to process
                        if (taskResult.isSuccessful) taskResult.result else null
                    } catch (e: Exception) {
                        null
                    }

                    if (docs != null && !docs.isEmpty) {
                        val batch = db.batch()
                        for (doc in docs) {
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                        delay(150)
                    }
                }

                // SECURE SEEDING OF NEW FRESH PRISTINE RECORDS
                // 1. Reseed Categories
                val cleanCategories = listOf(
                    ServiceCategory("cat_1", "صيانة وأعمال مهنية", "Maintenance & Professional", "🛠️"),
                    ServiceCategory("cat_2", "خدمات طبية ورعاية", "Medical & Care Services", "🩺"),
                    ServiceCategory("cat_3", "صيانة حاسوب وهواتف", "Tech & Mobile Fix", "📱"),
                    ServiceCategory("cat_4", "نقل وتوصيل وتكاسي", "Transportation & Delivery", "🚚"),
                    ServiceCategory("cat_5", "تعليم وتدريب", "Education & Training", "📚"),
                    ServiceCategory("cat_6", "مطاعم ومأكولات يمنية", "Restaurants & Food", "🍛"),
                    ServiceCategory("cat_7", "طوارئ وخدمات عامة", "Emergency Services", "🚨"),
                    ServiceCategory("cat_8", "عقارات ومقاولات وبناء", "Real Estate & Housing", "🧱")
                )
                for (cat in cleanCategories) {
                    db.collection("categories").document(cat.id).set(
                        mapOf("id" to cat.id, "nameAr" to cat.nameAr, "nameEn" to cat.nameEn, "iconEmoji" to cat.iconEmoji)
                    )
                }

                // 2. Reseed App Configuration settings
                val freshConfig = AppConfig(
                    appName = "دليل الخدمات اليمني الموحد",
                    themeType = AppThemeType.COSMIC_SILVER,
                    logoEmoji = "🇾🇪",
                    welcomeMessage = "مرحباً بكم في الدليل الرقمي والوطني الموحد لخدمات وحرف اليمن 2026!",
                    mainAdminPass = "maher736462"
                )
                db.collection("app_config").document("settings").set(freshConfig.toMap())

                // 3. Reseed Premium/Verified Native Providers across Yemeni Cities (Sana'a, Aden, Taiz, Hadramout)
                val seedProviders = listOf(
                    ServiceProvider(
                        id = "p_1",
                        fullName = "م. ماهر الخولاني",
                        phone = "777644670",
                        whatsapp = "777644670",
                        categoryId = "cat_1",
                        subCategory = "سباكة وتركيب شبكات المياه والمضخات",
                        address = "شارع حدة، أمام مركز الكميم",
                        area = "صنعاء",
                        imageUrl = "https://picsum.photos/id/1012/200/200",
                        isVerified = true,
                        isPinned = true,
                        isRecommended = true,
                        hasPremiumSubscription = true,
                        loyaltyPoints = 320,
                        ratingSum = 25.0f,
                        ratingCount = 5,
                        previewPrice = 1500.0
                    ),
                    ServiceProvider(
                        id = "p_2",
                        fullName = "د. أحمد اليماني",
                        phone = "736462000",
                        whatsapp = "736462000",
                        categoryId = "cat_2",
                        subCategory = "استشاري طب عام ورعاية صحية منزلية",
                        address = "حي التواهي، خلف البنك الأهلي",
                        area = "عدن",
                        imageUrl = "https://picsum.photos/id/1025/200/200",
                        isVerified = true,
                        isPinned = true,
                        isRecommended = true,
                        hasPremiumSubscription = false,
                        loyaltyPoints = 180,
                        ratingSum = 18.0f,
                        ratingCount = 4,
                        previewPrice = 5000.0
                    ),
                    ServiceProvider(
                        id = "p_3",
                        fullName = "صالح الحضرمي",
                        phone = "711222333",
                        whatsapp = "711222333",
                        categoryId = "cat_3",
                        subCategory = "برمجة حاسوب وصيانة بوردات الآيفون والأندرويد",
                        address = "فوة، مقابل هايبر المستهلك",
                        area = "حضرموت",
                        imageUrl = "https://picsum.photos/id/1035/200/200",
                        isVerified = true,
                        isPinned = false,
                        isRecommended = true,
                        hasPremiumSubscription = true,
                        loyaltyPoints = 140,
                        ratingSum = 19.5f,
                        ratingCount = 4,
                        previewPrice = 2000.0
                    ),
                    ServiceProvider(
                        id = "p_4",
                        fullName = "شركة الماهر للنقل السريع",
                        phone = "775556667",
                        whatsapp = "775556667",
                        categoryId = "cat_4",
                        subCategory = "شحن بضائع وسفريات وبريد محافظات سريع",
                        address = "شارع جمال، جوار سينما بلقيس",
                        area = "تعز",
                        imageUrl = "https://picsum.photos/id/1041/200/200",
                        isVerified = true,
                        isPinned = false,
                        isRecommended = false,
                        hasPremiumSubscription = false,
                        loyaltyPoints = 95,
                        ratingSum = 9.0f,
                        ratingCount = 2,
                        previewPrice = 10000.0
                    )
                )

                for (p in seedProviders) {
                    val pMap = mapOf(
                        "fullName" to p.fullName,
                        "phone" to p.phone,
                        "whatsapp" to p.whatsapp,
                        "categoryId" to p.categoryId,
                        "subCategory" to p.subCategory,
                        "address" to p.address,
                        "area" to p.area,
                        "imageUrl" to p.imageUrl,
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
                        "isBlocked" to false,
                        "previewPrice" to p.previewPrice
                    )
                    db.collection("service_providers").document(p.id).set(pMap)
                }

                // 4. Seed Beautiful Banner Ad
                val freshBanner = BannerAd(
                    id = "b_1",
                    title = "مرحباً بكم في خدمات دليلي اليمني الشامل 🇾🇪",
                    imageUrl = "https://picsum.photos/600/300?tmp=1",
                    linkUrl = "https://yemen-services.com",
                    displaySize = "L",
                    durationSeconds = 6,
                    isActive = true
                )
                db.collection("banners").document(freshBanner.id).set(
                    mapOf(
                        "title" to freshBanner.title,
                        "imageUrl" to freshBanner.imageUrl,
                        "linkUrl" to freshBanner.linkUrl,
                        "displaySize" to freshBanner.displaySize,
                        "durationSeconds" to freshBanner.durationSeconds,
                        "isActive" to freshBanner.isActive
                    )
                )

                // 5. Create Initial Fresh Seeding Logs
                val freshLog = ActivityLog(
                    id = "initial_reset_log",
                    user = "النظام",
                    action = "تم إعادة هيكلة، تصفير، وبناء كافة قواعد البيانات وإعادة تهيئة التطبيق بنجاح من الصفر 🇾🇪"
                )
                db.collection("activity_logs").document(freshLog.id).set(
                    mapOf(
                        "user" to freshLog.user,
                        "action" to freshLog.action,
                        "timestamp" to freshLog.timestamp
                    )
                )

                delay(200)
                forceReSubscribe()
                onComplete(true, "تم حذف كافة الملفات والبيانات بالكامل، وتم إعادة تهيئة وبناء التطبيق والشبكة من الصفر بنجاح تام! 🇾🇪")
            } catch (e: Exception) {
                onComplete(false, "حدثت مشكلة أثناء محاولة إعادة التهيئة والسيدر: ${e.message}")
            }
        }
    }
}
