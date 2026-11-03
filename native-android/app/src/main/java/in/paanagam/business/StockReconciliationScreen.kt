package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun StockReconciliationScreen(db: PaanagamDatabase) {
    val ingredients by db.dao().ingredients().collectAsState(initial = emptyList())
    val history by db.dao().stockReconciliations().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("STORE") }
    var physical by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val item = ingredients.firstOrNull { it.id == selectedId }
    var systemQty by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selectedId, location) {
        if (selectedId.isNotBlank()) systemQty = db.dao().stockBalance(selectedId, location)
    }
    LaunchedEffect(ingredients) {
        if (selectedId.isBlank() && ingredients.isNotEmpty()) selectedId = ingredients.first().id
    }
    val counted = physical.toDoubleOrNull()
    val countedBase = counted?.let { (it * 1000.0).toLong() }
    val variance = if (countedBase != null) countedBase - systemQty else 0L
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Physical stock count", color = Color(0xFF263A32), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Compare the shelf count with the live ledger. Saving applies the difference to stock and writes an audit record.", color = Color(0xFF718078), style = MaterialTheme.typography.bodySmall)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Count and reconcile", color = Color(0xFF16845B), fontWeight = FontWeight.Bold)
                if (ingredients.isEmpty()) Text("Add ingredients before reconciling stock.")
                else {
                    ingredients.forEach { ingredient ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ingredient.name + " (" + ingredient.baseUnit + ")", modifier = Modifier.weight(1f))
                            RadioButton(selected = selectedId == ingredient.id, onClick = { selectedId = ingredient.id; physical = "" })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = location == "STORE", onClick = { location = "STORE" }, label = { Text("Store") })
                        FilterChip(selected = location == "KITCHEN", onClick = { location = "KITCHEN" }, label = { Text("Kitchen") })
                    }
                    Text("System balance: %.3f %s".format(systemQty / 1000.0, item?.baseUnit ?: ""), color = Color(0xFF263A32), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(physical, { physical = it.filter { c -> c.isDigit() || c == '.' }.take(14) }, label = { Text("Physical count (${item?.baseUnit ?: "unit"})") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Text("Variance: %+.3f %s".format(variance / 1000.0, item?.baseUnit ?: ""), color = if (variance == 0L) Color(0xFF16845B) else Color(0xFFC26A27), fontWeight = FontWeight.Bold)
                    if (variance > 0) {
                        OutlinedTextField(unitCost, { unitCost = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, label = { Text("Cost per ${item?.baseUnit ?: "unit"} (₹) for added stock") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Text("For a stock gain, enter its unit cost so FIFO valuation is not silently set to zero.", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("Reason / count reference") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(
                        enabled = item != null && countedBase != null && countedBase >= 0 && (variance <= 0 || (unitCost.toDoubleOrNull() != null && unitCost.toDoubleOrNull()!! >= 0)),
                        onClick = {
                            val current = item
                            val physicalUnits = countedBase
                            if (current == null || physicalUnits == null) message = "Select an ingredient and enter a valid count."
                            else scope.launch {
                                val now = System.currentTimeMillis()
                                val record = StockReconciliation(
                                    UUID.randomUUID().toString(), current.id, current.name, location,
                                    systemQty, physicalUnits, physicalUnits - systemQty,
                                    if (physicalUnits > systemQty) ((unitCost.toDoubleOrNull() ?: 0.0) * 100.0).toLong() else 0L,
                                    note.trim(), now
                                )
                                runCatching { db.dao().reconcileStock(record, current) }
                                    .onSuccess {
                                        systemQty = db.dao().stockBalance(current.id, location)
                                        physical = ""; unitCost = ""; note = ""
                                        message = "Reconciled. Stock ledger now matches the physical count."
                                    }
                                    .onFailure { message = it.message ?: "Reconciliation failed." }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Apply stock adjustment") }
                }
                if (message.isNotBlank()) Text(message, color = Color(0xFF16845B))
            }
        }
        Text("Reconciliation audit trail", color = Color(0xFF263A32), fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history, key = { it.id }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.ingredientName + " • " + row.location, color = Color(0xFF263A32), fontWeight = FontWeight.SemiBold)
                        Text("System %.3f → Counted %.3f • Variance %+.3f".format(row.systemQtyBaseUnits / 1000.0, row.physicalQtyBaseUnits / 1000.0, row.varianceBaseUnits / 1000.0), color = Color(0xFF263A32))
                        if (row.note.isNotBlank()) Text(row.note, color = Color(0xFF718078), style = MaterialTheme.typography.bodySmall)
                        Text(fmt.format(Date(row.reconciledAt)), color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
