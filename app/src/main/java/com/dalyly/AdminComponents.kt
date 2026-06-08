package com.dalyly

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

// ======================== SECURITY SUPERVISOR PANEL ========================

// Package level helpers for professional PDF/CSV exports
fun exportReportsToPdf(context: Context, reports: List<IncidentReport>) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()

        // Page title
        paint.textSize = 18f
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        canvas.drawText("دليل الخدمات اليمني الموحد - تقرير البلاغات الأسبوعي", 40f, 60f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        canvas.drawText("تاريخ التصدير: ${dateFormat.format(java.util.Date())}", 40f, 85f, paint)
        canvas.drawText("إجمالي البلاغات المدرجة بالقائمة: ${reports.size}", 40f, 105f, paint)

        var y = 145f
        paint.strokeWidth = 1f
        canvas.drawLine(40f, y, 550f, y, paint)
        y += 20f

        paint.textSize = 10f
        for (rep in reports) {
            if (y > 800f) break
            canvas.drawText("الفني: ${rep.providerName} | الشاكي: ${rep.reporterName} | الحالة: ${rep.status}", 45f, y, paint)
            y += 18f
            canvas.drawText("التفاصيل: ${rep.complaintText.take(65)}...", 45f, y, paint)
            y += 22f
            canvas.drawLine(45f, y - 10f, 540f, y - 10f, paint)
        }

        pdfDocument.finishPage(page)
        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Weekly_Report_${System.currentTimeMillis()}.pdf")
        pdfDocument.writeTo(java.io.FileOutputStream(file))
        pdfDocument.close()
        Toast.makeText(context, "تم تصدير التقرير بنجاح:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "خطأ في تصدير PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun exportReportsToCsv(context: Context, reports: List<IncidentReport>) {
    try {
        val builder = java.lang.StringBuilder("ID,اسم الفني,الشاكي,التفاصيل,الحالة\n")
        reports.forEach { r ->
            builder.append("${r.id},${r.providerName},${r.reporterName},${r.complaintText.replace(",", " ")},${r.status}\n")
        }
        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Incident_Reports_${System.currentTimeMillis()}.csv")
        java.io.FileOutputStream(file).use { it.write(builder.toString().toByteArray()) }
        Toast.makeText(context, "تم تصدير CSV بنجاح:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "خطأ في تصدير CSV", Toast.LENGTH_SHORT).show()
    }
}

fun exportChatsToCsv(context: Context, chats: List<ChatMessage>) {
    try {
        val builder = java.lang.StringBuilder("اسم المرسل,نص الرسالة,التصنيف\n")
        chats.forEach { c ->
            builder.append("${c.senderName},${c.messageText.replace(",", " ")},${if (c.isFromAdmin) "مشرف" else "عميل"}\n")
        }
        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Chats_Backup_${System.currentTimeMillis()}.csv")
        java.io.FileOutputStream(file).use { it.write(builder.toString().toByteArray()) }
        Toast.makeText(context, "تم تصدير ملف دردشة CSV:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "خطأ في تصدير المحادثات", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardView(
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
    val isOwnerOrMain = loggedUser == "المالك السري (Owner)" || loggedUser == "WAM2026"
    val supervisorObj = supervisors.find { it.username == loggedUser }

    val pendingProviders = providers.filter { it.isPending }
    val activeProviders = providers.filter { !it.isPending }

    // Dynamic tabs based on user capabilities
    val allowedTabs = remember(loggedUser, supervisors, pendingProviders.size, incidents.size, activeProviders.size) {
        val list = mutableListOf<Pair<String, String>>()
        if (isOwnerOrMain || supervisorObj?.canAcceptRequests == true) {
            list.add("PENDING" to "طلبات التسجيل المعلقة (${pendingProviders.size}) ⏳")
        }
        if (isOwnerOrMain || supervisorObj?.canManageCategories == true) {
            list.add("ADD_MANUAL" to "تسجيل وتعديل فني ✍️")
        }
        if (isOwnerOrMain || supervisorObj?.canManageBanners == true) {
            list.add("BANNERS" to "الإعلانات والافتات 📢")
        }
        if (isOwnerOrMain || supervisorObj?.canManageCategories == true) {
            list.add("CATEGORIES_CITIES" to "الأقسام والمدن 🏛️")
        }
        if (isOwnerOrMain || supervisorObj?.canSeeReports == true) {
            list.add("REPORTS" to "البلاغات والتقارير (${incidents.size}) 🚨")
        }
        if (isOwnerOrMain) {
            list.add("CHATS" to "مراقبة سجل الدردشة 💬")
        }
        if (isOwnerOrMain || supervisorObj?.canDeleteProviders == true) {
            list.add("ACTIVE_PROVIDERS" to "المزودين النشطين (${activeProviders.size}) 👥")
        }
        if (isOwnerOrMain || supervisorObj?.canDeleteProviders == true) {
            list.add("SUBSCRIPTIONS" to "الاشتراكات والتثبيت 👑")
        }
        if (isOwnerOrMain) {
            list.add("SUPERVISORS" to "إدارة المشرفين 🛡️")
            list.add("CONFIGS" to "ثيم الألوان والهوية 🎨")
        }
        list
    }

    var selectedDashboardTab by remember { mutableStateOf("PENDING") }

    LaunchedEffect(allowedTabs) {
        if (allowedTabs.isNotEmpty() && !allowedTabs.any { it.first == selectedDashboardTab }) {
            selectedDashboardTab = allowedTabs.first().first
        }
    }

    // STATE VARIABLES
    // Tab 2: Manual add/editing states
    var editingId by remember { mutableStateOf<String?>(null) }
    var mName by remember { mutableStateOf("") }
    var mPhone by remember { mutableStateOf("") }
    var mWhatsapp by remember { mutableStateOf("") }
    var mCategory by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var mSub by remember { mutableStateOf("") }
    var mAddress by remember { mutableStateOf("") }
    var mArea by remember { mutableStateOf("صنعاء") }
    var mInspectionPrice by remember { mutableStateOf("1500") }
    var mIsVipBadge by remember { mutableStateOf(false) }
    var mIsPinned by remember { mutableStateOf(false) }
    var mIsVerified by remember { mutableStateOf(false) }
    var mGender by remember { mutableStateOf("Male") }
    var mPhotoUrl by remember { mutableStateOf("") }
    var mSelfieUrl by remember { mutableStateOf("") }

    // Tab 3: Banners states
    var newBannerTitle by remember { mutableStateOf("") }
    var newBannerUrl by remember { mutableStateOf("") }
    var newBannerLink by remember { mutableStateOf("") }
    var newBannerDuration by remember { mutableStateOf("5") }
    var newBannerType by remember { mutableStateOf("Image Background") }
    var newBannerSize by remember { mutableStateOf("M") }
    var newBannerCatTarget by remember { mutableStateOf("") }

    // Tab 4: Categories/Cities states
    var newCatAr by remember { mutableStateOf("") }
    var newCatEn by remember { mutableStateOf("") }
    var newCatEmoji by remember { mutableStateOf("🔧") }
    var newCatDescription by remember { mutableStateOf("") }
    var isCatPinned by remember { mutableStateOf(false) }
    var isCatPublishImmediately by remember { mutableStateOf(true) }

    var newCityAr by remember { mutableStateOf("") }
    var newCityEn by remember { mutableStateOf("") }

    // Tab 9: Supervisors states
    var newSuperUser by remember { mutableStateOf("") }
    var newSuperPass by remember { mutableStateOf("") }
    var pAcceptRequests by remember { mutableStateOf(true) }
    var pManageCategories by remember { mutableStateOf(true) }
    var pManageBanners by remember { mutableStateOf(true) }
    var pDeleteProviders by remember { mutableStateOf(true) }
    var pSeeReports by remember { mutableStateOf(true) }

    // Reject Dialog states
    var showRejectReasonDialogByProviderId by remember { mutableStateOf<String?>(null) }
    var rejectReasonInput by remember { mutableStateOf("") }

    // Tab 10: Colors/Parameters states
    var configThemeType by remember { mutableStateOf(config.themeType) }
    var configAppName by remember { mutableStateOf(config.appName) }
    var configLogoUrl by remember { mutableStateOf(config.logoUrl) }
    var configPrimaryColor by remember { mutableStateOf(config.primaryColorHex) }
    var configSecondaryColor by remember { mutableStateOf(config.secondaryColorHex) }
    var configTermsByAdmin by remember { mutableStateOf(config.registrationTerms) }
    var configFooterText by remember { mutableStateOf(config.footerText) }
    var configChatDisabled by remember { mutableStateOf(config.chatDisabled) }
    var configChatWarningMsg by remember { mutableStateOf(config.chatDisabledMessage) }
    var configChatIconSize by remember { mutableStateOf(config.chatIconSize.toFloat()) }
    var configAiIconSize by remember { mutableStateOf(config.aiAssistantSize.toFloat()) }
    var configSearchRadius by remember { mutableStateOf(config.searchRadiusKm.toString()) }
    var configVoiceSpeechEnabled by remember { mutableStateOf(config.voiceSearchEnabled) }
    var configRetentionDays by remember { mutableStateOf(config.retentionDays.toString()) }

    var adminChatReplyDraft by remember { mutableStateOf("") }
    var providersSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(config) {
        configThemeType = config.themeType
        configAppName = config.appName
        configLogoUrl = config.logoUrl
        configPrimaryColor = config.primaryColorHex
        configSecondaryColor = config.secondaryColorHex
        configTermsByAdmin = config.registrationTerms
        configFooterText = config.footerText
        configChatDisabled = config.chatDisabled
        configChatWarningMsg = config.chatDisabledMessage
        configChatIconSize = config.chatIconSize.toFloat()
        configAiIconSize = config.aiAssistantSize.toFloat()
        configSearchRadius = config.searchRadiusKm.toString()
        configVoiceSpeechEnabled = config.voiceSearchEnabled
        configRetentionDays = config.retentionDays.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Welcome Session Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "🛡️ الإدارة العليا: $loggedUser", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Text(text = "لوحة ترخيص يمنية فورية نشطة", fontSize = 10.sp, color = Color.LightGray)
                }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("خروج ⚠️", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // Dashboard selectors
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allowedTabs) { (key, title) ->
                val isActive = selectedDashboardTab == key
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isActive) MaterialTheme.colorScheme.primary else Color.DarkGray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedDashboardTab = key }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isActive) Color.Black else Color.White)
                }
            }
        }

        Divider(color = Color.Gray.copy(alpha = 0.2f))

        // TAB ACTIONS
        when (selectedDashboardTab) {
            "PENDING" -> {
                Text("طلبات مقدمي الخدمات وصيانة البرمجيات المعلقة ⏳", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (pendingProviders.isEmpty()) {
                    Text("لا توجد طلبات انضمام فنية قيد المراجعة حالياً.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    pendingProviders.forEach { pending ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = BorderStroke(1.dp, Color.Yellow)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("الاسم: ${pending.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("الجنس: ${if (pending.gender == "Female") "أنثى 👩‍🔧" else "ذكر 👤"}", fontSize = 12.sp)
                                Text("رقم الاتصال: ${pending.phone} | واتساب: ${pending.whatsapp}", fontSize = 12.sp)
                                Text("التخصص الدقيق: ${pending.subCategory}", fontSize = 12.sp)
                                Text("المنطقة/المدينة: ${pending.area} | تفصيل السكن: ${pending.address}", fontSize = 12.sp)

                                // Photo and Selfie displays
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (pending.photoUrl.isNotEmpty()) {
                                        Column {
                                            Text("شعار العمل / المهنة:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            AsyncImage(
                                                model = pending.photoUrl,
                                                contentDescription = "Work Photo",
                                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    if (pending.selfieUrl.isNotEmpty()) {
                                        Column {
                                            Text("صورة السيلفي المطابقة:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            AsyncImage(
                                                model = pending.selfieUrl,
                                                contentDescription = "Selfie Photo",
                                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val approved = pending.copy(isPending = false, active = true)
                                            FirebaseManager.saveProvider(approved) {
                                                Toast.makeText(context, "تم قبول وترخيص الفني بنجاح ونشر حسابه!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("قبول وترخيص ✅", fontSize = 11.sp, color = Color.Black)
                                    }
                                    Button(
                                        onClick = { showRejectReasonDialogByProviderId = pending.id },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("رفض ومسح الطلب ❌", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "ADD_MANUAL" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (editingId == null) "تسجيل وترخيص فني جديد يدوياً بالدليل المباشر ✍️" else "تعديل وتعديل تفاصيل الفني النشط 🔄",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextField(value = mName, onValueChange = { mName = it }, label = { Text("الاسم الكامل") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = mPhone, onValueChange = { mPhone = it }, label = { Text("رقم جوال الاتصال باليمن") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = mWhatsapp, onValueChange = { mWhatsapp = it }, label = { Text("رقم حساب الواتساب") }, modifier = Modifier.fillMaxWidth())

                    Text("اختر القسم المهني السحابي للخدمة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { mCategory = cat.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mCategory == cat.id, onClick = { mCategory = cat.id })
                            Text(text = "${cat.emoji} ${cat.nameAr}", fontSize = 12.sp)
                        }
                    }

                    TextField(value = mSub, onValueChange = { mSub = it }, label = { Text("تفصيل تخصص العمل الدقيق") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = mAddress, onValueChange = { mAddress = it }, label = { Text("تفصيل السكن والعنوان") }, modifier = Modifier.fillMaxWidth())

                    Text("محافظة مزاولة المهنة المحددة:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        cities.forEach { city ->
                            Box(
                                modifier = Modifier
                                    .background(if (mArea == city) MaterialTheme.colorScheme.primary else Color.DarkGray, RoundedCornerShape(6.dp))
                                    .clickable { mArea = city }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = city, fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }

                    TextField(value = mInspectionPrice, onValueChange = { mInspectionPrice = it }, label = { Text("تكلفة المعاينة والفحص (بالريال)") }, modifier = Modifier.fillMaxWidth())

                    // Gender Selector
                    Text("الجنس والخصوصية:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mGender == "Male", onClick = { mGender = "Male" })
                            Text("ذكر 👤")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mGender == "Female", onClick = { mGender = "Female" })
                            Text("أنثى 👩‍🔧")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mIsVipBadge, onCheckedChange = { mIsVipBadge = it })
                        Text("عضوية ذهبية مميزة VIP 👑", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mIsPinned, onCheckedChange = { mIsPinned = it })
                        Text("تثبيت بصدارة نتائج البحث 📌", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mIsVerified, onCheckedChange = { mIsVerified = it })
                        Text("منح شارة المعتمد الأزرق Verified 💎", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingId != null) {
                            Button(
                                onClick = {
                                    editingId = null
                                    mName = ""
                                    mPhone = ""
                                    mWhatsapp = ""
                                    mSub = ""
                                    mAddress = ""
                                    mInspectionPrice = "1500"
                                    mIsVipBadge = false
                                    mIsPinned = false
                                    mIsVerified = false
                                    mPhotoUrl = ""
                                    mSelfieUrl = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلغاء التعديل", color = Color.Black)
                            }
                        }

                        Button(
                            onClick = {
                                if (mName.isEmpty() || mPhone.isEmpty()) {
                                    Toast.makeText(context, "الرجاء اكمال التفاصيل الحساسة", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val p = ServiceProvider(
                                    id = editingId ?: "",
                                    name = mName,
                                    phone = mPhone,
                                    whatsapp = mWhatsapp,
                                    categoryId = mCategory,
                                    subCategory = mSub,
                                    address = mAddress,
                                    area = mArea,
                                    inspectionPrice = mInspectionPrice,
                                    isVip = mIsVipBadge,
                                    isPinned = mIsPinned,
                                    isVerified = mIsVerified,
                                    gender = mGender,
                                    photoUrl = mPhotoUrl,
                                    selfieUrl = mSelfieUrl,
                                    active = true,
                                    isPending = false
                                )
                                FirebaseManager.saveProvider(p) {
                                    Toast.makeText(context, "تم حفظ بيانات الفني بالدليل السحابي!", Toast.LENGTH_SHORT).show()
                                    editingId = null
                                    mName = ""
                                    mPhone = ""
                                    mWhatsapp = ""
                                    mSub = ""
                                    mAddress = ""
                                    mInspectionPrice = "1500"
                                    mIsVipBadge = false
                                    mIsPinned = false
                                    mIsVerified = false
                                    mPhotoUrl = ""
                                    mSelfieUrl = ""
                                }
                            },
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text(if (editingId == null) "حفظ وإضافة الفني مباشرة 💾" else "تحديث بيانات الفني بنجاح  💾")
                        }
                    }

                    // Technician List for selection / modification
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("اختر فني صيانة حالي لتعديل بياناته بالدليل:", fontWeight = FontWeight.Bold)
                    providers.forEach { prov ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${prov.name} | ${prov.area}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("الجوال: ${prov.phone} | التخصص: ${prov.subCategory}", fontSize = 11.sp, color = Color.LightGray)
                                }
                                Row {
                                    IconButton(onClick = {
                                        editingId = prov.id
                                        mName = prov.name
                                        mPhone = prov.phone
                                        mWhatsapp = prov.whatsapp
                                        mCategory = prov.categoryId
                                        mSub = prov.subCategory
                                        mAddress = prov.address
                                        mArea = prov.area
                                        mInspectionPrice = prov.inspectionPrice
                                        mIsVipBadge = prov.isVip
                                        mIsPinned = prov.isPinned
                                        mIsVerified = prov.isVerified
                                        mGender = prov.gender
                                        mPhotoUrl = prov.photoUrl
                                        mSelfieUrl = prov.selfieUrl
                                        Toast.makeText(context, "تم تحميل الفني ${prov.name} للتعديل!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "BANNERS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إدارة الافتات الإعلانية ومؤقتات العرض 📢:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextField(value = newBannerTitle, onValueChange = { newBannerTitle = it }, label = { Text("عنوان الإعلان") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newBannerUrl, onValueChange = { newBannerUrl = it }, label = { Text("رابط صورة الخلفية (Direct Image URL)") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newBannerLink, onValueChange = { newBannerLink = it }, label = { Text("رابط التوجيه عند النقر (Redirect URL)") }, modifier = Modifier.fillMaxWidth())

                    Text("توجيه عند النقر لقسم مهني معين (تجاوز الرابط الخارجي):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { newBannerCatTarget = cat.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = newBannerCatTarget == cat.id, onClick = { newBannerCatTarget = cat.id })
                            Text("${cat.emoji} ${cat.nameAr}", fontSize = 12.sp)
                        }
                    }

                    TextField(value = newBannerDuration, onValueChange = { newBannerDuration = it }, label = { Text("مدة بقاء الإعلان بالثواني (Lock Seconds)") }, modifier = Modifier.fillMaxWidth())

                    Text("حدد طبيعة تصميم الإعلان:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val adTypes = listOf("Image Background", "Video Background", "Promotional Text")
                    adTypes.forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { newBannerType = t }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = newBannerType == t, onClick = { newBannerType = t })
                            Text(t, fontSize = 12.sp)
                        }
                    }

                    Text("حجم عرض البانر بالدليل الرئيسي:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val adSizes = listOf("S", "M", "L")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        adSizes.forEach { sz ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = newBannerSize == sz, onClick = { newBannerSize = sz })
                                Text(sz)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (newBannerTitle.isEmpty() || newBannerUrl.isEmpty()) {
                                Toast.makeText(context, "الرجاء ملء حقول الإعلان الأساسية", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val b = BannerAd(
                                title = newBannerTitle,
                                imageUrl = newBannerUrl,
                                linkUrl = newBannerLink,
                                displaySize = newBannerSize,
                                type = newBannerType,
                                duration = newBannerDuration.toFloatOrNull() ?: 5f,
                                categoryId = newBannerCatTarget
                            )
                            FirebaseManager.saveBanner(b) {
                                Toast.makeText(context, "تم تخزين الإعلان ونشره بنجاح!", Toast.LENGTH_SHORT).show()
                                newBannerTitle = ""
                                newBannerUrl = ""
                                newBannerLink = ""
                                newBannerCatTarget = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إرسال وبث الإعلان الجديد 🚀")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("الإعلانات المشغلة حالياً:")
                    banners.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.DarkGray, RoundedCornerShape(8.dp)).padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = b.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = {
                                FirebaseManager.deleteBanner(b.id) {
                                    Toast.makeText(context, "تم إلغاء وسحب البانر بنجاح", Toast.LENGTH_SHORT).show()
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
                    Text("إدارة وتصنيف أقسام دليل دليلي 🏛️:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextField(value = newCatAr, onValueChange = { newCatAr = it }, label = { Text("الاسم بالعربية") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newCatEn, onValueChange = { newCatEn = it }, label = { Text("الاسم بالإنجليزية") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newCatEmoji, onValueChange = { newCatEmoji = it }, label = { Text("رمز التعبير للقسم (Emoji)") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newCatDescription, onValueChange = { newCatDescription = it }, label = { Text("توصيف تفصيلي عام للقسم") }, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCatPinned, onCheckedChange = { isCatPinned = it })
                        Text("تثبيت وتصدر القسم لأعلى الصفحة 📌", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCatPublishImmediately, onCheckedChange = { isCatPublishImmediately = it })
                        Text("نشر وتفعيل حسابات هذا القسم فوريًا بدون مراجعة مشرف", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (newCatAr.isEmpty() || newCatEmoji.isEmpty()) {
                                Toast.makeText(context, "يرجى كتابة الاسم والرمز التعبيري", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val cat = ServiceCategory(
                                nameAr = newCatAr,
                                nameEn = newCatEn,
                                emoji = newCatEmoji,
                                description = newCatDescription,
                                isPinned = isCatPinned,
                                publishImmediately = isCatPublishImmediately,
                                displayOrder = categories.size
                            )
                            FirebaseManager.saveCategory(cat) {
                                Toast.makeText(context, "تم حفظ وتحديث تصنيف الخدمات السحابية!", Toast.LENGTH_SHORT).show()
                                newCatAr = ""
                                newCatEn = ""
                                newCatEmoji = "🔧"
                                newCatDescription = ""
                                isCatPinned = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إرجاع وتحديث تصنيف الخدمات السحابية 💾")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("إدارة المدن والمحافظات اليمنية السحابية 🗺️:", fontWeight = FontWeight.Bold)
                    TextField(value = newCityAr, onValueChange = { newCityAr = it }, label = { Text("اسم المدينة بالعربية") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (newCityAr.trim().isEmpty()) return@Button
                            FirebaseManager.saveCity(newCityAr.trim(), newCityAr.trim()) {
                                Toast.makeText(context, "تم إضافة المحافظة ${newCityAr} وتحديث الدستور السحابي!", Toast.LENGTH_SHORT).show()
                                newCityAr = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إدراج المدينة بالدليل 🗺️")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("قائمة المدن المسجلة بالدليل السحابي:")
                    cities.forEach { city ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🇾🇪 $city", fontSize = 12.sp, color = Color.White)
                            IconButton(onClick = {
                                FirebaseManager.deleteCity(city) {
                                    Toast.makeText(context, "تم إزالة المحافظة بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            "REPORTS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("بلاغات وشكاوى سوء المعاملة أو غلاء الأسعار المرفوعة من المواطنين 🚨:", fontWeight = FontWeight.Bold, color = Color.Red)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportReportsToPdf(context, incidents) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تصدير تقرير PDF أسبوعي 📄", fontSize = 10.sp, color = Color.White)
                        }
                        Button(
                            onClick = { exportReportsToCsv(context, incidents) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تصدير جدول CSV 📊", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    if (incidents.isEmpty()) {
                        Text("لا توجد بلاغات فنية مرفوعة حالياً.", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        incidents.forEach { inc ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "🚨 البلاغ ضد الفني: ${inc.providerName}", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 12.sp)
                                    Text(text = "الشاكي: ${inc.reporterName}", fontSize = 11.sp)
                                    Text(text = "مضمون البلاغ: ${inc.complaintText}", fontSize = 11.sp, color = Color.LightGray)
                                    Text(text = "الحالة الراهنة: ${inc.status}", fontSize = 10.sp, color = Color.Yellow)

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val resolved = inc.copy(status = "RESOLVED")
                                                FirebaseManager.saveIncident(resolved) {
                                                    Toast.makeText(context, "تم حسم المشكلة ودوّنت مستوفية!", Toast.LENGTH_SHORT).show()
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
                                                    Toast.makeText(context, "تم حذف سجل البلاغ وتطهيره!", Toast.LENGTH_SHORT).show()
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
            }

            "CHATS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تدوير وإدارة سجلات المحادثات العامة والخصوصية والأمان 💬:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // Announcement broadcast
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = adminChatReplyDraft,
                            onValueChange = { adminChatReplyDraft = it },
                            placeholder = { Text("اكتب رسالة بث موحدة لكل المشتركين هنا...") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (adminChatReplyDraft.isNotBlank()) {
                                    val msg = ChatMessage(senderName = "المدير السحابي المعتمد 🛡️", messageText = adminChatReplyDraft, isFromAdmin = true)
                                    FirebaseManager.sendChatMessage(msg) {
                                        adminChatReplyDraft = ""
                                        Toast.makeText(context, "تم بث نص الإدارة الموحد بنجاح!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("بث إعلان")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { exportChatsToCsv(context, chats) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("تصدير سجل الدردشة كـ CSV 📊", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                FirebaseManager.clearChats {
                                    Toast.makeText(context, "تم تطهير وحذف سجل الدردشات للأبد!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("جرف وتفريغ السجل بالكامل 🆑", fontSize = 10.sp)
                        }
                    }

                    chats.forEach { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (chat.isFromAdmin) Color.DarkGray else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                text = chat.senderName
                                Text(chat.senderName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(chat.messageText, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            "ACTIVE_PROVIDERS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("مراقبة وتنقية المزودين المعتمدين النشطين بالدليل 👥:", fontWeight = FontWeight.Bold)
                    TextField(
                        value = providersSearchQuery,
                        onValueChange = { providersSearchQuery = it },
                        placeholder = { Text("ابحث باسم الفني أو مجال العمل للسرعة...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val filteredActive = activeProviders.filter {
                        it.name.contains(providersSearchQuery, ignoreCase = true) ||
                        it.subCategory.contains(providersSearchQuery, ignoreCase = true)
                    }

                    filteredActive.forEach { act ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(act.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (act.isVerified) {
                                            Text(" 💎 (Verified)", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Text("الجوال: ${act.phone} | الوظيفة: ${act.subCategory}", fontSize = 11.sp, color = Color.LightGray)
                                    Text("المنطقة: ${act.area} | الفحص: ${act.inspectionPrice} ريال", fontSize = 11.sp)
                                }
                                IconButton(onClick = {
                                    FirebaseManager.deleteProvider(act.id) {
                                        Toast.makeText(context, "تم إلغاء ترخيص الفني وإزالته من الدليل السحابي!", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }

            "SUBSCRIPTIONS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تثبيت مقدمي الخدمة وضبط العضوية المميزة 👑:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    activeProviders.forEach { act ->
                        var isVipLocal by remember(act.isVip) { mutableStateOf(act.isVip) }
                        var isPinnedLocal by remember(act.isPinned) { mutableStateOf(act.isPinned) }
                        var isVerifiedLocal by remember(act.isVerified) { mutableStateOf(act.isVerified) }
                        var showMediaLocal by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            border = BorderStroke(1.dp, if (isVipLocal) Color.Yellow else Color.DarkGray)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(text = act.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = "التخصص: ${act.subCategory} | هاتف: ${act.phone}", fontSize = 11.sp, color = Color.LightGray)
                                    }
                                    Button(
                                        onClick = { showMediaLocal = !showMediaLocal },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("معاينة الصور 👁️", fontSize = 9.sp)
                                    }
                                }

                                if (showMediaLocal) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f)).padding(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (act.photoUrl.isEmpty() && act.selfieUrl.isEmpty()) {
                                            Text("لا تتوفر أي وثائق مصورة للفني بالخادم حالياً", fontSize = 11.sp, color = Color.Gray)
                                        } else {
                                            if (act.photoUrl.isNotEmpty()) {
                                                Column {
                                                    Text("الهوية المهنية / الرمز:", fontSize = 9.sp, color = Color.White)
                                                    AsyncImage(
                                                        model = act.photoUrl,
                                                        contentDescription = "PFile",
                                                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray)
                                                    )
                                                }
                                            }
                                            if (act.selfieUrl.isNotEmpty()) {
                                                Column {
                                                    Text("الصورة الذاتية السيلفي:", fontSize = 9.sp, color = Color.White)
                                                    AsyncImage(
                                                        model = act.selfieUrl,
                                                        contentDescription = "SFile",
                                                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(4.dp)).background(Color.DarkGray)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(checked = isVipLocal, onCheckedChange = {
                                            isVipLocal = it
                                            val updated = act.copy(isVip = it)
                                            FirebaseManager.saveProvider(updated) {
                                                Toast.makeText(context, "شارة VIP الموثقة تم تحديثها!", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                        Text("عضوية VIP 👑", fontSize = 10.sp)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(checked = isPinnedLocal, onCheckedChange = {
                                            isPinnedLocal = it
                                            val updated = act.copy(isPinned = it)
                                            FirebaseManager.saveProvider(updated) {
                                                Toast.makeText(context, "تحديث حالة التثبيت بصدارة البحث 📌!", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                        Text("التثبيت 📌", fontSize = 10.sp)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.1f)) {
                                        Checkbox(checked = isVerifiedLocal, onCheckedChange = {
                                            isVerifiedLocal = it
                                            val updated = act.copy(isVerified = it)
                                            FirebaseManager.saveProvider(updated) {
                                                Toast.makeText(context, "تحديث رتبة التوثيق المعتمد 💎!", Toast.LENGTH_SHORT).show()
                                            }
                                        })
                                        Text("توثيق مالي 💎", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "SUPERVISORS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("👥 إدارة وإضافة المراقبين وحسابات المشرفين باليمن:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextField(value = newSuperUser, onValueChange = { newSuperUser = it }, label = { Text("اسم مستخدم المشرف الحركي") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = newSuperPass, onValueChange = { newSuperPass = it }, label = { Text("كلمة سر للمرور والتعديل") }, modifier = Modifier.fillMaxWidth())

                    Text("حدد الصلاحيات والامتيازات المرخصة (Permissions Checkbox):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pAcceptRequests, onCheckedChange = { pAcceptRequests = it })
                        Text("مراجعة وترخيص طلبات تسجيل الفنيين المعلقة", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pManageCategories, onCheckedChange = { pManageCategories = it })
                        Text("إدراج وإزالة الأقسام والمدن والمحافظات", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pManageBanners, onCheckedChange = { pManageBanners = it })
                        Text("توليف وإدارة البانرات والافتات الإعلانية الموقتة", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pDeleteProviders, onCheckedChange = { pDeleteProviders = it })
                        Text("سحب تراخيض وحذف وإقصاء المزودين النشطين", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pSeeReports, onCheckedChange = { pSeeReports = it })
                        Text("مطالعة بلاغات المواطنين وتصدير التقارير أسبوعية", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (newSuperUser.trim().isEmpty() || newSuperPass.trim().isEmpty()) {
                                Toast.makeText(context, "الرجاء اكمال التفاصيل الأساسية للمراقب", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val mod = Moderator(
                                username = newSuperUser.trim(),
                                password = newSuperPass.trim(),
                                canAcceptRequests = pAcceptRequests,
                                canManageCategories = pManageCategories,
                                canManageBanners = pManageBanners,
                                canDeleteProviders = pDeleteProviders,
                                canSeeReports = pSeeReports
                            )
                            FirebaseManager.saveSupervisor(mod) {
                                Toast.makeText(context, "تم تسجيل وترخيص المراقب المساعد بالخادم!", Toast.LENGTH_SHORT).show()
                                newSuperUser = ""
                                newSuperPass = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تسجيل وترخيص المراقب المساعد 🛡️")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("المشرفون وحسابات الرقابة المرخصة حالياً:")
                    supervisors.forEach { sup ->
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "🛡️ ${sup.username} | الرمز: ${sup.password}", fontSize = 12.sp, color = Color.White)
                                Text(text = "قبول: ${sup.canAcceptRequests} | أقسام: ${sup.canManageCategories} | بنرات: ${sup.canManageBanners} | حذف: ${sup.canDeleteProviders}", fontSize = 9.sp, color = Color.LightGray)
                            }
                            IconButton(onClick = {
                                FirebaseManager.deleteSupervisor(sup.id) {
                                    Toast.makeText(context, "تم سحب تصريح وإقصاء المشرف", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            "CONFIGS" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("لوحة تعقيب وضبط الألوان والهوية الترابطية والسلوك 🎨:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    TextField(value = configAppName, onValueChange = { configAppName = it }, label = { Text("اسم التطبيق المعتمد بالواجهة") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = configLogoUrl, onValueChange = { configLogoUrl = it }, label = { Text("رابط الشعار السحابي الموحد") }, modifier = Modifier.fillMaxWidth())

                    Text("اختر ثيم لوحة ألوان المحتوى:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val themes = listOf("Classic Dark", "Yemen Red", "Yemen Golden", "Ocean Blue")
                    themes.forEach { th ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { configThemeType = th }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = configThemeType == th, onClick = { configThemeType = th })
                            Text(text = th, fontSize = 12.sp)
                        }
                    }

                    if (configThemeType == "Yemen Golden" || configThemeType == "Custom") {
                        TextField(value = configPrimaryColor, onValueChange = { configPrimaryColor = it }, label = { Text("كود هيكس اللون الأساسي (e.g. #D4AF37)") }, modifier = Modifier.fillMaxWidth())
                        TextField(value = configSecondaryColor, onValueChange = { configSecondaryColor = it }, label = { Text("كود هيكس اللون الثانوي (e.g. #111111)") }, modifier = Modifier.fillMaxWidth())
                    }

                    TextField(value = configTermsByAdmin, onValueChange = { configTermsByAdmin = it }, label = { Text("شروط وضوابط التسجيل الفني") }, modifier = Modifier.fillMaxWidth())
                    TextField(value = configFooterText, onValueChange = { configFooterText = it }, label = { Text("نص تذييل الصفحة وحفظ الحقوق") }, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = configChatDisabled, onCheckedChange = { configChatDisabled = it })
                        Text("إيقاف وتعطيل غرفة شات الدعم المباشر مؤقتًا 📴", fontSize = 12.sp)
                    }

                    if (configChatDisabled) {
                        TextField(value = configChatWarningMsg, onValueChange = { configChatWarningMsg = it }, label = { Text("رسالة تعطيل الشات المعروضة للمواطن") }, modifier = Modifier.fillMaxWidth())
                    }

                    // Floating widget sliders
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("أداة التحكم بقطر وحجم وسيلة الدعم العائمة 💬 (dp):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Slider(value = configChatIconSize, onValueChange = { configChatIconSize = it }, valueRange = 40f..90f, modifier = Modifier.fillMaxWidth())
                    Text("الحجم المحدد: ${configChatIconSize.toInt()} dp", fontSize = 10.sp)

                    Text("أداة التحكم بقطر وحجم أيقونة مساعد الذكاء الاصطناعي 🤖 (dp):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Slider(value = configAiIconSize, onValueChange = { configAiIconSize = it }, valueRange = 40f..90f, modifier = Modifier.fillMaxWidth())
                    Text("الحجم المحدد: ${configAiIconSize.toInt()} dp", fontSize = 10.sp)

                    // RADIUS, RETENTION, SPEECH
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("نطاق قطر البحث الجغرافي وخوارزمية الخدمة المجاورة (كم):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    TextField(value = configSearchRadius, onValueChange = { configSearchRadius = it }, label = { Text("مسافة القطر (بالكيلومتر)") }, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = configVoiceSpeechEnabled, onCheckedChange = { configVoiceSpeechEnabled = it })
                        Text("تشغيل خوارزمية البحث عبر الأوامر الصوتية بالواجهة 🎤", fontSize = 11.sp)
                    }

                    TextField(value = configRetentionDays, onValueChange = { configRetentionDays = it }, label = { Text("مدة تفريغ السجلات وحفظ الملفات المؤقتة تلقائياً (بالأيام)") }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val daysLimit = configRetentionDays.toIntOrNull() ?: 30
                            // Simulate database sweep cleanup
                            incidents.forEach { inc ->
                                if (inc.status == "RESOLVED") {
                                    FirebaseManager.deleteIncident(inc.id)
                                }
                            }
                            Toast.makeText(context, "تم تنظيف السيرفر وتنظيف الكاش وتطهير السجلات بنجاح!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تنظيف الكاش وقاعدة البيانات السلوكية 🧼", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (configAppName.trim().isEmpty()) {
                                Toast.makeText(context, "الرجاء تحديد اسماً صالحاً للتطبيق", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val updated = config.copy(
                                appName = configAppName.trim(),
                                logoUrl = configLogoUrl.trim(),
                                themeType = configThemeType,
                                primaryColorHex = configPrimaryColor,
                                secondaryColorHex = configSecondaryColor,
                                registrationTerms = configTermsByAdmin,
                                footerText = configFooterText,
                                chatDisabled = configChatDisabled,
                                chatDisabledMessage = configChatWarningMsg,
                                chatIconSize = configChatIconSize.toInt(),
                                aiAssistantSize = configAiIconSize.toInt(),
                                searchRadiusKm = configSearchRadius.toIntOrNull() ?: 10,
                                voiceSearchEnabled = configVoiceSpeechEnabled,
                                retentionDays = configRetentionDays.toIntOrNull() ?: 30
                            )
                            FirebaseManager.updateConfig(updated) {
                                Toast.makeText(context, "تم حفظ وضبط الدستور وتعميمه على كل المشتركين فوراً!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تثبيت وتحصين خيارات الهوية سحابياً 💾")
                    }
                }
            }
        }
    }

    // Rejection reason input popup dialog
    if (showRejectReasonDialogByProviderId != null) {
        Dialog(onDismissRequest = { 
            showRejectReasonDialogByProviderId = null 
            rejectReasonInput = ""
        }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تقديم سبب رفض طلب الترخيص ❌:", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 13.sp)
                    TextField(
                        value = rejectReasonInput,
                        onValueChange = { rejectReasonInput = it },
                        label = { Text("سبب الرفض والتدقيق") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(onClick = { 
                            showRejectReasonDialogByProviderId = null
                            rejectReasonInput = ""
                        }) {
                            Text("إلغاء")
                        }
                        Button(
                            onClick = {
                                val providerId = showRejectReasonDialogByProviderId!!
                                if (rejectReasonInput.trim().isEmpty()) {
                                    Toast.makeText(context, "اكتب سبب الرفض", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val p = providers.find { it.id == providerId }
                                if (p != null) {
                                    val updated = p.copy(isPending = false, active = false, rejectReason = rejectReasonInput)
                                    FirebaseManager.saveProvider(updated) {
                                        Toast.makeText(context, "تم تسجيل سبب الرفض ورفض الطلب بنجاح", Toast.LENGTH_SHORT).show()
                                        showRejectReasonDialogByProviderId = null
                                        rejectReasonInput = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("أكد تسجيل الرفض")
                        }
                    }
                }
            }
        }
    }
}
