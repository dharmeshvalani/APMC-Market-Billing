package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShopConfig
import com.example.ui.BillingViewModel
import com.example.ui.components.Localization
import java.util.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSetupWizardScreen(viewModel: BillingViewModel) {
    val shopConfig by viewModel.shopConfig.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    var currentStep by remember { mutableIntStateOf(1) }

    // Temporary Wizard state variables initialized with current values
    var shopName by remember(shopConfig) { mutableStateOf(shopConfig.shopName) }
    var ownerName by remember(shopConfig) { mutableStateOf(shopConfig.ownerName) }
    var gstNumber by remember(shopConfig) { mutableStateOf(shopConfig.gstNumber) }
    var apmcLicense by remember(shopConfig) { mutableStateOf(shopConfig.apmcLicense) }
    var address by remember(shopConfig) { mutableStateOf(shopConfig.address) }
    var logoUrl by remember(shopConfig) { mutableStateOf(shopConfig.logoUrl) }
    var logoPreset by remember(shopConfig) { mutableStateOf(shopConfig.logoPreset) }
    var invoicePrefix by remember(shopConfig) { mutableStateOf(shopConfig.invoicePrefix) }
    var upiId by remember(shopConfig) { mutableStateOf(shopConfig.upiId) }
    var bankName by remember(shopConfig) { mutableStateOf(shopConfig.bankName) }
    var bankAccName by remember(shopConfig) { mutableStateOf(shopConfig.bankAccName) }
    var bankAccNum by remember(shopConfig) { mutableStateOf(shopConfig.bankAccNum) }
    var bankIfsc by remember(shopConfig) { mutableStateOf(shopConfig.bankIfsc) }
    var printerType by remember(shopConfig) { mutableStateOf(shopConfig.printerType) }
    var printAutomatically by remember(shopConfig) { mutableStateOf(shopConfig.printAutomatically) }
    var theme by remember(shopConfig) { mutableStateOf(shopConfig.theme) }
    var language by remember(shopConfig) { mutableStateOf(shopConfig.language) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    // Localized helper
    fun l(key: String): String {
        return Localization.translate(key, language)
    }

    // Update active language in real time to let labels morph instantly
    fun setLanguage(lang: String) {
        language = lang
        // also save language config locally on change for instant feedback
        val updatedConfig = shopConfig.copy(language = lang)
        viewModel.saveShopConfig(updatedConfig)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = l("wizard_title"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mandi Merchant Setup",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Quick language switcher in TopBar
                    listOf("English", "Hindi", "Marathi", "Telugu").forEach { lang ->
                        val isSelected = language == lang
                        TextButton(
                            onClick = { setLanguage(lang) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = when(lang) {
                                    "English" -> "EN"
                                    "Hindi" -> "हिं"
                                    "Marathi" -> "मरा"
                                    else -> "తె"
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // STEP PROGRESS TRACKER HEADER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = l("wizard_subtitle"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Stepper row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 1..5) {
                            val isActive = currentStep == i
                            val isCompleted = currentStep > i
                            val color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else if (isCompleted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable(enabled = isCompleted) { currentStep = i },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        text = i.toString(),
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (i < 5) {
                                val dividerColor = if (currentStep > i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(dividerColor)
                                )
                            }
                        }
                    }

                    // Stepper Labels
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("step_1_title", "step_2_title", "step_3_title", "step_4_title", "step_5_title").forEachIndexed { idx, key ->
                            val isSelected = currentStep == (idx + 1)
                            Text(
                                text = l(key),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // WIZARD BODY CONTENT with horizontal transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                when (currentStep) {
                    1 -> StepGeneralInfo(
                        shopName = shopName,
                        onShopNameChange = { shopName = it },
                        ownerName = ownerName,
                        onOwnerNameChange = { ownerName = it },
                        logoPreset = logoPreset,
                        onLogoPresetChange = { logoPreset = it },
                        logoUrl = logoUrl,
                        onLogoUrlChange = { logoUrl = it },
                        language = language,
                        onLanguageChange = { setLanguage(it) },
                        l = ::l
                    )
                    2 -> StepLegalDetails(
                        gstNumber = gstNumber,
                        onGstNumberChange = { gstNumber = it },
                        apmcLicense = apmcLicense,
                        onApmcLicenseChange = { apmcLicense = it },
                        address = address,
                        onAddressChange = { address = it },
                        invoicePrefix = invoicePrefix,
                        onInvoicePrefixChange = { invoicePrefix = it },
                        l = ::l
                    )
                    3 -> StepPaymentDetails(
                        upiId = upiId,
                        onUpiIdChange = { upiId = it },
                        bankName = bankName,
                        onBankNameChange = { bankName = it },
                        bankAccName = bankAccName,
                        onBankAccNameChange = { bankAccName = it },
                        bankAccNum = bankAccNum,
                        onBankAccNumChange = { bankAccNum = it },
                        bankIfsc = bankIfsc,
                        onBankIfscChange = { bankIfsc = it },
                        l = ::l
                    )
                    4 -> StepPreferences(
                        printerType = printerType,
                        onPrinterTypeChange = { printerType = it },
                        printAutomatically = printAutomatically,
                        onPrintAutomaticallyChange = { printAutomatically = it },
                        theme = theme,
                        onThemeChange = { theme = it },
                        l = ::l
                    )
                    5 -> StepReviewSync(
                        shopName = shopName,
                        ownerName = ownerName,
                        gstNumber = gstNumber,
                        apmcLicense = apmcLicense,
                        address = address,
                        logoPreset = logoPreset,
                        invoicePrefix = invoicePrefix,
                        upiId = upiId,
                        bankName = bankName,
                        bankAccNum = bankAccNum,
                        bankIfsc = bankIfsc,
                        printerType = printerType,
                        theme = theme,
                        language = language,
                        l = ::l
                    )
                }
            }

            // NAVIGATION BUTTONS PANEL
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = l("back"))
                        }
                    } else {
                        // Allow skipping only if already completed previously
                        if (shopConfig.isCompleted) {
                            TextButton(onClick = { viewModel.navigateTo("dashboard") }) {
                                Text(text = l("skip"))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }

                    // Next / Complete button
                    val isFormValid = when (currentStep) {
                        1 -> shopName.isNotBlank() && ownerName.isNotBlank()
                        2 -> gstNumber.isNotBlank() && apmcLicense.isNotBlank() && address.isNotBlank()
                        3 -> upiId.isNotBlank() && bankName.isNotBlank() && bankAccNum.isNotBlank()
                        else -> true
                    }

                    Button(
                        onClick = {
                            if (currentStep < 5) {
                                currentStep++
                            } else {
                                // Save configuration object
                                val newConfig = ShopConfig(
                                    shopName = shopName.trim(),
                                    ownerName = ownerName.trim(),
                                    gstNumber = gstNumber.trim().uppercase(),
                                    apmcLicense = apmcLicense.trim(),
                                    address = address.trim(),
                                    logoUrl = logoUrl.trim(),
                                    logoPreset = logoPreset,
                                    invoicePrefix = invoicePrefix.trim().uppercase(),
                                    upiId = upiId.trim().lowercase(),
                                    bankName = bankName.trim(),
                                    bankAccName = bankAccName.trim(),
                                    bankAccNum = bankAccNum.trim(),
                                    bankIfsc = bankIfsc.trim().uppercase(),
                                    printerType = printerType,
                                    printAutomatically = printAutomatically,
                                    theme = theme,
                                    language = language,
                                    isCompleted = true
                                )
                                viewModel.saveShopConfig(newConfig) {
                                    viewModel.navigateTo("dashboard")
                                }
                            }
                        },
                        enabled = isFormValid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(min = 140.dp)
                    ) {
                        val label = if (currentStep == 5) l("finish_setup") else l("save_changes")
                        Text(text = label, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (currentStep == 5) Icons.Default.CloudSync else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

// STEP 1: General Info Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepGeneralInfo(
    shopName: String,
    onShopNameChange: (String) -> Unit,
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    logoPreset: String,
    onLogoPresetChange: (String) -> Unit,
    logoUrl: String,
    onLogoUrlChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    l: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Step 1: " + l("step_1_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = shopName,
            onValueChange = onShopNameChange,
            label = { Text(l("shop_name")) },
            placeholder = { Text("e.g. Balaji Agri Merchants") },
            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = ownerName,
            onValueChange = onOwnerNameChange,
            label = { Text(l("owner_name")) },
            placeholder = { Text("e.g. Ramesh Kumar") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // BRANDING PRESET SELECTOR
        Text(
            text = l("preset_logo"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val presets = listOf(
                Triple("GRAINS", Icons.Default.Grass, Color(0xFFE5A93B)),
                Triple("VEGETABLES", Icons.Default.Eco, Color(0xFF4CAF50)),
                Triple("COTTON", Icons.Default.Cloud, Color(0xFF607D8B)),
                Triple("FRUITS", Icons.Default.Yard, Color(0xFFFF5722)),
                Triple("STORE", Icons.Default.Storefront, Color(0xFF2196F3))
            )

            presets.forEach { (preset, icon, color) ->
                val isSelected = logoPreset == preset
                Card(
                    onClick = { onLogoPresetChange(preset) },
                    modifier = Modifier
                        .width(110.dp)
                        .height(95.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            color.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = preset,
                                tint = color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Optional Logo URL
        OutlinedTextField(
            value = logoUrl,
            onValueChange = onLogoUrlChange,
            label = { Text(l("logo_label") + " (URL)") },
            placeholder = { Text("https://example.com/logo.png") },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// STEP 2: Legal & Licenses Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepLegalDetails(
    gstNumber: String,
    onGstNumberChange: (String) -> Unit,
    apmcLicense: String,
    onApmcLicenseChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    invoicePrefix: String,
    onInvoicePrefixChange: (String) -> Unit,
    l: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Step 2: " + l("step_2_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = gstNumber,
            onValueChange = { onGstNumberChange(it.uppercase()) },
            label = { Text(l("gst_number")) },
            placeholder = { Text("e.g. 27AAAAA1111A1Z1") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = apmcLicense,
            onValueChange = onApmcLicenseChange,
            label = { Text(l("apmc_license")) },
            placeholder = { Text("e.g. APMC/MKT/2026/849") },
            leadingIcon = { Icon(Icons.Default.Approval, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text(l("address")) },
            placeholder = { Text("Detailed warehouse, gate, or block details...") },
            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
            minLines = 3,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = invoicePrefix,
            onValueChange = onInvoicePrefixChange,
            label = { Text(l("invoice_prefix")) },
            placeholder = { Text("e.g. ATC/") },
            leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Real-time bill preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Real-time Bill format preview:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Patti (Purchase): ${invoicePrefix}P-001\nTax Invoice (Sale): ${invoicePrefix}S-1004",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// STEP 3: Payment Composable with Live Morphing QR Code Canvas!
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepPaymentDetails(
    upiId: String,
    onUpiIdChange: (String) -> Unit,
    bankName: String,
    onBankNameChange: (String) -> Unit,
    bankAccName: String,
    onBankAccNameChange: (String) -> Unit,
    bankAccNum: String,
    onBankAccNumChange: (String) -> Unit,
    bankIfsc: String,
    onBankIfscChange: (String) -> Unit,
    l: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Step 3: " + l("step_3_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        // UPI and LIVE QR Code Generator side-by-side or stacked
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Live Digital Payments QR Code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // LIVE QR CODE GENERATOR CANVAS
                LiveQrCodeCanvas(upiId = upiId)

                Text(
                    text = if (upiId.isNotBlank()) "UPI: $upiId" else "Enter UPI VPA to generate QR Code",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (upiId.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedTextField(
            value = upiId,
            onValueChange = { onUpiIdChange(it.trim().lowercase()) },
            label = { Text(l("upi_id")) },
            placeholder = { Text("e.g. svalani83@okaxis") },
            leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = bankName,
            onValueChange = onBankNameChange,
            label = { Text(l("bank_name")) },
            placeholder = { Text("e.g. State Bank of India") },
            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = bankAccName,
                onValueChange = onBankAccNameChange,
                label = { Text(l("bank_acc_name")) },
                placeholder = { Text("APMC Trading Co.") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = bankAccNum,
                onValueChange = onBankAccNumChange,
                label = { Text(l("bank_acc_num")) },
                placeholder = { Text("12345678901") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = bankIfsc,
            onValueChange = { onBankIfscChange(it.uppercase()) },
            label = { Text(l("bank_ifsc")) },
            placeholder = { Text("e.g. SBIN0001234") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// STUNNING MORPHING UPI QR CODE CANVAS!
@Composable
fun LiveQrCodeCanvas(upiId: String) {
    val hash = remember(upiId) {
        upiId.hashCode().absoluteValue
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val size2D = 10 // 10x10 grid of mock QR code blocks
            val blockSizeX = size.width / size2D
            val blockSizeY = size.height / size2D

            // Drawing finder patterns in the 3 corners (Top-Left, Top-Right, Bottom-Left)
            fun drawFinder(offsetX: Float, offsetY: Float) {
                // Outer 3x3 block
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(offsetX, offsetY),
                    size = Size(blockSizeX * 3f, blockSizeY * 3f)
                )
                // Inner 2x2 white
                drawRect(
                    color = Color.White,
                    topLeft = Offset(offsetX + blockSizeX * 0.5f, offsetY + blockSizeY * 0.5f),
                    size = Size(blockSizeX * 2f, blockSizeY * 2f)
                )
                // Core 1x1 black
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(offsetX + blockSizeX * 1f, offsetY + blockSizeY * 1f),
                    size = Size(blockSizeX * 1f, blockSizeY * 1f)
                )
            }

            // Draw finders
            drawFinder(0f, 0f) // Top Left
            drawFinder(size.width - blockSizeX * 3f, 0f) // Top Right
            drawFinder(0f, size.height - blockSizeY * 3f) // Bottom Left

            // Generate deterministic pixel pattern based on UPI ID Hash
            val random = Random(hash.toLong())
            for (x in 0 until size2D) {
                for (y in 0 until size2D) {
                    // Skip finders coordinates
                    val isTopLeftFinder = x < 3 && y < 3
                    val isTopRightFinder = x >= size2D - 3 && y < 3
                    val isBottomLeftFinder = x < 3 && y >= size2D - 3
                    val isCenterQuietZone = x in 4..5 && y in 4..5 // leave center for rupee symbol

                    if (!isTopLeftFinder && !isTopRightFinder && !isBottomLeftFinder && !isCenterQuietZone) {
                        val isBlack = random.nextBoolean()
                        if (isBlack && upiId.isNotBlank()) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * blockSizeX, y * blockSizeY),
                                size = Size(blockSizeX, blockSizeY)
                            )
                        }
                    }
                }
            }

            // Draw center premium circular coin for the Merchant branding
            val centerRadius = blockSizeX * 1.5f
            drawCircle(
                color = Color.White,
                radius = centerRadius,
                center = Offset(size.width / 2f, size.height / 2f)
            )

            drawCircle(
                color = Color(0xFFE5A93B), // gold ring
                radius = centerRadius - 2f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Place a gorgeous small rupee logo right in the middle
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFE5A93B).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "₹",
                color = Color(0xFFE5A93B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

// STEP 4: Printer Preferences & Theme
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepPreferences(
    printerType: String,
    onPrinterTypeChange: (String) -> Unit,
    printAutomatically: Boolean,
    onPrintAutomaticallyChange: (Boolean) -> Unit,
    theme: String,
    onThemeChange: (String) -> Unit,
    l: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Step 4: " + l("step_4_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        // PRINTER OPTION CARDS
        Text(
            text = l("printer_settings"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val printers = listOf(
            Triple("Thermal 2-inch", "Bluetooth handheld thermal receipts (58mm)", Icons.Default.MobileFriendly),
            Triple("Thermal 3-inch", "Desktop counter Bluetooth/USB receipts (80mm)", Icons.Default.Print),
            Triple("Standard A4", "Standard laser/inkjet full reports & invoices", Icons.Default.Description)
        )

        printers.forEach { (type, desc, icon) ->
            val isSelected = printerType == type
            Card(
                onClick = { onPrinterTypeChange(type) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = type,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = { onPrinterTypeChange(type) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // AUTO PRINT SWITCH
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = l("print_auto"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = printAutomatically,
                    onCheckedChange = onPrintAutomaticallyChange
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // SYSTEM THEME CHOICE
        Text(
            text = l("theme"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Light", "Dark", "System").forEach { t ->
                val isSelected = theme == t
                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = color,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onThemeChange(t) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when(t) {
                                "Light" -> Icons.Default.LightMode
                                "Dark" -> Icons.Default.DarkMode
                                else -> Icons.Default.SettingsSuggest
                            },
                            contentDescription = t,
                            modifier = Modifier.size(16.dp),
                            tint = color
                        )
                        Text(
                            text = t,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

// STEP 5: Review & Cloud Sync Composable
@Composable
fun StepReviewSync(
    shopName: String,
    ownerName: String,
    gstNumber: String,
    apmcLicense: String,
    address: String,
    logoPreset: String,
    invoicePrefix: String,
    upiId: String,
    bankName: String,
    bankAccNum: String,
    bankIfsc: String,
    printerType: String,
    theme: String,
    language: String,
    l: (String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Step 5: " + l("step_5_title"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // PREMIUM BRANDEED MERCHANT CARD
        val colorPreset = when (logoPreset) {
            "GRAINS" -> Color(0xFFE5A93B)
            "VEGETABLES" -> Color(0xFF4CAF50)
            "COTTON" -> Color(0xFF607D8B)
            "FRUITS" -> Color(0xFFFF5722)
            else -> Color(0xFF2196F3)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Elegant background visual dots/glow
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column {
                // Header of ID card with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(colorPreset, colorPreset.copy(alpha = 0.7f))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (logoPreset) {
                                    "GRAINS" -> Icons.Default.Grass
                                    "VEGETABLES" -> Icons.Default.Eco
                                    "COTTON" -> Icons.Default.Cloud
                                    "FRUITS" -> Icons.Default.Yard
                                    else -> Icons.Default.Storefront
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = shopName.uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "LICENSED APMC COMMISSION AGENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // Details list
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("OWNER MERCHANT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ownerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("GST NUMBER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(gstNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("APMC LICENSE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(apmcLicense, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BILL PREFIX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(invoicePrefix, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // QR and Bank Account details side by side
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LiveQrCodeCanvas(upiId = upiId)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("BANK DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(bankName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("A/C: *******${bankAccNum.takeLast(4)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("IFSC: $bankIfsc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Printer: $printerType", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text("Language: $language", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // SYNC IN PROGRESS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Infinitely spinning cloud icon
                val infiniteTransition = rememberInfiniteTransition(label = "cloud_sync")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "cloud_spin"
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Supabase Cloud Database Synchronization",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = l("syncing"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
