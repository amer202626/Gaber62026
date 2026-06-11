package com.example

import java.util.UUID

enum class AppThemeType {
    COSMIC_SILVER,   // 🌌 كوزميك سيلفر
    GOLD_LUXURY,     // ✨ الذهبي الفاخر
    EMERALD_CLASSIC, // 🟢 الزمردي الراقي
    SMOKY_BLACK,     // 🌌 الأسود الدخاني
    LIGHT_PINK,      // 🌸 الزهري الفاتح
    GOLDEN_WHITE,    // ✨ الأبيض الذهبي
    CUSTOM_COLOR     // 🎨 ألوان مخصصة ومعدلة
}

data class AppConfig(
    val appName: String = "دليل الخدمات اليمني",
    val themeType: AppThemeType = AppThemeType.COSMIC_SILVER,
    val logoEmoji: String = "🏠",
    val footerText: String = "MAW 777644670",
    val showFooter: Boolean = true,
    val welcomeMessage: String = "مرحباً بكم في الدليل الشامل لخدمات اليمن!",
    val supportPhone: String = "777644670",
    val supportEmail: String = "support@dalyly.com",
    val mainAdminPass: String = "maher736462", // Default editable password
    val maintenanceMode: Boolean = false,
    val dataSavingMode: Boolean = false,
    val subscriptionsEnabled: Boolean = true,
    val fcmNotificationsEnabled: Boolean = true,
    val radiusSearchOptions: String = "5,10,20,50",
    val selectedRadiusIndex: Int = 1,
    val voiceSearchEnabled: Boolean = true,
    val chatIconSize: Int = 54,
    val chatIconColorHex: String = "#FF107C41",
    val chatIconVisible: Boolean = true,
    val chatIconDeleted: Boolean = false,
    val aiAssistantSize: Int = 46,
    val aiAssistantColorHex: String = "#FF1A73E8",
    val aiAssistantVisible: Boolean = true,
    val aiAssistantDeleted: Boolean = false,
    val chatDisabled: Boolean = false,
    val chatDisabledMessage: String = "نعتذر عن الإزعاج، تم تعطيل المحادثات مؤقتاً لتحديث النظام.",
    val aboutImageUrl: String = "",
    val appDownloadUrl: String = "https://play.google.com/store/apps/details?id=com.yemen.services.hub",
    val footerFontSize: Int = 12,
    val customFontFamily: String = "Default",
    val registrationTerms: String = "1. الالتزام بالمهنية والأمانة الشديدة\n2. تقديم أسعار معاينة مناسبة وعادلة للمشتركين\n3. مراجعة ومصادقة وثائق الهوية الوطنية\n4. يحق للأدمن إيقاف أو حظر الحرفيين عند تلقي شكاوى",
    val searchBarVisible: Boolean = true,
    val searchPlaceholder: String = "ابحث عن مهندس، سباك، طبيب...",
    val searchPlaceholderEn: String = "Search plumber, doctor...",
    val activeCustomColorId: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "appName" to appName,
            "themeType" to themeType.name,
            "logoEmoji" to logoEmoji,
            "footerText" to footerText,
            "showFooter" to showFooter,
            "welcomeMessage" to welcomeMessage,
            "supportPhone" to supportPhone,
            "supportEmail" to supportEmail,
            "mainAdminPass" to mainAdminPass,
            "maintenanceMode" to maintenanceMode,
            "dataSavingMode" to dataSavingMode,
            "subscriptionsEnabled" to subscriptionsEnabled,
            "fcmNotificationsEnabled" to fcmNotificationsEnabled,
            "radiusSearchOptions" to radiusSearchOptions,
            "selectedRadiusIndex" to selectedRadiusIndex,
            "voiceSearchEnabled" to voiceSearchEnabled,
            "chatIconSize" to chatIconSize,
            "chatIconColorHex" to chatIconColorHex,
            "chatIconVisible" to chatIconVisible,
            "chatIconDeleted" to chatIconDeleted,
            "aiAssistantSize" to aiAssistantSize,
            "aiAssistantColorHex" to aiAssistantColorHex,
            "aiAssistantVisible" to aiAssistantVisible,
            "aiAssistantDeleted" to aiAssistantDeleted,
            "chatDisabled" to chatDisabled,
            "chatDisabledMessage" to chatDisabledMessage,
            "aboutImageUrl" to aboutImageUrl,
            "appDownloadUrl" to appDownloadUrl,
            "footerFontSize" to footerFontSize,
            "customFontFamily" to customFontFamily,
            "registrationTerms" to registrationTerms,
            "searchBarVisible" to searchBarVisible,
            "searchPlaceholder" to searchPlaceholder,
            "searchPlaceholderEn" to searchPlaceholderEn,
            "activeCustomColorId" to activeCustomColorId
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): AppConfig {
            return AppConfig(
                appName = map["appName"] as? String ?: "دليل الخدمات اليمني",
                themeType = try {
                    AppThemeType.valueOf(map["themeType"] as? String ?: "COSMIC_SILVER")
                } catch (e: Exception) {
                    AppThemeType.COSMIC_SILVER
                },
                logoEmoji = map["logoEmoji"] as? String ?: "🏠",
                footerText = map["footerText"] as? String ?: "MAW 777644670",
                showFooter = map["showFooter"] as? Boolean ?: true,
                welcomeMessage = map["welcomeMessage"] as? String ?: "مرحباً بكم في الدليل الشامل لخدمات اليمن!",
                supportPhone = map["supportPhone"] as? String ?: "777644670",
                supportEmail = map["supportEmail"] as? String ?: "support@dalyly.com",
                mainAdminPass = map["mainAdminPass"] as? String ?: "maher736462",
                maintenanceMode = map["maintenanceMode"] as? Boolean ?: false,
                dataSavingMode = map["dataSavingMode"] as? Boolean ?: false,
                subscriptionsEnabled = map["subscriptionsEnabled"] as? Boolean ?: true,
                fcmNotificationsEnabled = map["fcmNotificationsEnabled"] as? Boolean ?: true,
                radiusSearchOptions = map["radiusSearchOptions"] as? String ?: "5,10,20,50",
                selectedRadiusIndex = (map["selectedRadiusIndex"] as? Long)?.toInt() ?: 1,
                voiceSearchEnabled = map["voiceSearchEnabled"] as? Boolean ?: true,
                chatIconSize = (map["chatIconSize"] as? Long)?.toInt() ?: 54,
                chatIconColorHex = map["chatIconColorHex"] as? String ?: "#FF107C41",
                chatIconVisible = map["chatIconVisible"] as? Boolean ?: true,
                chatIconDeleted = map["chatIconDeleted"] as? Boolean ?: false,
                aiAssistantSize = (map["aiAssistantSize"] as? Long)?.toInt() ?: 46,
                aiAssistantColorHex = map["aiAssistantColorHex"] as? String ?: "#FF1A73E8",
                aiAssistantVisible = map["aiAssistantVisible"] as? Boolean ?: true,
                aiAssistantDeleted = map["aiAssistantDeleted"] as? Boolean ?: false,
                chatDisabled = map["chatDisabled"] as? Boolean ?: false,
                chatDisabledMessage = map["chatDisabledMessage"] as? String ?: "نعتذر عن الإزعاج، تم تعطيل المحادثات مؤقتاً لتحديث النظام.",
                aboutImageUrl = map["aboutImageUrl"] as? String ?: "",
                appDownloadUrl = map["appDownloadUrl"] as? String ?: "https://play.google.com/store/apps/details?id=com.yemen.services.hub",
                footerFontSize = (map["footerFontSize"] as? Long)?.toInt() ?: 12,
                customFontFamily = map["customFontFamily"] as? String ?: "Default",
                registrationTerms = map["registrationTerms"] as? String ?: "1. الالتزام بالمهنية والأمانة الشديدة\n2. تقديم أسعار معاينة مناسبة وعادلة للمشتركين\n3. مراجعة ومصادقة وثائق الهوية الوطنية\n4. يحق للأدمن إيقاف أو حظر الحرفيين عند تلقي شكاوى",
                searchBarVisible = map["searchBarVisible"] as? Boolean ?: true,
                searchPlaceholder = map["searchPlaceholder"] as? String ?: "ابحث عن مهندس، سباك، طبيب...",
                searchPlaceholderEn = map["searchPlaceholderEn"] as? String ?: "Search plumber, doctor...",
                activeCustomColorId = map["activeCustomColorId"] as? String ?: ""
            )
        }
    }
}

data class ServiceCategory(
    val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val iconEmoji: String = "",
    val parentId: String = ""
)

data class CustomColorTheme(
    val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val backgroundHex: String = "#FF0F1016",
    val surfaceHex: String = "#FF1E2230",
    val surfaceVariantHex: String = "#FF24293D",
    val primaryHex: String = "#FFE2E8F0",
    val secondaryHex: String = "#FF38BDF8",
    val tertiaryHex: String = "#FF818CF8",
    val outlineHex: String = "#FF475569"
)

data class CommercialCategory(
    val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val iconEmoji: String = "🛒",
    val imageUrl: String = ""
)

data class CommercialShop(
    val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val logoUrl: String = ""
)

data class CommercialItem(
    val id: String = "",
    val categoryId: String = "",
    val shopId: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0,
    val imageUrl: String = "",
    val description: String = "",
    val deliveryMethods: String = "توصيل منزلي"
)

data class ServiceProvider(
    val id: String = "",
    val fullName: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val categoryId: String = "",
    val subCategory: String = "",
    val address: String = "",
    val area: String = "",
    val imageUrl: String = "",
    val idCardUrl: String = "",
    val gpsLat: Double = 0.0,
    val gpsLng: Double = 0.0,
    val isVerified: Boolean = false,
    val isPinned: Boolean = false,
    val isRecommended: Boolean = false,
    val hasPremiumSubscription: Boolean = false,
    val loyaltyPoints: Int = 0,
    val ratingSum: Float = 0.0f,
    val ratingCount: Int = 0,
    val isBlocked: Boolean = false,
    val previewPrice: Double = 0.0
) {
    val averageRating: Float
        get() = if (ratingCount > 0) ratingSum / ratingCount else 0.0f
}

data class BannerAd(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val linkUrl: String = "",
    val displaySize: String = "M", // S, M, L
    val durationSeconds: Int = 5,
    val isActive: Boolean = true
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "", // Admin or specific professional
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ProviderReview(
    val id: String = "",
    val providerId: String = "",
    val reviewerName: String = "",
    val rating: Int = 5,
    val comment: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class IncidentReport(
    val id: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val reporterName: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ActivityLog(
    val id: String = "",
    val user: String = "",
    val action: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Moderator(
    val id: String = "",
    val username: String = "",
    val password: String = "",
    val secretKey: String = "",
    val permissions: String = ""
)
