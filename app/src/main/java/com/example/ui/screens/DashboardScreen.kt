package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: BillingViewModel,
    onNavigateToBilling: () -> Unit,
    onNavigateToParties: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToPayments: () -> Unit = {}
) {
    // Collect Reactive Flows from ViewModel
    val todaySales by viewModel.todaySales.collectAsState()
    val todayBillsCount by viewModel.todayBillsCount.collectAsState()
    val pendingPaymentsCount by viewModel.pendingPaymentsCount.collectAsState()
    val pendingPaymentsAmount by viewModel.pendingPaymentsAmount.collectAsState()
    val monthlyRevenue by viewModel.monthlyRevenue.collectAsState()
    val totalReceivables by viewModel.totalReceivables.collectAsState()
    val lowStockCount by viewModel.lowStockCount.collectAsState()
    
    val bills by viewModel.bills.collectAsState()
    val topProducts by viewModel.topProducts.collectAsState()
    val weeklySalesData by viewModel.weeklySalesData.collectAsState()
    val monthlySalesData by viewModel.monthlySalesData.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    val commodities by viewModel.commodities.collectAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("dashboard_root"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Dashboard Header Title Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Market Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "APMC Mandi Trading Dashboard • Real-time Insights",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Seed Button if no bills exist to help get started
            if (bills.isEmpty()) {
                IconButton(
                    onClick = { viewModel.seedDemoDataIfNeeded() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("seed_demo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Seed Demo Data",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (isTablet) {
            // TABLET RESPONSIVE 2-PANE GRID SYSTEM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // LEFT PANE (Metrics and Visual Interactive Charts)
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TabletMetricsGrid(
                        todaySales = todaySales,
                        todayBillsCount = todayBillsCount,
                        pendingPaymentsCount = pendingPaymentsCount,
                        pendingPaymentsAmount = pendingPaymentsAmount,
                        monthlyRevenue = monthlyRevenue,
                        totalReceivables = totalReceivables,
                        lowStockCount = lowStockCount,
                        currencyFormat = currencyFormat,
                        onNavigateToBilling = onNavigateToBilling,
                        onNavigateToParties = onNavigateToParties,
                        onNavigateToStocks = onNavigateToStocks,
                        onNavigateToPayments = onNavigateToPayments
                    )

                    InteractiveChartsCard(
                        weeklySalesData = weeklySalesData,
                        monthlySalesData = monthlySalesData,
                        currencyFormat = currencyFormat
                    )
                }

                // RIGHT PANE (Quick Actions, Top Commodities, Activities)
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    QuickActionsSection(
                        onNavigateToBilling = onNavigateToBilling,
                        onNavigateToParties = onNavigateToParties,
                        onNavigateToStocks = onNavigateToStocks,
                        onNavigateToPayments = onNavigateToPayments,
                        onNavigateToReports = { viewModel.navigateTo("reports") }
                    )

                    TopProductsSection(
                        topProducts = topProducts,
                        currencyFormat = currencyFormat
                    )

                    RecentActivitySection(
                        activities = recentActivity,
                        currencyFormat = currencyFormat,
                        dateFormat = dateFormat
                    )
                }
            }

            // Recent Invoices Full Width at Bottom on Tablet
            RecentInvoicesSection(
                bills = bills,
                viewModel = viewModel,
                currencyFormat = currencyFormat,
                dateFormat = dateFormat
            )
        } else {
            // MOBILE RESPONSIVE VERTICAL COLUMN LAYOUT
            MobileMetricsGrid(
                todaySales = todaySales,
                todayBillsCount = todayBillsCount,
                pendingPaymentsCount = pendingPaymentsCount,
                pendingPaymentsAmount = pendingPaymentsAmount,
                monthlyRevenue = monthlyRevenue,
                totalReceivables = totalReceivables,
                lowStockCount = lowStockCount,
                currencyFormat = currencyFormat,
                onNavigateToBilling = onNavigateToBilling,
                onNavigateToParties = onNavigateToParties,
                onNavigateToStocks = onNavigateToStocks,
                onNavigateToPayments = onNavigateToPayments
            )

            QuickActionsSection(
                onNavigateToBilling = onNavigateToBilling,
                onNavigateToParties = onNavigateToParties,
                onNavigateToStocks = onNavigateToStocks,
                onNavigateToPayments = onNavigateToPayments,
                onNavigateToReports = { viewModel.navigateTo("reports") }
            )

            InteractiveChartsCard(
                weeklySalesData = weeklySalesData,
                monthlySalesData = monthlySalesData,
                currencyFormat = currencyFormat
            )

            TopProductsSection(
                topProducts = topProducts,
                currencyFormat = currencyFormat
            )

            RecentActivitySection(
                activities = recentActivity,
                currencyFormat = currencyFormat,
                dateFormat = dateFormat
            )

            RecentInvoicesSection(
                bills = bills,
                viewModel = viewModel,
                currencyFormat = currencyFormat,
                dateFormat = dateFormat
            )
        }
    }
}

// Tablet Responsive 3-Column Metrics Grid
@Composable
fun TabletMetricsGrid(
    todaySales: Double,
    todayBillsCount: Int,
    pendingPaymentsCount: Int,
    pendingPaymentsAmount: Double,
    monthlyRevenue: Double,
    totalReceivables: Double,
    lowStockCount: Int,
    currencyFormat: NumberFormat,
    onNavigateToBilling: () -> Unit,
    onNavigateToParties: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToPayments: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Today's Sales",
                    value = currencyFormat.format(todaySales),
                    subtitle = "Sales revenue today",
                    icon = Icons.Default.TrendingUp,
                    colorAccent = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    testTag = "metric_today_sales",
                    onClick = onNavigateToBilling
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Today's Bills",
                    value = "$todayBillsCount Tickets",
                    subtitle = "Mandi trades printed",
                    icon = Icons.Default.ReceiptLong,
                    colorAccent = MaterialTheme.colorScheme.secondary,
                    bgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                    testTag = "metric_today_bills",
                    onClick = onNavigateToBilling
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Pending Payments",
                    value = currencyFormat.format(pendingPaymentsAmount),
                    subtitle = "$pendingPaymentsCount Unpaid bills",
                    icon = Icons.Default.HourglassBottom,
                    colorAccent = MaterialTheme.colorScheme.error,
                    bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    testTag = "metric_pending_payments",
                    onClick = onNavigateToParties
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Monthly Revenue",
                    value = currencyFormat.format(monthlyRevenue),
                    subtitle = "Current calendar month",
                    icon = Icons.Default.MonetizationOn,
                    colorAccent = Color(0xFF00897B),
                    bgColor = Color(0xFFE0F2F1),
                    testTag = "metric_monthly_revenue",
                    onClick = onNavigateToBilling
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Outstanding Amount",
                    value = currencyFormat.format(totalReceivables),
                    subtitle = "Receivables from traders",
                    icon = Icons.Default.AccountBalance,
                    colorAccent = MaterialTheme.colorScheme.tertiary,
                    bgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                    testTag = "metric_outstanding_amount",
                    onClick = onNavigateToPayments
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Low Stock Alert",
                    value = if (lowStockCount > 0) "$lowStockCount Items" else "All Stable",
                    subtitle = "Crops under 50 bags",
                    icon = Icons.Default.Warning,
                    colorAccent = Color(0xFFE65100),
                    bgColor = Color(0xFFFFE0B2),
                    testTag = "metric_low_stock",
                    onClick = onNavigateToStocks
                )
            }
        }
    }
}

// Mobile Responsive 2-Column Metrics Grid
@Composable
fun MobileMetricsGrid(
    todaySales: Double,
    todayBillsCount: Int,
    pendingPaymentsCount: Int,
    pendingPaymentsAmount: Double,
    monthlyRevenue: Double,
    totalReceivables: Double,
    lowStockCount: Int,
    currencyFormat: NumberFormat,
    onNavigateToBilling: () -> Unit,
    onNavigateToParties: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToPayments: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Today's Sales",
                    value = currencyFormat.format(todaySales),
                    subtitle = "Today's volume",
                    icon = Icons.Default.TrendingUp,
                    colorAccent = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    testTag = "metric_today_sales",
                    onClick = onNavigateToBilling
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Today's Bills",
                    value = "$todayBillsCount Tickets",
                    subtitle = "Printed",
                    icon = Icons.Default.ReceiptLong,
                    colorAccent = MaterialTheme.colorScheme.secondary,
                    bgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                    testTag = "metric_today_bills",
                    onClick = onNavigateToBilling
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Pending Payments",
                    value = currencyFormat.format(pendingPaymentsAmount),
                    subtitle = "$pendingPaymentsCount Unpaid",
                    icon = Icons.Default.HourglassBottom,
                    colorAccent = MaterialTheme.colorScheme.error,
                    bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    testTag = "metric_pending_payments",
                    onClick = onNavigateToParties
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Monthly Revenue",
                    value = currencyFormat.format(monthlyRevenue),
                    subtitle = "This month",
                    icon = Icons.Default.MonetizationOn,
                    colorAccent = Color(0xFF00897B),
                    bgColor = Color(0xFFE0F2F1),
                    testTag = "metric_monthly_revenue",
                    onClick = onNavigateToBilling
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Outstanding",
                    value = currencyFormat.format(totalReceivables),
                    subtitle = "Receivables",
                    icon = Icons.Default.AccountBalance,
                    colorAccent = MaterialTheme.colorScheme.tertiary,
                    bgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
                    testTag = "metric_outstanding_amount",
                    onClick = onNavigateToPayments
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PremiumMetricCard(
                    title = "Low Stock",
                    value = if (lowStockCount > 0) "$lowStockCount Items" else "Stable",
                    subtitle = "Under 50 bags",
                    icon = Icons.Default.Warning,
                    colorAccent = Color(0xFFE65100),
                    bgColor = Color(0xFFFFE0B2),
                    testTag = "metric_low_stock",
                    onClick = onNavigateToStocks
                )
            }
        }
    }
}

// Reusable Metric Card
@Composable
fun PremiumMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    colorAccent: Color,
    bgColor: Color,
    testTag: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Left boundary colored bar for luxury indicator
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(colorAccent)
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = colorAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Interactive Sales Charts Composable
@Composable
fun InteractiveChartsCard(
    weeklySalesData: List<Pair<String, Double>>,
    monthlySalesData: List<Pair<String, Double>>,
    currencyFormat: NumberFormat
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Weekly, 1 = Monthly

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("charts_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Switch Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sales Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Elegant Custom Toggle Buttons
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Weekly",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Monthly",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val currentData = if (selectedTab == 0) weeklySalesData else monthlySalesData
            val titleText = if (selectedTab == 0) "Weekly Mandi Invoiced Turnover" else "6-Month Revenue Distribution"

            if (currentData.isEmpty() || currentData.all { it.second == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Chart placeholder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No trades completed to visualize statistics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val maxValue = remember(currentData) { (currentData.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0) }
                val primaryColor = MaterialTheme.colorScheme.primary
                
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    
                    val bottomPadding = 30.dp.toPx()
                    val topPadding = 20.dp.toPx()
                    val chartHeight = height - bottomPadding - topPadding
                    
                    // Draw horizontal faint gridlines
                    val gridlineCount = 4
                    for (i in 0 until gridlineCount) {
                        val y = topPadding + (chartHeight * i / (gridlineCount - 1))
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    val stepX = width / currentData.size
                    
                    if (selectedTab == 1) {
                        // MONTHLY -> Bezier Area Chart
                        val points = currentData.mapIndexed { index, pair ->
                            val x = stepX * index + stepX / 2f
                            val y = topPadding + chartHeight - (pair.second.toFloat() / maxValue.toFloat() * chartHeight)
                            Offset(x, y)
                        }
                        
                        val path = Path().apply {
                            if (points.isNotEmpty()) {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val prev = points[i - 1]
                                    val curr = points[i]
                                    cubicTo(
                                        (prev.x + curr.x) / 2f, prev.y,
                                        (prev.x + curr.x) / 2f, curr.y,
                                        curr.x, curr.y
                                    )
                                }
                            }
                        }
                        
                        // Draw Area Gradient shading
                        val areaPath = Path().apply {
                            if (points.isNotEmpty()) {
                                moveTo(points[0].x, topPadding + chartHeight)
                                lineTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val prev = points[i - 1]
                                    val curr = points[i]
                                    cubicTo(
                                        (prev.x + curr.x) / 2f, prev.y,
                                        (prev.x + curr.x) / 2f, curr.y,
                                        curr.x, curr.y
                                    )
                                }
                                lineTo(points.last().x, topPadding + chartHeight)
                                close()
                            }
                        }
                        
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF00897B).copy(alpha = 0.3f), Color.Transparent),
                                startY = topPadding,
                                endY = topPadding + chartHeight
                            )
                        )
                        
                        // Line stroke
                        drawPath(
                            path = path,
                            color = Color(0xFF00897B),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        
                        // Anchor nodes
                        points.forEach { offset ->
                            drawCircle(
                                color = Color.White,
                                radius = 5.dp.toPx(),
                                center = offset
                            )
                            drawCircle(
                                color = Color(0xFF00897B),
                                radius = 3.dp.toPx(),
                                center = offset
                            )
                        }
                    } else {
                        // WEEKLY -> Rounded Gradient Column/Bar Chart
                        val barWidthFraction = 0.5f
                        currentData.forEachIndexed { index, pair ->
                            val x = stepX * index + (stepX * (1f - barWidthFraction) / 2f)
                            val barHeight = (pair.second / maxValue) * chartHeight
                            val y = topPadding + chartHeight - barHeight
                            val barWidth = stepX * barWidthFraction
                            
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor,
                                        primaryColor.copy(alpha = 0.5f)
                                    )
                                ),
                                topLeft = Offset(x, y.toFloat()),
                                size = Size(barWidth, barHeight.toFloat()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    }
                }
                
                // Labels below chart
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    currentData.forEach { pair ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pair.first,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// Quick Actions Block
@Composable
fun QuickActionsSection(
    onNavigateToBilling: () -> Unit,
    onNavigateToParties: () -> Unit,
    onNavigateToStocks: () -> Unit,
    onNavigateToPayments: () -> Unit = {},
    onNavigateToReports: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_actions_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Operational Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 3-Row Grid of Actions
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButtonTile(
                        title = "Farmer Patti",
                        subtitle = "Disburse Ticket",
                        icon = Icons.Default.Agriculture,
                        color = Color(0xFF2E7D32),
                        bgColor = Color(0xFFE8F5E9),
                        modifier = Modifier.weight(1f),
                        testTag = "add_patti_action",
                        onClick = onNavigateToBilling
                    )
                    ActionButtonTile(
                        title = "Trader Invoice",
                        subtitle = "B2B Sales Billing",
                        icon = Icons.Default.ShoppingCart,
                        color = Color(0xFF1565C0),
                        bgColor = Color(0xFFE3F2FD),
                        modifier = Modifier.weight(1f),
                        testTag = "create_sale_action",
                        onClick = onNavigateToBilling
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButtonTile(
                        title = "Receipts & Payouts",
                        subtitle = "Settle Ledger",
                        icon = Icons.Default.Payment,
                        color = Color(0xFF00897B),
                        bgColor = Color(0xFFE0F2F1),
                        modifier = Modifier.weight(1f),
                        testTag = "payments_book_action",
                        onClick = onNavigateToPayments
                    )
                    ActionButtonTile(
                        title = "Mandi Directory",
                        subtitle = "Manage Partners",
                        icon = Icons.Default.PersonAdd,
                        color = Color(0xFF6A1B9A),
                        bgColor = Color(0xFFF3E5F5),
                        modifier = Modifier.weight(1f),
                        testTag = "manage_parties_action",
                        onClick = onNavigateToParties
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButtonTile(
                        title = "Live Inventory",
                        subtitle = "Stock Rates",
                        icon = Icons.Default.Inventory,
                        color = Color(0xFFEF6C00),
                        bgColor = Color(0xFFFFF3E0),
                        modifier = Modifier.weight(1f),
                        testTag = "view_stocks_action",
                        onClick = onNavigateToStocks
                    )
                    ActionButtonTile(
                        title = "Business Reports",
                        subtitle = "Analytics & P&L",
                        icon = Icons.Default.BarChart,
                        color = Color(0xFFC62828),
                        bgColor = Color(0xFFFFEBEE),
                        modifier = Modifier.weight(1f),
                        testTag = "view_reports_action",
                        onClick = onNavigateToReports
                    )
                }
            }
        }
    }
}

// Quick action tile item
@Composable
fun ActionButtonTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Top Products / Commodities contribution section
@Composable
fun TopProductsSection(
    topProducts: List<Pair<String, Double>>,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_products_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top Traded Products",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Sales distribution by crop catalog",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (topProducts.isEmpty()) {
                Text(
                    text = "No product statistics available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val highestVolume = topProducts.firstOrNull()?.second ?: 1.0
                
                topProducts.forEach { (commodityName, netTurnover) ->
                    val progress = if (highestVolume > 0.0) (netTurnover / highestVolume).toFloat() else 0.0f
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = commodityName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currencyFormat.format(netTurnover),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Recent Activity Timeline Section
@Composable
fun RecentActivitySection(
    activities: List<DashboardActivity>,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_activity_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recent Activity Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (activities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent transactions found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column {
                    activities.take(5).forEachIndexed { index, activity ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Timeline visual line and bullet connector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (activity.isPositive) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                        )
                                )
                                if (index < activities.take(5).size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(48.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Activity description block
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activity.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currencyFormat.format(activity.amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activity.isPositive) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                }
                                Text(
                                    text = activity.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateFormat.format(Date(activity.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Recent Invoices section
@Composable
fun RecentInvoicesSection(
    bills: List<Bill>,
    viewModel: BillingViewModel,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Recent Mandi Bills",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (bills.isEmpty()) {
            EmptyStatePlaceholder(
                title = "No Invoices Logged",
                subtitle = "Generate your first farmer patti or trader invoice to begin recording live data.",
                icon = Icons.Default.ReceiptLong
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bills.take(5).forEach { bill ->
                    BillItemRow(
                        bill = bill,
                        currencyFormat = currencyFormat,
                        dateFormat = dateFormat,
                        onDelete = { viewModel.deleteBill(bill.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun BillItemRow(
    bill: Bill,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bill_item_card_${bill.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicator icon for Farmer Patti vs Trader Sale
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (bill.partyType == "FARMER") Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (bill.partyType == "FARMER") Icons.Default.Agriculture else Icons.Default.ShoppingCart,
                    contentDescription = bill.partyType,
                    tint = if (bill.partyType == "FARMER") Color(0xFF2E7D32) else Color(0xFF1565C0)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bill.billNumber.ifEmpty { "Draft" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (bill.partyType == "FARMER") Color(0xFFE2F9EE) else Color(0xFFE5EFFF)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (bill.partyType == "FARMER") "PATTI" else "SALE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (bill.partyType == "FARMER") Color(0xFF006B44) else Color(0xFF1E5EC1)
                        )
                    }
                }
                Text(
                    text = bill.partyName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${bill.bagCount} bags • ${bill.weight} Qtl • ${bill.commodityName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Price & Delete Actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currencyFormat.format(bill.netAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = if (bill.partyType == "FARMER") Color(0xFF2E7D32) else Color(0xFF1565C0)
                )
                Text(
                    text = dateFormat.format(Date(bill.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(28.dp).testTag("delete_bill_button_${bill.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Void and Delete Bill",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Void & Delete Bill?") },
            text = { Text("Are you sure you want to void bill ${bill.billNumber}? This will automatically reverse stock updates and ledger balances for ${bill.partyName}. This process is permanent.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Bill")
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
