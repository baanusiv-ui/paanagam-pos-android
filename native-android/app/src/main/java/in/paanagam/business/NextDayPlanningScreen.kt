package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private data class PlanStockLine(val name: String, val required: Long, val available: Long, val unit: String)

@Composable
fun NextDayPlanningScreen(db: PaanagamDatabase) {
    val catalog by db.dao().catalog().collectAsState(initial = emptyList())
    val ingredients by db.dao().ingredients().collectAsState(initial = emptyList())
    val plans by db.dao().nextDayPlans().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val gold = Color(0xFF16845B)
    val ink = Color(0xFF20332B)
    val muted = Color(0xFF65736B)
    var planDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time)) }
    var menuId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var message by remember { mutableStateOf("") }
    var stockLines by remember { mutableStateOf<List<PlanStockLine>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val dayPlans = plans.filter { it.planDate == planDate }
    LaunchedEffect(catalog) { if (menuId.isBlank() && catalog.isNotEmpty()) menuId = catalog.first().id }
    LaunchedEffect(planDate, dayPlans, ingredients) {
        loading = true
        val totals = mutableMapOf<String, Long>()
        suspend fun expandRequirements(menuItemId: String, multiplier: Long, path: Set<String>) {
            require(menuItemId !in path) { "Circular combo detected in preparation plan." }
            val nextPath = path + menuItemId
            val components = db.dao().comboComponents(menuItemId)
            if (components.isNotEmpty()) {
                components.forEach { component ->
                    val componentMultiplier = Math.multiplyExact(multiplier, component.quantity.toLong())
                    expandRequirements(component.componentItemId, componentMultiplier, nextPath)
                }
            } else {
                val recipe = db.dao().recipeLines(menuItemId)
                require(recipe.isNotEmpty()) { "Recipe missing for a planned menu item or combo component." }
                recipe.forEach { line ->
                    val required = Math.multiplyExact(line.quantityBaseUnits, multiplier)
                    totals[line.ingredientId] = Math.addExact(totals[line.ingredientId] ?: 0L, required)
                }
            }
        }
        dayPlans.forEach { plan ->
            expandRequirements(plan.menuItemId, plan.plannedQuantity.toLong(), emptySet())
        }
        stockLines = totals.mapNotNull { (ingredientId, required) ->
            val item = ingredients.firstOrNull { it.id == ingredientId } ?: return@mapNotNull null
            PlanStockLine(item.name, required, db.dao().stockBalance(item.id, "KITCHEN"), item.baseUnit)
        }.sortedBy { it.name }
        loading = false
    }
    val shortageCount = stockLines.count { it.available < it.required }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Next-day preparation plan", color = gold, style = MaterialTheme.typography.titleLarge)
        Text("Plan menu quantities and compare recipe requirements with current Kitchen stock.", color = muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(planDate, { value ->
            if (value.length <= 10 && value.all { it.isDigit() || it == '-' }) planDate = value
        }, label = { Text("Plan date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (catalog.isEmpty()) Text("Add menu items in POS first.", color = muted)
        else {
            Text("Menu item", color = ink, style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.heightIn(max = 115.dp)) {
                items(catalog, key = { it.id }) { item ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = menuId == item.id, onClick = { menuId = item.id })
                        Text(item.name, color = ink)
                    }
                }
            }
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(5) }, label = { Text("Planned quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val item = catalog.firstOrNull { it.id == menuId }
                val qty = quantity.toIntOrNull()
                if (planDate.length != 10 || runCatching { java.time.LocalDate.parse(planDate) }.isFailure) message = "Enter a valid date in YYYY-MM-DD format."
                else if (item == null || qty == null || qty <= 0) message = "Choose a menu item and quantity greater than zero."
                else scope.launch {
                    runCatching {
                        require(db.dao().recipeLines(item.id).isNotEmpty()) { "Configure this menu item's recipe before planning." }
                        db.dao().insertNextDayPlan(NextDayPlan(UUID.randomUUID().toString(), planDate, item.id, item.name, qty, "PLANNED", System.currentTimeMillis()))
                    }.onSuccess { message = "Plan item saved."; quantity = "1" }
                        .onFailure { message = it.message ?: "Could not save plan." }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Add to preparation plan") }
        }
        if (message.isNotBlank()) Text(message, color = gold)
        Divider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Plan for $planDate", color = ink, style = MaterialTheme.typography.titleMedium)
            Text("${dayPlans.size} items", color = muted)
        }
        if (dayPlans.isEmpty()) Text("No menu quantities planned for this date.", color = muted)
        else LazyColumn(Modifier.heightIn(max = 155.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(dayPlans, key = { it.id }) { plan ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE4F3EC))) {
                    Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.menuItemName, color = ink)
                            Text("Qty: ${plan.plannedQuantity} • ${plan.status}", color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (plan.status == "PLANNED") TextButton(onClick = { scope.launch { db.dao().updateNextDayPlan(plan.copy(status = "PREPARED")); message = "Marked prepared." } }) { Text("Prepared") }
                        TextButton(onClick = { scope.launch { db.dao().deleteNextDayPlan(plan.id); message = "Plan item removed." } }) { Text("Remove") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Kitchen stock check", color = ink, style = MaterialTheme.typography.titleMedium)
            Text(if (loading) "Checking…" else if (shortageCount == 0) "Covered" else "$shortageCount short", color = if (shortageCount == 0) gold else Color(0xFFC26A27))
        }
        if (stockLines.isEmpty()) Text("Recipe-linked ingredient needs will appear here after adding plan items.", color = muted, style = MaterialTheme.typography.bodySmall)
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(stockLines, key = { it.name }) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, color = ink)
                        Text("Need ${line.required / 1000.0} ${line.unit}", color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Have ${line.available / 1000.0}", color = if (line.available >= line.required) gold else Color(0xFFC26A27), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("Planning and Prepared status do not deduct or reserve stock. Actual production and FIFO consumption remain in Production.", color = muted, style = MaterialTheme.typography.labelSmall)
    }
}
