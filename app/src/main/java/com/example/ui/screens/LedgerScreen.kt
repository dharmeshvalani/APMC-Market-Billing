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
fun LedgerScreen(viewModel: BillingViewModel) {
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
                        text = "Staff roles do not have permissions to access or view Consolidated Ledger Balances and Payments. Please contact your administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        return
    }

    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val parties by viewModel.parties.collectAsState()

    var showGeneralPaymentDialog by remember { mutableStateOf(false) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0 = All, 1 = Debits (Receivables), 2 = Credits (Payables)

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    // Filtered ledger logic
    val filteredEntries = when (selectedFilterIndex) {
        1 -> ledgerEntries.filter { it.entryType == "DEBIT" }
        2 -> ledgerEntries.filter { it.entryType == "CREDIT" }
        else -> ledgerEntries
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header Stats block
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ledger Book",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total Entries: ${ledgerEntries.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showGeneralPaymentDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Settle")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payout/Receipt")
                    }
                }
            }

            // Debit/Credit filter bar
            TabRow(
                selectedTabIndex = selectedFilterIndex,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedFilterIndex == 0,
                    onClick = { selectedFilterIndex = 0 },
                    text = { Text("All Statements") }
                )
                Tab(
                    selected = selectedFilterIndex == 1,
                    onClick = { selectedFilterIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Debits (Dr)")
                        }
                    }
                )
                Tab(
                    selected = selectedFilterIndex == 2,
                    onClick = { selectedFilterIndex = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Credits (Cr)")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Statements entries lists
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePlaceholder(
                        title = "No Statements Recorded",
                        subtitle = "There are no transactions matching the chosen ledger filters.",
                        icon = Icons.Default.Book
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEntries) { entry ->
                        // Find matching party name for additional context
                        val matchedParty = parties.find { it.id == entry.partyId }
                        LedgerEntryCard(
                            entry = entry,
                            partyName = matchedParty?.name ?: "Unknown Partner",
                            partyType = matchedParty?.type ?: "PARTNER",
                            currencyFormat = currencyFormat,
                            dateFormat = dateFormat
                        )
                    }
                }
            }
        }

        // General record payment dialog
        if (showGeneralPaymentDialog) {
            GeneralPaymentRecordDialog(
                parties = parties,
                onDismiss = { showGeneralPaymentDialog = false },
                onConfirm = { party, amount, method, notes ->
                    viewModel.recordPayment(party, amount, method, notes)
                    showGeneralPaymentDialog = false
                }
            )
        }
    }
}

@Composable
fun LedgerEntryCard(
    entry: LedgerEntry,
    partyName: String,
    partyType: String,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Debit or Credit Circle Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.entryType == "DEBIT")
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = partyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormat.format(Date(entry.date)) + if (entry.referenceId.isNotEmpty()) " • Ref: ${entry.referenceId}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (entry.entryType == "DEBIT")
                        "+" + currencyFormat.format(entry.amount)
                    else
                        "-" + currencyFormat.format(entry.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (entry.entryType == "DEBIT")
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (partyType == "FARMER") "Seller Outflow" else "Buyer Inflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralPaymentRecordDialog(
    parties: List<Party>,
    onDismiss: () -> Unit,
    onConfirm: (Party, Double, String, String) -> Unit
) {
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    var amountString by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" or "BANK"
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Account Settlement", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select any registered Farmer or Trader to record custom payments/receipts against their outstanding ledgers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Party selection dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = partyDropdownExpanded,
                        onExpandedChange = { partyDropdownExpanded = !partyDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedParty?.name ?: "Select Farmer or Trader...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account Partner *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = partyDropdownExpanded,
                            onDismissRequest = { partyDropdownExpanded = false }
                        ) {
                            parties.forEach { party ->
                                DropdownMenuItem(
                                    text = { Text("${party.name} (${party.type})") },
                                    onClick = {
                                        selectedParty = party
                                        partyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedParty != null) {
                    val p = selectedParty!!
                    Text(
                        text = "Current balance: " + if (p.balance < 0) "We owe ₹${-p.balance}" else "They owe ₹${p.balance}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it },
                    label = { Text("Amount Settled (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

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
                    label = { Text("Optional Notes (Bank reference ID, Chq No)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedParty?.let { p ->
                        val amt = amountString.toDoubleOrNull() ?: 0.0
                        onConfirm(p, amt, paymentMethod, notes)
                    }
                },
                enabled = selectedParty != null && amountString.isNotBlank() && (amountString.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Confirm Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
