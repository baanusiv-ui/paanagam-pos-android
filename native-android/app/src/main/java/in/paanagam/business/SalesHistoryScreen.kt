package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.PaanagamDatabase
import `in`.paanagam.business.data.SaleLine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryGold = Color(0xFFDDB36C)
private val HistoryIvory = Color(0xFFFFF4DF)
private val HistoryPanel = Color(0xFF3A2519)

@Composable
fun SalesHistoryScreen(db: PaanagamDatabase) {
    val sales by db.dao().sales().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var expandedSaleId by remember { mutableStateOf<String?>(null) }
    var selectedLines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    val todayStart = remember {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    val todaySales = sales.filter { it.createdAt >= todayStart }
    val todayTotal = todaySales.sumOf { it.totalPaise }
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sales overview", color = HistoryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Today bills", todaySales.size.toString(), Modifier.weight(1f))
            SummaryCard("Today sales", "₹%.2f".format(todayTotal / 100.0), Modifier.weight(1f))
        }
        Text("All saved bills • newest first", color = HistoryIvory)
        if (sales.isEmpty()) {
            Text("No sales recorded yet. Completed POS bills will appear here.", color = HistoryIvory)
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sales, key = { it.id }) { sale ->
                    Card(colors = CardDefaults.cardColors(containerColor = HistoryPanel)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("₹%.2f".format(sale.totalPaise / 100.0), color = HistoryGold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(sale.paymentMethod, color = HistoryIvory)
                            }
                            Text(formatter.format(Date(sale.createdAt)), color = HistoryIvory)
                            Text("Bill ${sale.id.take(8).uppercase(Locale.ROOT)} • ${sale.status} • ${sale.syncState}", color = HistoryIvory, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                if (expandedSaleId == sale.id) {
                                    expandedSaleId = null
                                    selectedLines = emptyList()
                                } else {
                                    expandedSaleId = sale.id
                                    selectedLines = emptyList()
                                    scope.launch { selectedLines = db.dao().saleLines(sale.id) }
                                }
                            }) { Text(if (expandedSaleId == sale.id) "Hide items" else "View items", color = HistoryGold) }
                            if (expandedSaleId == sale.id) {
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
