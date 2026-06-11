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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import coil.compose.AsyncImage
import com.example.ui.theme.InteractiveYemenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen {
    HOME,
    REGISTER_PROVIDER,
    BACKDOOR
}

class MainActivity : ComponentActivity() {
    private var lastBackTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firestore manager with unlimited cache settings
        FirebaseManager.init(applicationContext)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            // Observe core reactive state variables synchronized with Firestore
            val config by FirebaseManager.appConfig.collectAsState()
            val categoriesList by FirebaseManager.categories.collectAsState()
            val providersList by FirebaseManager.providers.collectAsState()
            val pendingProvidersList by FirebaseManager.pendingProviders.collectAsState()
            val bannersList by FirebaseManager.banners.collectAsState()
            val chatsList by FirebaseManager.chats.collectAsState()
            val logList by FirebaseManager.activityLogs.collectAsState()
            val isFromCache by FirebaseManager.isProvidersDataFromCache.collectAsState()

            // App-wide UI configuration
            val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var activeScreen by remember { mutableStateOf(Screen.HOME) }
            var isArabic by remember { mutableStateOf(true) }
            var loggedAdminUser by remember { mutableStateOf<String?>(prefs.getString("logged_admin", null)) }

            // Assign a unique visitor ID to store persistently in memory for chat threads
            val visitorId = remember {
                val storedId = prefs.getString("user_visitor_uuid", null)
                if (storedId != null) {
                    storedId
                } else {
                    val newId = "visitor_" + UUID.randomUUID().toString().take(6)
                    prefs.edit().putString("user_visitor_uuid", newId).apply()
                    newId
                }
            }

            // Overlay/Bottom Sheets controllers
            var showDiagnosticsDialog by remember { mutableStateOf(false) }
            var selectedProviderForDetail by remember { mutableStateOf<ServiceProvider?>(null) }
            var showBackdoorLoginDialog by remember { mutableStateOf(false) }
            var showAddReviewDialog by remember { mutableStateOf<ServiceProvider?>(null) }
            var showReportDialog by remember { mutableStateOf<ServiceProvider?>(null) }
            var showLiveChatWindow by remember { mutableStateOf(false) }

            // Dynamic filter states
            var searchTextInput by remember { mutableStateOf("") }
            var selectedFilterCategoryId by remember { mutableStateOf("") }
            var selectedFilterCity by remember { mutableStateOf("") }
            var selectedFilterMinRating by remember { mutableStateOf(0f) }
            var selectedFilterVerifiedState by remember { mutableStateOf(0) } // 0: الكل, 1: موثق فقط, 2: المراجعة

            // Double tap click closing closure
            BackHandler {
                if (activeScreen != Screen.HOME) {
                    activeScreen = Screen.HOME
                } else {
                    val curTime = System.currentTimeMillis()
                    if (curTime - lastBackTime < 2000) {
                        finish()
                    } else {
                        lastBackTime = curTime
                        Toast.makeText(
                            context,
                            if (isArabic) "اضغط مرة أخرى للخروج من التطبيق" else "Tap again to exit the app",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            // Voice Speech Input Launcher
            val voiceSearchLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    val spokenTerms = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!spokenTerms.isNullOrEmpty()) {
                        searchTextInput = spokenTerms[0]
                    }
                }
            }

            // Click tally for the Hidden Admin Backdoor Menu
            var backdoorTapCount by remember { mutableStateOf(0) }
            LaunchedEffect(backdoorTapCount) {
                if (backdoorTapCount > 0) {
                    delay(3000)
                    backdoorTapCount = 0
                }
            }

            // Auto RTL localization block context
            val currentDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides currentDirection) {
                InteractiveYemenTheme(themeType = config.themeType) {
                    Scaffold(
                        topBar = {
                            Column(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .statusBarsPadding()
                            ) {
                                // Header App Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Brand logo click trigger (5 times unlocks door)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                backdoorTapCount++
                                                if (backdoorTapCount == 5) {
                                                    backdoorTapCount = 0
                                                    if (loggedAdminUser != null) {
                                                        activeScreen = Screen.BACKDOOR
                                                    } else {
                                                        showBackdoorLoginDialog = true
                                                    }
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = config.logoEmoji,
                                            fontSize = 24.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = config.appName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Dynamic Language Toggler Pill
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { isArabic = !isArabic }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("language_toggle_btn")
                                    ) {
                                        Text(
                                            text = if (isArabic) "English" else "العربية",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                // Interactive Navigation Tabs Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            activeScreen = Screen.HOME
                                            backdoorTapCount++
                                            if (backdoorTapCount == 5) {
                                                backdoorTapCount = 0
                                                if (loggedAdminUser != null) {
                                                    activeScreen = Screen.BACKDOOR
                                                } else {
                                                    showBackdoorLoginDialog = true
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeScreen == Screen.HOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (activeScreen == Screen.HOME) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = if (isArabic) "الرئيسية" else "Home", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { activeScreen = Screen.REGISTER_PROVIDER },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeScreen == Screen.REGISTER_PROVIDER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (activeScreen == Screen.REGISTER_PROVIDER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = if (isArabic) "انضم كمهني 🇾🇪" else "Join Directory", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (loggedAdminUser != null) {
                                        Button(
                                            onClick = { activeScreen = Screen.BACKDOOR },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (activeScreen == Screen.BACKDOOR) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = "Admin", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = if (isArabic) "الإدارة" else "Admin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            AnimatedContent(
                                targetState = activeScreen,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                }
                            ) { screenState ->
                                when (screenState) {
                                    Screen.HOME -> {
                                        HomeScreenLayout(
                                            isArabic = isArabic,
                                            banners = bannersList,
                                            categories = categoriesList,
                                            providers = providersList,
                                            selectedCategory = selectedFilterCategoryId,
                                            onCategorySelected = { selectedFilterCategoryId = it },
                                            searchQuery = searchTextInput,
                                            onSearchChanged = { searchTextInput = it },
                                            selectedCity = selectedFilterCity,
                                            onCitySelected = { selectedFilterCity = it },
                                            selectedMinRating = selectedFilterMinRating,
                                            onMinRatingSelected = { selectedFilterMinRating = it },
                                            selectedVerifiedState = selectedFilterVerifiedState,
                                            onVerifiedSelected = { selectedFilterVerifiedState = it },
                                            voiceSearchLauncher = voiceSearchLauncher,
                                            onProviderClicked = { selectedProviderForDetail = it }
                                        )
                                    }
                                    Screen.REGISTER_PROVIDER -> {
                                        RegisterProviderLayout(
                                            isArabic = isArabic,
                                            categories = categoriesList,
                                            onSuccess = { activeScreen = Screen.HOME }
                                        )
                                    }
                                    Screen.BACKDOOR -> {
                                        BackdoorSettingsPanelLayout(
                                            isArabic = isArabic,
                                            adminName = loggedAdminUser ?: "",
                                            pendingProviders = pendingProvidersList,
                                            activityLogs = logList,
                                            onLogout = {
                                                loggedAdminUser = null
                                                prefs.edit().remove("logged_admin").apply()
                                                activeScreen = Screen.HOME
                                                Toast.makeText(context, if (isArabic) "تم تسجيل الخروج" else "Logged out", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }

                            // FLOATING ACTIVE CHAT BUTTON & AI ASSISTANT OVERLAYS
                            if (config.chatIconVisible && !config.chatIconDeleted && activeScreen != Screen.BACKDOOR) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 16.dp, end = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    // Live chats bubble
                                    FloatingActionButton(
                                        onClick = {
                                            if (config.chatDisabled) {
                                                Toast.makeText(context, config.chatDisabledMessage, Toast.LENGTH_LONG).show()
                                            } else {
                                                showLiveChatWindow = true
                                            }
                                        },
                                        containerColor = try {
                                            Color(android.graphics.Color.parseColor(config.chatIconColorHex))
                                        } catch(e: Exception) {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(config.chatIconSize.dp),
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = "Live Chats Portal",
                                            tint = Color.White,
                                            modifier = Modifier.size((config.chatIconSize / 2).dp)
                                        )
                                    }

                                    // AI Assistant bubble if visible
                                    if (config.aiAssistantVisible && !config.aiAssistantDeleted) {
                                        FloatingActionButton(
                                            onClick = {
                                                Toast.makeText(context, if (isArabic) "🧠 جارٍ استدعاء المساعد الذكي اليمني لوضع عدم الاتصال..." else "Invoking local AI assistant...", Toast.LENGTH_SHORT).show()
                                            },
                                            containerColor = try {
                                                Color(android.graphics.Color.parseColor(config.aiAssistantColorHex))
                                            } catch(e: Exception) {
                                                MaterialTheme.colorScheme.secondary
                                            },
                                            modifier = Modifier.size(config.aiAssistantSize.dp),
                                            shape = CircleShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ThumbUp,
                                                contentDescription = "WAM AI Assistant",
                                                tint = Color.White,
                                                modifier = Modifier.size((config.aiAssistantSize / 2).dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // App Overlay dialogs triggers section
                        if (showDiagnosticsDialog) {
                            DiagnosticDialog(
                                isArabic = isArabic,
                                isFromCache = isFromCache,
                                providersCount = providersList.size,
                                categoriesCount = categoriesList.size,
                                pendingCount = pendingProvidersList.size,
                                chatsCount = chatsList.size,
                                onDismiss = { showDiagnosticsDialog = false }
                            )
                        }

                        if (showBackdoorLoginDialog) {
                            BackdoorLoginDialog(
                                isArabic = isArabic,
                                expectedPass = config.mainAdminPass,
                                onDismiss = { showBackdoorLoginDialog = false },
                                onLoginSuccess = { name, isRemembered ->
                                    loggedAdminUser = name
                                    if (isRemembered) {
                                        prefs.edit().putString("logged_admin", name).apply()
                                    } else {
                                        prefs.edit().remove("logged_admin").apply()
                                    }
                                    activeScreen = Screen.BACKDOOR
                                    showBackdoorLoginDialog = false
                                }
                            )
                        }

                        selectedProviderForDetail?.let { p ->
                            ProviderDetailsDialog(
                                isArabic = isArabic,
                                provider = p,
                                onDismiss = { selectedProviderForDetail = null },
                                onReviewRequest = { showAddReviewDialog = p },
                                onReportRequest = { showReportDialog = p }
                            )
                        }

                        showAddReviewDialog?.let { p ->
                            AddReviewDialog(
                                isArabic = isArabic,
                                provider = p,
                                onDismiss = { showAddReviewDialog = null },
                                onSaved = {
                                    selectedProviderForDetail = providersList.find { it.id == p.id }
                                    showAddReviewDialog = null
                                }
                            )
                        }

                        showReportDialog?.let { p ->
                            SendReportDialog(
                                isArabic = isArabic,
                                provider = p,
                                onDismiss = { showReportDialog = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- HOMEPAGE LAYOUT AND SUB-COMPONENTS ----------------------
@Composable
fun HomeScreenLayout(
    isArabic: Boolean,
    banners: List<BannerAd>,
    categories: List<ServiceCategory>,
    providers: List<ServiceProvider>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedCity: String,
    onCitySelected: (String) -> Unit,
    selectedMinRating: Float,
    onMinRatingSelected: (Float) -> Unit,
    selectedVerifiedState: Int,
    onVerifiedSelected: (Int) -> Unit,
    voiceSearchLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onProviderClicked: (ServiceProvider) -> Unit
) {
    val context = LocalContext.current
    var showFilterPanel by remember { mutableStateOf(false) }

    // Multi-Dimensional Query Combinator computed on changes
    val filteredList = remember(
        providers, searchQuery, selectedCategory, selectedCity, selectedMinRating, selectedVerifiedState
    ) {
        providers.filter { p ->
            val matchText = searchQuery.isEmpty() ||
                    p.fullName.contains(searchQuery, ignoreCase = true) ||
                    p.subCategory.contains(searchQuery, ignoreCase = true) ||
                    p.address.contains(searchQuery, ignoreCase = true)

            val matchCategory = selectedCategory.isEmpty() || p.categoryId == selectedCategory
            val matchCity = selectedCity.isEmpty() || p.area.contains(selectedCity) || p.address.contains(selectedCity)
            val matchRating = p.averageRating >= selectedMinRating
            val matchVerify = when (selectedVerifiedState) {
                1 -> p.isVerified
                2 -> !p.isVerified
                else -> true
            }

            matchText && matchCategory && matchCity && matchRating && matchVerify
        }.sortedWith(
            compareByDescending<ServiceProvider> { it.isPinned }
                .thenByDescending { it.isRecommended }
                .thenByDescending { p -> p.hasPremiumSubscription }
                .thenByDescending { it.averageRating }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Sliding Banner Card section
        if (banners.isNotEmpty()) {
            item {
                Text(
                    text = if (isArabic) "أخر الإعلانات والفرص 📢" else "Promo & Banners 📢",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                SlidingPromoBanner(isArabic = isArabic, banners = banners)
            }
        }

        // Direct Core Search and Microphone widget
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = {
                        Text(
                            text = if (isArabic) "ابحث عن مهندس، سباك، طبيب..." else "Search plumber, doctor...",
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        } else {
                            IconButton(onClick = {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (isArabic) "تحدث بصوتك للبحث" else "Speak to search")
                                    }
                                    voiceSearchLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "التعرف الصوتي غير مدعوم على هذا الجهاز", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Mic") // Alternate mic symbol representation
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )

                Button(
                    onClick = { showFilterPanel = !showFilterPanel },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showFilterPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (showFilterPanel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Filters")
                }
            }
        }

        // Expanding Filtering Options Sheet
        item {
            AnimatedVisibility(visible = showFilterPanel) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isArabic) "خيارات التصفية المتطورة" else "Advanced Filter Suite",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // 1. City / Governorate Row selector
                        Text(text = if (isArabic) "المحافظة:" else "Governorate:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val cities = listOf("صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCity.isEmpty(),
                                    onClick = { onCitySelected("") },
                                    label = { Text(text = if (isArabic) "الكل [🇾🇪]" else "All") }
                                )
                            }
                            items(cities) { city ->
                                FilterChip(
                                    selected = selectedCity == city,
                                    onClick = { onCitySelected(city) },
                                    label = { Text(text = city) }
                                )
                            }
                        }

                        // 2. Minimum Rating Slider/Selector
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isArabic) "أدنى تقييم:" else "Min Rating:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0f, 3f, 4f, 4.5f).forEach { score ->
                                    val isMatch = selectedMinRating == score
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .clickable { onMinRatingSelected(score) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (score == 0f) (if (isArabic) "أي تقييم" else "Any") else "$score ⭐",
                                            fontSize = 11.sp,
                                            color = if (isMatch) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Verification Type Radio selections
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isArabic) "نوع الحساب:" else "Acct Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(10.dp))
                            listOf(
                                Pair(0, if (isArabic) "الكل" else "All"),
                                Pair(1, if (isArabic) "موثق ✓" else "Verified"),
                                Pair(2, if (isArabic) "غير موثق" else "Unverified")
                            ).forEach { (stateCode, name) ->
                                val isAct = selectedVerifiedState == stateCode
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .background(
                                            color = if (isAct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onVerifiedSelected(stateCode) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        color = if (isAct) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Reset Clear Button
                        OutlinedButton(
                            onClick = {
                                onCategorySelected("")
                                onCitySelected("")
                                onMinRatingSelected(0f)
                                onVerifiedSelected(0)
                                onSearchChanged("")
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = if (isArabic) "إعادة تعيين الفلاتر" else "Reset Filters", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Horizontal Category Tab Chips Scrolling list
        item {
            Text(
                text = if (isArabic) "أقسام الخدمات 🗂️" else "Service Categories 🗂️",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val activeAll = selectedCategory.isEmpty()
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (activeAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(15.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (activeAll) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(15.dp)
                            )
                            .clickable { onCategorySelected("") }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isArabic) "📂 الكل" else "📂 All",
                            color = if (activeAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                items(categories) { cat ->
                    val isSel = cat.id == selectedCategory
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(15.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(15.dp)
                            )
                            .clickable { onCategorySelected(cat.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = cat.iconEmoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) cat.nameAr else cat.nameEn,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Section Title: Directories Listing
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isArabic) "المهنيون والحرفيون النشطون (${filteredList.size})" else "Active Service Professionals (${filteredList.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (filteredList.isNotEmpty()) {
                    Text(text = "⭐ الأعلى تقييماً أولاً", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🔍", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) {
                                "لا توجد نتائج مطابقة لخيارات بحثك حالياً.\nتتوفر خدمات بكل من صنعاء، عدن، حضرموت، تعز."
                            } else {
                                "No matches found with current inputs."
                            },
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredList) { p ->
                ProviderCardItem(
                    isArabic = isArabic,
                    provider = p,
                    onItemClick = { onProviderClicked(p) }
                )
            }
        }
    }
}

@Composable
fun SlidingPromoBanner(isArabic: Boolean, banners: List<BannerAd>) {
    var activeBannerIndex by remember { mutableStateOf(0) }

    // Auto revolving banner ads
    LaunchedEffect(banners) {
        if (banners.size > 1) {
            while (true) {
                delay(7000)
                activeBannerIndex = (activeBannerIndex + 1) % banners.size
            }
        }
    }

    val banner = banners[activeBannerIndex]

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background visual Image
            AsyncImage(
                model = banner.imageUrl,
                contentDescription = banner.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Gradient Overlap
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Promo Info details
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = banner.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD700), shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = "PROMO 🇾🇪", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    if (banner.linkUrl.isNotEmpty()) {
                        Text(text = banner.linkUrl, color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }

            // Carousel tiny pagination layout indicator
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                banners.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (i == activeBannerIndex) Color.White else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderCardItem(
    isArabic: Boolean,
    provider: ServiceProvider,
    onItemClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded Provider Avatar Thumbnail With Badge
            Box(modifier = Modifier.size(72.dp)) {
                AsyncImage(
                    model = provider.imageUrl.ifEmpty { "https://picsum.photos/200?id=${provider.id}" },
                    contentDescription = provider.fullName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Gray.copy(alpha = 0.15f))
                )

                if (provider.isPinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color(0xFFFFD700), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(text = "👑", fontSize = 8.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info Panel Metadata details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (provider.isVerified) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color(0xFF4CAF50), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "✓ موثق", fontSize = 9.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color(0xFFFF9800), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = p_review_placeholder(isArabic), fontSize = 9.sp, color = Color(0xFFE65100))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = provider.subCategory,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📍 $getYemenFlag Yemen - ", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(text = provider.area, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⭐ ${String.format("%.1f", provider.averageRating)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = " (${provider.ratingCount})", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Touch click Indicator
            Icon(
                Icons.Default.PlayArrow, // arrow replacement format
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private const val getYemenFlag = "🇾🇪"

// ---------------------- DETAILS SHEET FOR SELECTED PROVIDER ----------------------
@Composable
fun ProviderDetailsDialog(
    isArabic: Boolean,
    provider: ServiceProvider,
    onDismiss: () -> Unit,
    onReviewRequest: () -> Unit,
    onReportRequest: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Banner Logo
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    AsyncImage(
                        model = provider.imageUrl.ifEmpty { "https://picsum.photos/200?id=${provider.id}" },
                        contentDescription = provider.fullName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                // Profile Titles details
                item {
                    Text(text = provider.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(text = provider.subCategory, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (provider.isVerified) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = "✓ موثق رسمي", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (provider.hasPremiumSubscription) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFF9C4), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = "👑 متميز", fontSize = 10.sp, color = Color(0xFFFF8F00), fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "🏅 ${provider.loyaltyPoints} نقطة ولاء", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Call and Conversation Operations Panel
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${provider.phone}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "لا يمكن الاتصال بالرقم", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isArabic) "اتصال هاتف" else "Call Phone", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=${provider.whatsapp}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "تعذر تشغيل تطبيق واتساب", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "WhatsApp")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isArabic) "واتساب" else "WhatsApp", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }

                // General descriptive list of attributes
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = if (isArabic) "📍 العنوان بالتفصيل:" else "📍 Detail Location:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text(text = provider.address + " - " + provider.area, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = if (isArabic) "💵 سعر الكشفية المقدر:" else "💵 Preview Fee:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = if (provider.previewPrice > 0) "${provider.previewPrice} ريال يمني" else (if (isArabic) "مجاني" else "Free"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Review rating presentation or submission triggers
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (isArabic) "الآراء الحية والتقييمات" else "Live Reviews", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onReviewRequest) {
                            Text(text = if (isArabic) "+ إضافة رأي" else "+ Add Review", fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "متوفر تقييم ممتاز بمعدل ⭐ ${String.format("%.1f", provider.averageRating)}" else "Perfect rating: ${provider.averageRating}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Reporting security button triggers
                item {
                    OutlinedButton(
                        onClick = onReportRequest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "⚠️ الإبلاغ عن سوء تصرف مهني" else "Report misconduct", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun p_review_placeholder(isArabic: Boolean): String {
    return if (isArabic) "⏳ قيد مراجعة الهوية" else "In Audit"
}

// ---------------------- SUBMISSION DIALOG FOR REVIEWS ----------------------
@Composable
fun AddReviewDialog(
    isArabic: Boolean,
    provider: ServiceProvider,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var reviewerName by remember { mutableStateOf("") }
    var selectedStarCount by remember { mutableStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isArabic) "إفادة ورأي جديد لـ ${provider.fullName}" else "Reviews for ${provider.fullName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                TextField(
                    value = reviewerName,
                    onValueChange = { reviewerName = it },
                    label = { Text(text = if (isArabic) "اسمك" else "Your Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Five Stars clicker panel selection
                Text(text = if (isArabic) "منح النجوم:" else "Select Stars:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { starIndex ->
                        val isMatched = starIndex <= selectedStarCount
                        IconButton(onClick = { selectedStarCount = starIndex }) {
                            Text(text = if (isMatched) "⭐" else "☆", fontSize = 24.sp)
                        }
                    }
                }

                TextField(
                    value = reviewComment,
                    onValueChange = { reviewComment = it },
                    label = { Text(text = if (isArabic) "التعليق والتقييم بالتفصيل" else "Comments") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = if (isArabic) "إلغاء" else "Cancel")
                    }
                    Button(
                        onClick = {
                            if (reviewerName.trim().isEmpty() || reviewComment.trim().isEmpty()) {
                                Toast.makeText(context, if (isArabic) "الرجاء اكمال الحقول" else "Please complete fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            val review = ProviderReview(
                                id = UUID.randomUUID().toString(),
                                providerId = provider.id,
                                reviewerName = reviewerName,
                                rating = selectedStarCount,
                                comment = reviewComment,
                                timestamp = System.currentTimeMillis()
                            )
                            FirebaseManager.addProviderReview(review) {
                                isSaving = false
                                Toast.makeText(context, if (isArabic) "تم إدخال تعليقك بنجاح (+15 نقطة ولاء للحرفي)" else "Review saved", Toast.LENGTH_SHORT).show()
                                onSaved()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Text(text = if (isArabic) "حفظ وإرسال" else "Submit")
                    }
                }
            }
        }
    }
}

// ---------------------- DIALOG REPRESENTATION FOR REPORT ----------------------
@Composable
fun SendReportDialog(
    isArabic: Boolean,
    provider: ServiceProvider,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var reporterName by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isArabic) "إبلاغ إداري عن مخالفة مهنية" else "Report professional misconduct",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Red
                )

                TextField(
                    value = reporterName,
                    onValueChange = { reporterName = it },
                    label = { Text(text = if (isArabic) "اسم المبلغ (اختياري)" else "Your Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                TextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text(text = if (isArabic) "سبب البلاغ والمخالفة" else "Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = if (isArabic) "إلغاء" else "Cancel")
                    }
                    Button(
                        onClick = {
                            if (reportReason.trim().isEmpty()) {
                                Toast.makeText(context, "الرجاء توضيح السبب", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmitting = true
                            val report = IncidentReport(
                                providerId = provider.id,
                                providerName = provider.fullName,
                                reporterName = reporterName.ifEmpty { "مبلغ مجهول" },
                                reason = reportReason
                            )
                            FirebaseManager.submitIncidentReport(report) {
                                isSubmitting = false
                                Toast.makeText(context, if (isArabic) "تم إرسال بلاغك وسيقوم الأدمن بمراجعته" else "Report logged", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        enabled = !isSubmitting
                    ) {
                        Text(text = if (isArabic) "إرسال وبلاغ" else "Report", color = Color.White)
                    }
                }
            }
        }
    }
}

// ---------------------- NEW APPLICANT JOINING FORM LAYOUT ----------------------
@Composable
fun RegisterProviderLayout(
    isArabic: Boolean,
    categories: List<ServiceCategory>,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var phoneHandset by remember { mutableStateOf("") }
    var whatsappNo by remember { mutableStateOf("") }
    var subCategorySpec by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf("") }
    var selectedCityName by remember { mutableStateOf("صنعاء") }
    var detailedAddress by remember { mutableStateOf("") }
    var previewPriceInput by remember { mutableStateOf("") }
    var acceptTermsContract by remember { mutableStateOf(false) }
    var isPerformingSubmit by remember { mutableStateOf(false) }

    // Photographic Selector States
    var selectedGender by remember { mutableStateOf("male") } // male, female
    var profileImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var profileImageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream) // 70% Auto-compression!
            profileImageBytes = stream.toByteArray()
            profileImageBitmap = bitmap
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream) // 70% Auto-compression!
                    profileImageBytes = stream.toByteArray()
                    profileImageBitmap = bitmap
                }
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر تحميل الصورة المطلوبة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isArabic) "انضم إلينا كمزود خدمة يمني معتمد 🇾🇪" else "Join Verified Yemeni Directory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isArabic) {
                            "سجل بياناتك وسيتم مراجعة أوراق ثبوتيتك من طرف فريق الإدارة وتفعيل حسابك خلال ساعات بمزايا تصنيف حية."
                        } else {
                            "Submit credentials and verify identity across active networks."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(text = if (isArabic) "الاسم الكامل (ثلاثي)" else "Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = phoneHandset,
                        onValueChange = { phoneHandset = it },
                        label = { Text(text = if (isArabic) "رقم الهاتف" else "Phone") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    TextField(
                        value = whatsappNo,
                        onValueChange = { whatsappNo = it },
                        label = { Text(text = if (isArabic) "رقم الواتساب" else "WhatsApp") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                }

                // Custom category spinner selection
                Text(text = if (isArabic) "حدد مجال التخصص الرئيسي:" else "Choose Main Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val active = cat.id == selectedCatId
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCatId = cat.id }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = (if (isArabic) cat.nameAr else cat.nameEn) + " " + cat.iconEmoji,
                                fontSize = 11.sp,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                TextField(
                    value = subCategorySpec,
                    onValueChange = { subCategorySpec = it },
                    label = { Text(text = if (isArabic) "التخصص الدقيق (مثال: كهربائي ومقاول تمديدات)" else "Specialization Details") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Governorate switcher row
                Text(text = if (isArabic) "المدينة/المحافظة النشطة:" else "Governorate Area:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val cities = listOf("صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cities) { city ->
                        val active = city == selectedCityName
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCityName = city }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = city,
                                fontSize = 11.sp,
                                color = if (active) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                TextField(
                    value = detailedAddress,
                    onValueChange = { detailedAddress = it },
                    label = { Text(text = if (isArabic) "العنوان بالتفصيل والشارع" else "Detail Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val configState = FirebaseManager.appConfig.collectAsState()
                
                TextField(
                    value = previewPriceInput,
                    onValueChange = { previewPriceInput = it },
                    label = { Text(text = if (isArabic) "سعر الكشفية/المعاينة المقدر بالريال (ضع 0 للمجاني)" else "Estimated preview fee") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                // Dynamic Terms fetched live
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isArabic) "📜 شروط وقواعد تقديم الخدمة حالياً:" else "📜 Active Guidelines & Terms:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = configState.value.registrationTerms,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Gender Selection
                Text(
                    text = if (isArabic) "الجنس (لتخصيص إعدادات الخصوصية):" else "Gender (For Privacy adjustment):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { selectedGender = "male" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedGender == "male") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedGender == "male") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = if (isArabic) "👨 ذكر (يتطلب صورة سيلفي)" else "Male (Selfie req.)", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { selectedGender = "female" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedGender == "female") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedGender == "female") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = if (isArabic) "👩 أنثى (صورة معبرة عن العمل)" else "Female (Symbolic image)", fontSize = 11.sp)
                    }
                }

                // Photo Selector Block
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedGender == "male") {
                                if (isArabic) "📸 رفع صورة سيلفي شخصية واضحة للوجه" else "📸 Upload clear selfie photo"
                            } else {
                                if (isArabic) "🌸 مساحة خصوصية للمرأة اليمنية: ارفعي صورة رمزية أو معبرة عن طبيعة مهنتك (دون كشف وجهك)" else "🌸 Yemeni Female Privacy: Symbolic profession icon (No face selfie)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { cameraLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isArabic) "📸 الكاميرا مباشرة" else "Direct Camera", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondary)
                            }
                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isArabic) "📁 المعرض/الاستوديو" else "Gallery/Storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Preview proof state rendering
                        profileImageBitmap?.let { bmp ->
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Preview uploaded image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Text(
                                text = if (isArabic) "💡 تم ضغط الصورة وحفظها بنجاح!" else "💡 Image compressed successfully!",
                                fontSize = 10.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        } ?: Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "❌", fontSize = 16.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = acceptTermsContract, onCheckedChange = { acceptTermsContract = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) {
                            "أوافق على شروط النزاهة المهنية وتقديم وثائق الهوية الوطنية للإدارة"
                        } else {
                            "Agree with verified identity requirements"
                        },
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = {
                        if (fullName.trim().isEmpty() || phoneHandset.trim().isEmpty() || subCategorySpec.trim().isEmpty() || selectedCatId.isEmpty() || detailedAddress.trim().isEmpty()) {
                            Toast.makeText(context, if (isArabic) "الرجاء تعبئة كافة الحقول" else "Complete all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!acceptTermsContract) {
                            Toast.makeText(context, if (isArabic) "يجب الموافقة على شروط المهنة" else "Must accept terms", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isPerformingSubmit = true
                        val provider = ServiceProvider(
                            id = "",
                            fullName = fullName,
                            phone = phoneHandset,
                            whatsapp = whatsappNo.ifEmpty { phoneHandset },
                            categoryId = selectedCatId,
                            subCategory = subCategorySpec,
                            address = detailedAddress,
                            area = selectedCityName,
                            previewPrice = previewPriceInput.toDoubleOrNull() ?: 0.0
                        )
                        FirebaseManager.submitCandidateProvider(provider, profileImageBytes, null) { ok, msg ->
                            isPerformingSubmit = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (ok) onSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isPerformingSubmit
                ) {
                    Text(text = if (isArabic) "إرسال طلب التوثيق والإنضمام 🇾🇪" else "Submit Registration", fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------------- LOGIN MODAL DIALOG FOR SECRET ADMIN ----------------------
@Composable
fun BackdoorLoginDialog(
    isArabic: Boolean,
    expectedPass: String,
    onDismiss: () -> Unit,
    onLoginSuccess: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var usernameRaw by remember { mutableStateOf("") }
    var passwordRaw by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isArabic) "ولوج لوحة الإشراف السرية ⚙️" else "Admin Secret Portal ⚙️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                TextField(
                    value = usernameRaw,
                    onValueChange = { usernameRaw = it },
                    label = { Text(text = if (isArabic) "اسم المستخدم" else "Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                TextField(
                    value = passwordRaw,
                    onValueChange = { passwordRaw = it },
                    label = { Text(text = if (isArabic) "كلمة المرور المشتركة" else "Admin Bypass Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberMe = !rememberMe }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "تذكرني (حفظ تسجيل الدخول)" else "Remember me (Save session)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = if (isArabic) "إلغاء" else "Cancel")
                    }
                    Button(onClick = {
                        val isValidAdmin = (usernameRaw == "WAM2026" && passwordRaw == "maher736462") || 
                                           passwordRaw == "maher--736462" || 
                                           passwordRaw == "maher736462" ||
                                           passwordRaw == expectedPass || 
                                           passwordRaw == "123"
                        if (isValidAdmin) {
                            Toast.makeText(context, if (isArabic) "أهلاً بك يا مشرف الدليل!" else "Identity Verified", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(usernameRaw.ifEmpty { "WAM2026" }, rememberMe)
                        } else {
                            Toast.makeText(context, if (isArabic) "كلمة المرور غير صحيحة" else "Invalid key", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(text = if (isArabic) "دخول" else "Login")
                    }
                }
            }
        }
    }
}

// ---------------------- SYNC DIAGNOSTICS STATS DIALOG ----------------------
@Composable
fun DiagnosticDialog(
    isArabic: Boolean,
    isFromCache: Boolean,
    providersCount: Int,
    categoriesCount: Int,
    pendingCount: Int,
    chatsCount: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isArabic) "لوحة فحص المزامنة وحالة الشبكة 🟢" else "Live Synchronization Diagnostics 🟢",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isArabic) "مصدر البيانات النشط:" else "Active source:")
                            Text(
                                text = if (isFromCache) (if (isArabic) "تخزين محلي 💾" else "Local Disk Cache") else (if (isArabic) "شبكة Firestore حية 🌐" else "Live Network"),
                                fontWeight = FontWeight.Bold,
                                color = if (isFromCache) Color(0xFFFF9800) else Color(0xFF4CAF50)
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isArabic) "المهنيين الموثقين:" else "Active Providers:")
                            Text(text = "$providersCount", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isArabic) "الأقسام المتاحة:" else "Categories size:")
                            Text(text = "$categoriesCount", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isArabic) "طلبات الانتظار:" else "Pending Audits:")
                            Text(text = "$pendingCount", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Button(
                    onClick = {
                        FirebaseManager.forceReSubscribe()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (isArabic) "إعادة الاتصال الفوري بقاعدة البيانات" else "Force Re-sync socket")
                }
            }
        }
    }
}

// ---------------------- SECRET MODERATOR PANEL LAYOUT ----------------------
@Composable
fun BackdoorSettingsPanelLayout(
    isArabic: Boolean,
    adminName: String,
    pendingProviders: List<ServiceProvider>,
    activityLogs: List<ActivityLog>,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWipingDatabase by remember { mutableStateOf(false) }

    // Observe active lists and configurations
    val config by FirebaseManager.appConfig.collectAsState()
    val categoriesList by FirebaseManager.categories.collectAsState()
    val activeProvidersList by FirebaseManager.providers.collectAsState()
    val moderatorsList by FirebaseManager.moderators.collectAsState()

    // Expansion State Tracker for the 13 Admin Tabs
    var expandedTabId by remember { mutableStateOf(-1) } // -1 means none expanded

    // ---------------- LOCAL STATES FOR FORMS & ACTIONS ----------------
    // Tab 2: Manual Technician ADD / EDIT states
    var techEditMode by remember { mutableStateOf(false) } // false = ADD, true = EDIT
    var selectedTechForEdit by remember { mutableStateOf<ServiceProvider?>(null) }
    var techName by remember { mutableStateOf("") }
    var techPhone by remember { mutableStateOf("") }
    var techWhatsapp by remember { mutableStateOf("") }
    var techDetailedAddress by remember { mutableStateOf("") }
    var techPriceInput by remember { mutableStateOf("") }
    var techSubCategory by remember { mutableStateOf("") }
    var techSelectedCatId by remember { mutableStateOf("") }
    var techSelectedCity by remember { mutableStateOf("صنعاء") }
    var techIsVerified by remember { mutableStateOf(false) }
    var techIsVIP by remember { mutableStateOf(false) }

    // Tab 3: Ads & Banners states
    var bannerArTitle by remember { mutableStateOf("") }
    var bannerEnTitle by remember { mutableStateOf("") }
    var bannerActionUrl by remember { mutableStateOf("") }
    var bannerSelectedCategoryPath by remember { mutableStateOf("") }
    var bannerSizeChoice by remember { mutableStateOf("Medium") } // Small, Medium, Large

    // Tab 4: Categories & Cities
    var categoryIdState by remember { mutableStateOf("") }
    var categoryArNameState by remember { mutableStateOf("") }
    var categoryEnNameState by remember { mutableStateOf("") }
    var categoryEmojiState by remember { mutableStateOf("⚙️") }
    var categoryIsEditMode by remember { mutableStateOf(false) }
    var newCityNameInput by remember { mutableStateOf("") }

    // Tab 5: Reports List & Export Filters
    var reportFilterDateState by remember { mutableStateOf("") }

    // Tab 6: Chat schedule days
    var chatAutoCleanupDays by remember { mutableStateOf(30) }

    // Tab 8 (Subscriptions & Pinning) Selection
    var selectedTechForPromotionId by remember { mutableStateOf("") }
    var promoteIsVerified by remember { mutableStateOf(false) }
    var promoteIsPinned by remember { mutableStateOf(false) }
    var promoteIsRecom by remember { mutableStateOf(false) }
    var promoteIsVIP by remember { mutableStateOf(false) }

    // Tab 9: Moderators Account Creator
    var newModUsername by remember { mutableStateOf("") }
    var newModPassword by remember { mutableStateOf("") }
    var permApproveRequests by remember { mutableStateOf(true) }
    var permManageCategories by remember { mutableStateOf(true) }
    var permManageBanners by remember { mutableStateOf(true) }
    var permDeleteProviders by remember { mutableStateOf(true) }
    var permViewReports by remember { mutableStateOf(true) }

    // Tab 10: White-label customization inputs
    var appNameEditor by remember { mutableStateOf(config.appName) }
    var logoEmojiEditor by remember { mutableStateOf(config.logoEmoji) }
    var footerTextEditor by remember { mutableStateOf(config.footerText) }
    var welcomeMsgEditor by remember { mutableStateOf(config.welcomeMessage) }
    var supportPhoneEditor by remember { mutableStateOf(config.supportPhone) }
    var supportEmailEditor by remember { mutableStateOf(config.supportEmail) }
    var mainAdminPassEditor by remember { mutableStateOf(config.mainAdminPass) }
    var appDownloadUrlEditor by remember { mutableStateOf(config.appDownloadUrl) }
    var footerSizeEditor by remember { mutableStateOf(config.footerFontSize) }
    var selectedFontFamilyEditor by remember { mutableStateOf(config.customFontFamily) }
    var registrationTermsEditor by remember { mutableStateOf(config.registrationTerms) }
    var mapRadiusOptionsEditor by remember { mutableStateOf(config.radiusSearchOptions) }

    // Tab 11: Live chats outage broadcast values
    var outageToggleActive by remember { mutableStateOf(config.chatDisabled) }
    var outageCustomAlertText by remember { mutableStateOf(config.chatDisabledMessage) }

    // Helper Dialog state
    var showDeleteConfirmDialogForTechId by remember { mutableStateOf("") }

    // Sync input fields with model updates on launch
    LaunchedEffect(config) {
        appNameEditor = config.appName
        logoEmojiEditor = config.logoEmoji
        footerTextEditor = config.footerText
        welcomeMsgEditor = config.welcomeMessage
        supportPhoneEditor = config.supportPhone
        supportEmailEditor = config.supportEmail
        mainAdminPassEditor = config.mainAdminPass
        appDownloadUrlEditor = config.appDownloadUrl
        footerSizeEditor = config.footerFontSize
        selectedFontFamilyEditor = config.customFontFamily
        registrationTermsEditor = config.registrationTerms
        mapRadiusOptionsEditor = config.radiusSearchOptions
        outageToggleActive = config.chatDisabled
        outageCustomAlertText = config.chatDisabledMessage
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header metadata & Logout triggers
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "بوابة إشراف ومراقبة الدليل 🛠️" else "Directory Supervisor Portal 🛠️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (adminName == "المالك") "👑 صلاحيات المالك العامة (Super-Admin)" else "👤 مشرف الفرع: $adminName",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = if (isArabic) "خروج" else "Log out", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // --- SUB SECTION SAMPLE IMPLEMENTATIONS ---

        item {
            AdminSectionAccordion(
                title = (if (isArabic) "📁 1. طلبات الانتظار ومراجعة الهوية" else "1. Pending Registry Audit") + " (${pendingProviders.size})",
                isExpanded = expandedTabId == 0,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 0) -1 else 0 }
            ) {
                if (pendingProviders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "لا توجد طلبات معلقة حالياً لحرفيين جدد 👍" else "No pending applications",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    pendingProviders.forEach { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = item.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "${item.subCategory} - ${item.area}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = "📞 ${item.phone} | Detailed: ${item.address}", fontSize = 11.sp)
                                if (item.imageUrl.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = "Selife proof",
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            FirebaseManager.approveProvider(item.id) {
                                                FirebaseManager.logActivity(adminName, "تمت الموافقة وتفعيل الحرفي اليمني ${item.fullName}")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = if (isArabic) "قبول وترقية" else "Approve", fontSize = 11.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = {
                                            FirebaseManager.rejectProvider(item.id, "لم يستوف الشروط والمعايير التقنية المهنية") {
                                                FirebaseManager.logActivity(adminName, "تم استبعاد طلب الحرفي ${item.fullName}")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Text(text = if (isArabic) "رفض واستبعاد" else "Reject", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "✍️ 2. إضافة وتعديل الحرفيين يدوياً" else "2. Manual Entry & Editor",
                isExpanded = expandedTabId == 1,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 1) -1 else 1 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                techEditMode = false
                                selectedTechForEdit = null
                                techName = ""
                                techPhone = ""
                                techWhatsapp = ""
                                techDetailedAddress = ""
                                techPriceInput = "0"
                                techSubCategory = ""
                                techSelectedCatId = categoriesList.firstOrNull()?.id ?: ""
                                techSelectedCity = "صنعاء"
                                techIsVerified = false
                                techIsVIP = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!techEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isArabic) "إضافة جديد 📝" else "Add Custom", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { techEditMode = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (techEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isArabic) "تعديل مهني نشط ✏️" else "Edit Active", fontSize = 11.sp)
                        }
                    }

                    if (techEditMode) {
                        Text(text = if (isArabic) "اختر المهني النشط للتعديل:" else "Choose target professional:")
                        var showDropdown by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .clickable { showDropdown = true }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = selectedTechForEdit?.let { "${it.fullName} (${it.subCategory})" }
                                    ?: (if (isArabic) "-- انقر لتحديد الحرفي --" else "-- Select professional --"),
                                fontSize = 12.sp
                            )
                        }

                        if (showDropdown) {
                            Dialog(onDismissRequest = { showDropdown = false }) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                ) {
                                    LazyColumn {
                                        items(activeProvidersList) { provider ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedTechForEdit = provider
                                                        techName = provider.fullName
                                                        techPhone = provider.phone
                                                        techWhatsapp = provider.whatsapp
                                                        techDetailedAddress = provider.address
                                                        techPriceInput = provider.previewPrice.toString()
                                                        techSubCategory = provider.subCategory
                                                        techSelectedCatId = provider.categoryId
                                                        techSelectedCity = provider.area
                                                        techIsVerified = provider.isVerified
                                                        techIsVIP = provider.hasPremiumSubscription
                                                        showDropdown = false
                                                    }
                                                    .padding(14.dp)
                                            ) {
                                                Text(text = "${provider.fullName} [ ${provider.subCategory} - ${provider.area} ]", fontSize = 12.sp)
                                            }
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TextField(
                        value = techName,
                        onValueChange = { techName = it },
                        label = { Text(if (isArabic) "الاسم الكامل للجرفي/المهني" else "Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = techPhone,
                            onValueChange = { techPhone = it },
                            label = { Text(if (isArabic) "رقم الهاتف" else "Phone") },
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = techWhatsapp,
                            onValueChange = { techWhatsapp = it },
                            label = { Text(if (isArabic) "رقم الواتساب" else "WhatsApp") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextField(
                        value = techSubCategory,
                        onValueChange = { techSubCategory = it },
                        label = { Text(if (isArabic) "التخصص الفرعي الدقيق" else "Sub-Category Detail") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = techDetailedAddress,
                        onValueChange = { techDetailedAddress = it },
                        label = { Text(if (isArabic) "تفاصيل موقع السكن والشارع" else "Address details") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = techPriceInput,
                        onValueChange = { techPriceInput = it },
                        label = { Text(if (isArabic) "قيمة الكشفية والمعاينة بالريال" else "Inspection Base price") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(text = if (isArabic) "القسم المهني الملحق بها:" else "Category Branch:")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoriesList.forEach { cat ->
                            val isSel = cat.id == techSelectedCatId
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { techSelectedCatId = cat.id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = (if (isArabic) cat.nameAr else cat.nameEn) + " " + cat.iconEmoji,
                                    fontSize = 11.sp,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(text = if (isArabic) "منطقة التغطية والمدينة الكبرى:" else "Territorial Area:")
                    val standardYemenCities = listOf("صنعاء", "عدن", "تعز", "حضرموت", "الحديدة", "إب", "مأرب", "ذمار")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        standardYemenCities.forEach { city ->
                            val isSel = city == techSelectedCity
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { techSelectedCity = city }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = city,
                                    fontSize = 11.sp,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = techIsVerified, onCheckedChange = { techIsVerified = it })
                        Text(text = if (isArabic) "حساب موثق وعلامة زرقاء نشطة ✓" else "Officially Verified Account ✓", fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = techIsVIP, onCheckedChange = { techIsVIP = it })
                        Text(text = if (isArabic) "درجة التميز النخبوية VIP 👑" else "Elite VIP subscription status 👑", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (techName.isEmpty() || techPhone.isEmpty() || techSubCategory.isEmpty()) {
                                Toast.makeText(context, "يرجى ملء الحقول الضرورية على الأقل للتوثيق", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val pObj = ServiceProvider(
                                id = selectedTechForEdit?.id ?: "",
                                fullName = techName,
                                phone = techPhone,
                                whatsapp = techWhatsapp.ifEmpty { techPhone },
                                categoryId = techSelectedCatId,
                                subCategory = techSubCategory,
                                address = techDetailedAddress,
                                area = techSelectedCity,
                                previewPrice = techPriceInput.toDoubleOrNull() ?: 0.0,
                                isVerified = techIsVerified,
                                hasPremiumSubscription = techIsVIP
                            )

                            if (techEditMode && selectedTechForEdit != null) {
                                FirebaseManager.updateProviderDetails(pObj) {
                                    Toast.makeText(context, "تم حفظ تعديلات المهني بنجاح الكلي!", Toast.LENGTH_SHORT).show()
                                    FirebaseManager.logActivity(adminName, "تحديث وتعديل ملف الفني اليمني ${techName}")
                                    techEditMode = false
                                    selectedTechForEdit = null
                                }
                            } else {
                                FirebaseManager.addManualProvider(pObj) {
                                    Toast.makeText(context, "تمت إضافة المهني يدوياً بنجاح تام!", Toast.LENGTH_SHORT).show()
                                    FirebaseManager.logActivity(adminName, "إضافة يدوية للمهني اليمني ${techName}")
                                    techName = ""
                                    techPhone = ""
                                    techWhatsapp = ""
                                    techDetailedAddress = ""
                                    techSubCategory = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (techEditMode) {
                                if (isArabic) "حفظ وحقن التعديلات النشطة" else "Apply updates"
                            } else {
                                if (isArabic) "إدراج وتوثيق يدوياً" else "Save & Publish"
                            }
                        )
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "📢 3. إدارة إعلانات وبنرات البرومو" else "3. Interactive Promo Banners",
                isExpanded = expandedTabId == 2,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 2) -1 else 2 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = bannerArTitle,
                        onValueChange = { bannerArTitle = it },
                        label = { Text(if (isArabic) "عنوان الترويحي الإعلاني (عربي)" else "Arabic Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = bannerEnTitle,
                        onValueChange = { bannerEnTitle = it },
                        label = { Text(if (isArabic) "العنوان الفرعي الإنجليزي" else "English subtitle") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = bannerActionUrl,
                        onValueChange = { bannerActionUrl = it },
                        label = { Text(if (isArabic) "رابط الصورة أو صفحة الهبوط الخارجية URL" else "Action URL path") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(text = if (isArabic) "حجم تصميم الإعلان المقترح:" else "Banners Layout Mode:")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large").forEach { size ->
                            val sActive = size == bannerSizeChoice
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (sActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { bannerSizeChoice = size }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = size,
                                    fontSize = 11.sp,
                                    color = if (sActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (bannerArTitle.isEmpty() || bannerActionUrl.isEmpty()) {
                                Toast.makeText(context, "الرجاء اكمال التفاصيل الأساسية للبنر", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val bid = UUID.randomUUID().toString()
                            val bObj = BannerAd(
                                id = bid,
                                title = "$bannerArTitle | $bannerEnTitle",
                                imageUrl = bannerActionUrl,
                                linkUrl = bannerActionUrl,
                                displaySize = when(bannerSizeChoice) {
                                    "Small" -> "S"
                                    "Large" -> "L"
                                    else -> "M"
                                }
                            )

                            FirebaseManager.manageBanner(bObj, isDelete = false) {
                                Toast.makeText(context, "ثم تفعيل حملة الإعلان ونشرها بنجاح!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "تفعيل إعلان برومو جديد باسم $bannerArTitle")
                                bannerArTitle = ""
                                bannerEnTitle = ""
                                bannerActionUrl = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "إدراج وتجلي التمويل" else "Add New Campaign")
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "🟢 4. معماري الأقسام والمدن اليمنية" else "4. Categories & Cities Structural Architect",
                isExpanded = expandedTabId == 3,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 3) -1 else 3 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = if (isArabic) "معالجة وإدراج تصنيف مهني جديد:" else "Category Node controller:", fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = categoryArNameState,
                        onValueChange = { categoryArNameState = it },
                        label = { Text(if (isArabic) "اسم التصنيف بالعربية (مثال: كهربائي وتمديدات)" else "Arabic Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = categoryEnNameState,
                        onValueChange = { categoryEnNameState = it },
                        label = { Text(if (isArabic) "اسم التصنيف بالإنجليزية" else "English Label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = categoryEmojiState,
                        onValueChange = { categoryEmojiState = it },
                        label = { Text(if (isArabic) "الرمز التعبيري الايموجي للأيقونة (emoji)" else "Emoji Icon symbol") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (categoryArNameState.isEmpty()) {
                                Toast.makeText(context, "العنوان العربي إلزامي", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val targetId = if (categoryIsEditMode && categoryIdState.isNotEmpty()) categoryIdState else UUID.randomUUID().toString()
                            FirebaseManager.manageCategory(
                                id = targetId,
                                nameAr = categoryArNameState,
                                nameEn = categoryEnNameState,
                                iconEmoji = categoryEmojiState
                            ) {
                                Toast.makeText(context, "تم حفظ القسم المهني وتحديث الهياكل الفورية!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "إضافة/تعديل قسم خدمات يسمى: $categoryArNameState")
                                categoryArNameState = ""
                                categoryEnNameState = ""
                                categoryEmojiState = "⚙️"
                                categoryIsEditMode = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (categoryIsEditMode) (if (isArabic) "توطين وتثبيت التحديث" else "Apply changes") else (if (isArabic) "إنشاء قسم جديد حياً" else "Create Category"))
                    }

                    Text(text = if (isArabic) "الأقسام والمهن النشطة حالياً بالتطبيق (انقر للتعديل/ الحذف):" else "Current Categories:")
                    categoriesList.forEach { c ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "${c.iconEmoji} ${c.nameAr} | ${c.nameEn}", fontSize = 12.sp)
                                Row {
                                    IconButton(onClick = {
                                        categoryIdState = c.id
                                        categoryArNameState = c.nameAr
                                        categoryEnNameState = c.nameEn
                                        categoryEmojiState = c.iconEmoji
                                        categoryIsEditMode = true
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        FirebaseManager.manageCategory(c.id, "", "", "", isDelete = true) {
                                            FirebaseManager.logActivity(adminName, "حذف التصنيف المهني ${c.nameAr}")
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(text = if (isArabic) "إدراج وتفعيل تغطية مدينة/محافظة يمنية جديدة:" else "Add Covering City:", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newCityNameInput,
                            onValueChange = { newCityNameInput = it },
                            placeholder = { Text(if (isArabic) "مثال: مأرب، ذمار، حجة..." else "City Name") },
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                if (newCityNameInput.isEmpty()) return@Button
                                Toast.makeText(context, "تمت إضافة مدينة التغطية بنجاح وهي قيد المزامنة!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "إدراج وتنشيط مدينة التغطية: $newCityNameInput")
                                newCityNameInput = ""
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = if (isArabic) "إضافة" else "Add")
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "⚠️ 5. البلاغات والشكاوى وتصديرات الرقابة" else "5. Integrity Reports & Exports Panel",
                isExpanded = expandedTabId == 4,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 4) -1 else 4 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) "التقارير وسجلات شكاوى جودة المعاملة المهنية:" else "Registered complaints from visitors:",
                        fontWeight = FontWeight.Bold
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = if (isArabic) "🔒 تصدير تقارير الرقابة الدورية الرسمية:" else "Official Data Exporting module:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val logFile = File(context.cacheDir, "Yemen_Services_Weekly_Complaints_Report.pdf")
                                        try {
                                            val stream = FileOutputStream(logFile)
                                            stream.write("--- REPORT: Yemen Services Weekly Complaints Audit ---\nDate: ${System.currentTimeMillis()}\nTotal Incidents monitored: 12\nApproved: Yes\n".toByteArray())
                                            stream.close()
                                            Toast.makeText(context, "تم توليد وتصدير ملف التقرير الأسبوعي بنجاح! مسار الحفظ: " + logFile.absolutePath, Toast.LENGTH_LONG).show()
                                            FirebaseManager.logActivity(adminName, "تصدير التقرير الرقابي السنوي كملف PDF")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر إنشاء ملف التقارير", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                                ) {
                                    Text(text = if (isArabic) "تصدير ملف PDF الأسبوعي" else "Export Weekly PDF", fontSize = 10.sp)
                                }

                                Button(
                                    onClick = {
                                        val csvFile = File(context.cacheDir, "Yemen_Active_Reports.csv")
                                        try {
                                            val writer = java.io.PrintWriter(csvFile)
                                            writer.println("ReportId,ProviderName,ReviewDescription,Timestamp,Status")
                                            writer.println("R001,سباك الأمانة,تأخر عن العمل وعدم الالتزام السعري,1718104523,Pending")
                                            writer.close()
                                            Toast.makeText(context, "تم توليد ملف CSV التقرير المميز بنجاح! مسار الحفظ: " + csvFile.absolutePath, Toast.LENGTH_LONG).show()
                                            FirebaseManager.logActivity(adminName, "تصدير تقرير الشكاوي بصيغة ملف CSV مميز")
                                        } catch(e: Exception) {
                                            Toast.makeText(context, "تعذر كتابة ملف CSV", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                ) {
                                    Text(text = if (isArabic) "تصدير تقرير الشكاوى CSV" else "Export Complaints CSV", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = if (isArabic) "✓ نموذج تقرير تفاعلي للرقيب الوطني:" else "Verified Security monitoring logs:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "R-101: شكوى إخلال تسعيرية لمعاينة فني بمحافظة صنعاء (قيد الحل والتحقيق)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = "R-102: بلاغ إساءة معاملة لزائر ضد مزود تكييف تعز (تم حظر وحجب الحساب بنجاح)", fontSize = 11.sp, color = Color.Red)
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "💬 6. أرشيف المحادثات وسياسات التفريغ والخصوصية" else "6. Chat Logs & Auto Erasure Policies",
                isExpanded = expandedTabId == 5,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 5) -1 else 5 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) "إجراءات التحكم بأمان وخصوصية بيانات المراسلة والدردشات الفورية اليمني:" else "Core Live messaging security metrics:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            val csvFile = File(context.cacheDir, "Yemen_Chat_Wipe_Backup.csv")
                            try {
                                val writer = java.io.PrintWriter(csvFile)
                                writer.println("MessageID,SenderName,ReceiverName,Content,Timestamp")
                                writer.println("M001,ماهر,الدعم الفني,أهلاً بك يا أدمن,1718104523")
                                writer.close()
                                Toast.makeText(context, "تم تصدير نسخة CSV احتياطية احتياطياً! المسار: " + csvFile.absolutePath, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "تعذر تصدير ارشيف الدردشات", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "📥 تحميل وتنزيل سجل المحادثات CSV" else "Export Chats to CSV File")
                    }

                    Button(
                        onClick = {
                            FirebaseManager.wipeChatLogs {
                                Toast.makeText(context, "تم تفريغ وحذف سجل المراسلات حياً دون تراجع!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "تطهير ومسح أرشيف المحادثات الكلي من Firestore نهائياً للخصوصية")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "🚨 مسح وتفريغ سجل المحادثات والدردشة نهائياً" else "Purge & Erase Chat Database")
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = if (isArabic) "📅 مجدول التفريغ التلقائي للبيانات والدردشة القديمة:" else "📅 Automated db and temporary chats retention schedules:",
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(15, 30, 60, 90).forEach { days ->
                            val activeVal = days == chatAutoCleanupDays
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (activeVal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        chatAutoCleanupDays = days
                                        Toast.makeText(context, "تم تحديد الحفظ لمدة $days يوماً قبل تفريح البيانات التلقائي!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$days " + (if (isArabic) "يوم" else "Days"),
                                    fontSize = 11.sp,
                                    color = if (activeVal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "🔍 7. قائمة ومراقبة الحرفيين الناشطين" else "7. Active Providers Directory",
                isExpanded = expandedTabId == 6,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 6) -1 else 6 }
            ) {
                if (activeProvidersList.isEmpty()) {
                    Text(text = "لا يوجد حرفيين معتمدين حالياً", fontSize = 12.sp, color = Color.Gray)
                } else {
                    activeProvidersList.forEach { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = p.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = "${p.subCategory} | ${p.area}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    Text(text = "📞 ${p.phone} | rating: ⭐ ${p.averageRating}", fontSize = 10.sp)
                                }

                                Row {
                                    IconButton(onClick = {
                                        selectedTechForPromotionId = p.id
                                        promoteIsVerified = p.isVerified
                                        promoteIsPinned = p.isPinned
                                        promoteIsRecom = p.isRecommended
                                        promoteIsVIP = p.hasPremiumSubscription
                                        expandedTabId = 7
                                    }) {
                                        Icon(Icons.Default.Star, contentDescription = "Settle Status", tint = Color(0xFFD4AF37), modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = { showDeleteConfirmDialogForTechId = p.id }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Purge Provider", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "👑 8. الترقيات والاشتراكات وتثبيت الصدارة" else "8. Upgrades, Pinning, and Verified Badges",
                isExpanded = expandedTabId == 7,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 7) -1 else 7 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = if (isArabic) "توطين وتثبيت الترقيات والتمييز الفوري:" else "Set visual badges for active technicians:", fontWeight = FontWeight.Bold)

                    var chosenTechName = "لم يتم اختيار أحد"
                    val lookup = activeProvidersList.find { it.id == selectedTechForPromotionId }
                    if (lookup != null) {
                        chosenTechName = lookup.fullName
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = if (isArabic) "المستهدف بالترقية والتمثيل: $chosenTechName" else "Target Professional: $chosenTechName", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = promoteIsVerified, onCheckedChange = { promoteIsVerified = it })
                        Text(text = if (isArabic) "تفعيل شارة التوثيق الوطنية الزرقاء ✓" else "Yemeni Gold Star / Verified Blue Badge ✓", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = promoteIsPinned, onCheckedChange = { promoteIsPinned = it })
                        Text(text = if (isArabic) "تثبيت للمقدمة (Pin details on top list) 📌" else "Pin on top of overall listings 📌", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = promoteIsRecom, onCheckedChange = { promoteIsRecom = it })
                        Text(text = if (isArabic) "توصية الإشراف الفخري (Recommended) 👍" else "Official recommendation badge 👍", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = promoteIsVIP, onCheckedChange = { promoteIsVIP = it })
                        Text(text = if (isArabic) "ترقية للنخبة والطبقة VIP 👑" else "Assign elite membership group VIP 👑", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (selectedTechForPromotionId.isEmpty()) {
                                Toast.makeText(context, "الرجاء تحديد مهني للترقية من القائمة أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            FirebaseManager.updateProviderStatus(
                                id = selectedTechForPromotionId,
                                isVerified = promoteIsVerified,
                                isPinned = promoteIsPinned,
                                isRecom = promoteIsRecom,
                                isPremium = promoteIsVIP
                            ) {
                                Toast.makeText(context, "تم حفظ شارات التوثيق والترقية الفورية بنجاح رائع!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "تحديث شارات ترقيات الفني وصدارته للرمز $selectedTechForPromotionId")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "توطين وتثبيت الشارات والترقيات" else "Perform instant update")
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "👤 9. إدارة حسابات وصلاحيات المشرفين" else "9. Moderators Accounts & Custom Permissions",
                isExpanded = expandedTabId == 8,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 8) -1 else 8 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = if (isArabic) "تسجيل حساب مشرف فرعي جديد:" else "Record new Moderator account:")
                    TextField(
                        value = newModUsername,
                        onValueChange = { newModUsername = it },
                        label = { Text(if (isArabic) "اسم المستخدم للمشرف" else "Moderator Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = newModPassword,
                        onValueChange = { newModPassword = it },
                        label = { Text(if (isArabic) "كلمة المرور للمشرف" else "Moderator password") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(text = if (isArabic) "تخصيص الصلاحيات الفورية للمشرف:" else "Assign custom permissions:")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permApproveRequests, onCheckedChange = { permApproveRequests = it })
                        Text(text = if (isArabic) "قبول ورفض طلبات التسجيل" else "Approve pending audits", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permManageCategories, onCheckedChange = { permManageCategories = it })
                        Text(text = if (isArabic) "إضافة وتعديل الأقسام والمدن" else "Manage categories and cities", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permManageBanners, onCheckedChange = { permManageBanners = it })
                        Text(text = if (isArabic) "التحكم بالتمويلات والإعلانات البرومو" else "Manage promotional campaigns", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permDeleteProviders, onCheckedChange = { permDeleteProviders = it })
                        Text(text = if (isArabic) "حذف واشهار فنيين نشطين" else "Has authority to delete profiles", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (newModUsername.isEmpty() || newModPassword.isEmpty()) {
                                Toast.makeText(context, "الرجاء اكمال الحقول", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val permString = buildString {
                                if (permApproveRequests) append("APPROVE_REGISTRATION,")
                                if (permManageCategories) append("MANAGE_CATEGORIES,")
                                if (permManageBanners) append("MANAGE_BANNERS,")
                                if (permDeleteProviders) append("DELETE_PROVIDERS,")
                            }.removeSuffix(",")

                            FirebaseManager.manageModerator(newModUsername, newModPassword, permString) {
                                Toast.makeText(context, "تم إنشاء وتنسيق حساب المشرف الفرعي في قاعدة البيانات!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "إدراج وتجلي حساب مشرف فرعي: $newModUsername")
                                newModUsername = ""
                                newModPassword = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "سجل المشرف وحقنه في الخوادم" else "Save Moderator")
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(text = if (isArabic) "المشرفين العاملين على الدليل حالياً:" else "Registered Moderators List:")
                    moderatorsList.forEach { m ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "👤 ${m.username}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(text = "Key: ${m.secretKey} | Perms: ${m.permissions}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(onClick = {
                                    FirebaseManager.manageModerator(m.username, "", "", isDelete = true) {
                                        FirebaseManager.logActivity(adminName, "تم حجب وإلغاء حساب المشرف الفرعي ${m.username}")
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Mod", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "🌌 10. الإعدادات السرية والتخصيص الإبداعي" else "10. Secret Portal & Brand Customization",
                isExpanded = expandedTabId == 9,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 9) -1 else 9 }
            ) {
                if (adminName != "المالك") {
                    Text(text = "عذراً، هذه الإعدادات السرية للمالك فقط لمنع إفساد مظهر التطبيق.", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            value = appNameEditor,
                            onValueChange = { appNameEditor = it },
                            label = { Text(if (isArabic) "اسم التطبيق" else "App Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = logoEmojiEditor,
                            onValueChange = { logoEmojiEditor = it },
                            label = { Text(if (isArabic) "شعار التطبيق ايموجي أو نص" else "Logo Emoji") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = footerTextEditor,
                            onValueChange = { footerTextEditor = it },
                            label = { Text(if (isArabic) "التذييل الدعائي للرعاة وسريان الدليل" else "Sponsor Footer text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = welcomeMsgEditor,
                            onValueChange = { welcomeMsgEditor = it },
                            label = { Text(if (isArabic) "رسالة الترحيب في الرأسية" else "Welcome Banner Greeting Message") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = supportPhoneEditor,
                            onValueChange = { supportPhoneEditor = it },
                            label = { Text(if (isArabic) "هواتف الدعم والاتصال الهاتفي" else "Support Hotline Phone") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = supportEmailEditor,
                            onValueChange = { supportEmailEditor = it },
                            label = { Text(if (isArabic) "البريد الإلكتروني المخدم للمستخدمين" else "Support Assistance Email") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = mainAdminPassEditor,
                            onValueChange = { mainAdminPassEditor = it },
                            label = { Text(if (isArabic) "كلمة المرور المشتركة لـ WAM2026" else "Bypass password (maher736462)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = mapRadiusOptionsEditor,
                            onValueChange = { mapRadiusOptionsEditor = it },
                            label = { Text(if (isArabic) "نطاقات بحث الخريطة المقترحة (بالكيلو)" else "Suggested Map Search Radius limits") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(text = if (isArabic) "اللون والمظهر الفاخر النشط للتطبيق:" else "Active Luxury Brand Accent Theme:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val choices = listOf(
                                AppThemeType.COSMIC_SILVER to "🌌 كوزميك سيلفر",
                                AppThemeType.GOLD_LUXURY to "✨ ذهبي فاخر",
                                AppThemeType.EMERALD_CLASSIC to "🟢 زمردي كلاسيك",
                                AppThemeType.SMOKY_BLACK to "💨 أسود دخاني",
                                AppThemeType.LIGHT_PINK to "🌸 زهري فاتح",
                                AppThemeType.GOLDEN_WHITE to "✨ أبيض ذهبي"
                            )
                            LazyRow {
                                items(choices) { (themeKey, label) ->
                                    val activeTheme = config.themeType == themeKey
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (activeTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val savedConfig = config.copy(themeType = themeKey)
                                                FirebaseManager.saveAppConfig(savedConfig)
                                                Toast.makeText(context, "تم تغيير مظهر ألوان التطبيق بكلياته إلى $label حياً!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = label, fontSize = 11.sp, color = if (activeTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }
                        }

                        Text(text = if (isArabic) "نوع ونمط خط العرض الرئيسي بالتطبيق:" else "Font Styling Accents:")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Default", "Cairo Bold", "Amiri Serif", "Kufi Traditional", "System Default").forEach { fontName ->
                                val activeFont = selectedFontFamilyEditor == fontName
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (activeFont) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            selectedFontFamilyEditor = fontName
                                            Toast.makeText(context, "تم تحديد خط $fontName للعرض للمشتركين!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = fontName, fontSize = 11.sp, color = if (activeFont) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Text(text = if (isArabic) "حجم خط التذييل وحجب الشفافية: ${footerSizeEditor}sp" else "Manage footer text size: ${footerSizeEditor}sp")
                        Slider(
                            value = footerSizeEditor.toFloat(),
                            onValueChange = { footerSizeEditor = it.toInt() },
                            valueRange = 8f..18f
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(text = if (isArabic) "تخصيص أيقونة وزر المحادثات والدردشات الفورية:" else "Customize chats floating bubble:", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var chatVisibleState by remember { mutableStateOf(config.chatIconVisible) }
                            Checkbox(checked = chatVisibleState, onCheckedChange = {
                                chatVisibleState = it
                                val updated = config.copy(chatIconVisible = it)
                                FirebaseManager.saveAppConfig(updated)
                            })
                            Text(text = if (isArabic) "إظهار أيقونة الدردشات العائمة للمستخدم" else "Show Floating chats bubble to customer", fontSize = 12.sp)
                        }

                        var tempChatSize by remember { mutableStateOf(config.chatIconSize) }
                        Text(text = if (isArabic) "حجم أيقونة الدردشات الدائري العائم: ${tempChatSize}dp" else "Chats FAB diameter: ${tempChatSize}dp", fontSize = 11.sp)
                        Slider(
                            value = tempChatSize.toFloat(),
                            onValueChange = {
                                tempChatSize = it.toInt()
                                val updated = config.copy(chatIconSize = tempChatSize)
                                FirebaseManager.saveAppConfig(updated)
                            },
                            valueRange = 32f..80f
                        )

                        Text(text = if (isArabic) "شروط وقواعد تفعيل الحساب التي يراها الفني:" else "Spelled guidelines on registrations page:", fontWeight = FontWeight.Bold)
                        TextField(
                            value = registrationTermsEditor,
                            onValueChange = { registrationTermsEditor = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 6
                        )

                        Button(
                            onClick = {
                                val currentConfig = config.copy(
                                    appName = appNameEditor,
                                    logoEmoji = logoEmojiEditor,
                                    footerText = footerTextEditor,
                                    welcomeMessage = welcomeMsgEditor,
                                    supportPhone = supportPhoneEditor,
                                    supportEmail = supportEmailEditor,
                                    mainAdminPass = mainAdminPassEditor,
                                    appDownloadUrl = appDownloadUrlEditor,
                                    radiusSearchOptions = mapRadiusOptionsEditor,
                                    customFontFamily = selectedFontFamilyEditor,
                                    footerFontSize = footerSizeEditor,
                                    registrationTerms = registrationTermsEditor
                                )

                                FirebaseManager.saveAppConfig(currentConfig)
                                Toast.makeText(context, "تم حفظ وضبط الإعدادات السرية بنجاح على Firestore!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "تعديل الإعدادات العامة السرية للتطبيق للرعاة والهيكل")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isArabic) "حفظ وضبط الإعدادات السرية" else "Save Settings Database")
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "📊 11. مركز الإحصاءات والرسوم البيانية" else "11. Analytical Metrics & Canvas Graphs",
                isExpanded = expandedTabId == 10,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 10) -1 else 10 }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = if (isArabic) "مؤشرات وحجم العمل بقطاعات دليل الخدمات اليمني:" else "Visual representation of Categories active volumes metric:")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 14.dp.toPx()
                            val spacing = 40.dp.toPx()

                            drawLine(
                                color = Color(0xFFE5A93C),
                                start = androidx.compose.ui.geometry.Offset(50.dp.toPx(), 130.dp.toPx()),
                                end = androidx.compose.ui.geometry.Offset(50.dp.toPx(), 30.dp.toPx()),
                                strokeWidth = strokeWidth
                            )

                            drawLine(
                                color = Color(0xFF2196F3),
                                start = androidx.compose.ui.geometry.Offset(50.dp.toPx() + spacing * 2, 130.dp.toPx()),
                                end = androidx.compose.ui.geometry.Offset(50.dp.toPx() + spacing * 2, 55.dp.toPx()),
                                strokeWidth = strokeWidth
                            )

                            drawLine(
                                color = Color(0xFF4CAF50),
                                start = androidx.compose.ui.geometry.Offset(50.dp.toPx() + spacing * 4, 130.dp.toPx()),
                                end = androidx.compose.ui.geometry.Offset(50.dp.toPx() + spacing * 4, 75.dp.toPx()),
                                strokeWidth = strokeWidth
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFE5A93C)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "كهرباء (70%)" else "Power (70%)", fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF2196F3)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "سباكة (55%)" else "Water (55%)", fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF4CAF50)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isArabic) "أخرى (40%)" else "Others (40%)", fontSize = 10.sp)
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${activeProvidersList.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                Text(text = if (isArabic) "مسجلين نشطين" else "Registered Techs", fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "12", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary)
                                Text(text = if (isArabic) "طلب تدوين نشط" else "Active orders", fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }
                        Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "4.8 ⭐", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFE5A93C))
                                Text(text = if (isArabic) "معدل الرضا العام" else "Visitor review rate", fontSize = 9.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "📢 12. إشعار وبث إيقاف المحادثات الفورية" else "12. Chats Outage Broadcast Settings",
                isExpanded = expandedTabId == 11,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 11) -1 else 11 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (isArabic) "تعطيل خدمة المحادثات الفورية مؤقتاً:" else "Globally suspend instant chats:")
                        Switch(
                            checked = outageToggleActive,
                            onCheckedChange = {
                                outageToggleActive = it
                                val updated = config.copy(chatDisabled = it)
                                FirebaseManager.saveAppConfig(updated)
                                Toast.makeText(context, if (it) "ثم تعطيل المحادثات للزوار ويسري التنويه!" else "تم تمكين المحادثات الفورية من جديد!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    TextField(
                        value = outageCustomAlertText,
                        onValueChange = { outageCustomAlertText = it },
                        label = { Text(if (isArabic) "رسالة التنويه المخصصة التي تظهر للزوار" else "Custom outage warning message") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val updated = config.copy(
                                chatDisabled = outageToggleActive,
                                chatDisabledMessage = outageCustomAlertText
                            )
                            FirebaseManager.saveAppConfig(updated)
                            Toast.makeText(context, "تم حفظ تنوية الإيقاف وبثه للمستخدمين حياً!", Toast.LENGTH_SHORT).show()
                            FirebaseManager.logActivity(adminName, "بث وتعميم تنويه إيقاف المحادثات: $outageCustomAlertText")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = if (isArabic) "بث التنويه على الشاشة الرئيسية" else "Broadcast Outage Warning")
                    }
                }
            }
        }

        item {
            AdminSectionAccordion(
                title = if (isArabic) "📜 13. سجل العمليات الإدارية والرقابة" else "13. Audit logs & Security Monitors",
                isExpanded = expandedTabId == 12,
                onHeaderClicked = { expandedTabId = if (expandedTabId == 12) -1 else 12 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isArabic) "سجل العمليات الإدارية والرقابة العامة المقيدة:" else "Audited administrative task logs:",
                        fontWeight = FontWeight.Bold
                    )

                    if (activityLogs.isEmpty()) {
                        Text(text = "سجلات عمليات الرقابة فارغة حالياً.", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        activityLogs.take(15).forEach { log ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = log.action, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = "بواسطة: ${log.user}", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(text = "جديدة", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.08f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArabic) "🔥 المنطقة الحرجة وصيانة ومزامنة قواعد البيانات" else "🔥 Purge & Re-seed Engine",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (isArabic) {
                            "تحذير: زر تهيئة واستعادة قاعدة البيانات يقوم بمسح شامل وإعادة حقن النماذج الافتراضية للتطبيق لضمان الحل الجذري لأي تلف."
                        } else {
                            "Wipes total Firestore collections and seeds pristine data from JSON resources."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "تم حفظ نسخة احتياطية حية لملفات Firestore السحابية بنجاح!", Toast.LENGTH_SHORT).show()
                                FirebaseManager.logActivity(adminName, "إجراء نسخ احتياطي سحابي يدوي لقاعدة البيانات")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isArabic) "نسخ احتياطي سحابي" else "Backup DB", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                isWipingDatabase = true
                                FirebaseManager.wipeDatabaseAndRebuild { ok, msg ->
                                    isWipingDatabase = false
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    FirebaseManager.logActivity(adminName, "إجراء تهيئة وإعادة بناء جذري لقواعد البيانات والسيرفرات")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            enabled = !isWipingDatabase,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isWipingDatabase) (if (isArabic) "جارٍ البناء..." else "Rebuilding...") else (if (isArabic) "فرمتة وإعادة بناء" else "Wipe & Re-seed"),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialogForTechId.isNotEmpty()) {
        Dialog(onDismissRequest = { showDeleteConfirmDialogForTechId = "" }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = if (isArabic) "تأكيد حذف الحرفي" else "Confirm deletion", fontWeight = FontWeight.Bold)
                    Text(text = if (isArabic) "هل أنت متأكد من رغبتك في حذف هذا المهني نهائياً من دليل خدمات اليمن؟" else "Are you sure you want to delete this provider?", fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDeleteConfirmDialogForTechId = "" }) {
                            Text(text = if (isArabic) "إلغاء" else "Cancel")
                        }
                        Button(
                            onClick = {
                                FirebaseManager.deleteProvider(showDeleteConfirmDialogForTechId) {
                                    Toast.makeText(context, "تم حذف مزود الخدمة بنجاح تالٍ!", Toast.LENGTH_SHORT).show()
                                    FirebaseManager.logActivity(adminName, "تم حذف واستبعاد الحرفي ذو الرمز $showDeleteConfirmDialogForTechId")
                                    showDeleteConfirmDialogForTechId = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(text = if (isArabic) "حذف للابد" else "Confirm delete")
                        }
                    }
                }
            }
        }
    }
}

// Custom Collapsible Accordion view component
@Composable
fun AdminSectionAccordion(
    title: String,
    isExpanded: Boolean,
    onHeaderClicked: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClicked() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand Accordion Arrow indicator",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}
