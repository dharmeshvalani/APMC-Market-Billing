package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Responsive navigation shell for the app (Sidebar for Tablets/Widescreens, Bottom Bar for Mobile)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppResponsiveShell(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    currentUser: com.example.data.UserSession?,
    onLogout: () -> Unit,
    isOnline: Boolean = true,
    isSyncing: Boolean = false,
    pendingSyncCount: Int = 0,
    content: @Composable (PaddingValues) -> Unit
) {
    if (currentScreen == "auth") {
        Box(modifier = Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    Row(modifier = Modifier.fillMaxSize()) {
        if (isTablet) {
            // Sidebar Navigation for larger screens
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Logo / Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "APMC Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "APMC Billing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Market Yard Ledger",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Nav links - dynamically filter depending on User Role
                    val menuItems: List<Triple<String, String, ImageVector>> = remember(currentUser) {
                        val items = mutableListOf<Triple<String, String, ImageVector>>(
                            Triple("dashboard", "Dashboard", Icons.Default.Dashboard),
                            Triple("new_bill", "Create Invoice", Icons.Default.AddShoppingCart),
                            Triple("invoice_history", "Invoices", Icons.Default.Receipt)
                        )
                        if (currentUser?.role != com.example.data.UserRole.STAFF) {
                            items.add(Triple("parties", "Farmers", Icons.Default.Person))
                            items.add(Triple("customers", "Customers", Icons.Default.People))
                        }
                        items.add(Triple("commodities", "Commodities", Icons.Default.Category))
                        items.add(Triple("inventory", "Inventory", Icons.Default.Inventory))
                        if (currentUser?.role != com.example.data.UserRole.STAFF) {
                            items.add(Triple("payments", "Payments & Receipts", Icons.Default.Payment))
                            items.add(Triple("ledger", "Accounts Ledger", Icons.Default.Book))
                            items.add(Triple("reports", "Business Reports", Icons.Default.BarChart))
                        }
                        if (currentUser?.role == com.example.data.UserRole.OWNER || currentUser?.role == com.example.data.UserRole.ADMIN) {
                            items.add(Triple("settings", "Shop Settings", Icons.Default.Settings))
                        }
                        items
                    }

                    menuItems.forEach { (route, label, icon) ->
                        val isSelected = currentScreen == route
                        NavigationSidebarItem(
                            label = label,
                            icon = icon,
                            isSelected = isSelected,
                            onClick = { onNavigate(route) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Profile & Logout Panel
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (currentUser?.role) {
                                            com.example.data.UserRole.OWNER -> MaterialTheme.colorScheme.primaryContainer
                                            com.example.data.UserRole.ADMIN -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.tertiaryContainer
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (currentUser?.name?.take(1) ?: "U").uppercase(),
                                    color = when (currentUser?.role) {
                                        com.example.data.UserRole.OWNER -> MaterialTheme.colorScheme.onPrimaryContainer
                                        com.example.data.UserRole.ADMIN -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUser?.name ?: "Guest User",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUser?.role?.name ?: "STAFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (currentUser?.role) {
                                        com.example.data.UserRole.OWNER -> MaterialTheme.colorScheme.primary
                                        com.example.data.UserRole.ADMIN -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onToggleTheme) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme"
                                )
                            }
                            TextButton(
                                onClick = onLogout,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Logout",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Logout", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            VerticalDivider()
        }

        // Main Screen Content Area
        Scaffold(
            topBar = {
                // Header (with title and mobile menu toggle)
                HeaderBar(
                    title = when (currentScreen) {
                        "dashboard" -> "Market Overview"
                        "new_bill" -> "APMC Fresh Billing Calculator"
                        "parties" -> "Farmer Directory"
                        "customers" -> "Customer Ledger & Accounts"
                        "commodities" -> "Commodity Pricing & Stock"
                        "inventory" -> "Stock Flow Ledger & Reports"
                        "ledger" -> "Consolidated Ledgers"
                        "payments" -> "Payments & Collections"
                        "reports" -> "Business Performance Reports"
                        else -> "APMC Billing"
                    },
                    isTablet = isTablet,
                    isDarkMode = isDarkMode,
                    onToggleTheme = onToggleTheme,
                    currentUser = currentUser,
                    onLogout = onLogout,
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    pendingSyncCount = pendingSyncCount,
                    onStatusClick = { onNavigate("settings") }
                )
            },
            bottomBar = {
                if (!isTablet) {
                    var showMoreSheet by remember { mutableStateOf(false) }

                    if (showMoreSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showMoreSheet = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = MaterialTheme.colorScheme.surface,
                            dragHandle = { BottomSheetDefaults.DragHandle() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(bottom = 24.dp)
                                    .padding(horizontal = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "More Market Services",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                val extraItems = remember(currentUser) {
                                    val items = mutableListOf<Triple<String, String, ImageVector>>(
                                        Triple("invoice_history", "Invoices & Bills", Icons.Default.Receipt)
                                    )
                                    if (currentUser?.role != com.example.data.UserRole.STAFF) {
                                        items.add(Triple("parties", "Farmers Directory", Icons.Default.Person))
                                        items.add(Triple("payments", "Payments & Collections", Icons.Default.Payment))
                                        items.add(Triple("ledger", "Accounts Ledger", Icons.Default.Book))
                                    }
                                    if (currentUser?.role == com.example.data.UserRole.OWNER || currentUser?.role == com.example.data.UserRole.ADMIN) {
                                        items.add(Triple("settings", "Shop Settings", Icons.Default.Settings))
                                    }
                                    items
                                }
                                
                                extraItems.forEach { (route, label, icon) ->
                                    val isSelected = currentScreen == route
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        onClick = {
                                            onNavigate(route)
                                            showMoreSheet = false
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Navigate",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Mobile custom floating navigation bar at the bottom (Apple Stocks / Wallet style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 12.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val menuItems = remember(currentUser) {
                                    val items = mutableListOf<Triple<String, String, ImageVector>>(
                                        Triple("dashboard", "Home", Icons.Default.Dashboard),
                                        Triple("new_bill", "Billing", Icons.Default.AddShoppingCart),
                                        Triple("customers", "Customers", Icons.Default.People),
                                        Triple("inventory", "Inventory", Icons.Default.Inventory)
                                    )
                                    if (currentUser?.role != com.example.data.UserRole.STAFF) {
                                        items.add(Triple("reports", "Reports", Icons.Default.BarChart))
                                    }
                                    items
                                }

                                menuItems.forEach { (route, label, icon) ->
                                    val isSelected = currentScreen == route
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onNavigate(route) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // More Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showMoreSheet = true }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreHoriz,
                                            contentDescription = "More",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = "More",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content(innerPadding)
            }
        }
    }
}

@Composable
fun NavigationSidebarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderBar(
    title: String,
    isTablet: Boolean,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    currentUser: com.example.data.UserSession? = null,
    onLogout: (() -> Unit)? = null,
    isOnline: Boolean = true,
    isSyncing: Boolean = false,
    pendingSyncCount: Int = 0,
    onStatusClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (!isTablet) {
                    val subtitle = if (currentUser != null) {
                        "Role: ${currentUser.role.name} (${currentUser.name})"
                    } else {
                        "APMC Market Billing"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            // Live Synchronization Status Indicator
            IconButton(
                onClick = onStatusClick,
                modifier = Modifier.testTag("header_sync_status")
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = when {
                            !isOnline -> Icons.Default.WifiOff
                            isSyncing -> Icons.Default.Sync
                            pendingSyncCount > 0 -> Icons.Default.CloudQueue
                            else -> Icons.Default.CloudDone
                        },
                        contentDescription = "Sync Status Indicator",
                        tint = when {
                            !isOnline -> MaterialTheme.colorScheme.error
                            isSyncing -> MaterialTheme.colorScheme.primary
                            pendingSyncCount > 0 -> MaterialTheme.colorScheme.tertiary
                            else -> Color(0xFF06D6A0) // Synced green
                        }
                    )
                    if (pendingSyncCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = pendingSyncCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (!isTablet) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme"
                    )
                }
                if (currentUser != null && onLogout != null) {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        )
    )
}

// Custom Toast notification message overlay
@Composable
fun ToastNotification(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.animateContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

// Empty states placeholder
@Composable
fun EmptyStatePlaceholder(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.FolderOpen,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Empty icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onActionClick) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

// Loading Spinner Composable
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Processing accounts...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Error Page Placeholder
@Composable
fun ErrorPagePlaceholder(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 450.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = "Retry Action")
                }
            }
        }
    }
}

@Composable
fun ConflictResolutionDialog(
    localEntity: Any?,
    remoteEntity: Any?,
    onResolve: (useLocal: Boolean) -> Unit
) {
    if (localEntity == null || remoteEntity == null) return

    AlertDialog(
        onDismissRequest = { /* Force explicit decision */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SyncProblem,
                    contentDescription = "Conflict Detected",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cloud Sync Conflict",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "The local version of this record differs from the version stored in the cloud. Please compare and choose which database record to preserve.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (localEntity is com.example.data.Party && remoteEntity is com.example.data.Party) {
                    Text(
                        text = "Partner: ${localEntity.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Local Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Local (Device)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Phone: ${localEntity.phone}", style = MaterialTheme.typography.bodySmall)
                                Text("Addr: ${localEntity.address}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Lic: ${localEntity.licenseNo ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                Text("Bal: ₹${localEntity.balance}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Remote Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Remote (Cloud)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Phone: ${remoteEntity.phone}", style = MaterialTheme.typography.bodySmall)
                                Text("Addr: ${remoteEntity.address}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Lic: ${remoteEntity.licenseNo ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                Text("Bal: ₹${remoteEntity.balance}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (localEntity is com.example.data.Commodity && remoteEntity is com.example.data.Commodity) {
                    Text(
                        text = "Crop: ${localEntity.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Local Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Local (Device)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Stock: ${localEntity.stockBags} bags", style = MaterialTheme.typography.bodySmall)
                                Text("Rate: ₹${localEntity.currentRate}", style = MaterialTheme.typography.bodySmall)
                                Text("Category: ${localEntity.category}", style = MaterialTheme.typography.bodySmall)
                                Text("GST: ${localEntity.gstPercent}%", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        // Remote Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Remote (Cloud)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text("Stock: ${remoteEntity.stockBags} bags", style = MaterialTheme.typography.bodySmall)
                                Text("Rate: ₹${remoteEntity.currentRate}", style = MaterialTheme.typography.bodySmall)
                                Text("Category: ${remoteEntity.category}", style = MaterialTheme.typography.bodySmall)
                                Text("GST: ${remoteEntity.gstPercent}%", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onResolve(true) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("resolve_use_local")
            ) {
                Text("Keep Local")
            }
        },
        dismissButton = {
            FilledTonalButton(
                onClick = { onResolve(false) },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.testTag("resolve_use_remote")
            ) {
                Text("Use Cloud")
            }
        },
        modifier = Modifier.testTag("conflict_resolution_dialog")
    )
}
