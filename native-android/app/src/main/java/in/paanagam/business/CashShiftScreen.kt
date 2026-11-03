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
import `in`.paanagam.business.data.CashShift
import `in`.paanagam.business.data.PaanagamDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun CashShiftScreen(db: PaanagamDatabase) {
    val active by db.dao().activeCashShift().collectAsState(initial = null)
    val shifts by db.dao().cashShifts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf("") }
    var counted by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val money: (String) -> Long? = { value -> value.toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 100).toLong() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cash shift control", color = Color(0xFF16845B), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (active == null) {
            Text("No open shift. Enter the opening cash float to start.", color = Color(0xFF65736B))
            OutlinedTextField(opening, { opening = it }, label = { Text("Opening float (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && money(opening) != null, onClick = {
                val amount = money(opening) ?: 0L
                busy = true
                scope.launch {
                    runCatching { db.dao().openCashShift(CashShift(UUID.randomUUID().toString(), System.currentTimeMillis(), amount)) }
                        .onSuccess { opening = ""; message = "Cash shift opened." }
                        .onFailure { message = it.message ?: "Could not open shift." }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open shift") }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("SHIFT OPEN", color = Color(0xFF16845B), fontWeight = FontWeight.Bold)
                    Text("Opened: " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(active!!.openedAt)), color = Color(0xFF65736B))
                    Text("Opening float: ₹%.2f".format(active!!.openingFloatPaise / 100.0), color = Color(0xFF263A32))
                }
            }
            OutlinedTextField(counted, { counted = it }, label = { Text("Actual cash counted (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Closing notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(enabled = !busy && money(counted) != null, onClick = {
                val amount = money(counted) ?: 0L
                busy = true
                scope.launch {
                    runCatching { db.dao().closeCashShift(amount, notes.trim(), System.currentTimeMillis()) }
                        .onSuccess { closed -> message = "Shift closed. Expected ₹%.2f • Counted ₹%.2f • Variance ₹%.2f".format((closed.expectedCashPaise ?: 0L)/100.0, amount/100.0, (closed.variancePaise ?: 0L)/100.0); counted = ""; notes = "" }
                        .onFailure { message = it.message ?: "Could not close shift." }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Close shift & reconcile cash") }
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFF16845B))
        Text("Shift history", color = Color(0xFF263A32), fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(shifts, key = { it.id }) { shift ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (shift.status == "OPEN") "OPEN" else "CLOSED", color = if (shift.status == "OPEN") Color(0xFF16845B) else Color(0xFF65736B), fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(shift.openedAt)), color = Color(0xFF263A32))
                        Text("Float ₹%.2f".format(shift.openingFloatPaise/100.0) + (shift.expectedCashPaise?.let { " • Expected ₹%.2f".format(it/100.0) } ?: ""), color = Color(0xFF65736B))
                        if (shift.variancePaise != null) Text("Counted ₹%.2f • Variance ₹%.2f".format((shift.countedCashPaise ?: 0L)/100.0, shift.variancePaise/100.0), color = if (shift.variancePaise == 0L) Color(0xFF16845B) else Color(0xFFC26A27))
                        if (shift.notes.isNotBlank()) Text(shift.notes, color = Color(0xFF65736B))
                    }
                }
            }
        }
    }
}
