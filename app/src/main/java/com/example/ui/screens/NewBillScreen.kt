package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Commodity
import com.example.data.Party
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBillScreen(
    viewModel: BillingViewModel,
    onBillCreated: () -> Unit
) {
    val parties by viewModel.parties.collectAsState()
    val commodities by viewModel.commodities.collectAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    // Tab State: 0 = Farmer Purchase Patti, 1 = Trader Sales Invoice
    var billTypeTab by remember { mutableIntStateOf(0) }
    val activePartyType = if (billTypeTab == 0) "FARMER" else "TRADER"

    // Focus Manager & Focus Requesters for Keyboard Navigation & Shortcuts
    val focusManager = LocalFocusManager.current
    val customerSearchFocusRequester = remember { FocusRequester() }
    val productSearchFocusRequester = remember { FocusRequester() }
    val bagsFocusRequester = remember { FocusRequester() }
    val weightFocusRequester = remember { FocusRequester() }
    val rateFocusRequester = remember { FocusRequester() }
    val discountFocusRequester = remember { FocusRequester() }
    val gstFocusRequester = remember { FocusRequester() }
    val transportFocusRequester = remember { FocusRequester() }
    val laborFocusRequester = remember { FocusRequester() }
    val commissionFocusRequester = remember { FocusRequester() }
    val roundOffFocusRequester = remember { FocusRequester() }
    val paymentNotesFocusRequester = remember { FocusRequester() }

    // Dialog states
    var showQuickAddPartyDialog by remember { mutableStateOf(false) }
    var showInvoiceSuccessDialog by remember { mutableStateOf(false) }

    // Real-time Selected entities & search queries
    var customerSearchQuery by remember { mutableStateOf("") }
    var productSearchQuery by remember { mutableStateOf("") }
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    var selectedCommodity by remember { mutableStateOf<Commodity?>(null) }

    // Input States corresponding to sequential workflow steps
    var bagCountString by remember { mutableStateOf("") }
    var weightString by remember { mutableStateOf("") } // in Quintals
    var discountString by remember { mutableStateOf("") } // flat discount ₹
    var gstString by remember { mutableStateOf("") } // GST percentage
    var transportString by remember { mutableStateOf("0") } // transport cost
    var laborString by remember { mutableStateOf("15") } // labor hamali ₹ per bag
    var commissionString by remember { mutableStateOf("5") } // commission percentage %
    var roundOffString by remember { mutableStateOf("0") } // manual/auto round-off
    var isPaidPayment by remember { mutableStateOf(false) } // Fully Paid (Cash/Bank) vs On Credit
    var paymentMode by remember { mutableStateOf("CASH") } // "CASH", "BANK_TRANSFER", "CREDIT"
    var paymentNotesString by remember { mutableStateOf("") }

    // Currently Active Step in Workflow (1 to 11) for highlighting UI path
    var activeStep by remember { mutableIntStateOf(1) }

    // Reset fields on Tab swap
    LaunchedEffect(billTypeTab) {
        selectedParty = null
        selectedCommodity = null
        customerSearchQuery = ""
        productSearchQuery = ""
        bagCountString = ""
        weightString = ""
        discountString = ""
        gstString = ""
        transportString = "0"
        laborString = "15"
        commissionString = "5"
        roundOffString = "0"
        isPaidPayment = false
        paymentMode = "CREDIT"
        paymentNotesString = ""
        activeStep = 1
    }

    // Auto-prefill product values upon selection
    LaunchedEffect(selectedCommodity) {
        selectedCommodity?.let {
            val rateValue = if (it.sellingPrice > 0.0) it.sellingPrice else it.currentRate
            discountString = "0"
            gstString = it.gstPercent.toString()
            commissionString = it.commissionPercent.toString()
            activeStep = 3 // advance to quantity
        }
    }

    // Calculations Flow (Real-time)
    val bagCount = bagCountString.toIntOrNull() ?: 0
    val weight = weightString.toDoubleOrNull() ?: 0.0
    val defaultRate = selectedCommodity?.let { if (it.sellingPrice > 0.0) it.sellingPrice else it.currentRate } ?: 0.0
    val rate = defaultRate // Per quintal rate

    // Subtotal math
    val grossAmount = weight * rate
    val discountAmount = discountString.toDoubleOrNull() ?: 0.0
    val taxPercent = gstString.toDoubleOrNull() ?: 0.0
    val taxAmount = (grossAmount - discountAmount).coerceAtLeast(0.0) * (taxPercent / 100.0)
    val transportCharges = transportString.toDoubleOrNull() ?: 0.0
    val laborPerBag = laborString.toDoubleOrNull() ?: 0.0
    val laborChargesTotal = bagCount * laborPerBag
    val commissionPercent = commissionString.toDoubleOrNull() ?: 5.0
    val commissionAmount = grossAmount * (commissionPercent / 100.0)
    val manualRoundOff = roundOffString.toDoubleOrNull() ?: 0.0

    // APMC Market Yard Fee
    val marketFeePercent = selectedCommodity?.marketFeePercent ?: 1.0
    val marketFeeAmount = grossAmount * (marketFeePercent / 100.0)

    // Workflow Formula:
    // Farmer Purchase Patti: deductions from gross sales amount, subtract discount, subtract transport, subtract labor, subtract commission, subtract market fee, add round off
    // Trader Sale Invoice: additions on top of gross amount, subtract discount, add GST, add transport, add labor, add commission, add market fee, add round off
    val rawNetAmount = if (billTypeTab == 0) {
        grossAmount - discountAmount - commissionAmount - laborChargesTotal - transportCharges - marketFeeAmount + manualRoundOff
    } else {
        grossAmount - discountAmount + taxAmount + commissionAmount + laborChargesTotal + transportCharges + marketFeeAmount + manualRoundOff
    }
    val netAmount = rawNetAmount.coerceAtLeast(0.0)

    // Pre-computed lists for instant search
    val matchingParties = remember(parties, customerSearchQuery, billTypeTab) {
        parties.filter {
            it.type == activePartyType && 
            (customerSearchQuery.isBlank() || it.name.contains(customerSearchQuery, ignoreCase = true) || it.phone.contains(customerSearchQuery))
        }
    }

    val matchingCommodities = remember(commodities, productSearchQuery) {
        commodities.filter {
            productSearchQuery.isBlank() || it.name.contains(productSearchQuery, ignoreCase = true) || it.category.contains(productSearchQuery, ignoreCase = true)
        }
    }

    // Handle Quick Round Off math
    val roundToNearestRupeeDiff = remember(netAmount) {
        val rounded = Math.round(netAmount).toDouble()
        rounded - netAmount
    }
    val roundToNearestTenDiff = remember(netAmount) {
        val rounded = (Math.round(netAmount / 10.0) * 10).toDouble()
        rounded - netAmount
    }

    if (parties.isEmpty() || commodities.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyStatePlaceholder(
                title = "Setup Required",
                subtitle = "To generate an APMC Market Bill, you must first register at least one Farmer/Trader Party and add a Commodity crop to your stocks catalog.",
                icon = Icons.Default.Inventory
            )
        }
        return
    }

    // Top Header Step Progress Tracker Row
    val workflowSteps = listOf(
        Triple(1, "Customer", Icons.Default.Person),
        Triple(2, "Products", Icons.Default.Category),
        Triple(3, "Quantity", Icons.Default.Scale),
        Triple(4, "Discount", Icons.Default.LocalOffer),
        Triple(5, "GST/Tax", Icons.Default.Description),
        Triple(6, "Transport", Icons.Default.LocalShipping),
        Triple(7, "Hamali", Icons.Default.DirectionsRun),
        Triple(8, "Yard Fee", Icons.Default.HomeWork),
        Triple(9, "Round Off", Icons.Default.Tune),
        Triple(10, "Payment", Icons.Default.Payments),
        Triple(11, "Invoice", Icons.Default.ReceiptLong)
    )

    // Master Layout with global keyboard preview hooks
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pos_billing_root")
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.F1 -> {
                            customerSearchFocusRequester.requestFocus()
                            activeStep = 1
                            true
                        }
                        Key.F2 -> {
                            productSearchFocusRequester.requestFocus()
                            activeStep = 2
                            true
                        }
                        Key.F3 -> {
                            bagsFocusRequester.requestFocus()
                            activeStep = 3
                            true
                        }
                        Key.F4 -> {
                            discountFocusRequester.requestFocus()
                            activeStep = 4
                            true
                        }
                        Key.F5 -> {
                            transportFocusRequester.requestFocus()
                            activeStep = 6
                            true
                        }
                        Key.F9 -> {
                            // Quick Trigger Invoice Submit if valid
                            if (selectedParty != null && selectedCommodity != null && bagCount > 0 && weight > 0.0) {
                                viewModel.createBill(
                                    party = selectedParty!!,
                                    commodity = selectedCommodity!!,
                                    bagCount = bagCount,
                                    weight = weight,
                                    rate = rate,
                                    laborChargesPerBag = laborPerBag,
                                    transportCharges = transportCharges,
                                    taxPercent = taxPercent,
                                    discountAmount = discountAmount,
                                    roundOff = manualRoundOff,
                                    netAmount = netAmount,
                                    isPaid = isPaidPayment,
                                    paymentNotes = paymentNotesString
                                )
                                showInvoiceSuccessDialog = true
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Workflow Sequential Progress Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "APMC Sequential POS Workflow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Hotkeys: F1 (Cust) • F2 (Prod) • F3 (Qty) • F4 (Disc) • F9 (Save)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    workflowSteps.forEach { (stepNum, label, icon) ->
                        val isActive = activeStep == stepNum
                        val isCompleted = activeStep > stepNum
                        val tintColor = if (isActive) MaterialTheme.colorScheme.primary 
                                        else if (isCompleted) Color(0xFF4CAF50) 
                                        else MaterialTheme.colorScheme.outlineVariant

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = tintColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$stepNum. $label",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isCompleted) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Done",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (stepNum < 11) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("→", color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Responsive Panel Split
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left Form Column (Fills remaining on mobile, split on tablet)
            Column(
                modifier = Modifier
                    .weight(if (isTablet) 1.1f else 1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Inward / Outward billing type switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ElevatedFilterChip(
                        selected = billTypeTab == 0,
                        onClick = { billTypeTab = 0 },
                        label = { Text("Farmer Patti (Inward Purchase)", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Inward") },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                    ElevatedFilterChip(
                        selected = billTypeTab == 1,
                        onClick = { billTypeTab = 1 },
                        label = { Text("Trader Invoice (Outward Sale)", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Outward") },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                }

                // Step 1: Customer (Farmer/Trader Directory Lookup)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.AccountBox, contentDescription = "Cust", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (billTypeTab == 0) "Step 1: Farmer Selection" else "Step 1: Buyer Trader Selection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = { showQuickAddPartyDialog = true }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Quick Register")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Customer search text box
                        OutlinedTextField(
                            value = customerSearchQuery,
                            onValueChange = {
                                customerSearchQuery = it
                                activeStep = 1
                                if (selectedParty != null && selectedParty?.name != it) {
                                    selectedParty = null
                                }
                            },
                            label = { Text("Search Customer by Name, ID, or Phone") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(customerSearchFocusRequester)
                                .onFocusChanged { if (it.isFocused) activeStep = 1 }
                                .testTag("search_party_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            trailingIcon = {
                                if (customerSearchQuery.isNotEmpty() || selectedParty != null) {
                                    IconButton(onClick = {
                                        customerSearchQuery = ""
                                        selectedParty = null
                                    }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )

                        // If selected, show information panel; else show matching autocomplete recommendations
                        if (selectedParty != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(selectedParty!!.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text("Phone: ${selectedParty!!.phone} • Bal: ₹${selectedParty!!.balance}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    if (!selectedParty!!.licenseNo.isNullOrBlank()) {
                                        Text("License: ${selectedParty!!.licenseNo}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                AssistChip(
                                    onClick = {
                                        selectedParty = null
                                        customerSearchQuery = ""
                                    },
                                    label = { Text("Change") }
                                )
                            }
                        } else if (customerSearchQuery.isNotBlank() && matchingParties.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column {
                                    matchingParties.take(4).forEach { party ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedParty = party
                                                    customerSearchQuery = party.name
                                                    activeStep = 2 // advance focus to products
                                                    productSearchFocusRequester.requestFocus()
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(party.name, fontWeight = FontWeight.Bold)
                                                Text("Phone: ${party.phone}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                text = "Select",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: Products (Instant Commodity Selection & Stock Lookup)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Category, contentDescription = "Crop", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Step 2: Commodity Crop",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = {
                                productSearchQuery = it
                                activeStep = 2
                                if (selectedCommodity != null && selectedCommodity?.name != it) {
                                    selectedCommodity = null
                                }
                            },
                            label = { Text("Instant Product / Crop Search") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(productSearchFocusRequester)
                                .onFocusChanged { if (it.isFocused) activeStep = 2 }
                                .testTag("search_commodity_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            trailingIcon = {
                                if (productSearchQuery.isNotEmpty() || selectedCommodity != null) {
                                    IconButton(onClick = {
                                        productSearchQuery = ""
                                        selectedCommodity = null
                                    }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )

                        if (selectedCommodity != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(selectedCommodity!!.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "In Stock: ${selectedCommodity!!.stockBags} Bags • Standard Rate: ₹${selectedCommodity!!.currentRate}/Qtl",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Commission: ${selectedCommodity!!.commissionPercent}% • Fee: ${selectedCommodity!!.marketFeePercent}% • GST: ${selectedCommodity!!.gstPercent}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        selectedCommodity = null
                                        productSearchQuery = ""
                                    },
                                    label = { Text("Change") }
                                )
                            }
                        } else if (productSearchQuery.isNotBlank() && matchingCommodities.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column {
                                    matchingCommodities.take(4).forEach { crop ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCommodity = crop
                                                    productSearchQuery = crop.name
                                                    activeStep = 3 // advance focus to quantities
                                                    bagsFocusRequester.requestFocus()
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(crop.name, fontWeight = FontWeight.Bold)
                                                Text("In Stock: ${crop.stockBags} Bags • Rate: ₹${crop.currentRate}/Qtl", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text(
                                                text = "Select",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3: Quantities & Pricing
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Scale, contentDescription = "Qty", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Step 3: Quantities & Rate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = bagCountString,
                                onValueChange = {
                                    bagCountString = it
                                    activeStep = 3
                                },
                                label = { Text("Bags Count *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { weightFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(bagsFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 3 }
                                    .testTag("bags_input"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = weightString,
                                onValueChange = {
                                    weightString = it
                                    activeStep = 3
                                },
                                label = { Text("Weight (Quintals) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { discountFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(weightFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 3 }
                                    .testTag("weight_input"),
                                placeholder = { Text("e.g. 52.4") },
                                singleLine = true
                            )
                        }

                        // Showing standard rate being applied dynamically
                        if (selectedCommodity != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Market Rate Apply:")
                                Text(
                                    currencyFormat.format(rate) + " per Quintal",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gross Product Subtotal:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(currencyFormat.format(grossAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }

                // Step 4 & 5: Discounts & Taxes (GST)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocalOffer, contentDescription = "Disc", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Step 4 & 5: Deductions / Discounts & Taxes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Discount field
                            OutlinedTextField(
                                value = discountString,
                                onValueChange = {
                                    discountString = it
                                    activeStep = 4
                                },
                                label = { Text("Discount (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { gstFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(discountFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 4 }
                                    .testTag("discount_input"),
                                singleLine = true
                            )

                            // GST / Cess field
                            OutlinedTextField(
                                value = gstString,
                                onValueChange = {
                                    gstString = it
                                    activeStep = 5
                                },
                                label = { Text("GST / cess %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { transportFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(gstFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 5 }
                                    .testTag("gst_input"),
                                singleLine = true
                            )
                        }
                    }
                }

                // Step 6, 7 & 8: Transport, Hamali, Commission & Mandi Fees
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocalShipping, contentDescription = "Freight", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Step 6, 7 & 8: Direct Mandi Charges",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Transport
                            OutlinedTextField(
                                value = transportString,
                                onValueChange = {
                                    transportString = it
                                    activeStep = 6
                                },
                                label = { Text("Transport (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { laborFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .focusRequester(transportFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 6 }
                                    .testTag("transport_input"),
                                singleLine = true
                            )

                            // Hamali
                            OutlinedTextField(
                                value = laborString,
                                onValueChange = {
                                    laborString = it
                                    activeStep = 7
                                },
                                label = { Text("Hamali (₹/Bag)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { commissionFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(laborFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 7 }
                                    .testTag("labor_input"),
                                singleLine = true
                            )
                        }

                        // Commission Override
                        OutlinedTextField(
                            value = commissionString,
                            onValueChange = {
                                commissionString = it
                                activeStep = 8
                            },
                            label = { Text("Yard Commission %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { roundOffFocusRequester.requestFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(commissionFocusRequester)
                                .onFocusChanged { if (it.isFocused) activeStep = 8 }
                                .testTag("commission_input"),
                            singleLine = true
                        )
                    }
                }

                // Step 9 & 10: Round Off & Payment Options
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = "RoundOff", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Step 9 & 10: Round Off & Payment Voucher",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Round Off segment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = roundOffString,
                                onValueChange = {
                                    roundOffString = it
                                    activeStep = 9
                                },
                                label = { Text("Round Off (₹ +/-)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { paymentNotesFocusRequester.requestFocus() }),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .focusRequester(roundOffFocusRequester)
                                    .onFocusChanged { if (it.isFocused) activeStep = 9 }
                                    .testTag("round_off_input"),
                                singleLine = true
                            )

                            // Quick adjustment pills
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                InputChip(
                                    selected = false,
                                    onClick = { 
                                        roundOffString = String.format("%.2f", roundToNearestRupeeDiff)
                                        activeStep = 9
                                    },
                                    label = { Text("Round Rupee (${if (roundToNearestRupeeDiff >= 0) "+" else ""}${String.format("%.2f", roundToNearestRupeeDiff)})") }
                                )
                                InputChip(
                                    selected = false,
                                    onClick = { 
                                        roundOffString = String.format("%.2f", roundToNearestTenDiff)
                                        activeStep = 9
                                    },
                                    label = { Text("Round 10 (${if (roundToNearestTenDiff >= 0) "+" else ""}${String.format("%.2f", roundToNearestTenDiff)})") }
                                )
                            }
                        }

                        HorizontalDivider()

                        // Payment selector
                        Text("Payment Voucher Mode:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ElevatedFilterChip(
                                selected = paymentMode == "CREDIT",
                                onClick = {
                                    paymentMode = "CREDIT"
                                    isPaidPayment = false
                                    activeStep = 10
                                },
                                label = { Text("Outstanding Credit") },
                                leadingIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.TrendingFlat, contentDescription = "Credit") },
                                modifier = Modifier.weight(1f)
                            )

                            ElevatedFilterChip(
                                selected = paymentMode == "CASH",
                                onClick = {
                                    paymentMode = "CASH"
                                    isPaidPayment = true
                                    activeStep = 10
                                },
                                label = { Text("Paid Cash") },
                                leadingIcon = { Icon(imageVector = Icons.Default.Money, contentDescription = "Cash") },
                                modifier = Modifier.weight(1f)
                            )

                            ElevatedFilterChip(
                                selected = paymentMode == "BANK_TRANSFER",
                                onClick = {
                                    paymentMode = "BANK_TRANSFER"
                                    isPaidPayment = true
                                    activeStep = 10
                                },
                                label = { Text("Bank Transfer") },
                                leadingIcon = { Icon(imageVector = Icons.Default.AccountBalance, contentDescription = "Bank") },
                                modifier = Modifier.weight(1.1f)
                            )
                        }

                        OutlinedTextField(
                            value = paymentNotesString,
                            onValueChange = {
                                paymentNotesString = it
                                activeStep = 10
                            },
                            label = { Text("Payment/Voucher reference notes") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(paymentNotesFocusRequester)
                                .onFocusChanged { if (it.isFocused) activeStep = 10 }
                                .testTag("payment_notes_input"),
                            singleLine = true
                        )
                    }
                }

                // Create Invoice Trigger (F9 Shortcut)
                Button(
                    onClick = {
                        selectedParty?.let { p ->
                            selectedCommodity?.let { c ->
                                viewModel.createBill(
                                    party = p,
                                    commodity = c,
                                    bagCount = bagCount,
                                    weight = weight,
                                    rate = rate,
                                    laborChargesPerBag = laborPerBag,
                                    transportCharges = transportCharges,
                                    taxPercent = taxPercent,
                                    discountAmount = discountAmount,
                                    roundOff = manualRoundOff,
                                    netAmount = netAmount,
                                    isPaid = isPaidPayment,
                                    paymentNotes = paymentNotesString
                                )
                                showInvoiceSuccessDialog = true
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("submit_bill_btn"),
                    enabled = selectedParty != null && selectedCommodity != null && bagCount > 0 && weight > 0.0,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Receipt, contentDescription = "Invoice")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (billTypeTab == 0) "Save Farmer Patti & Adjust Ledger (F9)" 
                               else "Generate Sales Invoice (F9)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))
            }

            // Right Column: Official POS Live Receipt Preview Panel (Tablet Side-by-Side, Hidden on Mobile but has modal or quick view)
            if (isTablet) {
                Surface(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "LIVE INVOICE RECEIPT PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Visual receipt sheet
                        InvoiceReceiptView(
                            billTypeTab = billTypeTab,
                            selectedParty = selectedParty,
                            selectedCommodity = selectedCommodity,
                            bagCount = bagCount,
                            weight = weight,
                            rate = rate,
                            grossAmount = grossAmount,
                            discountAmount = discountAmount,
                            taxPercent = taxPercent,
                            taxAmount = taxAmount,
                            transportCharges = transportCharges,
                            laborChargesTotal = laborChargesTotal,
                            commissionAmount = commissionAmount,
                            commissionPercent = commissionPercent,
                            marketFeeAmount = marketFeeAmount,
                            manualRoundOff = manualRoundOff,
                            netAmount = netAmount,
                            paymentMode = paymentMode,
                            paymentNotesString = paymentNotesString,
                            currencyFormat = currencyFormat,
                            dateFormat = dateFormat
                        )
                    }
                }
            }
        }
    }

    // Quick Register Party Dialog
    if (showQuickAddPartyDialog) {
        AlertDialog(
            onDismissRequest = { showQuickAddPartyDialog = false },
            title = {
                Text(
                    text = "Quick Register Customer Party",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                var newName by remember { mutableStateOf("") }
                var newPhone by remember { mutableStateOf("") }
                var newAddress by remember { mutableStateOf("") }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Register a customer/farmer instantly onto the local database to resume your checkout workflow.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Full Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Town / Village Address") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (newName.isNotBlank() && newPhone.isNotBlank()) {
                                viewModel.addParty(
                                    name = newName.trim(),
                                    type = activePartyType,
                                    phone = newPhone.trim(),
                                    address = newAddress.trim()
                                )
                                showQuickAddPartyDialog = false
                            }
                        },
                        enabled = newName.isNotBlank() && newPhone.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        Text("Save & Add to Billing")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickAddPartyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Successful Bill Submission Receipt Dialog (Required to print/dismiss)
    if (showInvoiceSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showInvoiceSuccessDialog = false
                onBillCreated()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                    Text("Voucher Saved & Synced", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "The transaction has been captured in Room database and ledger adjustments are processed. Print receipt or copy data to share.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    InvoiceReceiptView(
                        billTypeTab = billTypeTab,
                        selectedParty = selectedParty,
                        selectedCommodity = selectedCommodity,
                        bagCount = bagCount,
                        weight = weight,
                        rate = rate,
                        grossAmount = grossAmount,
                        discountAmount = discountAmount,
                        taxPercent = taxPercent,
                        taxAmount = taxAmount,
                        transportCharges = transportCharges,
                        laborChargesTotal = laborChargesTotal,
                        commissionAmount = commissionAmount,
                        commissionPercent = commissionPercent,
                        marketFeeAmount = marketFeeAmount,
                        manualRoundOff = manualRoundOff,
                        netAmount = netAmount,
                        paymentMode = paymentMode,
                        paymentNotesString = paymentNotesString,
                        currencyFormat = currencyFormat,
                        dateFormat = dateFormat
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInvoiceSuccessDialog = false
                        onBillCreated()
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun InvoiceReceiptView(
    billTypeTab: Int,
    selectedParty: Party?,
    selectedCommodity: Commodity?,
    bagCount: Int,
    weight: Double,
    rate: Double,
    grossAmount: Double,
    discountAmount: Double,
    taxPercent: Double,
    taxAmount: Double,
    transportCharges: Double,
    laborChargesTotal: Double,
    commissionAmount: Double,
    commissionPercent: Double,
    marketFeeAmount: Double,
    manualRoundOff: Double,
    netAmount: Double,
    paymentMode: String,
    paymentNotesString: String,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Text(
                text = "APMC KRISHI MANDI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Official Grain Voucher / Invoice",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Live Sync Active: Supabase & Room Local",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Metadata Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateFormat.format(Date()), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Voucher Type:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (billTypeTab == 0) "FARMER PATTI (Inward)" else "TRADER SALE INVOICE (Outward)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (billTypeTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (billTypeTab == 0) "Farmer Seller:" else "Trader Buyer:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedParty?.name ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Commodity Crop:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedCommodity?.name ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Quantities
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bags Count:", style = MaterialTheme.typography.bodySmall)
                Text("$bagCount Bags", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Weight:", style = MaterialTheme.typography.bodySmall)
                Text("$weight Qtl", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rate applied:", style = MaterialTheme.typography.bodySmall)
                Text(currencyFormat.format(rate) + " / Qtl", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Gross Amount
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gross Market Subtotal:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(currencyFormat.format(grossAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }

            // Real-time breakdown of charges
            Text(
                text = if (billTypeTab == 0) "DEDUCTIONS FROM FARMER PATTI" else "ADDITIONS TO TRADER INVOICE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Discount
            if (discountAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Discount (Step 4):", style = MaterialTheme.typography.bodySmall)
                    Text("-" + currencyFormat.format(discountAmount), style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                }
            }

            // GST (Only positive additions on Trader, or can be custom for farmer)
            if (taxAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GST / Tax (${taxPercent}%) (Step 5):", style = MaterialTheme.typography.bodySmall)
                    Text("+" + currencyFormat.format(taxAmount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Transport Charges
            if (transportCharges > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Transport Fees (Step 6):", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (billTypeTab == 0) "-" + currencyFormat.format(transportCharges) else "+" + currencyFormat.format(transportCharges),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (billTypeTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Labor / Hamali
            if (laborChargesTotal > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hamali Labor (${currencyFormat.format(laborChargesTotal / bagCount.coerceAtLeast(1))} per bag) (Step 7):", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (billTypeTab == 0) "-" + currencyFormat.format(laborChargesTotal) else "+" + currencyFormat.format(laborChargesTotal),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (billTypeTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Commission & Yard fee
            if (commissionAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mandi Commission (${commissionPercent}%) (Step 8):", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (billTypeTab == 0) "-" + currencyFormat.format(commissionAmount) else "+" + currencyFormat.format(commissionAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (billTypeTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Mandi Market Fee (Standard)
            if (marketFeeAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Standard Market Yard Fee (${selectedCommodity?.marketFeePercent ?: 1.0}%):", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (billTypeTab == 0) "-" + currencyFormat.format(marketFeeAmount) else "+" + currencyFormat.format(marketFeeAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (billTypeTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Round Off
            if (manualRoundOff != 0.0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Round Off adjustment (Step 9):", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = (if (manualRoundOff >= 0.0) "+" else "") + currencyFormat.format(manualRoundOff),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // NET PAYABLE / TOTAL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (billTypeTab == 0) "NET PAYABLE TO FARMER:" else "NET INVOICE TOTAL:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = currencyFormat.format(netAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = if (billTypeTab == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Payment Voucher Details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (paymentMode == "CREDIT") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color(0xFFE8F5E9))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Payment: $paymentMode",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (paymentMode == "CREDIT") MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32)
                    )
                    Text(
                        text = if (paymentMode == "CREDIT") "Outstanding Credit" else "Fully Settled",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (paymentMode == "CREDIT") MaterialTheme.colorScheme.error else Color(0xFF1B5E20)
                    )
                }
            }

            if (paymentNotesString.isNotBlank()) {
                Text(
                    text = "Memo: $paymentNotesString",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
