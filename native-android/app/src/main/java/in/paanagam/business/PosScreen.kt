package `in`.paanagam.business

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID

private val PosWalnut = Color(0xFF3A2519)
private val PosBrown = Color(0xFF593923)
private val PosIvory = Color(0xFFFFF8E9)
private val PosMustard = Color(0xFFDDB36C)
private val PosOrange = Color(0xFFE97832)
private val PosMuted = Color(0xFF887766)

@Composable
fun PosScreen(db: PaanagamDatabase) {
    val catalog by db.dao().catalog().collectAsState(initial = emptyList())
    val sales by db.dao().sales().collectAsState(initial = emptyList())
    val heldOrders by db.dao().heldOrders().collectAsState(initial = emptyList())
    val recipeLines by db.dao().allRecipeLines().collectAsState(initial = emptyList())
    val comboComponents by db.dao().allComboComponents().collectAsState(initial = emptyList())
    val activeShift by db.dao().activeCashShift().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var cart by remember { mutableStateOf<Map<String, Pair<CatalogItem, Int>>>(emptyMap()) }
    var payment by remember { mutableStateOf("CASH") }
    var customerPhone by remember { mutableStateOf("") }
    var loyaltyRewards by remember { mutableIntStateOf(0) }
    var redeemFreeCoffee by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showHeldOrders by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Beverages") }
    var newPrice by remember { mutableStateOf("") }

    LaunchedEffect(customerPhone) {
        loyaltyRewards = if (customerPhone.length == 10) db.dao().loyaltyAccount(customerPhone)?.freeCoffeesAvailable ?: 0 else 0
        redeemFreeCoffee = false
    }

    val categories = remember(catalog) { listOf("All") + catalog.map { it.category.ifBlank { "Other" } }.distinct().sorted() }
    val visibleItems = remember(catalog, selectedCategory, search) {
        catalog.filter { item ->
            item.active &&
                (selectedCategory == "All" || item.category.ifBlank { "Other" } == selectedCategory) &&
                (search.isBlank() || item.name.contains(search.trim(), ignoreCase = true))
        }
    }
    val cartItems = cart.values.toList()
    val total = cartItems.sumOf { it.first.pricePaise * it.second }
    val itemCount = cartItems.sumOf { it.second }
    val coffeeItem = cartItems.firstOrNull { it.first.name.contains("coffee", ignoreCase = true) }?.first
    val missingRecipes = cartItems.map { it.first }.filter { item ->
        val components = comboComponents.filter { it.comboItemId == item.id }
        if (components.isEmpty()) recipeLines.none { it.menuItemId == item.id }
        else components.any { part -> recipeLines.none { it.menuItemId == part.componentItemId } }
    }
    val canCheckout = cart.isNotEmpty() && !saving && missingRecipes.isEmpty() &&
        (payment != "CASH" || activeShift != null) &&
        (!redeemFreeCoffee || (customerPhone.length == 10 && loyaltyRewards > 0 && coffeeItem != null))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosMenuPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategory = { selectedCategory = it },
                    search = search,
                    onSearch = { search = it },
                    items = visibleItems,
                    onAdd = { item -> cart = cart + (item.id to (item to ((cart[item.id]?.second ?: 0) + 1))) },
                    showAddItem = showAddItem,
                    onToggleAdd = { showAddItem = !showAddItem },
                    newName = newName, onNewName = { newName = it },
                    newCategory = newCategory, onNewCategory = { newCategory = it },
                    newPrice = newPrice, onNewPrice = { newPrice = it },
                    onSaveNew = {
                        val price = newPrice.toDoubleOrNull()
                        if (newName.isBlank() || price == null || price <= 0) message = "Enter a menu item and a valid price."
                        else scope.launch {
                            db.dao().saveCatalogItem(CatalogItem(UUID.randomUUID().toString(), newName.trim(), newCategory.trim(), (price * 100).toLong()))
                            newName = ""; newPrice = ""; showAddItem = false; message = "Menu item added."
                        }
                    }
                )
                PosCartPanel(
                    modifier = Modifier.width(340.dp).fillMaxHeight(),
                    cart = cartItems,
                    total = total,
                    itemCount = itemCount,
                    customerPhone = customerPhone,
                    onCustomerPhone = { customerPhone = it.filter(Char::isDigit).take(10) },
                    loyaltyRewards = loyaltyRewards,
                    redeemFreeCoffee = redeemFreeCoffee,
                    onRedeem = { redeemFreeCoffee = it },
                    coffeeItem = coffeeItem,
                    payment = payment,
                    onPayment = { payment = it },
                    activeShift = activeShift != null,
                    missingRecipes = missingRecipes,
                    saving = saving,
                    canCheckout = canCheckout,
                    message = message,
                    salesCount = sales.size,
                    onDecrease = { id, item, qty -> cart = if (qty <= 1) cart - id else cart + (id to (item to qty - 1)) },
                    onIncrease = { id, item, qty -> cart = cart + (id to (item to qty + 1)) },
                    onRemove = { id -> cart = cart - id },
                    onClear = { cart = emptyMap(); redeemFreeCoffee = false; message = "" },
                    onHold = {
                        val heldId = UUID.randomUUID().toString()
                        scope.launch {
                            runCatching {
                                db.dao().saveHeldOrder(
                                    HeldOrder(heldId, System.currentTimeMillis(), customerPhone, total),
                                    cartItems.map { (item, qty) -> HeldOrderLine(UUID.randomUUID().toString(), heldId, item.id, item.name, qty, item.pricePaise) }
                                )
                            }.onSuccess {
                                cart = emptyMap(); customerPhone = ""; redeemFreeCoffee = false
                                message = "Order held and saved on this device."
                            }.onFailure { message = it.message ?: "Could not hold order." }
                        }
                    },
                    onCheckout = {
                        saving = true
                        val saleId = UUID.randomUUID().toString()
                        val lines = cartItems.map { (item, qty) ->
                            SaleLine(UUID.randomUUID().toString(), saleId, item.id, item.name, qty, item.pricePaise, item.pricePaise * qty)
                        }.toMutableList()
                        if (redeemFreeCoffee && coffeeItem != null) {
                            lines.add(SaleLine(UUID.randomUUID().toString(), saleId, coffeeItem.id, coffeeItem.name, 1, 0L, 0L))
                        }
                        scope.launch {
                            runCatching { db.dao().completeSale(Sale(saleId, System.currentTimeMillis(), total, payment, customerPhone = customerPhone), lines) }
                                .onSuccess { cart = emptyMap(); customerPhone = ""; redeemFreeCoffee = false; loyaltyRewards = 0; message = "Bill saved successfully." }
                                .onFailure { message = it.message ?: "Checkout failed. No sale was confirmed." }
                            saving = false
                        }
                    }
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PosMenuPanel(
                    modifier = Modifier.weight(1f),
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategory = { selectedCategory = it },
                    search = search,
                    onSearch = { search = it },
                    items = visibleItems,
                    onAdd = { item -> cart = cart + (item.id to (item to ((cart[item.id]?.second ?: 0) + 1))) },
                    showAddItem = showAddItem,
                    onToggleAdd = { showAddItem = !showAddItem },
                    newName = newName, onNewName = { newName = it },
                    newCategory = newCategory, onNewCategory = { newCategory = it },
                    newPrice = newPrice, onNewPrice = { newPrice = it },
                    onSaveNew = {
                        val price = newPrice.toDoubleOrNull()
                        if (newName.isBlank() || price == null || price <= 0) message = "Enter a menu item and a valid price."
                        else scope.launch {
                            db.dao().saveCatalogItem(CatalogItem(UUID.randomUUID().toString(), newName.trim(), newCategory.trim(), (price * 100).toLong()))
                            newName = ""; newPrice = ""; showAddItem = false; message = "Menu item added."
                        }
                    }
                )
                PosCartPanel(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 390.dp),
                    cart = cartItems,
                    total = total,
                    itemCount = itemCount,
                    customerPhone = customerPhone,
                    onCustomerPhone = { customerPhone = it.filter(Char::isDigit).take(10) },
                    loyaltyRewards = loyaltyRewards,
                    redeemFreeCoffee = redeemFreeCoffee,
                    onRedeem = { redeemFreeCoffee = it },
                    coffeeItem = coffeeItem,
                    payment = payment,
                    onPayment = { payment = it },
                    activeShift = activeShift != null,
                    missingRecipes = missingRecipes,
                    saving = saving,
                    canCheckout = canCheckout,
                    message = message,
                    salesCount = sales.size,
                    onDecrease = { id, item, qty -> cart = if (qty <= 1) cart - id else cart + (id to (item to qty - 1)) },
                    onIncrease = { id, item, qty -> cart = cart + (id to (item to qty + 1)) },
                    onRemove = { id -> cart = cart - id },
                    onClear = { cart = emptyMap(); redeemFreeCoffee = false; message = "" },
                    onHold = {
                        val heldId = UUID.randomUUID().toString()
                        scope.launch {
                            runCatching {
                                db.dao().saveHeldOrder(
                                    HeldOrder(heldId, System.currentTimeMillis(), customerPhone, total),
                                    cartItems.map { (item, qty) -> HeldOrderLine(UUID.randomUUID().toString(), heldId, item.id, item.name, qty, item.pricePaise) }
                                )
                            }.onSuccess {
                                cart = emptyMap(); customerPhone = ""; redeemFreeCoffee = false
                                message = "Order held and saved on this device."
                            }.onFailure { message = it.message ?: "Could not hold order." }
                        }
                    },
                    onCheckout = {
                        saving = true
                        val saleId = UUID.randomUUID().toString()
                        val lines = cartItems.map { (item, qty) ->
                            SaleLine(UUID.randomUUID().toString(), saleId, item.id, item.name, qty, item.pricePaise, item.pricePaise * qty)
                        }.toMutableList()
                        if (redeemFreeCoffee && coffeeItem != null) lines.add(SaleLine(UUID.randomUUID().toString(), saleId, coffeeItem.id, coffeeItem.name, 1, 0L, 0L))
                        scope.launch {
                            runCatching { db.dao().completeSale(Sale(saleId, System.currentTimeMillis(), total, payment, customerPhone = customerPhone), lines) }
                                .onSuccess { cart = emptyMap(); customerPhone = ""; redeemFreeCoffee = false; loyaltyRewards = 0; message = "Bill saved successfully." }
                                .onFailure { message = it.message ?: "Checkout failed. No sale was confirmed." }
                            saving = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PosMenuPanel(
    modifier: Modifier,
    categories: List<String>,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    items: List<CatalogItem>,
    onAdd: (CatalogItem) -> Unit,
    showAddItem: Boolean,
    onToggleAdd: () -> Unit,
    newName: String,
    onNewName: (String) -> Unit,
    newCategory: String,
    onNewCategory: (String) -> Unit,
    newPrice: String,
    onNewPrice: (String) -> Unit,
    onSaveNew: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Counter POS", color = PosWalnut, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("பானகம்  •  காபியும் கதையும்", color = PosMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text("OFFLINE READY", color = PosBrown, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color(0xFFF3E6C9), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onToggleAdd) { Text(if (showAddItem) "Close" else "+ Menu item", color = PosBrown) }
        }
        if (showAddItem) {
            Card(colors = CardDefaults.cardColors(containerColor = PosIvory)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(newName, onNewName, label = { Text("Item name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(newCategory, onNewCategory, label = { Text("Category") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(newPrice, onNewPrice, label = { Text("Price ₹") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Button(onClick = onSaveNew, colors = ButtonDefaults.buttonColors(containerColor = PosWalnut)) { Text("Save menu item") }
                }
            }
        }
        OutlinedTextField(search, onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search menu items…") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { category ->
                    FilterChip(selected = selectedCategory == category, onClick = { onCategory(category) }, label = { Text(category) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Menu", color = PosWalnut, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${items.size} items", color = PosMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (items.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PosIvory)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("☕", style = MaterialTheme.typography.headlineLarge)
                    Text("No menu items found", color = PosWalnut, fontWeight = FontWeight.SemiBold)
                    Text("Add a menu item or change the search.", color = PosMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f)) {
                val columns = when { maxWidth >= 900.dp -> 4; maxWidth >= 560.dp -> 3; else -> 2 }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(item) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFFF7F0E4), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Text(menuEmoji(item.name), style = MaterialTheme.typography.headlineMedium)
                                }
                                Text(item.name, color = PosWalnut, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("₹${"%.2f".format(item.pricePaise / 100.0)}", color = PosBrown, fontWeight = FontWeight.Bold)
                                    Text("+ Add", color = PosOrange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
private fun PosCartPanel(
    modifier: Modifier,
    cart: List<Pair<CatalogItem, Int>>,
    total: Long,
    itemCount: Int,
    customerPhone: String,
    onCustomerPhone: (String) -> Unit,
    loyaltyRewards: Int,
    redeemFreeCoffee: Boolean,
    onRedeem: (Boolean) -> Unit,
    coffeeItem: CatalogItem?,
    payment: String,
    onPayment: (String) -> Unit,
    activeShift: Boolean,
    missingRecipes: List<CatalogItem>,
    saving: Boolean,
    canCheckout: Boolean,
    message: String,
    salesCount: Int,
    onDecrease: (String, CatalogItem, Int) -> Unit,
    onIncrease: (String, CatalogItem, Int) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onHold: () -> Unit,
    onCheckout: () -> Unit
) {
    Card(
        modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PosWalnut)
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Bill / Order", color = PosIvory, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Current counter order", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
                }
                Text("$itemCount items", color = PosWalnut, modifier = Modifier.background(PosMustard, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (cart.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF503625), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🧾", style = MaterialTheme.typography.headlineLarge)
                        Text("Select items from the menu", color = PosIvory, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cart, key = { it.first.id }) { (item, qty) ->
                        Row(Modifier.fillMaxWidth().background(Color(0xFF503625), RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = PosIvory, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text("₹${"%.2f".format(item.pricePaise / 100.0)} × $qty", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onDecrease(item.id, item, qty) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("−", color = PosIvory) }
                                Text(qty.toString(), color = PosIvory, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onIncrease(item.id, item, qty) }, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("+", color = PosMustard) }
                                TextButton(onClick = { onRemove(item.id) }, contentPadding = PaddingValues(horizontal = 3.dp)) { Text("×", color = Color(0xFFFFB59A)) }
                            }
                        }
                    }
                }
            }
            if (customerPhone.isNotBlank() || loyaltyRewards > 0) {
                Text("Customer loyalty", color = PosMustard, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedTextField(
                value = customerPhone,
                onValueChange = onCustomerPhone,
                label = { Text("Customer mobile (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PosIvory, unfocusedTextColor = PosIvory,
                    focusedBorderColor = PosMustard, unfocusedBorderColor = Color(0xFF98785B),
                    focusedLabelColor = PosMustard, unfocusedLabelColor = Color(0xFFD8C5A8),
                    cursorColor = PosMustard
                )
            )
            if (loyaltyRewards > 0 && coffeeItem != null && customerPhone.length == 10) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = redeemFreeCoffee, onCheckedChange = onRedeem, colors = CheckboxDefaults.colors(checkedColor = PosMustard, checkmarkColor = PosWalnut))
                    Text("Add 1 free coffee reward · $loyaltyRewards available", color = PosIvory, style = MaterialTheme.typography.bodySmall)
                }
            } else if (customerPhone.length == 10) {
                Text("Buy 10 paid coffees · earn 1 free", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Payment", color = PosIvory, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = payment == "CASH", onClick = { onPayment("CASH") }, label = { Text("Cash") })
                    FilterChip(selected = payment == "UPI", onClick = { onPayment("UPI") }, label = { Text("UPI") })
                }
            }
            if (payment == "CASH" && !activeShift) {
                Text("Open a cash shift before accepting cash payments.", color = Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall)
            }
            if (missingRecipes.isNotEmpty()) {
                Text("Recipe required: " + missingRecipes.joinToString { it.name }, color = Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider(color = Color(0xFF76553A))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL", color = PosIvory, fontWeight = FontWeight.Bold)
                Text("₹${"%.2f".format(total / 100.0)}", color = PosMustard, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear, enabled = cart.isNotEmpty() && !saving, modifier = Modifier.weight(0.8f), colors = ButtonDefaults.outlinedButtonColors(contentColor = PosIvory)) { Text("Clear") }
                OutlinedButton(onClick = onHold, enabled = cart.isNotEmpty() && !saving, modifier = Modifier.weight(0.9f), colors = ButtonDefaults.outlinedButtonColors(contentColor = PosMustard)) { Text("Hold") }
                Button(onClick = onCheckout, enabled = canCheckout, modifier = Modifier.weight(1.5f), colors = ButtonDefaults.buttonColors(containerColor = PosOrange, contentColor = Color.White)) {
                    Text(if (saving) "Saving…" else "Complete sale")
                }
            }
            if (message.isNotBlank()) Text(message, color = if (message.contains("success", true) || message.contains("added", true)) PosMustard else Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall, maxLines = 2)
            Text("$salesCount saved bills on this device", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun menuEmoji(name: String): String {
    val value = name.lowercase()
    return when {
        "tea" in value || "chai" in value -> "🍵"
        "chicken" in value -> "🍗"
        "fish" in value || "meal" in value || "rice" in value -> "🍛"
        "appam" in value || "breakfast" in value -> "🥞"
        "juice" in value || "lemon" in value -> "🥤"
        "snack" in value || "bun" in value -> "🥯"
        else -> "☕"
    }
}
