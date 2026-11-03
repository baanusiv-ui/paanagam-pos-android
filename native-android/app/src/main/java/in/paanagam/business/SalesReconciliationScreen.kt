package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.PaanagamDatabase
import `in`.paanagam.business.data.SalesReconciliation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun SalesReconciliationScreen(db: PaanagamDatabase) {
    val sales by db.dao().sales().collectAsState(initial = emptyList())
    val history by db.dao().salesReconciliations().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    var businessDate by remember { mutableStateOf(dateFormat.format(Date())) }
    var upiReceived by remember { mutableStateOf("") }
    var cashCounted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val daySales = sales.filter { it.status == "COMPLETED" && dateFormat.format(Date(it.createdAt)) == businessDate }
    val posCash = daySales.sumOf { it.cashPaidPaise }
    val posUpi = daySales.sumOf { it.upiPaidPaise }
    val receivedPaise = parseRupeesToPaise(upiReceived)
    val countedPaise = parseRupeesToPaise(cashCounted)
    val upiVariance = (receivedPaise ?: 0L) - posUpi
    val cashVariance = (countedPaise ?: 0L) - posCash
    val validDate = runCatching { LocalDate.parse(businessDate); true }.getOrDefault(false)
    val validInputs = receivedPaise != null && countedPaise != null

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Daily settlement check", style = MaterialTheme.typography.titleLarge, color = Color(0xFF2B1B12), fontWeight = FontWeight.Bold)
        Text("Compare completed POS bills with actual cash counted and UPI settlement received. Cash float is reconciled separately in Cash Shift.", color = Color(0xFF718078), style = MaterialTheme.typography.bodySmall)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = businessDate,
                    onValueChange = { businessDate = it.take(10); message = "" },
                    label = { Text("Business date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("POS totals", color = Color(0xFFB87928), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Completed bills: ${daySales.size}")
                    Text("₹%.2f".format(daySales.sumOf { it.totalPaise } / 100.0), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash sales")
                    Text("₹%.2f".format(posCash / 100.0), fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("UPI sales")
                    Text("₹%.2f".format(posUpi / 100.0), fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = cashCounted,
                    onValueChange = { cashCounted = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                    label = { Text("Actual cash counted (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = upiReceived,
                    onValueChange = { upiReceived = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                    label = { Text("UPI settlement received (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Text("Cash variance: ${if (cashVariance >= 0) "+" else ""}₹%.2f".format(cashVariance / 100.0), color = if (cashVariance == 0L) Color(0xFF16845B) else Color(0xFFC26A27), fontWeight = FontWeight.Bold)
                Text("UPI variance: ${if (upiVariance >= 0) "+" else ""}₹%.2f".format(upiVariance / 100.0), color = if (upiVariance == 0L) Color(0xFF16845B) else Color(0xFFC26A27), fontWeight = FontWeight.Bold)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Settlement reference / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Button(
                    enabled = validDate && validInputs,
                    onClick = {
                        scope.launch {
                            val entry = SalesReconciliation(
                                UUID.randomUUID().toString(), businessDate, posCash, posUpi,
                                receivedPaise ?: 0L, countedPaise ?: 0L, note.trim(), System.currentTimeMillis()
                            )
                            runCatching { db.dao().insertSalesReconciliation(entry) }
                                .onSuccess {
                                    message = "Settlement reconciliation saved on this device."
                                    note = ""
                                }
                                .onFailure { message = it.message ?: "Could not save reconciliation." }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save daily reconciliation") }
                if (!validDate) Text("Enter a valid date in YYYY-MM-DD format.", color = Color(0xFFC26A27), style = MaterialTheme.typography.labelSmall)
                if (message.isNotBlank()) Text(message, color = Color(0xFF16845B))
            }
        }
        Text("Saved reconciliation history", color = Color(0xFF2B1B12), fontWeight = FontWeight.Bold)
        if (history.isEmpty()) Text("No settlement checks saved yet.", color = Color(0xFF718078))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history, key = { it.id }) { row ->
                val cashDiff = row.countedCashPaise - row.posCashPaise
                val upiDiff = row.settledUpiPaise - row.posUpiPaise
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.businessDate, color = Color(0xFF2B1B12), fontWeight = FontWeight.Bold)
                        Text("Cash variance ${if (cashDiff >= 0) "+" else ""}₹%.2f • UPI variance ${if (upiDiff >= 0) "+" else ""}₹%.2f".format(cashDiff / 100.0, upiDiff / 100.0), color = if (cashDiff == 0L && upiDiff == 0L) Color(0xFF16845B) else Color(0xFFC26A27))
                        if (row.note.isNotBlank()) Text(row.note, color = Color(0xFF718078), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


private fun parseRupeesToPaise(value: String): Long? = try {
    val amount = BigDecimal(value)
    if (amount.signum() < 0) null
    else amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
} catch (_: Exception) {
    null
}
