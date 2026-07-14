package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Commodity
import com.example.ui.BillingViewModel
import com.example.ui.components.EmptyStatePlaceholder
import java.io.File
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommoditiesScreen(viewModel: BillingViewModel) {
    val commodities by viewModel.commodities.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Screen States
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedProductForEdit by remember { mutableStateOf<Commodity?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Search, Filter & Sort States
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var selectedStockFilter by remember { mutableStateOf("ALL") } // "ALL", "LOW_STOCK", "OUT_OF_STOCK", "IN_STOCK"
    var selectedGstFilter by remember { mutableStateOf("ALL") } // "ALL", "0", "5", "12", "18", "28"
    
    // Sort parameters
    var activeSortType by remember { mutableStateOf("NAME_ASC") } // NAME_ASC, NAME_DESC, STOCK_ASC, STOCK_DESC, SELL_ASC, SELL_DESC, BUY_ASC, BUY_DESC

    // Pagination
    var currentPage by remember { mutableStateOf(1) }
    var itemsPerPage by remember { mutableStateOf(5) } // 5, 10, 20

    // Preset Categories
    val categoriesList = remember {
        listOf("General", "Grains", "Vegetables", "Fruits", "Spices", "Pulses", "Seeds", "Cotton")
    }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    // Filtered and Sorted list calculation
    val processedProducts = remember(commodities, searchQuery, selectedCategoryFilter, selectedStockFilter, selectedGstFilter, activeSortType) {
        var list = commodities

        // 1. Search Query
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                (it.barcode?.contains(searchQuery, ignoreCase = true) ?: false) ||
                (it.hsnCode?.contains(searchQuery, ignoreCase = true) ?: false)
            }
        }

        // 2. Category Filter
        if (selectedCategoryFilter != "ALL") {
            list = list.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }

        // 3. Stock Level Filter
        list = when (selectedStockFilter) {
            "LOW_STOCK" -> list.filter { it.stockBags in 1..49 }
            "OUT_OF_STOCK" -> list.filter { it.stockBags == 0 }
            "IN_STOCK" -> list.filter { it.stockBags >= 50 }
            else -> list
        }

        // 4. GST Filter
        if (selectedGstFilter != "ALL") {
            val gstVal = selectedGstFilter.toDoubleOrNull() ?: 0.0
            list = list.filter { it.gstPercent == gstVal }
        }

        // 5. Sorting
        list = when (activeSortType) {
            "NAME_ASC" -> list.sortedBy { it.name.lowercase() }
            "NAME_DESC" -> list.sortedByDescending { it.name.lowercase() }
            "STOCK_ASC" -> list.sortedBy { it.stockBags }
            "STOCK_DESC" -> list.sortedByDescending { it.stockBags }
            "SELL_ASC" -> list.sortedBy { if (it.sellingPrice > 0.0) it.sellingPrice else it.currentRate }
            "SELL_DESC" -> list.sortedByDescending { if (it.sellingPrice > 0.0) it.sellingPrice else it.currentRate }
            "BUY_ASC" -> list.sortedBy { it.purchasePrice }
            "BUY_DESC" -> list.sortedByDescending { it.purchasePrice }
            else -> list
        }

        list
    }

    // Pagination Calculation
    val totalItems = processedProducts.size
    val totalPages = maxOf(1, (totalItems + itemsPerPage - 1) / itemsPerPage)

    LaunchedEffect(totalPages) {
        if (currentPage > totalPages) {
            currentPage = totalPages
        }
    }

    val paginatedProducts = remember(processedProducts, currentPage, itemsPerPage) {
        val startIndex = (currentPage - 1) * itemsPerPage
        processedProducts.drop(startIndex).take(itemsPerPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Product Catalog & Pricing",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Manage products, crop inventory, purchase & selling prices, taxes, and barcodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // CSV Import Button
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = "Import CSV", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV")
                    }

                    // CSV Export Button
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Export CSV", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }

                    // Add Product Button
                    if (currentUser?.role != com.example.data.UserRole.STAFF) {
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Product")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Product")
                        }
                    }
                }
            }

            // Search and Advanced Filters Area
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
                    // Row 1: Search & Sorting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by name, category, barcode, or HSN...") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        )

                        // Sorting dropdown trigger
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { showSortMenu = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (activeSortType) {
                                        "NAME_ASC" -> "Name: A-Z"
                                        "NAME_DESC" -> "Name: Z-A"
                                        "STOCK_ASC" -> "Stock: Low-High"
                                        "STOCK_DESC" -> "Stock: High-Low"
                                        "SELL_ASC" -> "Selling Price: Low-High"
                                        "SELL_DESC" -> "Selling Price: High-Low"
                                        "BUY_ASC" -> "Purchase Price: Low-High"
                                        "BUY_DESC" -> "Purchase Price: High-Low"
                                        else -> "Sort Catalog"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name (A to Z)") },
                                    onClick = { activeSortType = "NAME_ASC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z to A)") },
                                    onClick = { activeSortType = "NAME_DESC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stock Level (Low to High)") },
                                    onClick = { activeSortType = "STOCK_ASC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.TrendingUp, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stock Level (High to Low)") },
                                    onClick = { activeSortType = "STOCK_DESC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.TrendingDown, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Selling Price (Low to High)") },
                                    onClick = { activeSortType = "SELL_ASC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Selling Price (High to Low)") },
                                    onClick = { activeSortType = "SELL_DESC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Purchase Price (Low to High)") },
                                    onClick = { activeSortType = "BUY_ASC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Purchase Price (High to Low)") },
                                    onClick = { activeSortType = "BUY_DESC"; showSortMenu = false },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                                )
                            }
                        }

                        // Page Size
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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

                    // Row 2: Category Filter LazyRow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Category:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryFilter == "ALL",
                                    onClick = { selectedCategoryFilter = "ALL"; currentPage = 1 },
                                    label = { Text("All Categories") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                            items(categoriesList) { cat ->
                                FilterChip(
                                    selected = selectedCategoryFilter.equals(cat, ignoreCase = true),
                                    onClick = { selectedCategoryFilter = cat; currentPage = 1 },
                                    label = { Text(cat) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Row 3: Stock Status & GST filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Stock filters
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Inventory Status:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            val stockFilters = listOf("ALL" to "All Levels", "LOW_STOCK" to "⚠️ Low Stock", "OUT_OF_STOCK" to "❌ Out", "IN_STOCK" to "✅ In Stock")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(stockFilters) { (code, label) ->
                                    FilterChip(
                                        selected = selectedStockFilter == code,
                                        onClick = { selectedStockFilter = code; currentPage = 1 },
                                        label = { Text(label, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                }
                            }
                        }

                        // GST filters
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("GST Tax:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            val gstFilters = listOf("ALL", "0", "5", "12", "18", "28")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(gstFilters) { gst ->
                                    FilterChip(
                                        selected = selectedGstFilter == gst,
                                        onClick = { selectedGstFilter = gst; currentPage = 1 },
                                        label = { Text(if (gst == "ALL") "All GST" else "$gst%", fontSize = 11.sp) },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Product Catalog List View
            if (paginatedProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePlaceholder(
                        title = "No Products Found",
                        subtitle = "We couldn't find any products matching your search query, sorting, or filters. Try adjusting your parameters.",
                        icon = Icons.Default.Category
                    )
                }
            } else {
                if (isTablet) {
                    // Comprehensive Responsive Table layout for wide screens
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Product Details", modifier = Modifier.weight(2.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Category & Code", modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Stock (Bags)", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Purchase Price", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Selling Price", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("GST & Fees", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                if (currentUser?.role != com.example.data.UserRole.STAFF) {
                                    Text("Actions", modifier = Modifier.width(80.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Table Rows List
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(paginatedProducts) { product ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { if (currentUser?.role != com.example.data.UserRole.STAFF) selectedProductForEdit = product }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Product Details cell
                                        Row(
                                            modifier = Modifier.weight(2.2f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = when (product.category.lowercase()) {
                                                        "grains" -> Icons.Default.Grass
                                                        "vegetables" -> Icons.Default.Agriculture
                                                        "fruits" -> Icons.Default.Eco
                                                        "spices" -> Icons.Default.SoupKitchen
                                                        else -> Icons.Default.Category
                                                    },
                                                    contentDescription = "Category Icon",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = product.name,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!product.barcode.isNullOrBlank()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.QrCode, contentDescription = "Barcode", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(product.barcode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }

                                        // Category & Code Cell
                                        Column(modifier = Modifier.weight(1.8f)) {
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(product.category, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                border = null,
                                                modifier = Modifier.height(24.dp)
                                            )
                                            if (!product.hsnCode.isNullOrBlank()) {
                                                Text("HSN: ${product.hsnCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                            }
                                        }

                                        // Stock Cell
                                        Box(modifier = Modifier.weight(1.2f)) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = when {
                                                        product.stockBags == 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                                        product.stockBags < 50 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                    }
                                                ),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "${product.stockBags} Bags",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when {
                                                        product.stockBags == 0 -> MaterialTheme.colorScheme.error
                                                        product.stockBags < 50 -> MaterialTheme.colorScheme.onErrorContainer
                                                        else -> MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                            }
                                        }

                                        // Purchase price cell
                                        Text(
                                            text = if (product.purchasePrice > 0) currencyFormat.format(product.purchasePrice) else "—",
                                            modifier = Modifier.weight(1.3f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Selling Price Cell
                                        Column(modifier = Modifier.weight(1.3f)) {
                                            val sellRate = if (product.sellingPrice > 0.0) product.sellingPrice else product.currentRate
                                            Text(
                                                text = currencyFormat.format(sellRate),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text("per Qtl", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        // GST & Fees Cell
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Text("GST: ${product.gstPercent.toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("Comm: ${product.commissionPercent}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        // Actions Cell
                                        if (currentUser?.role != com.example.data.UserRole.STAFF) {
                                            Row(
                                                modifier = Modifier.width(80.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                IconButton(onClick = { selectedProductForEdit = product }) {
                                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Product", tint = MaterialTheme.colorScheme.secondary)
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                } else {
                    // Mobile Adaptive Card layout (Beautiful, clear touch targets)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(paginatedProducts) { product ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (currentUser?.role != com.example.data.UserRole.STAFF) selectedProductForEdit = product },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Row 1: Header (Name, Category tag, Edit button)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Category,
                                                    contentDescription = "Category",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = product.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = product.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (currentUser?.role != com.example.data.UserRole.STAFF) {
                                            IconButton(onClick = { selectedProductForEdit = product }, modifier = Modifier.size(28.dp)) {
                                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Row 2: Prices, Stock level, GST details
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("PURCHASE PRICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = if (product.purchasePrice > 0.0) currencyFormat.format(product.purchasePrice) else "—",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("SELLING PRICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            val sellRate = if (product.sellingPrice > 0.0) product.sellingPrice else product.currentRate
                                            Text(
                                                text = currencyFormat.format(sellRate),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("CURRENT STOCK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "${product.stockBags} Bags",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Black,
                                                color = when {
                                                    product.stockBags == 0 -> MaterialTheme.colorScheme.error
                                                    product.stockBags < 50 -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Row 3: Barcode, HSN, and GST Rate
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (!product.barcode.isNullOrBlank()) {
                                                Icon(Icons.Default.QrCode, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(product.barcode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (!product.hsnCode.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("HSN: ${product.hsnCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("GST ${product.gstPercent.toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            border = null,
                                            modifier = Modifier.height(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pagination Controls footer
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Showing ${(currentPage - 1) * itemsPerPage + 1} - ${minOf(currentPage * itemsPerPage, totalItems)} of $totalItems products",
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
                            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Prev Page")
                        }

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

        // Add Product Dialog
        if (showAddDialog) {
            ProductFormDialog(
                title = "Add Product / Crop to Catalog",
                categories = categoriesList,
                onDismiss = { showAddDialog = false },
                onSave = { name, category, barcode, hsn, stock, buyPrice, sellPrice, gst, marketFee, commission ->
                    viewModel.addCommodity(
                        name = name,
                        marketFeePercent = marketFee,
                        commissionPercent = commission,
                        initialStock = stock,
                        currentRate = sellPrice, // maintain standard rate compatibility
                        category = category,
                        barcode = barcode,
                        gstPercent = gst,
                        hsnCode = hsn,
                        purchasePrice = buyPrice,
                        sellingPrice = sellPrice
                    )
                    showAddDialog = false
                }
            )
        }

        // Edit Product Dialog
        if (selectedProductForEdit != null) {
            val product = selectedProductForEdit!!
            ProductFormDialog(
                title = "Update Product details",
                categories = categoriesList,
                initialProduct = product,
                onDismiss = { selectedProductForEdit = null },
                onSave = { name, category, barcode, hsn, stock, buyPrice, sellPrice, gst, marketFee, commission ->
                    viewModel.updateCommodity(
                        product.copy(
                            name = name,
                            category = category,
                            barcode = barcode,
                            hsnCode = hsn,
                            stockBags = stock,
                            purchasePrice = buyPrice,
                            sellingPrice = sellPrice,
                            currentRate = sellPrice, // sync standard rate too
                            gstPercent = gst,
                            marketFeePercent = marketFee,
                            commissionPercent = commission
                        )
                    )
                    selectedProductForEdit = null
                },
                onDelete = {
                    viewModel.deleteCommodity(product)
                    selectedProductForEdit = null
                }
            )
        }

        // CSV Import Dialog
        if (showImportDialog) {
            CSVImportDialog(
                categories = categoriesList,
                onDismiss = { showImportDialog = false },
                onImport = { importedProductsList ->
                    importedProductsList.forEach { prod ->
                        viewModel.addCommodity(
                            name = prod.name,
                            category = prod.category,
                            barcode = prod.barcode,
                            hsnCode = prod.hsnCode,
                            initialStock = prod.stockBags,
                            purchasePrice = prod.purchasePrice,
                            sellingPrice = prod.sellingPrice,
                            currentRate = prod.sellingPrice,
                            gstPercent = prod.gstPercent,
                            marketFeePercent = prod.marketFeePercent,
                            commissionPercent = prod.commissionPercent
                        )
                    }
                    showImportDialog = false
                    viewModel.showToast("Successfully imported ${importedProductsList.size} products from CSV!")
                }
            )
        }

        // CSV Export Dialog
        if (showExportDialog) {
            CSVExportDialog(
                productsToExport = processedProducts,
                onDismiss = { showExportDialog = false }
            )
        }
    }
}

// Product Form Dialog (Handles both Create and Update)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    title: String,
    categories: List<String>,
    initialProduct: Commodity? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        barcode: String?,
        hsn: String?,
        stock: Int,
        buyPrice: Double,
        sellPrice: Double,
        gst: Double,
        marketFee: Double,
        commission: Double
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var selectedCategory by remember { mutableStateOf(initialProduct?.category ?: "General") }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }
    var hsnCode by remember { mutableStateOf(initialProduct?.hsnCode ?: "") }
    var stockString by remember { mutableStateOf(initialProduct?.stockBags?.toString() ?: "0") }
    var buyPriceString by remember { mutableStateOf(initialProduct?.purchasePrice?.toString() ?: "0.0") }
    var sellPriceString by remember {
        val spVal = initialProduct?.sellingPrice ?: 0.0
        val finalVal = if (spVal > 0.0) spVal else (initialProduct?.currentRate ?: 0.0)
        mutableStateOf(finalVal.toString())
    }
    var gstString by remember { mutableStateOf(initialProduct?.gstPercent?.toString() ?: "0.0") }
    var marketFeeString by remember { mutableStateOf(initialProduct?.marketFeePercent?.toString() ?: "1.0") }
    var commissionString by remember { mutableStateOf(initialProduct?.commissionPercent?.toString() ?: "5.0") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Validation states
    var nameError by remember { mutableStateOf(false) }
    var sellPriceError by remember { mutableStateOf(false) }

    fun validateAndSave() {
        nameError = name.trim().isBlank()
        val sp = sellPriceString.toDoubleOrNull() ?: -1.0
        sellPriceError = sp < 0.0

        if (!nameError && !sellPriceError) {
            onSave(
                name.trim(),
                selectedCategory,
                barcode.trim().ifBlank { null },
                hsnCode.trim().ifBlank { null },
                stockString.toIntOrNull() ?: 0,
                buyPriceString.toDoubleOrNull() ?: 0.0,
                sellPriceString.toDoubleOrNull() ?: 0.0,
                gstString.toDoubleOrNull() ?: 0.0,
                marketFeeString.toDoubleOrNull() ?: 1.0,
                commissionString.toDoubleOrNull() ?: 5.0
            )
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Product Name *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Name is required", color = MaterialTheme.colorScheme.error) } } else null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                // Category selection dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedCategory,
                        onValueChange = {},
                        label = { Text("Product Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Barcode and HSN Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode / GTIN") },
                        placeholder = { Text("e.g. 890123...") },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    // Generate simulated barcode
                                    barcode = (890100000000L + (100000L..999999L).random()).toString()
                                }
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = "Generate Barcode")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = hsnCode,
                        onValueChange = { hsnCode = it },
                        label = { Text("HSN Code") },
                        placeholder = { Text("e.g. 1006") },
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                // Stock, Purchase Price, Selling Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockString,
                        onValueChange = { stockString = it },
                        label = { Text("Stock (Bags)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = buyPriceString,
                        onValueChange = { buyPriceString = it },
                        label = { Text("Purchase Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = sellPriceString,
                        onValueChange = { sellPriceString = it; sellPriceError = false },
                        label = { Text("Selling Price (₹) *") },
                        isError = sellPriceError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                // GST %, Commission %, Market Fee %
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = gstString,
                        onValueChange = { gstString = it },
                        label = { Text("GST Tax %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = commissionString,
                        onValueChange = { commissionString = it },
                        label = { Text("Commission %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = marketFeeString,
                        onValueChange = { marketFeeString = it },
                        label = { Text("Market Fee %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Product")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Product from Directory")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = ::validateAndSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Product permanently?") },
            text = { Text("Are you sure you want to delete '$name'? This will completely clear its stock level and records in the system catalog.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently")
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

// CSV Import Dialog with interactive load mock data button
@Composable
fun CSVImportDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onImport: (List<Commodity>) -> Unit
) {
    var rawCsvText by remember { mutableStateOf("") }
    var parseErrorMsg by remember { mutableStateOf<String?>(null) }
    var parsedPreviewList by remember { mutableStateOf<List<Commodity>>(emptyList()) }

    fun parseCSV() {
        if (rawCsvText.isBlank()) {
            parseErrorMsg = "Please input or paste CSV data first."
            return
        }

        try {
            val lines = rawCsvText.trim().split("\n")
            val products = mutableListOf<Commodity>()
            
            lines.forEachIndexed { idx, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachIndexed
                
                // Skip Header row if exists
                if (idx == 0 && (line.contains("name", ignoreCase = true) || line.contains("category", ignoreCase = true))) {
                    return@forEachIndexed
                }

                // Simple comma splitter
                val tokens = line.split(",")
                if (tokens.size < 2) {
                    throw Exception("Line ${idx + 1} has insufficient columns (must have at least Name & Category).")
                }

                val name = tokens[0].trim()
                val category = tokens.getOrNull(1)?.trim() ?: "General"
                val barcode = tokens.getOrNull(2)?.trim()?.ifBlank { null }
                val hsn = tokens.getOrNull(3)?.trim()?.ifBlank { null }
                val stock = tokens.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
                val buyPrice = tokens.getOrNull(5)?.trim()?.toDoubleOrNull() ?: 0.0
                val sellPrice = tokens.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 0.0
                val gst = tokens.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0
                val commission = tokens.getOrNull(8)?.trim()?.toDoubleOrNull() ?: 5.0
                val marketFee = tokens.getOrNull(9)?.trim()?.toDoubleOrNull() ?: 1.0

                products.add(
                    Commodity(
                        name = name,
                        category = category,
                        barcode = barcode,
                        hsnCode = hsn,
                        stockBags = stock,
                        purchasePrice = buyPrice,
                        sellingPrice = sellPrice,
                        currentRate = sellPrice,
                        gstPercent = gst,
                        commissionPercent = commission,
                        marketFeePercent = marketFee
                    )
                )
            }

            if (products.isEmpty()) {
                throw Exception("No valid rows parsed.")
            }

            parsedPreviewList = products
            parseErrorMsg = null
        } catch (e: Exception) {
            parseErrorMsg = "Parse failed: ${e.localizedMessage}"
            parsedPreviewList = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CSV Product Import Terminal", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Paste comma-separated products details. Expected order/columns per line:\nName, Category, Barcode, HSN, Stock, Purchase Price, Selling Price, GST %, Commission %, Market Fee %",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            rawCsvText = """
Name,Category,Barcode,HSN Code,Stock,Purchase Price,Selling Price,GST %,Commission %,Market Fee %
Premium Basmati Rice,Grains,890100210015,1006,120,5800,6500,5,6,1.5
Sharbati Wheat,Grains,890100210022,1001,250,2100,2400,0,5,1
Alphonso Mangoes,Fruits,890100210039,0804,45,1800,2500,12,8,2
Desi Red Onions,Vegetables,890100210046,0703,400,1200,1800,0,6,2
Organic Turmeric Powder,Spices,890100210053,0910,85,150,220,5,5,1
Chana Dal,Pulses,890100210060,0713,110,4800,5500,5,5.5,1
Green Cardamom,Spices,890100210077,0908,30,1200,1600,5,6,1.5
Fresh Potatoes,Vegetables,890100210084,0701,500,800,1200,0,5,1
                            """.trimIndent()
                            parseErrorMsg = null
                            parsedPreviewList = emptyList()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load Mock CSV Products")
                    }

                    Button(
                        onClick = ::parseCSV,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Validate & Preview")
                    }
                }

                OutlinedTextField(
                    value = rawCsvText,
                    onValueChange = { rawCsvText = it },
                    label = { Text("CSV Source Text Area") },
                    placeholder = { Text("Example: Premium Basmati, Grains, 890123, 1006, 120, 5800, 6500, 5, 6, 1.5") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                if (parseErrorMsg != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = parseErrorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (parsedPreviewList.isNotEmpty()) {
                    Text(
                        text = "Parsed Live Preview (${parsedPreviewList.size} Products Identified):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(parsedPreviewList) { prod ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(prod.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text("${prod.category} • HSN ${prod.hsnCode ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Qty: ${prod.stockBags} Bags", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text("Sell: ₹${prod.sellingPrice}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(parsedPreviewList) },
                enabled = parsedPreviewList.isNotEmpty()
            ) {
                Text("Commit Import (${parsedPreviewList.size} items)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// CSV Export Dialog with direct Copy to Clipboard
@Composable
fun CSVExportDialog(
    productsToExport: List<Commodity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rawCsvText = remember(productsToExport) {
        val header = "Name,Category,Barcode,HSN Code,Stock (Bags),Purchase Price (₹),Selling Price (₹),GST Tax %,Commission %,Market Fee %\n"
        val rows = productsToExport.joinToString("\n") { prod ->
            val sellRate = if (prod.sellingPrice > 0.0) prod.sellingPrice else prod.currentRate
            "${prod.name},${prod.category},${prod.barcode ?: ""},${prod.hsnCode ?: ""},${prod.stockBags},${prod.purchasePrice},${sellRate},${prod.gstPercent},${prod.commissionPercent},${prod.marketFeePercent}"
        }
        header + rows
    }

    // Attempt to write locally to app cache so it is technically a file in storage
    LaunchedEffect(rawCsvText) {
        try {
            val file = File(context.cacheDir, "exported_products.csv")
            file.writeText(rawCsvText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CSV Product Export Terminal", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "A CSV of your filtered/sorted product catalog has been generated. You can copy the raw data below to paste directly into Excel/Google Sheets, or download the local file.",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(rawCsvText))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Raw CSV to Clipboard")
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rawCsvText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Terminal")
            }
        }
    )
}
