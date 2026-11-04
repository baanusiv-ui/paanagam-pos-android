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
import `in`.paanagam.business.data.OperationalRecord
import `in`.paanagam.business.data.PaanagamDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

private val OpsInk = Color(0xFF263A32)
private val OpsGreen = Color(0xFF16845B)
private val OpsMuted = Color(0xFF718078)

private val moduleGuidance = mapOf(
    "Orders" to "Record phone, counter or advance orders. POS bills remain the official completed-sale record.",
    "Operating Expenses" to "Record rent, wages, utilities, repairs, transport and other overheads. These expenses are included in operating profit; ingredient cost remains separate.",
    "Combos" to "Define combo offers here for planning. Recipe deductions still happen only through configured POS item recipes.",
    "Modifiers" to "Record modifier options and price adjustments for menu setup review.",
    "BOM" to "Record bill-of-material notes. Recipe quantities used by POS are configured in Recipes & BOM / SOP.",
    "Next-Day Planning" to "Plan expected demand, prep quantities and purchase needs for the next operating day.",
    "Kitchen KDS" to "Track kitchen tickets and preparation status. This log is not yet a live POS ticket queue.",
    "Operations Control" to "Log opening checks, closing checks, handover notes and operational incidents.",
    "Cash Shift" to "Record shift opening float, cash count, cash handover and variance notes.",
    "Customers" to "Maintain customer contact notes and service preferences locally on this device.",
    "GST Calculator" to "Use this as a manual GST calculation record. Confirm applicable rates with your accountant.",
    "Marketing Hub" to "Store campaign ideas and draft notes. Nothing is published or scheduled from this screen.",
    "Marketing Automation" to "Record automation requirements. No background publishing or messaging is enabled here.",
    "Automation Hub" to "Track requested automations and their approval state. No external workflow is triggered.",
    "Business Planner" to "Record targets, action plans and review notes for the business.",
    "Stock Reconciliation" to "Record physical count variances. This log does not adjust stock balances automatically.",
    "Sales Reconciliation" to "Record settlement checks and discrepancies against POS bills.",
    "Suppliers" to "Maintain supplier contacts and purchasing notes. Purchase receipt remains in Purchases.",
    "Safety & FSSAI" to "Record cleaning, hygiene, temperature, pest-control and compliance checks.",
    "Equipment" to "Track equipment maintenance, service dates, repair notes and costs."
)

@Composable
fun OperationalModuleScreen(db: PaanagamDatabase, module: String) {
    val recordsFlow = remember(db, module) { db.dao().operationalRecords(module) }
    val records by recordsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var title by remember(module) { mutableStateOf("") }
    var details by remember(module) { mutableStateOf("") }
    var amount by remember(module) { mutableStateOf("") }
    var status by remember(module) { mutableStateOf("OPEN") }
    var message by remember(module) { mutableStateOf("") }
    val isGst = module == "GST Calculator"
    var gstRate by remember { mutableStateOf("5") }
    val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gstRateValue = gstRate.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val gstValue = if (isGst) amountValue.multiply(gstRateValue).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
    val amountPaise = runCatching { amountValue.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
    val guidance = moduleGuidance[module] ?: "Record and review ${module.lowercase(Locale.getDefault())} information locally."

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(module, color = OpsInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(guidance, color = OpsMuted, style = MaterialTheme.typography.bodySmall)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isGst) "GST calculation entry" else "New record", color = OpsGreen, fontWeight = FontWeight.Bold)
                OutlinedTextField(title, { title = it }, label = { Text(if (isGst) "Description" else "Title / reference") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(details, { details = it }, label = { Text(if (isGst) "Notes (inclusive or exclusive amount)" else "Details / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, label = { Text(if (isGst) "Taxable amount (₹)" else "Amount (₹), if applicable") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (isGst) {
                    OutlinedTextField(gstRate, { gstRate = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text("GST rate (%)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    if (amountValue > BigDecimal.ZERO && gstRateValue >= BigDecimal.ZERO) {
                        Text("Estimated GST: ₹$gstValue • Total: ₹${amountValue.add(gstValue).setScale(2, RoundingMode.HALF_UP)}", color = OpsGreen, fontWeight = FontWeight.SemiBold)
                        Text("Confirm the applicable HSN/SAC rate with your accountant. This is a calculation aid, not a tax filing.", color = OpsMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OPEN", "IN PROGRESS", "DONE").forEach { value ->
                        FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value) })
                    }
                }
                Button(onClick = {
                    if (title.isBlank()) message = "Enter a title or reference."
                    else if (amount.isNotBlank() && (amountPaise == null || amountPaise < 0)) message = "Enter a valid amount with up to 2 decimal places."
                    else if (module == "Operating Expenses" && (amountPaise == null || amountPaise <= 0L)) message = "Enter a positive expense amount."
                    else if (isGst && (gstRate.toBigDecimalOrNull() == null || gstRateValue < BigDecimal.ZERO || gstRateValue > BigDecimal("100"))) message = "Enter a GST rate between 0 and 100 percent."
                    else scope.launch {
                        val record = OperationalRecord(
                            UUID.randomUUID().toString(), module, title.trim(),
                            if (isGst) details.trim() + "\nGST rate: $gstRate% • GST: ₹$gstValue • total: ₹${amountValue.add(gstValue).setScale(2, RoundingMode.HALF_UP)}" else details.trim(),
                            amountPaise ?: 0L, status, System.currentTimeMillis()
                        )
                        runCatching { db.dao().insertOperationalRecord(record) }
                            .onSuccess { title = ""; details = ""; amount = ""; status = "OPEN"; message = "Saved on this device." }
                            .onFailure { message = it.message ?: "Could not save record." }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save record offline") }
                if (message.isNotBlank()) Text(message, color = OpsGreen)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saved records", color = OpsInk, fontWeight = FontWeight.Bold)
            Text("${records.size}", color = OpsGreen, fontWeight = FontWeight.Bold)
        }
        if (records.isEmpty()) Text("No records yet.", color = OpsMuted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(records, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, color = OpsInk, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(item.status, color = OpsGreen, style = MaterialTheme.typography.labelSmall)
                        }
                        if (item.details.isNotBlank()) Text(item.details, color = OpsMuted, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(item.createdAt)), color = OpsMuted, style = MaterialTheme.typography.labelSmall)
                            if (item.amountPaise > 0) Text("₹%.2f".format(item.amountPaise / 100.0), color = OpsInk, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
