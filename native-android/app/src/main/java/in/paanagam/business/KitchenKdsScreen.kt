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
    val scope = rememberCoroutineScope()
    var linesBySale by remember { mutableStateOf<Map<String, List<SaleLine>>>(emptyMap()) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(tickets) {
        val loaded = mutableMapOf<String, List<SaleLine>>()
        tickets.forEach { ticket -> loaded[ticket.saleId] = db.dao().saleLines(ticket.saleId) }
        linesBySale = loaded
    }
    val queued = tickets.count { it.status == "QUEUED" }
    val preparing = tickets.count { it.status == "PREPARING" }
    val ready = tickets.count { it.status == "READY" }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Kitchen display", style = MaterialTheme.typography.headlineSmall)
        Text("Live POS tickets • oldest orders first", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KdsCount("Queued", queued, Modifier.weight(1f))
            KdsCount("Preparing", preparing, Modifier.weight(1f))
            KdsCount("Ready", ready, Modifier.weight(1f))
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        if (tickets.isEmpty()) Text("No active kitchen tickets. New POS bills appear automatically.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(tickets, key = { it.id }) { ticket ->
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
                            "QUEUED" -> "PREPARING"
                            "PREPARING" -> "READY"
                            "READY" -> "SERVED"
                            else -> ""
                        }
                        if (next.isNotBlank()) Button(onClick = {
                            scope.launch {
                                db.dao().updateKitchenTicketStatus(ticket.id, next, System.currentTimeMillis())
                                message = "Order moved to $next."
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(when (next) { "PREPARING" -> "Accept • Start preparing"; "READY" -> "Mark ready"; else -> "Mark served" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KdsCount(label: String, count: Int, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(10.dp)) { Text(label, style = MaterialTheme.typography.labelSmall); Text(count.toString(), style = MaterialTheme.typography.titleLarge) } }
}
