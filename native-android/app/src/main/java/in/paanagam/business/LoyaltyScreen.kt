package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.PaanagamDatabase

@Composable
fun LoyaltyScreen(db: PaanagamDatabase) {
    val accounts by db.dao().loyaltyAccounts().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Coffee loyalty", color = Color(0xFF16845B), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Every 10 coffee servings earn 1 free coffee. Add the customer's mobile number at POS to track automatically.", color = Color(0xFF65736B))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("Customers", accounts.size.toString(), Modifier.weight(1f))
            SummaryTile("Free coffees", accounts.sumOf { it.freeCoffeesAvailable }.toString(), Modifier.weight(1f))
        }
        if (accounts.isEmpty()) Text("No loyalty customers yet. Enter a mobile number on a coffee bill to begin.", color = Color(0xFF65736B))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts, key = { it.phone }) { account ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(account.customerName, color = Color(0xFF263A32), fontWeight = FontWeight.Bold)
                        Text(account.phone, color = Color(0xFF718078))
                        LinearProgressIndicator(progress = { account.coffeeProgress / 10f }, modifier = Modifier.fillMaxWidth())
                        if (account.freeCoffeesAvailable >= 0) {
                            Text("${account.coffeeProgress}/10 coffees • ${account.freeCoffeesAvailable} free coffee reward(s) available", color = Color(0xFF16845B))
                        } else {
                            Text("${account.coffeeProgress}/10 coffees • ${-account.freeCoffeesAvailable} future reward(s) will offset refunded rewards before redemption", color = Color(0xFFC26A27))
                        }
                        Text("Lifetime coffee servings: ${account.lifetimeCoffeeCount}", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Color(0xFF718078))
            Text(value, color = Color(0xFF263A32), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
