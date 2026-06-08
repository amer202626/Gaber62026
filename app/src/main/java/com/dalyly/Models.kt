package com.dalyly

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class ServiceCategory(
    val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val emoji: String = "⚙️",
    val publishImmediately: Boolean = true,
    val displayOrder: Int = 0,
    val description: String = "",
    val isPinned: Boolean = false
)

data class ServiceProvider(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val categoryId: String = "",
    val subCategory: String = "",
    val address: String = "",
    val area: String = "صنعاء", // Default city in Yemen
    val inspectionPrice: String = "1500", // Standard YER or static amount
    val isVip: Boolean = false,
    val isPending: Boolean = false,
    val rejectReason: String = "",
    val active: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isVerified: Boolean = false,
    val photoUrl: String = "",
    val selfieUrl: String = "",
    val gender: String = "Male" // "Male" or "Female"
)

data class BannerAd(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val linkUrl: String = "",
    val displaySize: String = "M", // S, M, L
    val type: String = "Image Background", // Image Background, Video Background, Promotional Text
    val duration: Float = 5f,
    val categoryId: String = ""
)

data class IncidentReport(
    val id: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val reporterName: String = "",
    val complaintText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING" // PENDING, INVESTIGATING, RESOLVED
)

data class Moderator(
    val id: String = "",
    val username: String = "",
    val password: String = "",
    val canAcceptRequests: Boolean = true,
    val canManageCategories: Boolean = true,
    val canManageBanners: Boolean = true,
    val canDeleteProviders: Boolean = true,
    val canSeeReports: Boolean = true
)

data class ChatMessage(
    val id: String = "",
    val messageId: String = UUID.randomUUID().toString(),
    val senderName: String = "",
    val messageText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromAdmin: Boolean = false
)

data class AppConfig(
    val id: String = "current_config",
    val themeType: String = "Classic Dark", // Classic Dark, Yemen Red, Ocean Blue, luxury Golden, Custom Colors
    val appName: String = "دليل الخدمات",
    val logoUrl: String = "",
    val primaryColorHex: String = "#EAA135",
    val secondaryColorHex: String = "#2D2F33",
    val chatIconSize: Int = 56,
    val chatIconColorHex: String = "#D4AF37", // Gold
    val chatIconVisible: Boolean = true,
    val aiAssistantSize: Int = 56,
    val aiAssistantColorHex: String = "#FFD700",
    val aiAssistantVisible: Boolean = true,
    val chatDisabled: Boolean = false,
    val chatDisabledMessage: String = "المحادثات المباشرة مغلقة للتحديث الفني للشبكة حالياً",
    val registrationTerms: String = "١. الالتزام بالمعايير الفنية والمهنية باليمن.\n٢. عدم تحصيل أي رسوم خارج الأسعار المحددة.\n٣. الالتزام بالأمانة وحسن التعامل.",
    val footerText: String = "دليل الخدمات اليمني الموحد 2026 © جميع الحقوق محفوظة",
    val footerFontSize: Int = 11,
    val aboutImageUrl: String = "https://images.unsplash.com/photo-1581092921461-eab62e97a780",
    val appDownloadUrl: String = "https://play.google.com",
    val searchRadiusKm: Int = 10,
    val voiceSearchEnabled: Boolean = true,
    val retentionDays: Int = 30,
    val aiAssistantXOffset: Int = 16,
    val aiAssistantYOffset: Int = 90,
    val chatIconXOffset: Int = 16,
    val chatIconYOffset: Int = 16,
    val supportPhone: String = "777644670",
    val supportWhatsapp: String = "777644670",
    val supportEmail: String = "support@dalyly.com",
    val shareUrl: String = "https://play.google.com/store/apps/details?id=com.dalyly",
    val aboutText: String = "تطبيق دليلي للخدمات اليمنية هو دليل فني متكامل يجمع أفضل مقدمي الخدمات الفنية والمهنية في مكان واحد.",
    val welcomeMessage: String = "مرحباً بك في تطبيق دليلي للخدمات والمهن الصيانة!",
    val adminPassword: String = "maher736462",
    val textColorHex: String = "#FFFFFF",
    val fontType: String = "عريض"
)

data class RegistrationTerm(
    val id: String = "",
    val text: String = "",
    val order: Int = 0,
    val isActive: Boolean = true
)

