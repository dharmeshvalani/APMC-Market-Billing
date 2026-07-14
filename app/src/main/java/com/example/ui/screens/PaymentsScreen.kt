package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntry
import com.example.data.Party
import com.example.data.ShopConfig
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(viewModel: BillingViewModel) {
    val context = LocalContext.current
    val parties by viewModel.parties.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val shopConfig by viewModel.shopConfig.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Screen state
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Balances & Settlement, 1 = Receipt & History
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog control state
    var showPaymentRecordDialog by remember { mutableStateOf(false) }
    var selectedPartyForPayment by remember { mutableStateOf<Party?>(null) }
    var showReceiptDetailsDialog by remember { mutableStateOf<LedgerEntry?>(null) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Aggregate Outstanding Metrics
    val totalReceivablesAmount = remember(parties) {
        parties.filter { it.type == "TRADER" }.sumOf { maxOf(0.0, it.balance) }
    }
    val totalPayablesAmount = remember(parties) {
        parties.filter { it.type == "FARMER" }.sumOf { if (it.balance < 0.0) -it.balance else 0.0 }
    }
    
    // Filter Ledger Entries for Payment Vouchers (entry with "PAY-" reference or related descriptors)
    val paymentHistoryList = remember(ledgerEntries, searchQuery) {
        ledgerEntries.filter { entry ->
            val isPayment = entry.referenceId.startsWith("PAY-") || 
                            entry.description.contains("via", ignoreCase = true) || 
                            entry.description.contains("settle", ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() || 
                             entry.description.contains(searchQuery, ignoreCase = true) || 
                             entry.referenceId.contains(searchQuery, ignoreCase = true) ||
                             (parties.find { it.id == entry.partyId }?.name ?: "").contains(searchQuery, ignoreCase = true)
            isPayment && matchesQuery
        }
    }

    // Filter Parties for Outstanding Balances Screen
    val filteredPartiesList = remember(parties, searchQuery) {
        parties.filter { party ->
            searchQuery.isBlank() || 
            party.name.contains(searchQuery, ignoreCase = true) || 
            party.phone.contains(searchQuery) ||
            party.address.contains(searchQuery, ignoreCase = true)
        }
    }

    // Auth validation check: Staff cannot access consolidated payments module
    if (currentUser?.role == com.example.data.UserRole.STAFF) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access Denied",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Access Denied",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Staff role does not have authorization to view outstanding books or settle accounts. Please request an Administrator or Owner.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("payments_screen_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High fidelity Outstanding Metrics Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Receivable (Trader Outstanding) Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Receivable",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Traders Receivable",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currencyFormat.format(totalReceivablesAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mandi Outstandings to collect",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Payable (Farmer Outstanding) Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Payable",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Farmers Payable",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currencyFormat.format(totalPayablesAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Due payouts to farmers",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Modern Tab Selectors
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("payment_tab_balances"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Outstanding Ledger", fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("payment_tab_history"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Receipts & History", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (selectedTab == 0) "Search Farmers, Traders or phone..." else "Search Receipt ID or remarks...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // Contents switching based on Selected Tab
        if (selectedTab == 0) {
            // TAB 1: OUTSTANDING BALANCES LIST
            if (filteredPartiesList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyStatePlaceholder(
                        title = "No Accounts Found",
                        subtitle = "Try altering the search terms or add new parties from Farmers / Customers directory.",
                        icon = Icons.Default.Person
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPartiesList) { party ->
                        PartyOutstandingCard(
                            party = party,
                            currencyFormat = currencyFormat,
                            onRecordPayment = {
                                selectedPartyForPayment = party
                                showPaymentRecordDialog = true
                            }
                        )
                    }
                }
            }
        } else {
            // TAB 2: RECEIPT HISTORY & TRANSACTION STATEMENTS
            if (paymentHistoryList.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyStatePlaceholder(
                        title = "No Payment Receipts",
                        subtitle = "Outstanding payout or collection receipts haven't been logged yet.",
                        icon = Icons.Outlined.ReceiptLong
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(paymentHistoryList) { entry ->
                        val party = parties.find { it.id == entry.partyId }
                        PaymentHistoryCard(
                            entry = entry,
                            partyName = party?.name ?: "Deleted Account",
                            partyType = party?.type ?: "UNKNOWN",
                            currencyFormat = currencyFormat,
                            dateFormat = dateFormat,
                            onViewReceipt = {
                                showReceiptDetailsDialog = entry
                            }
                        )
                    }
                }
            }
        }
    }

    // Record Payment & Payout Dialog
    if (showPaymentRecordDialog) {
        RecordPaymentDialog(
            preselectedParty = selectedPartyForPayment,
            parties = parties.filter { it.type == "FARMER" || it.type == "TRADER" },
            currencyFormat = currencyFormat,
            onDismiss = {
                showPaymentRecordDialog = false
                selectedPartyForPayment = null
            },
            onConfirm = { party, amount, mode, reference ->
                viewModel.recordPayment(party, amount, mode, reference)
                showPaymentRecordDialog = false
                selectedPartyForPayment = null
            }
        )
    }

    // Elegant Digital Receipt Dialog
    if (showReceiptDetailsDialog != null) {
        val activeEntry = showReceiptDetailsDialog!!
        val matchingParty = parties.find { it.id == activeEntry.partyId }
        ReceiptDetailsDialog(
            entry = activeEntry,
            partyName = matchingParty?.name ?: "Unknown Partner",
            partyPhone = matchingParty?.phone ?: "N/A",
            partyType = matchingParty?.type ?: "PARTNER",
            shopConfig = shopConfig,
            currencyFormat = currencyFormat,
            dateFormat = dateFormat,
            onDismiss = { showReceiptDetailsDialog = null },
            onPrint = { triggerReceiptPrint(context, activeEntry, matchingParty?.name ?: "Unknown Partner", matchingParty?.type ?: "PARTNER", shopConfig) },
            onShare = { triggerReceiptShare(context, activeEntry, matchingParty?.name ?: "Unknown Partner", matchingParty?.type ?: "PARTNER", shopConfig) }
        )
    }
}

@Composable
fun PartyOutstandingCard(
    party: Party,
    currencyFormat: NumberFormat,
    onRecordPayment: () -> Unit
) {
    val isFarmer = party.type == "FARMER"
    val balance = party.balance
    
    // Determine balance description
    val balanceText = if (balance < 0.0) {
        "We owe: ${currencyFormat.format(-balance)}"
    } else if (balance > 0.0) {
        "They owe: ${currencyFormat.format(balance)}"
    } else {
        "Settled: ₹0.00"
    }

    val statusColor = if (balance < 0.0) {
        MaterialTheme.colorScheme.error // we owe them (payable)
    } else if (balance > 0.0) {
        Color(0xFF2E7D32) // they owe us (receivable)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("outstanding_party_card_${party.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.3f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Custom Role Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isFarmer) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isFarmer) "Farmer" else "Trader",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFarmer) Color(0xFF2E7D32) else Color(0xFF1565C0),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = party.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (party.address.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = party.address, 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Highlight Balance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Text(
                        text = balanceText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                }
            }

            // Quick Settle Action Button
            Button(
                onClick = onRecordPayment,
                modifier = Modifier
                    .weight(0.7f)
                    .testTag("quick_settle_btn_${party.id}"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (balance != 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (balance != 0.0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (isFarmer) Icons.Default.Payment else Icons.Default.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFarmer) "Payout" else "Receipt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PaymentHistoryCard(
    entry: LedgerEntry,
    partyName: String,
    partyType: String,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onViewReceipt: () -> Unit
) {
    val isDebit = entry.entryType == "DEBIT"
    val formattedDate = dateFormat.format(Date(entry.date))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewReceipt() }
            .testTag("receipt_history_card_${entry.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Flow indicator icon (Debit as Outward payment, Credit as Inward collection)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDebit) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else Color(0xFFE8F5E9)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDebit) Icons.Default.ArrowOutward else Icons.Default.CallReceived,
                    contentDescription = if (isDebit) "Payout" else "Receipt",
                    tint = if (isDebit) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    text = partyName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$formattedDate • Ref: ${entry.referenceId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(0.7f)
            ) {
                Text(
                    text = currencyFormat.format(entry.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isDebit) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isDebit) "PAYOUT" else "RECEIPT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    preselectedParty: Party?,
    parties: List<Party>,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onConfirm: (Party, Double, String, String) -> Unit
) {
    var selectedParty by remember { mutableStateOf(preselectedParty) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    var amountStr by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("CASH") } // "CASH", "UPI", "BANK", "CREDIT"
    var transactionReference by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    // Real-time outstanding ledger calculation
    val balancePreview = remember(selectedParty, amountStr) {
        val party = selectedParty ?: return@remember ""
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val isFarmer = party.type == "FARMER"
        val originalBalance = party.balance
        
        val updatedBalance = if (isFarmer) {
            // Farmer owes we money (positive balance). Payout increases balance towards 0
            originalBalance + amount
        } else {
            // Trader owes us money (positive balance). Receipt decreases balance towards 0
            originalBalance - amount
        }

        val originalText = if (originalBalance < 0) "We owe ₹${-originalBalance}" else if (originalBalance > 0) "They owe ₹$originalBalance" else "₹0.00 (Settled)"
        val updatedText = if (updatedBalance < 0) "We owe ₹${-updatedBalance}" else if (updatedBalance > 0) "They owe ₹$updatedBalance" else "₹0.00 (Settled)"
        
        "Current Outstanding: $originalText\nEstimated Balance after payment: $updatedText"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Record Payment & Settlement", fontWeight = FontWeight.Bold)
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
                    text = "Lodge Cash, UPI, or Bank settlement transactions directly against party outstanding books to keep APMC client ledgers reconciled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Searchable drop down for accounts partner
                if (preselectedParty != null) {
                    OutlinedTextField(
                        value = "${preselectedParty.name} (${preselectedParty.type})",
                        onValueChange = {},
                        label = { Text("Account Partner") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = partyDropdownExpanded,
                            onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedParty?.let { "${it.name} (${it.type})" } ?: "Choose account partner...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Mandi Partner *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("payment_partner_selector"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = partyDropdownExpanded,
                                onDismissRequest = { partyDropdownExpanded = false }
                            ) {
                                parties.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text("${p.name} (${p.type})") },
                                        onClick = {
                                            selectedParty = p
                                            partyDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Amount Text Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Settle Amount (₹) *") },
                    placeholder = { Text("e.g. 25000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Segmented Payment Modes (Cash, UPI, Bank, Credit)
                Text("Select Settlement Mode *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ElevatedButton(
                            onClick = { paymentMode = "CASH" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (paymentMode == "CASH") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMode == "CASH") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Money, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cash")
                        }

                        ElevatedButton(
                            onClick = { paymentMode = "UPI" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (paymentMode == "UPI") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMode == "UPI") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("UPI")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ElevatedButton(
                            onClick = { paymentMode = "BANK" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (paymentMode == "BANK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMode == "BANK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bank")
                        }

                        ElevatedButton(
                            onClick = { paymentMode = "CREDIT" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (paymentMode == "CREDIT") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (paymentMode == "CREDIT") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Credit Ledger")
                        }
                    }
                }

                // Transaction Reference Code
                OutlinedTextField(
                    value = transactionReference,
                    onValueChange = { transactionReference = it },
                    label = { Text("Transaction ID / Reference Code") },
                    placeholder = { Text("e.g. UPI Ref, Cheque No, Bank Txn") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Dynamic live simulation preview of Ledger outstanding
                if (selectedParty != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "RECONCILIATION PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = balancePreview,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Remarks
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Voucher Description / Remarks") },
                    placeholder = { Text("Partial payment for Rice lot, mandi levy etc...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedParty?.let { party ->
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        val refDetails = buildString {
                            append(paymentMode)
                            if (transactionReference.isNotBlank()) append(" (Ref: $transactionReference)")
                            if (remarks.isNotBlank()) append(" - $remarks")
                        }
                        onConfirm(party, amount, paymentMode, refDetails)
                    }
                },
                modifier = Modifier.testTag("payment_dialog_confirm"),
                enabled = selectedParty != null && amountStr.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Post Voucher")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReceiptDetailsDialog(
    entry: LedgerEntry,
    partyName: String,
    partyPhone: String,
    partyType: String,
    shopConfig: ShopConfig,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit
) {
    val isDebit = entry.entryType == "DEBIT"
    val isFarmer = partyType == "FARMER"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Skeuomorphic Ivory Paper Receipt Ticket
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)),
                    border = BorderStroke(1.dp, Color(0xFFE8E5DD)),
                    shape = RoundedCornerShape(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipt_ticket_visual")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header info
                        Text(
                            text = shopConfig.shopName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF1E1E1E)
                        )
                        Text(
                            text = shopConfig.address,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF555555)
                        )
                        if (shopConfig.gstNumber.isNotEmpty()) {
                            Text(
                                text = "GSTIN: ${shopConfig.gstNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF555555)
                            )
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFCCCCCC)
                        )

                        Text(
                            text = "OFFICIAL RECEIPT VOUCHER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Left align meta grid
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReceiptGridRow("Voucher Ref", entry.referenceId)
                            ReceiptGridRow("Date/Time", dateFormat.format(Date(entry.date)))
                            ReceiptGridRow("Partner", "$partyName ($partyType)")
                            ReceiptGridRow("Phone", partyPhone)
                            ReceiptGridRow("Trans Type", if (isDebit) "PAYOUT / DEBIT" else "RECEIPT / COLLECTION")
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFE0E0E0),
                            thickness = 1.dp
                        )

                        // Big prominent Amount
                        Text(
                            text = "AMOUNT SETTLED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF777777)
                        )
                        Text(
                            text = currencyFormat.format(entry.amount),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDebit) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFFE0E0E0),
                            thickness = 1.dp
                        )

                        // Details remarks
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TRANSACTION DETAILS:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF555555)
                            )
                            Text(
                                text = entry.description,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF1E1E1E)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Footer seal placeholder
                        Box(
                            modifier = Modifier
                                .border(BorderStroke(2.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "APMC ACCOUNTS SYSTEM VERIFIED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2E7D32).copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPrint,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print Receipt")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onShare) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun ReceiptGridRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.padEnd(12, '.'),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF777777)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1E1E),
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 160.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// PRINT RECEIPT IMPLEMENTATION
private fun triggerReceiptPrint(context: Context, entry: LedgerEntry, partyName: String, partyType: String, shopConfig: ShopConfig) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val isDebit = entry.entryType == "DEBIT"

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: 'Courier New', Courier, monospace; padding: 20px; color: #1e1e1e; font-size: 14px; line-height: 1.5; }
                .receipt-box { max-width: 500px; margin: auto; padding: 20px; border: 1px dashed #bbb; background: #fff; }
                .header { text-align: center; margin-bottom: 20px; }
                .shop-name { font-size: 20px; font-weight: bold; text-transform: uppercase; margin-bottom: 5px; }
                .meta-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
                .meta-table td { padding: 4px 0; font-size: 13px; }
                .meta-table td.label { color: #555; }
                .meta-table td.value { text-align: right; font-weight: bold; }
                .divider { border-bottom: 1px dashed #333; margin: 15px 0; }
                .amount-section { text-align: center; margin: 20px 0; }
                .amount-label { font-size: 11px; font-weight: bold; color: #555; letter-spacing: 1px; }
                .amount-value { font-size: 28px; font-weight: bold; margin-top: 5px; }
                .verified-seal { display: inline-block; border: 2px solid #2e7d32; color: #2e7d32; font-size: 10px; font-weight: bold; padding: 5px 10px; letter-spacing: 1px; margin-top: 15px; }
                .footer { text-align: center; font-size: 11px; margin-top: 30px; color: #777; }
            </style>
        </head>
        <body>
            <div class="receipt-box">
                <div class="header">
                    <div class="shop-name">${shopConfig.shopName}</div>
                    <div>${shopConfig.address}</div>
                    ${if (shopConfig.gstNumber.isNotEmpty()) "<div>GSTIN: ${shopConfig.gstNumber}</div>" else ""}
                    ${if (shopConfig.apmcLicense.isNotEmpty()) "<div>APMC License: ${shopConfig.apmcLicense}</div>" else ""}
                </div>
                
                <div class="divider"></div>
                <div style="text-align: center; font-weight: bold; margin-bottom: 15px;">OFFICIAL RECEIPT VOUCHER</div>
                
                <table class="meta-table">
                    <tr>
                        <td class="label">Voucher Ref</td>
                        <td class="value">${entry.referenceId}</td>
                    </tr>
                    <tr>
                        <td class="label">Date/Time</td>
                        <td class="value">${dateFormat.format(Date(entry.date))}</td>
                    </tr>
                    <tr>
                        <td class="label">Client Partner</td>
                        <td class="value">${partyName} (${partyType})</td>
                    </tr>
                    <tr>
                        <td class="label">Transaction Type</td>
                        <td class="value">${if (isDebit) "PAYOUT TO SELLER" else "RECEIPT FROM BUYER"}</td>
                    </tr>
                </table>
                
                <div class="divider"></div>
                
                <div class="amount-section">
                    <div class="amount-label">TOTAL AMOUNT SETTLED</div>
                    <div class="amount-value">${currencyFormat.format(entry.amount)}</div>
                </div>
                
                <div class="divider"></div>
                
                <div style="margin-bottom: 20px;">
                    <div style="font-size: 11px; font-weight: bold; color: #555;">REMARKS/METHOD:</div>
                    <div style="font-size: 13px; font-weight: bold; margin-top: 4px;">${entry.description}</div>
                </div>
                
                <div style="text-align: center;">
                    <div class="verified-seal">APMC ACCOUNTS SYSTEM VERIFIED</div>
                </div>
                
                <div class="footer">
                    Thank you for trading with us.<br>
                    Generated on APMC Krishi Mandi Smart Billing
                </div>
            </div>
        </body>
        </html>
    """

    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "APMC_Receipt_${entry.referenceId}"

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

// SHARE RECEIPT VIA STANDARD DIALOG / INTENT
private fun triggerReceiptShare(context: Context, entry: LedgerEntry, partyName: String, partyType: String, shopConfig: ShopConfig) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val isDebit = entry.entryType == "DEBIT"

    val message = """
        *APMC ACCOUNTS RECEIPT VOUCHER*
        
        *Shop:* ${shopConfig.shopName}
        *Address:* ${shopConfig.address}
        *Ref:* ${entry.referenceId}
        *Date:* ${dateFormat.format(Date(entry.date))}
        *Client:* $partyName ($partyType)
        
        ----------------------------------
        *AMOUNT:* ${currencyFormat.format(entry.amount)}
        *TYPE:* ${if (isDebit) "PAYOUT DISBURSED" else "COLLECTION RECEIVED"}
        ----------------------------------
        *Details:* ${entry.description}
        
        _APMC Digitally Verified Ledger Statement_
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Share Payment Receipt"))
}
