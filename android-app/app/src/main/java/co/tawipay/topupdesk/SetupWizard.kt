package co.tawipay.topupdesk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SetupWizard(
    isAccessibilityEnabled: Boolean,
    initialStep: Int = 0,
    initialOwnerName: String = "",
    initialBusinessName: String = "",
    initialOwnerPhone: String = "",
    initialExecutionCode: String = AppPreferences.DEFAULT_EXECUTION_CODE,
    initialMode: ExecutionMode = ExecutionMode.ADVANCED,
    onOpenAccessibilitySettings: () -> Unit,
    onComplete: (String, String, String, String, ExecutionMode, String) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(initialStep.coerceIn(0, 3)) }
    var ownerName by rememberSaveable { mutableStateOf(initialOwnerName) }
    var businessName by rememberSaveable { mutableStateOf(initialBusinessName) }
    var ownerPhone by rememberSaveable { mutableStateOf(initialOwnerPhone) }
    var executionCode by rememberSaveable { mutableStateOf(initialExecutionCode) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var revealCode by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

    val titles = listOf("Your profile", "USSD execution", "Execution Pin", "Review setup")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text("Setup", modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1} / 4", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.primary)) {
                    Spacer(Modifier.fillMaxWidth((step + 1) / 4f).height(5.dp).background(MaterialTheme.colorScheme.primaryContainer))
                }
                Text("Setup · ${titles[step]}", modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    TextButton(onClick = { error = ""; step-- }, modifier = Modifier.weight(1f).height(54.dp)) { Text("Back") }
                }
                Button(
                    onClick = {
                        error = when (step) {
                            0 -> when {
                                ownerName.isBlank() || businessName.isBlank() -> "Enter your name and business name."
                                ownerPhone.filter(Char::isDigit).length < 9 -> "Enter a valid Kenyan phone number."
                                else -> ""
                            }
                            1 -> when {
                                !executionCode.contains("Pn", ignoreCase = true) || !executionCode.contains("Am", ignoreCase = true) || !executionCode.contains("Pin", ignoreCase = true) -> "Include the Pn, Am, and Pin placeholders in your USSD template."
                                !executionCode.trim().startsWith("*") || !executionCode.trim().endsWith("#") -> "USSD code must start with * and end with #."
                                else -> ""
                            }
                            2 -> when {
                                pin.length < 4 -> "Execution PIN must be at least 4 digits."
                                pin.any { !it.isDigit() } -> "Use digits only for the execution PIN."
                                pin != confirmPin -> "PIN entries do not match."
                                else -> ""
                            }
                            else -> ""
                        }
                        if (error.isEmpty()) {
                            if (step < 3) step++
                            else onComplete(ownerName, businessName, ownerPhone, executionCode, mode, pin)
                        }
                    },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (step == 3) "Finish setup" else "Next", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Step ${step + 1} of 4", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (step) {
                        0 -> {
                            Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(ownerName, { ownerName = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(businessName, { businessName = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(ownerPhone, { ownerPhone = it }, label = { Text("Phone number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                        }
                        1 -> {
                            Text("Execution mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            ExecutionModeOption(
                                title = "Simple",
                                description = "Sends the complete USSD code once through Android. Best for a single complete code.",
                                selected = mode == ExecutionMode.SIMPLE,
                                onClick = { mode = ExecutionMode.SIMPLE },
                            )
                            ExecutionModeOption(
                                title = "Advanced",
                                description = "Starts the first USSD code, then enters the configured menu replies. Requires Accessibility access.",
                                selected = mode == ExecutionMode.ADVANCED,
                                onClick = { mode = ExecutionMode.ADVANCED },
                            )
                            if (mode == ExecutionMode.ADVANCED) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Accessibility service: ${if (isAccessibilityEnabled) "ON" else "OFF"}", color = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = onOpenAccessibilitySettings) { Text(if (isAccessibilityEnabled) "Manage" else "Turn on") }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = executionCode,
                                    onValueChange = { executionCode = it },
                                    label = { Text("Merchant execution USSD") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    visualTransformation = if (revealCode) VisualTransformation.None else PasswordVisualTransformation(),
                                )
                                IconButton(onClick = { revealCode = !revealCode }) {
                                    Icon(if (revealCode) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (revealCode) "Hide USSD code" else "Show USSD code", modifier = Modifier.size(22.dp))
                                }
                            }
                            Text("Use Pn for recipient phone, Am for airtime amount, and Pin for the execution PIN.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        2 -> {
                            Text("Execution Pin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(pin, { pin = it }, label = { Text("Execution Pin") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(confirmPin, { confirmPin = it }, label = { Text("Confirm Pin") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                            Text("Your PIN is encrypted on this phone and is only used after you confirm an airtime run.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        else -> {
                            Text("Review setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SummaryRow("Business", businessName)
                            SummaryRow("Account phone", ownerPhone)
                            SummaryRow("Execution mode", if (mode == ExecutionMode.ADVANCED) "Advanced" else "Simple")
                            SummaryRow("USSD template", executionCode)
                            Text("Airtime runs are started manually from the dashboard. Review the recipient and amount before each run.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExecutionModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
