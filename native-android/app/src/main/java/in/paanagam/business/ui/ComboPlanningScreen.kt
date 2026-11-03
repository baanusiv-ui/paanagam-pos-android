package `in`.paanagam.business.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.RoomDatabase
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ComboPlanningScreen(db: PaanagamDatabase) {
    val catalog by db.dao().catalog().collectAsState(initial = emptyList())
    val ingredients by db.dao().ingredients().collectAsState(initial = emptyList())
    val combos by db.dao().allComboComponents().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var comboName by remember { mutableStateOf("") }
    var comboPrice by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<CatalogItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    var draft by remember { mutableStateOf(listOf<ComboComponent>()) }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(combos, catalog, ingredients) {
        val result = mutableMapOf<String, String>()
        combos.groupBy { it.comboItemId }.forEach { (comboId, parts) ->
            val missing = parts.filter { part -> catalog.none { it.id == part.componentItemId } }
            val unconfigured = parts.filter { part -> db.dao().recipeLines(part.componentItemId).isEmpty() }
            result[comboId] = when {
                missing.isNotEmpty() -> "Component unavailable"
                unconfigured.isNotEmpty() -> "Recipe missing"
                else -> "Recipes configured"
            }
        }
        status = result
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Combo planning", style = MaterialTheme.typography.headlineSmall)
        Text("Create a sellable combo from existing menu items. POS expands every component recipe and consumes combined ingredient quantities using FIFO.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(comboName, { comboName = it }, label = { Text("Combo name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(comboPrice, { comboPrice = it.filter(Char::isDigit) }, label = { Text("Selling price (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedItem?.name ?: "Choose menu component") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                catalog.filter { it.active }.forEach { item ->
                    DropdownMenuItem(text = { Text(item.name) }, onClick = { selectedItem = item; expanded = false })
                }
            }
        }
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(3) }, label = { Text("Component quantity") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(onClick = {
            val item = selectedItem
            val qty = quantity.toIntOrNull() ?: 0
            if (item == null || qty < 1) message = "Select a component and enter a quantity."
            else if (draft.any { it.componentItemId == item.id }) {
                draft = draft.map { if (it.componentItemId == item.id) it.copy(quantity = it.quantity + qty) else it }
                selectedItem = null
            } else {
                draft = draft + ComboComponent(UUID.randomUUID().toString(), "DRAFT", item.id, item.name, qty)
                selectedItem = null
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Add component") }
        if (draft.isNotEmpty()) {
            Text("Combo contents", style = MaterialTheme.typography.titleMedium)
            draft.forEach { Text("• ${it.componentName} × ${it.quantity}") }
            OutlinedButton(onClick = { draft = emptyList() }) { Text("Clear components") }
        }
        Button(onClick = {
            val price = comboPrice.toLongOrNull()
            if (comboName.isBlank() || price == null || price <= 0 || draft.isEmpty()) message = "Enter combo name, valid price and at least one component."
            else scope.launch {
                val id = UUID.randomUUID().toString()
                val item = CatalogItem(id, comboName.trim(), "Combos", price * 100L, true)
                db.dao().saveCatalogItem(item)
                db.dao().replaceComboComponents(id, draft.map { it.copy(comboItemId = id) })
                comboName = ""; comboPrice = ""; draft = emptyList(); message = "Combo saved. POS will calculate component recipes and FIFO stock at checkout."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save combo") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        Divider()
        Text("Saved combos", style = MaterialTheme.typography.titleLarge)
        val grouped = combos.groupBy { it.comboItemId }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(grouped.keys.toList()) { id ->
                val item = catalog.firstOrNull { it.id == id }
                val parts = grouped[id].orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item?.name ?: "Unavailable combo", style = MaterialTheme.typography.titleMedium)
                        Text("₹${"%.2f".format((item?.pricePaise ?: 0L) / 100.0)}")
                        parts.forEach { Text("• ${it.componentName} × ${it.quantity}", style = MaterialTheme.typography.bodySmall) }
                        Text(status[id] ?: "Checking recipes…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
