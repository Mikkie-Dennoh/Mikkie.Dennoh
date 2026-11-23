package co.tawipay.topupdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class MenuEntry(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    ownerName: String,
    businessName: String,
    isAccessibilityEnabled: Boolean,
    runStatus: String,
    executionCode: String,
    executionPin: String,
    executionMode: ExecutionMode,
    initialSellingRate: String,
    isDashboardPaired: Boolean,
    dashboardStatus: String,
    initialDashboardUrl: String,
    onOpenAccessibilitySettings: () -> Unit,
    onEditExecutionSettings: () -> Unit,
    onRunUssd: (String, String) -> Unit,
    onStopUssd: () -> Unit,
    onSaveSellingRate: (String) -> Unit,
    onPairDashboard: (String, String) -> Unit,
    onDisconnectDashboard: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedPage by rememberSaveable { mutableStateOf("Dashboard") }
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var sellingRate by remember(initialSellingRate) { mutableStateOf(initialSellingRate) }
    var showConfirmation by remember { mutableStateOf(false) }
    var showRateEditor by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    var dashboardUrl by rememberSaveable { mutableStateOf(initialDashboardUrl) }
    var pairingCode by rememberSaveable { mutableStateOf("") }

    val mainItems = listOf(
        MenuEntry("Dashboard", Icons.Default.Home),
        MenuEntry("Transactions", Icons.Default.ReceiptLong),
        MenuEntry("Dial pad", Icons.Default.Phone),
        MenuEntry("Clients", Icons.Default.AccountCircle),
        MenuEntry("Statistics", Icons.Default.ReceiptLong),
        MenuEntry("Subscription", Icons.Default.Settings),
        MenuEntry("Schedule", Icons.Default.ReceiptLong),
        MenuEntry("M-PESA messages", Icons.Default.ReceiptLong),
        MenuEntry("Connect website", Icons.Default.Settings),
    )
    val settingsItems = listOf("Pricing & rate", "Execution USSD & PIN")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
                    Text(ownerName.ifBlank { "Your profile" }, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(businessName.ifBlank { "Business account" }, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f), style = MaterialTheme.typography.bodyMedium)
                    Text(if (isAccessibilityEnabled && executionMode == ExecutionMode.ADVANCED) "Automation ready" else "Automation stopped", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                }
                Text("MAIN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp))
                mainItems.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry.title) },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        selected = selectedPage == entry.title,
                        onClick = {
                            selectedPage = entry.title
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
                Text("SETTINGS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
                settingsItems.forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(entry) },
                        icon = { Icon(if (entry == "Execution USSD & PIN") Icons.Default.Phone else Icons.Default.Settings, contentDescription = null) },
                        selected = selectedPage == entry,
                        onClick = {
                            selectedPage = entry
                            scope.launch { drawerState.close() }
                            if (entry == "Execution USSD & PIN") onEditExecutionSettings()
                            if (entry == "Pricing & rate") showRateEditor = true
                        },
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (selectedPage == "Dashboard") "Good evening ${ownerName.substringBefore(' ')}" else selectedPage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (selectedPage == "Dashboard") Text(businessName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                        }
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Open navigation") } },
                    actions = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.padding(end = 16.dp).size(28.dp)) },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary),
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            if (selectedPage == "Dashboard") {
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!isAccessibilityEnabled && executionMode == ExecutionMode.ADVANCED) {
                        Card(
                            onClick = onOpenAccessibilitySettings,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text("Accessibility is OFF: USSD cannot run. Tap to turn it on.", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(listOf("Airtime balance" to "Ksh --", "Airtime sent" to "Ksh 0", "Automation" to if (executionMode == ExecutionMode.SIMPLE || isAccessibilityEnabled) "Ready" else "Stopped")) { (label, value) ->
                            MetricCard(label, value)
                        }
                    }

                    if (selectedPage == "Dashboard") {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Run airtime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("Enter the recipient and amount, then review before sending.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { selectedPage = "Pricing & rate"; showRateEditor = true }) { Text("Rate ${sellingRate}%") }
                                }
                                OutlinedTextField(
                                    value = recipient,
                                    onValueChange = { recipient = it.filter(Char::isDigit).take(12); formError = "" },
                                    label = { Text("Recipient phone number") },
                                    placeholder = { Text("07xx xxx xxx") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = amount,
                                    onValueChange = { amount = it.filter(Char::isDigit).take(6); formError = "" },
                                    label = { Text("Airtime amount (KES)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (amount.toIntOrNull() != null) {
                                    val customerPays = amount.toInt() * (sellingRate.toDoubleOrNull() ?: 91.9) / 100
                                    Text("Customer pays approximately KES ${String.format(java.util.Locale.US, "%.2f", customerPays)} at a ${sellingRate}% selling rate.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                if (formError.isNotBlank()) Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = {
                                        val validationError = when {
                                            recipient.length < 9 -> "Enter a valid recipient phone number."
                                            (amount.toIntOrNull() ?: 0) !in 20..10000 -> "Enter an airtime amount between KES 20 and 10,000."
                                            else -> ""
                                        }
                                        formError = validationError
                                        if (validationError.isEmpty()) showConfirmation = true
                                    },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("Review airtime run", fontWeight = FontWeight.Bold) }
                                if (runStatus.isNotBlank()) {
                                    Text(runStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    if (executionMode == ExecutionMode.ADVANCED && runStatus.startsWith("USSD started")) {
                                        TextButton(onClick = onStopUssd) { Text("Stop automatic replies") }
                                    }
                                }
                            }
                        }
                    } else {
                        FeaturePage(selectedPage, onEditExecutionSettings, onOpenAccessibilitySettings)
                    }

                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (selectedPage == "Dashboard") "Recent airtime runs" else selectedPage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
                            Text("No runs recorded yet", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Transaction history and dashboard sync are not connected in this initial Android build.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (selectedPage == "Connect website") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Connect TawiPay dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Pair this phone with your signed-in website. The daily rate and phone-reported USSD run statuses will sync while this app is open.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    OutlinedTextField(value = dashboardUrl, onValueChange = { dashboardUrl = it }, label = { Text("Dashboard HTTPS address") }, placeholder = { Text("https://your-tawipay-site.vercel.app") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !isDashboardPaired)
                                    if (!isDashboardPaired) {
                                        OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it.filter(Char::isDigit).take(8) }, label = { Text("One-time 8-digit pairing code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                                        Button(onClick = { onPairDashboard(dashboardUrl, pairingCode) }, enabled = dashboardUrl.startsWith("https://") && pairingCode.length == 8, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { Text("Pair this phone", fontWeight = FontWeight.Bold) }
                                    } else {
                                        Text("Connected. Your rate and phone activity sync securely.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        TextButton(onClick = onDisconnectDashboard) { Text("Disconnect dashboard") }
                                    }
                                    if (dashboardStatus.isNotBlank()) Text(dashboardStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Text("The phone keeps USSD execution and its saved PIN local. Dashboard pairing does not read SMS or trigger USSD automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                            }
                        }
                    } else {
                        FeaturePage(selectedPage, onEditExecutionSettings, onOpenAccessibilitySettings)
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        val sequence = buildUssdSequence(executionCode, recipient, amount, executionPin)
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Confirm airtime run", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Review these details before the phone sends a USSD request.")
                    ConfirmationItem("Recipient", recipient)
                    ConfirmationItem("Airtime", "KES $amount")
                    if (sequence == null) Text("USSD template is invalid. Update it in Execution USSD & PIN settings.", color = MaterialTheme.colorScheme.error)
                    else Text("Your Android default SIM will start the configured Safaricom USSD flow. Set Safaricom as the default SIM before running. The execution PIN is stored encrypted on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = sequence != null, onClick = {
                    showConfirmation = false
                    onRunUssd(recipient, amount)
                }) { Text("Run USSD") }
            },
            dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("Cancel") } },
        )
    }

    if (showRateEditor) {
        AlertDialog(
            onDismissRequest = { showRateEditor = false },
            title = { Text("Set your selling rate", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Airtime = amount paid ÷ rate. Enter the rate you sell airtime at.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = sellingRate, onValueChange = { sellingRate = it.filter { char -> char.isDigit() || char == '.' }.take(5) }, label = { Text("Selling rate %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { sellingRate.toDoubleOrNull()?.takeIf { it in 1.0..100.0 }?.let { onSaveSellingRate(sellingRate) }; showRateEditor = false }) { Text("Save rate") } },
            dismissButton = { TextButton(onClick = { showRateEditor = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConfirmationItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.size(width = 150.dp, height = 112.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FeaturePage(page: String, onEditExecutionSettings: () -> Unit, onOpenAccessibilitySettings: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(page, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val detail = when (page) {
                "Transactions" -> "Phone-reported USSD run records sync to your TawiPay dashboard when this phone is paired."
                "Dial pad" -> "Use the configured USSD template from the dashboard to start a run."
                "Clients" -> "Saved recipients can be added here in a future release."
                "Statistics" -> "Run totals will appear after airtime transactions are recorded."
                "Subscription" -> "Subscription details are not connected yet."
                "Schedule" -> "Scheduled USSD runs are not enabled. Each run currently requires your confirmation."
                "M-PESA messages" -> "This app does not read SMS or M-PESA messages."
                "Pricing & rate" -> "Update the selling rate from the dashboard."
                else -> "Manage the USSD code and execution PIN for this device."
            }
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (page == "Execution USSD & PIN") Button(onClick = onEditExecutionSettings) { Text("Open setup") }
            if (page == "Dial pad") Button(onClick = onOpenAccessibilitySettings) { Text("Accessibility settings") }
        }
    }
}
