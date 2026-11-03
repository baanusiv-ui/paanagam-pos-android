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
    val allTickets by db.dao().kitchenTickets().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var linesBySale by remember { mutableStateOf<Map<String, List<SaleLine>>>(emptyMap()) }
    var showCompleted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(allTickets) {
        val loaded = mutableMapOf<String, List<SaleLine>>()
        allTickets.forEach { ticket -> loaded[ticket.saleId] = db.dao().saleLines(ticket.saleId) }
        linesBySale = loaded
    }
    val active = allTickets.filter { it.status != "COMPLETED" }
    val visible = if (showCompleted) allTickets.filter { it.status == "COMPLETED" } else active
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Kitchen display", style = MaterialTheme.typography.headlineSmall)
        Text("Live POS tickets • oldest orders first", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showCompleted, onClick = { showCompleted = false }, label = { Text("Active (${active.size})") })
            FilterChip(selected = showCompleted, onClick = { showCompleted = true }, label = { Text("Completed") })
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        if (visible.isEmpty()) Text(if (showCompleted) "No completed tickets yet." else "No active kitchen tickets.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(visible, key = { it.saleId }) { ticket ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Order #${ticket.saleId.take(6).uppercase()}", style = MaterialTheme.typography.titleMedium)
                            AssistChip(onClick = {}, label = { Text(ticket.status) })
                        }
                        Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ticket.createdAt)))
                        linesBySale[ticket.saleId].orEmpty().forEach { line ->
                            Text("${line.quantity} × ${line.itemName}", style = MaterialTheme.typography.bodyLarge)
                        }
                        val next = when (ticket.status) {
                            "NEW" -> "PREPARING"
                            "PREPARING" -> "READY"
                            "READY" -> "COMPLETED"
                            else -> ""
                        }
                        if (next.isNotBlank()) Button(onClick = {
                            scope.launch {
                                db.dao().updateKitchenTicketStatus(ticket.saleId, next, System.currentTimeMillis())
                                message = "Order moved to $next."
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(when (next) { "PREPARING" -> "Accept • Start preparing"; "READY" -> "Mark ready"; else -> "Complete order" })
                        }
                    }
                }
            }
        }
    }
}
