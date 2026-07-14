package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.LedgerEntry
import com.example.data.Party
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(viewModel: BillingViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
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
                        text = "Staff roles do not have permissions to access or manage the Merchant & Farmer directory database. Please contact your administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        return
    }

    val filteredParties by viewModel.filteredParties.collectAsState()
    val partySearchQuery by viewModel.partySearchQuery.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPartyForDetail by remember { mutableStateOf<Party?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Farmers, 1 = Traders (Buyers)

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    val activeType = if (selectedTab == 0) "FARMER" else "TRADER"
    val displayedParties = filteredParties.filter { it.type == activeType }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab Header (Farmers vs Traders)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Agriculture, contentDescription = "Farmers")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Farmers (Sellers)")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Storefront, contentDescription = "Traders")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Traders (Buyers)")
                        }
                    }
                )
            }

            // Search Bar & Add Button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = partySearchQuery,
                    onValueChange = { viewModel.partySearchQuery.value = it },
                    placeholder = { Text("Search by name or phone...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.weight(1.9f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                    if (isTablet) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedTab == 0) "Add Farmer" else "Add Trader")
                    }
                }
            }

            // Parties content list
            if (displayedParties.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePlaceholder(
                        title = if (selectedTab == 0) "No Farmers Registered" else "No Traders Registered",
                        subtitle = "Begin by clicking the Add Button above to enroll a new partner in this APMC Ledger system.",
                        icon = if (selectedTab == 0) Icons.Default.Agriculture else Icons.Default.Storefront
                    )
                }
            } else {
                if (isTablet) {
                    // Two-column layout on Tablet
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedParties) { party ->
                            PartyCard(
                                party = party,
                                currencyFormat = currencyFormat,
                                onClick = { selectedPartyForDetail = party }
                            )
                        }
                    }
                } else {
                    // Single column list on mobile
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedParties) { party ->
                            PartyCard(
                                party = party,
                                currencyFormat = currencyFormat,
                                onClick = { selectedPartyForDetail = party }
                            )
                        }
                    }
                }
            }
        }

        // Add Party Dialog
        if (showAddDialog) {
            AddPartyDialog(
                type = activeType,
                onDismiss = { showAddDialog = false },
                onAdd = { name, phone, address, license, bank ->
                    viewModel.addParty(
                        name = name,
                        type = activeType,
                        phone = phone,
                        address = address,
                        licenseNo = license,
                        bankDetails = bank
                    )
                    showAddDialog = false
                }
            )
        }

        // Party Ledger Details Sheet
        if (selectedPartyForDetail != null) {
            val party = selectedPartyForDetail!!
            val partyLedger = ledgerEntries.filter { it.partyId == party.id }

            PartyLedgerDetailsDialog(
                party = party,
                ledgerEntries = partyLedger,
                currencyFormat = currencyFormat,
                dateFormat = dateFormat,
                onDismiss = { selectedPartyForDetail = null },
                onRecordPayment = { amount, method, notes ->
                    viewModel.recordPayment(party, amount, method, notes)
                    // Refresh view
                    selectedPartyForDetail = null
                },
                onDeleteParty = {
                    viewModel.deleteParty(party)
                    selectedPartyForDetail = null
                }
            )
        }
    }
}

@Composable
fun PartyCard(
    party: Party,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    val isDebtor = party.balance > 0.0
    val isCreditor = party.balance < 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (party.type == "FARMER")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = party.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (party.type == "FARMER")
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = party.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "📞 ${party.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!party.address.isNullOrBlank()) {
                    Text(
                        text = "📍 ${party.address}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Balance Area
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (party.type == "FARMER") {
                        // For Farmers, negative balance means we owe them (Payable to Farmer)
                        // Let's show as positive absolute with proper label
                        if (party.balance < 0) currencyFormat.format(-party.balance) else currencyFormat.format(party.balance)
                    } else {
                        // For Buyers/Traders, positive means they owe us (Receivable from Buyer)
                        currencyFormat.format(party.balance)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = when {
                        party.type == "FARMER" && party.balance < 0 -> MaterialTheme.colorScheme.error // We owe farmer
                        party.type == "TRADER" && party.balance > 0 -> MaterialTheme.colorScheme.primary // Trader owes us
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = when {
                        party.type == "FARMER" && party.balance < 0 -> "Payable (To Farmer)"
                        party.type == "TRADER" && party.balance > 0 -> "Receivable (To Merchant)"
                        else -> "Settled"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddPartyDialog(
    type: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var licenseNo by remember { mutableStateOf("") }
    var bankDetails by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (type == "FARMER") "Register New Farmer" else "Register New Buyer",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Partner Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Primary Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                if (type == "TRADER") {
                    OutlinedTextField(
                        value = licenseNo,
                        onValueChange = { licenseNo = it },
                        label = { Text("APMC License No.") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = bankDetails,
                        onValueChange = { bankDetails = it },
                        label = { Text("Bank Name & Acc No (Farmer Payouts)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, phone, address, licenseNo.ifEmpty { null }, bankDetails.ifEmpty { null }) },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyLedgerDetailsDialog(
    party: Party,
    ledgerEntries: List<LedgerEntry>,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onRecordPayment: (Double, String, String) -> Unit,
    onDeleteParty: () -> Unit
) {
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = party.name, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (party.type == "FARMER") "Farmer Account" else "Trader Account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                // Outstanding Balance summary block
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (party.balance < 0)
                            MaterialTheme.colorScheme.errorContainer
                        else if (party.balance > 0)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CURRENT ACCOUNT BALANCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (party.balance < 0) currencyFormat.format(-party.balance) else currencyFormat.format(party.balance),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = when {
                                party.type == "FARMER" && party.balance < 0 -> "Payable to Farmer"
                                party.type == "TRADER" && party.balance > 0 -> "Receivable from Buyer"
                                else -> "All Settled Up"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action row for specific ledger
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showPaymentDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Payment, contentDescription = "Record Cash")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (party.type == "FARMER") "Pay Farmer" else "Receive Payment")
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Party")
                    }
                }

                Text(
                    text = "Account Statements / Ledgers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (ledgerEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded transactions on this ledger yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ledgerEntries) { entry ->
                            LedgerItemRow(entry = entry, currencyFormat = currencyFormat, dateFormat = dateFormat)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    // Quick Payment Recording Inner Dialog
    if (showPaymentDialog) {
        RecordPaymentDialog(
            party = party,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, method, notes ->
                onRecordPayment(amount, method, notes)
                showPaymentDialog = false
            }
        )
    }

    // Party delete confirm Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Partner Account?") },
            text = { Text("Are you absolutely sure you want to delete ${party.name}? Doing so will erase all history of their transactions on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteParty()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LedgerItemRow(
    entry: LedgerEntry,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (entry.entryType == "DEBIT")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (entry.entryType == "DEBIT") "Dr" else "Cr",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.entryType == "DEBIT")
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateFormat.format(Date(entry.date)) + if (entry.referenceId.isNotEmpty()) " • Ref: ${entry.referenceId}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = if (entry.entryType == "DEBIT")
                    "+" + currencyFormat.format(entry.amount)
                else
                    "-" + currencyFormat.format(entry.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (entry.entryType == "DEBIT")
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun RecordPaymentDialog(
    party: Party,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    var amountString by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" or "BANK"
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (party.type == "FARMER") "Record Cash Paid to Farmer" else "Record Receipt from Buyer",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Recording balance settlement details for ledger of ${party.name}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it },
                    label = { Text("Amount Settled (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Cash or Bank Segmented control
                Text(
                    text = "Select Payment Mode",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ElevatedButton(
                        onClick = { paymentMethod = "CASH" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (paymentMethod == "CASH") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (paymentMethod == "CASH") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("CASH")
                    }
                    ElevatedButton(
                        onClick = { paymentMethod = "BANK" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = if (paymentMethod == "BANK") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (paymentMethod == "BANK") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("BANK / UPI")
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Optional Notes (e.g. Bank Ref, Chq No)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountString.toDoubleOrNull() ?: 0.0
                    onConfirm(amt, paymentMethod, notes)
                },
                enabled = amountString.isNotBlank() && (amountString.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Confirm & Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
