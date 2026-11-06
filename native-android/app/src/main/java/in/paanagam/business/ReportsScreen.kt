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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReportInk = Color(0xFF263A32)
private val ReportGold = Color(0xFF16845B)

@Composable
fun ReportsScreen(db: PaanagamDatabase, title: String) {
    val sales by rememberDbFlow(db, emptyList()) { d -> d.sales() }
    val costs by rememberDbFlow(db, emptyList()) { d -> d.saleCosts() }
    val refunds by rememberDbFlow(db, emptyList()) { d -> d.allSaleRefunds() }
    val expensesFlow = remember(db) { db.dao().operationalRecords("Operating Expenses") }
    val expenses by expensesFlow.collectAsState(initial = emptyList())
    val costBySale = remember(costs) { costs.associate { it.saleId to it.ingredientCostPaise } }
    val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val today = sales.filter { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.createdAt)) == todayKey }
    val todayGross = today.sumOf { it.totalPaise }
    val todayRefunds = refunds.filter { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.refundedAt)) == todayKey }
    val todayRefundTotal = todayRefunds.sumOf { it.amountPaise }
    val todaySales = todayGross - todayRefundTotal
    val todayCost = today.sumOf { costBySale[it.id] ?: 0L }
    val todayExpenses = expenses.filter { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.createdAt)) == todayKey }.sumOf { it.amountPaise }
    val allExpenses = expenses.sumOf { it.amountPaise }
    val allGrossSales = sales.sumOf { it.totalPaise }
    val allRefunds = refunds.sumOf { it.amountPaise }
    val cash = today.sumOf { it.cashPaidPaise } - todayRefunds.sumOf { it.cashRefundPaise }
    val upi = today.sumOf { it.upiPaidPaise } - todayRefunds.sumOf { it.upiRefundPaise }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = ReportInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Live figures from this device's offline sales records", color = Color(0xFF718078))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportMetric("Net sales today", rupees(todaySales), Modifier.weight(1f))
            ReportMetric("Bills", today.size.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportMetric("Ingredient cost", rupees(todayCost), Modifier.weight(1f))
            ReportMetric("Gross margin", rupees(todaySales - todayCost), Modifier.weight(1f))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE4F3EC))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sales and refunds • Today", color = ReportInk, fontWeight = FontWeight.SemiBold)
                Text("Gross sales  ${rupees(todayGross)}", color = ReportInk)
                Text("Refunds issued today  -${rupees(todayRefundTotal)}", color = ReportInk)
                Text("Cash net  ${rupees(cash)}", color = ReportInk)
                Text("UPI net  ${rupees(upi)}", color = ReportInk)
                Text("Operating expenses today: ${rupees(todayExpenses)}", color = ReportInk)
                Text("Operating profit today: ${rupees(todaySales - todayCost - todayExpenses)}", color = ReportInk, fontWeight = FontWeight.Bold)
                Text("All-time gross sales: ${rupees(allGrossSales)}", color = ReportInk)
                Text("All-time refunds: -${rupees(allRefunds)}", color = ReportInk)
                Text("All-time net sales: ${rupees(allGrossSales - allRefunds)}", color = ReportInk)
                Text("All-time recorded operating expenses: ${rupees(allExpenses)}", color = ReportInk)
            }
        }
        Text("Recent bills", color = ReportGold, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sales.take(50), key = { it.id }) { sale ->
                val cogs = costBySale[sale.id] ?: 0L
                val saleRefundTotal = refunds.filter { it.saleId == sale.id }.sumOf { it.amountPaise }
                val netSalePaise = sale.totalPaise - saleRefundTotal
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(Date(sale.createdAt)), color = ReportInk, fontWeight = FontWeight.Medium)
                            Text("${sale.paymentMethod} • ${sale.status}", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                            Text("Cash ${rupees(sale.cashPaidPaise)} • UPI ${rupees(sale.upiPaidPaise)}", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                            Text("Ingredient cost ${rupees(cogs)}", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                            if (saleRefundTotal > 0L) Text("Net after refunds ${rupees(netSalePaise)}", color = Color(0xFFC26A27), style = MaterialTheme.typography.labelSmall)
                            refunds.filter { it.saleId == sale.id }.forEach { refund -> Text("Refunded ${rupees(refund.amountPaise)} • ${fmt.format(Date(refund.refundedAt))}", color = Color(0xFFC26A27), style = MaterialTheme.typography.labelSmall) }
                        }
                        Column {
                            Text("Net ${rupees(netSalePaise)}", color = ReportInk, fontWeight = FontWeight.Bold)
                            Text("Margin ${rupees(netSalePaise - cogs)}", color = ReportGold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Text("Daily net sales subtract refunds issued on that day, even when the original bill was from an earlier date. Gross sales remain attributed to the original sale date. Operating profit subtracts expenses entered in Operating Expenses; GST, depreciation and other statutory accounting adjustments are not included.", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color(0xFF718078), style = MaterialTheme.typography.labelMedium)
            Text(value, color = ReportInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun rupees(paise: Long): String = "₹" + String.format(Locale.getDefault(), "%.2f", paise / 100.0)
