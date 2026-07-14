package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.BillingViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BillingViewModel) {
    val context = LocalContext.current
    val shopConfig by viewModel.shopConfig.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Setup local shared preferences for simple non-ShopConfig preferences
    val authPrefs = remember { context.getSharedPreferences("apmc_auth_prefs", Context.MODE_PRIVATE) }
    val settingsPrefs = remember { context.getSharedPreferences("apmc_settings_prefs", Context.MODE_PRIVATE) }

    // Active sub-section routing
    var activeSection by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (activeSection) {
                            null -> "Settings"
                            "profile" -> "Shop Profile"
                            "theme" -> "Theme Settings"
                            "language" -> "Language Selection"
                            "invoice" -> "Invoice Settings"
                            "security" -> "Security & Protection"
                            "backup" -> "Backup Database"
                            "restore" -> "Restore Database"
                            "printer" -> "Printer Configuration"
                            "notifications" -> "Notification Preferences"
                            "users" -> "User Management"
                            "offline_sync" -> "Offline & Cloud Sync"
                            else -> "Settings"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (activeSection != null) {
                        IconButton(
                            onClick = { activeSection = null },
                            modifier = Modifier.testTag("settings_back_to_menu")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.navigateTo("dashboard") },
                            modifier = Modifier.testTag("settings_back_to_dashboard")
                        ) {
                            Icon(imageVector = Icons.Default.Home, contentDescription = "Dashboard")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("settings_top_bar")
            )
        }
    ) { paddingValues ->
        Crossfade(
            targetState = activeSection,
            animationSpec = spring(),
            label = "settings_section_crossfade",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { section ->
            when (section) {
                null -> SettingsMenuGrid(
                    currentUser = currentUser,
                    onNavigateToSection = { activeSection = it }
                )
                "profile" -> ShopProfileSettingsView(
                    config = shopConfig,
                    onSave = { updated ->
                        viewModel.saveShopConfig(updated) {
                            activeSection = null
                        }
                    }
                )
                "theme" -> ThemeSettingsView(
                    config = shopConfig,
                    onSave = { updated ->
                        viewModel.saveShopConfig(updated) {
                            activeSection = null
                        }
                    }
                )
                "language" -> LanguageSettingsView(
                    config = shopConfig,
                    onSave = { updated ->
                        viewModel.saveShopConfig(updated) {
                            viewModel.showToast("Language updated to ${updated.language}")
                            activeSection = null
                        }
                    }
                )
                "invoice" -> InvoiceSettingsView(
                    config = shopConfig,
                    settingsPrefs = settingsPrefs,
                    onSave = { updated, terms, roundPref ->
                        settingsPrefs.edit()
                            .putString("invoice_terms", terms)
                            .putString("invoice_rounding", roundPref)
                            .apply()
                        viewModel.saveShopConfig(updated) {
                            activeSection = null
                        }
                    }
                )
                "security" -> SecuritySettingsView(
                    currentUser = currentUser,
                    authPrefs = authPrefs,
                    settingsPrefs = settingsPrefs,
                    viewModel = viewModel,
                    onBack = { activeSection = null }
                )
                "backup" -> BackupSettingsView(
                    viewModel = viewModel,
                    onBack = { activeSection = null }
                )
                "restore" -> RestoreSettingsView(
                    viewModel = viewModel,
                    onBack = { activeSection = null }
                )
                "printer" -> PrinterSettingsView(
                    config = shopConfig,
                    onSave = { updated ->
                        viewModel.saveShopConfig(updated) {
                            activeSection = null
                        }
                    }
                )
                "notifications" -> NotificationPreferencesView(
                    settingsPrefs = settingsPrefs,
                    onBack = { activeSection = null }
                )
                "users" -> UserManagementView(
                    currentUser = currentUser,
                    authPrefs = authPrefs,
                    viewModel = viewModel,
                    onBack = { activeSection = null }
                )
                "offline_sync" -> OfflineSyncSettingsView(
                    viewModel = viewModel,
                    onBack = { activeSection = null }
                )
            }
        }
    }
}

// Main Menu List
@Composable
fun SettingsMenuGrid(
    currentUser: UserSession?,
    onNavigateToSection: (String) -> Unit
) {
    val items = remember(currentUser) {
        val list = mutableListOf(
            Triple("profile", "Shop Profile", Icons.Default.Storefront),
            Triple("theme", "Theme", Icons.Default.Palette),
            Triple("language", "Language", Icons.Default.Translate),
            Triple("invoice", "Invoice", Icons.Default.Receipt),
            Triple("security", "Security", Icons.Default.Security),
            Triple("backup", "Backup", Icons.Default.CloudUpload),
            Triple("restore", "Restore", Icons.Default.SettingsBackupRestore),
            Triple("printer", "Printer", Icons.Default.Print),
            Triple("notifications", "Notification Preferences", Icons.Default.Notifications),
            Triple("offline_sync", "Offline & Cloud Sync", Icons.Default.Sync)
        )
        // User management is only for owners and admins
        if (currentUser?.role == UserRole.OWNER || currentUser?.role == UserRole.ADMIN) {
            list.add(Triple("users", "User Management", Icons.Default.ManageAccounts))
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) { (route, label, icon) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSection(route) }
                    .testTag("menu_item_$route"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Navigate",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// 1. Shop Profile Settings
@Composable
fun ShopProfileSettingsView(
    config: ShopConfig,
    onSave: (ShopConfig) -> Unit
) {
    var shopName by remember { mutableStateOf(config.shopName) }
    var ownerName by remember { mutableStateOf(config.ownerName) }
    var gstNumber by remember { mutableStateOf(config.gstNumber) }
    var apmcLicense by remember { mutableStateOf(config.apmcLicense) }
    var address by remember { mutableStateOf(config.address) }
    var logoPreset by remember { mutableStateOf(config.logoPreset) }

    val logoPresets = listOf("GRAINS", "VEGETABLES", "COTTON", "FRUITS", "STORE")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Update your business credentials and address below. These values are automatically stamped on all outgoing legal bills and tax reports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it },
            label = { Text("Shop / Trading Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_shop_name"),
            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) }
        )

        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it },
            label = { Text("Proprietor / Owner Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_owner_name"),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        OutlinedTextField(
            value = gstNumber,
            onValueChange = { gstNumber = it },
            label = { Text("GSTIN Identification Number") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_gst_number"),
            leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }
        )

        OutlinedTextField(
            value = apmcLicense,
            onValueChange = { apmcLicense = it },
            label = { Text("APMC Merchant License Code") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_apmc_license"),
            leadingIcon = { Icon(Icons.Default.CardMembership, contentDescription = null) }
        )

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Shop Physical Address") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_address"),
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
        )

        Text(
            text = "Select Shop Logo Preset",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            logoPresets.forEach { preset ->
                val selected = logoPreset == preset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { logoPreset = preset }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onSave(
                    config.copy(
                        shopName = shopName,
                        ownerName = ownerName,
                        gstNumber = gstNumber,
                        apmcLicense = apmcLicense,
                        address = address,
                        logoPreset = logoPreset
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("profile_save_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Profile Changes")
        }
    }
}

// 2. Theme Settings
@Composable
fun ThemeSettingsView(
    config: ShopConfig,
    onSave: (ShopConfig) -> Unit
) {
    var selectedTheme by remember { mutableStateOf(config.theme) }
    val themes = listOf("Light", "Dark", "System")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Customize the visual theme of your APMC billing portal. System theme automatically respects your device's global Dark/Light toggle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        themes.forEach { theme ->
            val isSelected = selectedTheme == theme
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTheme = theme }
                    .testTag("theme_card_$theme"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedTheme = theme }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "$theme Theme",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (theme) {
                                "Light" -> "Clean high contrast light workspace"
                                "Dark" -> "Comfortable eye-safe dark slate interface"
                                else -> "Adapts to device settings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                onSave(config.copy(theme = selectedTheme))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("theme_save_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply Theme")
        }
    }
}

// 3. Language Settings
@Composable
fun LanguageSettingsView(
    config: ShopConfig,
    onSave: (ShopConfig) -> Unit
) {
    var selectedLang by remember { mutableStateOf(config.language) }
    val languages = listOf("English", "Hindi", "Marathi", "Gujarati", "Telugu")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select your preferred language. System texts and generated bills will update to display translated structures.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        languages.forEach { lang ->
            val isSelected = selectedLang == lang
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedLang = lang }
                    .testTag("lang_card_$lang"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedLang = lang }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = lang,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                onSave(config.copy(language = selectedLang))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("lang_save_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Language Settings")
        }
    }
}

// 4. Invoice Settings
@Composable
fun InvoiceSettingsView(
    config: ShopConfig,
    settingsPrefs: SharedPreferences,
    onSave: (ShopConfig, String, String) -> Unit
) {
    var prefix by remember { mutableStateOf(config.invoicePrefix) }
    var upiId by remember { mutableStateOf(config.upiId) }
    var terms by remember { mutableStateOf(settingsPrefs.getString("invoice_terms", "1. Goods once sold are not returnable.\n2. Interest @18% will be charged if unpaid after due date.") ?: "") }
    var roundingSelection by remember { mutableStateOf(settingsPrefs.getString("invoice_rounding", "NEAREST") ?: "NEAREST") }

    val roundingOptions = listOf("NEAREST" to "Round to nearest Re. 1", "NONE" to "Display exact decimal fractions")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configure default billing, invoicing prefixes, standard digital payment endpoints, and payment terms printed at the footer of invoices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        OutlinedTextField(
            value = prefix,
            onValueChange = { prefix = it },
            label = { Text("Invoice Prefix Code") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("invoice_prefix"),
            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) }
        )

        OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it },
            label = { Text("Shop UPI Address (UPI ID)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("invoice_upi"),
            placeholder = { Text("e.g. svalani@okaxis") },
            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) }
        )

        OutlinedTextField(
            value = terms,
            onValueChange = { terms = it },
            label = { Text("Terms & Conditions (Footer)") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("invoice_terms_field"),
            leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) }
        )

        Text(
            text = "Invoice Cash Rounding Preference",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        roundingOptions.forEach { (key, label) ->
            val isSelected = roundingSelection == key
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { roundingSelection = key }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { roundingSelection = key }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onSave(
                    config.copy(
                        invoicePrefix = prefix,
                        upiId = upiId
                    ),
                    terms,
                    roundingSelection
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("invoice_save_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Invoicing Preferences")
        }
    }
}

// 5. Security Settings
@Composable
fun SecuritySettingsView(
    currentUser: UserSession?,
    authPrefs: SharedPreferences,
    settingsPrefs: SharedPreferences,
    viewModel: BillingViewModel,
    onBack: () -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var screenLockEnabled by remember { mutableStateOf(settingsPrefs.getBoolean("security_screen_lock", false)) }
    var requirePinOnBoot by remember { mutableStateOf(settingsPrefs.getBoolean("security_pin_on_boot", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Manage account security, update your local login password, or configure system security locks to prevent unauthorized modifications.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Change Login Password",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sec_old_pw"),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sec_new_pw"),
                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sec_confirm_pw"),
                    leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) }
                )

                Button(
                    onClick = {
                        val email = currentUser?.email ?: ""
                        val actualOld = authPrefs.getString("reg_pw_$email", "owner123") ?: "owner123"
                        if (oldPassword != actualOld) {
                            viewModel.showToast("Current password did not match records")
                        } else if (newPassword.length < 6) {
                            viewModel.showToast("New password must be at least 6 characters")
                        } else if (newPassword != confirmPassword) {
                            viewModel.showToast("Passwords do not match")
                        } else {
                            authPrefs.edit()
                                .putString("reg_pw_$email", newPassword)
                                .apply()
                            viewModel.showToast("Password updated successfully!")
                            oldPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("sec_save_pw")
                ) {
                    Text("Change Password")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Device Access Locks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    screenLockEnabled = !screenLockEnabled
                    settingsPrefs.edit().putBoolean("security_screen_lock", screenLockEnabled).apply()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PhonelinkLock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Enable Application PIN Lock", fontWeight = FontWeight.Bold)
                Text("Asks for verification PIN before opening key modules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = screenLockEnabled,
                onCheckedChange = {
                    screenLockEnabled = it
                    settingsPrefs.edit().putBoolean("security_screen_lock", it).apply()
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    requirePinOnBoot = !requirePinOnBoot
                    settingsPrefs.edit().putBoolean("security_pin_on_boot", requirePinOnBoot).apply()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Require PIN on System Boot", fontWeight = FontWeight.Bold)
                Text("Enforce strong PIN entry immediately on app start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = requirePinOnBoot,
                onCheckedChange = {
                    requirePinOnBoot = it
                    settingsPrefs.edit().putBoolean("security_pin_on_boot", it).apply()
                }
            )
        }
    }
}

// 6. Backup Settings
@Composable
fun BackupSettingsView(
    viewModel: BillingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val parties by viewModel.parties.collectAsState()
    val commodities by viewModel.commodities.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val ledger by viewModel.ledgerEntries.collectAsState()
    val txs by viewModel.inventoryTransactions.collectAsState()
    val expenses by viewModel.expenses.collectAsState()

    var showBackupString by remember { mutableStateOf<String?>(null) }

    fun generateBackupString(): String {
        val root = JSONObject()
        val partiesArr = JSONArray()
        parties.forEach { p ->
            partiesArr.put(JSONObject().apply {
                put("name", p.name)
                put("type", p.type)
                put("phone", p.phone)
                put("address", p.address)
                put("licenseNo", p.licenseNo ?: "")
                put("bankDetails", p.bankDetails ?: "")
                put("balance", p.balance)
            })
        }
        root.put("parties", partiesArr)

        val commoditiesArr = JSONArray()
        commodities.forEach { c ->
            commoditiesArr.put(JSONObject().apply {
                put("name", c.name)
                put("marketFeePercent", c.marketFeePercent)
                put("commissionPercent", c.commissionPercent)
                put("stockBags", c.stockBags)
                put("currentRate", c.currentRate)
                put("category", c.category)
                put("barcode", c.barcode ?: "")
                put("gstPercent", c.gstPercent)
                put("hsnCode", c.hsnCode ?: "")
                put("purchasePrice", c.purchasePrice)
                put("sellingPrice", c.sellingPrice)
            })
        }
        root.put("commodities", commoditiesArr)

        val billsArr = JSONArray()
        bills.forEach { b ->
            billsArr.put(JSONObject().apply {
                put("billNumber", b.billNumber)
                put("date", b.date)
                put("partyId", b.partyId)
                put("partyName", b.partyName)
                put("partyType", b.partyType)
                put("commodityId", b.commodityId)
                put("commodityName", b.commodityName)
                put("bagCount", b.bagCount)
                put("weight", b.weight)
                put("rate", b.rate)
                put("grossAmount", b.grossAmount)
                put("commissionAmount", b.commissionAmount)
                put("laborCharges", b.laborCharges)
                put("transportCharges", b.transportCharges)
                put("marketFeeAmount", b.marketFeeAmount)
                put("taxAmount", b.taxAmount)
                put("discountAmount", b.discountAmount)
                put("roundOff", b.roundOff)
                put("netAmount", b.netAmount)
                put("isPaid", b.isPaid)
                put("paymentNotes", b.paymentNotes)
            })
        }
        root.put("bills", billsArr)

        val ledgerArr = JSONArray()
        ledger.forEach { l ->
            ledgerArr.put(JSONObject().apply {
                put("partyId", l.partyId)
                put("date", l.date)
                put("entryType", l.entryType)
                put("amount", l.amount)
                put("referenceId", l.referenceId)
                put("description", l.description)
            })
        }
        root.put("ledger", ledgerArr)

        val txsArr = JSONArray()
        txs.forEach { t ->
            txsArr.put(JSONObject().apply {
                put("commodityId", t.commodityId)
                put("commodityName", t.commodityName)
                put("type", t.type)
                put("bags", t.bags)
                put("totalWeight", t.totalWeight)
                put("ratePerQtl", t.ratePerQtl)
                put("totalPrice", t.totalPrice)
                put("partyId", t.partyId ?: -1)
                put("partyName", t.partyName ?: "")
                put("date", t.date)
                put("remarks", t.remarks)
                put("referenceNo", t.referenceNo ?: "")
            })
        }
        root.put("transactions", txsArr)

        val expensesArr = JSONArray()
        expenses.forEach { e ->
            expensesArr.put(JSONObject().apply {
                put("description", e.description)
                put("amount", e.amount)
                put("category", e.category)
                put("date", e.date)
            })
        }
        root.put("expenses", expensesArr)

        return root.toString(4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Export a complete backup of your entire local APMC database. This generates a encrypted-like JSON data snapshot which you can copy/paste safely.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Database Contents Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Farmer & Buyer Contacts")
                    Text("${parties.size} registered", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Commodities & Stock items")
                    Text("${commodities.size} active", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Generated Invoices")
                    Text("${bills.size} recorded", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ledger Journals")
                    Text("${ledger.size} rows", fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Operating Expenses")
                    Text("${expenses.size} entries", fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = {
                try {
                    val json = generateBackupString()
                    showBackupString = json
                    
                    // Copy to clipboard
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("APMC Backup", json)
                    clipboard.setPrimaryClip(clip)
                    
                    // Store locally as quick-recovery point
                    val sp = context.getSharedPreferences("apmc_settings_prefs", Context.MODE_PRIVATE)
                    sp.edit()
                        .putString("last_local_backup_data", json)
                        .putString("last_local_backup_date", SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
                        .apply()
                    
                    viewModel.showToast("Database exported & copied to clipboard!")
                } catch (e: Exception) {
                    viewModel.showToast("Failed to compile backup: ${e.localizedMessage}")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("backup_export_button")
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compile and Copy Backup")
        }

        val sp = context.getSharedPreferences("apmc_settings_prefs", Context.MODE_PRIVATE)
        val lastBackupDate = sp.getString("last_local_backup_date", "Never")
        Text(
            text = "Last compiled local snapshot: $lastBackupDate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (showBackupString != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                border = CardDefaults.outlinedCardBorder(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = showBackupString ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

// 7. Restore Settings
@Composable
fun RestoreSettingsView(
    viewModel: BillingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("apmc_settings_prefs", Context.MODE_PRIVATE) }
    val lastBackupDate = sp.getString("last_local_backup_date", null)
    val lastBackupData = sp.getString("last_local_backup_data", null)

    var importText by remember { mutableStateOf("") }
    var showConfirmWipeDialog by remember { mutableStateOf(false) }

    fun parseAndApplyRestore(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            
            // 1. Parties
            val partiesList = mutableListOf<Party>()
            val partiesArr = root.optJSONArray("parties")
            if (partiesArr != null) {
                for (i in 0 until partiesArr.length()) {
                    val obj = partiesArr.getJSONObject(i)
                    partiesList.add(Party(
                        name = obj.getString("name"),
                        type = obj.getString("type"),
                        phone = obj.getString("phone"),
                        address = obj.getString("address"),
                        licenseNo = obj.optString("licenseNo").ifEmpty { null },
                        bankDetails = obj.optString("bankDetails").ifEmpty { null },
                        balance = obj.optDouble("balance", 0.0)
                    ))
                }
            }

            // 2. Commodities
            val commoditiesList = mutableListOf<Commodity>()
            val commoditiesArr = root.optJSONArray("commodities")
            if (commoditiesArr != null) {
                for (i in 0 until commoditiesArr.length()) {
                    val obj = commoditiesArr.getJSONObject(i)
                    commoditiesList.add(Commodity(
                        name = obj.getString("name"),
                        marketFeePercent = obj.optDouble("marketFeePercent", 1.0),
                        commissionPercent = obj.optDouble("commissionPercent", 5.0),
                        stockBags = obj.optInt("stockBags", 0),
                        currentRate = obj.optDouble("currentRate", 0.0),
                        category = obj.optString("category", "General"),
                        barcode = obj.optString("barcode").ifEmpty { null },
                        gstPercent = obj.optDouble("gstPercent", 0.0),
                        hsnCode = obj.optString("hsnCode").ifEmpty { null },
                        purchasePrice = obj.optDouble("purchasePrice", 0.0),
                        sellingPrice = obj.optDouble("sellingPrice", 0.0)
                    ))
                }
            }

            // 3. Bills
            val billsList = mutableListOf<Bill>()
            val billsArr = root.optJSONArray("bills")
            if (billsArr != null) {
                for (i in 0 until billsArr.length()) {
                    val obj = billsArr.getJSONObject(i)
                    billsList.add(Bill(
                        billNumber = obj.getString("billNumber"),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        partyId = obj.optInt("partyId", 0),
                        partyName = obj.getString("partyName"),
                        partyType = obj.getString("partyType"),
                        commodityId = obj.optInt("commodityId", 0),
                        commodityName = obj.optString("commodityName", "General"),
                        bagCount = obj.optInt("bagCount", 0),
                        weight = obj.optDouble("weight", 0.0),
                        rate = obj.optDouble("rate", 0.0),
                        grossAmount = obj.optDouble("grossAmount", 0.0),
                        commissionAmount = obj.optDouble("commissionAmount", 0.0),
                        laborCharges = obj.optDouble("laborCharges", 0.0),
                        transportCharges = obj.optDouble("transportCharges", 0.0),
                        marketFeeAmount = obj.optDouble("marketFeeAmount", 0.0),
                        taxAmount = obj.optDouble("taxAmount", 0.0),
                        discountAmount = obj.optDouble("discountAmount", 0.0),
                        roundOff = obj.optDouble("roundOff", 0.0),
                        netAmount = obj.optDouble("netAmount", 0.0),
                        isPaid = obj.optBoolean("isPaid", false),
                        paymentNotes = obj.optString("paymentNotes", "")
                    ))
                }
            }

            // 4. Ledger
            val ledgerList = mutableListOf<LedgerEntry>()
            val ledgerArr = root.optJSONArray("ledger")
            if (ledgerArr != null) {
                for (i in 0 until ledgerArr.length()) {
                    val obj = ledgerArr.getJSONObject(i)
                    ledgerList.add(LedgerEntry(
                        partyId = obj.optInt("partyId", 0),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        entryType = obj.getString("entryType"),
                        amount = obj.optDouble("amount", 0.0),
                        referenceId = obj.getString("referenceId"),
                        description = obj.getString("description")
                    ))
                }
            }

            // 5. InventoryTransactions
            val txsList = mutableListOf<InventoryTransaction>()
            val txsArr = root.optJSONArray("transactions")
            if (txsArr != null) {
                for (i in 0 until txsArr.length()) {
                    val obj = txsArr.getJSONObject(i)
                    txsList.add(InventoryTransaction(
                        commodityId = obj.optInt("commodityId", 0),
                        commodityName = obj.getString("commodityName"),
                        type = obj.getString("type"),
                        bags = obj.optInt("bags", 0),
                        totalWeight = obj.optDouble("totalWeight", 0.0),
                        ratePerQtl = obj.optDouble("ratePerQtl", 0.0),
                        totalPrice = obj.optDouble("totalPrice", 0.0),
                        partyId = if (obj.optInt("partyId", -1) == -1) null else obj.optInt("partyId"),
                        partyName = obj.optString("partyName").ifEmpty { null },
                        date = obj.optLong("date", System.currentTimeMillis()),
                        remarks = obj.optString("remarks", ""),
                        referenceNo = obj.optString("referenceNo").ifEmpty { null }
                    ))
                }
            }

            // 6. Expenses
            val expensesList = mutableListOf<Expense>()
            val expensesArr = root.optJSONArray("expenses")
            if (expensesArr != null) {
                for (i in 0 until expensesArr.length()) {
                    val obj = expensesArr.getJSONObject(i)
                    expensesList.add(Expense(
                        description = obj.optString("description", ""),
                        amount = obj.optDouble("amount", 0.0),
                        category = obj.getString("category"),
                        date = obj.optLong("date", System.currentTimeMillis())
                    ))
                }
            }

            viewModel.restoreBackup(
                partiesList,
                commoditiesList,
                billsList,
                ledgerList,
                txsList,
                expensesList
            )
        } catch (e: Exception) {
            viewModel.showToast("Failed to parse backup JSON: ${e.localizedMessage}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Restore database tables from a previously exported backup file or template. WARNING: Restoring will overwrite all current local items.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        if (lastBackupDate != null && lastBackupData != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Auto-Saved Backup Detected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Timestamp: $lastBackupDate", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { parseAndApplyRestore(lastBackupData) },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("restore_autosaved_button")
                    ) {
                        Text("Restore This Backup")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Import Custom Backup JSON String", fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = importText,
            onValueChange = { importText = it },
            label = { Text("Paste JSON Payload Here") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("restore_json_input")
        )

        Button(
            onClick = {
                if (importText.isBlank()) {
                    viewModel.showToast("Please enter or paste backup text")
                } else {
                    parseAndApplyRestore(importText)
                }
            },
            enabled = importText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("restore_import_button")
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Validate and Import Backup")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.seedDemoDataIfNeeded()
                    viewModel.showToast("Demo datasets populated")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("restore_seed_demo")
            ) {
                Text("Seed Demo Datasets")
            }

            Button(
                onClick = { showConfirmWipeDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("restore_wipe_database")
            ) {
                Text("Wipe All Tables")
            }
        }
    }

    if (showConfirmWipeDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmWipeDialog = false },
            title = { Text("Delete All Database Records?") },
            text = { Text("Are you absolutely sure you want to delete all invoices, ledger entries, farmers, and transaction histories? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.wipeAllData()
                        showConfirmWipeDialog = false
                    }
                ) {
                    Text("Wipe Database", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmWipeDialog = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("confirm_wipe_dialog")
        )
    }
}

// 8. Printer Settings
@Composable
fun PrinterSettingsView(
    config: ShopConfig,
    onSave: (ShopConfig) -> Unit
) {
    var selectedPrinter by remember { mutableStateOf(config.printerType) }
    var autoPrint by remember { mutableStateOf(config.printAutomatically) }

    var showTestPrintOverlay by remember { mutableStateOf(false) }

    val printerTypes = listOf("Thermal 2-inch", "Thermal 3-inch", "Standard A4")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Set up your hardware receipt printing dimensions and behaviors. Fits thermal Bluetooth rolls or desktop office A4 printers.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Text("Select Printer Connection Format", fontWeight = FontWeight.Bold)

        printerTypes.forEach { type ->
            val isSelected = selectedPrinter == type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedPrinter = type }
                    .testTag("printer_card_$type"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedPrinter = type }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = type, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { autoPrint = !autoPrint }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Print Automatically on Save", fontWeight = FontWeight.Bold)
                Text("Triggers background printing instantly upon creating any invoice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = autoPrint,
                onCheckedChange = { autoPrint = it }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showTestPrintOverlay = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("printer_test_print")
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Hardware Print")
            }

            Button(
                onClick = {
                    onSave(
                        config.copy(
                            printerType = selectedPrinter,
                            printAutomatically = autoPrint
                        )
                    )
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(50.dp)
                    .testTag("printer_save_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Hardware Options")
            }
        }
    }

    if (showTestPrintOverlay) {
        AlertDialog(
            onDismissRequest = { showTestPrintOverlay = false },
            title = { Text("Simulation: Thermal Receipt Test") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "--------------------------------\n" +
                                "     APMC TRADING PORTAL       \n" +
                                "     * HARDWARE TEST PAGE *     \n" +
                                "--------------------------------\n" +
                                "Date: ${SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(Date())}\n" +
                                "Printer: $selectedPrinter\n" +
                                "Status: ONLINE & IN-SYNC\n" +
                                "--------------------------------\n" +
                                "ITEM           QTY     RATE   AMT\n" +
                                "Wheat Bags     10      2400   24K\n" +
                                "--------------------------------\n" +
                                "SUBTOTAL:              INR 24,000\n" +
                                "GST (5%):              INR 1,200\n" +
                                "--------------------------------\n" +
                                "TOTAL NET:             INR 25,200\n" +
                                "--------------------------------\n" +
                                "    Thank you for choosing us!  \n" +
                                "--------------------------------",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = Color.Black)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestPrintOverlay = false }) {
                    Text("Done")
                }
            },
            modifier = Modifier.testTag("test_print_dialog")
        )
    }
}

// 9. Notification Preferences
@Composable
fun NotificationPreferencesView(
    settingsPrefs: SharedPreferences,
    onBack: () -> Unit
) {
    var notifyStock by remember { mutableStateOf(settingsPrefs.getBoolean("notify_stock", true)) }
    var notifyPayment by remember { mutableStateOf(settingsPrefs.getBoolean("notify_payment", true)) }
    var notifyDailyReport by remember { mutableStateOf(settingsPrefs.getBoolean("notify_daily_report", false)) }
    var notifyCustomers by remember { mutableStateOf(settingsPrefs.getBoolean("notify_customers", false)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select when and how the APMC application pushes alert warnings to you or your operator.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    notifyStock = !notifyStock
                    settingsPrefs.edit().putBoolean("notify_stock", notifyStock).apply()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Low Stock Warnings", fontWeight = FontWeight.Bold)
                Text("Alert when bags in warehouse fall below 20 units", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = notifyStock,
                onCheckedChange = {
                    notifyStock = it
                    settingsPrefs.edit().putBoolean("notify_stock", it).apply()
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    notifyPayment = !notifyPayment
                    settingsPrefs.edit().putBoolean("notify_payment", notifyPayment).apply()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Outstanding Payment Warnings", fontWeight = FontWeight.Bold)
                Text("Notify when bills remain unpaid for more than 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = notifyPayment,
                onCheckedChange = {
                    notifyPayment = it
                    settingsPrefs.edit().putBoolean("notify_payment", it).apply()
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    notifyDailyReport = !notifyDailyReport
                    settingsPrefs.edit().putBoolean("notify_daily_report", notifyDailyReport).apply()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Summarize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily Closing Reports", fontWeight = FontWeight.Bold)
                Text("Automatically push end-of-day summary on business hours close", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = notifyDailyReport,
                onCheckedChange = {
                    notifyDailyReport = it
                    settingsPrefs.edit().putBoolean("notify_daily_report", it).apply()
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    notifyCustomers = !notifyCustomers
                    settingsPrefs.edit().putBoolean("notify_customers", notifyCustomers).apply()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("New Customer Registrations", fontWeight = FontWeight.Bold)
                Text("Alert when staff adds new profiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = notifyCustomers,
                onCheckedChange = {
                    notifyCustomers = it
                    settingsPrefs.edit().putBoolean("notify_customers", it).apply()
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("notifications_save_button")
        ) {
            Text("Done")
        }
    }
}

// 10. User Management
@Composable
fun UserManagementView(
    currentUser: UserSession?,
    authPrefs: SharedPreferences,
    viewModel: BillingViewModel,
    onBack: () -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var roleSelection by remember { mutableStateOf(UserRole.STAFF) }

    var showAddUserDialog by remember { mutableStateOf(false) }

    // Read all locally registered users from shared preferences
    val registeredUsers = remember(showAddUserDialog) {
        val list = mutableListOf<UserSession>()
        val allKeys = authPrefs.all
        for ((key, value) in allKeys) {
            if (key.startsWith("reg_name_") && value is String) {
                val email = key.substringAfter("reg_name_")
                val name = value
                val phone = authPrefs.getString("reg_phone_$email", "") ?: ""
                val roleStr = authPrefs.getString("reg_role_$email", "STAFF") ?: "STAFF"
                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.STAFF }
                list.add(UserSession(email, phone, name, role, ""))
            }
        }
        // Always include current default system accounts if they aren't explicitly registered
        val hasOwner = list.any { it.email == "owner@apmc.com" }
        if (!hasOwner) {
            list.add(UserSession("owner@apmc.com", "9999999999", "Owner Merchant", UserRole.OWNER, ""))
        }
        val hasAdmin = list.any { it.email == "admin@apmc.com" }
        if (!hasAdmin) {
            list.add(UserSession("admin@apmc.com", "8888888888", "Admin Operator", UserRole.ADMIN, ""))
        }
        val hasStaff = list.any { it.email == "staff@apmc.com" }
        if (!hasStaff) {
            list.add(UserSession("staff@apmc.com", "7777777777", "Staff Billing Clerks", UserRole.STAFF, ""))
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add and remove system accounts to authorize clerks or managers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { showAddUserDialog = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("users_add_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add User")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(registeredUsers) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when (user.role) {
                                        UserRole.OWNER -> Color(0xFFC62828).copy(alpha = 0.1f)
                                        UserRole.ADMIN -> Color(0xFF1565C0).copy(alpha = 0.1f)
                                        UserRole.STAFF -> Color(0xFF2E7D32).copy(alpha = 0.1f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = when (user.role) {
                                    UserRole.OWNER -> Color(0xFFC62828)
                                    UserRole.ADMIN -> Color(0xFF1565C0)
                                    UserRole.STAFF -> Color(0xFF2E7D32)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.width(6.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (user.role) {
                                            UserRole.OWNER -> Color(0xFFFFEBEE)
                                            UserRole.ADMIN -> Color(0xFFE3F2FD)
                                            UserRole.STAFF -> Color(0xFFE8F5E9)
                                        }
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = user.role.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (user.role) {
                                            UserRole.OWNER -> Color(0xFFC62828)
                                            UserRole.ADMIN -> Color(0xFF1565C0)
                                            UserRole.STAFF -> Color(0xFF2E7D32)
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            if (user.phone.isNotEmpty()) {
                                Text("Phone: ${user.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // Do not allow deleting yourself or default owner
                        if (user.email != currentUser?.email && user.email != "owner@apmc.com") {
                            IconButton(
                                onClick = {
                                    authPrefs.edit()
                                        .remove("reg_pw_${user.email}")
                                        .remove("reg_name_${user.email}")
                                        .remove("reg_phone_${user.email}")
                                        .remove("reg_role_${user.email}")
                                        .apply()
                                    viewModel.showToast("Removed user account ${user.name}")
                                    // Trigger list update by toggling fake dialog state or re-render
                                    showAddUserDialog = false
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete account", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add New Operator Account") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Full Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_add_name")
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_add_email")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_add_phone")
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Account Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_add_password")
                    )

                    Text("System Authorization Role", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(UserRole.STAFF, UserRole.ADMIN, UserRole.OWNER).forEach { role ->
                            val isSelected = roleSelection == role
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { roleSelection = role }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = role.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isBlank() || emailInput.isBlank() || passwordInput.isBlank()) {
                            viewModel.showToast("Please enter Name, Email, and Password")
                        } else {
                            authPrefs.edit()
                                .putString("reg_pw_$emailInput", passwordInput)
                                .putString("reg_name_$emailInput", nameInput)
                                .putString("reg_phone_$emailInput", phoneInput)
                                .putString("reg_role_$emailInput", roleSelection.name)
                                .apply()
                            viewModel.showToast("Operator account registered!")
                            
                            // reset inputs
                            nameInput = ""
                            emailInput = ""
                            phoneInput = ""
                            passwordInput = ""
                            roleSelection = UserRole.STAFF
                            showAddUserDialog = false
                        }
                    },
                    modifier = Modifier.testTag("user_add_confirm")
                ) {
                    Text("Register User")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("add_user_dialog")
        )
    }
}

@Composable
fun OfflineSyncSettingsView(
    viewModel: BillingViewModel,
    onBack: () -> Unit
) {
    val isOnline by viewModel.isOnline.collectAsState()
    val isManualOffline by viewModel.isManualOffline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val conflictPolicy by viewModel.conflictResolutionPolicy.collectAsState()
    val autoRetryEnabled by viewModel.autoRetryEnabled.collectAsState()
    val syncLog by viewModel.syncLog.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configure offline operations, monitor real-time background cloud synchronization, and select conflict resolution rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        // 1. Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("sync_status_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOnline) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Sync Connection Status",
                        tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOnline) "Status: Online (Auto Sync active)" else "Status: Offline Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isOnline) "Connected to Supabase cloud" else "All transactions are saved locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = isManualOffline,
                    onCheckedChange = { viewModel.toggleManualOffline() },
                    modifier = Modifier.testTag("toggle_manual_offline")
                )
            }
        }

        // 2. Queue Status and Action Row
        Card(
            modifier = Modifier.fillMaxWidth().testTag("sync_queue_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pending Sync Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$pendingSyncCount items waiting to sync",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).testTag("syncing_indicator"),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = { viewModel.triggerBackgroundSync() },
                            enabled = isOnline,
                            modifier = Modifier.testTag("sync_now_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now")
                        }
                    }
                }
                if (pendingSyncCount > 0) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // 3. Configuration & Policy Selection
        Text(
            text = "Conflict Resolution Strategy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        val policies = listOf(
            "CLIENT_WINS" to "Client Wins (Keep local, overwrite cloud)",
            "SERVER_WINS" to "Server Wins (Overwrites local values with cloud)",
            "ASK_USER" to "Manual Resolve (Prompt to compare & choose)"
        )

        policies.forEach { (key, label) ->
            val isSelected = conflictPolicy == key
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setConflictPolicy(key) }
                    .testTag("policy_card_$key"),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setConflictPolicy(key) },
                        modifier = Modifier.testTag("radio_policy_$key")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 4. Auto-Retry switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggleAutoRetry() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Loop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Automatic Sync Retries", fontWeight = FontWeight.Bold)
                Text("Periodically attempts to retry failed synchronizations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(
                checked = autoRetryEnabled,
                onCheckedChange = { viewModel.toggleAutoRetry() },
                modifier = Modifier.testTag("switch_auto_retry")
            )
        }

        // 5. Sync Logs Console
        Text(
            text = "Sync Activity Console",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .testTag("sync_log_console"),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E) // Slate dark console
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (syncLog.isEmpty()) {
                    item {
                        Text(
                            text = "No sync activities recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888)
                        )
                    }
                } else {
                    items(syncLog) { logMsg ->
                        Text(
                            text = logMsg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (logMsg.contains("failed") || logMsg.contains("Error")) Color(0xFFFF6B6B)
                                    else if (logMsg.contains("Conflict") || logMsg.contains("Waiting")) Color(0xFFFFD166)
                                    else Color(0xFF06D6A0) // Retro-terminal green
                        )
                    }
                }
            }
        }
    }
}
