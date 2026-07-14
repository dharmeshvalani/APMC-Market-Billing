package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserRole
import com.example.ui.ActionState
import com.example.ui.BillingViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: BillingViewModel) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 720

    var authMode by remember { mutableStateOf(0) } // 0 = Login, 1 = Register, 2 = Forgot Password, 3 = Verify OTP

    // Form states
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.STAFF) }

    // Toggle password visibility
    var passwordVisible by remember { mutableStateOf(false) }

    // Toggle Supabase Config expansion
    var configExpanded by remember { mutableStateOf(false) }
    val savedUrl by viewModel.supabaseUrl.collectAsState()
    val savedKey by viewModel.supabaseAnonKey.collectAsState()

    var configUrlInput by remember { mutableStateOf(savedUrl) }
    var configKeyInput by remember { mutableStateOf(savedKey) }

    // Login tab (0 = Email, 1 = Mobile OTP)
    var loginTab by remember { mutableIntStateOf(0) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val actionState by viewModel.actionState.collectAsState()

    // Resend Timer for OTP
    var countdownSeconds by remember { mutableIntStateOf(30) }
    var isTimerActive by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerActive, countdownSeconds) {
        if (isTimerActive && countdownSeconds > 0) {
            delay(1000)
            countdownSeconds--
        } else if (countdownSeconds == 0) {
            isTimerActive = false
        }
    }

    // Reset fields on mode changes
    LaunchedEffect(authMode) {
        viewModel.clearActionState()
        otpCode = ""
        if (authMode == 3) {
            countdownSeconds = 30
            isTimerActive = true
        }
    }

    // Sync input states if saved keys load
    LaunchedEffect(savedUrl, savedKey) {
        if (configUrlInput.isEmpty()) configUrlInput = savedUrl
        if (configKeyInput.isEmpty()) configKeyInput = savedKey
    }

    val brandGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )

    @Composable
    fun HeaderSection() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hexagon-shaped or rounded elegant symbol logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(brandGradient)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = "APMC Market Billing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "APMC Market Billing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Market Yard Ledger & Accounting Suite",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun SupabaseConfigPanel() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (savedUrl.isNotBlank())
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { configExpanded = !configExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (savedUrl.isNotBlank()) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                            contentDescription = "Cloud Status",
                            tint = if (savedUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Supabase Cloud Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (savedUrl.isNotBlank()) "Connected to cloud database" else "Running in Offline/Simulated mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { configExpanded = !configExpanded }) {
                        Icon(
                            imageVector = if (configExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle config"
                        )
                    }
                }

                AnimatedVisibility(visible = configExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "To sync your APMC invoices dynamically, configure your private Supabase credentials below. Leave empty to activate the built-in sandbox mock engine.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = configUrlInput,
                            onValueChange = { configUrlInput = it },
                            label = { Text("Supabase URL (https://...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = configKeyInput,
                            onValueChange = { configKeyInput = it },
                            label = { Text("Supabase Anonymous key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                configUrlInput = ""
                                configKeyInput = ""
                                viewModel.saveConfig("", "")
                            }) {
                                Text("Reset to Local")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.saveConfig(configUrlInput, configKeyInput)
                                    configExpanded = false
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save Configuration")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun RoleCardSelector(role: UserRole, title: String, subtitle: String, icon: ImageVector) {
        val isSelected = selectedRole == role
        Card(
            onClick = { selectedRole = role },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(
                    selected = isSelected,
                    onClick = { selectedRole = role }
                )
            }
        }
    }

    @Composable
    fun EmailLoginFields() {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password") },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                if (email.isNotBlank() && password.isNotBlank()) {
                    viewModel.loginWithEmail(email, password)
                }
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
    }

    @Composable
    fun MobileLoginFields() {
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Mobile Number with Country Code") },
            placeholder = { Text("+91 98765 43210") },
            leadingIcon = { Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Mobile") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                if (phone.isNotBlank()) {
                    viewModel.requestOtp(phone) {
                        authMode = 3
                    }
                }
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )
    }

    @Composable
    fun ErrorDisplay() {
        if (actionState is ActionState.Error) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = (actionState as ActionState.Error).errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    @Composable
    fun AuthFormContainer(scrollable: Boolean = true) {
        val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .then(scrollModifier),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (authMode) {
                    0 -> { // LOGIN MODE
                        Text(
                            text = "Login to Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        TabRow(
                            selectedTabIndex = loginTab,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        ) {
                            Tab(
                                selected = loginTab == 0,
                                onClick = { loginTab = 0 },
                                text = { Text("Email Login") }
                            )
                            Tab(
                                selected = loginTab == 1,
                                onClick = { loginTab = 1 },
                                text = { Text("Mobile OTP") }
                            )
                        }

                        ErrorDisplay()

                        if (loginTab == 0) {
                            EmailLoginFields()
                        } else {
                            MobileLoginFields()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { authMode = 2 }) {
                                Text("Forgot Password?")
                            }
                        }

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (loginTab == 0) {
                                    viewModel.loginWithEmail(email, password)
                                } else {
                                    viewModel.requestOtp(phone) {
                                        authMode = 3
                                    }
                                }
                            },
                            enabled = if (loginTab == 0) email.isNotBlank() && password.isNotBlank() else phone.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Login, contentDescription = "Login")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (loginTab == 0) "Sign In" else "Send Login OTP")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("New to APMC Yard?", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { authMode = 1 }) {
                                Text("Create Account")
                            }
                        }

                        // Help instructions for sandbox
                        if (savedUrl.isBlank()) {
                            HorizontalDivider()
                            Text(
                                text = "Sandbox Accounts:\n• Owner: owner@apmc.com / owner123\n• Admin: admin@apmc.com / admin123\n• Staff: staff@apmc.com / staff123",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    1 -> { // REGISTER MODE
                        Text(
                            text = "Register Merchant Entity",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        ErrorDisplay()

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Mobile Phone") },
                            leadingIcon = { Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Phone") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Text(
                            text = "Choose Business Role *",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        RoleCardSelector(
                            role = UserRole.OWNER,
                            title = "Owner / Commission Agent",
                            subtitle = "Full ledger control, database resets, deleting bills/entities.",
                            icon = Icons.Default.VerifiedUser
                        )

                        RoleCardSelector(
                            role = UserRole.ADMIN,
                            title = "Admin Operator",
                            subtitle = "Add & edit bills, manage stocks & directory, no deletion access.",
                            icon = Icons.Default.SupervisedUserCircle
                        )

                        RoleCardSelector(
                            role = UserRole.STAFF,
                            title = "Staff Billing Clerk",
                            subtitle = "Generate fresh invoices, view stocks, zero management access.",
                            icon = Icons.Default.Badge
                        )

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.registerUser(email, password, name, phone, selectedRole)
                            },
                            enabled = email.isNotBlank() && password.isNotBlank() && name.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AppRegistration, contentDescription = "Register")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Up & Login")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Already registered?", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { authMode = 0 }) {
                                Text("Back to Login")
                            }
                        }
                    }

                    2 -> { // FORGOT PASSWORD
                        Text(
                            text = "Reset Password",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Enter your registered email address below, and we will send you a secure password recovery email.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ErrorDisplay()

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Registered Email") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.recoverPassword(email)
                            },
                            enabled = email.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Send Reset")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Recovery Link")
                        }

                        TextButton(
                            onClick = { authMode = 0 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Login")
                        }
                    }

                    3 -> { // VERIFY SMS OTP
                        Text(
                            text = "SMS Verification",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "A secure verification code has been dispatched to $phone. Enter the code below to complete authorization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ErrorDisplay()

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text("6-Digit OTP Code") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Key, contentDescription = "OTP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboardController?.hide()
                                if (otpCode.isNotBlank()) {
                                    viewModel.verifyOtp(phone, otpCode)
                                }
                            }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Countdown resend timer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTimerActive) "Resend code in ${countdownSeconds}s" else "Did not get code?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    countdownSeconds = 30
                                    isTimerActive = true
                                    viewModel.requestOtp(phone) {}
                                },
                                enabled = !isTimerActive
                            ) {
                                Text("Resend OTP")
                            }
                        }

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.verifyOtp(phone, otpCode)
                            },
                            enabled = otpCode.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.HowToReg, contentDescription = "Verify")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify Code & Log In")
                        }

                        TextButton(
                            onClick = { authMode = 0 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Change Mobile Number")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isTablet) {
                // Wide split screen for tablets/desktop
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        HeaderSection()
                        SupabaseConfigPanel()
                    }
                    Box(modifier = Modifier.weight(1.1f)) {
                        AuthFormContainer()
                    }
                }
            } else {
                // Portrait single column for mobile phones
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HeaderSection()
                    AuthFormContainer(scrollable = false)
                    SupabaseConfigPanel()
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
