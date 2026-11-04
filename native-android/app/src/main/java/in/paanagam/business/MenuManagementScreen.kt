package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.CatalogItem
import `in`.paanagam.business.data.PaanagamDatabase
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Composable
fun MenuManagementScreen(db: PaanagamDatabase) {
    val items by db.dao().allCatalog().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var isCoffee by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val selected = items.firstOrNull { it.id == selectedId }

    LaunchedEffect(items, selectedId) {
        val item = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull()
        if (item != null && item.id != selectedId) selectedId = item.id
        if (item != null) {
            name = item.name
            category = item.category
            sku = item.sku
            price = BigDecimal.valueOf(item.pricePaise, 2).toPlainString()
            isCoffee = item.isCoffee
            active = item.active
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Menu management", style = MaterialTheme.typography.titleLarge)
        Text("Edit existing menu items, prices, categories and coffee loyalty eligibility. Deactivated items remain in past bills.")
        if (items.isEmpty()) {
            Text("No menu items yet. Create your first item below or load sample items from POS.")
            OutlinedTextField(name, { name = it }, label = { Text("First item name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(sku, { sku = it.trimStart().take(32) }, label = { Text("Product code / SKU (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(price, { price = it }, label = { Text("Selling price (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(isCoffee, { isCoffee = it })
                Text("Counts towards buy 10 coffees, get 1 free")
            }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                onClick = {
                    val paise = runCatching { BigDecimal(price).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                    if (name.isBlank() || category.isBlank() || paise == null || paise <= 0L) {
                        message = "Enter a name, category and valid price (up to 2 decimal places)."
                    } else scope.launch {
                        runCatching { db.dao().saveCatalogItemWithSku(CatalogItem(UUID.randomUUID().toString(), name.trim(), category.trim(), paise, isCoffee = isCoffee, active = true, sku = sku.trim())) }
                            .onSuccess { name = ""; category = ""; sku = ""; price = ""; isCoffee = false; message = "First menu item created."; selectedId = "" }
                            .onFailure { message = it.message ?: "Could not create menu item." }
                    }
                }
            ) { Text("SAVE MENU ITEM") }

        }
        if (items.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("₹%.2f • %s%s%s".format(item.pricePaise / 100.0, item.category, if (item.sku.isBlank()) "" else " • ${item.sku}", if (item.active) "" else " • Inactive")) },
                        trailingContent = { RadioButton(selected = item.id == selectedId, onClick = { selectedId = item.id }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (selected != null) {
            OutlinedTextField(name, { name = it }, label = { Text("Item name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(sku, { sku = it.trimStart().take(32) }, label = { Text("Product code / SKU (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(price, { price = it }, label = { Text("Selling price (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(isCoffee, { isCoffee = it })
                Text("Counts towards buy 10 coffees, get 1 free")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(active, { active = it })
                Text("Available for new POS orders")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val paise = runCatching { BigDecimal(price).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                    if (name.isBlank() || category.isBlank() || paise == null || paise <= 0L) {
                        message = "Enter a name, category and valid price (up to 2 decimal places)."
                    } else {
                        scope.launch {
                            runCatching { db.dao().saveCatalogItemWithSku(selected.copy(name = name.trim(), category = category.trim(), pricePaise = paise, isCoffee = isCoffee, active = active, sku = sku.trim())) }
                                .onSuccess { message = "Menu item updated." }
                                .onFailure { message = it.message ?: "Could not update menu item." }
                        }
                    }
                }
            ) { Text("Save menu changes") }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}
