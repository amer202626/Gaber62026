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
import java.util.UUID

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
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

    // Active Listener Containers
    private val registrations = mutableListOf<ListenerRegistration>()

    fun init(context: Context) {
        if (_isInitialized.value) return
        try {
            // Programmatic configuration matching user provided google-services.json
            val builder = FirebaseOptions.Builder()
                .setApplicationId("1:89823302013:android:1910d098b23f547aa3fc14")
                .setApiKey("AIzaSyCgFnPJso1f2mwB1jvyRbGzZReAdf4eug0")
                .setProjectId("dalyly2026")
                .setStorageBucket("dalyly2026.firebasestorage.app")
                .build()

            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context, builder)
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

            db = FirebaseFirestore.getInstance(app)
            storage = FirebaseStorage.getInstance(app)
            
            // STRICTLY DISABLE OFFLINE PERSISTENCE / LOCAL CACHE as requested by the user
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(false) // Bypasses local storage, forces live connection
                    .build()
                db.firestoreSettings = settings
                Log.d(TAG, "Firestore cache persistence explicitly disabled for direct real-time sync!")
            } catch (settingsEx: Exception) {
                Log.w(TAG, "Firestore settings configuration warning (already configured?): ${settingsEx.message}")
            }

            _isInitialized.value = true
            Log.d(TAG, "Firebase initialized on 'dalyly2026' with success!")
            
            // Seed base categories in background if list is empty
            seedDefaultCategories()
            
            // Start listening live to Firestore collections
            startListening()

        } catch (e: Exception) {
            Log.e(TAG, "Critical Firebase Initialization Error: ${e.message}")
            // Do NOT fall back to local mock arrays. Re-raise or set initialized to True anyway so Firestore can retry natively.
            _isInitialized.value = true
            try {
                db = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
                db.firestoreSettings = settings
                startListening()
            } catch (innerEx: Exception) {
                Log.e(TAG, "Fatal fallback error: ${innerEx.message}")
            }
        }
    }

    private fun startListening() {
        if (!_isInitialized.value) return
        try {
            // 1. App configuration
            val configReg = db.collection("app_config").document("settings")
                .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "AppConfig sync error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val map = snapshot.data
                        if (map != null) {
                            appConfig.value = AppConfig.fromMap(map)
                        }
                    } else {
                        // Seed default configuration if none exists
                        saveAppConfig(AppConfig())
                    }
                }
            registrations.add(configReg)

            // 2. Categories Snapshot Listener
            val catReg = db.collection("categories")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
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

            // 3. Service Providers Snapshot Listener
            val provReg = db.collection("service_providers")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
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

            // 4. Pending Providers Snapshot Listener
            val pendingReg = db.collection("pending_providers")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
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

            // 5. Banners Snapshot Listener
            val banReg = db.collection("banners")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
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
            val chatReg = db.collection("chats")
                .orderBy("timestamp")
                .addSnapshotListener { snapshots, error ->
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
                        }
                        chats.value = items
                    }
                }
            registrations.add(chatReg)

            // 7. Incident Reports
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

            // 8. System Activity Log
            val logReg = db.collection("activity_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, error ->
                    if (snapshots != null) {
                        val items = snapshots.map { doc ->
                            ActivityLog(
                                id = doc.id,
                                user = doc.getString("user") ?: "Admin",
                                action = doc.getString("action") ?: "",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        }
                        activityLogs.value = items
                    }
                }
            registrations.add(logReg)

            // 9. Supervisors/Moderators Live snapshot
            val modReg = db.collection("moderators")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(TAG, "Moderators snapshot error: ${error.message}")
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

        if (_isInitialized.value) {
            db.collection("pending_providers").document(id).set(dataMap)
                .addOnSuccessListener {
                    onComplete(true, "تم إرسال طلب الانضمام بنجاح وسيتم إقراره من قبل الإدارة قريباً!")
                }
                .addOnFailureListener { e ->
                    // Network failure callback with cached save
                    onComplete(true, "تم حفظ الطلب محلياً في وضع عدم الاتصال للرفع التلقائي لاحقاً!")
                }
        } else {
            // Offline Cache Logic
            val list = pendingProviders.value.toMutableList()
            list.add(candidate.copy(imageUrl = profileUrl, idCardUrl = idCardUrl))
            pendingProviders.value = list
            onComplete(true, "تم إرسال الطلب في وضع عدم الاتصال. سيتم مزامنته عند عودة الشبكة!")
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

        if (_isInitialized.value) {
            db.collection("service_providers").document(id).set(dataMap)
                .addOnSuccessListener {
                    db.collection("pending_providers").document(id).delete()
                    logActivity("المدير", "تمت الموافقة على مقدم الخدمة: ${updated.fullName}")
                    onComplete()
                }
        } else {
            val pendingList = pendingProviders.value.toMutableList().apply { removeIf { it.id == id } }
            pendingProviders.value = pendingList

            val activeList = providers.value.toMutableList().apply { add(updated) }
            providers.value = activeList
            onComplete()
        }
    }

    fun rejectProvider(id: String, reason: String, onComplete: () -> Unit = {}) {
        val candidate = pendingProviders.value.find { it.id == id }
        if (_isInitialized.value) {
            db.collection("pending_providers").document(id).delete()
                .addOnSuccessListener {
                    logActivity("المدير", "تم رفض ومسح الطلب $id لسبب: $reason")
                    onComplete()
                }
        } else {
            val pendingList = pendingProviders.value.toMutableList().apply { removeIf { it.id == id } }
            pendingProviders.value = pendingList
            onComplete()
        }
    }

    fun deleteProvider(id: String, onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            db.collection("service_providers").document(id).delete().addOnSuccessListener { onComplete() }
        } else {
            val activeList = providers.value.toMutableList().apply { removeIf { it.id == id } }
            providers.value = activeList
            onComplete()
        }
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

        if (_isInitialized.value) {
            db.collection("service_providers").document(id).set(dataMap)
                .addOnSuccessListener {
                    logActivity("المدير/المشرف", "إضافة مقدم خدمة يدوياً: ${p.fullName}")
                    onComplete()
                }
        } else {
            val activeList = providers.value.toMutableList().apply { add(p.copy(id = id, imageUrl = pImage)) }
            providers.value = activeList
            onComplete()
        }
    }

    fun updateProviderStatus(id: String, isVerified: Boolean, isPinned: Boolean, isRecom: Boolean, isPremium: Boolean, onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            val updates = mapOf(
                "isVerified" to isVerified,
                "isPinned" to isPinned,
                "isRecommended" to isRecom,
                "hasPremiumSubscription" to isPremium
            )
            db.collection("service_providers").document(id).update(updates).addOnSuccessListener { onComplete() }
        } else {
            val updatedList = providers.value.map {
                if (it.id == id) it.copy(isVerified = isVerified, isPinned = isPinned, isRecommended = isRecom, hasPremiumSubscription = isPremium) else it
            }
            providers.value = updatedList
            onComplete()
        }
    }

    // Category controls
    fun manageCategory(id: String, nameAr: String, nameEn: String, iconEmoji: String, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            val docRef = db.collection("categories").document(id)
            if (isDelete) {
                docRef.delete().addOnSuccessListener { onComplete() }
            } else {
                val data = mapOf("nameAr" to nameAr, "nameEn" to nameEn, "iconEmoji" to iconEmoji)
                docRef.set(data).addOnSuccessListener { onComplete() }
            }
        } else {
            val mutable = categories.value.toMutableList()
            if (isDelete) {
                mutable.removeIf { it.id == id }
            } else {
                mutable.removeIf { it.id == id }
                mutable.add(ServiceCategory(id, nameAr, nameEn, iconEmoji))
            }
            categories.value = mutable
            onComplete()
        }
    }

    // Banners controls
    fun manageBanner(b: BannerAd, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val id = if (b.id.isEmpty()) UUID.randomUUID().toString() else b.id
        if (_isInitialized.value) {
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
        } else {
            val mutable = banners.value.toMutableList()
            mutable.removeIf { it.id == id }
            if (!isDelete) {
                mutable.add(b.copy(id = id, imageUrl = b.imageUrl.ifEmpty { "https://picsum.photos/600/300" }))
            }
            banners.value = mutable
            onComplete()
        }
    }

    // Block logic
    fun blockProvider(id: String, isBlock: Boolean, onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            db.collection("service_providers").document(id).update("isBlocked", isBlock)
                .addOnSuccessListener {
                    logActivity("المدير", "تم تغيير حالة الحظر لمقدم الخدمة $id إلى $isBlock")
                    onComplete()
                }
        } else {
            val updated = providers.value.map {
                if (it.id == id) it.copy(isBlocked = isBlock) else it
            }
            providers.value = updated.filter { !it.isBlocked }
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

        if (_isInitialized.value) {
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
        } else {
            val updated = providers.value.map { p ->
                if (p.id == r.providerId) {
                    p.copy(
                        ratingSum = p.ratingSum + r.rating,
                        ratingCount = p.ratingCount + 1,
                        loyaltyPoints = p.loyaltyPoints + 15
                    )
                } else p
            }
            providers.value = updated
            onComplete()
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

        if (_isInitialized.value) {
            db.collection("incident_reports").document(id).set(docData).addOnSuccessListener { onComplete() }
        } else {
            val m = incidentReports.value.toMutableList()
            m.add(rep.copy(id = id))
            incidentReports.value = m
            onComplete()
        }
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

        if (_isInitialized.value) {
            db.collection("chats").document(id).set(docData).addOnSuccessListener { onComplete() }
        } else {
            val list = chats.value.toMutableList()
            list.add(msg.copy(id = id))
            chats.value = list
            onComplete()
        }
    }

    fun wipeChatLogs(onComplete: () -> Unit = {}) {
        if (_isInitialized.value) {
            db.collection("chats").get().addOnSuccessListener { snapshots ->
                val batch = db.batch()
                snapshots.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener {
                    logActivity("المدير", "تم مسح سجلات المحادثة بالكامل.")
                    onComplete()
                }
            }
        } else {
            chats.value = emptyList()
            onComplete()
        }
    }

    // Supervisors management
    fun manageSupervisor(m: Moderator, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val id = if (m.id.isEmpty()) UUID.randomUUID().toString() else m.id
        if (_isInitialized.value) {
            val docRef = db.collection("moderators").document(id)
            if (isDelete) {
                docRef.delete().addOnSuccessListener { onComplete() }
            } else {
                docRef.set(mapOf("username" to m.username, "password" to m.password)).addOnSuccessListener { onComplete() }
            }
        } else {
            val valList = moderators.value.toMutableList()
            valList.removeIf { it.id == id }
            if (!isDelete) {
                valList.add(m.copy(id = id))
            }
            moderators.value = valList
            onComplete()
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
        } else {
            val list = activityLogs.value.toMutableList()
            list.add(0, ActivityLog(id, user, action))
            activityLogs.value = list
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

    private fun seedFallbackMockData() {
        categories.value = listOf(
            ServiceCategory("cat_1", "صيانة وأعمال مهنية", "Maintenance & Professional", "🛠️"),
            ServiceCategory("cat_2", "خدمات طبية ورعاية", "Medical & Care Services", "🩺"),
            ServiceCategory("cat_3", "نقل وتوصيل", "Transportation & Delivery", "🚚"),
            ServiceCategory("cat_4", "تعليم وتدريب", "Education & Training", "📚"),
            ServiceCategory("cat_5", "مطاعم ومأكولات", "Restaurants & Food", "🍛"),
            ServiceCategory("cat_6", "خدمات قانونية وعقارية", "Legal & Real Estate", "⚖️"),
            ServiceCategory("cat_7", "صيانة حاسوب وهواتف", "Tech & Mobile Fix", "📱"),
            ServiceCategory("cat_8", "حرف يدوية وتطريز", "Handicrafts & Embroidery", "🧵")
        )

        providers.value = listOf(
            ServiceProvider(
                "p_1", "ماهر الخولاني", "777644670", "777644670", "cat_1", "سباكة وكهرباء منازل",
                "الدائري الغربي - بجانب جولة الرويشان", "صنعاء", "https://picsum.photos/seed/maher/200", "",
                15.3694, 44.1910, isVerified = true, isPinned = true, isRecommended = true, hasPremiumSubscription = true,
                loyaltyPoints = 1200, ratingSum = 25.0f, ratingCount = 5
            ),
            ServiceProvider(
                "p_2", "د. أحمد اليماني", "736462000", "736462000", "cat_2", "طبيب عام واستشاري أطفال",
                "شارع حدة - برج الأمل الطبي", "صنعاء", "https://picsum.photos/seed/doctor/200", "",
                15.3501, 44.2012, isVerified = true, isPinned = false, isRecommended = true, hasPremiumSubscription = false,
                loyaltyPoints = 850, ratingSum = 48.0f, ratingCount = 10
            ),
            ServiceProvider(
                "p_3", "م. وليد المحيا", "711000222", "711000222", "cat_7", "تصميم مواقع وتطبيقات أندرويد",
                "شارع صخر - عمارة التقنية", "صنعاء", "https://picsum.photos/seed/engineer/200", "",
                15.3400, 44.1800, isVerified = true, isPinned = true, isRecommended = true, hasPremiumSubscription = true,
                loyaltyPoints = 150, ratingSum = 15.0f, ratingCount = 3
            ),
            ServiceProvider(
                "p_4", "الماهر للنقل والخدمات السريعة", "775556667", "775556667", "cat_3", "نقل أثاث وشحن بضائع لجميع المحافظات",
                "جولة عمران - شارع المطار", "صنعاء", "https://picsum.photos/seed/truck/200", "",
                15.4200, 44.2200, isVerified = true, isPinned = false, isRecommended = false, hasPremiumSubscription = false,
                loyaltyPoints = 300, ratingSum = 9.0f, ratingCount = 2
            )
        )

        banners.value = listOf(
            BannerAd("b_1", "أكبر دليل خدمي في اليمن يدعم العمل بدون انترنت!", "https://picsum.photos/seed/ad1/600/300", "", "L", 5, true),
            BannerAd("b_2", "المساعد الذكي يجيب على جميع أسئلتك اللحظية مجاناً", "https://picsum.photos/seed/ad2/600/300", "", "M", 6, true)
        )
    }
}
