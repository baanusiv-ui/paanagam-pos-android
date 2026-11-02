package `in`.paanagam.business

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.whatsAppSettingsStore by preferencesDataStore(name = "whatsapp_settings")

private object WhatsAppPreferenceKeys {
    val provider = stringPreferencesKey("provider")
    val mode = stringPreferencesKey("connection_mode")
    val receipt = booleanPreferencesKey("auto_receipt")
    val dailyReport = booleanPreferencesKey("auto_daily_report")
    val stockAlerts = booleanPreferencesKey("auto_stock_alerts")
    val supplierOrders = booleanPreferencesKey("auto_supplier_orders")
    val loyalty = booleanPreferencesKey("auto_loyalty")
}

@Composable
fun WhatsAppSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by context.whatsAppSettingsStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .collectAsState(initial = emptyPreferences())

    val provider = preferences[WhatsAppPreferenceKeys.provider] ?: "Choose later"
    val mode = preferences[WhatsAppPreferenceKeys.mode] ?: "Business App Coexistence"
    val receipt = preferences[WhatsAppPreferenceKeys.receipt] ?: false
    val dailyReport = preferences[WhatsAppPreferenceKeys.dailyReport] ?: false
    val stockAlerts = preferences[WhatsAppPreferenceKeys.stockAlerts] ?: false
    val supplierOrders = preferences[WhatsAppPreferenceKeys.supplierOrders] ?: false
    val loyalty = preferences[WhatsAppPreferenceKeys.loyalty] ?: false

    var providerMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }

    fun saveBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch { context.whatsAppSettingsStore.edit { it[key] = value } }
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("WhatsApp integration", color = Color(0xFFDDB36C), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Configure the provider and message workflows. Nothing is connected or sent until an administrator completes onboarding and enables the integration.", color = Color(0xFFFFF4DF))

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Connection status", color = Color(0xFFFFF4DF), fontWeight = FontWeight.SemiBold)
                            Text("Not connected", color = Color(0xFFDDB36C))
                        }
                        Text("DISABLED", color = Color(0xFFDDB36C), fontWeight = FontWeight.Bold)
                    }
                    Text("Automatic sending is locked until a supported provider connection is verified. Your existing WhatsApp Business number will not be migrated by this screen.", color = Color(0xFFFFF4DF), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable automatic sending", color = Color(0xFFFFF4DF), modifier = Modifier.weight(1f))
                        Switch(checked = false, onCheckedChange = {}, enabled = false)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection setup", color = Color(0xFFDDB36C), fontWeight = FontWeight.Bold)
                    Text("Provider", color = Color(0xFFFFF4DF))
                    OutlinedButton(onClick = { providerMenu = true }) { Text(provider) }
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        listOf("Choose later", "Direct Meta", "Coexistence Tech Provider").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                scope.launch { context.whatsAppSettingsStore.edit { it[WhatsAppPreferenceKeys.provider] = option } }
                                providerMenu = false
                            })
                        }
                    }
                    Text("Connection mode", color = Color(0xFFFFF4DF))
                    OutlinedButton(onClick = { modeMenu = true }) { Text(mode) }
                    DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                        listOf("Business App Coexistence", "Cloud API only (new number)", "Manual WhatsApp sharing").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                scope.launch { context.whatsAppSettingsStore.edit { it[WhatsAppPreferenceKeys.mode] = option } }
                                modeMenu = false
                            })
                        }
                    }
                    Text("Sender number", color = Color(0xFFFFF4DF))
                    Text("Not added — configure after eligibility check", color = Color(0xFFDDB36C))
                    Text("Do not enter verification codes or API tokens here. Credentials must be stored securely on the server.", color = Color(0xFFFFF4DF), style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Automatic message workflows", color = Color(0xFFDDB36C), fontWeight = FontWeight.Bold)
                    Text("Preferences are saved on this device. They do not send messages while the connection is inactive.", color = Color(0xFFFFF4DF), style = MaterialTheme.typography.bodySmall)
                    WhatsAppWorkflowSwitch("Customer bill receipt", receipt) { saveBoolean(WhatsAppPreferenceKeys.receipt, it) }
                    WhatsAppWorkflowSwitch("Daily owner sales report", dailyReport) { saveBoolean(WhatsAppPreferenceKeys.dailyReport, it) }
                    WhatsAppWorkflowSwitch("Low-stock / stock-out alerts", stockAlerts) { saveBoolean(WhatsAppPreferenceKeys.stockAlerts, it) }
                    WhatsAppWorkflowSwitch("Supplier purchase orders", supplierOrders) { saveBoolean(WhatsAppPreferenceKeys.supplierOrders, it) }
                    WhatsAppWorkflowSwitch("Loyalty reward notifications", loyalty) { saveBoolean(WhatsAppPreferenceKeys.loyalty, it) }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Before activation", color = Color(0xFFDDB36C), fontWeight = FontWeight.Bold)
                    Text("1. Verify existing-number Coexistence eligibility with a supported provider.", color = Color(0xFFFFF4DF))
                    Text("2. Review provider fees, Meta charges, linked-device and broadcast-list impacts.", color = Color(0xFFFFF4DF))
                    Text("3. Complete Meta Embedded Signup and approve message templates.", color = Color(0xFFFFF4DF))
                    Text("4. Send a test message, then explicitly enable automation.", color = Color(0xFFFFF4DF))
                    Spacer(Modifier.height(2.dp))
                    Text("Manual WhatsApp sharing remains available as a fallback. Checkout must never depend on WhatsApp availability.", color = Color(0xFFDDB36C), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun WhatsAppWorkflowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFFFF4DF), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
