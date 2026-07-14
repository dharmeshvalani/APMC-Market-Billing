package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class UserRole {
    OWNER,
    ADMIN,
    STAFF
}

data class UserSession(
    val email: String,
    val phone: String,
    val name: String,
    val role: UserRole,
    val token: String
)

class AuthService(private val context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("apmc_auth_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    // Configurable Supabase credentials so the user can connect their own database instance
    private val _supabaseUrl = MutableStateFlow(sharedPrefs.getString("supabase_url", "") ?: "")
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseAnonKey = MutableStateFlow(sharedPrefs.getString("supabase_anon_key", "") ?: "")
    val supabaseAnonKey: StateFlow<String> = _supabaseAnonKey.asStateFlow()

    init {
        loadSavedSession()
    }

    fun updateSupabaseConfig(url: String, anonKey: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val cleanKey = anonKey.trim()
        _supabaseUrl.value = cleanUrl
        _supabaseAnonKey.value = cleanKey
        sharedPrefs.edit()
            .putString("supabase_url", cleanUrl)
            .putString("supabase_anon_key", cleanKey)
            .apply()
    }

    private fun loadSavedSession() {
        val email = sharedPrefs.getString("session_email", null)
        val phone = sharedPrefs.getString("session_phone", null)
        val name = sharedPrefs.getString("session_name", null)
        val roleStr = sharedPrefs.getString("session_role", null)
        val token = sharedPrefs.getString("session_token", null)

        if (email != null && token != null) {
            val role = try {
                UserRole.valueOf(roleStr ?: "STAFF")
            } catch (e: Exception) {
                UserRole.STAFF
            }
            _currentUser.value = UserSession(
                email = email,
                phone = phone ?: "",
                name = name ?: "User",
                role = role,
                token = token
            )
        }
    }

    private fun saveSession(session: UserSession) {
        sharedPrefs.edit()
            .putString("session_email", session.email)
            .putString("session_phone", session.phone)
            .putString("session_name", session.name)
            .putString("session_role", session.role.name)
            .putString("session_token", session.token)
            .apply()
        _currentUser.value = session
    }

    fun logout() {
        sharedPrefs.edit()
            .remove("session_email")
            .remove("session_phone")
            .remove("session_name")
            .remove("session_role")
            .remove("session_token")
            .apply()
        _currentUser.value = null
    }

    // Sign up a new user with Supabase, attaching their Role and Name in custom user_metadata
    suspend fun signUp(email: String, password: String, name: String, phone: String, role: UserRole): Result<UserSession> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            // High-fidelity fallback/local simulation mode
            kotlinx.coroutines.delay(1000)
            val fakeToken = "local-simulated-token-" + System.currentTimeMillis()
            val session = UserSession(email, phone, name, role, fakeToken)
            saveSession(session)
            // Persist locally registered users to SharedPreferences so they can be logged back in
            sharedPrefs.edit()
                .putString("reg_pw_$email", password)
                .putString("reg_name_$email", name)
                .putString("reg_phone_$email", phone)
                .putString("reg_role_$email", role.name)
                .apply()
            return@withContext Result.success(session)
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val metadata = JSONObject().apply {
                put("name", name)
                put("role", role.name)
                put("phone", phone)
            }
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("phone", phone.ifBlank { null })
                put("data", metadata)
            }

            val request = Request.Builder()
                .url("$url/auth/v1/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(json.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(bodyString).getString("msg")
                    } catch (e: Exception) {
                        "Signup failed: ${response.code}"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonResponse = JSONObject(bodyString)
                val token = jsonResponse.optString("access_token", "dummy-token")
                val userObj = jsonResponse.getJSONObject("user")
                val userMetadata = userObj.optJSONObject("user_metadata")
                
                val finalName = userMetadata?.optString("name", name) ?: name
                val finalRoleStr = userMetadata?.optString("role", role.name) ?: role.name
                val finalRole = try { UserRole.valueOf(finalRoleStr) } catch (e: Exception) { role }

                val session = UserSession(
                    email = email,
                    phone = phone,
                    name = finalName,
                    role = finalRole,
                    token = token
                )
                saveSession(session)
                return@withContext Result.success(session)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Signup Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Sign in using email and password
    suspend fun signInWithEmail(email: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            // Local simulation login checking against previously registered users
            kotlinx.coroutines.delay(1000)
            val storedPw = sharedPrefs.getString("reg_pw_$email", null)
            
            // Standard demo default accounts if empty
            if (storedPw == null) {
                if (email == "owner@apmc.com" && password == "owner123") {
                    val session = UserSession(email, "9999999999", "Owner Merchant", UserRole.OWNER, "mock-owner-token")
                    saveSession(session)
                    return@withContext Result.success(session)
                } else if (email == "admin@apmc.com" && password == "admin123") {
                    val session = UserSession(email, "8888888888", "Admin Operator", UserRole.ADMIN, "mock-admin-token")
                    saveSession(session)
                    return@withContext Result.success(session)
                } else if (email == "staff@apmc.com" && password == "staff123") {
                    val session = UserSession(email, "7777777777", "Staff Billing Clerks", UserRole.STAFF, "mock-staff-token")
                    saveSession(session)
                    return@withContext Result.success(session)
                } else {
                    return@withContext Result.failure(Exception("Invalid email/password. Default accounts: owner@apmc.com (pwd: owner123), admin@apmc.com (pwd: admin123), staff@apmc.com (pwd: staff123)"))
                }
            } else if (storedPw == password) {
                val name = sharedPrefs.getString("reg_name_$email", "User") ?: "User"
                val phone = sharedPrefs.getString("reg_phone_$email", "") ?: ""
                val roleStr = sharedPrefs.getString("reg_role_$email", "STAFF") ?: "STAFF"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STAFF }
                
                val session = UserSession(email, phone, name, role, "mock-user-token")
                saveSession(session)
                return@withContext Result.success(session)
            } else {
                return@withContext Result.failure(Exception("Invalid credentials for this account."))
            }
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val request = Request.Builder()
                .url("$url/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .post(json.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(bodyString).getString("error_description")
                    } catch (e: Exception) {
                        "Invalid login credentials"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonResponse = JSONObject(bodyString)
                val token = jsonResponse.getString("access_token")
                val userObj = jsonResponse.getJSONObject("user")
                val userMetadata = userObj.optJSONObject("user_metadata")

                val name = userMetadata?.optString("name", "User") ?: "User"
                val phone = userMetadata?.optString("phone", "") ?: ""
                val roleStr = userMetadata?.optString("role", "STAFF") ?: "STAFF"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STAFF }

                val session = UserSession(email, phone, name, role, token)
                saveSession(session)
                return@withContext Result.success(session)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Login Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Trigger Mobile OTP Code sending
    suspend fun signInWithOtp(phone: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            // Local simulation - OTP is immediately accepted and sent
            kotlinx.coroutines.delay(1000)
            return@withContext Result.success(true)
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val json = JSONObject().apply {
                put("phone", phone)
            }

            val request = Request.Builder()
                .url("$url/auth/v1/otp")
                .addHeader("apikey", anonKey)
                .post(json.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val errorMsg = try {
                        JSONObject(bodyString).getString("msg")
                    } catch (e: Exception) {
                        "Failed to trigger OTP"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase OTP Trigger Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Verify Mobile OTP code and sign user in
    suspend fun verifyOtp(phone: String, token: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            // Local simulation - verify standard simulated code "123456" or any 6 digit code for test ease
            kotlinx.coroutines.delay(1000)
            if (token == "123456" || token.length == 6) {
                // Determine a simulation role
                val defaultRole = when {
                    phone.endsWith("9") -> UserRole.OWNER
                    phone.endsWith("8") -> UserRole.ADMIN
                    else -> UserRole.STAFF
                }
                val name = when (defaultRole) {
                    UserRole.OWNER -> "Simulated Owner"
                    UserRole.ADMIN -> "Simulated Admin"
                    UserRole.STAFF -> "Simulated Clerk"
                }
                val session = UserSession(
                    email = "${phone}@simulated.com",
                    phone = phone,
                    name = name,
                    role = defaultRole,
                    token = "mock-otp-token-" + System.currentTimeMillis()
                )
                saveSession(session)
                return@withContext Result.success(session)
            } else {
                return@withContext Result.failure(Exception("Incorrect 6-digit OTP code. Enter '123456' for instant access!"))
            }
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val json = JSONObject().apply {
                put("type", "sms")
                put("phone", phone)
                put("token", token)
            }

            val request = Request.Builder()
                .url("$url/auth/v1/verify")
                .addHeader("apikey", anonKey)
                .post(json.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(bodyString).getString("error_description")
                    } catch (e: Exception) {
                        "Invalid or expired OTP token"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonResponse = JSONObject(bodyString)
                val accessToken = jsonResponse.getString("access_token")
                val userObj = jsonResponse.getJSONObject("user")
                val userMetadata = userObj.optJSONObject("user_metadata")

                val name = userMetadata?.optString("name", "SMS User") ?: "SMS User"
                val finalEmail = userObj.optString("email", "${phone}@supabase.com")
                val roleStr = userMetadata?.optString("role", "STAFF") ?: "STAFF"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STAFF }

                val session = UserSession(finalEmail, phone, name, role, accessToken)
                saveSession(session)
                return@withContext Result.success(session)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase OTP Verification Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Trigger Forgot Password email reset
    suspend fun sendPasswordRecovery(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            // Local simulation mode
            kotlinx.coroutines.delay(1000)
            return@withContext Result.success(true)
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val json = JSONObject().apply {
                put("email", email)
            }

            val request = Request.Builder()
                .url("$url/auth/v1/recover")
                .addHeader("apikey", anonKey)
                .post(json.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val errorMsg = try {
                        JSONObject(bodyString).getString("msg")
                    } catch (e: Exception) {
                        "Failed to trigger password recovery"
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Recovery Error", e)
            return@withContext Result.failure(e)
        }
    }

    fun loadLocalShopConfig(): ShopConfig {
        val shopName = sharedPrefs.getString("shop_name", "APMC Trading Co.") ?: "APMC Trading Co."
        val ownerName = sharedPrefs.getString("shop_owner_name", "S. Valani") ?: "S. Valani"
        val gstNumber = sharedPrefs.getString("shop_gst_number", "27AAAAA1111A1Z1") ?: "27AAAAA1111A1Z1"
        val apmcLicense = sharedPrefs.getString("shop_apmc_license", "APMC/MKT/2026/849") ?: "APMC/MKT/2026/849"
        val address = sharedPrefs.getString("shop_address", "APMC Market Yard, Gate No. 2, Sector 19, Navi Mumbai, MH 400705") ?: "APMC Market Yard, Gate No. 2, Sector 19, Navi Mumbai, MH 400705"
        val logoUrl = sharedPrefs.getString("shop_logo_url", "") ?: ""
        val logoPreset = sharedPrefs.getString("shop_logo_preset", "GRAINS") ?: "GRAINS"
        val invoicePrefix = sharedPrefs.getString("shop_invoice_prefix", "ATC/") ?: "ATC/"
        val upiId = sharedPrefs.getString("shop_upi_id", "svalani83@okaxis") ?: "svalani83@okaxis"
        val bankName = sharedPrefs.getString("shop_bank_name", "State Bank of India") ?: "State Bank of India"
        val bankAccName = sharedPrefs.getString("shop_bank_acc_name", "APMC Trading Co.") ?: "APMC Trading Co."
        val bankAccNum = sharedPrefs.getString("shop_bank_acc_num", "12345678901") ?: "12345678901"
        val bankIfsc = sharedPrefs.getString("shop_bank_ifsc", "SBIN0001234") ?: "SBIN0001234"
        val printerType = sharedPrefs.getString("shop_printer_type", "Thermal 3-inch") ?: "Thermal 3-inch"
        val printAutomatically = sharedPrefs.getBoolean("shop_print_automatically", true)
        val theme = sharedPrefs.getString("shop_theme", "System") ?: "System"
        val language = sharedPrefs.getString("shop_language", "English") ?: "English"
        val isCompleted = sharedPrefs.getBoolean("shop_wizard_completed", false)

        return ShopConfig(
            shopName = shopName,
            ownerName = ownerName,
            gstNumber = gstNumber,
            apmcLicense = apmcLicense,
            address = address,
            logoUrl = logoUrl,
            logoPreset = logoPreset,
            invoicePrefix = invoicePrefix,
            upiId = upiId,
            bankName = bankName,
            bankAccName = bankAccName,
            bankAccNum = bankAccNum,
            bankIfsc = bankIfsc,
            printerType = printerType,
            printAutomatically = printAutomatically,
            theme = theme,
            language = language,
            isCompleted = isCompleted
        )
    }

    fun saveLocalShopConfig(config: ShopConfig) {
        sharedPrefs.edit()
            .putString("shop_name", config.shopName)
            .putString("shop_owner_name", config.ownerName)
            .putString("shop_gst_number", config.gstNumber)
            .putString("shop_apmc_license", config.apmcLicense)
            .putString("shop_address", config.address)
            .putString("shop_logo_url", config.logoUrl)
            .putString("shop_logo_preset", config.logoPreset)
            .putString("shop_invoice_prefix", config.invoicePrefix)
            .putString("shop_upi_id", config.upiId)
            .putString("shop_bank_name", config.bankName)
            .putString("shop_bank_acc_name", config.bankAccName)
            .putString("shop_bank_acc_num", config.bankAccNum)
            .putString("shop_bank_ifsc", config.bankIfsc)
            .putString("shop_printer_type", config.printerType)
            .putBoolean("shop_print_automatically", config.printAutomatically)
            .putString("shop_theme", config.theme)
            .putString("shop_language", config.language)
            .putBoolean("shop_wizard_completed", config.isCompleted)
            .apply()
    }

    // Pull shop configuration from Supabase REST API
    suspend fun fetchShopConfigFromSupabase(email: String): Result<ShopConfig?> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(null) // sandbox mode, use local
        }

        try {
            val request = Request.Builder()
                .url("$url/rest/v1/shop_config?owner_email=eq.$email")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Supabase pull failed: ${response.code}"))
                }

                val jsonArray = org.json.JSONArray(bodyString)
                if (jsonArray.length() == 0) {
                    return@withContext Result.success(null)
                }

                val obj = jsonArray.getJSONObject(0)
                val config = ShopConfig(
                    shopName = obj.optString("shop_name", "APMC Trading Co."),
                    ownerName = obj.optString("owner_name", "S. Valani"),
                    gstNumber = obj.optString("gst_number", "27AAAAA1111A1Z1"),
                    apmcLicense = obj.optString("apmc_license", "APMC/MKT/2026/849"),
                    address = obj.optString("address", ""),
                    logoUrl = obj.optString("logo_url", ""),
                    logoPreset = obj.optString("logo_preset", "GRAINS"),
                    invoicePrefix = obj.optString("invoice_prefix", "ATC/"),
                    upiId = obj.optString("upi_id", ""),
                    bankName = obj.optString("bank_name", ""),
                    bankAccName = obj.optString("bank_acc_name", ""),
                    bankAccNum = obj.optString("bank_acc_num", ""),
                    bankIfsc = obj.optString("bank_ifsc", ""),
                    printerType = obj.optString("printer_type", "Thermal 3-inch"),
                    printAutomatically = obj.optBoolean("print_automatically", true),
                    theme = obj.optString("theme", "System"),
                    language = obj.optString("language", "English"),
                    isCompleted = obj.optBoolean("is_completed", false)
                )
                return@withContext Result.success(config)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Pull Shop Config Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Push shop configuration to Supabase REST API (GET-then-POST/PATCH)
    suspend fun saveShopConfigToSupabase(config: ShopConfig, email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value

        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true) // sandbox mode, success
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("owner_email", email)
                put("shop_name", config.shopName)
                put("owner_name", config.ownerName)
                put("gst_number", config.gstNumber)
                put("apmc_license", config.apmcLicense)
                put("address", config.address)
                put("logo_url", config.logoUrl)
                put("logo_preset", config.logoPreset)
                put("invoice_prefix", config.invoicePrefix)
                put("upi_id", config.upiId)
                put("bank_name", config.bankName)
                put("bank_acc_name", config.bankAccName)
                put("bank_acc_num", config.bankAccNum)
                put("bank_ifsc", config.bankIfsc)
                put("printer_type", config.printerType)
                put("print_automatically", config.printAutomatically)
                put("theme", config.theme)
                put("language", config.language)
                put("is_completed", config.isCompleted)
            }

            // 1. Check if row exists
            val checkRequest = Request.Builder()
                .url("$url/rest/v1/shop_config?owner_email=eq.$email")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            var exists = false
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val resStr = response.body?.string() ?: "[]"
                    val jsonArray = org.json.JSONArray(resStr)
                    exists = jsonArray.length() > 0
                }
            }

            // 2. Perform POST or PATCH
            val requestBuilder = Request.Builder()
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")

            if (exists) {
                // PATCH (update)
                requestBuilder
                    .url("$url/rest/v1/shop_config?owner_email=eq.$email")
                    .patch(payload.toString().toRequestBody(mediaType))
            } else {
                // POST (insert)
                requestBuilder
                    .url("$url/rest/v1/shop_config")
                    .post(payload.toString().toRequestBody(mediaType))
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase push failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase save failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Save Shop Config Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Push party to Supabase REST API
    suspend fun pushPartyToSupabase(party: Party): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true) // sandbox mode, success
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("name", party.name)
                put("type", party.type)
                put("phone", party.phone)
                put("address", party.address)
                put("license_no", party.licenseNo ?: JSONObject.NULL)
                put("bank_details", party.bankDetails ?: JSONObject.NULL)
                put("balance", party.balance)
            }

            // Check if exists by name/phone since Room auto-generated IDs might differ initially from Supabase
            // Or we can query by a combination of phone and name to see if it exists
            val checkRequest = Request.Builder()
                .url("$url/rest/v1/parties?phone=eq.${party.phone}&type=eq.${party.type}")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            var remoteId: Int? = null
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val resStr = response.body?.string() ?: "[]"
                    val jsonArray = org.json.JSONArray(resStr)
                    if (jsonArray.length() > 0) {
                        remoteId = jsonArray.getJSONObject(0).getInt("id")
                    }
                }
            }

            val requestBuilder = Request.Builder()
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")

            if (remoteId != null) {
                // PATCH (update)
                requestBuilder
                    .url("$url/rest/v1/parties?id=eq.$remoteId")
                    .patch(payload.toString().toRequestBody(mediaType))
            } else {
                // POST (insert)
                requestBuilder
                    .url("$url/rest/v1/parties")
                    .post(payload.toString().toRequestBody(mediaType))
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase push party failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase push failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Save Party Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Delete party from Supabase
    suspend fun deletePartyFromSupabase(phone: String, type: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true)
        }

        try {
            val request = Request.Builder()
                .url("$url/rest/v1/parties?phone=eq.$phone&type=eq.$type")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase delete party failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase delete failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Delete Party Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Fetch parties from Supabase
    suspend fun fetchPartiesFromSupabase(): Result<List<Party>> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val request = Request.Builder()
                .url("$url/rest/v1/parties")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase fetch parties failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase fetch failed: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: "[]"
                val jsonArray = org.json.JSONArray(bodyString)
                val list = mutableListOf<Party>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        Party(
                            id = 0, // Will be auto-generated or matched in Room
                            name = obj.optString("name", ""),
                            type = obj.optString("type", ""),
                            phone = obj.optString("phone", ""),
                            address = obj.optString("address", ""),
                            licenseNo = if (obj.isNull("license_no")) null else obj.optString("license_no", null),
                            bankDetails = if (obj.isNull("bank_details")) null else obj.optString("bank_details", null),
                            balance = obj.optDouble("balance", 0.0)
                        )
                    )
                }
                return@withContext Result.success(list)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Fetch Parties Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Push Commodity to Supabase REST API
    suspend fun pushCommodityToSupabase(commodity: Commodity): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true) // sandbox mode
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("name", commodity.name)
                put("market_fee_percent", commodity.marketFeePercent)
                put("commission_percent", commodity.commissionPercent)
                put("stock_bags", commodity.stockBags)
                put("current_rate", commodity.currentRate)
                put("category", commodity.category)
                put("barcode", commodity.barcode ?: JSONObject.NULL)
                put("gst_percent", commodity.gstPercent)
                put("hsn_code", commodity.hsnCode ?: JSONObject.NULL)
                put("purchase_price", commodity.purchasePrice)
                put("selling_price", commodity.sellingPrice)
            }

            // Check if exists by name (since names are unique for commodities in standard setups)
            val checkRequest = Request.Builder()
                .url("$url/rest/v1/commodities?name=eq.${commodity.name}")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            var remoteId: Int? = null
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val resStr = response.body?.string() ?: "[]"
                    val jsonArray = org.json.JSONArray(resStr)
                    if (jsonArray.length() > 0) {
                        remoteId = jsonArray.getJSONObject(0).getInt("id")
                    }
                }
            }

            val requestBuilder = Request.Builder()
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")

            if (remoteId != null) {
                // PATCH (update)
                requestBuilder
                    .url("$url/rest/v1/commodities?id=eq.$remoteId")
                    .patch(payload.toString().toRequestBody(mediaType))
            } else {
                // POST (insert)
                requestBuilder
                    .url("$url/rest/v1/commodities")
                    .post(payload.toString().toRequestBody(mediaType))
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase push commodity failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase push commodity failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Save Commodity Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Delete Commodity from Supabase
    suspend fun deleteCommodityFromSupabase(name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true)
        }

        try {
            val request = Request.Builder()
                .url("$url/rest/v1/commodities?name=eq.$name")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase delete commodity failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase delete failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Delete Commodity Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Fetch Commodities from Supabase
    suspend fun fetchCommoditiesFromSupabase(): Result<List<Commodity>> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val request = Request.Builder()
                .url("$url/rest/v1/commodities")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase fetch commodities failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase fetch failed: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: "[]"
                val jsonArray = org.json.JSONArray(bodyString)
                val list = mutableListOf<Commodity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        Commodity(
                            id = 0,
                            name = obj.optString("name", ""),
                            marketFeePercent = obj.optDouble("market_fee_percent", 1.0),
                            commissionPercent = obj.optDouble("commission_percent", 5.0),
                            stockBags = obj.optInt("stock_bags", 0),
                            currentRate = obj.optDouble("current_rate", 0.0),
                            category = obj.optString("category", "General"),
                            barcode = if (obj.isNull("barcode")) null else obj.optString("barcode", null),
                            gstPercent = obj.optDouble("gst_percent", 0.0),
                            hsnCode = if (obj.isNull("hsn_code")) null else obj.optString("hsn_code", null),
                            purchasePrice = obj.optDouble("purchase_price", 0.0),
                            sellingPrice = obj.optDouble("selling_price", 0.0)
                        )
                    )
                }
                return@withContext Result.success(list)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Fetch Commodities Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Push InventoryTransaction to Supabase
    suspend fun pushInventoryTransactionToSupabase(tx: InventoryTransaction): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true)
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("commodity_id", tx.commodityId)
                put("commodity_name", tx.commodityName)
                put("type", tx.type)
                put("bags", tx.bags)
                put("total_weight", tx.totalWeight)
                put("rate_per_qtl", tx.ratePerQtl)
                put("total_price", tx.totalPrice)
                put("party_id", tx.partyId ?: JSONObject.NULL)
                put("party_name", tx.partyName ?: JSONObject.NULL)
                put("date", tx.date)
                put("remarks", tx.remarks)
                put("reference_no", tx.referenceNo ?: JSONObject.NULL)
            }

            // Insert directly as transactions are sequential journal entries
            val request = Request.Builder()
                .url("$url/rest/v1/inventory_transactions")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase push transaction failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase push transaction failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Save Transaction Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Fetch InventoryTransactions from Supabase
    suspend fun fetchInventoryTransactionsFromSupabase(): Result<List<InventoryTransaction>> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val request = Request.Builder()
                .url("$url/rest/v1/inventory_transactions")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase fetch transactions failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase fetch failed: ${response.code}"))
                }
                val bodyString = response.body?.string() ?: "[]"
                val jsonArray = org.json.JSONArray(bodyString)
                val list = mutableListOf<InventoryTransaction>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        InventoryTransaction(
                            id = 0,
                            commodityId = obj.optInt("commodity_id", 0),
                            commodityName = obj.optString("commodity_name", ""),
                            type = obj.optString("type", ""),
                            bags = obj.optInt("bags", 0),
                            totalWeight = obj.optDouble("total_weight", 0.0),
                            ratePerQtl = obj.optDouble("rate_per_qtl", 0.0),
                            totalPrice = obj.optDouble("total_price", 0.0),
                            partyId = if (obj.isNull("party_id")) null else obj.optInt("party_id"),
                            partyName = if (obj.isNull("party_name")) null else obj.optString("party_name"),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            remarks = obj.optString("remarks", ""),
                            referenceNo = if (obj.isNull("reference_no")) null else obj.optString("reference_no")
                        )
                    )
                }
                return@withContext Result.success(list)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Fetch Transactions Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Push Bill to Supabase REST API
    suspend fun pushBillToSupabase(bill: Bill): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true) // sandbox mode
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("bill_number", bill.billNumber)
                put("date", bill.date)
                put("party_id", bill.partyId)
                put("party_name", bill.partyName)
                put("party_type", bill.partyType)
                put("commodity_id", bill.commodityId)
                put("commodity_name", bill.commodityName)
                put("bag_count", bill.bagCount)
                put("weight", bill.weight)
                put("rate", bill.rate)
                put("gross_amount", bill.grossAmount)
                put("commission_amount", bill.commissionAmount)
                put("labor_charges", bill.laborCharges)
                put("transport_charges", bill.transportCharges)
                put("market_fee_amount", bill.marketFeeAmount)
                put("tax_amount", bill.taxAmount)
                put("discount_amount", bill.discountAmount)
                put("round_off", bill.roundOff)
                put("net_amount", bill.netAmount)
                put("is_paid", bill.isPaid)
                put("payment_notes", bill.paymentNotes)
            }

            val request = Request.Builder()
                .url("$url/rest/v1/bills")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase push bill failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase push bill failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Save Bill Error", e)
            return@withContext Result.failure(e)
        }
    }

    // Push Expense to Supabase REST API
    suspend fun pushExpenseToSupabase(expense: Expense): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = _supabaseUrl.value
        val anonKey = _supabaseAnonKey.value
        if (url.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(true) // sandbox mode
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("amount", expense.amount)
                put("category", expense.category)
                put("date", expense.date)
                put("description", expense.description)
            }

            val request = Request.Builder()
                .url("$url/rest/v1/expenses")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .post(payload.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Log.e("AuthService", "Supabase push expense failed: ${response.code} / $bodyString")
                    return@withContext Result.failure(Exception("Supabase push expense failed: ${response.code}"))
                }
                return@withContext Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AuthService", "Supabase Save Expense Error", e)
            return@withContext Result.failure(e)
        }
    }
}
