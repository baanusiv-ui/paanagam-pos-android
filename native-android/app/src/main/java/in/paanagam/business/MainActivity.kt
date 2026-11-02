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

private val Espresso = Color(0xFF24140D)
private val Ivory = Color(0xFFFFF4DF)
private val Gold = Color(0xFFDDB36C)

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
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, PaanagamDatabase::class.java, "paanagam-local.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
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
    var showDrawer by remember { mutableStateOf(true) }
    val sops by db.dao().sops().collectAsState(initial = emptyList())
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Espresso, surface = Color(0xFF321E14), onBackground = Ivory, onSurface = Ivory)) {
        Row(Modifier.fillMaxSize().background(Espresso)) {
            if (showDrawer) {
                LazyColumn(Modifier.width(210.dp).fillMaxHeight().padding(8.dp)) {
                    item { Text("பானகம்", color = Gold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
                    items(modules) { name ->
                        Surface(color = if (selected == name) Gold else Color.Transparent, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { selected = name }) {
                            Text(name, color = if (selected == name) Espresso else Ivory, modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDrawer = !showDrawer }) { Icon(Icons.Default.Menu, "Menu", tint = Ivory) }
                    Text(selected, color = Ivory, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                when (selected) {
                    "Dashboard" -> DashboardScreen(db)
                    "Sales History" -> SalesHistoryScreen(db)
                    "POS", "Menu" -> PosScreen(db)
                    "Recipes & BOM / SOP" -> RecipeScreen(db)
                    "SOP Library" -> SopScreen(sops)
                    "Inventory", "Purchases", "Store → Kitchen", "Kitchen Stock" -> InventoryScreen(db, selected)
                    "Production", "Wastage" -> ProductionScreen(db)
                    "WhatsApp Settings" -> WhatsAppSettingsScreen()
                    else -> ModuleScreen(selected)
                }
            }
        }
    }
}

@Composable private fun DashboardScreen(db: PaanagamDatabase) {
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paanagam Business", color = Gold, style = MaterialTheme.typography.titleLarge)
        Text("Offline-first native Android system", color = Ivory)
        val cards = listOf("Sales today" to "₹%.2f".format(todayTotal / 100.0), "Bills today" to todaySales.size.toString(), "Open orders" to "0", "Stock alerts" to lowStockCount.toString())
        cards.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { (label,value) -> Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) { Column(Modifier.padding(14.dp)) { Text(label, color = Ivory); Text(value, color = Gold, style = MaterialTheme.typography.headlineMedium) } } } } }
        if (lowStockCount > 0) Text("$lowStockCount ingredient/location stock level(s) are below their configured minimum.", color = Color(0xFFFFB36B)) else Text("No low-stock alerts. Configure minimum levels in Inventory.", color = Ivory)
        Text("Sales figures update automatically from locally saved POS bills.", color = Ivory)
        Text("Select Sales History to review bills and item details.", color = Gold)
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
