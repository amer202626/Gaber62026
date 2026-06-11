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
            var activeScreen by remember { mutableStateOf(Screen.HOME) }
            var isArabic by remember { mutableStateOf(true) }
            var loggedAdminUser by remember { mutableStateOf<String?>(null) }

            // Overlay/Bottom Sheets controllers
            var showDiagnosticsDialog by remember { mutableStateOf(false) }
            var selectedProviderForDetail by remember { mutableStateOf<ServiceProvider?>(null) }
            var showBackdoorLoginDialog by remember { mutableStateOf(false) }
            var showAddReviewDialog by remember { mutableStateOf<ServiceProvider?>(null) }
            var showReportDialog by remember { mutableStateOf<ServiceProvider?>(null) }

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
                                                activeScreen = Screen.HOME
                                                Toast.makeText(context, if (isArabic) "تم تسجيل الخروج" else "Logged out", Toast.LENGTH_SHORT).show()
                                            }
                                        )
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
                                onLoginSuccess = { name ->
                                    loggedAdminUser = name
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

                TextField(
                    value = previewPriceInput,
                    onValueChange = { previewPriceInput = it },
                    label = { Text(text = if (isArabic) "سعر الكشفية/المعاينة المقدر بالريال (ضع 0 للمجاني)" else "Estimated preview fee") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

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
                        FirebaseManager.submitCandidateProvider(provider, null, null) { ok, msg ->
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
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    var usernameRaw by remember { mutableStateOf("") }
    var passwordRaw by remember { mutableStateOf("") }

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
                            onLoginSuccess(usernameRaw.ifEmpty { "WAM2026" })
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
    var isWipingDatabase by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Control Card Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.08f)),
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
                            text = if (isArabic) "لوحة تحكم المشرف الوطني" else "National Moderator Suite",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFD32F2F)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "$adminName - Active Session", fontSize = 11.sp, color = Color.Gray)
                    }

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = if (isArabic) "خروج" else "Log out", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // CRITICAL PURGE ENGINE (THE RED BUTTON REQUESTED BY USER TO WIPE ALL AND RE-ESTABLISH IN PRISTINE FORM)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚨", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "منطقة الخطر واستعادة التهيئة!" else "Danger Zone & Hard Reset",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFD32F2F)
                        )
                    }

                    Text(
                        text = if (isArabic) {
                            "سيقوم هذا الزر بمسح وحذف كافة الملفات والبيانات والطلبات والمحادثات المخزنة سحابياً ومحلياً وإعادة بناء قاعدة البيانات نظيفة تماماً وضخ عينات يمنية ممتازة!"
                        } else {
                            "This action purges all Firestore & local cache collections, reseeding fresh databases cleanly."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            isWipingDatabase = true
                            FirebaseManager.wipeDatabaseAndRebuild { success, message ->
                                isWipingDatabase = false
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isWipingDatabase
                    ) {
                        if (isWipingDatabase) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = if (isArabic) {
                                    "⚠️ تفريغ كافة البيانات وإعادة بناء التطبيق من الصفر"
                                } else {
                                    "Wipe database & Seed pristine models"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Section: Active joins requests auditing
        item {
            Text(
                text = if (isArabic) "طلبات التوثيق قيد الانتظار (${pendingProviders.size})" else "Pending Join Applications (${pendingProviders.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (pendingProviders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isArabic) "لا توجد طلبات معلقة حالياً لحرفيين جدد 👍" else "No pending applications",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            items(pendingProviders) { item ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = item.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "${item.subCategory} - ${item.area}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(text = "📞 ${item.phone} | Detailed: ${item.address}", fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { FirebaseManager.approveProvider(item.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isArabic) "قبول وترقية" else "Approve", fontSize = 11.sp, color = Color.White)
                            }
                            Button(
                                onClick = { FirebaseManager.rejectProvider(item.id, "لم يستوف الشروط المهنية") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isArabic) "رفض" else "Reject", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Section: Audit activity security logs
        item {
            Text(
                text = if (isArabic) "سجل العمليات الإدارية والرقابة العامة" else "Moderator Security Audit Strings",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        if (activityLogs.isEmpty()) {
            item {
                Text(text = "سجلات الرقابة فارغة حالياً.", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            items(activityLogs.take(15)) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
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
                        Text(text = "الأخيرة", fontSize = 8.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
