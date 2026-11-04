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
import `in`.paanagam.business.data.SaleLine
import `in`.paanagam.business.data.StaffMember
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryGold = Color(0xFFDDB36C)
private val HistoryIvory = Color(0xFFFFF4DF)
private val HistoryPanel = Color(0xFF3A2519)

@Composable
fun SalesHistoryScreen(db: PaanagamDatabase, currentStaff: StaffMember) {
    val sales by rememberDbFlow(db, emptyList()) { d -> d.sales() }
    val refunds by rememberDbFlow(db, emptyList()) { d -> d.allSaleRefunds() }
    val scope = rememberCoroutineScope()
    var expandedSaleId by remember { mutableStateOf<String?>(null) }
    var selectedLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var selectedCostPaise by remember { mutableStateOf<Long?>(null) }
    var refundSaleId by remember { mutableStateOf<String?>(null) }
    var refundReason by remember { mutableStateOf("Customer return") }
    var partialRefundSaleId by remember { mutableStateOf<String?>(null) }
    var partialRefundLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    var partialRefundedByLine by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var partialRefundLineId by remember { mutableStateOf<String?>(null) }
    var partialRefundQuantity by remember { mutableStateOf("1") }
    var partialRefundReason by remember { mutableStateOf("Customer return") }
    var message by remember { mutableStateOf("") }
    val todayStart = run {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    val todaySales = sales.filter { it.createdAt >= todayStart }
    val todayGross = todaySales.sumOf { it.totalPaise }
    val todayRefunds = refunds.filter { it.refundedAt >= todayStart }.sumOf { it.amountPaise }
    val todayTotal = todayGross - todayRefunds
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (message.isNotBlank()) Text(message, color = HistoryGold)
        refundSaleId?.let { id ->
            val target = sales.firstOrNull { it.id == id }
            AlertDialog(
                onDismissRequest = { refundSaleId = null },
                title = { Text("Refund remaining bill balance?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This refunds only the bill units that have not already been refunded. Each returned food unit is recorded as wastage and never returned to Store or Kitchen stock.")
                        Text("The remaining amount and original Cash / UPI tender allocation are calculated from the recorded refund history.")
                        OutlinedTextField(refundReason, { refundReason = it }, label = { Text("Refund reason") }, modifier = Modifier.fillMaxWidth())
                        Text("Food refund reason (required)", fontWeight = FontWeight.SemiBold)
                        Text("The reason is saved against the wastage entries for the recipe ingredients. Refunded food is not reusable stock.")
                        Text("Cash refunds require an open cash shift. UPI refunds must also be reversed in the UPI app.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(enabled = target != null && refundReason.isNotBlank(), onClick = {
                        val sale = target ?: return@TextButton
                        refundSaleId = null
                        commitScope.launch {
                            runCatching { db.dao().refundRemainingSaleBalance(sale.id, refundReason, System.currentTimeMillis(), currentStaff.id, currentStaff.name) }
                                .onSuccess { message = "Remaining bill balance refunded. Returned food was logged as wastage; stock was not increased." }
                                .onFailure { message = it.message ?: "Refund failed." }
                        }
                    }) { Text("Confirm refund") }
                },
                dismissButton = { TextButton(onClick = { refundSaleId = null }) { Text("Cancel") } }
            )
        }
        partialRefundSaleId?.let { saleId ->
            val targetSale = sales.firstOrNull { it.id == saleId }
            val selectedLine = partialRefundLines.firstOrNull { it.id == partialRefundLineId }
            val remainingForLine = selectedLine?.let { it.quantity - (partialRefundedByLine[it.id] ?: 0) } ?: 0
            AlertDialog(
                onDismissRequest = { partialRefundSaleId = null },
                title = { Text("Refund item quantity") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select the sold item and quantity. Each returned food unit is recorded as wastage against its original FIFO ingredient lots. Usable stock is never increased.")
                        partialRefundLines.forEach { line ->
                            val remaining = line.quantity - (partialRefundedByLine[line.id] ?: 0)
                            if (remaining > 0) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = partialRefundLineId == line.id,
                                        onClick = { partialRefundLineId = line.id; partialRefundQuantity = "1" }
                                    )
                                    Column {
                                        Text("${line.itemName} • ${line.quantity} sold • $remaining refundable")
                                        Text("₹%.2f each".format(line.unitPricePaise / 100.0), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            partialRefundQuantity,
                            { partialRefundQuantity = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Quantity to refund (max $remainingForLine)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            partialRefundReason,
                            { partialRefundReason = it },
                            label = { Text("Food refund reason (required)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Cash refunds require an open cash shift. Reverse UPI payments in the UPI app. Old bills without per-unit FIFO traceability are blocked rather than guessed.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    val quantity = partialRefundQuantity.toIntOrNull()
                    TextButton(
                        enabled = targetSale != null && selectedLine != null && quantity != null && quantity in 1..remainingForLine && partialRefundReason.isNotBlank(),
                        onClick = {
                            val sale = targetSale ?: return@TextButton
                            val line = selectedLine ?: return@TextButton
                            val qty = quantity ?: return@TextButton
                            partialRefundSaleId = null
                            commitScope.launch {
                                runCatching {
                                    db.dao().refundSaleLineQuantity(
                                        sale.id, line.id, qty, partialRefundReason.trim(),
                                        System.currentTimeMillis(), currentStaff.id, currentStaff.name
                                    )
                                }.onSuccess {
                                    message = "Refunded $qty × ${line.itemName}. Returned food was recorded as wastage; usable stock was unchanged."
                                }.onFailure {
                                    message = it.message ?: "Item refund failed."
                                }
                            }
                        }
                    ) { Text("Confirm item refund") }
                },
                dismissButton = { TextButton(onClick = { partialRefundSaleId = null }) { Text("Cancel") } }
            )
        }
        Text("Sales overview", color = HistoryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Today bills", todaySales.size.toString(), Modifier.weight(1f))
            SummaryCard("Today sales", "₹%.2f".format(todayTotal / 100.0), Modifier.weight(1f))
        }
        Text("All saved bills • newest first", color = HistoryIvory)
        if (sales.isEmpty()) {
            Text("No sales recorded yet. Completed POS bills will appear here.", color = HistoryIvory)
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sales, key = { it.id }) { sale ->
                    Card(colors = CardDefaults.cardColors(containerColor = HistoryPanel)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("₹%.2f".format(sale.totalPaise / 100.0), color = HistoryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(sale.paymentMethod, color = HistoryIvory)
                            }
                            Text("Cash ₹%.2f • UPI ₹%.2f".format(sale.cashPaidPaise / 100.0, sale.upiPaidPaise / 100.0), color = HistoryIvory, style = MaterialTheme.typography.labelSmall)
                            Text(formatter.format(Date(sale.createdAt)), color = HistoryIvory)
                            Text("Bill ${sale.id.take(8).uppercase(Locale.ROOT)} • ${sale.status} • ${sale.syncState}", color = HistoryIvory, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = {
                                    if (expandedSaleId == sale.id) {
                                        expandedSaleId = null
                                        selectedLines = emptyList()
                                        selectedCostPaise = null
                                    } else {
                                        expandedSaleId = sale.id
                                        selectedLines = emptyList()
                                        selectedCostPaise = null
                                        commitScope.launch { selectedLines = db.dao().saleLines(sale.id); selectedCostPaise = db.dao().saleCost(sale.id)?.ingredientCostPaise ?: 0L }
                                    }
                                }) { Text(if (expandedSaleId == sale.id) "Hide items" else "View items", color = HistoryGold) }
                                if (sale.status == "COMPLETED" || sale.status == "PARTIALLY_REFUNDED") {
                                    TextButton(onClick = { refundSaleId = sale.id; refundReason = "Customer return" }) { Text(if (sale.status == "PARTIALLY_REFUNDED") "Refund remaining" else "Full refund", color = Color(0xFFFFB59A)) }
                                }
                                if (sale.status == "COMPLETED" || sale.status == "PARTIALLY_REFUNDED") {
                                    TextButton(onClick = {
                                        partialRefundSaleId = sale.id
                                        partialRefundLineId = null
                                        partialRefundQuantity = "1"
                                        partialRefundReason = "Customer return"
                                        commitScope.launch {
                                            partialRefundLines = db.dao().saleLines(sale.id)
                                            val refunds = db.dao().saleLineRefunds(sale.id)
                                            partialRefundedByLine = refunds.groupBy { it.saleLineId }.mapValues { (_, rows) -> rows.sumOf { it.refundedQuantity } }
                                        }
                                    }) { Text("Refund items", color = Color(0xFFFFB59A)) }
                                }
                            }
                            if (expandedSaleId == sale.id) {
                                if (selectedCostPaise != null) {
                                    val cost = selectedCostPaise ?: 0L
                                    Text("Ingredient cost: ₹%.2f".format(cost / 100.0), color = HistoryIvory)
                                    Text("Gross margin before other expenses: ₹%.2f".format((sale.totalPaise - cost) / 100.0), color = HistoryGold, fontWeight = FontWeight.Bold)
                                }
                                if (selectedLines.isEmpty()) {
                                    Text("No bill lines found.", color = HistoryIvory)
                                } else {
                                    selectedLines.forEach { line ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${line.itemName} × ${line.quantity}", color = HistoryIvory)
                                            Text("₹%.2f".format(line.lineTotalPaise / 100.0), color = HistoryGold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = HistoryPanel)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = HistoryIvory)
            Text(value, color = HistoryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
