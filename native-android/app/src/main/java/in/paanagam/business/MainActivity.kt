package in.paanagam.business

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
import in.paanagam.business.data.PaanagamDatabase
import in.paanagam.business.data.SopDocument

private val Espresso = Color(0xFF24140D)
private val Ivory = Color(0xFFFFF4DF)
private val Gold = Color(0xFFDDB36C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, PaanagamDatabase::class.java, "paanagam-local.db").build()
        setContent { PaanagamApp(db) }
    }
}

private val modules = listOf(
    "Dashboard","POS","Orders","Sales History","Menu","Inventory","Recipes & BOM / SOP","BOM","Modifiers","Combos",
    "Purchases","Store → Kitchen","Production","Kitchen Stock","Next-Day Planning","Kitchen KDS","Operations Control",
    "Cash Shift","Wastage","Customers","Food Cost","Finance & P&L","Reports","GST Calculator","Marketing Hub",
    "Marketing Automation","Automation Hub","Business Planner","Stock Reconciliation","Sales Reconciliation","Suppliers",
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
                    "Dashboard" -> DashboardScreen()\n                    "POS", "Menu" -> PosScreen(db)
                    "Recipes & BOM / SOP", "SOP Library" -> SopScreen(sops)
                    else -> ModuleScreen(selected)
                }
            }
        }
    }
}

@Composable private fun DashboardScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paanagam Business", color = Gold, style = MaterialTheme.typography.titleLarge)
        Text("Offline-first native Android system", color = Ivory)
        val cards = listOf("Sales today" to "₹0.00","Open orders" to "0","Store stock alerts" to "0","Kitchen stock alerts" to "0")
        cards.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { pair.forEach { (label,value) -> Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) { Column(Modifier.padding(14.dp)) { Text(label, color = Ivory); Text(value, color = Gold, style = MaterialTheme.typography.headlineMedium) } } } } }
        Text("Select a module from the navigation panel to begin.", color = Ivory)
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
