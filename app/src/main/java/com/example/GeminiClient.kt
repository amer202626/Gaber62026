package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Offline standard Yemeni Services directory QA Map
    private val offlineQA = mapOf(
        "طوارئ" to "أرقام الطوارئ العاجلة في اليمن: الدفاع المدني (191)، الإسعاف الطبي (195)، طوارئ الكهرباء (151)، المساعد المباشر للدليل: 777644670",
        "اسعاف" to "رقم الإسعاف الطبي الطارئ في اليمن هو 195. للمشافي الخاصة بصنعاء: مستشفى المتحدون (01500500)، مستشفى العلوم والتكنولوجيا (01373229)",
        "شرطة" to "أرقام الأمن والسلامة: شرطة النجدة (193)، عمليات وزارة الداخلية (199)، الدفاع المدني (191)",
        "انترنت" to "للحصول على دعم فني يمن نت ADSL اتصل بـ (8000000) أو اتصل بمندوبي صيانة الشبكات المسجلين بقسم 'صيانة حاسوب وهواتف' بالدليل",
        "كهرباء" to "طوارئ الكهرباء العامة: (151). إن كنت تبحث عن صيانة خطوط الطاقة الخاصة أو تركيب منظومات طاقة شمسية، يرجى مراجعة قسم 'صيانة وأعمال مهنية' في القائمة الرئيسية",
        "يماني" to "الدكتور أحمد اليماني مسجل لدينا في قسم 'خدمات طبية ورعاية' ورقم هاتف عيادته: 736462000 في شارع حدة صنعاء",
        "ماهر" to "المعلم ماهر الخولاني مسجل بقسم 'صيانة وأعمال مهنية' ومتخصص في السباكة والكهرباء المنزلية ورقمه المباشر هو 777644670",
        "نقل" to "شركة الماهر للنقل السريع متواجدة بالدليل لقسم 'نقل وتوصيل'. متاح شحن لجميع المحافظات وهاتفهم: 775556667"
    )

    suspend fun getAssistantResponse(prompt: String, customApiKey: String = ""): String = withContext(Dispatchers.IO) {
        val cleaned = prompt.trim()
        
        // 1. Check Offline Lookup first
        for ((keyword, reply) in offlineQA) {
            if (cleaned.contains(keyword, ignoreCase = true)) {
                return@withContext "🤖 [رد تلقائي أوفلاين]: $reply"
            }
        }

        // 2. Online processing with Gemini
        val apiKey = customApiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }.trim()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            return@withContext "⚠️ [استجابة محدودة أوفلاين]: لا تتوفر تصفح متصل بالإنترنت حالياً (مفتاح API غير متوفر). تفضل بمطابقة الكلمات الدلالية مثل: طوارئ، اسعاف، كهرباء، انترنت، ماهر، يماني."
        }

        try {
            val url = "$BASE_URL?key=$apiKey"
            
            // Crafting complete system instruction as a contextual anchor
            val systemContext = "أنت 'أبو يمن الذكي' - المساعد الذكي الوطني والمسؤول عن إرشاد المواطنين في دليل خدمات اليمن المتكامل. قم بالإجابة على استفسارات المستخدمين بشكل مفيد ومختصر وودود بالعامية اليمنية الدافئة واللبقة بلهجة صنعانية أو بروح وطنية. إن طلب كهرباء، طبيب، مهندس، أو سباك، أرشده للأقسام المناسبة في تطبيقنا، وتجنب الهلوسة."

            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArr = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", partsArr)
                    }
                    put(contentObj)
                }
                put("contents", contentsArr)
                
                // Add system directives
                val sysInstObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemContext) })
                    }
                    put("parts", partsArr)
                }
                put("systemInstruction", sysInstObj)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
                "لم أستطع معالجة السؤال حالياً، الرجاء المحاولة بكلمات بديلة."
            } else {
                Log.e(TAG, "Gemini call failed: Code ${response.code} ${response.message}")
                "عذراً، تعذر الاتصال بخوادم الذكاء الاصطناعي (رمز الخطأ: ${response.code}). لتوفير الاستهلاك جرب كلمات: طوارئ، كهرباء، طبيب."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Exception: ${e.message}")
            "حدث خطأ في الاتصال حالياً. تأكد من أنك لست في وضع توفير البيانات المفعل! الخطأ: ${e.localizedMessage}"
        }
    }
}
