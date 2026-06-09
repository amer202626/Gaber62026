package com.dalyly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.speech.RecognizerIntent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.result.launch
import java.io.ByteArrayOutputStream
import com.dalyly.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize our Local Offline Sync Manager!
        FirebaseManager.init(applicationContext)

        setContent {
            MainAppScreen()
        }
    }
}

// Dynamic theme mapper based on Admin configuration
@Composable
fun DalylyTheme(
    config: AppConfig,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    
    // Convert hex string to Color defensively
    fun parseColor(hex: String, default: Color): Color {
        return try {
            val cleanHex = hex.trim().replace("#", "")
            if (cleanHex.length == 6) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else if (cleanHex.length == 8) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else {
                default
            }
        } catch (e: Exception) {
            default
        }
    }

    val txtColor = if (isSystemDark) parseColor(config.textColorHex, Color.White) else Color.Black

    // Theme Colors
    val colors = if (!isSystemDark) {
        // Dynamic Elegant Light Mode Color Scheme mapping
        val primaryColor = when (config.themeType) {
            "Yemen Red" -> Color(0xFFCE1126)
            "Ocean Blue" -> Color(0xFF008B95)
            "luxury Golden", "الذهبي الفاخر", "الذهبي الفاخر ✨", "Luxury Gold" -> Color(0xFFC59B27)
            "Cosmic Silver", "كوزميك سيلفر", "كوزميك سيلفر 🌌" -> Color(0xFF5E5E62)
            "Royal Emerald", "الزمردي الراقي", "الزمردي الراقي 🟢" -> Color(0xFF008D59)
            "Purple & Teal", "البنفسجي والتيال", "البنفسجي والتيال الكلاسيكي" -> Color(0xFF6200EE)
            else -> parseColor(config.primaryColorHex, Color(0xFFEAA135))
        }
        val secondaryColor = when (config.themeType) {
            "Yemen Red" -> Color(0xFFE0E0E0)
            "Ocean Blue" -> Color(0xFFE0ECEF)
            else -> parseColor(config.secondaryColorHex, Color(0xFFECECEC))
        }
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            tertiary = primaryColor,
            background = Color(0xFFF7F8FA),
            surface = Color(0xFFFFFFFF),
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    } else {
        // High-fidelity dark mode mapping
        when (config.themeType) {
            "Custom Colors" -> {
                val primaryColor = parseColor(config.primaryColorHex, Color(0xFFEAA135))
                val secondaryColor = parseColor(config.secondaryColorHex, Color(0xFF2D2F33))
                darkColorScheme(
                    primary = primaryColor,
                    secondary = secondaryColor,
                    tertiary = primaryColor,
                    background = Color(0xFF0A0A0A),
                    surface = Color(0xFF151515),
                    onPrimary = Color.Black,
                    onSecondary = Color.White,
                    onBackground = txtColor,
                    onSurface = txtColor
                )
            }
            "Yemen Red" -> darkColorScheme(
                primary = Color(0xFFCE1126), // Flag Red
                secondary = Color(0xFF000000), // Flag Black
                tertiary = Color(0xFFFFFFFF), // White
                background = Color(0xFF141414),
                surface = Color(0xFF1F1F1F),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = txtColor,
                onSurface = txtColor
            )
            "Ocean Blue" -> darkColorScheme(
                primary = Color(0xFF00ADB5), // Cyan Tech
                secondary = Color(0xFF393E46), // Dark Slate Grey
                tertiary = Color(0xFF222831), // Deep Ocean Navy
                background = Color(0xFF0D1117),
                surface = Color(0xFF161B22),
                onPrimary = Color.Black,
                onSecondary = Color.White,
                onBackground = txtColor,
                onSurface = txtColor
            )
            "luxury Golden", "الذهبي الفاخر", "الذهبي الفاخر ✨", "Luxury Gold" -> darkColorScheme(
                primary = Color(0xFFD4AF37), // Metallic Gold
                secondary = Color(0xFFFFD700), // Bright Gold Accent
                tertiary = Color(0xFF2C2C2C), // Pitch Grey
                background = Color(0xFF141414), // Charcoal coal background
                surface = Color(0xFF1F1F1F), // Charcoal card surface
                onPrimary = Color.Black,
                onSecondary = Color.Black,
                onBackground = txtColor,
                onSurface = txtColor
            )
            "Cosmic Silver", "كوزميك سيلفر", "كوزميك سيلفر 🌌" -> darkColorScheme(
                primary = Color(0xFFD1D1D6), // Metallic Cosmic Silver
                secondary = Color(0xFF8E8E93), // Darker Silver Accent
                tertiary = Color(0xFFC0C0C0),
                background = Color(0xFF0F0F12), // Eye-safe comfortable deep space slate dark
                surface = Color(0xFF1C1C1E), // Slate dark card background
                onPrimary = Color.Black,
                onSecondary = Color.White,
                onBackground = txtColor,
                onSurface = txtColor
            )
            "Royal Emerald", "الزمردي الراقي", "الزمردي الراقي 🟢" -> darkColorScheme(
                primary = Color(0xFF50C878), // Royal Emerald Green
                secondary = Color(0xFF00A86B), // Majestic deep accent green
                tertiary = Color(0xFFD4AF37), // Luxury gold touch highlight
                background = Color(0xFF08120D), // Deep green dark background
                surface = Color(0xFF102218), // Dark forest card container surface
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = txtColor,
                onSurface = txtColor
            )
            "Purple & Teal", "البنفسجي والتيال", "البنفسجي والتيال الكلاسيكي" -> darkColorScheme(
                primary = Color(0xFF6200EE), // purple_500
                secondary = Color(0xFF03DAC5), // teal_200
                tertiary = Color(0xFF3700B3), // purple_700
                background = Color(0xFF000000), // black
                surface = Color(0xFF121212), // dark surface
                onPrimary = Color(0xFFFFFFFF), // white
                onSecondary = Color(0xFF000000), // black
                onBackground = txtColor,
                onSurface = txtColor
            )
            else -> darkColorScheme( // Classic Dark
                primary = Color(0xFFEAA135), // Golden Orange
                secondary = Color(0xFF2D2F33), // Warm Grey
                tertiary = Color(0xFFEAA135),
                background = Color(0xFF1A1B1F),
                surface = Color(0xFF232529),
                onPrimary = Color.Black,
                onSecondary = Color.White,
                onBackground = txtColor,
                onSurface = txtColor
            )
        }
    }

    val defaultWeight = if (config.fontType == "Normal" || config.fontType == "عادي" || config.fontType == "Default") FontWeight.Normal else FontWeight.Bold
    val defaultFamily = when (config.fontType) {
        "Serif", "منسق" -> androidx.compose.ui.text.font.FontFamily.Serif
        "Monospace", "مبرمج" -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }
    
    val typography = androidx.compose.material3.Typography(
        bodyLarge = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        bodyMedium = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        bodySmall = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        titleMedium = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        titleSmall = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        labelLarge = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        labelMedium = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        labelSmall = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        displayLarge = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        displayMedium = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        displaySmall = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        headlineLarge = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        headlineMedium = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor),
        headlineSmall = androidx.compose.ui.text.TextStyle(fontWeight = defaultWeight, fontFamily = defaultFamily, color = txtColor)
    )

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Firestore streams
    val configState by FirebaseManager.config.collectAsState()
    val categories by FirebaseManager.categories.collectAsState()
    val providers by FirebaseManager.providers.collectAsState()
    val banners by FirebaseManager.banners.collectAsState()
    val incidents by FirebaseManager.incidents.collectAsState()
    val chats by FirebaseManager.chats.collectAsState()
    val supervisors by FirebaseManager.supervisors.collectAsState()
    val cities by FirebaseManager.citiesList.collectAsState()
    val registrationTerms by FirebaseManager.registrationTerms.collectAsState()
    val lastUpdateTime by FirebaseManager.lastUpdateTime.collectAsState()
    val updateCount by FirebaseManager.updateCount.collectAsState()
    val latestPingLatency by FirebaseManager.latestPingLatency.collectAsState()

    val sharedPrefs = remember(context) { context.getSharedPreferences("dalyly_prefs", Context.MODE_PRIVATE) }
    var rememberMeOwner by remember { mutableStateOf(sharedPrefs.getBoolean("remember_owner", false)) }
    var rememberMeAdmin by remember { mutableStateOf(sharedPrefs.getBoolean("remember_admin", false)) }

    // UI Navigation & Filters states
    var selectedCategoryTabId by remember { mutableStateOf("") }
    var selectedCityFilter by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }

    // Admin state
    var isAdminMode by remember { mutableStateOf(sharedPrefs.getBoolean("is_admin", false)) }
    var loggedInSupervisor by remember { mutableStateOf<String?>(sharedPrefs.getString("logged_supervisor", null)) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginUser by remember { mutableStateOf("") }
    
    // Secret Owner Gateway states
    var secretClickCount by remember { mutableStateOf(0) }
    var showOwnerPasswordDialog by remember { mutableStateOf(false) }
    var ownerPasswordInput by remember { mutableStateOf("") }
    var isOwnerMode by remember { mutableStateOf(sharedPrefs.getBoolean("is_owner", false)) }
    var loginPass by remember { mutableStateOf("") }

    // Create applications & forms states
    var showRegisterDialog by remember { mutableStateOf(false) }
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regWhatsapp by remember { mutableStateOf("") }
    var regCategory by remember { mutableStateOf("") }
    var regSubDetails by remember { mutableStateOf("") }
    var regAddress by remember { mutableStateOf("") }
    var regArea by remember { mutableStateOf("صنعاء") }
    var regGender by remember { mutableStateOf("Male") }
    var regPhotoUrl by remember { mutableStateOf("") }
    var regSelfieUrl by remember { mutableStateOf("") }
    var isRegSubmitting by remember { mutableStateOf(false) }

    // Report complaints states
    var activeReportProviderId by remember { mutableStateOf<String?>(null) }
    var activeReportProviderName by remember { mutableStateOf("") }
    var reportReporterName by remember { mutableStateOf("") }
    var reportComplaintText by remember { mutableStateOf("") }

    // Floating Support chat panel
    var isChatViewExpanded by remember { mutableStateOf(false) }
    var clientChatName by remember { mutableStateOf("") }
    var isNameSavedForChat by remember { mutableStateOf(false) }
    var draftChatMessage by remember { mutableStateOf("") }

    var userSelectedTab by remember { mutableStateOf("HOME") }
    var isAIChatViewExpanded by remember { mutableStateOf(false) }
    var isArabic by remember { mutableStateOf(true) }

    // Double-back press navigation controller
    var lastBackPressTime by remember { mutableStateOf(0L) }
    val activity = (context as? Activity)
    androidx.activity.compose.BackHandler {
        if (isOwnerMode || isAdminMode || loggedInSupervisor != null || userSelectedTab != "HOME") {
            // Correct Back Button behavior: press once to return to main user home screen and exit admin/other screens
            isOwnerMode = false
            isAdminMode = false
            loggedInSupervisor = null
            userSelectedTab = "HOME"
            Toast.makeText(context, "العودة للشاشة الرئيسية", Toast.LENGTH_SHORT).show()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                activity?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "اضغط مرة أخرى للخروج من التطبيق", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Set default category selected on first load
    LaunchedEffect(categories) {
        if (selectedCategoryTabId.isEmpty() && categories.isNotEmpty()) {
            selectedCategoryTabId = categories.first().id
        }
    }

    // Setup Activity-Result Launchers with JPG 60% compression
    fun compressBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val byteArray = outputStream.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    // 1. Main Photo Gallery Launcher (ID / Document / Work symbol)
    val mainPhotoGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val encoded = compressBitmapToBase64(bitmap)
                    if (encoded != null) {
                        regPhotoUrl = encoded
                        Toast.makeText(context, "تم تحميل وضغط أيقونة/شعار التخصص بنجاح 🖼️", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "فشل ضغط الصورة المختارة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة ملف الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Main Photo Camera Launcher (ID / Document / Work symbol)
    val mainPhotoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val encoded = compressBitmapToBase64(it)
            if (encoded != null) {
                regPhotoUrl = encoded
                Toast.makeText(context, "تم التقاط وضغط أيقونة/شعار التخصص بالكاميرا بنجاح 📸", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "فشل معالجة لقطة الكاميرا", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3. Selfie/Profession Photo Gallery Launcher
    val selfieGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val encoded = compressBitmapToBase64(bitmap)
                    if (encoded != null) {
                        regSelfieUrl = encoded
                        Toast.makeText(context, "تم تحميل وضغط الصورة الثانية بنجاح 🖼️", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "فشل ضغط الصورة المختارة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "خطأ في قراءة ملف الصورة الشخصية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 4. Selfie/Profession Photo Camera Launcher
    val selfieCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val encoded = compressBitmapToBase64(it)
            if (encoded != null) {
                regSelfieUrl = encoded
                Toast.makeText(context, "تم التقاط وضغط الصورة بنجاح بالكاميرا 📸", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "فشل معالجة لقطة الكاميرا الشخصية", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 5. Voice Search Launcher
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchQuery = spokenText
                Toast.makeText(context, "نص البحث التعرفي: $spokenText", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DalylyTheme(config = configState) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column {
                        // M3 Styled Single Footer replacing bottom navigation entirely as requested
                        if (!isOwnerMode && !(loggedInSupervisor != null && isAdminMode)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Left: About Shortcut Icon (reduced by 50% as requested)
                                            IconButton(
                                                onClick = {
                                                    userSelectedTab = if (userSelectedTab == "ABOUT") "HOME" else "ABOUT"
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "عن التطبيق",
                                                    tint = if (userSelectedTab == "ABOUT") MaterialTheme.colorScheme.primary else Color.Gray,
                                                    modifier = Modifier.size(16.dp) // 50% scale
                                                )
                                            }

                                            // Center Footer Text
                                            Text(
                                                text = if (configState.footerText.isNotBlank()) configState.footerText else "WAM777644670",
                                                fontSize = configState.footerFontSize.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.weight(1f)
                                            )

                                            // Right: Floating AI assistant small circle button with "خدمات" label
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .clickable {
                                                        isAIChatViewExpanded = !isAIChatViewExpanded
                                                    },
                                                color = MaterialTheme.colorScheme.primary,
                                                contentColor = Color.Black
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SmartToy,
                                                        contentDescription = "خدمات",
                                                        modifier = Modifier.size(14.dp),
                                                        tint = Color.Black
                                                    )
                                                    Text(
                                                        text = "خدمات",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Black
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Below Footer: Version Number
                                    Text(
                                        text = "النسخة wam2026 - إصدار V2.6.2026",
                                        fontSize = 8.sp,
                                        color = Color.Gray.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (isOwnerMode || (loggedInSupervisor != null && isAdminMode)) {
                        AdminDashboardView(
                            context = context,
                            loggedUser = if (isOwnerMode) "المالك السري (Owner)" else loggedInSupervisor!!,
                            config = configState,
                            categories = categories,
                            providers = providers,
                            banners = banners,
                            incidents = incidents,
                            supervisors = supervisors,
                            chats = chats,
                            cities = cities,
                            registrationTerms = registrationTerms,
                            onLogout = {
                                sharedPrefs.edit()
                                    .putBoolean("is_owner", false)
                                    .putBoolean("is_admin", false)
                                    .putString("logged_supervisor", null)
                                    .putBoolean("remember_owner", false)
                                    .putBoolean("remember_admin", false)
                                    .apply()
                                rememberMeOwner = false
                                rememberMeAdmin = false
                                isOwnerMode = false
                                loggedInSupervisor = null
                                isAdminMode = false
                                secretClickCount = 0
                                Toast.makeText(context, "تم تسجيل الخروج بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        if (userSelectedTab == "ABOUT") {
                            AboutAppScreenView(config = configState, context = context)
                        } else if (userSelectedTab == "OFFERS") {
                            CommercialOffersScreenView(onBack = { userSelectedTab = "HOME" }, context = context)
                        } else {
                        // Users Directory Screen
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Top branding header banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surface,
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val logoPainter = if (configState.logoUrl.isNotBlank()) {
                                            coil.compose.rememberAsyncImagePainter(model = configState.logoUrl)
                                        } else {
                                            coil.compose.rememberAsyncImagePainter(model = R.drawable.ic_app_foreground_asset)
                                        }
                                        Image(
                                            painter = logoPainter,
                                            contentDescription = "Logo",
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    BorderStroke(
                                                        2.dp,
                                                        MaterialTheme.colorScheme.primary
                                                    ), CircleShape
                                                )
                                                .clickable {
                                                    secretClickCount++
                                                    if (secretClickCount >= 5) {
                                                        showOwnerPasswordDialog = true
                                                    }
                                                }
                                        )
                                        Column {
                                            Text(
                                                text = configState.appName,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 🏠 Home Icon
                                        IconButton(
                                            onClick = {
                                                userSelectedTab = "HOME"
                                                secretClickCount++
                                                if (secretClickCount >= 5) {
                                                    showOwnerPasswordDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .size(33.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = "الرئيسية",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // 🔐 Admin Login Icon
                                        IconButton(
                                            onClick = { showLoginDialog = true },
                                            modifier = Modifier
                                                .size(33.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "تسجيل الدخول",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // 👤 Provider Register Icon
                                        IconButton(
                                            onClick = { showRegisterDialog = true },
                                            modifier = Modifier
                                                .size(33.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "إنشاء حساب مقدم خدمة",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // 🌐 Language switcher (Ar/En)
                                        IconButton(
                                            onClick = {
                                                isArabic = !isArabic
                                                Toast.makeText(context, if (isArabic) "تم تحويل اللغة إلى العربية" else "Language switched to English", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(33.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = "تغيير اللغة",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // 🔄 Data Refresh
                                        IconButton(
                                            onClick = {
                                                FirebaseManager.forceNetworkRefresh()
                                                Toast.makeText(context, "تم تحديث البيانات والربط فورياً 🔄", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(33.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "تحديث الصفحة والبيانات",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic sliders / promotional Banner Ads configured by Database
                            if (banners.isNotEmpty()) {
                                SliderBannersView(banners = banners, context = context)
                            }

                            if (configState.isOffersSectionEnabled) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .clickable { userSelectedTab = "OFFERS" },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("🛒", fontSize = 24.sp)
                                            Column {
                                                Text(
                                                    text = "قسم العروض التجارية والسلع للبيع",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "تصفح وشاهد السيارات، العقارات والمعدات المتاحة للبيع باليمن فورا",
                                                    fontSize = 10.sp,
                                                    color = Color.LightGray
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "دخول",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (configState.chatDisabled) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("📢", fontSize = 20.sp)
                                        Column {
                                            Text(
                                                text = "تنبيه إيقاف خدمة الدعم والمحادثة:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = configState.chatDisabledMessage,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Yemen terms guidelines
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FactCheck,
                                            contentDescription = "Check Icon",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "شروط وضوابط الاستخدام والتقديم للخدمة:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = configState.registrationTerms,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search bar & filters settings
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                shadowElevation = 3.dp,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Live input
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("ابحث باسم الفني أو تخصص معين...") },
                                        trailingIcon = {
                                            if (configState.voiceSearchEnabled) {
                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
                                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن للبحث في دليلي...")
                                                            }
                                                            voiceSearchLauncher.launch(intent)
                                                        } catch (e: java.lang.Exception) {
                                                            Toast.makeText(context, "البحث الصوتي غير مدعوم على هذا الجهاز حاليًا", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Voice Search",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Search"
                                            )
                                        },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Cities horizontal dropdown selectors
                                    Text(
                                        text = "تصفية حسب المدينة / المحافظة اليمنيّة:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = selectedCityFilter == "الكل",
                                                onClick = { selectedCityFilter = "الكل" },
                                                label = { Text("الكل") }
                                            )
                                        }
                                        items(cities) { city ->
                                            FilterChip(
                                                selected = selectedCityFilter == city,
                                                onClick = { selectedCityFilter = city },
                                                label = { Text(city) }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Horizontal categories tags (Loaded from Firestore snapshot live lists!)
                            Text(
                                text = "أقسام ودليل دليل الخدمات النشطة 🛠️:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            if (categories.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(categories) { cat ->
                                        val isSelected = selectedCategoryTabId == cat.id
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(
                                                        alpha = 0.3f
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedCategoryTabId = cat.id }
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = cat.emoji, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = cat.nameAr,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.Black else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Realtime lists of maintenance technicians of selected category & city
                            val filteredProviders = providers.filter {
                                // Exclude pending ones for clients screen! Only show active verified ones
                                !it.isPending && it.active &&
                                        (selectedCategoryTabId.isEmpty() || it.categoryId == selectedCategoryTabId || it.categoryId == "seed_${selectedCategoryTabId}") &&
                                        (selectedCityFilter == "الكل" || it.area == selectedCityFilter) &&
                                        (searchQuery.isEmpty() || it.name.contains(searchQuery) || it.subCategory.contains(searchQuery))
                            }

                            Text(
                                text = "المزودون والفنيون المسجلون مباشرة (${filteredProviders.size}):",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )

                            if (filteredProviders.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Empty",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "لا يوجد فنيين متوفرين لهذا الفلتر حالياً.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            } else {
                                filteredProviders.forEach { prov ->
                                    TechProviderCard(
                                        prov = prov,
                                        onReport = { id, name ->
                                            activeReportProviderId = id
                                            activeReportProviderName = name
                                        },
                                        context = context
                                    )
                                }
                            }

                            // Dynamic call-to-action to register as technician
                            Spacer(modifier = Modifier.height(20.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "أنت فني صيانة وتريد الانضمام للدليل؟",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "سجل معنا الآن لتظهر في الصفحة الرئيسية للعملاء فور موافقة المشرف",
                                        fontSize = 11.sp,
                                        color = Color.LightGray,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            regName = ""
                                            regPhone = ""
                                            regWhatsapp = ""
                                            regSubDetails = ""
                                            regAddress = ""
                                            // Preselect category if available
                                            regCategory = categories.firstOrNull()?.id ?: ""
                                            showRegisterDialog = true
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("تقديم طلب تسجيل فني صيانة")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }

                    // overlays!
                    if (!isOwnerMode && !(loggedInSupervisor != null && isAdminMode)) {
                        // Realtime Floating Support chat widget (synced with Firestore live snapshot!)
                        if (configState.chatIconVisible) {
                            FloatingActionChatWidget(
                                config = configState,
                                messages = chats,
                                isExpanded = isChatViewExpanded,
                                isNameSaved = isNameSavedForChat,
                                clientName = clientChatName,
                                draftText = draftChatMessage,
                                onToggle = { isChatViewExpanded = !isChatViewExpanded },
                                onNameSave = { name ->
                                    if (name.isNotBlank()) {
                                        clientChatName = name
                                        isNameSavedForChat = true
                                    }
                                },
                                onDraftChange = { draftChatMessage = it },
                                onSendMessage = {
                                    if (draftChatMessage.isNotBlank()) {
                                        val msg = ChatMessage(
                                            senderName = clientChatName,
                                            messageText = draftChatMessage,
                                            isFromAdmin = false
                                        )
                                        FirebaseManager.sendChatMessage(msg) {
                                            draftChatMessage = ""
                                        }
                                    }
                                }
                            )
                        }

                        // Floating AI Assistant Widget
                        if (configState.aiAssistantVisible) {
                            FloatingAIAssistantWidget(
                                config = configState,
                                isExpanded = isAIChatViewExpanded,
                                onToggle = { isAIChatViewExpanded = !isAIChatViewExpanded }
                            )
                        }
                    }
                }
            }
        }

            // === POPUP WINDOWS / DIALOGS ===

            // MODERATORS & ADMINS LOGIN POPUP
            if (showLoginDialog) {
                Dialog(onDismissRequest = { showLoginDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "تسجيل دعم ومراقب الإدارة العليا 🛡️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            TextField(
                                value = loginUser,
                                onValueChange = { loginUser = it },
                                label = { Text("اسم المستخدم (Username)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = loginPass,
                                onValueChange = { loginPass = it },
                                label = { Text("معرف المرور (Passkey/PIN)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { rememberMeAdmin = !rememberMeAdmin }
                            ) {
                                Checkbox(
                                    checked = rememberMeAdmin,
                                    onCheckedChange = { rememberMeAdmin = it }
                                )
                                Text("حفظ تسجيل الدخول للمرة القادمة ⏳", fontSize = 11.sp, color = Color.White)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { showLoginDialog = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إلغاء")
                                }
                                Button(
                                    onClick = {
                                        val match = supervisors.firstOrNull {
                                            it.username.trim().equals(loginUser.trim(), ignoreCase = true) &&
                                                    it.password.trim() == loginPass.trim()
                                        }
                                        val isMainAdmin = loginUser.trim() == "WAM2026" && loginPass.trim() == "maher736462"
                                        val isLegacyAdmin = loginUser.trim() == "admin" && loginPass.trim() == "admin2026"
                                        if (match != null || isMainAdmin || isLegacyAdmin) {
                                            val suName = if (isMainAdmin) "WAM2026" else (match?.username ?: "الإدارة العليا")
                                            loggedInSupervisor = suName
                                            isAdminMode = true
                                            showLoginDialog = false
                                            
                                            sharedPrefs.edit()
                                                .putBoolean("is_admin", true)
                                                .putString("logged_supervisor", suName)
                                                .putBoolean("remember_admin", rememberMeAdmin)
                                                .apply()

                                            Toast.makeText(context, "أهلاً بك مشرف: $loginUser", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "البيانات خاطئة أو لحساب معطل", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("دخول")
                                }
                            }
                        }
                    }
                }
            }

            // SECRET OWNER PIN CHALLENGE POPUP
            if (showOwnerPasswordDialog) {
                Dialog(onDismissRequest = { showOwnerPasswordDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "👑 بوابة المالك السرية لتعميم الهوية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            TextField(
                                value = ownerPasswordInput,
                                onValueChange = { ownerPasswordInput = it },
                                label = { Text("رمز مرور المالك السري  (Owner Password)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { rememberMeOwner = !rememberMeOwner }
                            ) {
                                Checkbox(
                                    checked = rememberMeOwner,
                                    onCheckedChange = { rememberMeOwner = it }
                                )
                                Text("حفظ تسجيل الدخول للمرة القادمة ⏳", fontSize = 11.sp, color = Color.White)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { 
                                        showOwnerPasswordDialog = false 
                                        ownerPasswordInput = ""
                                        secretClickCount = 0
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إلغاء")
                                }
                                Button(
                                    onClick = {
                                        if (ownerPasswordInput.trim() == "maher--736462") {
                                            isOwnerMode = true
                                            showOwnerPasswordDialog = false
                                            ownerPasswordInput = ""
                                            secretClickCount = 0
                                            
                                            sharedPrefs.edit()
                                                .putBoolean("is_owner", true)
                                                .putBoolean("remember_owner", rememberMeOwner)
                                                .apply()

                                            Toast.makeText(context, "أهلاً بك مالك التطبيق!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "الرمز السري غير صحيح!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("دخول")
                                }
                            }
                        }
                    }
                }
            }

            // REGISTER AS A SERVICE PROVIDER APPLICATION FORM
            if (showRegisterDialog) {
                Dialog(onDismissRequest = { showRegisterDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "تسجيل كفني جديد (دليل دليلي) 🖋️",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = regName,
                                onValueChange = { regName = it },
                                label = { Text("اسم المزود الكامل") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = regPhone,
                                onValueChange = { regPhone = it },
                                label = { Text("رقم جوال الاتصال باليمن") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = regWhatsapp,
                                onValueChange = { regWhatsapp = it },
                                label = { Text("رقم حساب الواتساب (بدون رمز 967)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Select category
                            Text("حدد نوع الخدمة البرمجية أو الفنية:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            categories.forEach { cat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { regCategory = cat.id }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = regCategory == cat.id,
                                        onClick = { regCategory = cat.id }
                                    )
                                    Text(text = "${cat.emoji} ${cat.nameAr}", fontSize = 12.sp)
                                }
                            }

                            TextField(
                                value = regSubDetails,
                                onValueChange = { regSubDetails = it },
                                label = { Text("تفصيل تخصصك الدقيق (مثال: صيانة كروت وبور)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = regAddress,
                                onValueChange = { regAddress = it },
                                label = { Text("العنوان بالتفصيل (الحي، الشارع)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // City selector list
                            Text("اختر مدينة المزاولة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cities) { city ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (regArea == city) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { regArea = city }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = city, fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Gender selection
                            Text("الجنس (المطابقة والخصوصية):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = regGender == "Male", onClick = { regGender = "Male" })
                                    Text("ذكر 👤", fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = regGender == "Female", onClick = { regGender = "Female" })
                                    Text("أنثى 👩‍🔧", fontSize = 11.sp)
                                }
                            }

                            // Profile Photo / Gallery
                            Text("أيقونة العمل / ترخيص المهنة / شعار المهنة 🛠️:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { mainPhotoGalleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("المعرض 🖼️", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { mainPhotoCameraLauncher.launch() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("كاميرا 🤳", fontSize = 11.sp)
                                }
                                if (regPhotoUrl.isNotEmpty()) {
                                    Text("تم ✅", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("اختياري", fontSize = 11.sp, color = Color.LightGray)
                                }
                            }

                            // Camera / Selfie Logic
                            if (regGender == "Male") {
                                Text("صورة شخصية سلفي Selfie (إلزامي لمطابقة بيانات الهوية للذكور) 👤:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { selfieGalleryLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("من المعرض 🖼️", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { selfieCameraLauncher.launch() },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("افتح الكاميرا 🤳", fontSize = 11.sp)
                                    }
                                    if (regSelfieUrl.isNotEmpty()) {
                                        Text("تم ✅", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("مطلوب 🚨", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("صورة تعبّر عن طبيعة العمل أو التخصص (إلزامي للإناث كبديل عن السيلفي) 👩‍🔧:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { selfieGalleryLauncher.launch("image/*") },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("من المعرض 🖼️", fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = { selfieCameraLauncher.launch() },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("افتح الكاميرا 🤳", fontSize = 11.sp)
                                        }
                                        if (regSelfieUrl.isNotEmpty()) {
                                            Text("تم ✅", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("مطلوب 🚨", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            "🛡️ لحماية الخصوصية المطلقة، لا يُطلب من مقدمات الخدمة الإناث التقاط أو رفع صورة ذاتية Selfie للشخص الفردي. يُكتفى بصورة معبرة عن مهنتكِ أو تخصصكِ لتأكيد التواجد العملي.",
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(10.dp),
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.FactCheck, contentDescription = "Terms", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تعهد وشروط مزاولة المهنة المعتمدة باليمن 📝:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (registrationTerms.isEmpty()) {
                                        Text("جاري مزامنة الشروط التعبدية بالدليل من السيرفر...", fontSize = 11.sp, color = Color.Gray)
                                    } else {
                                        registrationTerms.filter { it.isActive }.sortedBy { it.order }.forEachIndexed { idx, term ->
                                            Text(
                                                text = "${idx + 1}. ${term.text}",
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("📝 بالضغط على زر الإرسال، فإنك تقر وتلتزم التزاماً كاملاً بكافة البنود والشروط القانونية المذكورة أعلاه.", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isRegSubmitting) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "جاري تجميع ورفع طلبك وصورك... الرجاء الانتظار ⏳",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = { showRegisterDialog = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("إلغاء")
                                    }
                                    Button(
                                        onClick = {
                                            if (regName.isEmpty() || regPhone.isEmpty() || regWhatsapp.isEmpty()) {
                                                Toast.makeText(context, "الرجاء اكمال الحقول الرئيسية", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (regGender == "Male" && regSelfieUrl.isEmpty()) {
                                                Toast.makeText(context, "الصورة الذاتية Selfie مطلوبة لمطابقة حسابك الأمني كذكر!", Toast.LENGTH_LONG).show()
                                                return@Button
                                            }
                                            isRegSubmitting = true
                                            val approvedImmediately = categories.find { it.id == regCategory }?.publishImmediately ?: true
                                            val newProv = ServiceProvider(
                                                name = regName,
                                                phone = regPhone,
                                                whatsapp = regWhatsapp,
                                                categoryId = regCategory,
                                                subCategory = regSubDetails,
                                                address = regAddress,
                                                area = regArea,
                                                isPending = !approvedImmediately, // Pending if publishImmediately is false
                                                active = approvedImmediately,
                                                photoUrl = regPhotoUrl,
                                                selfieUrl = regSelfieUrl,
                                                gender = regGender
                                            )
                                            FirebaseManager.saveProvider(newProv) {
                                                isRegSubmitting = false
                                                Toast.makeText(
                                                    context,
                                                    if (approvedImmediately) "تم تفعيلك ونشر خدماتك فوراً بالصفحة!" else "تم تقديم الطلب للمراجعة والتدقيق الفني!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                // Clear form values to prevent duplicate entries
                                                regName = ""
                                                regPhone = ""
                                                regWhatsapp = ""
                                                regSubDetails = ""
                                                regAddress = ""
                                                regPhotoUrl = ""
                                                regSelfieUrl = ""
                                                regGender = "Male"
                                                showRegisterDialog = false
                                            }
                                        },
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Text("إرسال طلب الانضمام")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // REPORT / INCIDENT DIALOG
            if (activeReportProviderId != null) {
                Dialog(onDismissRequest = { activeReportProviderId = null }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "🚨 الإبلاغ عن فني: $activeReportProviderName",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Red,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = reportReporterName,
                                onValueChange = { reportReporterName = it },
                                label = { Text("اسمك الكريم (اختياري)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextField(
                                value = reportComplaintText,
                                onValueChange = { reportComplaintText = it },
                                label = { Text("تفاصيل المشكلة أو سوء المعاملة بالتكامل...") },
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { activeReportProviderId = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إلغاء")
                                }
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    onClick = {
                                        if (reportComplaintText.isEmpty()) {
                                            Toast.makeText(context, "الرجاء كتابة تفصيل البلاغ", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val rep = IncidentReport(
                                            providerId = activeReportProviderId!!,
                                            providerName = activeReportProviderName,
                                            reporterName = reportReporterName.ifEmpty { "عميل مجهول" },
                                            complaintText = reportComplaintText
                                        )
                                        FirebaseManager.saveIncident(rep) {
                                            Toast.makeText(context, "تم رفع الشكوى للإدارة وسيتم الإجراء فوراً!", Toast.LENGTH_LONG).show()
                                            activeReportProviderId = null
                                            reportComplaintText = ""
                                            reportReporterName = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إرسال الشكوى")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tech provider list card item
@Composable
fun TechProviderCard(
    prov: ServiceProvider,
    onReport: (String, String) -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (prov.isVip) 1.5.dp else 1.dp,
            color = if (prov.isVip) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = prov.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = if (prov.isVip) MaterialTheme.colorScheme.primary else Color.White
                    )
                    if (prov.isVip) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "موثق 👑",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                
                // Location badge
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondary,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "📍 ${prov.area}",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = prov.subCategory,
                fontSize = 12.sp,
                color = Color.LightGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (prov.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚙️ العنوان: ${prov.address}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "معاينة الفحص: ${prov.inspectionPrice} ر.ي",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Complaint
                    IconButton(
                        onClick = { onReport(prov.id, prov.name) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Report",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Direct mobile trigger
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${prov.phone}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", modifier = Modifier.size(14.dp))
                            Text("اتصال", fontSize = 11.sp)
                        }
                    }

                    // Direct WhatsApp trigger
                    Button(
                        onClick = {
                            val cleanNumber = prov.whatsapp.trim().removePrefix("+").removePrefix("00")
                            val finalUrl = "https://api.whatsapp.com/send?phone=967$cleanNumber&text=مرحباً يا هندسة، وجدتك في دليل دليلي للخدمات وأود الاستفسار عن صيانة فنية صيانة..."
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "WhatsApp", modifier = Modifier.size(14.dp), tint = Color.White)
                            Text("واتساب", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Banners slideshow carousel widget
@Composable
fun SliderBannersView(banners: List<BannerAd>, context: Context) {
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(banners) {
        if (banners.size > 1) {
            while (true) {
                delay(5000L)
                currentIndex = (currentIndex + 1) % banners.size
            }
        }
    }

    val banner = banners.getOrNull(currentIndex) ?: return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable {
                if (banner.linkUrl.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(banner.linkUrl))
                    context.startActivity(intent)
                }
            }
    ) {
        if (banner.imageUrl.isNotBlank()) {
            AsyncImage(
                model = banner.imageUrl,
                contentDescription = "Ad",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // overlay banner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(10.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "إعلان ترويجي مميز 📢", fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = banner.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Real-time floating instant support chat widget
@Composable
fun FloatingActionChatWidget(
    config: AppConfig,
    messages: List<ChatMessage>,
    isExpanded: Boolean,
    isNameSaved: Boolean,
    clientName: String,
    draftText: String,
    onToggle: () -> Unit,
    onNameSave: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    if (!config.chatIconVisible) return

    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val themeColor = try {
        val hex = config.chatIconColorHex.trim().replace("#", "")
        if (hex.length == 6 || hex.length == 8) {
            Color(android.graphics.Color.parseColor("#$hex"))
        } else {
            fallbackPrimary
        }
    } catch (e: Exception) {
        fallbackPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 14.dp, // positioned on the left side above info icon
                bottom = (config.chatIconYOffset + 14).dp
            ),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chat Expand Panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .width(310.dp)
                        .height(380.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f))
                ) {
                    if (config.chatDisabled) {
                        // Closed Chat view
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.Chat, contentDescription = "Disabled", modifier = Modifier.size(48.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = config.chatDisabledMessage,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header Support Window
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(themeColor)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("💬 المحادثة والدعم السحابي الفوري", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                    Icon(imageVector = Icons.Default.Star, contentDescription = "Active", tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                            }

                            if (!isNameSaved) {
                                // Request Name
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("أهلاً بك! الرجاء كتابة اسمك الكريم لبدء المحادثة:", fontSize = 11.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    var tempName by remember { mutableStateOf("") }
                                    OutlinedTextField(
                                        value = tempName,
                                        onValueChange = { tempName = it },
                                        placeholder = { Text("مثال: أمين ردمان", color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = if (isSystemInDarkTheme()) Color.White else Color.Black),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                            unfocusedTextColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                            focusedBorderColor = themeColor,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF9F9F9),
                                            unfocusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF9F9F9)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { onNameSave(tempName) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.Black)
                                    ) {
                                        Text("ابدأ المحادثة", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // Messages List
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp)
                                ) {
                                    items(messages) { msg ->
                                        val isAdmin = msg.isFromAdmin
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            contentAlignment = if (isAdmin) Alignment.CenterStart else Alignment.CenterEnd
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .background(
                                                        color = if (isAdmin) Color.DarkGray else themeColor.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Text(text = msg.senderName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeColor)
                                                Text(text = msg.messageText, fontSize = 11.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                            }
                                        }
                                    }
                                }

                                // Message input
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.2f))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = draftText,
                                        onValueChange = { onDraftChange(it) },
                                        placeholder = { Text("تفاصيل المشكلة للتحقق...", color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray) },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = if (isSystemInDarkTheme()) Color.White else Color.Black),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                            unfocusedTextColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                            focusedBorderColor = themeColor,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF9F9F9),
                                            unfocusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF9F9F9)
                                        )
                                    )
                                    IconButton(
                                        onClick = onSendMessage,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(themeColor, CircleShape)
                                    ) {
                                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Main Button
            FloatingActionButton(
                onClick = onToggle,
                containerColor = themeColor,
                contentColor = Color.Black,
                modifier = Modifier.size(config.chatIconSize.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Chat,
                    contentDescription = "Chat Support"
                )
            }
        }
    }
}

// ======================== SECURITY SUPERVISOR PANEL ========================
@Composable
fun AdminDashboardViewOld(
    context: Context,
    loggedUser: String,
    config: AppConfig,
    categories: List<ServiceCategory>,
    providers: List<ServiceProvider>,
    banners: List<BannerAd>,
    incidents: List<IncidentReport>,
    supervisors: List<Moderator>,
    chats: List<ChatMessage>,
    cities: List<String>,
    onLogout: () -> Unit
) {
    var selectedDashboardTab by remember { mutableStateOf("PENDING") } // PENDING, ADD_MANUAL, BANNERS, CATEGORIES_CITIES, REPORTS, CHATS, CONFIGS

    // Form states
    var mName by remember { mutableStateOf("") }
    var mPhone by remember { mutableStateOf("") }
    var mWhatsapp by remember { mutableStateOf("") }
    var mCategory by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var mSub by remember { mutableStateOf("") }
    var mAddress by remember { mutableStateOf("") }
    var mArea by remember { mutableStateOf("صنعاء") }
    var mInspectionPrice by remember { mutableStateOf("1500") }
    var mIsVipBadge by remember { mutableStateOf(false) }

    // Banners state
    var newBannerTitle by remember { mutableStateOf("") }
    var newBannerUrl by remember { mutableStateOf("") }
    var newBannerLink by remember { mutableStateOf("") }

    // Categories state
    var newCatAr by remember { mutableStateOf("") }
    var newCatEn by remember { mutableStateOf("") }
    var newCatEmoji by remember { mutableStateOf("🛠️") }
    var isCatPublishImmediately by remember { mutableStateOf(true) }

    // Config state
    var configThemeType by remember { mutableStateOf(config.themeType) }
    var configTermsByAdmin by remember { mutableStateOf(config.registrationTerms) }
    var configFooterText by remember { mutableStateOf(config.footerText) }
    var configChatDisabled by remember { mutableStateOf(config.chatDisabled) }
    var configChatWarningMsg by remember { mutableStateOf(config.chatDisabledMessage) }

    // Chat reply draft
    var adminChatReplyDraft by remember { mutableStateOf("") }

    // Supervisors List inside Admin view
    var newSuperUser by remember { mutableStateOf("") }
    var newSuperPass by remember { mutableStateOf("") }

    val pendingProviders = providers.filter { it.isPending }
    val activeProviders = providers.filter { !it.isPending }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Welcome and session status details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🛡️ الإدارة العليا: $loggedUser",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "مزامنة تفاعلية فورية | UTC +03:00",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                }

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("خروج المراقب", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Horizontal Panel Selector
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf(
                "PENDING" to "طلبات المراجعة المعلقة (${pendingProviders.size}) ⏳",
                "ADD_MANUAL" to "تسجيل فني مباشر ✍️",
                "BANNERS" to "الإعلانات النشطة 📢",
                "CATEGORIES_CITIES" to "إدارة الأقسام والمدن 🏛️",
                "REPORTS" to "البلاغات المرفوعة (${incidents.size}) 🚨",
                "CHATS" to "مراقبة المحادثات (${chats.size}) 💬",
                "CONFIGS" to "إعدادات الألوان والضوابط 🎨"
            )
            items(tabs) { (key, title) ->
                val active = selectedDashboardTab == key
                Box(
                    modifier = Modifier
                        .background(
                            color = if (active) MaterialTheme.colorScheme.primary else Color.DarkGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedDashboardTab = key }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) Color.Black else Color.White)
                }
            }
        }

        Divider(color = Color.Gray.copy(alpha = 0.3f))

        // Tab Content Routing Panel
        when (selectedDashboardTab) {
            "PENDING" -> {
                Text("المزودون والفنيون بانتظار الترخيص والموافقة:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (pendingProviders.isEmpty()) {
                    Text("لا يوجد طلبات انضمام فنية معلقة حالياً.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    pendingProviders.forEach { pending ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFA500))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "الاسم: ${pending.name}", fontWeight = FontWeight.Bold)
                                Text(text = "رقم الجوال: ${pending.phone} | واتساب: ${pending.whatsapp}")
                                Text(text = "تفصيل التخصص: ${pending.subCategory}")
                                Text(text = "المدينة: ${pending.area} - العنوان: ${pending.address}")
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val updated = pending.copy(isPending = false, active = true)
                                            FirebaseManager.saveProvider(updated) {
                                                Toast.makeText(context, "تم الموافقة على طلب الفني الصيانة بنجاح ونشره!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("قبول وترخيص الخدمة", fontSize = 10.sp, color = Color.Black)
                                    }
                                    Button(
                                        onClick = {
                                            FirebaseManager.deleteProvider(pending.id) {
                                                Toast.makeText(context, "تم رفض ومسح الطلب بنجاح", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("رفض ومسح الطلب", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "ADD_MANUAL" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إضافة وترخيص فني صيانة مباشرة بالدليل السحابي:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextField(value = mName, onValueChange = { mName = it }, label = { Text("اسم الفني المعتمد") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = mPhone, onValueChange = { mPhone = it }, label = { Text("رقم جوال للتواصل") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = mWhatsapp, onValueChange = { mWhatsapp = it }, label = { Text("رقم الواتساب") }, modifier = Modifier.fillMaxWidth())
                    
                    Text("اختر القسم الفني:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mCategory = cat.id }
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mCategory == cat.id, onClick = { mCategory = cat.id })
                            Text(text = "${cat.emoji} ${cat.nameAr}", fontSize = 12.sp)
                        }
                    }

                    TextField(value = mSub, onValueChange = { mSub = it }, label = { Text("التوصيف الفني") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = mAddress, onValueChange = { mAddress = it }, label = { Text("تفصيل العنوان") }, modifier = Modifier.fillMaxWidth())
                    
                    // City selector
                    Text("محافظة العمل:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        cities.forEach { city ->
                            Box(
                                modifier = Modifier
                                    .background(if (mArea == city) MaterialTheme.colorScheme.primary else Color.DarkGray, RoundedCornerShape(6.dp))
                                    .clickable { mArea = city }
                                    .padding(8.dp)
                            ) {
                                Text(text = city, fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }

                    TextField(value = mInspectionPrice, onValueChange = { mInspectionPrice = it }, label = { Text("تكلفة الفحص الفني") }, modifier = Modifier.fillMaxWidth())
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mIsVipBadge, onCheckedChange = { mIsVipBadge = it })
                        Text("منح شارة الموزّع الموثق VIP 👑")
                    }

                    Button(
                        onClick = {
                            if (mName.isEmpty() || mPhone.isEmpty() || mCategory.isEmpty()) {
                                Toast.makeText(context, "الرجاء تعبئة البيانات الأساسية", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val prov = ServiceProvider(
                                name = mName,
                                phone = mPhone,
                                whatsapp = mWhatsapp,
                                categoryId = mCategory,
                                subCategory = mSub,
                                address = mAddress,
                                area = mArea,
                                inspectionPrice = mInspectionPrice,
                                isVip = mIsVipBadge,
                                active = true,
                                isPending = false
                            )
                            FirebaseManager.saveProvider(prov) {
                                Toast.makeText(context, "تم حفظ وترقية المزود بنجاح بالدليل السحابي!", Toast.LENGTH_SHORT).show()
                                mName = ""
                                mPhone = ""
                                mWhatsapp = ""
                                mSub = ""
                                mAddress = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ترخيص ونشر فني الصيانة فوراً")
                    }
                }
            }

            "BANNERS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إدارة الافتات الإعلانية والترويجية بالدليل:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextField(value = newBannerTitle, onValueChange = { newBannerTitle = it }, label = { Text("عنوان الإعلان") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newBannerUrl, onValueChange = { newBannerUrl = it }, label = { Text("رابط صورة الإعلان (Unsplash أو Direct Link)") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newBannerLink, onValueChange = { newBannerLink = it }, label = { Text("رابط التوجيه عند النقر (Link URL)") }, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            if (newBannerTitle.isEmpty() || newBannerUrl.isEmpty()) {
                                Toast.makeText(context, "الرجاء اكمال بيانات البانر", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val b = BannerAd(title = newBannerTitle, imageUrl = newBannerUrl, linkUrl = newBannerLink)
                            FirebaseManager.saveBanner(b) {
                                Toast.makeText(context, "تم تخزين الإعلان السحابي وبدء البث!", Toast.LENGTH_SHORT).show()
                                newBannerTitle = ""
                                newBannerUrl = ""
                                newBannerLink = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إطلاق وبث الإعلان الجديد")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("الإعلانات المشغلة حالياً:", fontWeight = FontWeight.Bold)
                    banners.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = b.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = {
                                FirebaseManager.deleteBanner(b.id) {
                                    Toast.makeText(context, "تم إنهاء وسحب البانر", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            "CATEGORIES_CITIES" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إدارة وتصنيف أقسام دليلي للخدمات والصيانة بالفلو السحابي:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextField(value = newCatAr, onValueChange = { newCatAr = it }, label = { Text("اسم القسم بالعربية") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newCatEn, onValueChange = { newCatEn = it }, label = { Text("اسم القسم بالإنجليزية") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newCatEmoji, onValueChange = { newCatEmoji = it }, label = { Text("رمز الايموجي للقسم") }, modifier = Modifier.fillMaxWidth())
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCatPublishImmediately, onCheckedChange = { isCatPublishImmediately = it })
                        Text("نشر وتفعيل مقدمي الخدمة بالقسم بدون انتظار ترخيص المشرف")
                    }

                    Button(
                        onClick = {
                            if (newCatAr.isEmpty() || newCatEmoji.isEmpty()) {
                                Toast.makeText(context, "اكمل الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val cat = ServiceCategory(
                                nameAr = newCatAr,
                                nameEn = newCatEn,
                                emoji = newCatEmoji,
                                publishImmediately = isCatPublishImmediately,
                                displayOrder = categories.size
                            )
                            FirebaseManager.saveCategory(cat) {
                                Toast.makeText(context, "تم حفظ القسم بنجاح ومزامنته لكل أجهزة اليمن!", Toast.LENGTH_SHORT).show()
                                newCatAr = ""
                                newCatEn = ""
                                newCatEmoji = "🛠️"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ترخيص ونشر القسم الجديد")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("الأقسام المفعلة ومسارها:", fontWeight = FontWeight.Bold)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "${cat.emoji} ${cat.nameAr} | ${if (cat.publishImmediately) "تلقائي تدوين" else "مراجعة مشرف"}")
                            IconButton(onClick = {
                                FirebaseManager.deleteCategory(cat.id) {
                                    Toast.makeText(context, "تم مسح القسم", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            "REPORTS" -> {
                Text("بلاغات وشكاوى سوء المعاملة أو المعاينة المرفوعة من العملاء:", fontWeight = FontWeight.Bold, color = Color.Red)
                if (incidents.isEmpty()) {
                    Text("لا توجد شكاوى أو إبلاغات فنية مرفوعة حالياً.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    incidents.forEach { inc ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "🚨 البلاغ ضد: ${inc.providerName}", fontWeight = FontWeight.Bold, color = Color.Red)
                                Text(text = "اسم المبلّغ: ${inc.reporterName}")
                                Text(text = "مضمون الشكوى: ${inc.complaintText}", color = Color.LightGray)
                                Text(text = "حالة البلاغ: ${inc.status}", fontSize = 10.sp, color = Color.Yellow)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val updated = inc.copy(status = "RESOLVED")
                                            FirebaseManager.saveIncident(updated) {
                                                Toast.makeText(context, "تم تحديد الشكوى كـ مستوفية ومحسومة!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("حل وحسم المشكلة", fontSize = 10.sp, color = Color.Black)
                                    }
                                    Button(
                                        onClick = {
                                            FirebaseManager.deleteIncident(inc.id) {
                                                Toast.makeText(context, "تم مسح السجل", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("مسح البلاغ", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CHATS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("غرفة مراقبة ومشاركة المحادثات مع العملاء مباشرة 💬:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    // Input to reply
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = adminChatReplyDraft,
                            onValueChange = { adminChatReplyDraft = it },
                            placeholder = { Text("اكتب رد الإدارة الموحد هنا للتثبيت...") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (adminChatReplyDraft.isNotBlank()) {
                                    val msg = ChatMessage(
                                        senderName = "الإدارة العليا 🛡️",
                                        messageText = adminChatReplyDraft,
                                        isFromAdmin = true
                                    )
                                    FirebaseManager.sendChatMessage(msg) {
                                        adminChatReplyDraft = ""
                                        Toast.makeText(context, "تم إرسال الرد السحابي الفوري!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("أرسل رداً")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("سجل المحادثات المزامنة:", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                FirebaseManager.clearChats {
                                    Toast.makeText(context, "تم مسح تفريغ المحادثات السابقة!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("تفريغ وجرف السجل 🆑", fontSize = 10.sp)
                        }
                    }

                    chats.forEach { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (chat.isFromAdmin) Color.DarkGray else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = chat.senderName, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                Text(text = chat.messageText, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            "CONFIGS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تخصيص الهوية والضوابط بالدليلي سحابياً:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Text("اختر ثيم لوحة ألوان المحتوى:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val themes = listOf("Classic Dark", "Yemen Red", "Ocean Blue", "luxury Golden", "Purple & Teal")
                    themes.forEach { th ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { configThemeType = th }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = configThemeType == th, onClick = { configThemeType = th })
                            Text(text = th, fontSize = 12.sp)
                        }
                    }

                    TextField(
                        value = configTermsByAdmin,
                        onValueChange = { configTermsByAdmin = it },
                        label = { Text("نص ضوابط وشروط فنيين اليمن بالواجهة") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = configFooterText,
                        onValueChange = { configFooterText = it },
                        label = { Text("نص التذييل وحفظ الحقوق") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = configChatDisabled, onCheckedChange = { configChatDisabled = it })
                        Text("إيقاف وتعطيل شات المحادثات المباشرة مؤقتاً")
                    }

                    if (configChatDisabled) {
                        TextField(
                            value = configChatWarningMsg,
                            onValueChange = { configChatWarningMsg = it },
                            label = { Text("رسالة تعطيل الشات المعروضة للعميل") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            val newConf = config.copy(
                                themeType = configThemeType,
                                registrationTerms = configTermsByAdmin,
                                footerText = configFooterText,
                                chatDisabled = configChatDisabled,
                                chatDisabledMessage = configChatWarningMsg
                            )
                            FirebaseManager.updateConfig(newConf) {
                                Toast.makeText(context, "تم نشر المزايدات والتحديثات بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تثبيت وتحديث الخيارات سحابياً 💾")
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // SUPERVISORS ACCOUNT GENERATION IN THE CLOUD
                    Text("👥 حسابات وإدارة المشرفين المساعدين (Database Accounts):", fontWeight = FontWeight.Bold)
                    TextField(value = newSuperUser, onValueChange = { newSuperUser = it }, label = { Text("اسم مستخدم المشرف الجديد") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newSuperPass, onValueChange = { newSuperPass = it }, label = { Text("كلمة مرور المشرف") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (newSuperUser.isEmpty() || newSuperPass.isEmpty()) return@Button
                            val mod = Moderator(username = newSuperUser, password = newSuperPass)
                            FirebaseManager.saveSupervisor(mod) {
                                Toast.makeText(context, "تم حفظ وترخيص حساب المشرف السحابي الجديد!", Toast.LENGTH_SHORT).show()
                                newSuperUser = ""
                                newSuperPass = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إنشاء حساب المشرف المساعد")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    supervisors.forEach { sup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "👤 ${sup.username} | كلمة مرور: ${sup.password}", fontSize = 11.sp, color = Color.White)
                            IconButton(onClick = {
                                FirebaseManager.deleteSupervisor(sup.id) {
                                    Toast.makeText(context, "تم سحب ترخيص المشرف", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardViewOld(
    context: Context,
    config: AppConfig,
    onClose: () -> Unit
) {
    var appNameInput by remember { mutableStateOf(config.appName) }
    var primaryColorInput by remember { mutableStateOf(config.primaryColorHex) }
    var secondaryColorInput by remember { mutableStateOf(config.secondaryColorHex) }
    var logoUrlInput by remember { mutableStateOf(config.logoUrl) }
    var themeTypeInput by remember { mutableStateOf(config.themeType) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Owner Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "👑 البوابة السحابية السرية للمالك",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "تحكم مباشر بهوية التطبيق وشعاره وثيمه لجميع الأجهزة",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إغلاق", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Section settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚙️ إعدادات هوية العلامة التجارية:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 1. App name
                TextField(
                    value = appNameInput,
                    onValueChange = { appNameInput = it },
                    label = { Text("اسم التطبيق المعروض (App Display Name)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 2. Logo URL
                TextField(
                    value = logoUrlInput,
                    onValueChange = { logoUrlInput = it },
                    label = { Text("رابط الشعار المخصص (Custom Logo URL)") },
                    placeholder = { Text("https://example.com/logo.png") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 3. Select theme mode or Custom Colors
                Text("نوع الثيم لتلوين التطبيق:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val themes = listOf("Classic Dark", "Yemen Red", "Ocean Blue", "luxury Golden", "Purple & Teal", "Custom Colors")
                themes.forEach { th ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { themeTypeInput = th }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = themeTypeInput == th, onClick = { themeTypeInput = th })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = th, fontSize = 13.sp)
                    }
                }

                if (themeTypeInput == "Custom Colors") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("حدد الألوان السداسية عشرية (Hex Colors):", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    
                    TextField(
                        value = primaryColorInput,
                        onValueChange = { primaryColorInput = it },
                        label = { Text("اللون الأساسي السداسي (Primary Hex e.g. #EAA135)") },
                        placeholder = { Text("#EAA135") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    TextField(
                        value = secondaryColorInput,
                        onValueChange = { secondaryColorInput = it },
                        label = { Text("اللون الثانوي السداسي (Secondary Hex e.g. #2D2F33)") },
                        placeholder = { Text("#2D2F33") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (appNameInput.trim().isEmpty()) {
                            Toast.makeText(context, "الرجاء تحديد اسم صالح للتطبيق", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Save configuration
                        val updated = config.copy(
                            appName = appNameInput.trim(),
                            logoUrl = logoUrlInput.trim(),
                            themeType = themeTypeInput,
                            primaryColorHex = primaryColorInput.trim(),
                            secondaryColorHex = secondaryColorInput.trim()
                        )
                        FirebaseManager.updateConfig(updated) {
                            Toast.makeText(context, "تم حفظ وتعميم التعديلات السحرية على كافة الأجهزة فوراً!", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ ونشر التحديثات للجميع 💾", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

object GeminiHelper {
    private fun checkOfflineKeywords(prompt: String): String? {
        val p = prompt.trim()
        if (p.contains("أقسام") || p.contains("اقسام") || p.contains("فئة") || p.contains("فئات") || p.contains("مهن") || p.contains("مهنة") || p.contains("متوفر")) {
            return "مرحباً بك في المساعد المحلي المباشر 🤖\n\nالأقسام المتوفرة حالياً في التطبيق هي:\n🔧 سباكة وتمديدات\n⚡ طاقة شمسية وكهرباء\n📱 برمجة وصيانة هواتف\n🧹 نظافة وصيانة منزلية\n🚗 صيانة سيارات وميكانيك\n\nويمكنك العثور على جميع الفئات والمهنيين مباشرة في واجهة التطبيق الرئيسية."
        }
        if (p.contains("اتصال") || p.contains("اتصل") || p.contains("تواصل") || p.contains("راسل") || p.contains("واتساب")) {
            return "مرحباً بك في المساعد المحلي المباشر 🤖\n\nطريقة الاتصال بمقدمي الخدمات:\n1️⃣ تصفح الفنيين أو ابحث عن الاسم أو المهنة.\n2️⃣ اضغط على بطاقة فني الخدمة لعرض تفاصيله الكاملة.\n3️⃣ ستجد بداخلها زر 'اتصال هاتفي مباشر' 📞 وزر 'مراسلة عبر واتس اب' 💬 للتواصل الفوري السريع معه."
        }
        if (p.contains("رقم") || p.contains("دعم") || p.contains("فني") || p.contains("مساعدة") || p.contains("رقم الدعم")) {
            return "مرحباً بك في المساعد المحلي المباشر 🤖\n\nللدعم السحابي والتقني، يمكنك الاتصال فوراً برقم الدعم الفني العام الموحد لتطبيق كل خدمات اليمن:\n📞 هاتف وواتساب: 777644670\n📨 بريد إلكتروني: support@dalyly.yemen"
        }
        if (p.contains("بلاغ") || p.contains("ابلاغ") || p.contains("أبلغ") || p.contains("اشتكي") || p.contains("شكوى")) {
            return "مرحباً بك في المساعد المحلي المباشر 🤖\n\nخطوات تقديم بلاغ سريع:\n1️⃣ افتح صفحة تفاصيل مقدم الخدمة المعني.\n2️⃣ اضغط على زر 'الإبلاغ عن مقدم الخدمة' ⚠️ التابع للتذييل والصفحة.\n3️⃣ اكتب اسمك وجوالك ووصف البلاغ، ثم اضغط 'إرسال البلاغ'. وسيتم اتخاذ الإجراء من المشرفين في فترة قصيرة."
        }
        return null
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            val offlineAnswer = checkOfflineKeywords(prompt)
            if (offlineAnswer != null) return@withContext offlineAnswer
            return@withContext "خطأ: لم يتم تهيئة مفتاح API الخاص بالذكاء الاصطناعي.\nيمكنك تجربة الأسئلة المحلية المباشرة لليمن مثل: 'الأقسام المتوفرة'، 'طريقة الاتصال'، 'رقم الدعم'، أو 'طريقة تقديم بلاغ'."
        }
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            // Construct simple JSON: {"contents": [{"parts": [{"text": "prompt"}]}]}
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val os = conn.outputStream
            val writer = OutputStreamWriter(os, "UTF-8")
            writer.write(requestJson.toString())
            writer.flush()
            writer.close()
            os.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                // Parse response JSON: candidates[0].content.parts[0].text
                val root = JSONObject(responseText)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    if (content != null) {
                        val parts = content.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text")
                        }
                    }
                }
                "لم يتم استلام نص استجابة من المساعد."
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val offlineAnswer = checkOfflineKeywords(prompt)
                if (offlineAnswer != null) return@withContext offlineAnswer
                "خطأ في الاتصال بالذكاء الاصطناعي السحابي: $responseCode\n$errorText"
            }
        } catch (e: Exception) {
            val offlineAnswer = checkOfflineKeywords(prompt)
            if (offlineAnswer != null) return@withContext offlineAnswer
            "عذراً، لا يوجد اتصال نشط بالشبكة حالياً 🌐.\nيمكنك الاستفسار محلياً وسأقوم بالإجابة الفورية عن الأسئلة التالية:\n- الأقسام المتوفرة\n- طريقة الاتصال بالفنيين\n- رقم الدعم الفني باليمن\n- طريقة تقديم بلاغ"
        }
    }
}

@Composable
fun AboutAppScreenView(config: AppConfig, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = config.aboutTitle.ifBlank { "بوابتك إلى الخدمات المباشرة باليمن" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.ExtraBold
        )

        // Editable component under text: Image, Text, or HIDE!
        if (config.aboutImageOrTextType == "TEXT") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = config.aboutImageOrTextValue.ifBlank { "تواصل فوري، مباشر وبدون وسيط في كل المحافظات اليمنية ✨" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (config.aboutImageOrTextType == "IMAGE" || config.aboutImageOrTextType.isBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                val bannerSource = config.aboutImageOrTextValue.ifBlank { config.aboutImageUrl.ifBlank { "https://images.unsplash.com/photo-1581092921461-eab62e97a780" } }
                val aboutPainter = coil.compose.rememberAsyncImagePainter(model = bannerSource)
                Image(
                    painter = aboutPainter,
                    contentDescription = "About Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // About Description Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = config.aboutText.ifBlank { "تطبيق دليلي للخدمات اليمنية هو دليل فني متكامل يجمع أفضل مقدمي الخدمات الفنية والمهنية في مكان واحد." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        val visibleActions = remember(config.supportPhone, config.supportWhatsapp, config.supportEmail, config.shareUrl) {
            val list = mutableListOf<String>()
            if (config.supportPhone.isNotBlank()) list.add("PHONE")
            if (config.supportWhatsapp.isNotBlank()) list.add("WHATSAPP")
            if (config.supportEmail.isNotBlank()) list.add("EMAIL")
            if (config.shareUrl.isNotBlank()) list.add("SHARE")
            list
        }

        if (visibleActions.isNotEmpty()) {
            Text(
                text = "📞 قنوات التواصل والدعم الفني المباشر:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            // Grid of interactive action buttons (dynamically sized and shown!)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                visibleActions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { item ->
                            val cardModifier = Modifier.weight(1f).clickable {
                                when (item) {
                                    "PHONE" -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${config.supportPhone}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل فتح تطبيق الاتصال", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "WHATSAPP" -> {
                                        try {
                                            val url = "https://api.whatsapp.com/send?phone=${config.supportWhatsapp}"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل فتح تطبيق واتساب", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "EMAIL" -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${config.supportEmail}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل فتح تطبيق البريد الإلكتروني", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    "SHARE" -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "قم بتحميل تطبيق دليلي للخدمات والصيانة في اليمن الآن وتواصل مع أفضل الفنيين الفوريين سحابياً:\n${config.shareUrl}")
                                            }
                                            context.startActivity(Intent.createChooser(intent, "مشاركة التطبيق عبر:"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل فتح واجهة المشاركة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = cardModifier,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    when (item) {
                                        "PHONE" -> {
                                            Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("اتصال هاتفي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(config.supportPhone, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        "WHATSAPP" -> {
                                            Icon(imageVector = Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color.Green)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("مراسلة واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(config.supportWhatsapp, fontSize = 10.sp, color = Color.Gray)
                                        }
                                        "EMAIL" -> {
                                            Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.secondary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("البريد الإلكتروني", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(if (config.supportEmail.length > 15) config.supportEmail.take(12) + "..." else config.supportEmail, fontSize = 9.sp, color = Color.Gray)
                                        }
                                        "SHARE" -> {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("مشاركة التطبيق", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("انشر الفائدة", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun CommercialOffersScreenView(onBack: () -> Unit, context: Context) {
    val offersList = FirebaseManager.commercialOffers.collectAsState().value.filter { it.active }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Custom Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "الرجوع للرئيسية",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "العروض التجارية والسلع للبيع 🛒",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(40.dp)) // placeholder to balance header
        }

        if (offersList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ثمة عروض جديدة يتم تجهيزها حالياً باليمن... ترقبونا! ✨",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            offersList.forEach { offer ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = offer.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (offer.price.isNotBlank()) {
                            Text(
                                text = "السعر المقدر: ${offer.price}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (offer.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = offer.imageUrl,
                                contentDescription = "صورة العرض",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Text(
                            text = offer.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            lineHeight = 20.sp
                        )

                        if (offer.contactPhone.isNotBlank()) {
                            Divider(color = Color.LightGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${offer.contactPhone}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر تشغيل تطبيق الهاتف", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Phone, contentDescription = "اتصال", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("اتصال مباشر", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${offer.contactPhone}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر تشغيل تطبيق واتساب", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("واتساب 💬", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingAIAssistantWidget(
    config: AppConfig,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    if (!config.aiAssistantVisible && !isExpanded) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // AI Chat History state (local to session)
    var aiMessages by remember { mutableStateOf(listOf(ChatMessage(senderName = "دليلي الذكي 🤖", messageText = config.welcomeMessage.ifBlank { "مرحباً بك! أنا مساعدك الذكي لخدمات اليمن. كيف يمكنني مساعدتك اليوم؟" }, isFromAdmin = true))) }
    var aiDraft by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun parseColor(hex: String, default: Color): Color {
        return try {
            val cleanHex = hex.trim().replace("#", "")
            if (cleanHex.length == 6) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else if (cleanHex.length == 8) {
                Color(android.graphics.Color.parseColor("#$cleanHex"))
            } else {
                default
            }
        } catch (e: Exception) {
            default
        }
    }

    val themeColor = parseColor(config.aiAssistantColorHex, MaterialTheme.colorScheme.primary)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                end = config.aiAssistantXOffset.dp,
                bottom = config.aiAssistantYOffset.dp
            ),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chat Dialog panel
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .width(310.dp)
                        .height(380.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, themeColor.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header panel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(themeColor)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🤖 المساعد الذكي (دليلي AI)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Icon(imageVector = Icons.Default.SmartToy, contentDescription = "AI", tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Messages list
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp)
                        ) {
                            items(aiMessages) { msg ->
                                val isAi = msg.isFromAdmin
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = if (isAi) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(
                                                color = if (isAi) Color.DarkGray else themeColor.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(8.dp)
                                    ) {
                                        Text(text = if (isAi) "مساعد دليلي الذكي 🤖" else "أنت", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeColor)
                                        Text(text = msg.messageText, fontSize = 11.sp, color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                    }
                                }
                            }
                            if (isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = themeColor)
                                    }
                                }
                            }
                        }

                        // Message input box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = aiDraft,
                                onValueChange = { aiDraft = it },
                                placeholder = { Text("اطرح سؤالاً...", color = if (isSystemInDarkTheme()) Color.LightGray else Color.Gray) },
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                textStyle = androidx.compose.ui.text.TextStyle(color = if (isSystemInDarkTheme()) Color.White else Color.Black),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                    unfocusedTextColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                    focusedBorderColor = themeColor,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF9F9F9),
                                    unfocusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF9F9F9)
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (aiDraft.isNotBlank() && !isLoading) {
                                        isLoading = true
                                        val userMsg = aiDraft
                                        aiMessages = aiMessages + ChatMessage(senderName = "أنت", messageText = userMsg, isFromAdmin = false)
                                        aiDraft = ""
                                        scope.launch {
                                            try {
                                                val response = GeminiHelper.generateResponse(userMsg)
                                                // Verify message does not exist already
                                                if (aiMessages.none { it.senderName == "المساعد الذكي 🤖" && it.messageText == response }) {
                                                    aiMessages = aiMessages + ChatMessage(senderName = "المساعد الذكي 🤖", messageText = response, isFromAdmin = true)
                                                }
                                            } catch (e: Exception) {
                                                aiMessages = aiMessages + ChatMessage(senderName = "المساعد الذكي 🤖", messageText = "عذراً، حدث خطأ في معالجة طلبك.", isFromAdmin = true)
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(themeColor, CircleShape),
                                enabled = aiDraft.isNotBlank() && !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send, 
                                    contentDescription = "Send", 
                                    tint = if (aiDraft.isNotBlank()) Color.Black else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Main Assistant Icon
            if (config.aiAssistantVisible) {
                FloatingActionButton(
                    onClick = onToggle,
                    containerColor = themeColor,
                    contentColor = Color.Black,
                    modifier = Modifier.size(config.aiAssistantSize.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.SmartToy,
                        contentDescription = "AI Assistant"
                    )
                }
            }
        }
    }
}
