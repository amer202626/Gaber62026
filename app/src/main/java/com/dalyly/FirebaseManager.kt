package com.dalyly

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow

object FirebaseManager {
    private val db by lazy { FirebaseFirestore.getInstance() }

    val categories = MutableStateFlow<List<ServiceCategory>>(emptyList())
    val providers = MutableStateFlow<List<ServiceProvider>>(emptyList())
    val banners = MutableStateFlow<List<BannerAd>>(emptyList())
    val incidents = MutableStateFlow<List<IncidentReport>>(emptyList())
    val chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val supervisors = MutableStateFlow<List<Moderator>>(emptyList())
    val config = MutableStateFlow<AppConfig>(AppConfig())
    val citiesList = MutableStateFlow<List<String>>(listOf("صنعاء", "عدن", "تعز", "إب", "حضرموت", "الحديدة", "ذمار"))

    private var hasStarted = false

    fun startListening() {
        if (hasStarted) return
        hasStarted = true

        Log.d("FirebaseManager", "Starting live Firestore sync snapshot listeners...")

        // Listen to Config (Singleton)
        db.collection("config").document("current_config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to config", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val current = snapshot.toObject(AppConfig::class.java)
                    if (current != null) {
                        config.value = current
                    }
                } else {
                    // Create default configuration
                    db.collection("config").document("current_config").set(AppConfig())
                }
            }

        // Listen to Categories
        db.collection("categories")
            .orderBy("displayOrder", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to categories", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ServiceCategory::class.java)?.copy(id = doc.id)
                    }
                    if (list.isEmpty()) {
                        seedCategories()
                    } else {
                        categories.value = list
                    }
                }
            }

        // Listen to Providers
        db.collection("providers")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to providers", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ServiceProvider::class.java)?.copy(id = doc.id)
                    }
                    if (list.isEmpty()) {
                        seedProviders()
                    } else {
                        providers.value = list
                    }
                }
            }

        // Listen to Banners
        db.collection("banners")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to banners", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(BannerAd::class.java)?.copy(id = doc.id)
                    }
                    if (list.isEmpty()) {
                        seedBanners()
                    } else {
                        banners.value = list
                    }
                }
            }

        // Listen to Incidents
        db.collection("incidents")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to incidents", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    incidents.value = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(IncidentReport::class.java)?.copy(id = doc.id)
                    }
                }
            }

        // Listen to Chats
        db.collection("chats")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to chats", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    chats.value = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    }
                }
            }

        // Listen to Supervisors
        db.collection("supervisors")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error listening to supervisors", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Moderator::class.java)?.copy(id = doc.id)
                    }
                    if (list.isEmpty()) {
                        seedSupervisors()
                    } else {
                        supervisors.value = list
                    }
                }
            }
    }

    // ================== MUTATION METHODS ==================

    fun updateConfig(newConfig: AppConfig, onComplete: () -> Unit = {}) {
        db.collection("config").document("current_config")
            .set(newConfig)
            .addOnSuccessListener { onComplete() }
    }

    fun saveCategory(category: ServiceCategory, onComplete: () -> Unit = {}) {
        if (category.id.isEmpty()) {
            db.collection("categories").add(category)
                .addOnSuccessListener { onComplete() }
        } else {
            db.collection("categories").document(category.id).set(category)
                .addOnSuccessListener { onComplete() }
        }
    }

    fun deleteCategory(id: String, onComplete: () -> Unit = {}) {
        db.collection("categories").document(id).delete()
            .addOnSuccessListener { onComplete() }
    }

    fun saveProvider(provider: ServiceProvider, onComplete: () -> Unit = {}) {
        if (provider.id.isEmpty()) {
            db.collection("providers").add(provider)
                .addOnSuccessListener { onComplete() }
        } else {
            db.collection("providers").document(provider.id).set(provider)
                .addOnSuccessListener { onComplete() }
        }
    }

    fun deleteProvider(id: String, onComplete: () -> Unit = {}) {
        db.collection("providers").document(id).delete()
            .addOnSuccessListener { onComplete() }
    }

    fun saveBanner(banner: BannerAd, onComplete: () -> Unit = {}) {
        if (banner.id.isEmpty()) {
            db.collection("banners").add(banner)
                .addOnSuccessListener { onComplete() }
        } else {
            db.collection("banners").document(banner.id).set(banner)
                .addOnSuccessListener { onComplete() }
        }
    }

    fun deleteBanner(id: String, onComplete: () -> Unit = {}) {
        db.collection("banners").document(id).delete()
            .addOnSuccessListener { onComplete() }
    }

    fun saveIncident(report: IncidentReport, onComplete: () -> Unit = {}) {
        if (report.id.isEmpty()) {
            db.collection("incidents").add(report)
                .addOnSuccessListener { onComplete() }
        } else {
            db.collection("incidents").document(report.id).set(report)
                .addOnSuccessListener { onComplete() }
        }
    }

    fun deleteIncident(id: String, onComplete: () -> Unit = {}) {
        db.collection("incidents").document(id).delete()
            .addOnSuccessListener { onComplete() }
    }

    fun sendChatMessage(msg: ChatMessage, onComplete: () -> Unit = {}) {
        db.collection("chats").add(msg)
            .addOnSuccessListener { onComplete() }
    }

    fun clearChats(onComplete: () -> Unit = {}) {
        db.collection("chats").get().addOnSuccessListener { snapshot ->
            val batch = db.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().addOnSuccessListener { onComplete() }
        }
    }

    fun saveSupervisor(mod: Moderator, onComplete: () -> Unit = {}) {
        if (mod.id.isEmpty()) {
            db.collection("supervisors").add(mod)
                .addOnSuccessListener { onComplete() }
        } else {
            db.collection("supervisors").document(mod.id).set(mod)
                .addOnSuccessListener { onComplete() }
        }
    }

    fun deleteSupervisor(id: String, onComplete: () -> Unit = {}) {
        db.collection("supervisors").document(id).delete()
            .addOnSuccessListener { onComplete() }
    }

    // ================== DATA SEEDING ==================

    private fun seedCategories() {
        val defaultCats = listOf(
            ServiceCategory("", "طاقة شمسية وكهرباء", "Solar Energy & Electricity", "⚡", true, 0),
            ServiceCategory("", "برمجة وصيانة هواتف", "Software & Mobile Services", "📱", true, 1),
            ServiceCategory("", "سباكة وتمديدات", "Plumbing Services", "🚰", true, 2),
            ServiceCategory("", "نظافة وصيانة منزلية", "Cleaning & Home Repair", "🧹", true, 3),
            ServiceCategory("", "صيانة سيارات وميكانيك", "Car Maintenance & Repair", "🚗", true, 4)
        )
        for (cat in defaultCats) {
            db.collection("categories").add(cat)
        }
    }

    private fun seedProviders() {
        val defaultProviders = listOf(
            ServiceProvider("", "المهندس عادل الحمادي", "777123456", "777123456", "seed_cat_1", "تركيب منظومات شمسية متكاملة وإصلاحها", "شارع حدة - أمام مركز الكميم", "صنعاء", "3000", true, false, "", true),
            ServiceProvider("", "فني طاقة المهندس كريم", "735987654", "735987654", "seed_cat_1", "تمديدات كهرباء منزلية وصيانة ألواح", "المعلا - بجانب البريد", "عدن", "2000", false, false, "", true),
            ServiceProvider("", "صابر مبرمج أنظمة وأندرويد", "780111222", "780111222", "seed_cat_2", "برمجة وتحديث هواتف وتخطي حسابات", "شارع جمال - جولة سنان", "تعز", "1000", true, false, "", true),
            ServiceProvider("", "فارس اليماني للسباكة", "712654321", "712654321", "seed_cat_3", "تأسيس شبكات مياه وصيانة مضخات", "شارع الدائري - بجانب الجامعة", "إب", "2500", false, false, "", true)
        )
        // Note: For seeding, normally we look up the newly created category, or we just seed them as placeholders
        for (prov in defaultProviders) {
            db.collection("providers").add(prov)
        }
    }

    private fun seedBanners() {
        val defaultBanners = listOf(
            BannerAd("", "تثبيت وضمان فني مميز لمنزلك", "https://images.unsplash.com/photo-1621905251189-08b45d6a269e", "https://google.com", "M", "Image Background", 5f),
            BannerAd("", "خصومات الصيف على تمديدات الكهرباء والطاقة المجددة", "https://images.unsplash.com/photo-1508514177221-188b1cf16e9d", "https://google.com", "L", "Promotional Text", 6f)
        )
        for (b in defaultBanners) {
            db.collection("banners").add(b)
        }
    }

    private fun seedSupervisors() {
        db.collection("supervisors").add(Moderator("", "admin", "admin2026"))
        db.collection("supervisors").add(Moderator("", "ali", "1234"))
    }
}
