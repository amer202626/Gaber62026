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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import java.io.ByteArrayOutputStream
import com.dalyly.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start live snapshot listening on Firestore collections
        FirebaseManager.startListening()

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

    // Theme Colors
    val colors = when (config.themeType) {
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
                onBackground = Color(0xFFFFFDF5),
                onSurface = Color(0xFFE8E5D8)
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
            onBackground = Color(0xFFE3E3E3),
            onSurface = Color(0xFFE3E3E3)
        )
        "Ocean Blue" -> darkColorScheme(
            primary = Color(0xFF00ADB5), // Cyan Tech
            secondary = Color(0xFF393E46), // Dark Slate Grey
            tertiary = Color(0xFF222831), // Deep Ocean Navy
            background = Color(0xFF0D1117),
            surface = Color(0xFF161B22),
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = Color(0xFFF0F6FC),
            onSurface = Color(0xFFC9D1D9)
        )
        "luxury Golden" -> darkColorScheme(
            primary = Color(0xFFD4AF37), // Metallic Gold
            secondary = Color(0xFFFFD700), // Bright Gold Accent
            tertiary = Color(0xFF2C2C2C), // Pitch Grey
            background = Color(0xFF0A0A0A),
            surface = Color(0xFF151515),
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = Color(0xFFFFFDF5),
            onSurface = Color(0xFFE8E5D8)
        )
        else -> darkColorScheme( // Classic Dark
            primary = Color(0xFFEAA135), // Golden Orange
            secondary = Color(0xFF2D2F33), // Warm Grey
            tertiary = Color(0xFFEAA135),
            background = Color(0xFF1A1B1F),
            surface = Color(0xFF232529),
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = Color(0xFFECEFF4),
            onSurface = Color(0xFFD8DEE9)
        )
    }

    MaterialTheme(
        colorScheme = colors,
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

    // UI Navigation & Filters states
    var selectedCategoryTabId by remember { mutableStateOf("") }
    var selectedCityFilter by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }

    // Admin state
    var isAdminMode by remember { mutableStateOf(false) }
    var loggedInSupervisor by remember { mutableStateOf<String?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var loginUser by remember { mutableStateOf("") }
    
    // Secret Owner Gateway states
    var secretClickCount by remember { mutableStateOf(0) }
    var showOwnerPasswordDialog by remember { mutableStateOf(false) }
    var ownerPasswordInput by remember { mutableStateOf("") }
    var isOwnerMode by remember { mutableStateOf(false) }
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

    // Set default category selected on first load
    LaunchedEffect(categories) {
        if (selectedCategoryTabId.isEmpty() && categories.isNotEmpty()) {
            selectedCategoryTabId = categories.first().id
        }
    }

    // Setup Activity-Result Launchers with JPG 60% compression
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                val outputStream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                val byteArray = outputStream.toByteArray()
                regPhotoUrl = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
                Toast.makeText(context, "تم تحميل وضغط صورة الملف بنجاح (الجودة 60%)!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل معالجة وضغط الصورة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            try {
                val outputStream = java.io.ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                val byteArray = outputStream.toByteArray()
                regSelfieUrl = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
                Toast.makeText(context, "تم التقاط وضغط الصورة الذاتية بنجاح!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "فشل معالجة الصورة الملتقطة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DalylyTheme(config = configState) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.platform.LayoutDirection.Rtl) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    // System dynamic footer configured instantly by database
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = configState.footerText,
                                fontSize = configState.footerFontSize.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
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
                            onLogout = {
                                isOwnerMode = false
                                loggedInSupervisor = null
                                isAdminMode = false
                                secretClickCount = 0
                                Toast.makeText(context, "تم تسجيل الخروج بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        )
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color.Green, CircleShape)
                                                )
                                                Text(
                                                    text = "تزامن فوري سحابي 🟢",
                                                    fontSize = 11.sp,
                                                    color = Color.LightGray
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                secretClickCount++
                                                if (secretClickCount >= 5) {
                                                    showOwnerPasswordDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(10.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = "Home Gateway",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = { showLoginDialog = true },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(10.dp)
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Admin Area",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Dynamic sliders / promotional Banner Ads configured by Database
                            if (banners.isNotEmpty()) {
                                SliderBannersView(banners = banners, context = context)
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
                                            modifier = Modifier.size(18.dp)
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp, bottom = 8.dp)
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

                        // Realtime Floating Support chat widget (synced with Firestore live snapshot!)
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
                                            loggedInSupervisor = if (isMainAdmin) "WAM2026" else (match?.username ?: "الإدارة العليا")
                                            isAdminMode = true
                                            showLoginDialog = false
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
                            Text("أيقونة العمل / ترخيص المهنة / شعار (معرض الصور):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1.3f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("تحميل من الجهاز 🖼️", fontSize = 11.sp)
                                }
                                if (regPhotoUrl.isNotEmpty()) {
                                    Text("تم التحميل ✅", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("اختياري", fontSize = 11.sp, color = Color.LightGray)
                                }
                            }

                            // Camera / Selfie Logic
                            if (regGender == "Male") {
                                Text("صورة شخصية سلفي Selfie (إلزامي لمطابقة بيانات الهوية للذكور):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { cameraLauncher.launch() },
                                        modifier = Modifier.weight(1.3f)
                                    ) {
                                        Text("افتح الكاميرا والتقط 🤳", fontSize = 11.sp, color = Color.Black)
                                    }
                                    if (regSelfieUrl.isNotEmpty()) {
                                        Text("تم الالتقاط ✅", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("مطلوب 🚨", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        "🛡️ لحماية الخصوصية المطلقة، لا يُطلب من مقدمات الخدمة الإناث التقاط صورة ذاتية Selfie. يمكنك الاكتفاء برفع أيقونة التخصص أو أي صورة رمزية.",
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(10.dp),
                                        color = Color.LightGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

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
                                            Toast.makeText(
                                                context,
                                                if (approvedImmediately) "تم تفعيلك ونشر خدماتك فوراً بالصفحة!" else "تم تقديم الطلب للمراجعة والتدقيق الفني!",
                                                Toast.LENGTH_LONG
                                            ).show()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
                                    .background(MaterialTheme.colorScheme.primary)
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
                                    Text("أهلاً بك! الرجاء كتابة اسمك الكريم لبدء المحادثة:", fontSize = 11.sp, color = Color.White)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    var tempName by remember { mutableStateOf("") }
                                    TextField(
                                        value = tempName,
                                        onValueChange = { tempName = it },
                                        placeholder = { Text("مثال: أمين ردمان") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { onNameSave(tempName) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("ابدأ المحادثة")
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
                                                        color = if (isAdmin) Color.DarkGray else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Text(text = msg.senderName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(text = msg.messageText, fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                // Message input
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextField(
                                        value = draftText,
                                        onValueChange = { onDraftChange(it) },
                                        placeholder = { Text("تفاصيل المشكلة للتحقق...") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    IconButton(
                                        onClick = onSendMessage,
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            border = BorderStroke(1.dp, Color.Orange)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
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
                    val themes = listOf("Classic Dark", "Yemen Red", "Ocean Blue", "luxury Golden")
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
                val themes = listOf("Classic Dark", "Yemen Red", "Ocean Blue", "luxury Golden", "Custom Colors")
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
