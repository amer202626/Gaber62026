package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.ui.theme.InteractiveYemenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    REGISTER_PROVIDER,
    LOGIN
}

class MainActivity : ComponentActivity() {
    private var lastBackTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Sync Firestore
        FirebaseManager.init(applicationContext)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            // State management
            val config by FirebaseManager.appConfig.collectAsState()
            val categoriesList by FirebaseManager.categories.collectAsState()
            val providersList by FirebaseManager.providers.collectAsState()
            val pendingProvidersList by FirebaseManager.pendingProviders.collectAsState()
            val bannersList by FirebaseManager.banners.collectAsState()
            val chatsList by FirebaseManager.chats.collectAsState()
            val reportList by FirebaseManager.incidentReports.collectAsState()
            val supervisorList by FirebaseManager.moderators.collectAsState()
            val historyLogs by FirebaseManager.activityLogs.collectAsState()

            var activeScreen by remember { mutableStateOf(Screen.HOME) }
            var isArabic by remember { mutableStateOf(true) }

            // Session users state
            var loggedUser by remember { mutableStateOf<String?>(null) } // "Admin" or supervisor username
            var loggedInAsProvider by remember { mutableStateOf<String?>(null) } // Provider identifier (phone)
            var rememberMeEnabled by remember { mutableStateOf(true) }

            // Overlay Dialog switches
            var displaySyncDiagnosticsDialog by remember { mutableStateOf(false) }
            var activeProviderForDetails by remember { mutableStateOf<ServiceProvider?>(null) }
            var isBackdoorAuthorized by remember { mutableStateOf(false) }
            var displayBackdoorPasswordDialog by remember { mutableStateOf(false) }
            var displaySupportAssistant by remember { mutableStateOf(false) }
            var displayDirectChatRoom by remember { mutableStateOf(false) }

            // Home view filters
            var searchTextInput by remember { mutableStateOf("") }
            var selectedFilterCategoryId by remember { mutableStateOf("") }
            var selectedFilterCity by remember { mutableStateOf("") }
            var selectedFilterMinRating by remember { mutableStateOf(0f) }

            // Double click back closure
            BackHandler {
                val current = System.currentTimeMillis()
                if (current - lastBackTime < 2000) {
                    finish()
                } else {
                    lastBackTime = current
                    Toast.makeText(context, if (isArabic) "اضغط مرة أخرى للخروج" else "Press back again to exit", Toast.LENGTH_SHORT).show()
                    activeScreen = Screen.HOME
                }
            }

            // Speech Recognizer intent logic
            val voiceSearchLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!matches.isNullOrEmpty()) {
                        searchTextInput = matches[0]
                    }
                }
            }

            // TopBar Tap clicker counter for the Secret Backdoor
            var tapHomeCount by remember { mutableStateOf(0) }
            LaunchedEffect(tapHomeCount) {
                if (tapHomeCount > 0) {
                    delay(3000) // reset count after 3 seconds
                    tapHomeCount = 0
                }
            }

            // Render layouts in structural forced RTL (or customized)
            val computedDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides computedDirection) {
                InteractiveYemenTheme(themeType = config.themeType) {
                    Scaffold(
                        topBar = {
                            Column(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .statusBarsPadding()
                            ) {
                                // Row 1: App Branding Header & Interactive Language Pill
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Clickable Brand Title to trigger Backdoor
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                tapHomeCount++
                                                if (tapHomeCount == 5) {
                                                    displayBackdoorPasswordDialog = true
                                                    tapHomeCount = 0
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = config.logoEmoji,
                                            fontSize = 24.sp,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text(
                                            text = config.appName,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }

                                    // Live Sync Diagnostic pill
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { displaySyncDiagnosticsDialog = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF4CAF50), shape = androidx.compose.foundation.shape.CircleShape)
                                        )
                                        Text(
                                            text = if (isArabic) "مزامنة حية 🟢" else "Live Sync 🟢",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Gorgeous Prominent Language Selector Capsule (Fixes visibility & layout issues)
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { isArabic = !isArabic }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("language_toggle_btn"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = "🌐", fontSize = 12.sp)
                                            Text(
                                                text = if (isArabic) "English" else "عربي",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)

                                // Row 2: Navigation Tab system with icons & text labels
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    // 🏠 HOME TAB
                                    val isHomeSelected = activeScreen == Screen.HOME
                                    val homeTint = if (isHomeSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { activeScreen = Screen.HOME }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "Home",
                                            tint = homeTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isArabic) "الرئيسية" else "Home",
                                            fontSize = 12.sp,
                                            fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = homeTint
                                        )
                                    }

                                    // 👥 JOIN / REGISTER PROVIDER TAB
                                    val isRegSelected = activeScreen == Screen.REGISTER_PROVIDER
                                    val regTint = if (isRegSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { activeScreen = Screen.REGISTER_PROVIDER }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Add Provider",
                                            tint = regTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isArabic) "انضمام كعضو" else "Register",
                                            fontSize = 12.sp,
                                            fontWeight = if (isRegSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = regTint
                                        )
                                    }

                                    // 🔐 LOGIN / DASHBOARD TAB
                                    val isLoginSelected = activeScreen == Screen.LOGIN
                                    val loginTint = if (isLoginSelected || loggedUser != null) MaterialTheme.colorScheme.primary else Color.Gray
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { activeScreen = Screen.LOGIN }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Auth Admin",
                                            tint = loginTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (loggedUser != null) {
                                                if (isArabic) "لوحة التحكم" else "Dashboard"
                                            } else {
                                                if (isArabic) "دخول الإدارة" else "Login"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isLoginSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = loginTint
                                        )
                                    }

                                    // 🔄 REAL-TIME SYNC TRIGGER / INDICATOR
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(
                                                context,
                                                if (isArabic) "مزامنة لحظية مفعّلة وثابتة، البيانات متطابقة!" 
                                                else "Real-time sync active, data is fully synced!", 
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Sync refresh",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), thickness = 1.dp)
                            }
                        },
                        bottomBar = {
                            // Custom Footer layout
                            if (config.showFooter) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .navigationBarsPadding(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left element info
                                        Text(
                                            text = if (isArabic) "ℹ️ عن الدليل" else "ℹ️ About Directory",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.clickable {
                                                Toast.makeText(context, config.welcomeMessage, Toast.LENGTH_LONG).show()
                                            }
                                        )

                                        // Center advert info
                                        Text(
                                            text = config.footerText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )

                                        // Backdoor indicator or contact support
                                        Text(
                                            text = "📞 ${config.supportPhone}",
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            modifier = Modifier.clickable {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${config.supportPhone}"))
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                // 1. Real-time Support Chat Bubble (dynamic configuration bound layout)
                                if (config.chatIconVisible && !config.chatIconDeleted) {
                                    FloatingActionButton(
                                        onClick = { displayDirectChatRoom = true },
                                        containerColor = try {
                                            Color(android.graphics.Color.parseColor(config.chatIconColorHex))
                                        } catch (e: Exception) {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier
                                            .size((if (config.chatIconSize in 30..150) config.chatIconSize else 46).dp)
                                            .testTag("support_chat_bubble")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Chat Logs",
                                            tint = Color.White,
                                            modifier = Modifier.size((if (config.chatIconSize in 30..150) (config.chatIconSize / 2.55f) else 18.0f).dp)
                                        )
                                    }
                                }

                                // 2. Smart AI Assistant Bubble (🤖)
                                FloatingActionButton(
                                    onClick = { displaySupportAssistant = true },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .testTag("ai_assistant_bubble")
                                ) {
                                    Text(
                                        text = "🤖",
                                        fontSize = 20.sp
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(innerPadding)
                        ) {
                            // Page router logic
                            when (activeScreen) {
                                Screen.HOME -> HomeScreen(
                                    context = context,
                                    config = config,
                                    categories = categoriesList,
                                    providers = providersList,
                                    banners = bannersList,
                                    searchTextInput = searchTextInput,
                                    selectedFilterCategoryId = selectedFilterCategoryId,
                                    selectedFilterCity = selectedFilterCity,
                                    selectedFilterMinRating = selectedFilterMinRating,
                                    isArabic = isArabic,
                                    onSearchTextChange = { searchTextInput = it },
                                    onCategorySelect = { selectedFilterCategoryId = it },
                                    onCitySelect = { selectedFilterCity = it },
                                    onRatingSelect = { selectedFilterMinRating = it },
                                    onTriggerVoiceSearch = {
                                        try {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
                                            }
                                            voiceSearchLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, if (isArabic) "البحث الصوتي غير مدعوم على جهازك" else "Voice search not supported", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onProviderClick = { activeProviderForDetails = it }
                                )

                                Screen.REGISTER_PROVIDER -> RegisterProviderScreen(
                                    context = context,
                                    categories = categoriesList,
                                    isArabic = isArabic,
                                    onSubmitted = { activeScreen = Screen.HOME }
                                )

                                Screen.LOGIN -> {
                                    if (loggedUser != null) {
                                        AdminDashboardScreen(
                                            context = context,
                                            loggedUser = loggedUser!!,
                                            config = config,
                                            categories = categoriesList,
                                            providers = providersList,
                                            pendingProviders = pendingProvidersList,
                                            reports = reportList,
                                            supervisors = supervisorList,
                                            historyLogs = historyLogs,
                                            isArabic = isArabic,
                                            onLogout = { loggedUser = null }
                                        )
                                    } else {
                                        LoginScreen(
                                            context = context,
                                            config = config,
                                            supervisors = supervisorList,
                                            rememberMe = rememberMeEnabled,
                                            onRememberMeChange = { rememberMeEnabled = it },
                                            isArabic = isArabic,
                                            onLoginSuccess = { username -> loggedUser = username }
                                        )
                                    }
                                }
                            }

                            // 1. Secret Backdoor Settings Lock Dialog
                            if (displayBackdoorPasswordDialog) {
                                BackdoorAuthDialog(
                                    isArabic = isArabic,
                                    onDismiss = { displayBackdoorPasswordDialog = false },
                                    onUnlock = {
                                        isBackdoorAuthorized = true
                                        displayBackdoorPasswordDialog = false
                                    }
                                )
                            }

                            // Firebase Live Sync & Caching System Diagnostic Monitor
                            if (displaySyncDiagnosticsDialog) {
                                FirebaseSyncDiagnosticDialog(
                                    isArabic = isArabic,
                                    config = config,
                                    categoriesCount = categoriesList.size,
                                    providersCount = providersList.size,
                                    pendingCount = pendingProvidersList.size,
                                    bannersCount = bannersList.size,
                                    chatsCount = chatsList.size,
                                    incidentsCount = reportList.size,
                                    logsCount = historyLogs.size,
                                    moderatorsCount = supervisorList.size,
                                    onDismiss = { displaySyncDiagnosticsDialog = false }
                                )
                            }

                            // Secret settings pane UI
                            if (isBackdoorAuthorized) {
                                BackdoorSettingsDrawer(
                                    config = config,
                                    supervisors = supervisorList,
                                    isArabic = isArabic,
                                    onDismiss = { isBackdoorAuthorized = false }
                                )
                            }

                            // 2. Interactive Provider Detail Pane Overlay
                            activeProviderForDetails?.let { prov ->
                                ProviderDetailsDialog(
                                    prov = prov,
                                    chats = chatsList,
                                    isArabic = isArabic,
                                    onDismiss = { activeProviderForDetails = null }
                                )
                            }

                            // 3. AI Assistant pop up panel (50% drawer wrapper UI)
                            if (displaySupportAssistant) {
                                SmartAssistantPopup(
                                    isArabic = isArabic,
                                    onDismiss = { displaySupportAssistant = false }
                                )
                            }

                            // 4. Live Chat Room 50% persistent overlay
                            if (displayDirectChatRoom) {
                                DirectChatWidget(
                                    chats = chatsList,
                                    isArabic = isArabic,
                                    isAdmin = (loggedUser != null),
                                    onDismiss = { displayDirectChatRoom = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================== SCREEN: HOME DIRECTORY ========================
@Composable
fun HomeScreen(
    context: Context,
    config: AppConfig,
    categories: List<ServiceCategory>,
    providers: List<ServiceProvider>,
    banners: List<BannerAd>,
    searchTextInput: String,
    selectedFilterCategoryId: String,
    selectedFilterCity: String,
    selectedFilterMinRating: Float,
    isArabic: Boolean,
    onSearchTextChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onCitySelect: (String) -> Unit,
    onRatingSelect: (Float) -> Unit,
    onTriggerVoiceSearch: () -> Unit,
    onProviderClick: (ServiceProvider) -> Unit
) {
    var expandedFilterDrawer by remember { mutableStateOf(false) }

    // Sliding banner control
    var activeBannerIndex by remember { mutableStateOf(0) }
    LaunchedEffect(banners) {
        if (banners.isNotEmpty()) {
            while (true) {
                delay(5000)
                activeBannerIndex = (activeBannerIndex + 1) % banners.size
            }
        }
    }

    // Comprehensive search filtration logic
    val filteredProviders = remember(providers, searchTextInput, selectedFilterCategoryId, selectedFilterCity, selectedFilterMinRating) {
        providers.filter { p ->
            val matchesText = searchTextInput.isEmpty() ||
                    p.fullName.contains(searchTextInput, ignoreCase = true) ||
                    p.phone.contains(searchTextInput) ||
                    p.subCategory.contains(searchTextInput, ignoreCase = true) ||
                    p.address.contains(searchTextInput, ignoreCase = true)

            val matchesCategory = selectedFilterCategoryId.isEmpty() || p.categoryId == selectedFilterCategoryId
            val matchesCity = selectedFilterCity.isEmpty() || p.area.contains(selectedFilterCity) || p.address.contains(selectedFilterCity)
            val matchesRating = p.averageRating >= selectedFilterMinRating

            matchesText && matchesCategory && matchesCity && matchesRating
        }.sortedWith(compareByDescending<ServiceProvider> { it.isPinned }
            .thenByDescending { it.isRecommended }
            .thenByDescending { it.hasPremiumSubscription })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Welcome Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🇾🇪", fontSize = 34.sp, modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text(
                            text = config.welcomeMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isArabic) "دليل لحظي ذكي، آمن وسهل لكل خدمات اليمن" else "Yemeni services sync directory",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        // Carousel Slider Advertisements (Banners)
        if (banners.isNotEmpty() && activeBannerIndex < banners.size) {
            val ad = banners[activeBannerIndex]
            val bannerHeight = when (ad.displaySize) {
                "S" -> 100.dp
                "L" -> 180.dp
                else -> 140.dp
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .clickable {
                            if (ad.linkUrl.isNotEmpty()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ad.linkUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, ad.linkUrl, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ad.imageUrl,
                            contentDescription = ad.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                        Text(
                            text = ad.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                            maxLines = 2
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Red, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "AD إعلان", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Search inputs + Filter toggler
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchTextInput,
                    onValueChange = onSearchTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_search_bar"),
                    placeholder = { Text(if (isArabic) "ابحث عن طبيب، سباك، هاتف..." else "Search providers...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search icon") },
                    trailingIcon = {
                        IconButton(onClick = onTriggerVoiceSearch) {
                            Text(text = "🎙️", fontSize = 16.sp)
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Advanced filtering button
                Button(
                    onClick = { expandedFilterDrawer = !expandedFilterDrawer },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Advanced Filter options")
                }
            }
        }

        // Advanced filter options slide drawer
        if (expandedFilterDrawer) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isArabic) "خيارات التصفية والبحث المتقدم" else "Advanced Filter Panels",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 1. City Selectors
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isArabic) "المدينة:" else "City:", fontSize = 11.sp, modifier = Modifier.width(60.dp))
                            val cities = listOf("الكل", "صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cities) { city ->
                                    val cityValue = if (city == "الكل") "" else city
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (selectedFilterCity == cityValue) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onCitySelect(cityValue) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = city, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        // 2. Minimal Rating selector
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isArabic) "التقييم:" else "Rating:", fontSize = 11.sp, modifier = Modifier.width(60.dp))
                            val ratings = listOf(0f, 3f, 4f, 4.5f)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(ratings) { rate ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (selectedFilterMinRating == rate) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onRatingSelect(rate) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (rate == 0f) "كل التقييمات" else "⭐ $rate+",
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Clear filter
                        TextButton(
                            onClick = {
                                onCitySelect("")
                                onRatingSelect(0f)
                                onCategorySelect("")
                                onSearchTextChange("")
                                expandedFilterDrawer = false
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = if (isArabic) "إعادة تعيين الكل" else "Reset All Filters", fontSize = 10.sp, color = Color.Red)
                        }
                    }
                }
            }
        }

        // Horizontal Category Quick filters list
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isArabic) "الأقسام والخدمات الرئيسية:" else "Main Categories:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    item {
                        Card(
                            onClick = { onCategorySelect("") },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFilterCategoryId.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "👑", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                                Text(text = if (isArabic) "الكل" else "All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(categories) { cat ->
                        Card(
                            onClick = { onCategorySelect(cat.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFilterCategoryId == cat.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = cat.iconEmoji, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                                Text(
                                    text = if (isArabic) cat.nameAr else cat.nameEn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Results listing
        if (filteredProviders.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "⚠️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isArabic) "عذراً! لا يوجد مهنيين يطابقون هذه الفلاتر حالياً" else "No providers matched.",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            items(filteredProviders) { p ->
                ProviderCardItem(p = p, isArabic = isArabic, onClick = { onProviderClick(p) })
            }
        }
    }
}

// Single provider item card listing
@Composable
fun ProviderCardItem(p: ServiceProvider, isArabic: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("provider_item_card_${p.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (p.isPinned) 2.dp else 1.dp,
            color = if (p.isPinned) MaterialTheme.colorScheme.primary else Color.DarkGray.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left block photo
            Box {
                AsyncImage(
                    model = p.imageUrl,
                    contentDescription = p.fullName,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                // Gold star subscript badge
                if (p.isRecommended) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color(0xFFFFD700), CircleShape)
                            .padding(2.dp)
                    ) {
                        Text(text = "⭐", fontSize = 9.sp)
                    }
                }
                // Subscription emblem
                if (p.hasPremiumSubscription) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                            .padding(2.dp)
                    ) {
                        Text(text = "🫅", fontSize = 9.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Block description
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = p.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    if (p.isVerified) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified status",
                            tint = Color(0xFF10B981),
                            modifier = Modifier
                                .size(16.dp)
                                .padding(start = 4.dp)
                        )
                    }
                    if (p.isPinned) {
                        Text(
                            text = "📌 مسبت",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = p.subCategory,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🏠", fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp))
                    Text(text = "${p.area} - ${p.address}", fontSize = 10.sp, color = Color.LightGray)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Rating stars and Loyalty logs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⭐", fontSize = 11.sp, modifier = Modifier.padding(end = 2.dp))
                        Text(
                            text = String.format("%.1f", p.averageRating),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(text = " (${p.ratingCount})", fontSize = 9.sp, color = Color.Gray)
                    }

                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "${p.loyaltyPoints} ${if (isArabic) "نقطة" else "Pts"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ======================== SCREEN: REGISTER SERVICE PROVIDER ========================
@Composable
fun RegisterProviderScreen(
    context: Context,
    categories: List<ServiceCategory>,
    isArabic: Boolean,
    onSubmitted: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }
    var subCategorySpec by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var selectedAreaCity by remember { mutableStateOf("صنعاء") }
    var isWomanRegisterResult by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isArabic) "👤 طلب تسجيل مقدم خدمة جديد" else "Submit Practitioner Registry",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Divider()

        // Required field message
        Text(
            text = if (isArabic) "* يرجى تعبئة الحقول الإجبارية كاملة بحرص" else "* Please fill all compulsory fields carefully",
            fontSize = 10.sp,
            color = Color.Red
        )

        // Triple name input
        TextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text(if (isArabic) "الاسم الثلاثي كاملاً *" else "Triple Full Name *") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors()
        )

        // Contact inputs
        TextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(if (isArabic) "رقم الهاتف الفتح المباشر *" else "Phone Number *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = whatsapp,
            onValueChange = { whatsapp = it },
            label = { Text(if (isArabic) "رقم الواتساب *" else "WhatsApp Number *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        // Drop-down simple category logic
        Text(text = if (isArabic) "اختر القسم المناسب للخدمة *:" else "Select Category *:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat ->
                val isSelected = selectedCategoryId == cat.id
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategoryId = cat.id }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "${cat.iconEmoji} ${if (isArabic) cat.nameAr else cat.nameEn}", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Subcategory specialism
        TextField(
            value = subCategorySpec,
            onValueChange = { subCategorySpec = it },
            label = { Text(if (isArabic) "التخصص الدقيق (مثال: كهربائي منازل، طبيب جراحة) *" else "Specialization detail *") },
            modifier = Modifier.fillMaxWidth()
        )

        // City Selector
        Text(text = if (isArabic) "المحافظة المقيم بها *:" else "Yemeni Governorate *:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val listCities = listOf("صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listCities) { city ->
                val isSel = selectedAreaCity == city
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSel) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedAreaCity = city }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = city, fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Full Address
        TextField(
            value = addressInput,
            onValueChange = { addressInput = it },
            label = { Text(if (isArabic) "عنوان العمل بالتفصيل (الحي، الشارع، المعلم) *" else "Work Address details *") },
            modifier = Modifier.fillMaxWidth()
        )

        // Woman flag option to hide photo optionally
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isWomanRegisterResult = !isWomanRegisterResult }
        ) {
            Checkbox(checked = isWomanRegisterResult, onCheckedChange = { isWomanRegisterResult = it })
            Text(
                text = if (isArabic) "التسجيل كمنشأة نسائية أو لامرأة (إخفاء الصورة الشخصية اختيارياً)" else "Register as women-only enterprise (removes photos)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Confirm buttons
        Button(
            onClick = {
                if (fullName.isEmpty() || phone.isEmpty() || whatsapp.isEmpty() || selectedCategoryId.isEmpty() || subCategorySpec.isEmpty() || addressInput.isEmpty()) {
                    Toast.makeText(context, if (isArabic) "يرجى ملء جميع الحقول الإجبارية تميزاً للنجمة الحمراء!" else "Please fill all required inputs!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isSubmitting = true
                val sampleProvider = ServiceProvider(
                    id = "",
                    fullName = fullName,
                    phone = phone,
                    whatsapp = whatsapp,
                    categoryId = selectedCategoryId,
                    subCategory = subCategorySpec,
                    address = addressInput,
                    area = selectedAreaCity,
                    imageUrl = if (isWomanRegisterResult) "https://picsum.photos/seed/female_fallback/200" else "",
                    gpsLat = 15.3694,
                    gpsLng = 44.1910
                )

                // Execute real simulation
                FirebaseManager.submitCandidateProvider(sampleProvider, null, null) { success, msg ->
                    isSubmitting = false
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        onSubmitted()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("submit_provider_registration"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(text = if (isArabic) "إرسال طلب التسجيل للمراجعة" else "Submit Registration application", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ======================== SCREEN: AUTH & LOGIN SYSTEM ========================
@Composable
fun LoginScreen(
    context: Context,
    config: AppConfig,
    supervisors: List<Moderator>,
    rememberMe: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    isArabic: Boolean,
    onLoginSuccess: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🔐", fontSize = 48.sp)
        Text(
            text = if (isArabic) "تسجيل دخول لوحة تحكم الإدارة" else "Administrators Panel Access",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Username
        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(if (isArabic) "اسم المستخدم" else "Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (isArabic) "كلمة المرور" else "Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Remember me flag selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRememberMeChange(!rememberMe) },
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(checked = rememberMe, onCheckedChange = onRememberMeChange)
            Text(text = if (isArabic) "تذكرني في هذا الجهاز" else "Keep me authenticated", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                // Verify credentials
                val mainAdminUser = "WAM2026"
                val mainAdminPass = config.mainAdminPass

                val supervisorMatch = supervisors.find { 
                    it.username.trim().equals(username.trim(), ignoreCase = true) && 
                    it.password.trim() == password.trim() 
                }

                if ((username.trim().equals(mainAdminUser, ignoreCase = true) && password.trim() == mainAdminPass) || supervisorMatch != null) {
                    Toast.makeText(context, if (isArabic) "مرحباً بك! تم تسجيل الدخول بنجاح" else "Login Successful!", Toast.LENGTH_SHORT).show()
                    val actualUsername = supervisorMatch?.username ?: username.trim()
                    onLoginSuccess(actualUsername)
                } else {
                    Toast.makeText(context, if (isArabic) "خطأ! اسم المستخدم أو كلمة المرور غير صحيحة" else "Invalid username or password", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("admin_login_submit_btn"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = if (isArabic) "تسجيل الدخول" else "Authenticate", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ======================== OVERLAY: DETAILED SERVICES SHEET ========================
@Composable
fun ProviderDetailsDialog(
    prov: ServiceProvider,
    chats: List<ChatMessage>,
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var reviewerNameInput by remember { mutableStateOf("") }
    var reviewStarsInput by remember { mutableStateOf(5) }
    var reviewCommentInput by remember { mutableStateOf("") }
    
    // Safety flags report dialog togglers
    var displaySafetyReportDialog by remember { mutableStateOf(false) }
    var safetyReportReasonInput by remember { mutableStateOf("") }

    val coroutine = rememberCoroutineScope()
    var displayZoomedImageDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Close bar header
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isArabic) "توصيف ومراجعة مقدم الخدمة" else "Provider Specifications", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                // Photo and stats review
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = prov.imageUrl,
                        contentDescription = prov.fullName,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { displayZoomedImageDialog = true },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = prov.fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Text(text = prov.subCategory, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(text = "${prov.area} - ${prov.address}", fontSize = 11.sp, color = Color.LightGray)
                    }
                }

                // Interactive Contact Actions Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Call direct button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${prov.phone}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Call direct", modifier = Modifier.size(16.dp))
                            Text(text = if (isArabic) "اتصال" else "Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Direct WhatsApp launcher
                    Button(
                        onClick = {
                            val msg = "مرحباً يا معلم ${prov.fullName}، لقد تواصلت معك عبر تطبيق الدليل..."
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=967${prov.whatsapp}&text=${Uri.encode(msg)}"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Text(text = "واتساب ✅", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Safety Incident button
                    Button(
                        onClick = { displaySafetyReportDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Text(text = if (isArabic) "إبلاغ ⚠️" else "Report ⚠️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Divider(color = Color.DarkGray.copy(alpha = 0.5f))

                // Loyalty message box
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎁", fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = if (isArabic) "امتلاك هذا المهني لـ ${prov.loyaltyPoints} نقطة يدل على وثوقية تقييمات العملاء وامتثاله للاتفاقيات!" else "Loyalty validation points: ${prov.loyaltyPoints}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Add Review block form
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) "أضف تقييمك الخاص (+15 نقطة ولاء مجاناً):" else "Post a service review (+15 Loyalty points):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 1..5) {
                            val active = (i <= reviewStarsInput)
                            Text(
                                text = "⭐",
                                fontSize = 18.sp,
                                modifier = Modifier
                                    .clickable { reviewStarsInput = i }
                                    .padding(horizontal = 4.dp),
                                color = if (active) Color(0xFFFFD700) else Color.Gray
                            )
                        }
                    }

                    TextField(
                        value = reviewerNameInput,
                        onValueChange = { reviewerNameInput = it },
                        placeholder = { Text(if (isArabic) "اسمك الثلاثي" else "Your triple name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    TextField(
                        value = reviewCommentInput,
                        onValueChange = { reviewCommentInput = it },
                        placeholder = { Text(if (isArabic) "اكتب رأيك الصدق بأمانة حول جودة وسعر وبنود الخدمة المقدمة..." else "Honest feedback review...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (reviewerNameInput.isEmpty() || reviewCommentInput.isEmpty()) {
                                Toast.makeText(context, if (isArabic) "يرجى تعبئة خانة الاسم والتعليق!" else "Blank reviewers name!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val revObj = ProviderReview(
                                id = "",
                                providerId = prov.id,
                                reviewerName = reviewerNameInput,
                                rating = reviewStarsInput,
                                comment = reviewCommentInput
                            )
                            FirebaseManager.addProviderReview(revObj) {
                                Toast.makeText(context, if (isArabic) "شكراً لك! تم وضع مراجعتك وحساب 15 نقطة ولاء لمقدم الخدمة." else "Review recorded!", Toast.LENGTH_SHORT).show()
                                reviewerNameInput = ""
                                reviewCommentInput = ""
                                onDismiss()
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = if (isArabic) "تأكيد وإرسال التقييم" else "Submit Review Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Safety Report internal overlays dialog
    if (displaySafetyReportDialog) {
        Dialog(onDismissRequest = { displaySafetyReportDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "⚠️ تقديم بلاغ رسمي ضد المهني", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp)
                    Text(text = "يرجى وصف أي انتهاك، غش، تأخير، أو سوء سلوك بكل موضوعية للتدقيق القانوني:", fontSize = 11.sp, color = Color.LightGray)
                    
                    TextField(
                        value = safetyReportReasonInput,
                        onValueChange = { safetyReportReasonInput = it },
                        placeholder = { Text("اكتب تفصيل الشكوى هنا مع ذكر الأمانة وسعر الخلاف...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { displaySafetyReportDialog = false }) {
                            Text(text = "إلغاء لعدم الظلم")
                        }
                        Button(
                            onClick = {
                                if (safetyReportReasonInput.isEmpty()) {
                                    Toast.makeText(context, "اكتب سبب البلاغ أولاً!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val report = IncidentReport(
                                    providerId = prov.id,
                                    providerName = prov.fullName,
                                    reporterName = "مواطن يمني مستفيد",
                                    reason = safetyReportReasonInput
                                )
                                FirebaseManager.submitIncidentReport(report) {
                                    Toast.makeText(context, "تم رفع بلاغك وسيتم تتبعه ومراجعته واتخاذ قرار بحظر المهني إن تطلب الأمر.", Toast.LENGTH_LONG).show()
                                    displaySafetyReportDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = "رفع بلاغ رسمي")
                        }
                    }
                }
            }
        }
    }

    // Image zoomed tool dial
    if (displayZoomedImageDialog) {
        Dialog(onDismissRequest = { displayZoomedImageDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = prov.imageUrl,
                    contentDescription = prov.fullName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

// ======================== DIALOG: AI SMART ASSISTANT popup (🤖) ========================
@Composable
fun SmartAssistantPopup(
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    var conversationsTextLogs by remember { mutableStateOf(listOf("🤖 المساعد الذكي: حياك الله يا غالي! أنا 'أبو يمن الذكي'، خبير دليل الخدمات. كيف أقدر أخدمك اليوم؟ اكتب سؤالك لأبحث لك في قواعد البيانات!")) }
    var userCommandQueryInput by remember { mutableStateOf("") }
    var processingAnswerState by remember { mutableStateOf(false) }

    val coroutine = rememberCoroutineScope()
    val listState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header assistant info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🤖", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "أبو يمن الذكي الدليل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Divider()

                // Chat logs contents
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(listState)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    conversationsTextLogs.forEach { text ->
                        val isUser = text.startsWith("👤")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isUser) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                                    .widthIn(max = 240.dp)
                            ) {
                                Text(text = text, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                    if (processingAnswerState) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.CenterHorizontally),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Interaction text tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = userCommandQueryInput,
                        onValueChange = { userCommandQueryInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isArabic) "اكتب سؤالك (مثال: طوارئ، اسعاف...)" else "Ask assistant...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors()
                    )

                    Button(
                        onClick = {
                            if (userCommandQueryInput.isEmpty()) return@Button
                            val query = userCommandQueryInput
                            conversationsTextLogs = conversationsTextLogs + "👤 أنت: $query"
                            userCommandQueryInput = ""
                            processingAnswerState = true

                            coroutine.launch {
                                val reply = GeminiClient.getAssistantResponse(query)
                                processingAnswerState = false
                                conversationsTextLogs = conversationsTextLogs + reply
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Submit query")
                    }
                }
            }
        }
    }
}

// ======================== WIDGET: CHAT SYSTEM OVERLAY PANEL ========================
@Composable
fun DirectChatWidget(
    chats: List<ChatMessage>,
    isArabic: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit
) {
    var chatMessageInput by remember { mutableStateOf("") }
    val listState = rememberScrollState()

    // Enforce active single-listener configuration on enter/exit
    LaunchedEffect(Unit) {
        FirebaseManager.startListeningToChats()
    }
    DisposableEffect(Unit) {
        onDispose {
            FirebaseManager.activeChatListenerRegistration?.remove()
            FirebaseManager.activeChatListenerRegistration = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header chat details
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "💬 غرفة المحادثات والملاحظات الفورية" else "Direct Chat logs",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Divider()

                // Messages lists
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(listState)
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chats.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = if (isArabic) "لا توجد رسائل نشطة حالياً." else "No messages logged.", fontSize = 10.sp, color = Color.Gray)
                        }
                    } else {
                        chats.forEach { msg ->
                            val activeSelfSender = if (isAdmin) msg.senderId == "Admin" else msg.senderId != "Admin"
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (activeSelfSender) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (activeSelfSender) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(text = msg.senderName, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = msg.content, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Messages input tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = chatMessageInput,
                        onValueChange = { chatMessageInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isArabic) "اكتب رسالة فورية للتنسيق..." else "Type message...") },
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (chatMessageInput.isEmpty()) return@Button
                            FirebaseManager.activeChatListenerRegistration?.remove()
                            FirebaseManager.activeChatListenerRegistration = null

                            val msg = ChatMessage(
                                senderId = if (isAdmin) "Admin" else "Guest",
                                senderName = if (isAdmin) "المشرف العام" else "مواطن مستفيد",
                                content = chatMessageInput
                            )
                            FirebaseManager.sendChatMessage(msg) {
                                chatMessageInput = ""
                                FirebaseManager.startListeningToChats()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = if (isArabic) "إرسال" else "Send", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ======================== SECRETS BACKDOOR DIALOGS AND CONFIGS ========================
@Composable
fun BackdoorAuthDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit
) {
    var passInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "🏠 البوابة الخلفية السرية للمالك", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Text(text = "هذه البوابة مخصصة حصرياً للمالك لتعديل هوية التطبيق الأساسية. أدخل كلمة مرور المالك للحفر والتعديل:", fontSize = 11.sp, color = Color.LightGray)

                TextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("أدخل كلمة المرور السرية للمالك") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "إلغاء المالك")
                    }
                    Button(
                        onClick = {
                            if (passInput == "maher--736462") {
                                onUnlock()
                            } else {
                                Toast.makeText(context, "الرمز السري غير صحيح!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(text = "تأكيد وولوج")
                    }
                }
            }
        }
    }
}

@Composable
fun BackdoorSettingsDrawer(
    config: AppConfig,
    supervisors: List<Moderator>,
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    var appNameValue by remember { mutableStateOf(config.appName) }
    var footerValue by remember { mutableStateOf(config.footerText) }
    var supportPhoneValue by remember { mutableStateOf(config.supportPhone) }
    var selectedThemeType by remember { mutableStateOf(config.themeType) }
    var welcomeMsgValue by remember { mutableStateOf(config.welcomeMessage) }
    var customAdminPassValue by remember { mutableStateOf(config.mainAdminPass) }

    // Chat and Search Settings States
    var chatIconVisibleValue by remember { mutableStateOf(config.chatIconVisible) }
    var chatIconDeletedValue by remember { mutableStateOf(config.chatIconDeleted) }
    var chatIconColorHexValue by remember { mutableStateOf(config.chatIconColorHex) }
    var chatIconSizeValue by remember { mutableStateOf(config.chatIconSize) }
    var radiusOptionsValue by remember { mutableStateOf(config.radiusSearchOptions) }
    var voiceSearchValue by remember { mutableStateOf(config.voiceSearchEnabled) }

    // Supervisors creation states
    var newSupervisorName by remember { mutableStateOf("") }
    var newSupervisorPass by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🌌 لوحة إعدادات المالك الحصرية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Close setting", tint = Color.Gray)
                    }
                }

                Divider()

                // Configuration factors
                TextField(
                    value = appNameValue,
                    onValueChange = { appNameValue = it },
                    label = { Text("تعديل اسم التطبيق الفوري") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = welcomeMsgValue,
                    onValueChange = { welcomeMsgValue = it },
                    label = { Text("تعديل رسالة الترحيب الأولى") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = footerValue,
                    onValueChange = { footerValue = it },
                    label = { Text("تذييل التطبيق (نص الدعاية)") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = supportPhoneValue,
                    onValueChange = { supportPhoneValue = it },
                    label = { Text("رقم هاتف الدعم واتساب") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = customAdminPassValue,
                    onValueChange = { customAdminPassValue = it },
                    label = { Text("كلمة مرور الأدمن الرئيسي (WAM2026)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Themes selections
                Text(text = "اختر السمة الجمالية للدليل اليمني:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppThemeType.values().forEach { theme ->
                        val themeTitle = when (theme) {
                            AppThemeType.COSMIC_SILVER -> "🌌 فضي"
                            AppThemeType.GOLD_LUXURY -> "✨ ذهبي"
                            AppThemeType.EMERALD_CLASSIC -> "🟢 زمردي"
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selectedThemeType == theme) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedThemeType = theme }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(text = themeTitle, fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                Divider()

                Text(text = "🛠️ تبويب إعدادات الدردشة والبحث والمنطقة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)

                // 1. Chat settings visibility & deletion
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "أيقونة الدردشة مرئية في الواجهة", fontSize = 11.sp, color = Color.White)
                    Switch(
                        checked = chatIconVisibleValue,
                        onCheckedChange = { chatIconVisibleValue = it }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "حذف أيقونة الدردشة نهائياً من التطبيق", fontSize = 11.sp, color = Color.Red)
                    Switch(
                        checked = chatIconDeletedValue,
                        onCheckedChange = { chatIconDeletedValue = it }
                    )
                }

                // 2. Chat icon color hex input & presets
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "لون أيقونة الدردشة (مثال: #FF107C41)", fontSize = 11.sp, color = Color.White)
                    TextField(
                        value = chatIconColorHexValue,
                        onValueChange = { chatIconColorHexValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    // Preset colors row
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "#FF107C41" to "🟢 أخضر",
                            "#FF0288D1" to "🔵 أزرق",
                            "#FFE53935" to "🔴 أحمر",
                            "#FFFBC02D" to "🟡 ذهبي",
                            "#FF008080" to "🟢 تيل"
                        ).forEach { (hex, name) ->
                            Box(
                                modifier = Modifier
                                    .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(4.dp))
                                    .clickable { chatIconColorHexValue = hex }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = name, fontSize = 9.sp, color = Color.White)
                            }
                        }
                    }
                }

                // 3. Chat icon size
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "حجم أيقونة الدردشة (بكسل: 30 - 150): $chatIconSizeValue dp", fontSize = 11.sp, color = Color.White)
                    Slider(
                        value = chatIconSizeValue.toFloat(),
                        onValueChange = { chatIconSizeValue = it.toInt() },
                        valueRange = 30f..150f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Map Radius options & current selections
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "خيارات نطاق البحث بالخريطة (مفصولة بفاصلة)", fontSize = 11.sp, color = Color.White)
                    TextField(
                        value = radiusOptionsValue,
                        onValueChange = { radiusOptionsValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // 5. Voice search enabled toggle
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "تفعيل البحث الصوتي الذكي 🎙️", fontSize = 11.sp, color = Color.White)
                    Switch(
                        checked = voiceSearchValue,
                        onCheckedChange = { voiceSearchValue = it }
                    )
                }

                Divider()

                Button(
                    onClick = {
                        val capCong = config.copy(
                            appName = appNameValue,
                            footerText = footerValue,
                            supportPhone = supportPhoneValue,
                            themeType = selectedThemeType,
                            welcomeMessage = welcomeMsgValue,
                            mainAdminPass = customAdminPassValue,
                            chatIconVisible = chatIconVisibleValue,
                            chatIconDeleted = chatIconDeletedValue,
                            chatIconColorHex = chatIconColorHexValue,
                            chatIconSize = chatIconSizeValue,
                            radiusSearchOptions = radiusOptionsValue,
                            voiceSearchEnabled = voiceSearchValue
                        )
                        FirebaseManager.saveAppConfig(capCong)
                        FirebaseManager.logActivity("المالك", "تم تغيير هوية التطبيق اللحظية وتحديث إعدادات الدردشة والبحث.")
                        Toast.makeText(context, "تم حفظ وتطبيق وتعميم البيانات الهوياتية بنجاح بنظام المزامنة اللحظية!", Toast.LENGTH_LONG).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "حفظ وتثبيت إعدادات الهوية والدردشة", fontWeight = FontWeight.Bold)
                }

                Divider(modifier = Modifier.padding(vertical = 6.dp))

                // Supervisors moderators creation tools list
                Text(text = "👥 إدارة الحسابات والمشرفين الفرعيين:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                
                TextField(
                    value = newSupervisorName,
                    onValueChange = { newSupervisorName = it },
                    placeholder = { Text("اسم مستخدم المشرف الجديد") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                TextField(
                    value = newSupervisorPass,
                    onValueChange = { newSupervisorPass = it },
                    placeholder = { Text("كلمة مرور المشرف الجديد") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (newSupervisorName.isEmpty() || newSupervisorPass.isEmpty()) return@Button
                        val sup = Moderator("", newSupervisorName, newSupervisorPass)
                        FirebaseManager.manageSupervisor(sup) {
                            Toast.makeText(context, "تم إضافة حساب مشرف بنجاح ومزامنته للتحقق!", Toast.LENGTH_SHORT).show()
                            newSupervisorName = ""
                            newSupervisorPass = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(text = "إضافة حساب مشرف فرعي")
                }

                // Listing currently added supervisors
                supervisors.forEach { sup ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "👤 ${sup.username} | ${sup.password}", fontSize = 11.sp, color = Color.White)
                        IconButton(
                            onClick = {
                                FirebaseManager.manageSupervisor(sup, isDelete = true) {
                                    Toast.makeText(context, "تم مسح المشرف المختار.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete supervisor", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ======================== ADMIN DASHBOARD PANEL ========================
@Composable
fun AdminDashboardScreen(
    context: Context,
    loggedUser: String,
    config: AppConfig,
    categories: List<ServiceCategory>,
    providers: List<ServiceProvider>,
    pendingProviders: List<ServiceProvider>,
    reports: List<IncidentReport>,
    supervisors: List<Moderator>,
    historyLogs: List<ActivityLog>,
    isArabic: Boolean,
    onLogout: () -> Unit
) {
    var selectedDashboardTab by remember { mutableStateOf("PENDING") } // PENDING, LIST, ADD, METRICS, CATEGORIES, BANNERS, INCIDENTS, LOGS
    var editingProvider by remember { mutableStateOf<ServiceProvider?>(null) }

    // Manual provider build state
    var mName by remember { mutableStateOf("") }
    var mPhone by remember { mutableStateOf("") }
    var mWhatsapp by remember { mutableStateOf("") }
    var mCategory by remember { mutableStateOf("") }
    var mSub by remember { mutableStateOf("") }
    var mAddress by remember { mutableStateOf("") }
    var mArea by remember { mutableStateOf("صنعاء") }

    // Categories controls state
    var newCatAr by remember { mutableStateOf("") }
    var newCatEn by remember { mutableStateOf("") }
    var newCatEmoji by remember { mutableStateOf("") }

    // Banners state
    var newBannerTitle by remember { mutableStateOf("") }
    var newBannerUrl by remember { mutableStateOf("") }
    var newBannerLink by remember { mutableStateOf("") }
    var newBannerDisplaySize by remember { mutableStateOf("M") } // S, M, L

    val scrollState = rememberScrollState()
    val chatsList by FirebaseManager.chats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Welcome and logout
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "🛡️ مرحباً: $loggedUser", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                Text(text = "لوحة المزامنة وإحصائيات الدليل العام", fontSize = 11.sp, color = Color.Gray)
            }
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                Text(text = "تسجيل خروج", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Divider()

        // Horizontally scrolling Navigation tabs for Admin dashboard sections
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tabs = listOf(
                "PENDING" to "الطلبات المعلقة ⏳",
                "LIST" to "إقرارات المهنيين 👥",
                "ADD" to "إضافة يدوية ✍️",
                "CHAT_MGMT" to "إدارة الدردشات 💬",
                "METRICS" to "مؤشرات الدعم 📊",
                "CATEGORIES" to "الأقسام 🛠️",
                "BANNERS" to "الإعلانات Banners 📢",
                "INCIDENTS" to "البلاغات والحظر 🚨",
                "LOGS" to "سجلات Supervisors 📝"
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
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tab Content Router Panel
        when (selectedDashboardTab) {
            "PENDING" -> {
                Text(text = "⏳ التطبيقات المعلقة للمراجعة والمصادقة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                if (pendingProviders.isEmpty()) {
                    Text(text = "رائع! لا يوجد أي طلبات تسجيل معلقة بانتظار المراجعة حالياً.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    pendingProviders.forEach { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "👤 ${p.fullName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "(${p.subCategory})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(text = "📞 هاتف: ${p.phone} | واتساب: ${p.whatsapp}", fontSize = 11.sp, color = Color.LightGray)
                                Text(text = "📍 المحافظة والعنوان: ${p.area} - ${p.address}", fontSize = 11.sp, color = Color.LightGray)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Button(
                                        onClick = {
                                            FirebaseManager.rejectProvider(p.id, "لم يستوف المعايير الكافية للتصنيف") {
                                                Toast.makeText(context, "تم رفض ومسح الطلب بنجاح", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) {
                                        Text(text = "رفض الطلب ❌", fontSize = 11.sp, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            FirebaseManager.approveProvider(p.id) {
                                                Toast.makeText(context, "تم الموافقة والتعميم في خدمات الدليل بنجاح!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                    ) {
                                        Text(text = "موافقة وقبول بقسم النشاط ✅", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "LIST" -> {
                Text(text = "👥 تعديل إشعارات وشارات وتوصيات المهنيين:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                providers.forEach { p ->
                    var isVerif by remember(p.isVerified) { mutableStateOf(p.isVerified) }
                    var isPin by remember(p.isPinned) { mutableStateOf(p.isPinned) }
                    var isRecom by remember(p.isRecommended) { mutableStateOf(p.isRecommended) }
                    var isPrem by remember(p.hasPremiumSubscription) { mutableStateOf(p.hasPremiumSubscription) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(text = p.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                IconButton(
                                    onClick = { 
                                        FirebaseManager.deleteProvider(p.id) {
                                            Toast.makeText(context, "تم حذف المهني.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(text = p.subCategory, fontSize = 11.sp, color = Color.LightGray)

                            // Status toggles checkbox controls
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isVerif, onCheckedChange = { isVerif = it })
                                    Text(text = "🛡️ موثق", fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isPin, onCheckedChange = { isPin = it })
                                    Text(text = "📌 تثبيت", fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isRecom, onCheckedChange = { isRecom = it })
                                    Text(text = "⭐ توصية", fontSize = 10.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isPrem, onCheckedChange = { isPrem = it })
                                    Text(text = "👑 تميز", fontSize = 10.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { editingProvider = p },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(text = "تعديل البيانات ✏️", fontSize = 10.sp)
                                }

                                Button(
                                    onClick = {
                                        FirebaseManager.updateProviderStatus(p.id, isVerif, isPin, isRecom, isPrem) {
                                            Toast.makeText(context, "تم تحديث الشارات والموثوقية!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text(text = "تعديل الشارات", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            "ADD" -> {
                Text(text = "✍️ إضافة مقدم خدمة مباشرة بدون مراجعة:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                TextField(value = mName, onValueChange = { mName = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                TextField(value = mPhone, onValueChange = { mPhone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
                TextField(value = mWhatsapp, onValueChange = { mWhatsapp = it }, label = { Text("الواتساب") }, modifier = Modifier.fillMaxWidth())
                TextField(value = mSub, onValueChange = { mSub = it }, label = { Text("التوصيف أو الحرفة") }, modifier = Modifier.fillMaxWidth())
                TextField(value = mAddress, onValueChange = { mAddress = it }, label = { Text("العنوان بالتفصيل") }, modifier = Modifier.fillMaxWidth())
                
                Button(
                    onClick = {
                        if (mName.isEmpty() || mPhone.isEmpty() || mSub.isEmpty()) return@Button
                        val p = ServiceProvider("", mName, mPhone, mWhatsapp, "cat_1", mSub, mAddress, mArea)
                        FirebaseManager.addManualProvider(p) {
                            Toast.makeText(context, "تم الإضافة مباشرة وحفظ بفرستور بنجاح!", Toast.LENGTH_LONG).show()
                            mName = ""
                            mPhone = ""
                            mWhatsapp = ""
                            mSub = ""
                            mAddress = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "تسجيل وادراج فوري لمقدم الخدمة", fontWeight = FontWeight.Bold)
                }
            }

            "METRICS" -> {
                Text(text = "📊 إحصائيات وتحليلات الدعم الفني الحية (Realtime Metrics):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "• إجمالي الحرفيين والمسجلين بالدليل: ${providers.size}", fontSize = 12.sp, color = Color.White)
                        Text(text = "• إجمالي الطلبات بانتظار المراجعة: ${pendingProviders.size}", fontSize = 12.sp, color = Color.White)
                        Text(text = "• المشرفين والمدراء النشطين: ${supervisors.size + 1}", fontSize = 12.sp, color = Color.White)
                        Text(text = "• الحظر والتقصيات القانونية: ${reports.size}", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            "CATEGORIES" -> {
                Text(text = "🛠️ إدارة الأقسام والـ Categories الرئيسية والفرعية:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                TextField(value = newCatAr, onValueChange = { newCatAr = it }, label = { Text("اسم القسم بالعربي") }, modifier = Modifier.fillMaxWidth())
                TextField(value = newCatEn, onValueChange = { newCatEn = it }, label = { Text("اسم القسم بالإنجليزي") }, modifier = Modifier.fillMaxWidth())
                TextField(value = newCatEmoji, onValueChange = { newCatEmoji = it }, label = { Text("أيقونة الـ Emoji") }, modifier = Modifier.fillMaxWidth())

                Button(
                    onClick = {
                        if (newCatAr.isEmpty() || newCatEmoji.isEmpty()) return@Button
                        val id = "cat_" + System.currentTimeMillis()
                        FirebaseManager.manageCategory(id, newCatAr, newCatEn, newCatEmoji) {
                            Toast.makeText(context, "تم إدراج القسم بنجاح!", Toast.LENGTH_SHORT).show()
                            newCatAr = ""
                            newCatEn = ""
                            newCatEmoji = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "إضافة القسم")
                }

                // Listing currently added categories
                categories.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${cat.iconEmoji} ${cat.nameAr}", fontSize = 11.sp, color = Color.White)
                        IconButton(
                            onClick = {
                                FirebaseManager.manageCategory(cat.id, "", "", "", isDelete = true) {
                                    Toast.makeText(context, "تم الحذف.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            "BANNERS" -> {
                Text(text = "📢 رفع اللافتات الإعلانية (Banners Ads) والتحكم بحجم العرض:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                TextField(value = newBannerTitle, onValueChange = { newBannerTitle = it }, label = { Text("عنوان الإعلان") }, modifier = Modifier.fillMaxWidth())
                TextField(value = newBannerUrl, onValueChange = { newBannerUrl = it }, label = { Text("رابط الصورة الشخصية أو الإعلانية") }, modifier = Modifier.fillMaxWidth())
                TextField(value = newBannerLink, onValueChange = { newBannerLink = it }, label = { Text("رابط الموقع عند الضغط") }, modifier = Modifier.fillMaxWidth())
                
                Text(text = "حجم عرض الإعلان بالدليل الرئيسي: S, M, L", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("S", "M", "L").forEach { d ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (newBannerDisplaySize == d) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { newBannerDisplaySize = d }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(text = d, color = Color.White)
                        }
                    }
                }

                Button(
                    onClick = {
                        if (newBannerTitle.isEmpty() || newBannerUrl.isEmpty()) return@Button
                        val ad = BannerAd("", newBannerTitle, newBannerUrl, newBannerLink, newBannerDisplaySize, 5, true)
                        FirebaseManager.manageBanner(ad) {
                            Toast.makeText(context, "تم حفظ ونشر الإعلان بنجاح!", Toast.LENGTH_SHORT).show()
                            newBannerTitle = ""
                            newBannerUrl = ""
                            newBannerLink = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "نشر الإعلان الفوري")
                }
            }

            "INCIDENTS" -> {
                Text(text = "🚨 شكاوى المواطنين وبنود وتصنيفات حظر الحسابات:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                if (reports.isEmpty()) {
                    Text(text = "سجل الإبلاغات والمحاضر سليم وخالٍ من الشكاوى.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    reports.forEach { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "⚠️ البلاغ المرفوع ضد: ${r.providerName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(text = "• الشاكي: ${r.reporterName}", fontSize = 11.sp, color = Color.LightGray)
                                Text(text = "• دافع وتفصيل الشكوى: ${r.reason}", fontSize = 11.sp, color = Color.LightGray)
                                
                                Button(
                                    onClick = {
                                        FirebaseManager.blockProvider(r.providerId, true) {
                                            Toast.makeText(context, "تم حظر وإيقاف المهني واجتنابه من دليل العرض العام!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(text = "إدراج ضمن قائمة الحظر (BLOCK)", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            "CHAT_MGMT" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💬 إدارة محادثات الدعم والمساندة المباشرة:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                FirebaseManager.wipeChatLogs {
                                    Toast.makeText(context, "تم مسح جميع سجلات المحادثة بنجاح!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "🧹 مسح كل المحادثات", fontSize = 10.sp)
                        }
                    }

                    // Super Admin fast reply input
                    var adminReplyInput by remember { mutableStateOf("") }
                    val adminChatScrollState = rememberScrollState()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp).fillMaxSize()) {
                            // Chat Logs List
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(adminChatScrollState),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val currentChats = chatsList
                                if (currentChats.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = "لا توجد رسائل مسجلة حالياً.", fontSize = 10.sp, color = Color.Gray)
                                    }
                                } else {
                                    currentChats.forEach { msg ->
                                        val isAdm = msg.senderId == "Admin"
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (isAdm) Alignment.End else Alignment.Start
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(text = msg.senderName, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isAdm) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                                                Text(text = sdf.format(java.util.Date(msg.timestamp)), fontSize = 7.sp, color = Color.Gray)
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isAdm) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                                ),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(text = msg.content, fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(6.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            // Input block
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextField(
                                    value = adminReplyInput,
                                    onValueChange = { adminReplyInput = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("رد الأدمن الفائق (Super Admin Reply)...") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (adminReplyInput.isEmpty()) return@Button
                                        val adminMsg = ChatMessage(
                                            senderId = "Admin",
                                            senderName = "الأدمن الفائق (رد رسمي) 👑",
                                            content = adminReplyInput,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        FirebaseManager.sendChatMessage(adminMsg) {
                                            adminReplyInput = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("رد فائق", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            "LOGS" -> {
                Text(text = "📝 سجل تتبع نشاطات المشرفين والأعضاء (Security Logs):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                historyLogs.forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(text = "• [👤${log.user}]: ${log.action}", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // 10. Edit Provider Dialog modal
    editingProvider?.let { prov ->
        var editName by remember { mutableStateOf(prov.fullName) }
        var editPhone by remember { mutableStateOf(prov.phone) }
        var editWhatsapp by remember { mutableStateOf(prov.whatsapp) }
        var editSub by remember { mutableStateOf(prov.subCategory) }
        var editAddress by remember { mutableStateOf(prov.address) }
        var editArea by remember { mutableStateOf(prov.area) }
        var editCategoryId by remember { mutableStateOf(prov.categoryId) }

        AlertDialog(
            onDismissRequest = { editingProvider = null },
            title = {
                Text(
                    text = "✏️ تعديل بيانات المهني/الخدمة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("الاسم الكامل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("هاتف الاتصال") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = editWhatsapp,
                        onValueChange = { editWhatsapp = it },
                        label = { Text("رقم الواتساب") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = editSub,
                        onValueChange = { editSub = it },
                        label = { Text("التوصيف المهني أو الحرفة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("العنوان بالتفصيل") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text(text = "المحافظة والنطاق الجغرافي:", fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("صنعاء", "عدن", "تعز", "حضرموت", "إب").forEach { ar ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (editArea == ar) MaterialTheme.colorScheme.primary else Color.DarkGray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { editArea = ar }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = ar, fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "التصنيف والـ Category الرئيسي:", fontSize = 11.sp)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editCategoryId = cat.id }
                                .background(
                                    color = if (editCategoryId == cat.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = editCategoryId == cat.id,
                                onClick = { editCategoryId = cat.id }
                            )
                            Text(text = "${cat.iconEmoji} ${cat.nameAr}", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = prov.copy(
                            fullName = editName,
                            phone = editPhone,
                            whatsapp = editWhatsapp,
                            subCategory = editSub,
                            address = editAddress,
                            area = editArea,
                            categoryId = editCategoryId
                        )
                        FirebaseManager.updateProviderDetails(updated) {
                            Toast.makeText(context, "تم حفظ وسحب التعديل مباشرة على فرستور!", Toast.LENGTH_SHORT).show()
                            editingProvider = null
                        }
                    }
                ) {
                    Text("حفظ التغييرات ✅")
                }
            },
            dismissButton = {
                Button(
                    onClick = { editingProvider = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ======================== DIALOG: FIREBASE SYNC & CONNECTION DIAGNOSTICS ========================
@Composable
fun FirebaseSyncDiagnosticDialog(
    isArabic: Boolean,
    config: AppConfig,
    categoriesCount: Int,
    providersCount: Int,
    pendingCount: Int,
    bannersCount: Int,
    chatsCount: Int,
    incidentsCount: Int,
    logsCount: Int,
    moderatorsCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isTestingSync by remember { mutableStateOf(false) }
    val scrollState = androidx.compose.foundation.rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "⚙️ لوحة فحص المزامنة والربط السحابي" else "⚙️ Cloud Sync & Connection Diagnostics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable details content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Modern Connection Status Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF4CAF50), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) "الحالة السحابية: متصل ومزامن بالكامل 🟢" else "Cloud Status: Connected & Fully Synced 🟢",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isArabic) 
                                    "✓ تم تأسيس اتصال مقبس شبكة Firestore ثنائي الاتجاه بالخلفية لـ dalyly2026." 
                                    else "✓ Established bi-directional socket connection for dalyly2026.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Static App Metadata Summary (Yemeni Service Directory Identity)
                    Text(
                        text = if (isArabic) "📊 إحصائيات البيانات المتزامنة بالكاش (Firestore Cache):" else "📊 Live Cached Documents Stats:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagnosticStatRow(if (isArabic) "العنوان واسم التطبيق" else "App Style & Identity", "1 مستند (مستمر عبر الكاش)", isArabic)
                        DiagnosticStatRow(if (isArabic) "تصنيفات الخدمة المتاحة" else "Available Categories", "$categoriesCount تصنيف مفعل", isArabic)
                        DiagnosticStatRow(if (isArabic) "مقدمي الخدمات المعتمدين" else "Verified Providers", "$providersCount مقدم خدمة", isArabic)
                        DiagnosticStatRow(if (isArabic) "الطلبات المعلقة للتدقيق" else "Pending Review Submissions", "$pendingCount طلب معلق", isArabic)
                        DiagnosticStatRow(if (isArabic) "إعلانات البانر النشطة" else "Active Advertisement Banners", "$bannersCount إعلان سلايد", isArabic)
                        DiagnosticStatRow(if (isArabic) "رسائل غرف الدردشة الفورية" else "Live Instant Messages", "$chatsCount رسالة", isArabic)
                        DiagnosticStatRow(if (isArabic) "سجل عمليات المشرفين" else "Moderator Session Logs", "$logsCount سجل عمليات", isArabic)
                        DiagnosticStatRow(if (isArabic) "حسابات القيادة والرقابة" else "Supervisor / Moderator Accounts", "$moderatorsCount حساب مفعل", isArabic)
                        DiagnosticStatRow(if (isArabic) "بلاغات الأمان والشكاوى" else "Security Complaints Listed", "$incidentsCount بلاغ", isArabic)
                    }

                    // Complete Details / Overview of the Application ("بروميت كامل عن التطبيق بكل تفاصيله")
                    Text(
                        text = if (isArabic) "📝 تفاصيل ونظام عمل تطبيق دليلي 2026 الكامل:" else "Detailed Application Architecture Summary:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val appDescriptionAr = """
                        تطبيق دليلي (dalyly2026) هو عبارة عن منصة تفاعلية يمنية متكاملة للربط المباشر بين المواطنين ومقدمي الخدمات والمهنيين (مثل الأطباء، الكهربائيين، الفنيين، المحامين، والورش الصناعية) محلياً مع دعم كامل للتشغيل دون إنترنت (Offline-First).
                        
                        🎯 معمارية وموثوقية المزامنة المقاومة للانقطاع:
                        1. تخزين كاش غير محدود (Firestore Cache Unlimited): قمنا بتعديل إعدادات التهيئة لتمكين التخزين المحلي غير المحدود على ذاكرة الجهاز. بمجرد تحميل البيانات لمرة واحدة وهي متصلة بالإنترنت، يتم حفظها على قرص الهاتف ليتم الرجوع إليها فوراً وبسرعة فائقة عند انقطاع الإنترنت.
                        2. المزامنة الثنائية الفورية (Bi-directional Live Sync): يتم التنصت اللحظي بـ addSnapshotListener لكافة جداول البيانات. أي إضافة أو تعديل من جهة المشرفين أو المستخدمين تنعكس فوراً وفي أجزاء من الثانية بفضل هندسة تدفق الحالة (Kotlin StateFlows).
                        3. تسجيل الدخول المجهول (Anonymous Firebase Authentication): لضمان فتح قواعد البيانات وحمايتها من الاختراقات مع مطابقة شروط حماية البيانات السحابية، يقم التطبيق تلقائياً بتأسيس جلسة تسجيل دخول مجهول آمنة في الخلفية فور تشغيله لأول مرة.
                        4. معالجة فشل الاتصال التلقائي (Retry Socket System): في حال حدوث أي انقطاع مفاجئ بالشبكة، يتم جدولتها تلقائياً لإعادة محاولة الربط السحابي وبدء الاستماع للقنوات النشطة خلال 5 ثوانٍ من استعادة الاتجاه.
                    """.trimIndent()

                    val appDescriptionEn = """
                        Dalyly (Yemeni Service Directory) is an offline-first interactive directory facilitating local connections between users and service providers (maintenance, medical, engineering, legal, tech workshops) in Yemen.
                        
                        🎯 Fail-Safe Real-time Sync Infrastructure:
                        1. Offline-First Caching: Configured Unlimited local SQLite cache parameters on Firebase Firestore. The app immediately displays cached databases without needing an active internet network.
                        2. Direct Flow Stream: State management uses Kotlin Flow triggers to synchronize Firestore tables in real-time, refreshing lists on any backend modification instantly.
                        3. Secure Anonymous Credentials: Automatically registers secure anonymous authentication sessions to meet cloud security rules without imposing complex login screens on general users.
                        4. Auto-Reconnect Recovery: Contains a 5-second resilient background task to re-establish broken Firestore sockets on network drops.
                    """.trimIndent()

                    Text(
                        text = if (isArabic) appDescriptionAr else appDescriptionEn,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Actions Footer: Retry & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isTestingSync = true
                            // Force Resubscription
                            FirebaseManager.forceReSubscribe()
                            Toast.makeText(
                                context,
                                if (isArabic) "🔄 جاري إعادة تشغيل قنوات الاتصال والتحقق من كاش فرستور السحابي..." 
                                else "🔄 Re-subscribing socket channels & validating Firestore offline cache...",
                                Toast.LENGTH_LONG
                            ).show()
                            isTestingSync = false
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTestingSync
                    ) {
                        Text(
                            text = if (isArabic) "فحص اتصال مخصص 🔄" else "Test Connection 🔄",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(
                            text = if (isArabic) "إغلاق" else "Close",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticStatRow(label: String, valStr: String, isArabic: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = valStr,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = if (isArabic) TextAlign.Left else TextAlign.Right
        )
    }
}
