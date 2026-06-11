package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    // State flows representing global real-time cloud data pools
    val appConfig = MutableStateFlow(AppConfig())
    val categories = MutableStateFlow<List<ServiceCategory>>(emptyList())
    val providers = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val pendingProviders = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val banners = MutableStateFlow<List<BannerAd>>(emptyList())
    val chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val incidentReports = MutableStateFlow<List<IncidentReport>>(emptyList())
    val activityLogs = MutableStateFlow<List<ActivityLog>>(emptyList())
    val moderators = MutableStateFlow<List<Moderator>>(emptyList())
    val customColors = MutableStateFlow<List<CustomColorTheme>>(emptyList())
    val commercialCategories = MutableStateFlow<List<CommercialCategory>>(emptyList())
    val commercialShops = MutableStateFlow<List<CommercialShop>>(emptyList())
    val commercialItems = MutableStateFlow<List<CommercialItem>>(emptyList())
    val citiesList = MutableStateFlow(listOf("صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب"))
    val isProvidersDataFromCache = MutableStateFlow(false)
    val bookings = MutableStateFlow<List<ServiceBooking>>(emptyList())
    val notifications = MutableStateFlow<List<AppNotification>>(emptyList())

    private val db get() = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    fun init(context: Context) {
        if (_isInitialized.value) return
        try {
            startListeningToAll()
            _isInitialized.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Initialization Error: ${e.message}")
            _isInitialized.value = true
        }
    }

    fun startListeningToAll() {
        removeAllListeners()

        // 1. AppConfig
        listeners.add(db.collection("app_config").document("main").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to app_config: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    appConfig.value = AppConfig.fromMap(data)
                }
            } else {
                val defConfig = AppConfig(
                    appName = "دليل الخدمات اليمني الموحد",
                    themeType = AppThemeType.COSMIC_SILVER,
                    logoEmoji = "🇾🇪",
                    welcomeMessage = "مرحباً بكم في الدليل الرقمي والوطني الموحد لخدمات وحرف اليمن 2026!",
                    mainAdminPass = "maher736462"
                )
                saveAppConfig(defConfig)
            }
        })

        // 2. Categories
        listeners.add(db.collection("categories").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { serviceCategoryFromMap(doc.id, it) }
                }
                categories.value = list
                if (list.isEmpty()) {
                    seedDefaultCategories()
                }
            }
        })

        // 3. Providers
        listeners.add(db.collection("providers").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { serviceProviderFromMap(doc.id, it) }
                }
                providers.value = list
                isProvidersDataFromCache.value = false
                if (list.isEmpty()) {
                    seedDefaultProviders()
                }
            }
        })

        // 4. Pending Providers
        listeners.add(db.collection("pending_providers").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                pendingProviders.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { serviceProviderFromMap(doc.id, it) }
                }
            }
        })

        // 5. Banners
        listeners.add(db.collection("banners").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { bannerAdFromMap(doc.id, it) }
                }
                banners.value = list
                if (list.isEmpty()) {
                    seedDefaultBanners()
                }
            }
        })

        // 6. Chats
        listeners.add(db.collection("chats").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                chats.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { chatMessageFromMap(doc.id, it) }
                }.sortedBy { it.timestamp }
            }
        })

        // 7. Incident Reports
        listeners.add(db.collection("incident_reports").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                incidentReports.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { incidentReportFromMap(doc.id, it) }
                }
            }
        })

        // 8. Activity Logs
        listeners.add(db.collection("activity_logs").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                activityLogs.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { activityLogFromMap(doc.id, it) }
                }.sortedByDescending { it.timestamp }
            }
        })

        // 9. Moderators
        listeners.add(db.collection("moderators").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                moderators.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { moderatorFromMap(doc.id, it) }
                }
            }
        })

        // 10. Custom Colors
        listeners.add(db.collection("custom_colors").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { customColorThemeFromMap(doc.id, it) }
                }
                customColors.value = list
                if (list.isEmpty()) {
                    seedDefaultCustomColors()
                }
            }
        })

        // 11. Commercial Categories
        listeners.add(db.collection("commercial_categories").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { commercialCategoryFromMap(doc.id, it) }
                }
                commercialCategories.value = list
                if (list.isEmpty()) {
                    seedDefaultCommercialCategories()
                }
            }
        })

        // 12. Commercial Shops
        listeners.add(db.collection("commercial_shops").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { commercialShopFromMap(doc.id, it) }
                }
                commercialShops.value = list
                if (list.isEmpty()) {
                    seedDefaultCommercialShops()
                }
            }
        })

        // 13. Commercial Items
        listeners.add(db.collection("commercial_items").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { commercialItemFromMap(doc.id, it) }
                }
                commercialItems.value = list
                if (list.isEmpty()) {
                    seedDefaultCommercialItems()
                }
            }
        })

        // 14. Bookings
        listeners.add(db.collection("bookings").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                bookings.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { serviceBookingFromMap(doc.id, it) }
                }.sortedByDescending { it.timestamp }
            }
        })

        // 15. Notifications
        listeners.add(db.collection("notifications").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                notifications.value = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { appNotificationFromMap(doc.id, it) }
                }.sortedByDescending { it.timestamp }
            }
        })
    }

    fun startListeningToChats(onUpdate: (List<ChatMessage>) -> Unit = {}) {
        onUpdate(chats.value)
    }

    fun removeAllListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    fun forceReSubscribe() {
        startListeningToAll()
    }

    fun saveAppConfig(config: AppConfig) {
        db.collection("app_config").document("main").set(config.toMap())
    }

    fun submitCandidateProvider(p: ServiceProvider, imageBytes: ByteArray?, idCardBytes: ByteArray?, onComplete: (Boolean, String) -> Unit) {
        val id = UUID.randomUUID().toString()
        val profileUrl = if (p.imageUrl.isEmpty()) "https://picsum.photos/200/300?tmp=${UUID.randomUUID()}" else p.imageUrl
        val idCardUrl = if (p.idCardUrl.isEmpty()) "https://picsum.photos/400/300?tmp=${UUID.randomUUID()}" else p.idCardUrl

        val newPending = p.copy(id = id, imageUrl = profileUrl, idCardUrl = idCardUrl)
        db.collection("pending_providers").document(id).set(newPending.toMap())

        logActivity("طلب إنضمام جديد", "مقدم جديد: ${p.fullName}")

        val config = appConfig.value
        if (config.notificationsAllEnabled) {
            sendNotification(
                AppNotification(
                    id = UUID.randomUUID().toString(),
                    titleAr = "تم استلام الطلب",
                    titleEn = "Request Received",
                    contentAr = config.notifyOnJoinRequestAr,
                    contentEn = config.notifyOnJoinRequestEn,
                    isPublic = false,
                    targetId = p.phone
                )
            )
        }
        onComplete(true, "تم إرسال طلب الانضمام بنجاح! سيتم مراجعته وتوثيقه من قبل المشرفين قريباً جداً.")
    }

    fun approveProvider(id: String, onComplete: () -> Unit = {}) {
        val candidate = pendingProviders.value.find { it.id == id } ?: return
        val approved = candidate.copy(
            isVerified = true,
            isPinned = false,
            isRecommended = false,
            hasPremiumSubscription = false,
            loyaltyPoints = 50,
            ratingSum = 5.0f,
            ratingCount = 1,
            isBlocked = false
        )

        db.collection("pending_providers").document(id).delete()
        db.collection("providers").document(id).set(approved.toMap())

        logActivity("المشرف", "الموافقة على الحرفي: ${candidate.fullName}")

        val config = appConfig.value
        if (config.notificationsAllEnabled) {
            sendNotification(
                AppNotification(
                    id = UUID.randomUUID().toString(),
                    titleAr = "تم تفعيل حسابك",
                    titleEn = "Account Activated",
                    contentAr = config.notifyOnJoinApproveAr,
                    contentEn = config.notifyOnJoinApproveEn,
                    isPublic = false,
                    targetId = candidate.phone
                )
            )
        }
        onComplete()
    }

    fun rejectProvider(id: String, reason: String, onComplete: () -> Unit = {}) {
        val candidate = pendingProviders.value.find { it.id == id }
        db.collection("pending_providers").document(id).delete()

        logActivity("المشرف", "رفض الطلب $id للسبب: $reason")

        val config = appConfig.value
        if (candidate != null && config.notificationsAllEnabled) {
            sendNotification(
                AppNotification(
                    id = UUID.randomUUID().toString(),
                    titleAr = "طلب الانضمام مرفوض",
                    titleEn = "Join Request Rejected",
                    contentAr = "${config.notifyOnJoinRejectAr} السبب: $reason",
                    contentEn = "${config.notifyOnJoinRejectEn} Reason: $reason",
                    isPublic = false,
                    targetId = candidate.phone
                )
            )
        }
        onComplete()
    }

    fun deleteProvider(id: String, onComplete: () -> Unit = {}) {
        db.collection("providers").document(id).delete()
        logActivity("المشرف", "حزف مقدم الخدمة ذو المعرف [$id]")
        onComplete()
    }

    fun addManualProvider(p: ServiceProvider, onComplete: () -> Unit) {
        val id = if (p.id.isEmpty()) UUID.randomUUID().toString() else p.id
        val newP = p.copy(
            id = id,
            imageUrl = p.imageUrl.ifEmpty { "https://picsum.photos/200/300?id=${UUID.randomUUID()}" },
            idCardUrl = "https://picsum.photos/400/300"
        )

        db.collection("providers").document(id).set(newP.toMap())
        logActivity("المشرف", "إضافة مهني يدوياً: ${p.fullName}")
        onComplete()
    }

    fun updateProviderDetails(p: ServiceProvider, onComplete: () -> Unit = {}) {
        db.collection("providers").document(p.id).set(p.toMap())
        logActivity("المشرف", "تعديل بيانات الحرفي المهني: ${p.fullName}")
        onComplete()
    }

    fun updateProviderStatus(id: String, isVerified: Boolean, isPinned: Boolean, isRecom: Boolean, isPremium: Boolean, onComplete: () -> Unit = {}) {
        val p = providers.value.find { it.id == id } ?: return
        val updated = p.copy(
            isVerified = isVerified,
            isPinned = isPinned,
            isRecommended = isRecom,
            hasPremiumSubscription = isPremium
        )
        db.collection("providers").document(id).set(updated.toMap())
        onComplete()
    }

    fun manageCategory(id: String, nameAr: String, nameEn: String, iconEmoji: String, parentId: String = "", isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (isDelete) {
            db.collection("categories").document(id).delete().addOnCompleteListener { onComplete() }
        } else {
            val item = ServiceCategory(id, nameAr, nameEn, iconEmoji, parentId)
            db.collection("categories").document(id).set(item.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun manageCustomColor(theme: CustomColorTheme, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (isDelete) {
            db.collection("custom_colors").document(theme.id).delete().addOnCompleteListener { onComplete() }
        } else {
            db.collection("custom_colors").document(theme.id).set(theme.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun manageCommercialCategory(cat: CommercialCategory, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (isDelete) {
            db.collection("commercial_categories").document(cat.id).delete().addOnCompleteListener { onComplete() }
        } else {
            db.collection("commercial_categories").document(cat.id).set(cat.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun manageCommercialShop(shop: CommercialShop, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (isDelete) {
            db.collection("commercial_shops").document(shop.id).delete().addOnCompleteListener { onComplete() }
        } else {
            db.collection("commercial_shops").document(shop.id).set(shop.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun manageCommercialItem(item: CommercialItem, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (isDelete) {
            db.collection("commercial_items").document(item.id).delete().addOnCompleteListener { onComplete() }
        } else {
            db.collection("commercial_items").document(item.id).set(item.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun manageModerator(username: String, secretKey: String, permissions: String, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        if (isDelete) {
            db.collection("moderators").document(username).delete().addOnCompleteListener { onComplete() }
        } else {
            val item = Moderator(id = username, username = username, password = secretKey, secretKey = secretKey, permissions = permissions)
            db.collection("moderators").document(username).set(item.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun sendChatMessage(msg: ChatMessage, onComplete: () -> Unit = {}) {
        val id = if (msg.id.isEmpty()) UUID.randomUUID().toString() else msg.id
        val newMsg = msg.copy(id = id)
        db.collection("chats").document(id).set(newMsg.toMap()).addOnCompleteListener { onComplete() }
    }

    fun wipeChatLogs(onComplete: () -> Unit = {}) {
        db.collection("chats").get().addOnSuccessListener { snaps ->
            val batch = db.batch()
            for (doc in snaps) {
                batch.delete(doc.reference)
            }
            batch.commit().addOnCompleteListener {
                logActivity("المدير", "تم تفريغ المحادثات بالكامل.")
                onComplete()
            }
        }.addOnFailureListener {
            onComplete()
        }
    }

    fun manageBanner(b: BannerAd, isDelete: Boolean = false, onComplete: () -> Unit = {}) {
        val id = if (b.id.isEmpty()) UUID.randomUUID().toString() else b.id
        val item = b.copy(id = id, imageUrl = b.imageUrl.ifEmpty { "https://picsum.photos/600/300?v=${UUID.randomUUID()}" })

        if (isDelete) {
            db.collection("banners").document(id).delete().addOnCompleteListener { onComplete() }
        } else {
            db.collection("banners").document(id).set(item.toMap()).addOnCompleteListener { onComplete() }
        }
    }

    fun blockProvider(id: String, isBlock: Boolean, onComplete: () -> Unit = {}) {
        val p = providers.value.find { it.id == id } ?: return
        val updated = p.copy(isBlocked = isBlock)
        db.collection("providers").document(id).set(updated.toMap()).addOnCompleteListener {
            logActivity("المشرف", "تغيير حالة حظر المهني $id إلى $isBlock")
            onComplete()
        }
    }

    fun addProviderReview(r: ProviderReview, onComplete: () -> Unit = {}) {
        val p = providers.value.find { it.id == r.providerId } ?: return
        val updated = p.copy(
            ratingSum = p.ratingSum + r.rating,
            ratingCount = p.ratingCount + 1,
            loyaltyPoints = p.loyaltyPoints + 15
        )
        db.collection("providers").document(r.providerId).set(updated.toMap()).addOnCompleteListener { onComplete() }
    }

    fun submitIncidentReport(rep: IncidentReport, onComplete: () -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        val newRep = rep.copy(id = id)
        db.collection("incident_reports").document(id).set(newRep.toMap()).addOnCompleteListener { onComplete() }
    }

    fun logActivity(user: String, action: String) {
        val id = UUID.randomUUID().toString()
        val item = ActivityLog(id = id, user = user, action = action, timestamp = System.currentTimeMillis())
        db.collection("activity_logs").document(id).set(item.toMap())
    }

    fun addBooking(b: ServiceBooking, onComplete: () -> Unit = {}) {
        val id = if (b.id.isEmpty()) UUID.randomUUID().toString() else b.id
        val newBooking = b.copy(id = id)
        db.collection("bookings").document(id).set(newBooking.toMap()).addOnCompleteListener {
            logActivity("حجز موعد", "طلب حجز جديد من ${b.userName} مع ${b.providerName}")

            val config = appConfig.value
            if (config.notificationsAllEnabled) {
                sendNotification(
                    AppNotification(
                        id = UUID.randomUUID().toString(),
                        titleAr = "طلب حجز قيد الانتظار",
                        titleEn = "Booking Request Pending",
                        contentAr = "تم تسجيل طلب حجز موعدك مع ${b.providerName} بنجاح وهو قيد الانتظار الحالي.",
                        contentEn = "Your booking request with ${b.providerName} is now pending.",
                        isPublic = false,
                        targetId = b.userPhone
                    )
                )
            }
            onComplete()
        }
    }

    fun updateBookingStatus(id: String, status: String, onComplete: () -> Unit = {}) {
        val b = bookings.value.find { it.id == id } ?: return
        val updated = b.copy(status = status)
        db.collection("bookings").document(id).set(updated.toMap()).addOnCompleteListener {
            logActivity("تحديث الحجز", "تعديل حالة الحجز $id لـ $status")

            if (appConfig.value.notificationsAllEnabled) {
                val statusTextAr = when(status) {
                    "ACCEPTED" -> "مقبول"
                    "COMPLETED" -> "مكتمل"
                    else -> "قيد الانتظار"
                }
                val statusTextEn = when(status) {
                    "ACCEPTED" -> "Accepted"
                    "COMPLETED" -> "Completed"
                    else -> "Pending"
                }
                sendNotification(
                    AppNotification(
                        id = UUID.randomUUID().toString(),
                        titleAr = "تحديث حالة حجز الموعد",
                        titleEn = "Booking Status Updated",
                        contentAr = "تمت الموافقة وتحديث حالة حجزك مع ${b.providerName} إلى: $statusTextAr",
                        contentEn = "Your booking status with ${b.providerName} has been updated to: $statusTextEn",
                        isPublic = false,
                        targetId = b.userPhone
                    )
                )
            }
            onComplete()
        }
    }

    fun deleteBooking(id: String, onComplete: () -> Unit = {}) {
        db.collection("bookings").document(id).delete().addOnCompleteListener {
            logActivity("حذف حجز", "حذف حجز موعد ذو المعرف [$id]")
            onComplete()
        }
    }

    fun sendNotification(n: AppNotification, onComplete: () -> Unit = {}) {
        val id = if (n.id.isEmpty()) UUID.randomUUID().toString() else n.id
        db.collection("notifications").document(id).set(n.copy(id = id).toMap()).addOnCompleteListener { onComplete() }
    }

    fun deleteNotification(id: String, onComplete: () -> Unit = {}) {
        db.collection("notifications").document(id).delete().addOnCompleteListener { onComplete() }
    }

    private fun seedDefaultCategories() {
        val defaultCats = listOf(
            ServiceCategory("cat_1", "صيانة وأعمال مهنية", "Maintenance & Professional", "🛠️"),
            ServiceCategory("cat_2", "خدمات طبية ورعاية", "Medical & Care Services", "🩺"),
            ServiceCategory("cat_3", "صيانة حاسوب وهواتف", "Tech & Mobile Fix", "📱"),
            ServiceCategory("cat_4", "نقل وتوصيل وتكاسي", "Transportation & Delivery", "🚚"),
            ServiceCategory("cat_5", "تعليم وتدريب", "Education & Training", "📚"),
            ServiceCategory("cat_6", "مطاعم ومأكولات يمنية", "Restaurants & Food", "🍛"),
            ServiceCategory("cat_7", "طوارئ وخدمات عامة", "Emergency Services", "🚨"),
            ServiceCategory("cat_8", "عقارات ومقاولات وبناء", "Real Estate & Housing", "🧱"),
            ServiceCategory("cat_elec", "صيانة كهرباء ومولدات", "Electricity & Generators", "⚡", "cat_1"),
            ServiceCategory("cat_plumb", "سباكة وصحي", "Plumbing & Sanitary", "🚰", "cat_1"),
            ServiceCategory("cat_maint", "صيانة وإنشاءات عامة", "General Maintenance", "🔧", "cat_1"),
            ServiceCategory("cat_med", "عيادات وأطقم طبية", "Clinics & Doctors", "🏥", "cat_2"),
            ServiceCategory("cat_edu", "تدريس ومدرسين خصوصي", "Teachers & Education", "🎓", "cat_5"),
            ServiceCategory("cat_law", "محاماة واستشارات قانونية", "Law & Legal Consultations", "⚖️", "cat_7")
        )
        val batch = db.batch()
        for (cat in defaultCats) {
            batch.set(db.collection("categories").document(cat.id), cat.toMap())
        }
        batch.commit()
    }

    private fun seedDefaultProviders() {
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
        val batch = db.batch()
        for (p in seedProviders) {
            batch.set(db.collection("providers").document(p.id), p.toMap())
        }
        batch.commit()
    }

    private fun seedDefaultBanners() {
        val seedBanners = listOf(
            BannerAd(
                id = "b_1",
                title = "مرحباً بكم في خدمات دليلي اليمني الشامل 🇾🇪",
                imageUrl = "https://picsum.photos/600/300?tmp=1",
                linkUrl = "https://yemen-services.com",
                displaySize = "L",
                durationSeconds = 6,
                isActive = true
            )
        )
        val batch = db.batch()
        for (b in seedBanners) {
            batch.set(db.collection("banners").document(b.id), b.toMap())
        }
        batch.commit()
    }

    private fun seedDefaultCustomColors() {
        val seedCustomColors = listOf(
            CustomColorTheme("color_navy", "كحلي كلاسيك", "Classic Navy", "#FF0A1324", "#FF111F36", "#FF182B4B", "#FFE6F1FC", "#FF38BDF8", "#FF818CF8", "#FF475569")
        )
        val batch = db.batch()
        for (c in seedCustomColors) {
            batch.set(db.collection("custom_colors").document(c.id), c.toMap())
        }
        batch.commit()
    }

    private fun seedDefaultCommercialCategories() {
        val defaultCommercialCats = listOf(
            CommercialCategory("comm_elec", "أدوات ومواد كهربائية", "Electrical Equipment", "🔌"),
            CommercialCategory("comm_phone", "تلفونات وملحقاتها", "Phones & Accessories", "📱"),
            CommercialCategory("comm_plumb", "مواد سباكة وحدادة ومقاولات", "Plumbing & Hardware", "🛠️")
        )
        val batch = db.batch()
        for (cc in defaultCommercialCats) {
            batch.set(db.collection("commercial_categories").document(cc.id), cc.toMap())
        }
        batch.commit()
    }

    private fun seedDefaultCommercialShops() {
        val defaultShops = listOf(
            CommercialShop("shop_1", "محلات البرق لمواد البناء والأجهزة", "Al-Barq Materials & Tools", "777644670", "777644670", "اليمن - صنعاء - شارع صخر", "")
        )
        val batch = db.batch()
        for (sh in defaultShops) {
            batch.set(db.collection("commercial_shops").document(sh.id), sh.toMap())
        }
        batch.commit()
    }

    private fun seedDefaultCommercialItems() {
        val defaultItems = listOf(
            CommercialItem("item_1", "comm_elec", "shop_1", "لفة كابل كهرباء يمني أصلي 4ملم", "Yemeni Electric Wire 4mm", 18500.0, 30, "", "سلك نحاسي نقي بمواصفات ومقاييس يمنية فاخرة مقاومة للضغط العالي", "توصيل صنعاء خلال 24 ساعة"),
            CommercialItem("item_2", "comm_phone", "shop_1", "شاحن سفري ذكي أصلي بقوة 120 واط", "Xiaomi Travel Power Charger 120W", 14000.0, 15, "", "شاحن بمخرجات ذكية معتمد مع نظام تبريد وحماية فائق الكفاءة", "توصيل فوري لجميع المدن"),
            CommercialItem("item_3", "comm_plumb", "shop_1", "صنبور مياه إيطالي أصلي نحاس ثقيل", "Italian Brass Water Tap 1 Inch", 4200.0, 50, "", "محبس نحاسي إيطالي متين ومقاوم للصدأ بطبقة مصفحة", "توصيل عبر حافلات البريد")
        )
        val batch = db.batch()
        for (i in defaultItems) {
            batch.set(db.collection("commercial_items").document(i.id), i.toMap())
        }
        batch.commit()
    }

    fun wipeDatabaseAndRebuild(onComplete: (Boolean, String) -> Unit) {
        managerScope.launch {
            try {
                val collections = listOf(
                    "categories", "providers", "pending_providers", "banners", "chats",
                    "incident_reports", "activity_logs", "moderators", "custom_colors",
                    "commercial_categories", "commercial_shops", "commercial_items",
                    "bookings", "notifications"
                )

                // Delete all collections in Firestore
                for (col in collections) {
                    db.collection(col).get().addOnSuccessListener { snaps ->
                        val batch = db.batch()
                        for (doc in snaps) {
                            batch.delete(doc.reference)
                        }
                        batch.commit()
                    }
                }

                delay(1000)

                // Re-seed all defaults
                seedDefaultCategories()
                seedDefaultProviders()
                seedDefaultBanners()
                seedDefaultCustomColors()
                seedDefaultCommercialCategories()
                seedDefaultCommercialShops()
                seedDefaultCommercialItems()

                val log = ActivityLog(
                    id = "initial_reset_log",
                    user = "النظام",
                    action = "تم إعادة هيكلة، تصفير، وبناء كافة قواعد البيانات وإعادة تهيئة التطبيق بنجاح من الصفر 🇾🇪"
                )
                db.collection("activity_logs").document(log.id).set(log.toMap())

                val defaultCfg = AppConfig(
                    appName = "دليل الخدمات اليمني الموحد",
                    themeType = AppThemeType.COSMIC_SILVER,
                    logoEmoji = "🇾🇪",
                    welcomeMessage = "مرحباً بكم في الدليل الرقمي والوطني الموحد لخدمات وحرف اليمن 2026!",
                    mainAdminPass = "maher736462"
                )
                db.collection("app_config").document("main").set(defaultCfg.toMap())

                delay(1000)
                onComplete(true, "تم حذف كافة الملفات والبيانات بالكامل، وتم إعادة تهيئة وبناء التطبيق والشبكة من الصفر بنجاح تام! 🇾🇪")
            } catch (e: Exception) {
                onComplete(false, "حدثت مشكلة أثناء محاولة إعادة التهيئة: ${e.message}")
            }
        }
    }

    fun serviceCategoryFromMap(id: String, map: Map<String, Any>): ServiceCategory {
        return ServiceCategory(
            id = id,
            nameAr = map["nameAr"] as? String ?: "",
            nameEn = map["nameEn"] as? String ?: "",
            iconEmoji = map["iconEmoji"] as? String ?: "",
            parentId = map["parentId"] as? String ?: ""
        )
    }

    fun customColorThemeFromMap(id: String, map: Map<String, Any>): CustomColorTheme {
        return CustomColorTheme(
            id = id,
            nameAr = map["nameAr"] as? String ?: "",
            nameEn = map["nameEn"] as? String ?: "",
            backgroundHex = map["backgroundHex"] as? String ?: "#FF0F1016",
            surfaceHex = map["surfaceHex"] as? String ?: "#FF1E2230",
            surfaceVariantHex = map["surfaceVariantHex"] as? String ?: "#FF24293D",
            primaryHex = map["primaryHex"] as? String ?: "#FFE2E8F0",
            secondaryHex = map["secondaryHex"] as? String ?: "#FF38BDF8",
            tertiaryHex = map["tertiaryHex"] as? String ?: "#FF818CF8",
            outlineHex = map["outlineHex"] as? String ?: "#FF475569"
        )
    }

    fun commercialCategoryFromMap(id: String, map: Map<String, Any>): CommercialCategory {
        return CommercialCategory(
            id = id,
            nameAr = map["nameAr"] as? String ?: "",
            nameEn = map["nameEn"] as? String ?: "",
            iconEmoji = map["iconEmoji"] as? String ?: "🛒",
            imageUrl = map["imageUrl"] as? String ?: ""
        )
    }

    fun commercialShopFromMap(id: String, map: Map<String, Any>): CommercialShop {
        return CommercialShop(
            id = id,
            nameAr = map["nameAr"] as? String ?: "",
            nameEn = map["nameEn"] as? String ?: "",
            phone = map["phone"] as? String ?: "",
            whatsapp = map["whatsapp"] as? String ?: "",
            address = map["address"] as? String ?: "",
            logoUrl = map["logoUrl"] as? String ?: ""
        )
    }

    fun commercialItemFromMap(id: String, map: Map<String, Any>): CommercialItem {
        return CommercialItem(
            id = id,
            categoryId = map["categoryId"] as? String ?: "",
            shopId = map["shopId"] as? String ?: "",
            nameAr = map["nameAr"] as? String ?: "",
            nameEn = map["nameEn"] as? String ?: "",
            price = (map["price"] as? Number)?.toDouble() ?: 0.0,
            quantity = (map["quantity"] as? Number)?.toInt() ?: 0,
            imageUrl = map["imageUrl"] as? String ?: "",
            description = map["description"] as? String ?: "",
            deliveryMethods = map["deliveryMethods"] as? String ?: "توصيل منزلي"
        )
    }

    fun serviceProviderFromMap(id: String, map: Map<String, Any>): ServiceProvider {
        return ServiceProvider(
            id = id,
            fullName = map["fullName"] as? String ?: "",
            phone = map["phone"] as? String ?: "",
            whatsapp = map["whatsapp"] as? String ?: "",
            categoryId = map["categoryId"] as? String ?: "",
            subCategory = map["subCategory"] as? String ?: "",
            address = map["address"] as? String ?: "",
            area = map["area"] as? String ?: "",
            imageUrl = map["imageUrl"] as? String ?: "",
            idCardUrl = map["idCardUrl"] as? String ?: "",
            gpsLat = (map["gpsLat"] as? Number)?.toDouble() ?: 0.0,
            gpsLng = (map["gpsLng"] as? Number)?.toDouble() ?: 0.0,
            isVerified = map["isVerified"] as? Boolean ?: false,
            isPinned = map["isPinned"] as? Boolean ?: false,
            isRecommended = map["isRecommended"] as? Boolean ?: false,
            hasPremiumSubscription = map["hasPremiumSubscription"] as? Boolean ?: false,
            loyaltyPoints = (map["loyaltyPoints"] as? Number)?.toInt() ?: 0,
            ratingSum = (map["ratingSum"] as? Number)?.toFloat() ?: 0.0f,
            ratingCount = (map["ratingCount"] as? Number)?.toInt() ?: 0,
            isBlocked = map["isBlocked"] as? Boolean ?: false,
            previewPrice = (map["previewPrice"] as? Number)?.toDouble() ?: 0.0,
            experienceYears = (map["experienceYears"] as? Number)?.toInt() ?: 3,
            portfolioImages = map["portfolioImages"] as? String ?: "",
            workDescriptionAr = map["workDescriptionAr"] as? String ?: "خبرة ممتازة في تصنيع وصيانة كافة الأعمال بمهنية وجودة عالية في اليمن.",
            workDescriptionEn = map["workDescriptionEn"] as? String ?: "Excellent professional experience in all maintenance and setup work in Yemen.",
            hideProfileDetails = map["hideProfileDetails"] as? Boolean ?: false
        )
    }

    fun serviceBookingFromMap(id: String, map: Map<String, Any>): ServiceBooking {
        return ServiceBooking(
            id = id,
            providerId = map["providerId"] as? String ?: "",
            providerName = map["providerName"] as? String ?: "",
            userName = map["userName"] as? String ?: "",
            userPhone = map["userPhone"] as? String ?: "",
            bookingTime = map["bookingTime"] as? String ?: "",
            status = map["status"] as? String ?: "PENDING",
            notes = map["notes"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun appNotificationFromMap(id: String, map: Map<String, Any>): AppNotification {
        return AppNotification(
            id = id,
            titleAr = map["titleAr"] as? String ?: "",
            titleEn = map["titleEn"] as? String ?: "",
            contentAr = map["contentAr"] as? String ?: "",
            contentEn = map["contentEn"] as? String ?: "",
            isPublic = map["isPublic"] as? Boolean ?: false,
            targetId = map["targetId"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun bannerAdFromMap(id: String, map: Map<String, Any>): BannerAd {
        return BannerAd(
            id = id,
            title = map["title"] as? String ?: "",
            imageUrl = map["imageUrl"] as? String ?: "",
            linkUrl = map["linkUrl"] as? String ?: "",
            displaySize = map["displaySize"] as? String ?: "M",
            durationSeconds = (map["durationSeconds"] as? Number)?.toInt() ?: 5,
            isActive = map["isActive"] as? Boolean ?: true
        )
    }

    fun chatMessageFromMap(id: String, map: Map<String, Any>): ChatMessage {
        return ChatMessage(
            id = id,
            senderId = map["senderId"] as? String ?: "",
            senderName = map["senderName"] as? String ?: "",
            receiverId = map["receiverId"] as? String ?: "",
            content = map["content"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun incidentReportFromMap(id: String, map: Map<String, Any>): IncidentReport {
        return IncidentReport(
            id = id,
            providerId = map["providerId"] as? String ?: "",
            providerName = map["providerName"] as? String ?: "",
            reporterName = map["reporterName"] as? String ?: "",
            reason = map["reason"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun activityLogFromMap(id: String, map: Map<String, Any>): ActivityLog {
        return ActivityLog(
            id = id,
            user = map["user"] as? String ?: "",
            action = map["action"] as? String ?: "",
            timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    fun moderatorFromMap(id: String, map: Map<String, Any>): Moderator {
        return Moderator(
            id = id,
            username = map["username"] as? String ?: "",
            password = map["password"] as? String ?: "",
            secretKey = map["secretKey"] as? String ?: "",
            permissions = map["permissions"] as? String ?: ""
        )
    }
}

// Top-level extension functions to support direct clean Firestore serialization
fun ServiceCategory.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "nameAr" to nameAr,
        "nameEn" to nameEn,
        "iconEmoji" to iconEmoji,
        "parentId" to parentId
    )
}

fun CustomColorTheme.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "nameAr" to nameAr,
        "nameEn" to nameEn,
        "backgroundHex" to backgroundHex,
        "surfaceHex" to surfaceHex,
        "surfaceVariantHex" to surfaceVariantHex,
        "primaryHex" to primaryHex,
        "secondaryHex" to secondaryHex,
        "tertiaryHex" to tertiaryHex,
        "outlineHex" to outlineHex
    )
}

fun CommercialCategory.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "nameAr" to nameAr,
        "nameEn" to nameEn,
        "iconEmoji" to iconEmoji,
        "imageUrl" to imageUrl
    )
}

fun CommercialShop.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "nameAr" to nameAr,
        "nameEn" to nameEn,
        "phone" to phone,
        "whatsapp" to whatsapp,
        "address" to address,
        "logoUrl" to logoUrl
    )
}

fun CommercialItem.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "categoryId" to categoryId,
        "shopId" to shopId,
        "nameAr" to nameAr,
        "nameEn" to nameEn,
        "price" to price,
        "quantity" to quantity,
        "imageUrl" to imageUrl,
        "description" to description,
        "deliveryMethods" to deliveryMethods
    )
}

fun ServiceProvider.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "fullName" to fullName,
        "phone" to phone,
        "whatsapp" to whatsapp,
        "categoryId" to categoryId,
        "subCategory" to subCategory,
        "address" to address,
        "area" to area,
        "imageUrl" to imageUrl,
        "idCardUrl" to idCardUrl,
        "gpsLat" to gpsLat,
        "gpsLng" to gpsLng,
        "isVerified" to isVerified,
        "isPinned" to isPinned,
        "isRecommended" to isRecommended,
        "hasPremiumSubscription" to hasPremiumSubscription,
        "loyaltyPoints" to loyaltyPoints,
        "ratingSum" to ratingSum,
        "ratingCount" to ratingCount,
        "isBlocked" to isBlocked,
        "previewPrice" to previewPrice,
        "experienceYears" to experienceYears,
        "portfolioImages" to portfolioImages,
        "workDescriptionAr" to workDescriptionAr,
        "workDescriptionEn" to workDescriptionEn,
        "hideProfileDetails" to hideProfileDetails
    )
}

fun ServiceBooking.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "providerId" to providerId,
        "providerName" to providerName,
        "userName" to userName,
        "userPhone" to userPhone,
        "bookingTime" to bookingTime,
        "status" to status,
        "notes" to notes,
        "timestamp" to timestamp
    )
}

fun AppNotification.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "titleAr" to titleAr,
        "titleEn" to titleEn,
        "contentAr" to contentAr,
        "contentEn" to contentEn,
        "isPublic" to isPublic,
        "targetId" to targetId,
        "timestamp" to timestamp
    )
}

fun BannerAd.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "title" to title,
        "imageUrl" to imageUrl,
        "linkUrl" to linkUrl,
        "displaySize" to displaySize,
        "durationSeconds" to durationSeconds,
        "isActive" to isActive
    )
}

fun ChatMessage.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "senderId" to senderId,
        "senderName" to senderName,
        "receiverId" to receiverId,
        "content" to content,
        "timestamp" to timestamp
    )
}

fun IncidentReport.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "providerId" to providerId,
        "providerName" to providerName,
        "reporterName" to reporterName,
        "reason" to reason,
        "timestamp" to timestamp
    )
}

fun ActivityLog.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "user" to user,
        "action" to action,
        "timestamp" to timestamp
    )
}

fun Moderator.toMap(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "username" to username,
        "password" to password,
        "secretKey" to secretKey,
        "permissions" to permissions
    )
}
