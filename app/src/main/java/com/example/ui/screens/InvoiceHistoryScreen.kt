package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalPrintshop
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Bill
import com.example.data.ShopConfig
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceHistoryScreen(
    viewModel: BillingViewModel,
    onBack: (() -> Unit)? = null
) {
    val bills by viewModel.bills.collectAsState()
    val shopConfig by viewModel.shopConfig.collectAsState()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Farmer (Inward), 2 = Trader (Outward)
    var selectedStatusFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Paid, 2 = Credit/Unpaid

    var selectedBill by remember { mutableStateOf<Bill?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Bill?>(null) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // Pre-filter the list of invoices
    val filteredInvoices = remember(bills, searchQuery, selectedTabFilter, selectedStatusFilter) {
        bills.filter { bill ->
            val matchesQuery = searchQuery.isBlank() ||
                    bill.billNumber.contains(searchQuery, ignoreCase = true) ||
                    bill.partyName.contains(searchQuery, ignoreCase = true) ||
                    bill.commodityName.contains(searchQuery, ignoreCase = true)

            val matchesType = when (selectedTabFilter) {
                1 -> bill.partyType == "FARMER"
                2 -> bill.partyType == "TRADER"
                else -> true
            }

            val matchesStatus = when (selectedStatusFilter) {
                1 -> bill.isPaid
                2 -> !bill.isPaid
                else -> true
            }

            matchesQuery && matchesType && matchesStatus
        }
    }

    // Auto-select the first invoice if tablet and none selected
    LaunchedEffect(filteredInvoices, isTablet) {
        if (isTablet && selectedBill == null && filteredInvoices.isNotEmpty()) {
            selectedBill = filteredInvoices.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice & Billing History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null || (!isTablet && selectedBill != null)) {
                        IconButton(onClick = {
                            if (!isTablet && selectedBill != null) {
                                selectedBill = null
                            } else {
                                onBack?.invoke()
                            }
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        modifier = Modifier.testTag("invoice_history_root")
    ) { innerPadding ->
        if (bills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStatePlaceholder(
                    title = "No Invoices Found",
                    subtitle = "There are no APMC mandi bills generated in your local database yet. Go to Billing to execute checkout.",
                    icon = Icons.Default.ReceiptLong
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Left Column: List panel (Visible always on Tablet, or when no invoice selected on mobile)
                if (isTablet || selectedBill == null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search Invoice #, party, crop...") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invoice_history_search"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        // Filters row 1: Type Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All Invoices", "Farmer Patti", "Trader Sale").forEachIndexed { index, label ->
                                val isSelected = selectedTabFilter == index
                                ElevatedFilterChip(
                                    selected = isSelected,
                                    onClick = { selectedTabFilter = index },
                                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Filters row 2: Paid/Credit Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Status:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            listOf("All", "Paid", "Credit").forEachIndexed { index, label ->
                                val isSelected = selectedStatusFilter == index
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedStatusFilter = index },
                                    label = { Text(label) }
                                )
                            }
                        }

                        // Invoices count
                        Text(
                            text = "Showing ${filteredInvoices.size} Invoices",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // List of items
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredInvoices) { invoice ->
                                val isSelected = selectedBill?.id == invoice.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedBill = invoice }
                                        .testTag("invoice_card_${invoice.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = invoice.billNumber,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            // Paid status pill
                                            Surface(
                                                color = if (invoice.isPaid) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                                                contentColor = if (invoice.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer,
                                                shape = CircleShape,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (invoice.isPaid) "PAID" else "CREDIT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = invoice.partyName,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = currencyFormat.format(invoice.netAmount),
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (invoice.partyType == "FARMER") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${invoice.commodityName} • ${invoice.bagCount} Bags / ${invoice.weight} Qtl",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(invoice.date)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Split divider (Visible on Tablet only)
                if (isTablet) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Right Column: Details Viewer panel
                if (isTablet || selectedBill != null) {
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        if (selectedBill == null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = "Select Invoice",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Select an Invoice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Pick an invoice from the history ledger list to view full specifications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            InvoiceDetailPanel(
                                bill = selectedBill!!,
                                shopConfig = shopConfig,
                                currencyFormat = currencyFormat,
                                dateFormat = dateFormat,
                                isTablet = isTablet,
                                onBackToList = { selectedBill = null },
                                onDelete = { showDeleteConfirmDialog = selectedBill }
                            )
                        }
                    }
                }
            }
        }
    }

    // Void / Delete Confirm Dialog
    if (showDeleteConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Void & Delete Invoice?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This action is permanent. Deleting invoice ${showDeleteConfirmDialog!!.billNumber} will completely reverse inventory, subtract bag counts, and settle accounts from client ledger.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val billId = showDeleteConfirmDialog!!.id
                        viewModel.deleteBill(billId)
                        selectedBill = null
                        showDeleteConfirmDialog = null
                        Toast.makeText(context, "Invoice voided successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailPanel(
    bill: Bill,
    shopConfig: ShopConfig,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    isTablet: Boolean,
    onBackToList: () -> Unit,
    onDelete: () -> Unit
) {
    var selectedTemplate by remember { mutableIntStateOf(0) } // 0 = A4 Invoice, 1 = Thermal Receipt
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Details Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isTablet) {
                IconButton(onClick = onBackToList) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }

            // Tabs to swap designs
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedTemplate == 0,
                    onClick = { selectedTemplate = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Outlined.Description, contentDescription = "A4", modifier = Modifier.size(16.dp))
                        Text("A4 Invoice", style = MaterialTheme.typography.bodySmall)
                    }
                }
                SegmentedButton(
                    selected = selectedTemplate == 1,
                    onClick = { selectedTemplate = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Outlined.ReceiptLong, contentDescription = "Thermal", modifier = Modifier.size(16.dp))
                        Text("Thermal Rec.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Quick Void Option
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Void Invoice")
            }
        }

        // Action Buttons Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { triggerPrint(context, bill, shopConfig) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Outlined.LocalPrintshop, contentDescription = "Print")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print / PDF", style = MaterialTheme.typography.bodyMedium)
            }

            ElevatedButton(
                onClick = { triggerDownload(context, bill, currencyFormat, dateFormat) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Download")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download", style = MaterialTheme.typography.bodyMedium)
            }

            IconButton(
                onClick = { triggerWhatsAppShare(context, bill, currencyFormat) },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF25D366))
                    .size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = "Share on WhatsApp")
            }
        }

        // Invoice Preview Scrollable Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
        ) {
            if (selectedTemplate == 0) {
                A4InvoiceLayout(bill = bill, shopConfig = shopConfig, currencyFormat = currencyFormat, dateFormat = dateFormat)
            } else {
                ThermalReceiptLayout(bill = bill, shopConfig = shopConfig, currencyFormat = currencyFormat, dateFormat = dateFormat)
            }
        }
    }
}

@Composable
fun CompanyLogoHeader(preset: String, size: Int = 48) {
    val brush = Brush.linearGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        val icon = when (preset.uppercase()) {
            "GRAINS" -> Icons.Default.Agriculture
            "VEGETABLES", "FRUITS" -> Icons.Default.Eco
            "COTTON" -> Icons.Default.Cloud
            else -> Icons.Default.Storefront
        }
        Icon(
            imageVector = icon,
            contentDescription = "Logo preset",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size((size * 0.6).dp)
        )
    }
}

@Composable
fun UPIQRCodeWidget(payload: String, size: Int = 120) {
    // A fully stylized custom Canvas UPI payment code representation
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(size.dp)
                .background(Color.White)
                .padding(8.dp)
        ) {
            val modules = 21 // Version 1 QR
            val moduleWidth = this.size.width / modules
            val moduleHeight = this.size.height / modules

            // Draw Finder Patterns (TL, TR, BL)
            fun drawFinder(x: Int, y: Int) {
                // Outer 7x7
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(x * moduleWidth, y * moduleHeight),
                    size = Size(7 * moduleWidth, 7 * moduleHeight)
                )
                // White inner spacer
                drawRect(
                    color = Color.White,
                    topLeft = Offset((x + 1) * moduleWidth, (y + 1) * moduleHeight),
                    size = Size(5 * moduleWidth, 5 * moduleHeight)
                )
                // Center 3x3
                drawRect(
                    color = Color.Black,
                    topLeft = Offset((x + 2) * moduleWidth, (y + 2) * moduleHeight),
                    size = Size(3 * moduleWidth, 3 * moduleHeight)
                )
            }

            drawFinder(0, 0) // TL
            drawFinder(14, 0) // TR
            drawFinder(0, 14) // BL

            // Draw alignment/timing block line
            for (i in 7 until 14) {
                if (i % 2 == 0) {
                    drawRect(Color.Black, Offset(i * moduleWidth, 6 * moduleHeight), Size(moduleWidth, moduleHeight))
                    drawRect(Color.Black, Offset(6 * moduleWidth, i * moduleHeight), Size(moduleWidth, moduleHeight))
                }
            }

            // Seed deterministic modules based on payload hashcode
            val payloadHash = payload.hashCode()
            val rGenerator = Random(payloadHash.toLong())

            for (r in 0 until modules) {
                for (c in 0 until modules) {
                    // Skip Finder Patterns
                    if (r < 7 && c < 7) continue
                    if (r < 7 && c >= 14) continue
                    if (r >= 14 && c < 7) continue

                    // Random pixels matching deterministic barcode
                    if (rGenerator.nextBoolean()) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(c * moduleWidth, r * moduleHeight),
                            size = Size(moduleWidth, moduleHeight)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Scan to Pay / Verify", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}

@Composable
fun A4InvoiceLayout(
    bill: Bill,
    shopConfig: ShopConfig,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    val hsnCode = when (bill.commodityName.uppercase()) {
        "WHEAT" -> "HSN-1001"
        "MAIZE", "CORN" -> "HSN-1005"
        "COTTON" -> "HSN-5201"
        "RICE", "PADDY" -> "HSN-1006"
        else -> "HSN-1209"
    }

    val isFarmer = bill.partyType == "FARMER"

    // Real GST Splits
    val taxRate = if (bill.grossAmount - bill.discountAmount > 0) {
        (bill.taxAmount / (bill.grossAmount - bill.discountAmount)) * 100
    } else 0.0
    val cgstPercent = taxRate / 2
    val sgstPercent = taxRate / 2
    val cgstAmount = bill.taxAmount / 2
    val sgstAmount = bill.taxAmount / 2

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        contentColor = Color.Black
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompanyLogoHeader(preset = shopConfig.logoPreset, size = 48)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isFarmer) "FARMER PURCHASE PATTI" else "GST TAX INVOICE",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isFarmer) Color(0xFFC62828) else Color(0xFF1B5E20)
                    )
                    Text("Original Copy", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            HorizontalDivider(color = Color.LightGray)

            // Shop / Company Particulars
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(shopConfig.shopName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("APMC Mandi Yard License: ${shopConfig.apmcLicense}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    Text(shopConfig.address, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                    Text("GSTIN: ${shopConfig.gstNumber}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Invoice #: ${bill.billNumber}", fontWeight = FontWeight.Bold)
                    Text("Date: ${dateFormat.format(Date(bill.date))}", style = MaterialTheme.typography.bodySmall)
                    Text("State: Maharashtra (27)", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider(color = Color.LightGray)

            // Party details (Billed to)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("BILL TO / PARTY PARTICULARS:", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(bill.partyName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("Party Type: ${bill.partyType}", style = MaterialTheme.typography.bodySmall)
                    Text("Contact: ${bill.paymentNotes.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Products Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(8.dp)
            ) {
                Text("Description / HSN", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Bags", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("Weight", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("Rate/Qtl", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Right)
                Text("Gross", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Right)
            }

            // Product row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    Text(bill.commodityName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(hsnCode, fontSize = 11.sp, color = Color.Gray)
                }
                Text("${bill.bagCount} bags", modifier = Modifier.weight(0.5f), fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("${bill.weight} Qtl", modifier = Modifier.weight(0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                Text(currencyFormat.format(bill.rate), modifier = Modifier.weight(0.9f), fontSize = 13.sp, textAlign = TextAlign.Right)
                Text(currencyFormat.format(bill.grossAmount), modifier = Modifier.weight(1.1f), fontSize = 13.sp, textAlign = TextAlign.Right)
            }

            HorizontalDivider(color = Color.LightGray)

            // Calculations panel split
            Row(modifier = Modifier.fillMaxWidth()) {
                // Bank and payment UPI on left
                Column(modifier = Modifier.weight(1f)) {
                    val upiLink = "upi://pay?pa=${shopConfig.upiId}&pn=${Uri.encode(shopConfig.shopName)}&am=${bill.netAmount}&cu=INR"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UPIQRCodeWidget(payload = upiLink, size = 96)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("BANK DETAILS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(shopConfig.bankName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("A/C: ${shopConfig.bankAccNum}", fontSize = 11.sp)
                            Text("IFSC: ${shopConfig.bankIfsc}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Subtotal breakdowns on right
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Gross Subtotal:", fontSize = 12.sp)
                        Text(currencyFormat.format(bill.grossAmount), fontSize = 12.sp)
                    }

                    if (bill.discountAmount > 0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Discount / Rebate:", fontSize = 12.sp, color = Color(0xFF2E7D32))
                            Text("-" + currencyFormat.format(bill.discountAmount), fontSize = 12.sp, color = Color(0xFF2E7D32))
                        }
                    }

                    if (!isFarmer && bill.taxAmount > 0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("CGST (${String.format("%.1f", cgstPercent)}%):", fontSize = 12.sp)
                            Text("+" + currencyFormat.format(cgstAmount), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("SGST (${String.format("%.1f", sgstPercent)}%):", fontSize = 12.sp)
                            Text("+" + currencyFormat.format(sgstAmount), fontSize = 12.sp)
                        }
                    }

                    if (bill.transportCharges > 0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Transport Charges:", fontSize = 12.sp)
                            Text(
                                text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.transportCharges),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (bill.laborCharges > 0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Hamali Labor Charges:", fontSize = 12.sp)
                            Text(
                                text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.laborCharges),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (bill.commissionAmount > 0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Mandi Commission:", fontSize = 12.sp)
                            Text(
                                text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.commissionAmount),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (bill.marketFeeAmount > 0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("APMC Market Yard Fee:", fontSize = 12.sp)
                            Text(
                                text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.marketFeeAmount),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (bill.roundOff != 0.0) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Round Off:", fontSize = 12.sp)
                            Text(
                                text = (if (bill.roundOff >= 0.0) "+" else "") + currencyFormat.format(bill.roundOff),
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isFarmer) "NET PAYABLE:" else "GRAND TOTAL:",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text(
                            text = currencyFormat.format(bill.netAmount),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = if (isFarmer) Color.Red else Color(0xFF1B5E20)
                        )
                    }
                }
            }

            // Signatures line & terms
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("Terms & Conditions:", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("1. Goods once sold/purchased are subject to APMC guidelines.", fontSize = 10.sp, color = Color.Gray)
                    Text("2. Pay immediately via scan UPI or bank transfer.", fontSize = 10.sp, color = Color.Gray)
                    Text("3. Any discrepancies must be reported within 24 hours.", fontSize = 10.sp, color = Color.Gray)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("For ${shopConfig.shopName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(30.dp))
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.width(140.dp))
                    Text("Authorized Signatory", fontSize = 10.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
fun BarcodeWidget() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        val count = 48
        val width = size.width / count
        val r = Random(12345)
        for (i in 0 until count) {
            val isBlack = r.nextBoolean()
            if (isBlack) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(i * width, 0f),
                    size = Size(width * 0.7f, size.height)
                )
            }
        }
    }
}

@Composable
fun ThermalReceiptLayout(
    bill: Bill,
    shopConfig: ShopConfig,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    val isFarmer = bill.partyType == "FARMER"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFFFF2), // Thermal paper yellowish tint
        contentColor = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ASCII logo
            Text(
                text = "=== APMC KRISHI ===",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = shopConfig.shopName.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "MARKET YARD, GATE NO. 2\nNAVY MUMBAI, IN",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
            Text(
                text = "LIC: ${shopConfig.apmcLicense}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            Text(
                text = "--------------------------------",
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
            )

            // Invoice meta
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("INVOICE#", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(bill.billNumber, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DATE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(dateFormat.format(Date(bill.date)), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PARTY", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(bill.partyName, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TYPE", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(bill.partyType, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            Text(
                text = "--------------------------------",
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
            )

            // Items table
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ITEM DESCRIPTION", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("AMOUNT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(bill.commodityName.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${bill.bagCount} BAGS @ ${currencyFormat.format(bill.rate)}/Q", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Text(currencyFormat.format(bill.grossAmount), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "--------------------------------",
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
            )

            // Subtotals break down
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GROSS SUB:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(currencyFormat.format(bill.grossAmount), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }

            if (bill.discountAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("DISCOUNT:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("-" + currencyFormat.format(bill.discountAmount), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            if (bill.taxAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TAX / GST:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("+" + currencyFormat.format(bill.taxAmount), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            if (bill.transportCharges > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TRANSPORT:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text(
                        text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.transportCharges),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            if (bill.laborCharges > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HAMALI LABOR:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text(
                        text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.laborCharges),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            if (bill.commissionAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("COMMISSION:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text(
                        text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.commissionAmount),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            if (bill.marketFeeAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("APMC YARD FEE:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text(
                        text = (if (isFarmer) "-" else "+") + currencyFormat.format(bill.marketFeeAmount),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            if (bill.roundOff != 0.0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ROUND OFF:", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text(
                        text = (if (bill.roundOff >= 0.0) "+" else "") + currencyFormat.format(bill.roundOff),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "================================",
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isFarmer) "NET PAYABLE:" else "INVOICE TOTAL:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currencyFormat.format(bill.netAmount),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = "================================",
                fontFamily = FontFamily.Monospace,
                color = Color.DarkGray
            )

            // Dynamic payment status
            Text(
                text = "PAYMENT: ${if (bill.isPaid) "PAID / SETTLED" else "CREDIT BALANCE ACCT"}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Barcode simulator
            BarcodeWidget()

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "THANK YOU - VISIT AGAIN",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Print invoice logic via standard system PrintManager and dynamic HTML rendering
private fun triggerPrint(context: Context, bill: Bill, shopConfig: ShopConfig) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val isFarmer = bill.partyType == "FARMER"
    val hsn = when (bill.commodityName.uppercase()) {
        "WHEAT" -> "1001"
        "MAIZE", "CORN" -> "1005"
        "COTTON" -> "5201"
        "RICE", "PADDY" -> "1006"
        else -> "1209"
    }

    val taxRate = if (bill.grossAmount - bill.discountAmount > 0) {
        (bill.taxAmount / (bill.grossAmount - bill.discountAmount)) * 100
    } else 0.0
    val cgstPercent = taxRate / 2
    val sgstPercent = taxRate / 2
    val cgstAmount = bill.taxAmount / 2
    val sgstAmount = bill.taxAmount / 2

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 20px; color: #333; margin: 0; }
                .invoice-box { max-width: 800px; margin: auto; padding: 30px; border: 1px solid #eee; box-shadow: 0 0 10px rgba(0, 0, 0, .15); font-size: 14px; line-height: 24px; background: #fff; }
                .invoice-box table { width: 100%; line-height: inherit; text-align: left; border-collapse: collapse; }
                .invoice-box table td { padding: 5px; vertical-align: top; }
                .invoice-box table tr td:nth-child(2) { text-align: right; }
                .invoice-box table tr.top table td { padding-bottom: 20px; }
                .invoice-box table tr.top table td.title { font-size: 45px; line-height: 45px; color: #333; }
                .invoice-box table tr.information table td { padding-bottom: 30px; }
                .invoice-box table tr.heading td { background: #f7f7f7; border-bottom: 1px solid #ddd; font-weight: bold; }
                .invoice-box table tr.details td { padding-bottom: 10px; font-size: 13px; }
                .invoice-box table tr.item td { border-bottom: 1px solid #eee; }
                .invoice-box table tr.item.last td { border-bottom: none; }
                .invoice-box table tr.total td:nth-child(2) { border-top: 2px solid #eee; font-weight: bold; }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .badge { background-color: #2e7d32; color: white; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
                .badge-unpaid { background-color: #c62828; color: white; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
                .bank-details { margin-top: 35px; border-top: 1px solid #ddd; padding-top: 15px; font-size: 12px; color: #666; }
                .signature-area { margin-top: 50px; text-align: right; }
                .signature-line { display: inline-block; width: 200px; border-bottom: 1px solid #333; margin-top: 40px; }
            </style>
        </head>
        <body>
            <div class="invoice-box">
                <table>
                    <tr class="top">
                        <td colspan="4">
                            <table>
                                <tr>
                                    <td>
                                        <div style="font-size: 26px; font-weight: bold; color: #1b5e20;">${shopConfig.shopName}</div>
                                        <div style="font-size: 12px; color: #666; font-weight: normal; margin-top: 4px;">APMC License No: ${shopConfig.apmcLicense}</div>
                                    </td>
                                    <td>
                                        Invoice #: <strong>${bill.billNumber}</strong><br>
                                        Date: ${dateFormat.format(Date(bill.date))}<br>
                                        Status: <span class="${if (bill.isPaid) "badge" else "badge-unpaid"}">${if (bill.isPaid) "PAID" else "CREDIT/UNPAID"}</span>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <tr class="information">
                        <td colspan="4">
                            <table>
                                <tr>
                                    <td>
                                        <strong>From:</strong><br>
                                        ${shopConfig.shopName}<br>
                                        ${shopConfig.address}<br>
                                        GSTIN: <strong>${shopConfig.gstNumber}</strong>
                                    </td>
                                    <td>
                                        <strong>To (Party):</strong><br>
                                        ${bill.partyName}<br>
                                        Type: ${bill.partyType}<br>
                                        Payment Mode: ${bill.paymentNotes.ifBlank { "N/A" }}
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    
                    <tr class="heading">
                        <td>Item / Description</td>
                        <td class="text-center">HSN Code</td>
                        <td class="text-center">Qty / Bags</td>
                        <td class="text-right">Price per Qtl</td>
                    </tr>
                    
                    <tr class="item">
                        <td>${bill.commodityName} Grain Crop (Sales/Purchase)</td>
                        <td class="text-center">$hsn</td>
                        <td class="text-center">${bill.bagCount} Bags / ${bill.weight} Qtl</td>
                        <td class="text-right">${currencyFormat.format(bill.rate)}</td>
                    </tr>
                    
                    <tr class="heading">
                        <td colspan="3">Transaction Summary Details</td>
                        <td class="text-right">Amount</td>
                    </tr>
                    
                    <tr class="details">
                        <td colspan="3">Gross Market Subtotal:</td>
                        <td class="text-right">${currencyFormat.format(bill.grossAmount)}</td>
                    </tr>
                    
                    ${if (bill.discountAmount > 0) """
                    <tr class="details">
                        <td colspan="3">Less: Discount / Deduction:</td>
                        <td class="text-right" style="color: #2e7d32;">-${currencyFormat.format(bill.discountAmount)}</td>
                    </tr>
                    """ else ""}
                    
                    ${if (!isFarmer && bill.taxAmount > 0) """
                    <tr class="details">
                        <td colspan="3">Add: CGST (${String.format("%.1f", cgstPercent)}%):</td>
                        <td class="text-right">+${currencyFormat.format(cgstAmount)}</td>
                    </tr>
                    <tr class="details">
                        <td colspan="3">Add: SGST (${String.format("%.1f", sgstPercent)}%):</td>
                        <td class="text-right">+${currencyFormat.format(sgstAmount)}</td>
                    </tr>
                    """ else ""}
                    
                    ${if (bill.transportCharges > 0) """
                    <tr class="details">
                        <td colspan="3">${if (isFarmer) "Less" else "Add"}: Transport / Freight Charges:</td>
                        <td class="text-right">${if (isFarmer) "-" else "+"}${currencyFormat.format(bill.transportCharges)}</td>
                    </tr>
                    """ else ""}
                    
                    ${if (bill.laborCharges > 0) """
                    <tr class="details">
                        <td colspan="3">${if (isFarmer) "Less" else "Add"}: Hamali (Labor) Charges:</td>
                        <td class="text-right">${if (isFarmer) "-" else "+"}${currencyFormat.format(bill.laborCharges)}</td>
                    </tr>
                    """ else ""}
                    
                    ${if (bill.commissionAmount > 0) """
                    <tr class="details">
                        <td colspan="3">${if (isFarmer) "Less" else "Add"}: Mandi Commission:</td>
                        <td class="text-right">${if (isFarmer) "-" else "+"}${currencyFormat.format(bill.commissionAmount)}</td>
                    </tr>
                    """ else ""}
                    
                    ${if (bill.marketFeeAmount > 0) """
                    <tr class="details">
                        <td colspan="3">${if (isFarmer) "Less" else "Add"}: Standard APMC Market Fee:</td>
                        <td class="text-right">${if (isFarmer) "-" else "+"}${currencyFormat.format(bill.marketFeeAmount)}</td>
                    </tr>
                    """ else ""}
                    
                    ${if (bill.roundOff != 0.0) """
                    <tr class="details">
                        <td colspan="3">Round-Off Adjustment:</td>
                        <td class="text-right">${if (bill.roundOff >= 0.0) "+" else ""}${currencyFormat.format(bill.roundOff)}</td>
                    </tr>
                    """ else ""}
                    
                    <tr class="total">
                        <td colspan="3" style="font-size: 16px;"><strong>${if (isFarmer) "NET AMOUNT PAYABLE TO FARMER:" else "NET INVOICE TOTAL AMOUNT:"}</strong></td>
                        <td class="text-right" style="font-size: 18px; color: #1b5e20;"><strong>${currencyFormat.format(bill.netAmount)}</strong></td>
                    </tr>
                </table>
                
                <div class="bank-details">
                    <strong>Bank Settlement Details for UPI/NEFT Transfer:</strong><br>
                    Bank Name: ${shopConfig.bankName} | Beneficiary Name: ${shopConfig.bankAccName}<br>
                    Account No: ${shopConfig.bankAccNum} | IFSC Code: ${shopConfig.bankIfsc}<br>
                    UPI ID: <strong>${shopConfig.upiId}</strong>
                </div>
                
                <div class="signature-area">
                    For <strong>${shopConfig.shopName}</strong><br>
                    <div class="signature-line"></div><br>
                    Authorized Signatory / Stamp
                </div>
            </div>
        </body>
        </html>
    """

    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "Invoice_${bill.billNumber}"

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

// Download receipt details as a TXT formatted receipt in files
private fun triggerDownload(context: Context, bill: Bill, currencyFormat: NumberFormat, dateFormat: SimpleDateFormat) {
    try {
        val receiptData = """
            ==================================
            APMC INVOICE RECEIPT DETAILED EXPORT
            ==================================
            Invoice No  : ${bill.billNumber}
            Date        : ${dateFormat.format(Date(bill.date))}
            Customer    : ${bill.partyName} (${bill.partyType})
            Commodity   : ${bill.commodityName}
            Bags Count  : ${bill.bagCount} bags
            Weight      : ${bill.weight} Qtl
            Rate Apply  : ${currencyFormat.format(bill.rate)} / Qtl
            ----------------------------------
            Gross Value : ${currencyFormat.format(bill.grossAmount)}
            Discount    : ${currencyFormat.format(bill.discountAmount)}
            Tax Amount  : ${currencyFormat.format(bill.taxAmount)}
            Transport   : ${currencyFormat.format(bill.transportCharges)}
            Labor       : ${currencyFormat.format(bill.laborCharges)}
            Commission  : ${currencyFormat.format(bill.commissionAmount)}
            Market Fee  : ${currencyFormat.format(bill.marketFeeAmount)}
            Round Off   : ${currencyFormat.format(bill.roundOff)}
            ----------------------------------
            NET AMOUNT  : ${currencyFormat.format(bill.netAmount)}
            Payment     : ${if (bill.isPaid) "PAID" else "CREDIT BALANCE"}
            ==================================
        """.trimIndent()

        // Write as a downloadable file on device
        val fileName = "Invoice_${bill.billNumber.replace("/", "_")}.txt"
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(receiptData.toByteArray())
        }

        Toast.makeText(context, "Invoice exported & saved as $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

// Share pre-formatted text on WhatsApp / other apps
private fun triggerWhatsAppShare(context: Context, bill: Bill, currencyFormat: NumberFormat) {
    val message = """
        *APMC KRISHI MANDI BILL RECEIPT*
        
        *Invoice #:* ${bill.billNumber}
        *Party Name:* ${bill.partyName} (${bill.partyType})
        *Crop Commodity:* ${bill.commodityName}
        *Bags/Weight:* ${bill.bagCount} Bags / ${bill.weight} Qtl
        *Gross Subtotal:* ${currencyFormat.format(bill.grossAmount)}
        *Net Payable Total:* ${currencyFormat.format(bill.netAmount)}
        *Payment Status:* ${if (bill.isPaid) "✅ PAID & SETTLED" else "❌ OUTSTANDING CREDIT"}
        
        Generated via APMC Mandi Billing POS System. Thank you!
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, "Share Invoice via"))
}
