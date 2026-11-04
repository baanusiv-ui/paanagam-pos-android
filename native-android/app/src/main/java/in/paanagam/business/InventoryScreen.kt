package `in`.paanagam.business

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

private val InvGold = Color(0xFFDDB36C)
private val InvIvory = Color(0xFFFFF4DF)
private val InvPanel = Color(0xFF3A2519)

@Composable
fun InventoryScreen(db: PaanagamDatabase, mode: String) {
    val ingredients by db.dao().ingredients().collectAsState(initial = emptyList())
    val suppliers by db.dao().suppliers().collectAsState(initial = emptyList())
    val stockMovements by db.dao().stockMovements().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var coffee by remember { mutableStateOf(false) }
    var supplier by remember { mutableStateOf("") }
    var selectedSupplierId by remember { mutableStateOf<String?>(null) }
    var invoice by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf("") }
    var transferQty by remember { mutableStateOf("") }
    var storeMinimum by remember { mutableStateOf("0") }
    var kitchenMinimum by remember { mutableStateOf("0") }
    var from by remember { mutableStateOf("STORE") }
    var message by remember { mutableStateOf("") }
    var balances by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }

    LaunchedEffect(selectedId, ingredients) {
        ingredients.firstOrNull { it.id == selectedId }?.let { selected ->
            storeMinimum = (selected.storeMinStockBaseUnits / 1000.0).toString()
            kitchenMinimum = (selected.kitchenMinStockBaseUnits / 1000.0).toString()
        }
    }

    LaunchedEffect(ingredients, stockMovements) {
        val values = mutableMapOf<String, Pair<Long, Long>>()
        ingredients.forEach { item -> values[item.id] = Pair(db.dao().stockBalance(item.id, "STORE"), db.dao().stockBalance(item.id, "KITCHEN")) }
        balances = values
        if (selectedId.isBlank() && ingredients.isNotEmpty()) selectedId = ingredients.first().id
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        if (mode == "Inventory" && ingredients.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = InvPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Low-stock alert settings", color = InvGold)
                    Text("Selected ingredient: ${ingredients.firstOrNull { it.id == selectedId }?.name ?: "Choose an ingredient"}", color = InvIvory)
                    OutlinedTextField(storeMinimum, { storeMinimum = it }, label = { Text("Store minimum (${ingredients.firstOrNull { it.id == selectedId }?.baseUnit ?: "unit"})") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(kitchenMinimum, { kitchenMinimum = it }, label = { Text("Kitchen minimum (${ingredients.firstOrNull { it.id == selectedId }?.baseUnit ?: "unit"})") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Button(onClick = {
                        val item = ingredients.firstOrNull { it.id == selectedId }
                        val store = runCatching { BigDecimal(storeMinimum).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                        val kitchen = runCatching { BigDecimal(kitchenMinimum).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                        if (item == null || store == null || kitchen == null || store < 0 || kitchen < 0) message = "Enter valid levels with up to 3 decimal places (zero disables an alert)"
                        else scope.launch {
                            runCatching {
                                db.dao().updateIngredient(item.copy(storeMinStockBaseUnits = store, kitchenMinStockBaseUnits = kitchen))
                            }.onSuccess { message = "Minimum stock levels saved" }
                                .onFailure { message = it.message ?: "Could not save minimum levels" }
                        }
                    }) { Text("Save alert levels") }
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
                        if (suppliers.isNotEmpty()) {
                            Text("Choose saved supplier", color = InvIvory)
                            LazyColumn(Modifier.heightIn(max = 90.dp)) {
                                items(suppliers.filter { it.active }, key = { it.id }) { saved ->
                                    Row(Modifier.fillMaxWidth().clickable { selectedSupplierId = saved.id; supplier = saved.name }.padding(4.dp)) {
                                        RadioButton(selected = selectedSupplierId == saved.id, onClick = { selectedSupplierId = saved.id; supplier = saved.name })
                                        Text(saved.name, color = InvIvory, modifier = Modifier.padding(top = 12.dp))
                                    }
                                }
                            }
                        }
                        OutlinedTextField(supplier, { supplier = it; selectedSupplierId = suppliers.firstOrNull { saved -> saved.name.equals(it.trim(), true) }?.id }, label = { Text("Supplier name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(invoice, { invoice = it }, label = { Text("Invoice number (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(qty, { qty = it }, label = { Text("Quantity in base units (decimals allowed)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(cost, { cost = it }, label = { Text("Cost per base unit (₹)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Button(onClick = {
                            val item = ingredients.firstOrNull { it.id == selectedId }
                            val quantity = runCatching { BigDecimal(qty).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                            val unitCost = runCatching { BigDecimal(cost).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
                            if (item == null || quantity == null || unitCost == null || quantity <= 0 || unitCost < 0) message = "Check quantity (up to 3 decimals) and cost"
                            else scope.launch {
                                val purchaseId = UUID.randomUUID().toString()
                                val lotId = UUID.randomUUID().toString()
                                val total = Math.multiplyExact(quantity, unitCost) / 1000L
                                val now = System.currentTimeMillis()
                                val purchase = Purchase(purchaseId, supplier.trim(), invoice.trim(), now, total, supplierId = selectedSupplierId)
                                val line = PurchaseLine(UUID.randomUUID().toString(), purchaseId, item.id, quantity, unitCost, total, lotId)
                                val lot = StockLot(lotId, item.id, "STORE", now, quantity, quantity, item.baseUnit, unitCost)
                                runCatching { db.dao().receivePurchase(purchase, listOf(line), listOf(lot)) }
                                    .onSuccess { supplier = ""; selectedSupplierId = null; invoice = ""; qty = ""; cost = ""; message = "Purchase received into Store" }
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
                                Text(item.name + " • Store " + (b.first / 1000.0) + " / Kitchen " + (b.second / 1000.0) + " " + item.baseUnit, color = InvIvory, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        OutlinedTextField(transferQty, { transferQty = it }, label = { Text("Transfer quantity in base units (decimals allowed)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Button(onClick = {
                            val item = ingredients.firstOrNull { it.id == selectedId }
                            val q = runCatching { BigDecimal(transferQty).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                            if (item == null || q == null || q <= 0) message = "Enter a valid transfer quantity (up to 3 decimals)"
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
