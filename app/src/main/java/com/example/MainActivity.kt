package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.AppDatabase
import com.example.data.BillingRepository
import com.example.ui.ActionState
import com.example.ui.BillingViewModel
import com.example.ui.components.AppResponsiveShell
import com.example.ui.components.LoadingSpinner
import com.example.ui.components.ToastNotification
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import com.example.supabase.SupabaseClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
          // Initialize Supabase
        val supabase = SupabaseClient.client

        setContent {
            App()
        }
    }
}

        // Initialize Room Database, Repository, AuthService, and ViewModel
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = BillingRepository(database)
        val authService = com.example.data.AuthService(applicationContext)
        val viewModel = BillingViewModel(repository, authService)

        // Seed initial agricultural commodities and parties for high-fidelity out-of-the-box viewing
        viewModel.seedDemoDataIfNeeded()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val actionState by viewModel.actionState.collectAsState()
            val currentUser by viewModel.currentUser.collectAsState()
            val activeConflictLocal by viewModel.activeConflictEntity.collectAsState()
            val activeConflictRemote by viewModel.activeConflictRemote.collectAsState()
            val isOnline by viewModel.isOnline.collectAsState()
            val isSyncing by viewModel.isSyncing.collectAsState()
            val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()

            var activeToastMessage by remember { mutableStateOf<String?>(null) }

            // Listen to floating Toast event triggers
            LaunchedEffect(viewModel.toastMessage) {
                viewModel.toastMessage.collectLatest { msg ->
                    activeToastMessage = msg
                }
            }

            // Auto-dismiss Toast notifications after 3 seconds
            LaunchedEffect(activeToastMessage) {
                activeToastMessage?.let {
                    kotlinx.coroutines.delay(3000)
                    activeToastMessage = null
                }
            }

            MyApplicationTheme(darkTheme = isDarkMode) {
                AppResponsiveShell(
                    currentScreen = currentScreen,
                    onNavigate = { route -> viewModel.navigateTo(route) },
                    isDarkMode = isDarkMode,
                    onToggleTheme = { viewModel.toggleTheme() },
                    currentUser = currentUser,
                    onLogout = { viewModel.logout() },
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    pendingSyncCount = pendingSyncCount
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Routing Navigation Swapper
                        Crossfade(targetState = currentScreen, label = "routing_crossfade") { screen ->
                            when (screen) {
                                "auth" -> AuthScreen(viewModel = viewModel)
                                "setup_wizard" -> ShopSetupWizardScreen(viewModel = viewModel)
                                "settings" -> SettingsScreen(viewModel = viewModel)
                                "dashboard" -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToBilling = { viewModel.navigateTo("new_bill") },
                                    onNavigateToParties = { viewModel.navigateTo("parties") },
                                    onNavigateToStocks = { viewModel.navigateTo("commodities") },
                                    onNavigateToPayments = { viewModel.navigateTo("payments") }
                                )
                                "new_bill" -> NewBillScreen(
                                    viewModel = viewModel,
                                    onBillCreated = { viewModel.navigateTo("invoice_history") }
                                )
                                "invoice_history" -> InvoiceHistoryScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.navigateTo("dashboard") }
                                )
                                "parties" -> PartiesScreen(viewModel = viewModel)
                                "customers" -> CustomerManagementScreen(viewModel = viewModel)
                                "commodities" -> CommoditiesScreen(viewModel = viewModel)
                                "inventory" -> InventoryManagementScreen(viewModel = viewModel)
                                "ledger" -> LedgerScreen(viewModel = viewModel)
                                "payments" -> PaymentsScreen(viewModel = viewModel)
                                "reports" -> ReportsScreen(viewModel = viewModel)
                                else -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToBilling = { viewModel.navigateTo("new_bill") },
                                    onNavigateToParties = { viewModel.navigateTo("parties") },
                                    onNavigateToStocks = { viewModel.navigateTo("commodities") }
                                )
                            }
                        }

                        // Global Loading state overlays
                        if (actionState is ActionState.Loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Box(modifier = Modifier.padding(24.dp)) {
                                        LoadingSpinner()
                                    }
                                }
                            }
                        }

                        // Floating Toast notification banner overlay
                        activeToastMessage?.let { msg ->
                            ToastNotification(
                                message = msg,
                                onDismiss = { activeToastMessage = null }
                            )
                        }

                        // Conflict Resolution Overlay Dialog
                        if (activeConflictLocal != null && activeConflictRemote != null) {
                            com.example.ui.components.ConflictResolutionDialog(
                                localEntity = activeConflictLocal,
                                remoteEntity = activeConflictRemote,
                                onResolve = { useLocal ->
                                    viewModel.resolveConflictWithDecision(useLocal)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
