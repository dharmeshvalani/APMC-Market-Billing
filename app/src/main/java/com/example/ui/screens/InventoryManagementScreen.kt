package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryManagementScreen(viewModel: BillingViewModel) {
    val commodities by viewModel.commodities.collectAsState()
    val parties by viewModel.parties.collectAsState()
    val inventoryTransactions by viewModel.filteredInventoryTransactions.collectAsState()
    val rawInventoryTransactions by viewModel.inventoryTransactions.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    val searchQuery by viewModel.inventorySearchQuery.collectAsState()
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "STOCK_IN", "STOCK_OUT", "PURCHASE_ENTRY", "ADJUST_STOCK"
    
    // Dialog and Sheet States
    var showTransactionConsole by remember { mutableStateOf(false) }
    var selectedConsoleTab by remember { mutableStateOf("STOCK_IN") } // "STOCK_IN", "STOCK_OUT", "PURCHASE_ENTRY", "ADJUST_STOCK"
    var showExportReportsDialog by remember { mutableStateOf(false) }

    // Navigation and layout class
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Computed Dashboard Metrics
    val totalStockBags = remember(commodities) { commodities.sumOf { it.stockBags } }
    val lowStockCrops = remember(commodities) { commodities.filter { it.stockBags in 1..49 } }
    val outOfStockCrops = remember(commodities) { commodities.filter { it.stockBags == 0 } }
    val totalStockValue = remember(commodities) {
        commodities.sumOf { it.stockBags * (if (it.sellingPrice > 0.0) it.sellingPrice else it.currentRate) }
    }

    // Filtered Transactions
    val processedTransactions = remember(inventoryTransactions, selectedTypeFilter) {
        if (selectedTypeFilter == "ALL") {
            inventoryTransactions
        } else {
            inventoryTransactions.filter { it.type == selectedTypeFilter }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("inventory_root_layout"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Upper Title Bar and Main Actions Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Inventory Management Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time commodities tracking, grain logs, and Supabase ledger sync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sync with Supabase Button
                FilledTonalButton(
                    onClick = { viewModel.syncInventoryWithSupabase() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("sync_supabase_btn")
                ) {
                    Icon(imageVector = Icons.Default.CloudSync, contentDescription = "Sync Now")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Cloud")
                }

                // Add Transaction / Flow Console Button
                Button(
                    onClick = { showTransactionConsole = true },
                    modifier = Modifier.testTag("open_flow_console_btn")
                ) {
                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Vouchers")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stock Action")
                }
            }
        }

        // Live Alerts Segment for Low / Critical Stock Items
        if (lowStockCrops.isNotEmpty() || outOfStockCrops.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Low Stock Alerts & Shortages",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Horizontal lazy row list of products facing shortage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        outOfStockCrops.forEach { crop ->
                            AssistChip(
                                onClick = {},
                                label = { Text("${crop.name}: OUT OF STOCK") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "No Stock",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f)
                                )
                            )
                        }

                        lowStockCrops.forEach { crop ->
                            AssistChip(
                                onClick = {},
                                label = { Text("${crop.name}: ${crop.stockBags} Bags left") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.TrendingDown,
                                        contentDescription = "Low Stock",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Inventory Overview Dashboard Indicators Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Total Stock Volume
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Total Active Stock",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalStockBags Bags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Card 2: Valuation of Stock
            Card(
                modifier = Modifier.weight(1.2f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Estimated Valuation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currencyFormat.format(totalStockValue),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Card 3: Alert Counts
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = if (lowStockCrops.isNotEmpty()) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) 
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Low Stock Crops",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${lowStockCrops.size + outOfStockCrops.size} Items",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (lowStockCrops.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Search & Filter Controllers Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.inventorySearchQuery.value = it },
                label = { Text("Search logs (Crop, Farmer, Ref...)") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("inventory_search_input"),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.inventorySearchQuery.value = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )

            // Filter Dropdown / Row Chips for Tablet vs Mobile
            Box(modifier = Modifier.weight(1f)) {
                ScrollableTabRow(
                    selectedTabIndex = when (selectedTypeFilter) {
                        "ALL" -> 0
                        "STOCK_IN" -> 1
                        "STOCK_OUT" -> 2
                        "PURCHASE_ENTRY" -> 3
                        "ADJUST_STOCK" -> 4
                        else -> 0
                    },
                    edgePadding = 0.dp,
                    indicator = {},
                    divider = {}
                ) {
                    listOf(
                        "ALL" to "All Logs",
                        "STOCK_IN" to "Stock In",
                        "STOCK_OUT" to "Stock Out",
                        "PURCHASE_ENTRY" to "Purchase",
                        "ADJUST_STOCK" to "Adjust"
                    ).forEach { (filterVal, label) ->
                        Tab(
                            selected = selectedTypeFilter == filterVal,
                            onClick = { selectedTypeFilter = filterVal },
                            text = { Text(label, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            // Reports / Export Action Button
            IconButton(
                onClick = { showExportReportsDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export CSV Report",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Inventory History Logs / Table Listing
        Text(
            text = "Inventory History Journal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (processedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyStatePlaceholder(
                    title = "No Stock Transactions",
                    subtitle = "Perform a stock adjustment, purchase, or sync with Supabase to display records.",
                    icon = Icons.Default.ReceiptLong
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("inventory_logs_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(processedTransactions) { tx ->
                    InventoryTransactionRow(tx, currencyFormat, dateFormat)
                }
            }
        }
    }

    // Unified Stock Action Flow Console Dialog
    if (showTransactionConsole) {
        AlertDialog(
            onDismissRequest = { showTransactionConsole = false },
            title = {
                Text(
                    text = "Inventory Ledger Voucher",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                var selectedCommodity by remember { mutableStateOf<Commodity?>(null) }
                var bagsInput by remember { mutableStateOf("") }
                var weightInput by remember { mutableStateOf("") }
                var rateInput by remember { mutableStateOf("") }
                var remarksInput by remember { mutableStateOf("") }
                var referenceInput by remember { mutableStateOf("") }
                
                // Party selection states
                var selectedParty by remember { mutableStateOf<Party?>(null) }
                var commodityDropdownExpanded by remember { mutableStateOf(false) }
                var partyDropdownExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Type Selector Tabs inside dialog
                    TabRow(
                        selectedTabIndex = when (selectedConsoleTab) {
                            "STOCK_IN" -> 0
                            "STOCK_OUT" -> 1
                            "PURCHASE_ENTRY" -> 2
                            "ADJUST_STOCK" -> 3
                            else -> 0
                        }
                    ) {
                        listOf(
                            "STOCK_IN" to "Stock In",
                            "STOCK_OUT" to "Stock Out",
                            "PURCHASE_ENTRY" to "Purchase",
                            "ADJUST_STOCK" to "Adjust"
                        ).forEach { (tabVal, label) ->
                            Tab(
                                selected = selectedConsoleTab == tabVal,
                                onClick = {
                                    selectedConsoleTab = tabVal
                                    // Reset contextual party states
                                    selectedParty = null
                                    rateInput = ""
                                },
                                text = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Commodity Selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = commodityDropdownExpanded,
                            onExpandedChange = { commodityDropdownExpanded = !commodityDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedCommodity?.name ?: "Select Commodity *",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Commodity *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = commodityDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = commodityDropdownExpanded,
                                onDismissRequest = { commodityDropdownExpanded = false }
                            ) {
                                commodities.forEach { crop ->
                                    DropdownMenuItem(
                                        text = { Text("${crop.name} (${crop.stockBags} Bags)") },
                                        onClick = {
                                            selectedCommodity = crop
                                            commodityDropdownExpanded = false
                                            
                                            // Prefill standard rates if applicable
                                            if (selectedConsoleTab == "PURCHASE_ENTRY") {
                                                rateInput = crop.purchasePrice.toString()
                                            } else if (selectedConsoleTab == "STOCK_OUT") {
                                                rateInput = if (crop.sellingPrice > 0.0) crop.sellingPrice.toString() else crop.currentRate.toString()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Bags Input
                    OutlinedTextField(
                        value = bagsInput,
                        onValueChange = { bagsInput = it },
                        label = {
                            Text(
                                if (selectedConsoleTab == "ADJUST_STOCK") "Bag Adjustment Delta (e.g. -5 or 10) *"
                                else "Number of Bags *"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Weight Input
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Total Weight in Quintals *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Contextual Inputs based on Selected Tab
                    if (selectedConsoleTab == "PURCHASE_ENTRY" || selectedConsoleTab == "STOCK_OUT") {
                        val expectedPartyType = if (selectedConsoleTab == "PURCHASE_ENTRY") "FARMER" else "TRADER"
                        val contextParties = parties.filter { it.type == expectedPartyType }

                        // Party Selector Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ExposedDropdownMenuBox(
                                expanded = partyDropdownExpanded,
                                onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedParty?.name ?: "Select ${if (expectedPartyType == "FARMER") "Farmer" else "Trader"} *",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Associated Partner *") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                )

                                ExposedDropdownMenu(
                                    expanded = partyDropdownExpanded,
                                    onDismissRequest = { partyDropdownExpanded = false }
                                ) {
                                    contextParties.forEach { partner ->
                                        DropdownMenuItem(
                                            text = { Text("${partner.name} (Bal: ${partner.balance})") },
                                            onClick = {
                                                selectedParty = partner
                                                partyDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Rate per Quintal input
                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = { rateInput = it },
                            label = { Text("Rate per Quintal (₹) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Reference No / Invoice Voucher input
                        OutlinedTextField(
                            value = referenceInput,
                            onValueChange = { referenceInput = it },
                            label = { Text("Invoice Reference / Receipt No.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Remarks field
                    OutlinedTextField(
                        value = remarksInput,
                        onValueChange = { remarksInput = it },
                        label = { Text("Transaction Remarks / Location") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Error Check Panel
                    val inputValidationError = remember(selectedCommodity, bagsInput, weightInput, selectedParty, rateInput) {
                        val bags = bagsInput.toIntOrNull()
                        val weight = weightInput.toDoubleOrNull()
                        val rate = rateInput.toDoubleOrNull()

                        when {
                            selectedCommodity == null -> "Please select a commodity."
                            bags == null || (selectedConsoleTab != "ADJUST_STOCK" && bags <= 0) -> "Please enter a valid bag count."
                            weight == null || weight <= 0.0 -> "Please enter a valid total weight in Quintals."
                            (selectedConsoleTab == "PURCHASE_ENTRY" || selectedConsoleTab == "STOCK_OUT") && selectedParty == null -> "Please select a trader/farmer."
                            (selectedConsoleTab == "PURCHASE_ENTRY" || selectedConsoleTab == "STOCK_OUT") && (rate == null || rate <= 0.0) -> "Please enter a valid rate per quintal."
                            else -> null
                        }
                    }

                    inputValidationError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Primary Action Trigger Button
                    Button(
                        onClick = {
                            val comm = selectedCommodity ?: return@Button
                            val bags = bagsInput.toInt()
                            val weight = weightInput.toDouble()
                            val rate = rateInput.toDoubleOrNull() ?: 0.0
                            val totalPrice = bags * rate // rough total

                            // Compute delta based on Action Tab
                            val finalDelta = when (selectedConsoleTab) {
                                "STOCK_IN" -> bags
                                "PURCHASE_ENTRY" -> bags
                                "STOCK_OUT" -> -bags
                                "ADJUST_STOCK" -> bags
                                else -> bags
                            }

                            viewModel.processInventoryChange(
                                commodityId = comm.id,
                                type = selectedConsoleTab,
                                bagsDelta = finalDelta,
                                totalWeight = weight,
                                ratePerQtl = rate,
                                totalPrice = totalPrice,
                                partyId = selectedParty?.id,
                                partyName = selectedParty?.name,
                                remarks = remarksInput.trim(),
                                referenceNo = referenceInput.trim().ifBlank { null }
                            )

                            showTransactionConsole = false
                        },
                        enabled = inputValidationError == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("submit_stock_transaction_btn")
                    ) {
                        Text("Record Voucher & Sync Cloud")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTransactionConsole = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Reports & Export CSV Dialog
    if (showExportReportsDialog) {
        val context = LocalContext.current
        val clipboardManager = LocalClipboardManager.current

        AlertDialog(
            onDismissRequest = { showExportReportsDialog = false },
            title = {
                Text(
                    text = "Export Inventory Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This will generate a consolidated CSV of the current ${rawInventoryTransactions.size} stock records. You can copy the raw data or view the generated file.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val rawCsvContent = remember(rawInventoryTransactions) {
                        val builder = StringBuilder()
                        builder.append("Date,Commodity,Type,Bags,Weight(Qtl),Rate,TotalPrice,Partner,RefNo,Remarks\n")
                        rawInventoryTransactions.forEach { tx ->
                            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.date))
                            builder.append("\"$formattedDate\",")
                                .append("\"${tx.commodityName}\",")
                                .append("\"${tx.type}\",")
                                .append("${tx.bags},")
                                .append("${tx.totalWeight},")
                                .append("${tx.ratePerQtl},")
                                .append("${tx.totalPrice},")
                                .append("\"${tx.partyName ?: ""}\",")
                                .append("\"${tx.referenceNo ?: ""}\",")
                                .append("\"${tx.remarks}\"\n")
                        }
                        builder.toString()
                    }

                    // Share/Copy Button
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(rawCsvContent))
                            viewModel.showToast("CSV copied to clipboard!")
                            showExportReportsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy CSV to Clipboard")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportReportsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InventoryTransactionRow(
    tx: InventoryTransaction,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_log_row_${tx.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Code Indicator Circle based on transaction type
                val indicatorColor = when (tx.type) {
                    "STOCK_IN" -> Color(0xFF4CAF50) // Green
                    "PURCHASE_ENTRY" -> Color(0xFF2196F3) // Blue
                    "STOCK_OUT" -> Color(0xFFFF9800) // Orange
                    "ADJUST_STOCK" -> Color(0xFF9C27B0) // Purple
                    else -> MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(indicatorColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (tx.type) {
                            "STOCK_IN" -> Icons.Default.ArrowDownward
                            "PURCHASE_ENTRY" -> Icons.Default.ShoppingCart
                            "STOCK_OUT" -> Icons.Default.ArrowUpward
                            "ADJUST_STOCK" -> Icons.Default.Tune
                            else -> Icons.Default.SwapHoriz
                        },
                        contentDescription = tx.type,
                        tint = indicatorColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Description labels
                Column {
                    Text(
                        text = tx.commodityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (tx.type) {
                            "STOCK_IN" -> "Direct Inflow • ${tx.remarks.ifBlank { "Mandi Yard" }}"
                            "PURCHASE_ENTRY" -> "Purchase Patti from ${tx.partyName ?: "Farmer"}"
                            "STOCK_OUT" -> "Despatch Sale to ${tx.partyName ?: "Trader"}"
                            "ADJUST_STOCK" -> "Audit Adjustment • ${tx.remarks.ifBlank { "Stock count corrected" }}"
                            else -> tx.type
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateFormat.format(Date(tx.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Delta quantity and cost details
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(0.8f)
            ) {
                val sign = if (tx.type == "STOCK_OUT" || (tx.type == "ADJUST_STOCK" && tx.bags < 0)) "-" else "+"
                val qtyColor = if (sign == "+") Color(0xFF4CAF50) else Color(0xFFFF9800)
                
                Text(
                    text = "$sign${if (tx.bags < 0) -tx.bags else tx.bags} Bags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = qtyColor
                )
                
                Text(
                    text = "${tx.totalWeight} Qtl",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (tx.totalPrice > 0.0) {
                    Text(
                        text = currencyFormat.format(tx.totalPrice),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
