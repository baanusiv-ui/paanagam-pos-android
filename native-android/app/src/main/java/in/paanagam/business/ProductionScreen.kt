package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun ProductionScreen(db: PaanagamDatabase) {
    val ingredients by rememberDbFlow(db, emptyList()) { d -> d.ingredients() }
    val wastageRecords by rememberDbFlow(db, emptyList()) { d -> d.wastageRecords() }
    val scope = rememberCoroutineScope()
    var powder by remember { mutableStateOf("") }
    var water by remember { mutableStateOf("") }
    var decoction by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var wastageItem by remember { mutableStateOf("") }
    var wastageLocation by remember { mutableStateOf("KITCHEN") }
    var wastageQty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    LaunchedEffect(ingredients) {
        if (powder.isBlank()) powder = ingredients.firstOrNull { it.isCoffeePowder }?.id ?: ""
        if (water.isBlank()) water = ingredients.firstOrNull { it.name.contains("water", true) }?.id ?: ""
        if (decoction.isBlank()) decoction = ingredients.firstOrNull { it.name.contains("decoction", true) }?.id ?: ""
        if (wastageItem.isBlank()) wastageItem = ingredients.firstOrNull()?.id ?: ""
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kitchen production & wastage", style = MaterialTheme.typography.titleLarge)
        Text("Coffee decoction standard: 250 g powder + 1,100 ml water → 1,000 ml usable decoction; 100 ml retained in grounds.")
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Select coffee powder")
                LazyColumn(Modifier.heightIn(max = 100.dp)) {
                    items(ingredients.filter { it.isCoffeePowder }, key = { it.id }) { item ->
                        Row { RadioButton(powder == item.id, { powder = item.id }); Text(item.name, modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
                Text("Select water ingredient")
                LazyColumn(Modifier.heightIn(max = 90.dp)) {
                    items(ingredients.filter { it.baseUnit == "ml" && !it.name.contains("decoction", true) }, key = { it.id }) { item ->
                        Row { RadioButton(water == item.id, { water = item.id }); Text(item.name, modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
                Text("Select decoction output ingredient (unit ml)")
                LazyColumn(Modifier.heightIn(max = 90.dp)) {
                    items(ingredients.filter { it.baseUnit == "ml" && it.name.contains("decoction", true) }, key = { it.id }) { item ->
                        Row { RadioButton(decoction == item.id, { decoction = item.id }); Text(item.name, modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
                Button(onClick = {
                    if (powder.isBlank() || water.isBlank() || decoction.isBlank()) message = "Create/select coffee powder (g), water (ml), and coffee decoction (ml) ingredients first."
                    else commitScope.launch {
                        val now = System.currentTimeMillis()
                        val batch = ProductionBatch(UUID.randomUUID().toString(), "Coffee decoction", now, 250, 1100, 1000, 100)
                        runCatching { db.dao().produceCoffeeDecoction(batch, powder, water, decoction) }
                            .onSuccess { message = "1,000 ml decoction produced and added to Kitchen stock." }
                            .onFailure { message = it.message ?: "Production failed" }
                    }
                }) { Text("Produce 1,000 ml decoction") }
            }
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Record wastage")
                LazyColumn(Modifier.heightIn(max = 130.dp)) {
                    items(ingredients, key = { it.id }) { item ->
                        Row { RadioButton(wastageItem == item.id, { wastageItem = item.id }); Text(item.name + " (" + item.baseUnit + ")", modifier = Modifier.padding(top = 12.dp)) }
                    }
                }
                Row {
                    FilterChip(wastageLocation == "STORE", { wastageLocation = "STORE" }, label = { Text("Store") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(wastageLocation == "KITCHEN", { wastageLocation = "KITCHEN" }, label = { Text("Kitchen") })
                }
                OutlinedTextField(wastageQty, { wastageQty = it }, label = { Text("Quantity (base units)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val quantity = runCatching { BigDecimal(wastageQty).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                    if (wastageItem.isBlank() || quantity == null || quantity <= 0 || reason.isBlank()) message = "Enter a valid quantity (up to 3 decimals) and reason"
                    else commitScope.launch {
                        val record = WastageRecord(UUID.randomUUID().toString(), wastageItem, wastageLocation, quantity, reason.trim(), System.currentTimeMillis(), 0)
                        runCatching { db.dao().saveWastage(record) }
                            .onSuccess { wastageQty = ""; reason = ""; message = "Wastage recorded and stock reduced using FIFO." }
                            .onFailure { message = it.message ?: "Wastage could not be recorded" }
                    }
                }) { Text("Save wastage") }
            }
        }
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Recent wastage", style = MaterialTheme.typography.titleMedium)
                    Text("₹%.2f".format(wastageRecords.sumOf { it.costPaise } / 100.0))
                }
                if (wastageRecords.isEmpty()) {
                    Text("No wastage entries recorded yet.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(wastageRecords.take(20), key = { it.id }) { record ->
                            val itemName = ingredients.firstOrNull { it.id == record.ingredientId }?.name ?: "Deleted ingredient"
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(itemName, style = MaterialTheme.typography.bodyMedium)
                                    Text("₹%.2f".format(record.costPaise / 100.0), style = MaterialTheme.typography.labelLarge)
                                }
                                val stockEffect = if (record.stockAdjusted) "Stock reduced" else "Stock unchanged"
                                val sourceLabel = if (record.sourceType == "FOOD_REFUND") "Food refund" else "Manual wastage"
                                Text("${record.quantityBaseUnits / 1000.0} ${ingredients.firstOrNull { it.id == record.ingredientId }?.baseUnit ?: ""} • ${record.location} • $sourceLabel • $stockEffect • ${record.reason}", style = MaterialTheme.typography.bodySmall)
                                Divider()
                            }
                        }
                    }
                }
            }
        }
        Text(message)
    }
}
