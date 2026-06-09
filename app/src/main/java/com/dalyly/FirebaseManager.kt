package com.dalyly

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

object FirebaseManager {
    private var isInitialized = false
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val activeListeners = mutableListOf<ListenerRegistration>()

    val categories = MutableStateFlow<List<ServiceCategory>>(emptyList())
    val providers = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val banners = MutableStateFlow<List<BannerAd>>(emptyList())
    val incidents = MutableStateFlow<List<IncidentReport>>(emptyList())
    val chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val supervisors = MutableStateFlow<List<Moderator>>(emptyList())
    val config = MutableStateFlow<AppConfig>(AppConfig())
    val citiesList = MutableStateFlow<List<String>>(emptyList())
    val registrationTerms = MutableStateFlow<List<RegistrationTerm>>(emptyList())
    val commercialOffers = MutableStateFlow<List<CommercialOffer>>(emptyList())
    val lastUpdateTime = MutableStateFlow<Long>(System.currentTimeMillis())
    val updateCount = MutableStateFlow<Int>(0)
    val latestPingLatency = MutableStateFlow<Long>(-1L)

    private fun onTelemetryUpdate() {
        updateCount.value = updateCount.value + 1
        lastUpdateTime.value = System.currentTimeMillis()
    }

    fun init(context: Context) {
        if (!isInitialized) {
            isInitialized = true
            
            // Set Firestore offline persistence settings
            try {
                val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true) // Enabled for robust offline caching and seamless transitions without stuttering
                    .build()
                db.setFirestoreSettings(settings)
                Log.d("FirebaseManager", "Firestore cache priority and persistence enabled.")
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error setting Firestore settings", e)
            }
            
            startListening()
        }
    }

    fun sendPing(onComplete: () -> Unit = {}) {
        val start = System.currentTimeMillis()
        db.collection("config").document("ping_test").set(mapOf("time" to start))
            .addOnCompleteListener {
                val end = System.currentTimeMillis()
                latestPingLatency.value = end - start
                onTelemetryUpdate()
                onComplete()
            }
    }

    fun forceNetworkRefresh() {
        Log.d("FirebaseManager", "Force network reconnection triggered. Rebinding listeners...")
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
        startListening()
    }

    fun startListening() {
        if (activeListeners.isNotEmpty()) return

        Log.d("FirebaseManager", "Starting live Firestore sync snapshot listeners...")

        // 1. Config
        val configListener = db.collection("config").document("current_config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to config", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.exists()) {
                        val c = snapshot.toObject(AppConfig::class.java)
                        if (c != null) {
                            config.value = c
                            onTelemetryUpdate()
                        }
                    } else {
                        // Bootstrap default config if not found online
                        val defaultConf = AppConfig()
                        db.collection("config").document("current_config").set(defaultConf)
                            .addOnSuccessListener {
                                Log.d("FirebaseManager", "Bootstrapped config in cloud Firestore.")
                            }
                    }
                }
            }
        activeListeners.add(configListener)

        // 2. Cities
        val citiesListener = db.collection("cities")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to cities", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { it.id }
                    if (list.isEmpty()) {
                        bootstrapCities()
                    } else {
                        citiesList.value = list
                        onTelemetryUpdate()
                    }
                }
            }
        activeListeners.add(citiesListener)

        // 3. Categories
        val catsListener = db.collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to categories", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(ServiceCategory::class.java) }
                    if (list.isEmpty()) {
                        bootstrapCategories()
                    } else {
                        categories.value = list.sortedBy { it.displayOrder }
                        onTelemetryUpdate()
                    }
                }
            }
        activeListeners.add(catsListener)

        // 4. Providers
        val provsListener = db.collection("providers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to providers", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(ServiceProvider::class.java) }
                    providers.value = list.sortedByDescending { it.timestamp }
                    onTelemetryUpdate()
                }
            }
        activeListeners.add(provsListener)

        // 5. Banners
        val bannersListener = db.collection("banners")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to banners", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(BannerAd::class.java) }
                    banners.value = list
                    onTelemetryUpdate()
                }
            }
        activeListeners.add(bannersListener)

        // 6. Incidents
        val incidentsListener = db.collection("incidents")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to incidents", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(IncidentReport::class.java) }
                    incidents.value = list.sortedBy { it.timestamp }
                    onTelemetryUpdate()
                }
            }
        activeListeners.add(incidentsListener)

        // 7. Chats
        val chatsListener = db.collection("chats")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to chats", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                    chats.value = list.sortedBy { it.timestamp }
                    onTelemetryUpdate()
                }
            }
        activeListeners.add(chatsListener)

        // 8. Supervisors
        val supervisorsListener = db.collection("supervisors")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to supervisors", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(Moderator::class.java) }
                    if (list.isEmpty()) {
                        bootstrapSupervisors()
                    } else {
                        supervisors.value = list
                        onTelemetryUpdate()
                    }
                }
            }
        activeListeners.add(supervisorsListener)

        // 9. Registration Terms
        val termsListener = db.collection("registration_terms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to registration terms", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(RegistrationTerm::class.java) }
                    if (list.isEmpty()) {
                        bootstrapTerms()
                    } else {
                        registrationTerms.value = list.sortedBy { it.order }
                        onTelemetryUpdate()
                    }
                }
            }
        activeListeners.add(termsListener)

        // 10. Commercial Offers
        val offersListener = db.collection("commercial_offers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to commercial_offers", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(CommercialOffer::class.java) }
                    commercialOffers.value = list.sortedByDescending { it.timestamp }
                    onTelemetryUpdate()
                }
            }
        activeListeners.add(offersListener)
    }

    // ================== CLOUD SEEDING ON EMPTY STATE ==================

    private fun bootstrapCities() {
        val defaultCities = listOf("صنعاء", "عدن", "تعز", "إب", "حضرموت", "الحديدة", "ذمار")
        defaultCities.forEach { city ->
            db.collection("cities").document(city).set(mapOf("nameAr" to city, "nameEn" to city))
        }
    }

    private fun bootstrapCategories() {
        val defaultCats = listOf(
            ServiceCategory("cat_solar", "طاقة شمسية وكهرباء", "Solar Energy & Electricity", "⚡", true, 0),
            ServiceCategory("cat_mobile", "برمجة وصيانة هواتف", "Software & Mobile Services", "📱", true, 1),
            ServiceCategory("cat_plumbing", "سباكة وتمديدات", "Plumbing Services", "🚰", true, 2),
            ServiceCategory("cat_cleaning", "نظافة وصيانة منزلية", "Cleaning & Home Repair", "🧹", true, 3),
            ServiceCategory("cat_cars", "صيانة سيارات وميكانيك", "Car Maintenance & Repair", "🚗", true, 4)
        )
        defaultCats.forEach { cat ->
            db.collection("categories").document(cat.id).set(cat)
        }
    }

    private fun bootstrapSupervisors() {
        val defaultSupervisors = listOf(
            Moderator("mod_admin_main", "admin", "admin2026"),
            Moderator("mod_ali_main", "ali", "1234")
        )
        defaultSupervisors.forEach { mod ->
            db.collection("supervisors").document(mod.id).set(mod)
        }
    }

    private fun bootstrapTerms() {
        val defaultTerms = listOf(
            RegistrationTerm("term_1", "الالتزام بالمعايير الفنية والمهنية باليمن والمحافظة على أمانة العمل.", 0, true),
            RegistrationTerm("term_2", "عدم تحصيل أي مبالغ إضافية أو رسوم تفوق السعر المتفق عليه بالدليل.", 1, true),
            RegistrationTerm("term_3", "تحمل المسؤولية الكاملة القانونية والأخلاقية عن أي خلل ناتج عن سوء تقديم الخدمة.", 2, true)
        )
        defaultTerms.forEach { term ->
            db.collection("registration_terms").document(term.id).set(term)
        }
    }

    // ================== CLOUD MUTATIONS ==================

    fun updateConfig(newConfig: AppConfig, onComplete: () -> Unit = {}) {
        db.collection("config").document("current_config").set(newConfig)
            .addOnSuccessListener {
                config.value = newConfig
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveCategory(category: ServiceCategory, onComplete: () -> Unit = {}) {
        val targetId = if (category.id.isEmpty()) UUID.randomUUID().toString() else category.id
        val target = category.copy(id = targetId)
        db.collection("categories").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteCategory(id: String, onComplete: () -> Unit = {}) {
        db.collection("categories").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveProvider(provider: ServiceProvider, onComplete: () -> Unit = {}) {
        val targetId = if (provider.id.isEmpty()) UUID.randomUUID().toString() else provider.id
        val target = provider.copy(id = targetId)
        db.collection("providers").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteProvider(id: String, onComplete: () -> Unit = {}) {
        db.collection("providers").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveBanner(banner: BannerAd, onComplete: () -> Unit = {}) {
        val targetId = if (banner.id.isEmpty()) UUID.randomUUID().toString() else banner.id
        val target = banner.copy(id = targetId)
        db.collection("banners").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteBanner(id: String, onComplete: () -> Unit = {}) {
        db.collection("banners").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveIncident(report: IncidentReport, onComplete: () -> Unit = {}) {
        val targetId = if (report.id.isEmpty()) UUID.randomUUID().toString() else report.id
        val target = report.copy(id = targetId)
        db.collection("incidents").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteIncident(id: String, onComplete: () -> Unit = {}) {
        db.collection("incidents").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun sendChatMessage(msg: ChatMessage, onComplete: () -> Unit = {}) {
        val targetId = if (msg.id.isEmpty()) UUID.randomUUID().toString() else msg.id
        val targetMsgId = if (msg.messageId.isEmpty()) UUID.randomUUID().toString() else msg.messageId
        val target = msg.copy(id = targetId, messageId = targetMsgId)
        db.collection("chats").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun clearChats(onComplete: () -> Unit = {}) {
        db.collection("chats").get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit().addOnCompleteListener {
                    onTelemetryUpdate()
                    onComplete()
                }
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveSupervisor(mod: Moderator, onComplete: () -> Unit = {}) {
        val targetId = if (mod.id.isEmpty()) UUID.randomUUID().toString() else mod.id
        val target = mod.copy(id = targetId)
        db.collection("supervisors").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteSupervisor(id: String, onComplete: () -> Unit = {}) {
        db.collection("supervisors").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveCity(cityAr: String, cityEn: String, onComplete: () -> Unit = {}) {
        val cityData = mapOf("nameAr" to cityAr, "nameEn" to cityEn)
        db.collection("cities").document(cityAr).set(cityData)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteCity(cityName: String, onComplete: () -> Unit = {}) {
        db.collection("cities").document(cityName).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveRegistrationTerm(term: RegistrationTerm, onComplete: () -> Unit = {}) {
        val targetId = if (term.id.isEmpty()) UUID.randomUUID().toString() else term.id
        val target = term.copy(id = targetId)
        db.collection("registration_terms").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteRegistrationTerm(id: String, onComplete: () -> Unit = {}) {
        db.collection("registration_terms").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun saveCommercialOffer(offer: CommercialOffer, onComplete: () -> Unit = {}) {
        val targetId = if (offer.id.isEmpty()) UUID.randomUUID().toString() else offer.id
        val target = offer.copy(id = targetId)
        db.collection("commercial_offers").document(targetId).set(target)
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }

    fun deleteCommercialOffer(id: String, onComplete: () -> Unit = {}) {
        db.collection("commercial_offers").document(id).delete()
            .addOnSuccessListener {
                onTelemetryUpdate()
                onComplete()
            }
            .addOnFailureListener {
                onComplete()
            }
    }
}
