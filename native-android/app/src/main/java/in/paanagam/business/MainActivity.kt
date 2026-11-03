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

private val Espresso = Color(0xFF263A32)
private val Ivory = Color(0xFFF5F8F5)
private val Gold = Color(0xFF16845B)
private val Ink = Color(0xFF20332B)
private val Mint = Color(0xFFE4F3EC)
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, PaanagamDatabase::class.java, "paanagam-local.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
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
                        else -> ModuleScreen(selected)
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
    "CASHIER" -> module in listOf("Dashboard", "POS", "Orders", "Sales History")
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
    val lowStockCount by db.dao().lowStockCount().collectAsState(initial = 0)
    val todayStart = remember {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    val todaySales = sales.filter { it.createdAt >= todayStart }
    val todayTotal = todaySales.sumOf { it.totalPaise }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Espresso),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("இனிய காலை வணக்கம்!", color = Color(0xFFB9DCCB), style = MaterialTheme.typography.labelLarge)
                    Text("Today's business", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("₹%.2f".format(todayTotal / 100.0), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("${todaySales.size} bills  •  Offline data saved on this device", color = Color(0xFFB9DCCB), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onNavigate("POS") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C9A6B), contentColor = Color.White)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New sale")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric("Bills today", todaySales.size.toString(), Modifier.weight(1f))
                DashboardMetric("Stock alerts", lowStockCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("Quick actions", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("POS", Icons.Default.PointOfSale, Modifier.weight(1f)) { onNavigate("POS") }
                QuickAction("Inventory", Icons.Default.Inventory2, Modifier.weight(1f)) { onNavigate("Inventory") }
                QuickAction("Purchases", Icons.Default.LocalShipping, Modifier.weight(1f)) { onNavigate("Purchases") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stock health", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        Text(if (lowStockCount == 0) "All clear" else "Needs attention", color = if (lowStockCount == 0) Gold else Color(0xFFC26A27), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        if (lowStockCount > 0) "$lowStockCount ingredient/location stock level(s) are below their configured minimum."
                        else "No low-stock alerts. Set Store and Kitchen minimums in Inventory.",
                        color = Color(0xFF65736B), style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onNavigate("Inventory") }) { Text("Manage stock levels") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent transactions", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onNavigate("Sales History") }) { Text("See all") }
                    }
                    if (todaySales.isEmpty()) Text("Your completed bills will appear here.", color = Color(0xFF65736B))
                    else todaySales.take(4).forEach { sale ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Bill ${sale.id.take(8).uppercase()}", color = Ink, fontWeight = FontWeight.Medium)
                                Text(sale.paymentMethod, color = Color(0xFF829088), style = MaterialTheme.typography.labelSmall)
                            }
                            Text("₹%.2f".format(sale.totalPaise / 100.0), color = Gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

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
