package `in`.paanagam.business

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.paanagam.business.data.PaanagamDatabase
import `in`.paanagam.business.data.SopDocument
import `in`.paanagam.business.data.StaffMember
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.UUID
import kotlinx.coroutines.launch

private val Espresso = Color(0xFF3A2519)
private val Ivory = Color(0xFFFFF8E9)
private val Gold = Color(0xFFB87928)
private val Ink = Color(0xFF2B1B12)
private val Mint = Color(0xFFF3E6C9)
private val White = Color(0xFFFFFFFF)

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_lines (id TEXT NOT NULL PRIMARY KEY, saleId TEXT NOT NULL, itemId TEXT NOT NULL, itemName TEXT NOT NULL, quantity INTEGER NOT NULL, unitPricePaise INTEGER NOT NULL, lineTotalPaise INTEGER NOT NULL)")
    }
}


private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ingredients (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, baseUnit TEXT NOT NULL, isCoffeePowder INTEGER NOT NULL, active INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS purchases (id TEXT NOT NULL PRIMARY KEY, supplierName TEXT NOT NULL, invoiceNumber TEXT NOT NULL, purchasedAt INTEGER NOT NULL, totalPaise INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_lines (id TEXT NOT NULL PRIMARY KEY, purchaseId TEXT NOT NULL, ingredientId TEXT NOT NULL, quantityBaseUnits INTEGER NOT NULL, unitCostPaise INTEGER NOT NULL, lineTotalPaise INTEGER NOT NULL, lotId TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_transfers (id TEXT NOT NULL PRIMARY KEY, fromLocation TEXT NOT NULL, toLocation TEXT NOT NULL, transferredAt INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (id TEXT NOT NULL PRIMARY KEY, ingredientId TEXT NOT NULL, lotId TEXT NOT NULL, movementType TEXT NOT NULL, fromLocation TEXT NOT NULL, toLocation TEXT NOT NULL, quantityBaseUnits INTEGER NOT NULL, unitCostPaise INTEGER NOT NULL, referenceId TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("ALTER TABLE stock_lots ADD COLUMN sourceLotId TEXT")
    }
}


private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS production_batches (id TEXT NOT NULL PRIMARY KEY, productName TEXT NOT NULL, producedAt INTEGER NOT NULL, inputPowderGrams INTEGER NOT NULL, inputWaterMl INTEGER NOT NULL, outputDecoctionMl INTEGER NOT NULL, retainedGroundsMl INTEGER NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS production_inputs (id TEXT NOT NULL PRIMARY KEY, batchId TEXT NOT NULL, ingredientId TEXT NOT NULL, quantityBaseUnits INTEGER NOT NULL, costPaise INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS wastage_records (id TEXT NOT NULL PRIMARY KEY, ingredientId TEXT NOT NULL, location TEXT NOT NULL, quantityBaseUnits INTEGER NOT NULL, reason TEXT NOT NULL, recordedAt INTEGER NOT NULL, costPaise INTEGER NOT NULL)")
    }
}


private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS recipe_lines (id TEXT NOT NULL PRIMARY KEY, menuItemId TEXT NOT NULL, ingredientId TEXT NOT NULL, quantityBaseUnits INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_lines_menuItemId ON recipe_lines(menuItemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_lines_ingredientId ON recipe_lines(ingredientId)")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sale_costs (saleId TEXT NOT NULL PRIMARY KEY, ingredientCostPaise INTEGER NOT NULL)")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ingredients ADD COLUMN storeMinStockBaseUnits INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE ingredients ADD COLUMN kitchenMinStockBaseUnits INTEGER NOT NULL DEFAULT 0")
    }
}
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS staff_members (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, role TEXT NOT NULL, pinSalt TEXT NOT NULL, pinHash TEXT NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS operational_records (id TEXT NOT NULL PRIMARY KEY, module TEXT NOT NULL, title TEXT NOT NULL, details TEXT NOT NULL, amountPaise INTEGER NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL)")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN customerPhone TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS loyalty_accounts (phone TEXT NOT NULL PRIMARY KEY, customerName TEXT NOT NULL, coffeeProgress INTEGER NOT NULL, freeCoffeesAvailable INTEGER NOT NULL, lifetimeCoffeeCount INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
    }
}


private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cash_shifts (id TEXT NOT NULL PRIMARY KEY, openedAt INTEGER NOT NULL, openingFloatPaise INTEGER NOT NULL, closedAt INTEGER, expectedCashPaise INTEGER, countedCashPaise INTEGER, variancePaise INTEGER, notes TEXT NOT NULL, status TEXT NOT NULL)")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN shiftId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_shiftId ON sales(shiftId)")
    }
}

private val MIGRATION_17_18 = object : Migration(17, 18) {
 override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS kitchen_tickets (saleId TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_kitchen_tickets_status ON kitchen_tickets(status)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_kitchen_tickets_createdAt ON kitchen_tickets(createdAt)")
 }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
 override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS combo_components (id TEXT NOT NULL PRIMARY KEY, comboItemId TEXT NOT NULL, componentItemId TEXT NOT NULL, componentName TEXT NOT NULL, quantity INTEGER NOT NULL)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_combo_components_comboItemId ON combo_components(comboItemId)")
  db.execSQL("CREATE INDEX IF NOT EXISTS index_combo_components_componentItemId ON combo_components(componentItemId)")
 }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS next_day_plans (id TEXT NOT NULL PRIMARY KEY, planDate TEXT NOT NULL, menuItemId TEXT NOT NULL, menuItemName TEXT NOT NULL, plannedQuantity INTEGER NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_next_day_plans_planDate ON next_day_plans(planDate)")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchases ADD COLUMN supplierId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_supplierId ON purchases(supplierId)")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS suppliers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, contactPerson TEXT NOT NULL, phone TEXT NOT NULL, gstin TEXT NOT NULL, notes TEXT NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_reconciliations (id TEXT NOT NULL PRIMARY KEY, ingredientId TEXT NOT NULL, ingredientName TEXT NOT NULL, location TEXT NOT NULL, systemQtyBaseUnits INTEGER NOT NULL, physicalQtyBaseUnits INTEGER NOT NULL, varianceBaseUnits INTEGER NOT NULL, adjustmentCostPaise INTEGER NOT NULL, note TEXT NOT NULL, reconciledAt INTEGER NOT NULL)")
    }
}


private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS held_orders (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, customerPhone TEXT NOT NULL, totalPaise INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS held_order_lines (id TEXT NOT NULL PRIMARY KEY, orderId TEXT NOT NULL, itemId TEXT NOT NULL, itemName TEXT NOT NULL, quantity INTEGER NOT NULL, unitPricePaise INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_held_order_lines_orderId ON held_order_lines(orderId)")
    }
}


private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN cashPaidPaise INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sales ADD COLUMN upiPaidPaise INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE sales SET cashPaidPaise = totalPaise WHERE paymentMethod = 'CASH'")
        db.execSQL("UPDATE sales SET upiPaidPaise = totalPaise WHERE paymentMethod = 'UPI'")
    }
}


private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sales_reconciliations (id TEXT NOT NULL PRIMARY KEY, businessDate TEXT NOT NULL, posCashPaise INTEGER NOT NULL, posUpiPaise INTEGER NOT NULL, settledUpiPaise INTEGER NOT NULL, countedCashPaise INTEGER NOT NULL, note TEXT NOT NULL, reconciledAt INTEGER NOT NULL)")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, PaanagamDatabase::class.java, "paanagam-local.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21).build()
        setContent { PaanagamApp(db) }
    }
}

private val modules = listOf(
    "Dashboard","POS","Orders","Sales History","Menu","Inventory","Recipes & BOM / SOP","BOM","Modifiers","Combos",
    "Purchases","Store → Kitchen","Production","Kitchen Stock","Next-Day Planning","Kitchen KDS","Operations Control",
    "Cash Shift","Wastage","Customers","Food Cost","Finance & P&L","Reports","GST Calculator","Marketing Hub",
    "Marketing Automation","Automation Hub","WhatsApp Settings","Business Planner","Stock Reconciliation","Sales Reconciliation","Suppliers",
    "Staff & HR","Safety & FSSAI","Equipment","SOP Library"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaanagamApp(db: PaanagamDatabase) {
    var selected by remember { mutableStateOf("Dashboard") }
    var activeStaff by remember { mutableStateOf<StaffMember?>(null) }
    var firstStaffExists by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(db) { firstStaffExists = db.dao().firstActiveStaff() != null }
    var showMore by remember { mutableStateOf(false) }
    val sops by db.dao().sops().collectAsState(initial = emptyList())
    val primaryTabs = listOf("Dashboard", "POS", "Inventory", "Sales History", "More")
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Gold, onPrimary = Color.White, secondary = Espresso,
            background = Ivory, surface = White, onBackground = Ink, onSurface = Ink
        )
    ) {
        Scaffold(
            containerColor = Ivory,
            topBar = {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text("பானகம்", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text(if (selected == "Sales History") "Transactions" else selected, color = Color(0xFF718078), style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    actions = {
                        if (activeStaff != null) {
                            Text(activeStaff!!.name + " • " + activeStaff!!.role, color = Gold, style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { activeStaff = null; selected = "Dashboard" }) { Text("Logout") }
                        } else Text("காபியும் >< கதையும்", color = Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 14.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ivory)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = White, contentColor = Gold) {
                    primaryTabs.forEach { tab ->
                        val isSelected = if (tab == "More") showMore else selected == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (tab == "More") showMore = !showMore
                                else { selected = tab; showMore = false }
                            },
                            icon = {
                                Icon(
                                    when (tab) {
                                        "Dashboard" -> Icons.Default.Home
                                        "POS" -> Icons.Default.PointOfSale
                                        "Inventory" -> Icons.Default.Inventory2
                                        "Sales History" -> Icons.Default.ReceiptLong
                                        else -> Icons.Default.Menu
                                    }, contentDescription = tab
                                )
                            },
                            label = { Text(if (tab == "Sales History") "Sales" else tab) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold, selectedTextColor = Gold,
                                indicatorColor = Mint, unselectedIconColor = Color(0xFF829088),
                                unselectedTextColor = Color(0xFF829088)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    firstStaffExists == null -> CircularProgressIndicator()
                    firstStaffExists == false -> AdminSetupScreen { name, pin ->
                        scope.launch {
                            val c = pinCredentials(pin)
                            db.dao().insertStaff(StaffMember(UUID.randomUUID().toString(), name.trim(), "ADMIN", c.first, c.second, true, System.currentTimeMillis()))
                            firstStaffExists = true
                            activeStaff = db.dao().firstActiveStaff()
                        }
                    }
                    activeStaff == null -> StaffLoginScreen(db) { activeStaff = it }
                    !canAccess(activeStaff!!.role, selected) -> { selected = "Dashboard"; Text("Access restricted.", color = Color(0xFFC26A27)) }
                    showMore -> {
                    MoreModulesScreen(modules.filterNot { it in primaryTabs }.filter { canAccess(activeStaff!!.role, it) }) { module ->
                        selected = module
                        showMore = false
                    }
                    }
                    else -> {
                    when (selected) {
                        "Dashboard" -> DashboardScreen(db, onNavigate = { selected = it })
                        "Sales History" -> SalesHistoryScreen(db)
                        "POS" -> PosScreen(db)
                        "Menu" -> PosScreen(db)
                        "Recipes & BOM / SOP" -> RecipeScreen(db)
                        "SOP Library" -> SopScreen(sops)
                        "Inventory", "Purchases", "Store → Kitchen", "Kitchen Stock" -> InventoryScreen(db, selected)
                        "Production", "Wastage" -> ProductionScreen(db)
                        "WhatsApp Settings" -> WhatsAppSettingsScreen()
                        "Staff & HR" -> StaffAdminScreen(db, activeStaff!!)
                        "Reports", "Food Cost", "Finance & P&L" -> ReportsScreen(db, selected)
                        "Customers" -> LoyaltyScreen(db)
                        "Stock Reconciliation" -> StockReconciliationScreen(db)
                        "Sales Reconciliation" -> SalesReconciliationScreen(db)
                        "Cash Shift" -> CashShiftScreen(db)
                        "Suppliers" -> SuppliersScreen(db)
                        "Next-Day Planning" -> NextDayPlanningScreen(db)
                        "Combos" -> ComboPlanningScreen(db)
                        "Kitchen KDS" -> KitchenKdsScreen(db)
                        else -> OperationalModuleScreen(db, selected)
                    }
                    }
                }
            }
        }
    }
}

private fun canAccess(role: String, module: String): Boolean = when (role) {
    "ADMIN" -> true
    "MANAGER" -> module !in listOf("Staff & HR", "WhatsApp Settings", "Business Planner", "Marketing Automation", "Automation Hub")
    "CASHIER" -> module in listOf("Dashboard", "POS", "Orders", "Sales History", "Cash Shift")
    else -> false
}

private fun pinCredentials(pin: String): Pair<String, String> {
    require(pin.length in 4..8 && pin.all { it.isDigit() })
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
    val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return Base64.encodeToString(salt, Base64.NO_WRAP) to Base64.encodeToString(hash, Base64.NO_WRAP)
}

private fun verifyPin(pin: String, saltText: String, hashText: String): Boolean = try {
    val salt = Base64.decode(saltText, Base64.NO_WRAP)
    val expected = Base64.decode(hashText, Base64.NO_WRAP)
    val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
    val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    java.security.MessageDigest.isEqual(expected, actual)
} catch (_: Exception) { false }

@Composable
private fun AdminSetupScreen(onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("Admin") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Set up Paanagam Admin", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Create the first administrator account. Choose a private 4–8 digit PIN.", color = Color(0xFF65736B))
        OutlinedTextField(name, { name = it }, label = { Text("Admin name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("Admin PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirm, { confirm = it.filter(Char::isDigit).take(8) }, label = { Text("Confirm PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = Color.Red)
        Button(onClick = { if (name.isBlank() || pin.length !in 4..8 || pin != confirm) error = "Enter a name and matching 4–8 digit PIN." else onCreate(name, pin) }, modifier = Modifier.fillMaxWidth()) { Text("Create Admin account") }
    }
}

@Composable
private fun StaffLoginScreen(db: PaanagamDatabase, onLogin: (StaffMember) -> Unit) {
    val staff by db.dao().staffMembers().collectAsState(initial = emptyList())
    var selectedId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(staff) { if (selectedId.isBlank()) selectedId = staff.firstOrNull()?.id.orEmpty() }
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Staff login", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        staff.forEach { member ->
            Row(Modifier.fillMaxWidth().clickable { selectedId = member.id }.background(if (selectedId == member.id) Mint else White, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedId == member.id, onClick = { selectedId = member.id })
                Column { Text(member.name, color = Ink, fontWeight = FontWeight.Medium); Text(member.role, color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall) }
            }
        }
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = Color.Red)
        Button(onClick = { scope.launch { val member = staff.firstOrNull { it.id == selectedId }; if (member != null && verifyPin(pin, member.pinSalt, member.pinHash)) { error = ""; onLogin(member) } else error = "Incorrect PIN. Try again." } }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
    }
}

@Composable
private fun StaffAdminScreen(db: PaanagamDatabase, current: StaffMember) {
    val staff by db.dao().staffMembers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("CASHIER") }
    var message by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Staff access management", color = Gold, style = MaterialTheme.typography.titleLarge)
        Text("Create accounts, reset PINs and deactivate access. PINs are salted PBKDF2 hashes stored locally.", color = Color(0xFF65736B))
        OutlinedTextField(name, { name = it }, label = { Text("Staff name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("Initial / reset PIN (4–8 digits)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row { FilterChip(selected = role == "CASHIER", onClick = { role = "CASHIER" }, label = { Text("Cashier") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = role == "MANAGER", onClick = { role = "MANAGER" }, label = { Text("Manager") }) }
        Button(onClick = { scope.launch { if (name.isBlank() || pin.length !in 4..8) message = "Enter staff name and valid PIN." else { val c = pinCredentials(pin); db.dao().insertStaff(StaffMember(UUID.randomUUID().toString(), name.trim(), role, c.first, c.second, true, System.currentTimeMillis())); name = ""; pin = ""; message = "Staff account created." } } }, modifier = Modifier.fillMaxWidth()) { Text("Create staff account") }
        if (message.isNotBlank()) Text(message, color = Gold)
        staff.forEach { member ->
            Card(colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(member.name + " • " + member.role, color = Ink, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { scope.launch { if (pin.length !in 4..8) message = "Enter a 4–8 digit PIN above, then tap Reset." else { val c = pinCredentials(pin); db.dao().resetStaffPin(member.id, c.first, c.second); pin = ""; message = "PIN reset." } } }) { Text("Reset PIN") }
                        if (member.id != current.id) {
                            if (member.active) TextButton(onClick = { scope.launch { db.dao().setStaffActive(member.id, false); message = "Account deactivated." } }) { Text("Deactivate") }
                            else TextButton(onClick = { scope.launch { db.dao().setStaffActive(member.id, true); message = "Account reactivated." } }) { Text("Reactivate") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreModulesScreen(items: List<String>, onSelect: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text("Business tools", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("More operations and settings", color = Color(0xFF718078))
        }
        items(items) { name ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(name) },
                colors = CardDefaults.cardColors(containerColor = White),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.ChevronRight, contentDescription = "Open $name", tint = Gold)
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(db: PaanagamDatabase, onNavigate: (String) -> Unit) {
    val sales by db.dao().sales().collectAsState(initial = emptyList())
    val saleCosts by db.dao().saleCosts().collectAsState(initial = emptyList())
    val wastage by db.dao().wastageRecords().collectAsState(initial = emptyList())
    val lowStockCount by db.dao().lowStockCount().collectAsState(initial = 0)
    val todayStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todaySales = sales.filter { it.createdAt >= todayStart && it.status == "COMPLETED" }
    val todaySaleIds = todaySales.map { it.id }.toSet()
    val todayRevenue = todaySales.sumOf { it.totalPaise }
    val todayCost = saleCosts.filter { it.saleId in todaySaleIds }.sumOf { it.ingredientCostPaise }
    val todayWastage = wastage.filter { it.recordedAt >= todayStart }.sumOf { it.costPaise }
    val foodCost = if (todayRevenue > 0) todayCost.toDouble() / todayRevenue * 100.0 else 0.0
    val averageBill = if (todaySales.isNotEmpty()) todayRevenue / todaySales.size else 0L
    val grossMargin = todayRevenue - todayCost
    val signals = buildList {
        if (lowStockCount > 0) add("Reorder review" to "$lowStockCount ingredient/location balances are below configured minimums.")
        if (foodCost > 35.0) add("Food cost needs attention" to "Recorded recipe cost is %.1f%% of today's sales.".format(foodCost))
        if (todayWastage > 0) add("Wastage recorded" to "Today's recorded wastage is ₹%.2f.".format(todayWastage / 100.0))
        if (todaySales.isEmpty()) add("Ready for first sale" to "Open POS to begin today's sales.")
    }.take(4)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Espresso), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("பானகம்  •  காபியும் கதையும்", color = PosMustardFallback, style = MaterialTheme.typography.labelLarge)
                    Text("Business dashboard", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Today's sales", color = Color(0xFFE8D9C4), style = MaterialTheme.typography.labelMedium)
                    Text("₹%.2f".format(todayRevenue / 100.0), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("${todaySales.size} completed bills  •  Saved on this device", color = Color(0xFFE8D9C4), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onNavigate("POS") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE97832), contentColor = Color.White)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New sale")
                    }
                }
            }
        }
        item {
            Text("Today's performance", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetric("Sales", "₹%.2f".format(todayRevenue / 100.0), Modifier.weight(1f))
                    DashboardMetric("Bills", todaySales.size.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetric("Recipe cost", "₹%.2f".format(todayCost / 100.0), Modifier.weight(1f))
                    DashboardMetric("Gross margin", "₹%.2f".format(grossMargin / 100.0), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetric("Food cost", "%.1f%%".format(foodCost), Modifier.weight(1f))
                    DashboardMetric("Avg. bill", "₹%.2f".format(averageBill / 100.0), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardMetric("Wastage", "₹%.2f".format(todayWastage / 100.0), Modifier.weight(1f))
                    DashboardMetric("Stock alerts", lowStockCount.toString(), Modifier.weight(1f))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1D8)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Insights, contentDescription = null, tint = Gold)
                        Spacer(Modifier.width(7.dp))
                        Text("Paanagam business advisor", color = Ink, fontWeight = FontWeight.Bold)
                    }
                    Text("Signals from today's recorded sales, recipe cost, wastage and stock.", color = Color(0xFF79634A), style = MaterialTheme.typography.bodySmall)
                    if (signals.isEmpty()) {
                        Text("No immediate stock, wastage or food-cost alert from the available records.", color = Ink, style = MaterialTheme.typography.bodyMedium)
                    } else signals.forEach { (title, detail) ->
                        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Gold)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(detail, color = Color(0xFF79634A), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onNavigate(if (title.contains("Reorder")) "Inventory" else if (title.contains("Food")) "Food Cost" else if (title.contains("Wastage")) "Wastage" else "POS") }) { Text("Open") }
                        }
                    }
                }
            }
        }
        item {
            Text("Quick actions", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("POS", Icons.Default.PointOfSale, Modifier.weight(1f)) { onNavigate("POS") }
                QuickAction("Inventory", Icons.Default.Inventory2, Modifier.weight(1f)) { onNavigate("Inventory") }
                QuickAction("Purchases", Icons.Default.LocalShipping, Modifier.weight(1f)) { onNavigate("Purchases") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                QuickAction("Production", Icons.Default.LocalCafe, Modifier.weight(1f)) { onNavigate("Production") }
                QuickAction("Reconcile", Icons.Default.FactCheck, Modifier.weight(1f)) { onNavigate("Stock Reconciliation") }
                QuickAction("Reports", Icons.Default.Assessment, Modifier.weight(1f)) { onNavigate("Reports") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent transactions", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onNavigate("Sales History") }) { Text("See all") }
                    }
                    if (todaySales.isEmpty()) Text("Completed bills will appear here after your first sale.", color = Color(0xFF718078))
                    else todaySales.take(5).forEach { sale ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Bill ${sale.id.take(8).uppercase()}", color = Ink, fontWeight = FontWeight.Medium)
                                Text(sale.paymentMethod + " • " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(sale.createdAt)), color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                            }
                            Text("₹%.2f".format(sale.totalPaise / 100.0), color = Gold, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Color(0xFFEFE6D8))
                    }
                }
            }
        }
    }
}

private val PosMustardFallback = Color(0xFFDDB36C)

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Color(0xFF718078), style = MaterialTheme.typography.labelMedium)
            Text(value, color = Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = label, tint = Gold)
            Text(label, color = Ink, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable private fun SopScreen(sops: List<SopDocument>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Standard Operating Procedures", color = Gold, style = MaterialTheme.typography.titleLarge)
        Text("Approved instructions, version history and staff completion records are stored locally.", color = Ivory)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) {
            Column(Modifier.padding(14.dp)) {
                Text("Filter coffee — approved preparation reference", color = Gold, fontWeight = FontWeight.Bold)
                Text("Decoction batch: 250 g coffee powder + 1,100 ml water. Target usable decoction: 1,000 ml (100 ml retained in grounds).", color = Ivory)
                Spacer(Modifier.height(8.dp))
                Text("One serving: 25 ml decoction + 75 ml milk + 5 g sugar.", color = Ivory)
            }
        }
        if (sops.isEmpty()) Text("No additional SOP documents entered yet.", color = Ivory)
        sops.forEach { sop -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) { Column(Modifier.padding(12.dp)) { Text(sop.title, color = Gold, fontWeight = FontWeight.Bold); Text("v${sop.version} • ${sop.status}", color = Ivory); Text(sop.instructions, color = Ivory) } } }
    }
}

@Composable private fun ModuleScreen(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, color = Gold, style = MaterialTheme.typography.titleLarge)
        Text("Module workspace", color = Ivory, style = MaterialTheme.typography.titleMedium)
        Text("This module is included in the native navigation shell. Its transaction workflows, validations and reports will be implemented against the shared local database in staged builds.", color = Ivory)
        if (name == "POS") Text("Payment methods planned: Cash and UPI only.", color = Gold)
        if (name == "Inventory" || name.contains("Stock") || name == "Purchases") Text("Store and Kitchen are separate stock locations. Costing follows FIFO; purchase prices are never averaged.", color = Gold)
        if (name.contains("SOP")) Text("SOPs include preparation steps, hygiene, storage, opening/closing, equipment, staff acknowledgement and approval/version history.", color = Gold)
    }
}
