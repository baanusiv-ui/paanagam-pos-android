package in.paanagam.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import in.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID

private val InvGold = Color(0xFFDDB36C)
private val InvIvory = Color(0xFFFFF4DF)
private val InvPanel = Color(0xFF3A2519)

@Composable
fun InventoryScreen(db: PaanagamDatabase, mode: String) {
    val ingredients by db.dao().ingredients().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var coffee by remember { mutableStateOf(false) }
    var supplier by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf("") }
    var transferQty by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("STORE") }
    var message by remember { mutableStateOf("") }
    var balances by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }

    LaunchedEffect(ingredients) {
        val values = mutableMapOf<String, Pair<Long, Long>>()
        ingredients.forEach { item -> values[item.id] = Pair(db.dao().stockBalance(item.id, "STORE"), db.dao().stockBalance(item.id, "KITCHEN")) }
        balances = values
        if (selectedId.isBlank() && ingredients.isNotEmpty()) selectedId = ingredients.first().id
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (mode == "Purchases") "Receive purchase into Store" else if (mode == "Store → Kitchen") "Transfer stock: Store ↔ Kitchen" else "Ingredient & stock control", color = InvGold, style = MaterialTheme.typography.titleLarge)
        if (mode == "Inventory" || mode == "Purchases" || ingredients.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = InvPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ingredient master", color = InvGold)
                    OutlinedTextField(name, { name = it }, label = { Text("Ingredient name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(unit, { unit = it }, label = { Text("Base unit (g, ml, unit)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row { Checkbox(checked = coffee, onCheckedChange = { coffee = it }); Text("Coffee powder (250 g issue rule)", color = InvIvory, modifier = Modifier.padding(top = 12.dp)) }
                    Button(onClick = {
                        if (name.isBlank() || unit.isBlank()) message = "Enter ingredient name and base unit"
                        else scope.launch {
                            val item = Ingredient(UUID.randomUUID().toString(), name.trim(), unit.trim(), coffee)
                            db.dao().insertIngredient(item)
                            selectedId = item.id
                            name = ""; coffee = false; message = "Ingredient saved"
                        }
                    }) { Text("Add ingredient") }
                }
            }
        }
        if (mode == "Purchases" || mode == "Inventory") {
            Card(colors = CardDefaults.cardColors(containerColor = InvPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Purchase receipt • Store stock", color = InvGold)
                    if (ingredients.isEmpty()) Text("Add an ingredient before receiving stock.", color = InvIvory)
                    else {
                        LazyColumn(Modifier.heightIn(max = 110.dp)) {
                            items(ingredients, key = { it.id }) { item ->
                                Row(Modifier.fillMaxWidth().background(if (selectedId == item.id) Color(0xFF5B3A25) else Color.Transparent, RoundedCornerShape(8.dp)).padding(5.dp)) {
                                    RadioButton(selected = selectedId == item.id, onClick = { selectedId = item.id })
                                    Text(item.name + " (" + item.baseUnit + ")", color = InvIvory, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                        OutlinedTextField(supplier, { supplier = it }, label = { Text("Supplier") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(invoice, { invoice = it }, label = { Text("Invoice number (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(qty, { qty = it }, label = { Text("Quantity in base units") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(cost, { cost = it }, label = { Text("Cost per base unit (₹)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Button(onClick = {
                            val item = ingredients.firstOrNull { it.id == selectedId }
                            val q = qty.toDoubleOrNull()
                            val c = cost.toDoubleOrNull()
                            if (item == null || q == null || c == null || q <= 0 || c < 0) message = "Check ingredient, quantity and cost"
                            else scope.launch {
                                val purchaseId = UUID.randomUUID().toString()
                                val lotId = UUID.randomUUID().toString()
                                val quantity = q.toLong()
                                val unitCost = (c * 100.0).toLong()
                                val total = quantity * unitCost
                                val now = System.currentTimeMillis()
                                val purchase = Purchase(purchaseId, supplier.trim(), invoice.trim(), now, total)
                                val line = PurchaseLine(UUID.randomUUID().toString(), purchaseId, item.id, quantity, unitCost, total, lotId)
                                val lot = StockLot(lotId, item.id, "STORE", now, quantity, quantity, item.baseUnit, unitCost)
                                runCatching { db.dao().receivePurchase(purchase, listOf(line), listOf(lot)) }
                                    .onSuccess { supplier = ""; invoice = ""; qty = ""; cost = ""; message = "Purchase received into Store" }
                                    .onFailure { message = it.message ?: "Purchase could not be saved" }
                            }
                        }) { Text("Receive purchase") }
                    }
                }
            }
        }
        if (mode == "Store → Kitchen" || mode == "Inventory" || mode == "Kitchen Stock") {
            Card(colors = CardDefaults.cardColors(containerColor = InvPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Stock transfer", color = InvGold)
                    if (ingredients.isEmpty()) Text("Add ingredients and receive stock first.", color = InvIvory)
                    else {
                        Row {
                            FilterChip(selected = from == "STORE", onClick = { from = "STORE" }, label = { Text("Store → Kitchen") })
                            Spacer(Modifier.width(8.dp))
                            FilterChip(selected = from == "KITCHEN", onClick = { from = "KITCHEN" }, label = { Text("Kitchen → Store") })
                        }
                        ingredients.forEach { item ->
                            Row(Modifier.fillMaxWidth()) {
                                RadioButton(selected = selectedId == item.id, onClick = { selectedId = item.id })
                                val b = balances[item.id] ?: Pair(0L, 0L)
                                Text(item.name + " • Store " + b.first + " / Kitchen " + b.second + " " + item.baseUnit, color = InvIvory, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        OutlinedTextField(transferQty, { transferQty = it }, label = { Text("Transfer quantity in base units") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Button(onClick = {
                            val item = ingredients.firstOrNull { it.id == selectedId }
                            val q = transferQty.toLongOrNull()
                            if (item == null || q == null || q <= 0) message = "Enter a valid transfer quantity"
                            else scope.launch {
                                val transfer = StockTransfer(UUID.randomUUID().toString(), from, if (from == "STORE") "KITCHEN" else "STORE", System.currentTimeMillis())
                                runCatching { db.dao().transferFifo(transfer, item.id, q) }
                                    .onSuccess {
                                        transferQty = ""; message = "FIFO transfer completed"
                                        balances = balances + (item.id to Pair(db.dao().stockBalance(item.id, "STORE"), db.dao().stockBalance(item.id, "KITCHEN")))
                                    }
                                    .onFailure { message = it.message ?: "Transfer failed" }
                            }
                        }) { Text("Complete transfer") }
                    }
                }
            }
        }
        Text(message, color = InvGold)
        Text("FIFO costing preserves each purchase price. No average costing is used.", color = InvIvory)
    }
}
