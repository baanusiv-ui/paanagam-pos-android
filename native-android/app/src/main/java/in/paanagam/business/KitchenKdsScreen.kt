package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KitchenKdsScreen(db: PaanagamDatabase) {
    val tickets by db.dao().kitchenTickets().collectAsState(initial = emptyList())
    val sales by db.dao().sales().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var linesBySale by remember { mutableStateOf<Map<String, List<SaleLine>>>(emptyMap()) }
    var filter by remember { mutableStateOf("ACTIVE") }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(tickets) {
        val loaded = mutableMapOf<String, List<SaleLine>>()
        tickets.forEach { ticket -> loaded[ticket.saleId] = db.dao().saleLines(ticket.saleId) }
        linesBySale = loaded
    }
    val visible = tickets.filter { if (filter == "ACTIVE") it.status != "COMPLETED" else it.status == "COMPLETED" }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Kitchen display", style = MaterialTheme.typography.headlineSmall)
        Text("Live tickets from POS • oldest orders first", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == "ACTIVE", onClick = { filter = "ACTIVE" }, label = { Text("Active (${tickets.count { it.status != "COMPLETED" }})") })
            FilterChip(selected = filter == "COMPLETED", onClick = { filter = "COMPLETED" }, label = { Text("Completed") })
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        if (visible.isEmpty()) Text(if (filter == "ACTIVE") "No active kitchen tickets." else "No completed tickets yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(visible, key = { it.saleId }) { ticket ->
                val sale = sales.firstOrNull { it.id == ticket.saleId }
                val lines = linesBySale[ticket.saleId].orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Order #${ticket.saleId.take(6).uppercase()}", style = MaterialTheme.typography.titleMedium)
                            AssistChip(onClick = {}, label = { Text(ticket.status) })
                        }
                        Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ticket.createdAt)) + " • " + (sale?.paymentMethod ?: "POS"))
                        lines.forEach { line ->
                            Text("${line.quantity} × ${line.itemName}", style = MaterialTheme.typography.bodyLarge)
                        }
                        if (ticket.status != "COMPLETED") {
                            val next = when (ticket.status) {
                                "NEW" -> "PREPARING"
                                "PREPARING" -> "READY"
                                else -> "COMPLETED"
                            }
                            Button(onClick = {
                                scope.launch {
                                    db.dao().updateKitchenTicketStatus(ticket.saleId, next, System.currentTimeMillis())
                                    message = "Order moved to $next."
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(when (next) { "PREPARING" -> "Accept • Start preparing"; "READY" -> "Mark ready"; else -> "Complete order" })
                            }
                        } else {
                            Text("Completed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
