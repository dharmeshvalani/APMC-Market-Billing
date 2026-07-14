package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import java.util.*
import java.text.*

sealed interface ActionState {
    object Idle : ActionState
    object Loading : ActionState
    data class Success(val message: String) : ActionState
    data class Error(val errorMessage: String) : ActionState
}

class BillingViewModel(
    private val repository: BillingRepository,
    val authService: AuthService
) : ViewModel() {

    // Shop Configuration State
    private val _shopConfig = MutableStateFlow(authService.loadLocalShopConfig())
    val shopConfig: StateFlow<ShopConfig> = _shopConfig.asStateFlow()

    fun loadShopConfig() {
        val email = authService.currentUser.value?.email
        if (email != null) {
            viewModelScope.launch {
                // First load local cache
                val local = authService.loadLocalShopConfig()
                _shopConfig.value = local
                _isDarkMode.value = when (local.theme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> _isDarkMode.value
                }
                
                // Then try to pull from Supabase
                val result = authService.fetchShopConfigFromSupabase(email)
                result.onSuccess { cloudConfig ->
                    if (cloudConfig != null) {
                        authService.saveLocalShopConfig(cloudConfig)
                        _shopConfig.value = cloudConfig
                        _isDarkMode.value = when (cloudConfig.theme) {
                            "Dark" -> true
                            "Light" -> false
                            else -> _isDarkMode.value
                        }
                        showToast("Settings synchronized with Supabase")
                    }
                }.onFailure {
                    Log.e("BillingViewModel", "Failed to fetch cloud shop config, using local cache", it)
                }
            }
        } else {
            val local = authService.loadLocalShopConfig()
            _shopConfig.value = local
            _isDarkMode.value = when (local.theme) {
                "Dark" -> true
                "Light" -> false
                else -> _isDarkMode.value
            }
        }
    }

    fun saveShopConfig(config: ShopConfig, onComplete: () -> Unit = {}) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                // Save locally first
                authService.saveLocalShopConfig(config)
                _shopConfig.value = config
                
                // Sync theme
                _isDarkMode.value = when (config.theme) {
                    "Dark" -> true
                    "Light" -> false
                    else -> _isDarkMode.value
                }

                val email = authService.currentUser.value?.email
                if (email != null) {
                    val result = authService.saveShopConfigToSupabase(config, email)
                    result.onSuccess {
                        _actionState.value = ActionState.Success("Shop Settings saved and uploaded to Supabase!")
                        showToast("Synced to Cloud database")
                        onComplete()
                    }.onFailure { error ->
                        _actionState.value = ActionState.Success("Saved locally. Supabase upload failed.")
                        showToast("Offline mode: Saved locally")
                        onComplete()
                    }
                } else {
                    _actionState.value = ActionState.Success("Saved locally (Sandbox mode).")
                    showToast("Sandbox: Saved locally")
                    onComplete()
                }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to save settings")
            }
        }
    }

    // Theme toggle
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
        val current = _shopConfig.value
        val updated = current.copy(theme = if (_isDarkMode.value) "Dark" else "Light")
        _shopConfig.value = updated
        authService.saveLocalShopConfig(updated)
    }

    // Navigation State
    private val _currentScreen = MutableStateFlow("auth") // Default to auth page for protection
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    fun navigateTo(screen: String) {
        // Protected Route check: If not logged in and not heading to auth, force redirect
        if (authService.currentUser.value == null && screen != "auth") {
            _currentScreen.value = "auth"
        } else {
            _currentScreen.value = screen
        }
    }

    // Action execution state (sealed class)
    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    fun clearActionState() {
        _actionState.value = ActionState.Idle
    }

    // Toast notifications
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastMessage.emit(message)
        }
    }

    // User session properties
    val currentUser: StateFlow<UserSession?> = authService.currentUser
    val supabaseUrl: StateFlow<String> = authService.supabaseUrl
    val supabaseAnonKey: StateFlow<String> = authService.supabaseAnonKey

    // --- OFFLINE & BACKING SYNC ENGINE ---
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isManualOffline = MutableStateFlow(false)
    val isManualOffline: StateFlow<Boolean> = _isManualOffline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _conflictResolutionPolicy = MutableStateFlow("CLIENT_WINS") // "CLIENT_WINS", "SERVER_WINS", "ASK_USER"
    val conflictResolutionPolicy: StateFlow<String> = _conflictResolutionPolicy.asStateFlow()

    private val _autoRetryEnabled = MutableStateFlow(true)
    val autoRetryEnabled: StateFlow<Boolean> = _autoRetryEnabled.asStateFlow()

    private val _syncLog = MutableStateFlow<List<String>>(emptyList())
    val syncLog: StateFlow<List<String>> = _syncLog.asStateFlow()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    // Conflict Resolution Screen helper states
    var activeConflictEntity = MutableStateFlow<Any?>(null) // Party or Commodity that is in conflict
    var activeConflictRemote = MutableStateFlow<Any?>(null) // Remote version

    fun addLog(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _syncLog.value = listOf("[$time] $msg") + _syncLog.value.take(49)
    }

    fun toggleManualOffline() {
        _isManualOffline.value = !_isManualOffline.value
        _isOnline.value = !_isManualOffline.value
        if (_isOnline.value) {
            addLog("Forced ONLINE. Background sync started.")
            showToast("Online. Background sync started.")
            triggerBackgroundSync()
        } else {
            addLog("Forced OFFLINE. Working locally.")
            showToast("Offline. Working locally.")
        }
    }

    fun setConflictPolicy(policy: String) {
        _conflictResolutionPolicy.value = policy
        addLog("Conflict Resolution policy updated to: $policy")
    }

    fun toggleAutoRetry() {
        _autoRetryEnabled.value = !_autoRetryEnabled.value
        addLog("Auto-Retry toggled to: ${_autoRetryEnabled.value}")
    }

    fun updatePendingSyncCount() {
        viewModelScope.launch {
            try {
                val p = repository.getUnsyncedParties().size
                val c = repository.getUnsyncedCommodities().size
                val b = repository.getUnsyncedBills().size
                val t = repository.getUnsyncedTransactions().size
                val e = repository.getUnsyncedExpenses().size
                _pendingSyncCount.value = p + c + b + t + e
            } catch (ex: Exception) {
                Log.e("Sync", "Count update error", ex)
            }
        }
    }

    fun resolveConflictWithDecision(useLocal: Boolean) {
        val local = activeConflictEntity.value
        val remote = activeConflictRemote.value
        viewModelScope.launch {
            try {
                if (local is Party && remote is Party) {
                    if (useLocal) {
                        // Client Wins: push local version to remote
                        authService.pushPartyToSupabase(local)
                        repository.updatePartySyncStatus(local.id, true)
                        addLog("Resolved Conflict for ${local.name}: Client Wins (Local Kept)")
                    } else {
                        // Server Wins: replace local with remote
                        val updated = local.copy(
                            name = remote.name,
                            address = remote.address,
                            licenseNo = remote.licenseNo ?: local.licenseNo,
                            bankDetails = remote.bankDetails ?: local.bankDetails,
                            balance = remote.balance,
                            isSynced = true
                        )
                        repository.updateParty(updated)
                        addLog("Resolved Conflict for ${local.name}: Server Wins (Remote Applied)")
                    }
                } else if (local is Commodity && remote is Commodity) {
                    if (useLocal) {
                        authService.pushCommodityToSupabase(local)
                        repository.updateCommoditySyncStatus(local.id, true)
                        addLog("Resolved Conflict for ${local.name}: Client Wins (Local Kept)")
                    } else {
                        val updated = local.copy(
                            marketFeePercent = remote.marketFeePercent,
                            commissionPercent = remote.commissionPercent,
                            stockBags = remote.stockBags,
                            currentRate = remote.currentRate,
                            category = remote.category,
                            barcode = remote.barcode ?: local.barcode,
                            gstPercent = remote.gstPercent,
                            hsnCode = remote.hsnCode ?: local.hsnCode,
                            purchasePrice = remote.purchasePrice,
                            sellingPrice = remote.sellingPrice,
                            isSynced = true
                        )
                        repository.updateCommodity(updated)
                        addLog("Resolved Conflict for ${local.name}: Server Wins (Remote Applied)")
                    }
                }
            } catch (ex: Exception) {
                addLog("Conflict Resolution error: ${ex.localizedMessage}")
            } finally {
                activeConflictEntity.value = null
                activeConflictRemote.value = null
                updatePendingSyncCount()
                // Continue with sync loop if applicable
                triggerBackgroundSync()
            }
        }
    }

    fun triggerBackgroundSync() {
        if (!_isOnline.value || _isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            addLog("Starting background synchronization...")
            try {
                // 1. Sync Parties (Customers)
                val unsyncedParties = repository.getUnsyncedParties()
                for (party in unsyncedParties) {
                    addLog("Syncing Partner: ${party.name}...")
                    
                    // Conflict detection: see if remote has a conflicting version
                    val remoteResult = authService.fetchPartiesFromSupabase()
                    var remoteParty: Party? = null
                    remoteResult.onSuccess { remoteList ->
                        remoteParty = remoteList.find { it.phone == party.phone && it.type == party.type }
                    }

                    if (remoteParty != null && (remoteParty!!.name != party.name || remoteParty!!.balance != party.balance)) {
                        // Conflict!
                        when (_conflictResolutionPolicy.value) {
                            "CLIENT_WINS" -> {
                                authService.pushPartyToSupabase(party)
                                repository.updatePartySyncStatus(party.id, true)
                                addLog("Conflict for ${party.name} resolved: Client Wins.")
                            }
                            "SERVER_WINS" -> {
                                val updated = party.copy(
                                    name = remoteParty!!.name,
                                    address = remoteParty!!.address,
                                    licenseNo = remoteParty!!.licenseNo ?: party.licenseNo,
                                    bankDetails = remoteParty!!.bankDetails ?: party.bankDetails,
                                    balance = remoteParty!!.balance,
                                    isSynced = true
                                )
                                repository.updateParty(updated)
                                addLog("Conflict for ${party.name} resolved: Server Wins.")
                            }
                            "ASK_USER" -> {
                                addLog("Conflict for ${party.name} detected. Waiting for user decision.")
                                activeConflictEntity.value = party
                                activeConflictRemote.value = remoteParty
                                _isSyncing.value = false
                                return@launch // Stop and wait for user
                            }
                        }
                    } else {
                        val result = authService.pushPartyToSupabase(party)
                        result.onSuccess {
                            repository.updatePartySyncStatus(party.id, true)
                            addLog("Partner ${party.name} synced.")
                        }.onFailure { err ->
                            addLog("Partner ${party.name} sync failed: ${err.localizedMessage}")
                        }
                    }
                }

                // 2. Sync Commodities (Products)
                val unsyncedCommodities = repository.getUnsyncedCommodities()
                for (commodity in unsyncedCommodities) {
                    addLog("Syncing Product: ${commodity.name}...")

                    // Conflict detection
                    val remoteResult = authService.fetchCommoditiesFromSupabase()
                    var remoteCommodity: Commodity? = null
                    remoteResult.onSuccess { remoteList ->
                        remoteCommodity = remoteList.find { it.name == commodity.name }
                    }

                    if (remoteCommodity != null && remoteCommodity!!.stockBags != commodity.stockBags) {
                        // Conflict!
                        when (_conflictResolutionPolicy.value) {
                            "CLIENT_WINS" -> {
                                authService.pushCommodityToSupabase(commodity)
                                repository.updateCommoditySyncStatus(commodity.id, true)
                                addLog("Conflict for Product ${commodity.name} resolved: Client Wins.")
                            }
                            "SERVER_WINS" -> {
                                val updated = commodity.copy(
                                    marketFeePercent = remoteCommodity!!.marketFeePercent,
                                    commissionPercent = remoteCommodity!!.commissionPercent,
                                    stockBags = remoteCommodity!!.stockBags,
                                    currentRate = remoteCommodity!!.currentRate,
                                    category = remoteCommodity!!.category,
                                    barcode = remoteCommodity!!.barcode ?: commodity.barcode,
                                    gstPercent = remoteCommodity!!.gstPercent,
                                    hsnCode = remoteCommodity!!.hsnCode ?: commodity.hsnCode,
                                    purchasePrice = remoteCommodity!!.purchasePrice,
                                    sellingPrice = remoteCommodity!!.sellingPrice,
                                    isSynced = true
                                )
                                repository.updateCommodity(updated)
                                addLog("Conflict for Product ${commodity.name} resolved: Server Wins.")
                            }
                            "ASK_USER" -> {
                                addLog("Conflict for Product ${commodity.name} detected. Waiting for user decision.")
                                activeConflictEntity.value = commodity
                                activeConflictRemote.value = remoteCommodity
                                _isSyncing.value = false
                                return@launch
                            }
                        }
                    } else {
                        val result = authService.pushCommodityToSupabase(commodity)
                        result.onSuccess {
                            repository.updateCommoditySyncStatus(commodity.id, true)
                            addLog("Product ${commodity.name} synced.")
                        }.onFailure { err ->
                            addLog("Product ${commodity.name} sync failed: ${err.localizedMessage}")
                        }
                    }
                }

                // 3. Sync Inventory Transactions
                val unsyncedTx = repository.getUnsyncedTransactions()
                for (tx in unsyncedTx) {
                    addLog("Syncing Stock Ledger Entry...")
                    val result = authService.pushInventoryTransactionToSupabase(tx)
                    result.onSuccess {
                        repository.updateTransactionSyncStatus(tx.id, true)
                        addLog("Stock transaction synced.")
                    }.onFailure { err ->
                        addLog("Stock transaction sync failed: ${err.localizedMessage}")
                    }
                }

                // 4. Sync Bills (Invoices)
                val unsyncedBills = repository.getUnsyncedBills()
                for (bill in unsyncedBills) {
                    addLog("Syncing Bill #${bill.billNumber}...")
                    val result = authService.pushBillToSupabase(bill)
                    result.onSuccess {
                        repository.updateBillSyncStatus(bill.id, true)
                        addLog("Bill #${bill.billNumber} synced.")
                    }.onFailure { err ->
                        addLog("Bill #${bill.billNumber} sync failed: ${err.localizedMessage}")
                    }
                }

                // 5. Sync Expenses
                val unsyncedExpenses = repository.getUnsyncedExpenses()
                for (expense in unsyncedExpenses) {
                    addLog("Syncing Expense item...")
                    val result = authService.pushExpenseToSupabase(expense)
                    result.onSuccess {
                        repository.updateExpenseSyncStatus(expense.id, true)
                        addLog("Expense item synced.")
                    }.onFailure { err ->
                        addLog("Expense item sync failed: ${err.localizedMessage}")
                    }
                }

                updatePendingSyncCount()
                addLog("Sync finished. Pending queue: ${_pendingSyncCount.value} items.")
            } catch (ex: Exception) {
                addLog("Sync loop encountered error: ${ex.localizedMessage}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    init {
        // Enforce protected routes globally: If logged out, redirect to auth screen immediately.
        viewModelScope.launch {
            authService.currentUser.collect { user ->
                if (user == null) {
                    _currentScreen.value = "auth"
                } else {
                    // Load the shop configuration
                    loadShopConfig()

                    // Update sync counters on login/start
                    updatePendingSyncCount()
                    triggerBackgroundSync()
                    
                    // If shop configuration is NOT completed, redirect to "setup_wizard" onboarding flow
                    val currentLocalConfig = authService.loadLocalShopConfig()
                    if (!currentLocalConfig.isCompleted && (user.role == UserRole.OWNER || user.role == UserRole.ADMIN)) {
                        _currentScreen.value = "setup_wizard"
                    } else if (_currentScreen.value == "auth" || _currentScreen.value == "setup_wizard") {
                        _currentScreen.value = "dashboard"
                    }
                }
            }
        }

        // Automatic Retry background job
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15000) // check every 15 seconds
                if (_autoRetryEnabled.value && _isOnline.value && _pendingSyncCount.value > 0) {
                    addLog("Auto-Retry: Pending items detected. Triggering sync.")
                    triggerBackgroundSync()
                }
            }
        }
    }

    // Authentication actions
    fun loginWithEmail(email: String, pwd: String) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            val result = authService.signInWithEmail(email, pwd)
            result.onSuccess { session ->
                _actionState.value = ActionState.Success("Welcome, ${session.name}!")
                showToast("Logged in as ${session.role.name}")
            }.onFailure { exception ->
                _actionState.value = ActionState.Error(exception.localizedMessage ?: "Login failed")
            }
        }
    }

    fun requestOtp(phone: String, onSent: () -> Unit) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            val result = authService.signInWithOtp(phone)
            result.onSuccess {
                _actionState.value = ActionState.Success("OTP sent successfully!")
                showToast("6-digit code dispatched")
                onSent()
            }.onFailure { exception ->
                _actionState.value = ActionState.Error(exception.localizedMessage ?: "Failed to send OTP")
            }
        }
    }

    fun verifyOtp(phone: String, code: String) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            val result = authService.verifyOtp(phone, code)
            result.onSuccess { session ->
                _actionState.value = ActionState.Success("Welcome, ${session.name}!")
                showToast("OTP Verification Success")
            }.onFailure { exception ->
                _actionState.value = ActionState.Error(exception.localizedMessage ?: "OTP verify failed")
            }
        }
    }

    fun registerUser(email: String, pwd: String, name: String, phone: String, role: UserRole) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            val result = authService.signUp(email, pwd, name, phone, role)
            result.onSuccess { session ->
                _actionState.value = ActionState.Success("Account registered! Welcome ${session.name}!")
                showToast("Account Created")
            }.onFailure { exception ->
                _actionState.value = ActionState.Error(exception.localizedMessage ?: "Registration failed")
            }
        }
    }

    fun recoverPassword(email: String) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            val result = authService.sendPasswordRecovery(email)
            result.onSuccess {
                _actionState.value = ActionState.Success("Recovery email dispatched to $email if registered.")
                showToast("Reset Link Dispatched")
            }.onFailure { exception ->
                _actionState.value = ActionState.Error(exception.localizedMessage ?: "Failed to recover password")
            }
        }
    }

    fun logout() {
        authService.logout()
        showToast("Logged out successfully.")
    }

    fun saveConfig(url: String, anonKey: String) {
        authService.updateSupabaseConfig(url, anonKey)
        showToast("Supabase credentials saved.")
    }

    // Search and Filters
    val partySearchQuery = MutableStateFlow("")
    val commoditySearchQuery = MutableStateFlow("")
    val billSearchQuery = MutableStateFlow("")
    val inventorySearchQuery = MutableStateFlow("")

    // Raw Room Database Streams
    val parties: StateFlow<List<Party>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val commodities: StateFlow<List<Commodity>> = repository.allCommodities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bills: StateFlow<List<Bill>> = repository.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ledgerEntries: StateFlow<List<LedgerEntry>> = repository.allLedgerEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryTransactions: StateFlow<List<InventoryTransaction>> = repository.allInventoryTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Parties
    val filteredParties: StateFlow<List<Party>> = combine(parties, partySearchQuery) { list, query ->
        if (query.isEmpty()) list else list.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Inventory Transactions
    val filteredInventoryTransactions: StateFlow<List<InventoryTransaction>> = combine(inventoryTransactions, inventorySearchQuery) { list, query ->
        if (query.isEmpty()) list else list.filter {
            it.commodityName.contains(query, ignoreCase = true) ||
            it.type.contains(query, ignoreCase = true) ||
            (it.partyName ?: "").contains(query, ignoreCase = true) ||
            it.remarks.contains(query, ignoreCase = true) ||
            (it.referenceNo ?: "").contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Commodities
    val filteredCommodities: StateFlow<List<Commodity>> = combine(commodities, commoditySearchQuery) { list, query ->
        if (query.isEmpty()) list else list.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Bills
    val filteredBills: StateFlow<List<Bill>> = combine(bills, billSearchQuery) { list, query ->
        if (query.isEmpty()) list else list.filter {
            it.billNumber.contains(query, ignoreCase = true) ||
            it.partyName.contains(query, ignoreCase = true) ||
            it.commodityName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Computed Dashboard Stats
    val totalReceivables: StateFlow<Double> = parties.map { list ->
        list.filter { it.type == "TRADER" }.sumOf { maxOf(0.0, it.balance) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalPayables: StateFlow<Double> = parties.map { list ->
        list.filter { it.type == "FARMER" }.sumOf { if (it.balance < 0) -it.balance else 0.0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalStockBags: StateFlow<Int> = commodities.map { list ->
        list.sumOf { it.stockBags }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSalesVolume: StateFlow<Double> = bills.map { list ->
        list.filter { it.partyType == "TRADER" }.sumOf { it.netAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Today's Sales
    val todaySales: StateFlow<Double> = bills.map { list ->
        val calendar = Calendar.getInstance()
        val todayYear = calendar.get(Calendar.YEAR)
        val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
        list.filter { bill ->
            bill.partyType == "TRADER" && {
                val bCal = Calendar.getInstance().apply { timeInMillis = bill.date }
                bCal.get(Calendar.YEAR) == todayYear && bCal.get(Calendar.DAY_OF_YEAR) == todayDay
            }()
        }.sumOf { it.netAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Today's Bills
    val todayBillsCount: StateFlow<Int> = bills.map { list ->
        val calendar = Calendar.getInstance()
        val todayYear = calendar.get(Calendar.YEAR)
        val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
        list.count { bill ->
            val bCal = Calendar.getInstance().apply { timeInMillis = bill.date }
            bCal.get(Calendar.YEAR) == todayYear && bCal.get(Calendar.DAY_OF_YEAR) == todayDay
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Pending Payments Count
    val pendingPaymentsCount: StateFlow<Int> = bills.map { list ->
        list.count { !it.isPaid }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Pending Payments Amount
    val pendingPaymentsAmount: StateFlow<Double> = bills.map { list ->
        list.filter { !it.isPaid }.sumOf { it.netAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Monthly Revenue
    val monthlyRevenue: StateFlow<Double> = bills.map { list ->
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        list.filter { bill ->
            bill.partyType == "TRADER" && {
                val bCal = Calendar.getInstance().apply { timeInMillis = bill.date }
                bCal.get(Calendar.YEAR) == currentYear && bCal.get(Calendar.MONTH) == currentMonth
            }()
        }.sumOf { it.netAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Low Stock Count
    val lowStockCount: StateFlow<Int> = commodities.map { list ->
        list.count { it.stockBags < 50 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Top Products
    val topProducts: StateFlow<List<Pair<String, Double>>> = bills.map { list ->
        list.filter { it.partyType == "TRADER" }
            .groupBy { it.commodityName }
            .mapValues { entry -> entry.value.sumOf { it.netAmount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weekly Sales Data
    val weeklySalesData: StateFlow<List<Pair<String, Double>>> = bills.map { list ->
        val sdf = SimpleDateFormat("E", Locale.getDefault())
        (0..6).map { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayEnd = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            val label = sdf.format(cal.time)
            val sales = list.filter { it.partyType == "TRADER" && it.date in dayStart..dayEnd }.sumOf { it.netAmount }
            label to sales
        }.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly Sales Data
    val monthlySalesData: StateFlow<List<Pair<String, Double>>> = bills.map { list ->
        val sdf = SimpleDateFormat("MMM", Locale.getDefault())
        (0..5).map { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val label = sdf.format(cal.time)
            val sales = list.filter { bill ->
                bill.partyType == "TRADER" && {
                    val bCal = Calendar.getInstance().apply { timeInMillis = bill.date }
                    bCal.get(Calendar.YEAR) == year && bCal.get(Calendar.MONTH) == month
                }()
            }.sumOf { it.netAmount }
            label to sales
        }.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unified Recent Activity Feed
    val recentActivity: StateFlow<List<DashboardActivity>> = combine(bills, ledgerEntries) { bList, lList ->
        val activities = mutableListOf<DashboardActivity>()
        
        bList.forEach { bill ->
            val typeStr = if (bill.partyType == "FARMER") "Patti Ticket" else "Sale Invoice"
            activities.add(
                DashboardActivity(
                    id = "bill_${bill.id}",
                    title = "Created $typeStr ${bill.billNumber}",
                    description = "${bill.partyName} • ${bill.bagCount} bags of ${bill.commodityName}",
                    timestamp = bill.date,
                    amount = bill.netAmount,
                    type = "BILL",
                    isPositive = bill.partyType == "TRADER"
                )
            )
        }
        
        lList.forEach { entry ->
            val isReceived = entry.entryType == "CREDIT"
            activities.add(
                DashboardActivity(
                    id = "ledger_${entry.id}",
                    title = if (isReceived) "Received Payment" else "Disbursed Payment",
                    description = entry.description,
                    timestamp = entry.date,
                    amount = entry.amount,
                    type = "PAYMENT",
                    isPositive = isReceived
                )
            )
        }
        
        activities.sortedByDescending { it.timestamp }.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // CRUD - Parties
    fun addParty(name: String, type: String, phone: String, address: String, licenseNo: String? = null, bankDetails: String? = null) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            _actionState.value = ActionState.Error("Access Denied: Staff role cannot manage accounts directory.")
            return
        }
        if (name.isBlank() || phone.isBlank()) {
            _actionState.value = ActionState.Error("Name and Phone number are required.")
            return
        }
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                val newParty = Party(
                    name = name.trim(),
                    type = type,
                    phone = phone.trim(),
                    address = address.trim(),
                    licenseNo = licenseNo?.trim(),
                    bankDetails = bankDetails?.trim(),
                    isSynced = false
                )
                val generatedId = repository.insertParty(newParty)
                _actionState.value = ActionState.Success("Party $name successfully added.")
                showToast("Added $name ($type)")
                updatePendingSyncCount()

                // Background Supabase Push if online
                if (_isOnline.value) {
                    launch {
                        val finalParty = newParty.copy(id = generatedId.toInt())
                        val syncResult = authService.pushPartyToSupabase(finalParty)
                        syncResult.onSuccess {
                            repository.updatePartySyncStatus(generatedId.toInt(), true)
                            updatePendingSyncCount()
                            showToast("Synced to Supabase")
                        }.onFailure {
                            Log.e("BillingViewModel", "Supabase push failed for $name", it)
                            addLog("Immediate push failed for $name: ${it.localizedMessage}")
                        }
                    }
                } else {
                    addLog("Offline mode: Added partner $name locally.")
                }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to add party")
            }
        }
    }

    fun updateParty(party: Party) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            showToast("Access Denied: Staff role cannot update directory details.")
            return
        }
        viewModelScope.launch {
            try {
                val updatedParty = party.copy(isSynced = false)
                repository.updateParty(updatedParty)
                showToast("Updated party: ${party.name}")
                updatePendingSyncCount()

                // Background Supabase push
                if (_isOnline.value) {
                    launch {
                        val syncResult = authService.pushPartyToSupabase(updatedParty)
                        syncResult.onSuccess {
                            repository.updatePartySyncStatus(updatedParty.id, true)
                            updatePendingSyncCount()
                            showToast("Updates pushed to Supabase")
                        }.onFailure {
                            Log.e("BillingViewModel", "Supabase update push failed", it)
                            addLog("Immediate update push failed for ${party.name}: ${it.localizedMessage}")
                        }
                    }
                } else {
                    addLog("Offline mode: Updated partner ${party.name} locally.")
                }
            } catch (e: Exception) {
                showToast("Failed to update party: ${e.localizedMessage}")
            }
        }
    }

    fun deleteParty(party: Party) {
        val user = currentUser.value
        if (user == null || user.role != UserRole.OWNER) {
            showToast("Access Denied: Only Owners can delete registered parties.")
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteParty(party)
                showToast("Deleted party: ${party.name}")
                updatePendingSyncCount()

                // Background Supabase delete if online
                if (_isOnline.value) {
                    launch {
                        val syncResult = authService.deletePartyFromSupabase(party.phone, party.type)
                        syncResult.onSuccess {
                            showToast("Deleted from Supabase")
                        }.onFailure {
                            Log.e("BillingViewModel", "Supabase delete failed", it)
                        }
                    }
                } else {
                    addLog("Offline mode: Deleted partner ${party.name} locally.")
                }
            } catch (e: Exception) {
                showToast("Failed to delete party: ${e.localizedMessage}")
            }
        }
    }

    fun syncPartiesWithSupabase() {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            val result = authService.fetchPartiesFromSupabase()
            result.onSuccess { remoteParties ->
                try {
                    val localList = parties.value
                    var addedCount = 0
                    var updatedCount = 0
                    remoteParties.forEach { remote ->
                        val matched = localList.find { it.phone == remote.phone && it.type == remote.type }
                        if (matched == null) {
                            repository.insertParty(remote)
                            addedCount++
                        } else {
                            val updated = matched.copy(
                                name = remote.name,
                                address = remote.address,
                                licenseNo = remote.licenseNo ?: matched.licenseNo,
                                bankDetails = remote.bankDetails ?: matched.bankDetails,
                                balance = remote.balance
                            )
                            repository.updateParty(updated)
                            updatedCount++
                        }
                    }
                    _actionState.value = ActionState.Success("Database synced with Supabase! Added $addedCount, updated $updatedCount partners.")
                    showToast("Database synced with Supabase")
                } catch (e: Exception) {
                    _actionState.value = ActionState.Error("Sync failed: ${e.localizedMessage}")
                }
            }.onFailure { exception ->
                _actionState.value = ActionState.Error(exception.localizedMessage ?: "Sync failed")
            }
        }
    }

    // CRUD - Commodities
    fun addCommodity(
        name: String,
        marketFeePercent: Double,
        commissionPercent: Double,
        initialStock: Int,
        currentRate: Double,
        category: String = "General",
        barcode: String? = null,
        gstPercent: Double = 0.0,
        hsnCode: String? = null,
        purchasePrice: Double = 0.0,
        sellingPrice: Double = 0.0
    ) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            _actionState.value = ActionState.Error("Access Denied: Staff role cannot manage crop catalog.")
            return
        }
        if (name.isBlank()) {
            _actionState.value = ActionState.Error("Commodity name is required.")
            return
        }
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                val newCommodity = Commodity(
                    name = name.trim(),
                    marketFeePercent = marketFeePercent,
                    commissionPercent = commissionPercent,
                    stockBags = initialStock,
                    currentRate = currentRate,
                    category = category.trim(),
                    barcode = barcode?.trim()?.ifBlank { null },
                    gstPercent = gstPercent,
                    hsnCode = hsnCode?.trim()?.ifBlank { null },
                    purchasePrice = purchasePrice,
                    sellingPrice = sellingPrice,
                    isSynced = false
                )
                val generatedId = repository.insertCommodity(newCommodity)
                _actionState.value = ActionState.Success("Commodity $name successfully added.")
                showToast("Added commodity: $name")
                updatePendingSyncCount()

                if (_isOnline.value) {
                    launch {
                        val finalComm = newCommodity.copy(id = generatedId.toInt())
                        val syncResult = authService.pushCommodityToSupabase(finalComm)
                        syncResult.onSuccess {
                            repository.updateCommoditySyncStatus(generatedId.toInt(), true)
                            updatePendingSyncCount()
                        }.onFailure {
                            Log.e("BillingViewModel", "Supabase commodity push failed", it)
                            addLog("Immediate commodity push failed for $name: ${it.localizedMessage}")
                        }
                    }
                } else {
                    addLog("Offline mode: Added crop $name locally.")
                }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to add commodity")
            }
        }
    }

    fun updateCommodity(commodity: Commodity) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            showToast("Access Denied: Staff role cannot update crop pricing/stocks.")
            return
        }
        viewModelScope.launch {
            try {
                val updatedCommodity = commodity.copy(isSynced = false)
                repository.updateCommodity(updatedCommodity)
                showToast("Updated commodity: ${commodity.name}")
                updatePendingSyncCount()

                if (_isOnline.value) {
                    launch {
                        val syncResult = authService.pushCommodityToSupabase(updatedCommodity)
                        syncResult.onSuccess {
                            repository.updateCommoditySyncStatus(updatedCommodity.id, true)
                            updatePendingSyncCount()
                        }.onFailure {
                            Log.e("BillingViewModel", "Supabase update push failed", it)
                            addLog("Immediate update push failed for ${commodity.name}: ${it.localizedMessage}")
                        }
                    }
                } else {
                    addLog("Offline mode: Updated crop ${commodity.name} locally.")
                }
            } catch (e: Exception) {
                showToast("Failed to update commodity: ${e.localizedMessage}")
            }
        }
    }

    fun deleteCommodity(commodity: Commodity) {
        val user = currentUser.value
        if (user == null || user.role != UserRole.OWNER) {
            showToast("Access Denied: Only Owners can delete crops from stock catalog.")
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteCommodity(commodity)
                showToast("Deleted commodity: ${commodity.name}")
                updatePendingSyncCount()

                if (_isOnline.value) {
                    launch {
                        val result = authService.deleteCommodityFromSupabase(commodity.name)
                        result.onFailure {
                            addLog("Delete commodity remote failed: ${it.localizedMessage}")
                        }
                    }
                } else {
                    addLog("Offline mode: Deleted crop ${commodity.name} locally.")
                }
            } catch (e: Exception) {
                showToast("Failed to delete commodity: ${e.localizedMessage}")
            }
        }
    }

    fun processInventoryChange(
        commodityId: Int,
        type: String, // "STOCK_IN", "STOCK_OUT", "PURCHASE_ENTRY", "ADJUST_STOCK"
        bagsDelta: Int, // positive to increase stock, negative to decrease stock
        totalWeight: Double,
        ratePerQtl: Double = 0.0,
        totalPrice: Double = 0.0,
        partyId: Int? = null,
        partyName: String? = null,
        remarks: String = "",
        referenceNo: String? = null
    ) {
        viewModelScope.launch {
            try {
                val currentCommodity = commodities.value.find { it.id == commodityId }
                if (currentCommodity == null) {
                    showToast("Commodity not found")
                    return@launch
                }

                repository.processInventoryChange(
                    commodityId = commodityId,
                    type = type,
                    bagsDelta = bagsDelta,
                    totalWeight = totalWeight,
                    ratePerQtl = ratePerQtl,
                    totalPrice = totalPrice,
                    partyId = partyId,
                    partyName = partyName,
                    remarks = remarks,
                    referenceNo = referenceNo
                )

                showToast("Stock change processed successfully!")
                updatePendingSyncCount()

                if (_isOnline.value) {
                    triggerBackgroundSync()
                } else {
                    addLog("Offline mode: Recorded stock change locally.")
                }
            } catch (e: Exception) {
                showToast("Failed to process stock: ${e.localizedMessage}")
            }
        }
    }

    fun syncInventoryWithSupabase() {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                // 1. Sync Commodities
                val commoditiesResult = authService.fetchCommoditiesFromSupabase()
                commoditiesResult.onSuccess { remoteCommodities ->
                    val localList = commodities.value
                    remoteCommodities.forEach { remote ->
                        val matched = localList.find { it.name == remote.name }
                        if (matched == null) {
                            repository.insertCommodity(remote)
                        } else {
                            val updated = matched.copy(
                                marketFeePercent = remote.marketFeePercent,
                                commissionPercent = remote.commissionPercent,
                                stockBags = remote.stockBags,
                                currentRate = remote.currentRate,
                                category = remote.category,
                                barcode = remote.barcode ?: matched.barcode,
                                gstPercent = remote.gstPercent,
                                hsnCode = remote.hsnCode ?: matched.hsnCode,
                                purchasePrice = remote.purchasePrice,
                                sellingPrice = remote.sellingPrice
                            )
                            repository.updateCommodity(updated)
                        }
                    }
                }

                // 2. Sync Inventory Transactions
                val transactionsResult = authService.fetchInventoryTransactionsFromSupabase()
                transactionsResult.onSuccess { remoteTransactions ->
                    val localList = inventoryTransactions.value
                    remoteTransactions.forEach { remote ->
                        val matched = localList.any { it.date == remote.date && it.commodityId == remote.commodityId }
                        if (!matched) {
                            val localComm = commodities.value.find { it.name == remote.commodityName }
                            val finalTx = if (localComm != null) remote.copy(commodityId = localComm.id) else remote
                            repository.insertInventoryTransactionDirectly(finalTx)
                        }
                    }
                }

                _actionState.value = ActionState.Success("Inventory synced successfully with Supabase!")
                showToast("Inventory synced with Supabase")
            } catch (e: Exception) {
                _actionState.value = ActionState.Error("Sync failed: ${e.localizedMessage}")
            }
        }
    }

    // Create Invoices / Bills (Allowed for Owner, Admin, and Staff)
    fun createBill(
        party: Party,
        commodity: Commodity,
        bagCount: Int,
        weight: Double,
        rate: Double,
        laborChargesPerBag: Double,
        transportCharges: Double,
        taxPercent: Double,
        discountAmount: Double = 0.0,
        roundOff: Double = 0.0,
        netAmount: Double = 0.0,
        isPaid: Boolean = false,
        paymentNotes: String = ""
    ) {
        if (bagCount <= 0 || weight <= 0.0 || rate <= 0.0) {
            _actionState.value = ActionState.Error("Please enter valid weight, bag count, and rate.")
            return
        }
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                val grossAmount = weight * rate
                val commissionAmount = grossAmount * (commodity.commissionPercent / 100.0)
                val marketFeeAmount = grossAmount * (commodity.marketFeePercent / 100.0)
                val laborChargesTotal = bagCount * laborChargesPerBag
                val taxAmount = grossAmount * (taxPercent / 100.0)

                val calculatedNetAmount = if (netAmount != 0.0) netAmount else {
                    if (party.type == "FARMER") {
                        // Farmer Patti: deductions from gross sales amount, subtract discount, add round off
                        grossAmount - commissionAmount - laborChargesTotal - transportCharges - marketFeeAmount - discountAmount + roundOff
                    } else {
                        // Trader Invoice: additions on top of gross purchase amount, subtract discount, add round off
                        grossAmount + commissionAmount + laborChargesTotal + transportCharges + marketFeeAmount + taxAmount - discountAmount + roundOff
                    }
                }

                val bill = Bill(
                    billNumber = "", // empty to trigger auto-calculation
                    partyId = party.id,
                    partyName = party.name,
                    partyType = party.type,
                    commodityId = commodity.id,
                    commodityName = commodity.name,
                    bagCount = bagCount,
                    weight = weight,
                    rate = rate,
                    grossAmount = grossAmount,
                    commissionAmount = commissionAmount,
                    laborCharges = laborChargesTotal,
                    transportCharges = transportCharges,
                    marketFeeAmount = marketFeeAmount,
                    taxAmount = taxAmount,
                    discountAmount = discountAmount,
                    roundOff = roundOff,
                    netAmount = calculatedNetAmount,
                    isPaid = isPaid,
                    paymentNotes = paymentNotes,
                    isSynced = false
                )

                repository.processBill(bill)
                _actionState.value = ActionState.Success("Bill created successfully.")
                showToast("Invoice processed for ${party.name}")
                updatePendingSyncCount()
                if (_isOnline.value) {
                    triggerBackgroundSync()
                } else {
                    addLog("Offline mode: Created bill #${bill.billNumber} locally.")
                }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to process bill")
            }
        }
    }

    fun deleteBill(billId: Int) {
        val user = currentUser.value
        if (user == null || user.role != UserRole.OWNER) {
            showToast("Access Denied: Only Owners can void/delete market bills.")
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteBill(billId)
                showToast("Bill deleted & accounts updated.")
            } catch (e: Exception) {
                showToast("Failed to delete bill: ${e.localizedMessage}")
            }
        }
    }

    // Ledger Payment Recording (Allowed for Owner and Admin)
    fun recordPayment(party: Party, amount: Double, paymentMethod: String, notes: String) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            _actionState.value = ActionState.Error("Access Denied: Staff role cannot record ledger payments.")
            return
        }
        if (amount <= 0.0) {
            _actionState.value = ActionState.Error("Amount must be greater than zero.")
            return
        }
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                // If it is a Farmer, we pay them (isPaymentMadeToFarmer = true)
                // If it is a Trader, we receive money from them (isPaymentMadeToFarmer = false)
                val isPaymentMade = (party.type == "FARMER")
                repository.recordPayment(
                    partyId = party.id,
                    partyType = party.type,
                    amount = amount,
                    isPaymentMadeToFarmer = isPaymentMade,
                    paymentMethod = paymentMethod,
                    notes = notes.trim()
                )
                _actionState.value = ActionState.Success("Payment recorded successfully.")
                val message = if (isPaymentMade) "Paid $amount to ${party.name}" else "Received $amount from ${party.name}"
                showToast(message)
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to record payment")
            }
        }
    }

    // Expense recording and deletion operations
    fun recordExpense(amount: Double, category: String, description: String, date: Long = System.currentTimeMillis()) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            _actionState.value = ActionState.Error("Access Denied: Staff role cannot record shop expenses.")
            return
        }
        if (amount <= 0.0) {
            _actionState.value = ActionState.Error("Amount must be greater than zero.")
            return
        }
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                val expense = Expense(
                    amount = amount,
                    category = category.trim(),
                    description = description.trim(),
                    date = date,
                    isSynced = false
                )
                repository.insertExpense(expense)
                _actionState.value = ActionState.Success("Expense recorded successfully.")
                showToast("Expense of ₹$amount under $category recorded.")
                updatePendingSyncCount()
                if (_isOnline.value) {
                    triggerBackgroundSync()
                } else {
                    addLog("Offline mode: Recorded expense locally.")
                }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to record expense")
            }
        }
    }

    fun deleteExpense(id: Int) {
        val user = currentUser.value
        if (user == null || user.role == UserRole.STAFF) {
            _actionState.value = ActionState.Error("Access Denied: Staff role cannot delete shop expenses.")
            return
        }
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                repository.deleteExpenseById(id)
                _actionState.value = ActionState.Success("Expense deleted successfully.")
                showToast("Expense entry deleted.")
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to delete expense")
            }
        }
    }

    // Seed initial demo data if database is empty
    fun seedDemoDataIfNeeded() {
        viewModelScope.launch {
            if (parties.value.isEmpty()) {
                val demoParties = listOf(
                    Party(name = "Rajesh Patel (Farmer)", type = "FARMER", phone = "9876543210", address = "Village Anand, Gujarat", balance = 0.0),
                    Party(name = "Anil Sharma (Farmer)", type = "FARMER", phone = "8765432109", address = "Village Bardoli, Gujarat", balance = 0.0),
                    Party(name = "Karan Traders (Buyer)", type = "TRADER", phone = "7654321098", address = "APMC Yard Shop No. 12, Ahmedabad", licenseNo = "APMC-AHD-T552", balance = 0.0),
                    Party(name = "Sardar Agro Foods (Buyer)", type = "TRADER", phone = "9123456789", address = "APMC Yard Shop No. 44, Ahmedabad", licenseNo = "APMC-AHD-T812", balance = 0.0)
                )
                demoParties.forEach { repository.insertParty(it) }

                val demoCommodities = listOf(
                    Commodity(name = "Wheat (Lokwan)", marketFeePercent = 1.0, commissionPercent = 5.0, stockBags = 250, currentRate = 2400.0),
                    Commodity(name = "Basmati Rice", marketFeePercent = 1.5, commissionPercent = 6.0, stockBags = 120, currentRate = 6500.0),
                    Commodity(name = "Cotton (Kapas)", marketFeePercent = 1.0, commissionPercent = 4.5, stockBags = 80, currentRate = 7200.0),
                    Commodity(name = "Red Onion", marketFeePercent = 2.0, commissionPercent = 6.0, stockBags = 400, currentRate = 1800.0)
                )
                demoCommodities.forEach { repository.insertCommodity(it) }
            }
        }
    }

    fun restoreBackup(
        partiesList: List<Party>,
        commoditiesList: List<Commodity>,
        billsList: List<Bill>,
        ledgerEntriesList: List<LedgerEntry>,
        transactionsList: List<InventoryTransaction>,
        expensesList: List<Expense>
    ) {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                repository.clearAllTables()
                partiesList.forEach { repository.insertParty(it) }
                commoditiesList.forEach { repository.insertCommodity(it) }
                billsList.forEach { repository.insertBillDirectly(it) }
                ledgerEntriesList.forEach { repository.insertLedgerEntryDirectly(it) }
                transactionsList.forEach { repository.insertInventoryTransactionDirectly(it) }
                expensesList.forEach { repository.insertExpense(it) }
                _actionState.value = ActionState.Success("Database restored successfully!")
                showToast("Restore Successful")
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to restore database")
                showToast("Restore Failed")
            }
        }
    }

    fun wipeAllData() {
        _actionState.value = ActionState.Loading
        viewModelScope.launch {
            try {
                repository.clearAllTables()
                _actionState.value = ActionState.Success("All database tables wiped successfully.")
                showToast("All data cleared")
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.localizedMessage ?: "Failed to wipe database")
            }
        }
    }
}
