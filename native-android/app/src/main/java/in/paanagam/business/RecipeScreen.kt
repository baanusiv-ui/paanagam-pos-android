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
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun RecipeScreen(db: PaanagamDatabase) {
    val catalog by db.dao().catalog().collectAsState(initial = emptyList())
    val ingredients by db.dao().ingredients().collectAsState(initial = emptyList())
    val recipeLines by db.dao().allRecipeLines().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var menuId by remember { mutableStateOf("") }
    var ingredientId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val selectedMenu = catalog.firstOrNull { it.id == menuId }
    val selectedIngredient = ingredients.firstOrNull { it.id == ingredientId }
    val gold = Color(0xFFDDB36C)
    val ivory = Color(0xFFFFF4DF)

    LaunchedEffect(catalog) { if (menuId.isBlank() && catalog.isNotEmpty()) menuId = catalog.first().id }
    LaunchedEffect(ingredients) { if (ingredientId.isBlank() && ingredients.isNotEmpty()) ingredientId = ingredients.first().id }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Recipe / Bill of Materials", color = gold, style = MaterialTheme.typography.titleLarge)
        Text("Map each sold menu item to the quantity of kitchen ingredient it consumes. Enter quantities in the ingredient's base unit.", color = ivory)
        if (catalog.isEmpty() || ingredients.isEmpty()) {
            Text("Add menu items in POS and ingredients in Inventory before configuring recipes.", color = gold)
        } else {
            val standardCoffeeItem = catalog.firstOrNull { it.name.contains("filter coffee", ignoreCase = true) }
            val decoction = ingredients.firstOrNull { it.name.contains("decoction", ignoreCase = true) && it.baseUnit.equals("ml", true) }
            val milk = ingredients.firstOrNull { it.name.contains("milk", ignoreCase = true) && it.baseUnit.equals("ml", true) }
            val sugar = ingredients.firstOrNull { it.name.contains("sugar", ignoreCase = true) && it.baseUnit.equals("g", true) }
            OutlinedButton(
                enabled = standardCoffeeItem != null && decoction != null && milk != null && sugar != null,
                onClick = {
                    val menu = standardCoffeeItem!!
                    val recipe = listOf(
                        RecipeLine(UUID.randomUUID().toString(), menu.id, decoction!!.id, 25000L),
                        RecipeLine(UUID.randomUUID().toString(), menu.id, milk!!.id, 75000L),
                        RecipeLine(UUID.randomUUID().toString(), menu.id, sugar!!.id, 5000L)
                    )
                    scope.launch {
                        runCatching { db.dao().replaceRecipe(menu.id, recipe) }
                            .onSuccess { menuId = menu.id; message = "Standard filter coffee recipe applied: 25 ml decoction + 75 ml milk + 5 g sugar" }
                            .onFailure { message = it.message ?: "Could not apply standard recipe" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply standard filter coffee recipe") }
            if (standardCoffeeItem == null || decoction == null || milk == null || sugar == null) {
                Text("For the quick setup, add a menu item named Filter Coffee and ingredients Decoction (ml), Milk (ml), Sugar (g).", color = gold)
            }
            Text("Menu item", color = gold)
            catalog.forEach { item ->
                FilterChip(selected = menuId == item.id, onClick = { menuId = item.id }, label = { Text(item.name) })
            }
            Text("Ingredient", color = gold)
            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                items(ingredients, key = { it.id }) { item ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = ingredientId == item.id, onClick = { ingredientId = item.id })
                        Text("${item.name} (${item.baseUnit})", color = ivory)
                    }
                }
            }
            OutlinedTextField(
                value = quantity, onValueChange = { quantity = it },
                label = { Text("Quantity per serving (${selectedIngredient?.baseUnit ?: "unit"})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val amount = quantity.trim().toBigDecimalOrNull()
                val baseUnits = runCatching {
                    requireNotNull(amount).multiply(BigDecimal("1000"))
                        .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
                }.getOrNull()
                if (selectedMenu == null || selectedIngredient == null || amount == null || amount.signum() <= 0 || baseUnits == null || baseUnits <= 0L) {
                    message = "Enter a positive quantity with at most three decimal places."
                } else {
                    scope.launch {
                        runCatching {
                            require(db.dao().recipeLines(selectedMenu.id).none { it.ingredientId == selectedIngredient.id }) {
                                "This ingredient is already in the recipe. Remove its existing line before adding it again."
                            }
                            db.dao().saveRecipeLine(RecipeLine(UUID.randomUUID().toString(), selectedMenu.id, selectedIngredient.id, baseUnits))
                        }.onSuccess {
                            quantity = ""
                            message = "Recipe ingredient saved"
                        }.onFailure {
                            message = it.message ?: "Could not save recipe ingredient."
                        }
                    }
                }
            }) { Text("Add ingredient to recipe") }
        }
        Text("Configured recipe lines", color = gold, style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(recipeLines, key = { it.id }) { line ->
                val menuName = catalog.firstOrNull { it.id == line.menuItemId }?.name ?: "Unknown menu item"
                val ingredient = ingredients.firstOrNull { it.id == line.ingredientId }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2519))) {
                    Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(menuName, color = gold)
                            Text("${ingredient?.name ?: "Unknown ingredient"} • ${line.quantityBaseUnits / 1000.0} ${ingredient?.baseUnit ?: ""} / serving", color = ivory)
                        }
                        TextButton(onClick = { scope.launch { db.dao().deleteRecipeLine(line.id); message = "Recipe line removed" } }) { Text("Remove") }
                    }
                }
            }
        }
        Text(message, color = gold)
        Text("POS sales deduct configured recipe quantities from Kitchen stock using FIFO. Unconfigured menu items do not deduct stock.", color = ivory)
    }
}
