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
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

private val PosWalnut = Color(0xFF3A2519)
private val PosBrown = Color(0xFF593923)
private val PosIvory = Color(0xFFFFF8E9)
private val PosMustard = Color(0xFFDDB36C)
private val PosOrange = Color(0xFFE97832)
private val PosMuted = Color(0xFF887766)

@Composable
fun PosScreen(db: PaanagamDatabase, staff: StaffMember) {
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
    var cashPartInput by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var loyaltyRewards by remember { mutableIntStateOf(0) }
    var redeemFreeCoffee by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showHeldOrders by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    val cartAuditSessionId = remember { UUID.randomUUID().toString() }
    var showAddItem by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Beverages") }
    var newPrice by remember { mutableStateOf("") }
    var newIsCoffee by remember { mutableStateOf(false) }

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
    val total = cartItems.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.first.pricePaise, line.second.toLong())) }
    val cashPartPaise = when (payment) {
        "CASH" -> total
        "UPI" -> 0L
        else -> runCatching { BigDecimal(cashPartInput).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrDefault(-1L)
    }
    val upiPartPaise = total - cashPartPaise
    val itemCount = cartItems.sumOf { it.second }
    val coffeeItem = cartItems.firstOrNull { it.first.isCoffee }?.first
    val missingRecipes = cartItems.map { it.first }.filter { item ->
        val components = comboComponents.filter { it.comboItemId == item.id }
        if (components.isEmpty()) recipeLines.none { it.menuItemId == item.id }
        else components.any { part -> recipeLines.none { it.menuItemId == part.componentItemId } }
    }
    fun recordPreCheckoutAudit(eventType: String, reason: String, amountPaise: Long, onSaved: () -> Unit) {
        scope.launch {
            runCatching {
                db.dao().insertCctvAuditEvent(
                    CctvAuditEvent(
                        UUID.randomUUID().toString(),
                        "PRECHECKOUT-$cartAuditSessionId",
                        eventType,
                        reason.trim(),
                        amountPaise,
                        staff.id,
                        staff.name,
                        System.currentTimeMillis(),
                        "LOCAL_BOOKMARK"
                    )
                )
            }.onSuccess {
                onSaved()
            }.onFailure { error ->
                saving = false
                message = "Cancellation was not applied because its audit record could not be saved: " +
                    (error.message ?: "database error")
            }
        }
    }

    val canSeedDemoMenu = staff.role == "ADMIN" && catalog.isEmpty() && !saving
    fun loadDemoMenu() {
        if (!canSeedDemoMenu) return
        saving = true
        scope.launch {
            runCatching {
                val existing = db.dao().allCatalog().first()
                check(existing.isEmpty()) { "Menu already contains items. Demo data was not added." }
                db.dao().saveCatalog(
                    listOf(
                        CatalogItem("demo-filter-coffee", "Filter Coffee", "Beverages", 2500L, isCoffee = true),
                        CatalogItem("demo-strong-coffee", "Strong Filter Coffee", "Beverages", 3000L, isCoffee = true),
                        CatalogItem("demo-ginger-tea", "Ginger Tea", "Tea", 2000L),
                        CatalogItem("demo-lemon-tea", "Lemon Tea", "Tea", 2500L),
                        CatalogItem("demo-masala-sundal", "Masala Sundal", "Snacks", 3000L),
                        CatalogItem("demo-chicken-fry", "Chicken Fry", "Non-Veg", 9000L)
                    )
                )
            }.onSuccess { message = "6 sample menu items loaded. These are demo prices, not stock or sales records." }
             .onFailure { message = it.message ?: "Could not load sample menu." }
            saving = false
        }
    }

    val canCheckout = cart.isNotEmpty() && !saving && missingRecipes.isEmpty() &&
        cashPartPaise in 0L..total && upiPartPaise >= 0L &&
        (payment != "SPLIT" || (cashPartPaise > 0L && upiPartPaise > 0L)) &&
        (cashPartPaise == 0L || activeShift != null) &&
        (!redeemFreeCoffee || (customerPhone.length == 10 && loyaltyRewards > 0 && coffeeItem != null))

    BoxWithConstraints(Modifier.fillMaxSize()) {

        if (showHeldOrders) {
            AlertDialog(
                onDismissRequest = { showHeldOrders = false },
                title = { Text("Held orders (" + heldOrders.size + ")") },
                text = {
                    if (heldOrders.isEmpty()) Text("No held orders saved.")
                    else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        heldOrders.forEach { held ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("₹%.2f".format(held.totalPaise / 100.0), fontWeight = FontWeight.Bold)
                                    Text(if (held.customerPhone.isBlank()) "Walk-in customer" else held.customerPhone, style = MaterialTheme.typography.labelSmall)
                                    Text(java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(held.createdAt)), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        val savedLines = db.dao().heldOrderLines(held.id)
                                        val restored = savedLines.associate { line ->
                                            val item = catalog.firstOrNull { it.id == line.itemId }
                                                ?: CatalogItem(line.itemId, line.itemName, "Other", line.unitPricePaise)
                                            line.itemId to (item to line.quantity)
                                        }
                                        cart = restored
                                        customerPhone = held.customerPhone
                                        db.dao().removeHeldOrder(held.id)
                                        showHeldOrders = false
                                        message = "Held order restored."
                                    }
                                }) { Text("Resume") }
                                TextButton(onClick = { scope.launch { db.dao().removeHeldOrder(held.id) } }) { Text("Delete") }
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showHeldOrders = false }) { Text("Close") } }
            )
        }

        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { if (!saving) showCancelDialog = false },
                title = { Text("Cancel current POS cart?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This cart has not been checked out. Enter a reason; the cashier, amount and time will be saved in the local audit log before the cart is cleared.")
                        OutlinedTextField(
                            value = cancelReason,
                            onValueChange = { cancelReason = it },
                            label = { Text("Cancellation reason") },
                            placeholder = { Text("Customer changed order, duplicate entry, etc.") },
                            minLines = 2,
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !saving && cancelReason.isNotBlank() && cart.isNotEmpty(),
                        onClick = {
                            saving = true
                            recordPreCheckoutAudit("CART_VOID", cancelReason, total) {
                                cart = emptyMap()
                                redeemFreeCoffee = false
                                customerPhone = ""
                                cashPartInput = ""
                                loyaltyRewards = 0
                                cancelReason = ""
                                showCancelDialog = false
                                saving = false
                                message = "Cart cancelled. POS audit bookmark saved."
                            }
                        }
                    ) { Text(if (saving) "Saving…" else "Record & cancel") }
                },
                dismissButton = {
                    TextButton(enabled = !saving, onClick = { showCancelDialog = false }) { Text("Keep cart") }
                }
            )
        }

        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PosMenuPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    saving = saving,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategory = { selectedCategory = it },
                    search = search,
                    onSearch = { search = it },
                    items = visibleItems,
                    cartQuantities = cart.mapValues { it.value.second },
                    onAdd = { item -> cart = cart + (item.id to (item to ((cart[item.id]?.second ?: 0) + 1))); message = item.name + " added to bill." },
                    showAddItem = showAddItem,
                    onToggleAdd = { showAddItem = !showAddItem },
                    canSeedDemoMenu = canSeedDemoMenu,
                    onSeedDemoMenu = { loadDemoMenu() },
                    heldCount = heldOrders.size,
                    onShowHeld = { showHeldOrders = true },
                    newName = newName, onNewName = { newName = it },
                    newCategory = newCategory, onNewCategory = { newCategory = it },
                    newPrice = newPrice, onNewPrice = { newPrice = it },
                    newIsCoffee = newIsCoffee, onNewIsCoffee = { newIsCoffee = it },
                    onSaveNew = {
                        val pricePaise = runCatching { BigDecimal(newPrice).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                        if (newName.isBlank() || pricePaise == null || pricePaise <= 0) message = "Enter a menu item and a valid price with up to 2 decimals."
                        else scope.launch {
                            db.dao().saveCatalogItem(CatalogItem(UUID.randomUUID().toString(), newName.trim(), newCategory.trim(), pricePaise, isCoffee = newIsCoffee))
                            newName = ""; newPrice = ""; newIsCoffee = false; showAddItem = false; message = "Menu item added."
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
                    cashPartInput = cashPartInput,
                    onCashPartInput = { cashPartInput = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                    upiRemainder = upiPartPaise,
                    activeShift = activeShift != null,
                    missingRecipes = missingRecipes,
                    saving = saving,
                    canCheckout = canCheckout,
                    message = message,
                    salesCount = sales.size,
                    onDecrease = { id, item, qty ->
                        saving = true
                        recordPreCheckoutAudit("CART_ITEM_VOID", "Reduced ${item.name} quantity by 1 before checkout", item.pricePaise) {
                            cart = if (qty <= 1) cart - id else cart + (id to (item to qty - 1))
                            saving = false
                        }
                    },
                    onIncrease = { id, item, qty -> cart = cart + (id to (item to qty + 1)) },
                    onRemove = { id ->
                        val removed = cart[id]
                        if (removed != null) {
                            saving = true
                            recordPreCheckoutAudit("CART_ITEM_VOID", "Removed ${removed.first.name} before checkout", removed.first.pricePaise * removed.second) {
                                cart = cart - id
                                saving = false
                            }
                        }
                    },
                    onClear = { cancelReason = ""; showCancelDialog = true },
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
                            runCatching { db.dao().completeSale(Sale(saleId, System.currentTimeMillis(), total, payment, customerPhone = customerPhone, cashPaidPaise = cashPartPaise, upiPaidPaise = upiPartPaise), lines) }
                                .onSuccess { cart = emptyMap(); customerPhone = ""; cashPartInput = ""; redeemFreeCoffee = false; loyaltyRewards = 0; message = "Bill saved successfully." }
                                .onFailure { message = it.message ?: "Checkout failed. No sale was confirmed." }
                            saving = false
                        }
                    }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().imePadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PosMenuPanel(
                    modifier = Modifier.weight(1f),
                    saving = saving,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategory = { selectedCategory = it },
                    search = search,
                    onSearch = { search = it },
                    items = visibleItems,
                    cartQuantities = cart.mapValues { it.value.second },
                    onAdd = { item -> cart = cart + (item.id to (item to ((cart[item.id]?.second ?: 0) + 1))); message = item.name + " added to bill." },
                    showAddItem = showAddItem,
                    onToggleAdd = { showAddItem = !showAddItem },
                    canSeedDemoMenu = canSeedDemoMenu,
                    onSeedDemoMenu = { loadDemoMenu() },
                    heldCount = heldOrders.size,
                    onShowHeld = { showHeldOrders = true },
                    newName = newName, onNewName = { newName = it },
                    newCategory = newCategory, onNewCategory = { newCategory = it },
                    newPrice = newPrice, onNewPrice = { newPrice = it },
                    newIsCoffee = newIsCoffee, onNewIsCoffee = { newIsCoffee = it },
                    onSaveNew = {
                        val pricePaise = runCatching { BigDecimal(newPrice).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                        if (newName.isBlank() || pricePaise == null || pricePaise <= 0) message = "Enter a menu item and a valid price with up to 2 decimals."
                        else scope.launch {
                            db.dao().saveCatalogItem(CatalogItem(UUID.randomUUID().toString(), newName.trim(), newCategory.trim(), pricePaise, isCoffee = newIsCoffee))
                            newName = ""; newPrice = ""; showAddItem = false; message = "Menu item added."
                        }
                    }
                )
                PosCartPanel(
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
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
                    cashPartInput = cashPartInput,
                    onCashPartInput = { cashPartInput = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                    upiRemainder = upiPartPaise,
                    activeShift = activeShift != null,
                    missingRecipes = missingRecipes,
                    saving = saving,
                    canCheckout = canCheckout,
                    message = message,
                    salesCount = sales.size,
                    onDecrease = { id, item, qty ->
                        saving = true
                        recordPreCheckoutAudit("CART_ITEM_VOID", "Reduced ${item.name} quantity by 1 before checkout", item.pricePaise) {
                            cart = if (qty <= 1) cart - id else cart + (id to (item to qty - 1))
                            saving = false
                        }
                    },
                    onIncrease = { id, item, qty -> cart = cart + (id to (item to qty + 1)) },
                    onRemove = { id ->
                        val removed = cart[id]
                        if (removed != null) {
                            saving = true
                            recordPreCheckoutAudit("CART_ITEM_VOID", "Removed ${removed.first.name} before checkout", removed.first.pricePaise * removed.second) {
                                cart = cart - id
                                saving = false
                            }
                        }
                    },
                    onClear = { cancelReason = ""; showCancelDialog = true },
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
                            runCatching { db.dao().completeSale(Sale(saleId, System.currentTimeMillis(), total, payment, customerPhone = customerPhone, cashPaidPaise = cashPartPaise, upiPaidPaise = upiPartPaise), lines) }
                                .onSuccess { cart = emptyMap(); customerPhone = ""; cashPartInput = ""; redeemFreeCoffee = false; loyaltyRewards = 0; message = "Bill saved successfully." }
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
    saving: Boolean,
    categories: List<String>,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    items: List<CatalogItem>,
    cartQuantities: Map<String, Int>,
    onAdd: (CatalogItem) -> Unit,
    showAddItem: Boolean,
    onToggleAdd: () -> Unit,
    canSeedDemoMenu: Boolean,
    onSeedDemoMenu: () -> Unit,
    heldCount: Int,
    onShowHeld: () -> Unit,
    newName: String,
    onNewName: (String) -> Unit,
    newCategory: String,
    onNewCategory: (String) -> Unit,
    newPrice: String,
    onNewPrice: (String) -> Unit,
    newIsCoffee: Boolean,
    onNewIsCoffee: (Boolean) -> Unit,
    onSaveNew: () -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 560.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Counter POS", color = PosWalnut, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("OFFLINE READY", color = PosBrown, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFF3E6C9), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                    Text("பானகம் • காபியும் கதையும்", color = PosMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onShowHeld, enabled = !saving, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Held $heldCount", color = PosOrange) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onToggleAdd, enabled = !saving, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (showAddItem) "Close" else "+ Menu item", color = PosBrown) }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Counter POS", color = PosWalnut, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("பானகம்  •  காபியும் கதையும்", color = PosMuted, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("OFFLINE READY", color = PosBrown, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFFF3E6C9), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
                    TextButton(onClick = onShowHeld, enabled = !saving) { Text("Held $heldCount", color = PosOrange) }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = onToggleAdd, enabled = !saving) { Text(if (showAddItem) "Close" else "+ Menu item", color = PosBrown) }
                }
            }
        }
        if (showAddItem) {
            Card(colors = CardDefaults.cardColors(containerColor = PosIvory)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(newName, onNewName, label = { Text("Item name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(newCategory, onNewCategory, label = { Text("Category") }, modifier = Modifier.weight(1f), singleLine = true, enabled = !saving)
                        OutlinedTextField(newPrice, onNewPrice, label = { Text("Price ₹") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !saving)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newIsCoffee, onCheckedChange = onNewIsCoffee, enabled = !saving)
                        Text("Counts toward coffee loyalty", color = PosBrown, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onSaveNew, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = PosWalnut)) { Text("Save menu item") }
                }
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !saving,
            placeholder = { Text("Search product name or code") },
            shape = RoundedCornerShape(12.dp)
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val columns = when { maxWidth >= 900.dp -> 3; maxWidth >= 560.dp -> 3; else -> 2 }
            if (maxWidth >= 560.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier.width(154.dp).fillMaxHeight()
                            .background(Color(0xFFF4F1EC), RoundedCornerShape(14.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("CATEGORIES", color = PosMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        categories.forEach { category ->
                            TextButton(
                                onClick = { onCategory(category) },
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    category,
                                    color = if (selectedCategory == category) PosOrange else PosBrown,
                                    fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selectedCategory == "All") "All products" else selectedCategory, color = PosWalnut, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                gridItems(items, key = { it.id }) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { onAdd(item) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.fillMaxWidth().height(86.dp).background(Color(0xFFF7F0E4), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                                Text(menuEmoji(item.name), style = MaterialTheme.typography.headlineLarge)
                                            }
                                            Text(item.name, color = PosWalnut, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("₹${"%.2f".format(item.pricePaise / 100.0)}", color = PosBrown, fontWeight = FontWeight.Bold)
                                                val inBill = cartQuantities[item.id] ?: 0
                                                Text(if (inBill > 0) "✓ ×$inBill" else "+ Add", color = if (inBill > 0) Color(0xFF16845B) else PosOrange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { category ->
                            FilterChip(selected = selectedCategory == category, onClick = { onCategory(category) }, enabled = !saving, label = { Text(category) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selectedCategory == "All") "All products" else selectedCategory, color = PosWalnut, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${items.size} items", color = PosMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    if (items.isEmpty()) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PosIvory)) {
                            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("☕", style = MaterialTheme.typography.headlineLarge)
                                Text("No menu items found", color = PosWalnut, fontWeight = FontWeight.SemiBold)
                                Text("Add a menu item or change the search.", color = PosMuted, style = MaterialTheme.typography.bodySmall)
                                if (canSeedDemoMenu) Button(onClick = onSeedDemoMenu, enabled = !saving, colors = ButtonDefaults.buttonColors(containerColor = PosOrange)) { Text("Load 6 sample menu items") }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            gridItems(items, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { onAdd(item) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(Modifier.fillMaxWidth().height(64.dp).background(Color(0xFFF7F0E4), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                            Text(menuEmoji(item.name), style = MaterialTheme.typography.headlineMedium)
                                        }
                                        Text(item.name, color = PosWalnut, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("₹${"%.2f".format(item.pricePaise / 100.0)}", color = PosBrown, fontWeight = FontWeight.Bold)
                                            val inBill = cartQuantities[item.id] ?: 0
                                            Text(if (inBill > 0) "✓ ×$inBill" else "+ Add", color = if (inBill > 0) Color(0xFF16845B) else PosOrange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
private fun PosCartPanel(
    modifier: Modifier,
    compact: Boolean = false,
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
    cashPartInput: String,
    onCashPartInput: (String) -> Unit,
    upiRemainder: Long,
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
    if (compact) {
        var showCustomerDetails by remember { mutableStateOf(false) }
        Card(
            modifier.fillMaxWidth().heightIn(max = 250.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PosWalnut)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bill · $itemCount items", color = PosIvory, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showCustomerDetails = !showCustomerDetails }, enabled = !saving, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text(if (showCustomerDetails) "Hide customer" else if (customerPhone.isBlank()) "Add customer" else "Customer ✓", color = PosMustard)
                    }
                }
                if (cart.isEmpty()) {
                    Text("Select items from the menu", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 92.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(cart, key = { it.first.id }) { (item, qty) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = PosIvory, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("₹%.2f × $qty".format(item.pricePaise / 100.0), color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { onDecrease(item.id, item, qty) }, enabled = !saving, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("−", color = PosIvory) }
                                Text("$qty", color = PosIvory, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onIncrease(item.id, item, qty) }, enabled = !saving, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("+", color = PosMustard) }
                                TextButton(onClick = { onRemove(item.id) }, enabled = !saving, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("×", color = Color(0xFFFFB59A)) }
                            }
                        }
                    }
                }
                if (showCustomerDetails) {
                    OutlinedTextField(
                        value = customerPhone, onValueChange = onCustomerPhone, enabled = !saving,
                        label = { Text("Customer mobile (optional)") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PosIvory, unfocusedTextColor = PosIvory,
                            focusedBorderColor = PosMustard, unfocusedBorderColor = Color(0xFF98785B),
                            focusedLabelColor = PosMustard, unfocusedLabelColor = Color(0xFFD8C5A8), cursorColor = PosMustard
                        )
                    )
                    if (loyaltyRewards > 0 && coffeeItem != null && customerPhone.length == 10) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = redeemFreeCoffee, onCheckedChange = onRedeem, enabled = !saving,
                                colors = CheckboxDefaults.colors(checkedColor = PosMustard, checkmarkColor = PosWalnut))
                            Text("Redeem 1 free coffee · $loyaltyRewards available", color = PosIvory, style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (customerPhone.length == 10) {
                        Text("Buy 10 paid coffees · earn 1 free", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Payment", color = PosIvory, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = payment == "CASH", onClick = { onPayment("CASH") }, enabled = !saving, label = { Text("Cash") })
                        FilterChip(selected = payment == "UPI", onClick = { onPayment("UPI") }, enabled = !saving, label = { Text("UPI") })
                        FilterChip(selected = payment == "SPLIT", onClick = { onPayment("SPLIT") }, enabled = !saving, label = { Text("Split") })
                    }
                }
                if (payment == "SPLIT") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = cashPartInput, onValueChange = onCashPartInput, enabled = !saving,
                            label = { Text("Cash ₹") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PosIvory, unfocusedTextColor = PosIvory,
                                focusedBorderColor = PosMustard, unfocusedBorderColor = Color(0xFF98785B),
                                focusedLabelColor = PosMustard, unfocusedLabelColor = Color(0xFFD8C5A8)))
                        Text("UPI ₹%.2f".format(upiRemainder / 100.0), color = PosMustard, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if ((payment == "CASH" || payment == "SPLIT") && !activeShift) {
                    Text("Open a cash shift before accepting cash payments.", color = Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall)
                }
                if (missingRecipes.isNotEmpty()) {
                    Text("Recipe required: " + missingRecipes.joinToString { it.name }, color = Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                HorizontalDivider(color = Color(0xFF76553A))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", color = PosIvory, fontWeight = FontWeight.Bold)
                    Text("₹%.2f".format(total / 100.0), color = PosMustard, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onClear, enabled = cart.isNotEmpty() && !saving, modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PosIvory)) { Text("Clear") }
                    OutlinedButton(onClick = onHold, enabled = cart.isNotEmpty() && !saving, modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PosMustard)) { Text("Hold") }
                    Button(onClick = onCheckout, enabled = canCheckout, modifier = Modifier.weight(1.5f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PosOrange, contentColor = Color.White)) {
                        Text(if (saving) "Saving…" else "Complete sale", maxLines = 1)
                    }
                }
                if (message.isNotBlank()) Text(message, color = if (message.contains("success", true) || message.contains("added", true)) PosMustard else Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    } else {
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
                                    Text("₹%.2f × $qty".format(item.pricePaise / 100.0), color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { onDecrease(item.id, item, qty) }, enabled = !saving, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("−", color = PosIvory) }
                                    Text(qty.toString(), color = PosIvory, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { onIncrease(item.id, item, qty) }, enabled = !saving, contentPadding = PaddingValues(horizontal = 5.dp)) { Text("+", color = PosMustard) }
                                    TextButton(onClick = { onRemove(item.id) }, enabled = !saving, contentPadding = PaddingValues(horizontal = 3.dp)) { Text("×", color = Color(0xFFFFB59A)) }
                                }
                            }
                        }
                    }
                }
                if (customerPhone.isNotBlank() || loyaltyRewards > 0) Text("Customer loyalty", color = PosMustard, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = customerPhone, onValueChange = onCustomerPhone, enabled = !saving,
                    label = { Text("Customer mobile (optional)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PosIvory, unfocusedTextColor = PosIvory,
                        focusedBorderColor = PosMustard, unfocusedBorderColor = Color(0xFF98785B),
                        focusedLabelColor = PosMustard, unfocusedLabelColor = Color(0xFFD8C5A8), cursorColor = PosMustard)
                )
                if (loyaltyRewards > 0 && coffeeItem != null && customerPhone.length == 10) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = redeemFreeCoffee, onCheckedChange = onRedeem, enabled = !saving, colors = CheckboxDefaults.colors(checkedColor = PosMustard, checkmarkColor = PosWalnut))
                        Text("Add 1 free coffee reward · $loyaltyRewards available", color = PosIvory, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (customerPhone.length == 10) Text("Buy 10 paid coffees · earn 1 free", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Payment", color = PosIvory, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = payment == "CASH", onClick = { onPayment("CASH") }, enabled = !saving, label = { Text("Cash") })
                        FilterChip(selected = payment == "UPI", onClick = { onPayment("UPI") }, enabled = !saving, label = { Text("UPI") })
                        FilterChip(selected = payment == "SPLIT", onClick = { onPayment("SPLIT") }, enabled = !saving, label = { Text("Split") })
                    }
                }
                if (payment == "SPLIT") {
                    OutlinedTextField(value = cashPartInput, onValueChange = onCashPartInput, enabled = !saving,
                        label = { Text("Cash portion ₹") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PosIvory, unfocusedTextColor = PosIvory,
                            focusedBorderColor = PosMustard, unfocusedBorderColor = Color(0xFF98785B),
                            focusedLabelColor = PosMustard, unfocusedLabelColor = Color(0xFFD8C5A8)))
                    Text("UPI balance: ₹%.2f".format(upiRemainder / 100.0), color = PosMustard, style = MaterialTheme.typography.labelSmall)
                }
                if ((payment == "CASH" || payment == "SPLIT") && !activeShift) Text("Open a cash shift before accepting cash payments.", color = Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall)
                if (missingRecipes.isNotEmpty()) Text("Recipe required: " + missingRecipes.joinToString { it.name }, color = Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall)
                HorizontalDivider(color = Color(0xFF76553A))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", color = PosIvory, fontWeight = FontWeight.Bold)
                    Text("₹%.2f".format(total / 100.0), color = PosMustard, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClear, enabled = cart.isNotEmpty() && !saving, modifier = Modifier.weight(0.8f), colors = ButtonDefaults.outlinedButtonColors(contentColor = PosIvory)) { Text("Clear") }
                    OutlinedButton(onClick = onHold, enabled = cart.isNotEmpty() && !saving, modifier = Modifier.weight(0.9f), colors = ButtonDefaults.outlinedButtonColors(contentColor = PosMustard)) { Text("Hold") }
                    Button(onClick = onCheckout, enabled = canCheckout, modifier = Modifier.weight(1.5f), colors = ButtonDefaults.buttonColors(containerColor = PosOrange, contentColor = Color.White)) { Text(if (saving) "Saving…" else "Complete sale") }
                }
                if (message.isNotBlank()) Text(message, color = if (message.contains("success", true) || message.contains("added", true)) PosMustard else Color(0xFFFFC28D), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                Text("$salesCount saved bills on this device", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.labelSmall)
            }
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
