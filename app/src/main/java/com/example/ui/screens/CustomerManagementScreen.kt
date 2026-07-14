package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.Party
import com.example.data.LedgerEntry
import com.example.data.Bill
import com.example.ui.BillingViewModel
import com.example.ui.ActionState
import com.example.ui.components.EmptyStatePlaceholder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementScreen(viewModel: BillingViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val parties by viewModel.parties.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val supabaseUrl by viewModel.supabaseUrl.collectAsState()

    // Filter only Traders (who are the buyers/customers)
    val customers = remember(parties) { parties.filter { it.type == "TRADER" } }

    // Search and filter states
    var searchQuery by remember { mutableStateOf("") }
    var balanceFilter by remember { mutableStateOf("ALL") } // "ALL", "OUTSTANDING", "SETTLED", "ADVANCE"
    
    // Pagination states
    var currentPage by remember { mutableStateOf(1) }
    var itemsPerPage by remember { mutableStateOf(5) } // 5, 10, 20

    // UI Dialog & Detail States
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Party?>(null) }
    var selectedCustomerForProfile by remember { mutableStateOf<Party?>(null) }
    var customerToDelete by remember { mutableStateOf<Party?>(null) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    // Filtered customer list
    val filteredCustomers = remember(customers, searchQuery, balanceFilter) {
        customers.filter { customer ->
            val matchesSearch = customer.name.contains(searchQuery, ignoreCase = true) ||
                    customer.phone.contains(searchQuery) ||
                    (customer.licenseNo?.contains(searchQuery, ignoreCase = true) ?: false)

            val matchesBalance = when (balanceFilter) {
                "OUTSTANDING" -> customer.balance > 0.0 // Traders owe us money
                "SETTLED" -> customer.balance == 0.0
                "ADVANCE" -> customer.balance < 0.0 // They paid extra / credit balance
                else -> true
            }

            matchesSearch && matchesBalance
        }
    }

    // Paginated customer list
    val totalItems = filteredCustomers.size
    val totalPages = maxOf(1, (totalItems + itemsPerPage - 1) / itemsPerPage)
    
    // Adjust current page if it is out of bounds due to filtering changes
    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) {
            currentPage = totalPages
        }
    }

    val paginatedCustomers = remember(filteredCustomers, currentPage, itemsPerPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        filteredCustomers.drop(startIndex).take(itemsPerPage)
    }

    // Staff access protection
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
                        text = "Staff roles do not have permissions to access or manage the Customer directory database. Please contact your administrator.",
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Supabase Connection & Sync Bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (supabaseUrl.isNotBlank())
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (supabaseUrl.isNotBlank()) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                        contentDescription = "Cloud Status",
                        tint = if (supabaseUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (supabaseUrl.isNotBlank()) "Supabase Linked & Live Sync Enabled" else "Local-First Sandbox Mode",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (supabaseUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (supabaseUrl.isNotBlank()) "Customer changes automatically sync to cloud. Click Sync to pull updates." else "Set up your credentials in Shop Settings to enable cloud synchronization.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (supabaseUrl.isNotBlank()) {
                    Button(
                        onClick = { viewModel.syncPartiesWithSupabase() },
                        enabled = actionState !is ActionState.Loading,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cloud Sync", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Header and Actions Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Customer Ledger & Accounts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Manage registered merchant buyers, outstanding balances, invoices, and payments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Customer")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Customer")
            }
        }

        // Search, Filters & Controls Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Search text field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, phone or license...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )

                    // Items per page drop down / selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Show:", style = MaterialTheme.typography.bodySmall)
                        listOf(5, 10, 20).forEach { size ->
                            FilterChip(
                                selected = itemsPerPage == size,
                                onClick = {
                                    itemsPerPage = size
                                    currentPage = 1
                                },
                                label = { Text(size.toString()) },
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                }

                // Balance filter row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Filters:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    
                    val balanceFilters = listOf(
                        "ALL" to "All Customers",
                        "OUTSTANDING" to "Outstanding Debt",
                        "SETTLED" to "Settled Balance",
                        "ADVANCE" to "Credit Advance"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(balanceFilters) { (filter, label) ->
                            val isSelected = balanceFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { balanceFilter = filter },
                                label = { Text(label) },
                                leadingIcon = if (isSelected) {
                                    { Icon(imageVector = Icons.Default.Check, contentDescription = "Checked", modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // Customer Listing Grid / Table Area
        if (paginatedCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyStatePlaceholder(
                    title = "No Customers Found",
                    subtitle = "Adjust your search parameters/filters or register a new Customer to start billing.",
                    icon = Icons.Default.PeopleOutline
                )
            }
        } else {
            if (isTablet) {
                // Responsive Grid Table for Tablets / Desktop
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Table Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Customer Name", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("Contact & Address", modifier = Modifier.weight(2.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("APMC License", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("Outstanding Balance", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("Actions", modifier = Modifier.width(180.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Table Rows
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(paginatedCustomers) { customer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedCustomerForProfile = customer }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Name cell
                                    Row(
                                        modifier = Modifier.weight(2f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = customer.name.take(1).uppercase(),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Text(
                                            text = customer.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Contact Cell
                                    Column(modifier = Modifier.weight(2.5f)) {
                                        Text(customer.phone, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(customer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    // APMC License
                                    Text(
                                        text = customer.licenseNo ?: "N/A",
                                        modifier = Modifier.weight(1.5f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (customer.licenseNo != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Balance
                                    Column(modifier = Modifier.weight(2f)) {
                                        val displayBalance = if (customer.balance < 0) -customer.balance else customer.balance
                                        Text(
                                            text = currencyFormat.format(displayBalance),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                customer.balance > 0.0 -> MaterialTheme.colorScheme.error // Trader owes us
                                                customer.balance < 0.0 -> MaterialTheme.colorScheme.primary // We owe trader (credit advance)
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Text(
                                            text = when {
                                                customer.balance > 0.0 -> "Receivable"
                                                customer.balance < 0.0 -> "Credit Advance"
                                                else -> "Settled"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Actions
                                    Row(
                                        modifier = Modifier.width(180.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { selectedCustomerForProfile = customer }) {
                                            Icon(imageVector = Icons.Default.Visibility, contentDescription = "Profile", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { editingCustomer = customer }) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.secondary)
                                        }
                                        IconButton(onClick = { customerToDelete = customer }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            } else {
                // Mobile-friendly card list (Responsive fall-back)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(paginatedCustomers) { customer ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomerForProfile = customer },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = customer.name.take(1).uppercase(),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = customer.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "📞 ${customer.phone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Balance Section
                                    Column(horizontalAlignment = Alignment.End) {
                                        val displayBalance = if (customer.balance < 0) -customer.balance else customer.balance
                                        Text(
                                            text = currencyFormat.format(displayBalance),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Black,
                                            color = when {
                                                customer.balance > 0.0 -> MaterialTheme.colorScheme.error
                                                customer.balance < 0.0 -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        Text(
                                            text = when {
                                                customer.balance > 0.0 -> "Receivable"
                                                customer.balance < 0.0 -> "Advance"
                                                else -> "Settled"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (customer.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "📍 ${customer.address}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { selectedCustomerForProfile = customer }) {
                                        Icon(imageVector = Icons.Default.Visibility, contentDescription = "View Profile", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Profile & Ledger")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { editingCustomer = customer }) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                    IconButton(onClick = { customerToDelete = customer }) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pagination Controller Footer
        if (totalPages > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${(currentPage - 1) * itemsPerPage + 1} - ${minOf(currentPage * itemsPerPage, totalItems)} of $totalItems customers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPage > 1) currentPage-- },
                        enabled = currentPage > 1
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }

                    // Simple display of pages e.g. "Page 1 of 5"
                    Text(
                        text = "Page $currentPage of $totalPages",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { if (currentPage < totalPages) currentPage++ },
                        enabled = currentPage < totalPages
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                }
            }
        }
    }

    // Add Customer Dialog with real-time validation
    if (showAddDialog) {
        CustomerFormDialog(
            title = "Register New Customer",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, address, license ->
                viewModel.addParty(
                    name = name,
                    type = "TRADER",
                    phone = phone,
                    address = address,
                    licenseNo = license
                )
                showAddDialog = false
            }
        )
    }

    // Edit Customer Dialog
    if (editingCustomer != null) {
        val customer = editingCustomer!!
        CustomerFormDialog(
            title = "Edit Customer Profile",
            initialName = customer.name,
            initialPhone = customer.phone,
            initialAddress = customer.address,
            initialLicense = customer.licenseNo ?: "",
            onDismiss = { editingCustomer = null },
            onConfirm = { name, phone, address, license ->
                viewModel.updateParty(
                    customer.copy(
                        name = name,
                        phone = phone,
                        address = address,
                        licenseNo = license.ifBlank { null }
                    )
                )
                editingCustomer = null
            }
        )
    }

    // Delete Customer Confirm Dialog
    if (customerToDelete != null) {
        val customer = customerToDelete!!
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Customer Directory Profile") },
            text = {
                Text("Are you sure you want to permanently delete ${customer.name}? This operation is irreversible. Any bills or ledger entries registered for this merchant will become unassociated.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteParty(customer)
                        customerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Customer Detailed Profile & Ledger Hub Dialog
    if (selectedCustomerForProfile != null) {
        val customer = selectedCustomerForProfile!!
        val customerBills = bills.filter { it.partyId == customer.id }
        val customerLedger = ledgerEntries.filter { it.partyId == customer.id }

        CustomerProfileDialog(
            customer = customer,
            bills = customerBills,
            ledger = customerLedger,
            currencyFormat = currencyFormat,
            dateFormat = dateFormat,
            onDismiss = { selectedCustomerForProfile = null },
            onRecordPayment = { amount, method, notes ->
                viewModel.recordPayment(customer, amount, method, notes)
                selectedCustomerForProfile = null // Reload detail
            }
        )
    }
}

// Reusable Customer Form Dialog with detailed real-time validation checks
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormDialog(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    initialAddress: String = "",
    initialLicense: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    var license by remember { mutableStateOf(initialLicense) }

    // Validation States
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var licenseError by remember { mutableStateOf<String?>(null) }

    fun validateAndSubmit() {
        var isValid = true

        if (name.trim().length < 3) {
            nameError = "Name must be at least 3 characters long."
            isValid = false
        } else {
            nameError = null
        }

        val phoneDigits = phone.filter { it.isDigit() }
        if (phoneDigits.length != 10) {
            phoneError = "Phone must be a valid 10-digit number."
            isValid = false
        } else {
            phoneError = null
        }

        if (license.isNotBlank() && license.trim().length < 4) {
            licenseError = "APMC License No must be at least 4 characters if provided."
            isValid = false
        } else {
            licenseError = null
        }

        if (isValid) {
            onConfirm(name.trim(), phoneDigits, address.trim(), license.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Customer Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        if (phoneError != null) phoneError = null
                    },
                    label = { Text("Mobile Contact *") },
                    placeholder = { Text("10-digit number") },
                    isError = phoneError != null,
                    supportingText = phoneError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop or Primary Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = license,
                    onValueChange = {
                        license = it
                        if (licenseError != null) licenseError = null
                    },
                    label = { Text("APMC License No.") },
                    isError = licenseError != null,
                    supportingText = licenseError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    placeholder = { Text("e.g. APMC-AHD-T992") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = ::validateAndSubmit) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Dialog that encapsulates the Customer Profile, complete Ledger and Payment logs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileDialog(
    customer: Party,
    bills: List<Bill>,
    ledger: List<LedgerEntry>,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onRecordPayment: (Double, String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Invoices/Sales, 1 = Financial Ledger History
    var showRecordPaymentDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(customer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("Licensed Buyer • ID #${customer.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Profile")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Outstanding Balance Display Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile card info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Contact & Info", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("📞 Phone: ${customer.phone}", style = MaterialTheme.typography.bodySmall)
                            Text("📍 Shop: ${customer.address.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("🪪 License: ${customer.licenseNo ?: "No License"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Balance card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                customer.balance > 0.0 -> MaterialTheme.colorScheme.errorContainer
                                customer.balance < 0.0 -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when {
                                    customer.balance > 0.0 -> "Total Outstanding Balance"
                                    customer.balance < 0.0 -> "Available Credit Advance"
                                    else -> "Balance Settled"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            val displayBalance = if (customer.balance < 0.0) -customer.balance else customer.balance
                            Text(
                                text = currencyFormat.format(displayBalance),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = when {
                                    customer.balance > 0.0 -> MaterialTheme.colorScheme.error
                                    customer.balance < 0.0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                // Inner Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { showRecordPaymentDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = "Receive payment")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payment Received")
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Invoice Billing History (${bills.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Financial Ledger Logs (${ledger.size})") }
                    )
                }

                // Tab details
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == 0) {
                        // Billing Sales List
                        if (bills.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No invoices or bills generated for this Customer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(bills.sortedByDescending { it.date }) { bill ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = "Invoice ${bill.billNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(text = dateFormat.format(Date(bill.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(text = "${bill.bagCount} bags of ${bill.commodityName} (${bill.weight} Qtl)", style = MaterialTheme.typography.bodySmall)
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(text = currencyFormat.format(bill.netAmount), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (bill.isPaid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (bill.isPaid) "PAID" else "UNPAID",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (bill.isPaid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Ledger entries logs
                        if (ledger.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No financial ledger entries recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ledger.sortedByDescending { it.date }) { entry ->
                                    val isCredit = entry.entryType == "CREDIT"
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isCredit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (isCredit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                                Column {
                                                    Text(text = entry.description, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                    Text(text = dateFormat.format(Date(entry.date)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    if (entry.referenceId.isNotBlank()) {
                                                        Text(text = "Ref: ${entry.referenceId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                            }

                                            Text(
                                                text = (if (isCredit) "+" else "-") + currencyFormat.format(entry.amount),
                                                fontWeight = FontWeight.Black,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    // Inner Record Payment Dialog with validation
    if (showRecordPaymentDialog) {
        var paymentAmountStr by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf("CASH") }
        var paymentNotes by remember { mutableStateOf("") }
        var amountError by remember { mutableStateOf<String?>(null) }

        val paymentMethods = listOf("CASH", "UPI", "CHEQUE", "BANK_TRANSFER")

        AlertDialog(
            onDismissRequest = { showRecordPaymentDialog = false },
            title = { Text("Record Customer Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = paymentAmountStr,
                        onValueChange = {
                            paymentAmountStr = it
                            if (amountError != null) amountError = null
                        },
                        label = { Text("Payment Amount Received (₹) *") },
                        isError = amountError != null,
                        supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Text("Payment Method:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paymentMethods.forEach { method ->
                            val isSelected = paymentMethod == method
                            FilterChip(
                                selected = isSelected,
                                onClick = { paymentMethod = method },
                                label = { Text(method) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = paymentNotes,
                        onValueChange = { paymentNotes = it },
                        label = { Text("Remarks / Notes") },
                        placeholder = { Text("Receipt details, bank tx reference...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = paymentAmountStr.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            amountError = "Please enter a valid amount greater than 0."
                        } else {
                            onRecordPayment(amount, paymentMethod, paymentNotes)
                            showRecordPaymentDialog = false
                        }
                    }
                ) {
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordPaymentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
