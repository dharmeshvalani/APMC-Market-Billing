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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.*
import com.example.ui.ActionState
import com.example.ui.BillingViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: BillingViewModel) {
    val context = LocalContext.current
    val bills by viewModel.bills.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val shopConfig by viewModel.shopConfig.collectAsState()

    var activeTab by remember { mutableStateOf("profit") } // profit, gst, expenses, analytics, leaderboard
    var timeFilter by remember { mutableStateOf("MONTHLY") } // DAILY, WEEKLY, MONTHLY, YEARLY

    var showAddExpenseDialog by remember { mutableStateOf(false) }

    // Aggregate filters
    val currentTime = System.currentTimeMillis()

    val filteredBills = remember(bills, timeFilter) {
        val limitTime = when (timeFilter) {
            "DAILY" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "WEEKLY" -> currentTime - (7 * 24 * 60 * 60 * 1000L)
            "MONTHLY" -> currentTime - (30 * 24 * 60 * 60 * 1000L)
            "YEARLY" -> currentTime - (365 * 24 * 60 * 60 * 1000L)
            else -> 0L
        }
        bills.filter { it.date >= limitTime }
    }

    val filteredExpenses = remember(expenses, timeFilter) {
        val limitTime = when (timeFilter) {
            "DAILY" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "WEEKLY" -> currentTime - (7 * 24 * 60 * 60 * 1000L)
            "MONTHLY" -> currentTime - (30 * 24 * 60 * 60 * 1000L)
            "YEARLY" -> currentTime - (365 * 24 * 60 * 60 * 1000L)
            else -> 0L
        }
        expenses.filter { it.date >= limitTime }
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Standard Math aggregations
    val totalTraderSales = filteredBills.filter { it.partyType == "TRADER" }.sumOf { it.netAmount }
    val grossTraderSales = filteredBills.filter { it.partyType == "TRADER" }.sumOf { it.grossAmount }
    val totalFarmerPurchases = filteredBills.filter { it.partyType == "FARMER" }.sumOf { it.netAmount }
    val grossFarmerPurchases = filteredBills.filter { it.partyType == "FARMER" }.sumOf { it.grossAmount }

    val commissionsEarned = filteredBills.sumOf { it.commissionAmount }
    val laborChargesCollected = filteredBills.sumOf { it.laborCharges }
    val marketFeesCollected = filteredBills.sumOf { it.marketFeeAmount }
    val totalGstCollected = filteredBills.sumOf { it.taxAmount }
    val totalExpenses = filteredExpenses.sumOf { it.amount }

    // Net Wholesaler Profit = Net Sales - Net Purchases - Expenses
    val tradingProfit = totalTraderSales - totalFarmerPurchases - totalExpenses
    // Net Commission Agent Profit = Commissions Earned + Labor Charges (if retained) - Expenses
    val brokerageProfit = commissionsEarned + (laborChargesCollected * 0.5) - totalExpenses // assume 50% labor goes to payouts

    Scaffold(
        floatingActionButton = {
            if (activeTab == "expenses") {
                FloatingActionButton(
                    onClick = { showAddExpenseDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_expense_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Log Expense")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Stats Selector bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Time Horizon Filter",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { filter ->
                            val isSelected = timeFilter == filter
                            Button(
                                onClick = { timeFilter = filter },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("time_filter_$filter"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null
                            ) {
                                Text(
                                    text = filter.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions: Share and Export
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { triggerPdfExport(context, filteredBills, filteredExpenses, timeFilter, shopConfig) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_pdf_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF Report", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { triggerExcelExport(context, filteredBills, filteredExpenses, timeFilter) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_excel_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                    border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Excel", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { triggerCsvExport(context, filteredBills, filteredExpenses, timeFilter) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_csv_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CSV Share", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-modules navigation tab layout
            ScrollableTabRow(
                selectedTabIndex = when (activeTab) {
                    "profit" -> 0
                    "gst" -> 1
                    "expenses" -> 2
                    "analytics" -> 3
                    "leaderboard" -> 4
                    else -> 0
                },
                edgePadding = 14.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider() }
            ) {
                Tab(
                    selected = activeTab == "profit",
                    onClick = { activeTab = "profit" },
                    modifier = Modifier.testTag("tab_profit"),
                    text = { Text("P&L Summary") }
                )
                Tab(
                    selected = activeTab == "gst",
                    onClick = { activeTab = "gst" },
                    modifier = Modifier.testTag("tab_gst"),
                    text = { Text("GST Tax Book") }
                )
                Tab(
                    selected = activeTab == "expenses",
                    onClick = { activeTab = "expenses" },
                    modifier = Modifier.testTag("tab_expenses"),
                    text = { Text("Direct Expenses") }
                )
                Tab(
                    selected = activeTab == "analytics",
                    onClick = { activeTab = "analytics" },
                    modifier = Modifier.testTag("tab_analytics"),
                    text = { Text("Visual Charts") }
                )
                Tab(
                    selected = activeTab == "leaderboard",
                    onClick = { activeTab = "leaderboard" },
                    modifier = Modifier.testTag("tab_leaderboard"),
                    text = { Text("Top Rankings") }
                )
            }

            // Sub-screen render switch
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (activeTab) {
                    "profit" -> ProfitSummaryView(
                        timeFilter = timeFilter,
                        grossTraderSales = grossTraderSales,
                        totalTraderSales = totalTraderSales,
                        grossFarmerPurchases = grossFarmerPurchases,
                        totalFarmerPurchases = totalFarmerPurchases,
                        commissionsEarned = commissionsEarned,
                        laborChargesCollected = laborChargesCollected,
                        marketFeesCollected = marketFeesCollected,
                        totalGstCollected = totalGstCollected,
                        totalExpenses = totalExpenses,
                        tradingProfit = tradingProfit,
                        brokerageProfit = brokerageProfit,
                        currencyFormat = currencyFormat
                    )
                    "gst" -> GstTaxBookView(
                        filteredBills = filteredBills,
                        currencyFormat = currencyFormat,
                        totalGstCollected = totalGstCollected
                    )
                    "expenses" -> DirectExpensesView(
                        filteredExpenses = filteredExpenses,
                        onDeleteExpense = { viewModel.deleteExpense(it.id) },
                        currencyFormat = currencyFormat,
                        dateFormat = dateFormat
                    )
                    "analytics" -> VisualChartsView(
                        filteredBills = filteredBills,
                        filteredExpenses = filteredExpenses,
                        currencyFormat = currencyFormat,
                        timeFilter = timeFilter
                    )
                    "leaderboard" -> LeaderboardsView(
                        filteredBills = filteredBills,
                        currencyFormat = currencyFormat
                    )
                }
            }
        }
    }

    // Modal dialog to Add Expenses
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onSave = { amount, category, description ->
                viewModel.recordExpense(amount, category, description)
                showAddExpenseDialog = false
            }
        )
    }
}

@Composable
fun ProfitSummaryView(
    timeFilter: String,
    grossTraderSales: Double,
    totalTraderSales: Double,
    grossFarmerPurchases: Double,
    totalFarmerPurchases: Double,
    commissionsEarned: Double,
    laborChargesCollected: Double,
    marketFeesCollected: Double,
    totalGstCollected: Double,
    totalExpenses: Double,
    tradingProfit: Double,
    brokerageProfit: Double,
    currencyFormat: NumberFormat
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "${timeFilter} Profitability Assessment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Wholesaler / Wholesale Wholesaling Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Wholesale Trading P&L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Buying from farmers, selling to buyers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    RowValueItem(label = "Gross Sales (Traders)", value = currencyFormat.format(grossTraderSales))
                    RowValueItem(label = "Net Trading Revenue (Sales)", value = currencyFormat.format(totalTraderSales))
                    RowValueItem(label = "Net Produce Cost (Farmers)", value = "-${currencyFormat.format(totalFarmerPurchases)}", color = Color(0xFFD32F2F))
                    RowValueItem(label = "Shop Operations Outlay", value = "-${currencyFormat.format(totalExpenses)}", color = Color(0xFFD32F2F))

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (tradingProfit >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Net Trading Margin",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (tradingProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            currencyFormat.format(tradingProfit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tradingProfit >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }

        // Commission Agent Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Handshake, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Commission Brokerage P&L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Earning from facilitation & mandi services", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    RowValueItem(label = "Commissions Earned (Arhat)", value = currencyFormat.format(commissionsEarned), color = Color(0xFF2E7D32))
                    RowValueItem(label = "Mandi Labor Fees (Collected)", value = currencyFormat.format(laborChargesCollected))
                    RowValueItem(label = "Operational Expenses", value = "-${currencyFormat.format(totalExpenses)}", color = Color(0xFFD32F2F))

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (brokerageProfit >= 0) Color(0xFFE0F2F1) else Color(0xFFFFEBEE),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Net Brokerage Profit",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (brokerageProfit >= 0) Color(0xFF00796B) else Color(0xFFC62828)
                        )
                        Text(
                            currencyFormat.format(brokerageProfit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (brokerageProfit >= 0) Color(0xFF00796B) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }

        // Consolidated Mandi Business Charges Recap
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Consolidated Mandi Surcharges & Taxes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    RowValueItem(label = "Total Mandi Cess / Market Fee Collected", value = currencyFormat.format(marketFeesCollected))
                    RowValueItem(label = "Total GST Input/Output Captured", value = currencyFormat.format(totalGstCollected))
                }
            }
        }
    }
}

@Composable
fun RowValueItem(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun GstTaxBookView(
    filteredBills: List<Bill>,
    currencyFormat: NumberFormat,
    totalGstCollected: Double
) {
    // Group invoices by GST percentage (0, 5, 12, 18)
    val gstGroups = remember(filteredBills) {
        val groups = mutableMapOf<Int, MutableList<Bill>>()
        filteredBills.forEach { bill ->
            // compute rate based on details
            val rate = if (bill.grossAmount - bill.discountAmount > 0.0) {
                val calculated = (bill.taxAmount / (bill.grossAmount - bill.discountAmount)) * 100
                // Match to closest standard APMC tax percentage
                when {
                    calculated < 2.5 -> 0
                    calculated < 8.0 -> 5
                    calculated < 15.0 -> 12
                    else -> 18
                }
            } else {
                0
            }
            groups.getOrPut(rate) { mutableListOf() }.add(bill)
        }
        groups.toSortedMap()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Consolidated GST liability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("CGST & SGST Split (50% each)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                    Text(
                        currencyFormat.format(totalGstCollected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text("Tax Class Slabs Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        if (gstGroups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No GST transactions recorded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(gstGroups.entries.toList()) { (slab, itemsList) ->
                val taxableAmount = itemsList.sumOf { it.grossAmount - it.discountAmount }
                val taxCollected = itemsList.sumOf { it.taxAmount }
                val cgst = taxCollected / 2
                val sgst = taxCollected / 2

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$slab%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Slab $slab% Commodities", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            Text("${itemsList.size} Invoices", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(10.dp))

                        RowValueItem(label = "Taxable Base Amount", value = currencyFormat.format(taxableAmount))
                        RowValueItem(label = "Central GST (CGST)", value = currencyFormat.format(cgst))
                        RowValueItem(label = "State GST (SGST)", value = currencyFormat.format(sgst))
                        RowValueItem(
                            label = "Total Tax Accrued",
                            value = currencyFormat.format(taxCollected),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DirectExpensesView(
    filteredExpenses: List<Expense>,
    onDeleteExpense: (Expense) -> Unit,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Direct Operational Expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Total: ${currencyFormat.format(filteredExpenses.sumOf { it.amount })}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828)
                )
            }
        }

        if (filteredExpenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MoneyOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No shop operational outlays recorded.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Text("Tap '+' below to log rent, transport, or salaries.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                    }
                }
            }
        } else {
            items(filteredExpenses) { expense ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (expense.category) {
                                            "Labor" -> Color(0xFFFFF3E0)
                                            "Transport" -> Color(0xFFE3F2FD)
                                            "Rent" -> Color(0xFFF3E5F5)
                                            "Office Supplies" -> Color(0xFFE8F5E9)
                                            "Mandi Levy" -> Color(0xFFE0F2F1)
                                            else -> Color(0xFFECEFF1)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (expense.category) {
                                        "Labor" -> Icons.Default.Engineering
                                        "Transport" -> Icons.Default.LocalShipping
                                        "Rent" -> Icons.Default.HomeWork
                                        "Office Supplies" -> Icons.Default.Create
                                        "Mandi Levy" -> Icons.Default.Gavel
                                        else -> Icons.Default.ReceiptLong
                                    },
                                    contentDescription = null,
                                    tint = when (expense.category) {
                                        "Labor" -> Color(0xFFE65100)
                                        "Transport" -> Color(0xFF0D47A1)
                                        "Rent" -> Color(0xFF4A148C)
                                        "Office Supplies" -> Color(0xFF1B5E20)
                                        "Mandi Levy" -> Color(0xFF004D40)
                                        else -> Color(0xFF37474F)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = expense.description.ifEmpty { expense.category },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${expense.category} • ${dateFormat.format(Date(expense.date))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currencyFormat.format(expense.amount),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { onDeleteExpense(expense) },
                                modifier = Modifier.testTag("delete_expense_${expense.id}")
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Expense", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualChartsView(
    filteredBills: List<Bill>,
    filteredExpenses: List<Expense>,
    currencyFormat: NumberFormat,
    timeFilter: String
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Data Visualization Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // BAR CHART: Sales and Cost Flow Trend over Time
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trader Sales & Cost of Procurement", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Visualization of recent cash flow in/out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                Spacer(modifier = Modifier.height(18.dp))

                // Compile data points dynamically
                val salesSum = filteredBills.filter { it.partyType == "TRADER" }.sumOf { it.netAmount }
                val purchasesSum = filteredBills.filter { it.partyType == "FARMER" }.sumOf { it.netAmount }
                val maxAmount = maxOf(salesSum, purchasesSum, 1.0)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height

                        // Draw Grid lines
                        for (i in 0..4) {
                            val y = height * (i / 4f)
                            drawLine(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Render Trading Sales bar
                        val salesBarHeight = (salesSum / maxAmount) * height
                        val barWidth = width * 0.25f

                        drawRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(width * 0.15f, height - salesBarHeight.toFloat()),
                            size = Size(barWidth, salesBarHeight.toFloat())
                        )

                        // Render purchases cost bar
                        val purchaseBarHeight = (purchasesSum / maxAmount) * height
                        drawRect(
                            color = Color(0xFFEF5350),
                            topLeft = Offset(width * 0.6f, height - purchaseBarHeight.toFloat()),
                            size = Size(barWidth, purchaseBarHeight.toFloat())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sales: ${currencyFormat.format(salesSum)}", style = MaterialTheme.typography.labelMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFEF5350), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Purchases: ${currencyFormat.format(purchasesSum)}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // DONUT CHART: Expense allocation by categories
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Operational Expense Distribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))

                val expenseGroups = remember(filteredExpenses) {
                    filteredExpenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
                }

                val totalExp = expenseGroups.values.sum()

                if (totalExp <= 0.0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No expenses logged to display charts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    val colors = listOf(Color(0xFFE65100), Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF1B5E20), Color(0xFF004D40), Color(0xFF37474F))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                var startAngle = -90f
                                expenseGroups.entries.forEachIndexed { index, entry ->
                                    val sweep = (entry.value / totalExp * 360).toFloat()
                                    drawArc(
                                        color = colors[index % colors.size],
                                        startAngle = startAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .padding(start = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            expenseGroups.entries.forEachIndexed { index, entry ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(colors[index % colors.size], CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${entry.key}: ${currencyFormat.format(entry.value)} (${String.format("%.1f", entry.value / totalExp * 100)}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardsView(
    filteredBills: List<Bill>,
    currencyFormat: NumberFormat
) {
    val topCustomers = remember(filteredBills) {
        filteredBills.filter { it.partyType == "TRADER" }
            .groupBy { it.partyName }
            .mapValues { it.value.sumOf { b -> b.netAmount } }
            .entries.sortedByDescending { it.value }
            .take(5)
    }

    val topProducts = remember(filteredBills) {
        filteredBills.groupBy { it.commodityName }
            .map { entry ->
                val totalValue = entry.value.sumOf { it.netAmount }
                val totalBags = entry.value.sumOf { it.bagCount }
                Triple(entry.key, totalValue, totalBags)
            }
            .sortedByDescending { it.second }
            .take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP BUYERS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Leaderboard, contentDescription = null, tint = Color(0xFFFFB300))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Top 5 Customers (Buying Traders)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                if (topCustomers.isEmpty()) {
                    Text("No trader transactions recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    topCustomers.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${index + 1}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                Text(entry.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(currencyFormat.format(entry.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }

        // TOP COMMODITIES CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Top 5 Commodities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                if (topProducts.isEmpty()) {
                    Text("No sales commodities logged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    topProducts.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${index + 1}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                    Text(item.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Text("${item.third} bags sold", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 28.dp))
                            }
                            Text(currencyFormat.format(item.second), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (Double, String, String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Labor") }
    var description by remember { mutableStateOf("") }

    val categories = listOf("Labor", "Transport", "Rent", "Office Supplies", "Mandi Levy", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Shop Operational Outlay") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Column {
                    Text("Category Selector", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) },
                                modifier = Modifier.testTag("expense_chip_$cat")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Memo / Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_desc_input"),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSave(amt, category, description)
                    }
                },
                modifier = Modifier.testTag("save_expense_button")
            ) {
                Text("Record Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------- EXPORT LOGIC (PDF, Excel, CSV) ----------------

private fun triggerPdfExport(context: Context, bills: List<Bill>, expenses: List<Expense>, timeFilter: String, shopConfig: ShopConfig) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    val totalTraderSales = bills.filter { it.partyType == "TRADER" }.sumOf { it.netAmount }
    val totalFarmerPurchases = bills.filter { it.partyType == "FARMER" }.sumOf { it.netAmount }
    val commissions = bills.sumOf { it.commissionAmount }
    val labor = bills.sumOf { it.laborCharges }
    val exp = expenses.sumOf { it.amount }

    val tradingProfit = totalTraderSales - totalFarmerPurchases - exp

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body { font-family: Arial, sans-serif; padding: 25px; color: #333; line-height: 1.5; }
                .header { text-align: center; border-bottom: 2px solid #2E7D32; padding-bottom: 15px; margin-bottom: 25px; }
                .shop-name { font-size: 24px; font-weight: bold; color: #1B5E20; }
                .report-title { font-size: 18px; margin-top: 5px; font-style: italic; color: #555; }
                .section-title { font-size: 16px; font-weight: bold; background: #E8F5E9; padding: 6px 10px; margin-top: 25px; border-left: 5px solid #2E7D32; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; }
                .total-row { font-weight: bold; background: #fafafa; }
                .text-right { text-align: right; }
                .green { color: #2E7D32; font-weight: bold; }
                .red { color: #C62828; font-weight: bold; }
                .footer { margin-top: 40px; text-align: center; font-size: 12px; color: #777; border-top: 1px solid #ddd; padding-top: 15px; }
            </style>
        </head>
        <body>
            <div class="header">
                <div class="shop-name">${shopConfig.shopName}</div>
                <div>${shopConfig.address}</div>
                <div class="report-title">${timeFilter} Business Performance & Profitability Statement</div>
                <div>Generated: ${dateFormat.format(Date())}</div>
            </div>

            <div class="section-title">Wholesale Trading Financials</div>
            <table>
                <tr>
                    <th>Particulars</th>
                    <th class="text-right">Amount</th>
                </tr>
                <tr>
                    <td>Total Trading Sales (Outward)</td>
                    <td class="text-right">${currencyFormat.format(totalTraderSales)}</td>
                </tr>
                <tr>
                    <td>Total Produce Procurement (Inward)</td>
                    <td class="text-right red">-${currencyFormat.format(totalFarmerPurchases)}</td>
                </tr>
                <tr>
                    <td>Operational Outlays (Expenses)</td>
                    <td class="text-right red">-${currencyFormat.format(exp)}</td>
                </tr>
                <tr class="total-row">
                    <td>Net Trading Profit / Loss</td>
                    <td class="text-right ${if (tradingProfit >= 0) "green" else "red"}">${currencyFormat.format(tradingProfit)}</td>
                </tr>
            </table>

            <div class="section-title">Operational Surcharges & Commission Book</div>
            <table>
                <tr>
                    <th>Income Stream</th>
                    <th class="text-right">Amount</th>
                </tr>
                <tr>
                    <td>Total Brokerage Commissions Earned (Arhat)</td>
                    <td class="text-right green">${currencyFormat.format(commissions)}</td>
                </tr>
                <tr>
                    <td>Mandi Labor Fees Collected (Hamali)</td>
                    <td class="text-right">${currencyFormat.format(labor)}</td>
                </tr>
                <tr>
                    <td>Total Direct Operational Expenses</td>
                    <td class="text-right red">-${currencyFormat.format(exp)}</td>
                </tr>
            </table>

            <div class="section-title">Direct Shop Expenses Log</div>
            <table>
                <thead>
                    <tr>
                        <th>Category</th>
                        <th>Description</th>
                        <th class="text-right">Amount</th>
                    </tr>
                </thead>
                <tbody>
                    ${expenses.joinToString("") { e ->
                        """
                        <tr>
                            <td>${e.category}</td>
                            <td>${e.description}</td>
                            <td class="text-right red">-${currencyFormat.format(e.amount)}</td>
                        </tr>
                        """
                    }}
                    <tr class="total-row">
                        <td colspan="2">Total Direct Expenses Outlay</td>
                        <td class="text-right red">-${currencyFormat.format(exp)}</td>
                    </tr>
                </tbody>
            </table>

            <div class="footer">
                Thank you for using APMC Digital Mandi Accounts Suite. Prepared via Authorized Secure Ledger.
            </div>
        </body>
        </html>
    """.trimIndent()

    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val jobName = "${timeFilter}_Business_Report"

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private fun triggerCsvExport(context: Context, bills: List<Bill>, expenses: List<Expense>, timeFilter: String) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val csv = StringBuilder()
    csv.append("APMC Digital Mandi Accounts CSV Export\n")
    csv.append("Time Horizon,${timeFilter}\n")
    csv.append("Export Date,${dateFormat.format(Date())}\n\n")

    csv.append("--- INVOICE SALES AND PURCHASES ---\n")
    csv.append("Bill Number,Date,Party Name,Party Type,Commodity,Bags,Weight,Rate,Gross,Tax,Commission,Labor,Net,Paid Status\n")
    bills.forEach { b ->
        csv.append("${b.billNumber},${dateFormat.format(Date(b.date))},\"${b.partyName}\",${b.partyType},\"${b.commodityName}\",${b.bagCount},${b.weight},${b.rate},${b.grossAmount},${b.taxAmount},${b.commissionAmount},${b.laborCharges},${b.netAmount},${b.isPaid}\n")
    }

    csv.append("\n--- SHOP OPERATIONAL EXPENSES ---\n")
    csv.append("Expense ID,Date,Category,Description,Amount\n")
    expenses.forEach { e ->
        csv.append("${e.id},${dateFormat.format(Date(e.date))},\"${e.category}\",\"${e.description}\",${e.amount}\n")
    }

    shareDataFile(context, csv.toString(), "APMC_${timeFilter}_Mandi_Ledger.csv", "text/csv")
}

private fun triggerExcelExport(context: Context, bills: List<Bill>, expenses: List<Expense>, timeFilter: String) {
    // Standard Excel applications read UTF-8 CSVs perfectly. Let's provide a highly structured, Excel-compatible tab/comma separated file!
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val xls = StringBuilder()
    xls.append("APMC Digital Mandi Accounts Statement Export\n")
    xls.append("Time Horizon,${timeFilter}\n")
    xls.append("Export Date,${dateFormat.format(Date())}\n\n")

    xls.append("--- TRADING SUMMARY ---\n")
    val totalTraderSales = bills.filter { it.partyType == "TRADER" }.sumOf { it.netAmount }
    val totalFarmerPurchases = bills.filter { it.partyType == "FARMER" }.sumOf { it.netAmount }
    val totalExp = expenses.sumOf { it.amount }
    xls.append("Metric,Amount\n")
    xls.append("Total Outward Trader Sales,${totalTraderSales}\n")
    xls.append("Total Inward Farmer Purchases,${totalFarmerPurchases}\n")
    xls.append("Direct Shop Operational Expenses,${totalExp}\n")
    xls.append("Calculated Wholesale Net Profit,${totalTraderSales - totalFarmerPurchases - totalExp}\n\n")

    xls.append("--- DETAILED BILLING TRANSACTIONS ---\n")
    xls.append("Invoice No,Date,Party,Type,Commodity,Bags,Weight,Rate,Gross,Tax,Commission,Labor,Net Amount,Is Paid\n")
    bills.forEach { b ->
        xls.append("${b.billNumber},${dateFormat.format(Date(b.date))},\"${b.partyName}\",${b.partyType},\"${b.commodityName}\",${b.bagCount},${b.weight},${b.rate},${b.grossAmount},${b.taxAmount},${b.commissionAmount},${b.laborCharges},${b.netAmount},${b.isPaid}\n")
    }

    xls.append("\n--- DETAILED OPERATIONAL EXPENSES ---\n")
    xls.append("ID,Date,Category,Description,Amount\n")
    expenses.forEach { e ->
        xls.append("${e.id},${dateFormat.format(Date(e.date))},\"${e.category}\",\"${e.description}\",${e.amount}\n")
    }

    shareDataFile(context, xls.toString(), "APMC_${timeFilter}_Accounts_Statement.csv", "text/csv")
}

private fun shareDataFile(context: Context, data: String, fileName: String, mimeType: String) {
    try {
        val cachePath = File(context.cacheDir, "exports")
        cachePath.mkdirs()
        val file = File(cachePath, fileName)
        val stream = FileOutputStream(file)
        stream.write(data.toByteArray())
        stream.close()

        val contentUri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, mimeType)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "APMC Mandi Ledger Report Export")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Statement File"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
