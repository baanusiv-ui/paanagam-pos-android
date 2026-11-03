package `in`.paanagam.business
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable fun PosScreen(db: PaanagamDatabase) {
 val catalog by db.dao().catalog().collectAsState(initial=emptyList())
 val sales by db.dao().sales().collectAsState(initial=emptyList())
 val recipeLines by db.dao().allRecipeLines().collectAsState(initial=emptyList())
 val scope=rememberCoroutineScope()
 var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Beverages") }; var price by remember { mutableStateOf("") }
 var customerPhone by remember { mutableStateOf("") }; var loyaltyRewards by remember { mutableIntStateOf(0) }; var redeemFreeCoffee by remember { mutableStateOf(false) }; var cart by remember { mutableStateOf<Map<String,Pair<CatalogItem,Int>>>(emptyMap()) }; var payment by remember { mutableStateOf("CASH") }; var msg by remember { mutableStateOf("") }
 LaunchedEffect(customerPhone) {
  loyaltyRewards = if (customerPhone.length == 10) db.dao().loyaltyAccount(customerPhone)?.freeCoffeesAvailable ?: 0 else 0
  redeemFreeCoffee = false
 }
 val coffeeItem = cart.values.map { it.first }.firstOrNull { it.name.contains("coffee", ignoreCase = true) }
 val total=cart.values.sumOf { it.first.pricePaise*it.second }
 val missingRecipes = cart.values.map { it.first }.filter { item -> recipeLines.none { it.menuItemId == item.id } }
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
  Text("Menu setup",color=Color(0xFFDDB36C))
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   OutlinedTextField(name,{name=it},label={Text("Item")},modifier=Modifier.weight(2f),singleLine=true)
   OutlinedTextField(price,{price=it},label={Text("₹")},modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
  }
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   OutlinedTextField(category,{category=it},label={Text("Category")},modifier=Modifier.weight(1f),singleLine=true)
   Button(onClick={ val p=price.toDoubleOrNull(); if(name.isNotBlank()&&p!=null&&p>0) scope.launch { db.dao().saveCatalogItem(CatalogItem(UUID.randomUUID().toString(),name.trim(),category.trim(),(p*100).toLong())); name="";price="";msg="Menu item saved" } else msg="Enter valid item and price" }) { Text("Save") }
  }
  Text("Tap item to add to bill",color=Color(0xFFDDB36C))
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
   items(catalog,key={it.id}) { item ->
    Row(Modifier.fillMaxWidth().background(Color(0xFF3A2519),RoundedCornerShape(10.dp)).clickable { val q=cart[item.id]?.second?:0;cart=cart+(item.id to (item to q+1)) }.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
     Text(item.name,color=Color(0xFFFFF4DF));Text("₹%.2f".format(item.pricePaise/100.0),color=Color(0xFFDDB36C))
    }
   }
  }
  cart.values.forEach { (item,q) -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text("${item.name} × $q",color=Color(0xFFFFF4DF))
   Row { TextButton(onClick={val n=q-1;cart=if(n<=0)cart-item.id else cart+(item.id to (item to n))}){Text("−")};TextButton(onClick={cart=cart+(item.id to (item to q+1))}){Text("+")} }
  } }
  OutlinedTextField(customerPhone,{customerPhone=it.filter(Char::isDigit).take(10)},label={Text("Customer mobile (optional, for loyalty)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,modifier=Modifier.fillMaxWidth())
  if(cart.isNotEmpty()) {
   if (loyaltyRewards > 0 && coffeeItem != null && customerPhone.length == 10) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
     Checkbox(checked=redeemFreeCoffee,onCheckedChange={redeemFreeCoffee=it})
     Text("Redeem 1 free coffee (available: $loyaltyRewards)",color=Color(0xFFDDB36C))
    }
   }
   Text("Total ₹%.2f".format(total/100.0),color=Color(0xFFDDB36C),style=MaterialTheme.typography.titleLarge)
   if (missingRecipes.isNotEmpty()) {
    Text("Recipe required before sale: " + missingRecipes.joinToString { it.name }, color=Color(0xFFFFB36B))
   }
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    FilterChip(selected=payment=="CASH",onClick={payment="CASH"},label={Text("Cash")})
    FilterChip(selected=payment=="UPI",onClick={payment="UPI"},label={Text("UPI")})
    Button(enabled=missingRecipes.isEmpty() && (!redeemFreeCoffee || (customerPhone.length == 10 && loyaltyRewards > 0 && coffeeItem != null)),onClick={
     val id=UUID.randomUUID().toString()
     val lines=cart.values.map{(i,q)->SaleLine(UUID.randomUUID().toString(),id,i.id,i.name,q,i.pricePaise,i.pricePaise*q)}.toMutableList()
     if (redeemFreeCoffee && coffeeItem != null) lines.add(SaleLine(UUID.randomUUID().toString(),id,coffeeItem.id,coffeeItem.name,1,0L,0L))
     scope.launch {
      runCatching { db.dao().completeSale(Sale(id,System.currentTimeMillis(),total,payment,customerPhone=customerPhone),lines) }
       .onSuccess { cart=emptyMap();customerPhone="";redeemFreeCoffee=false;loyaltyRewards=0;msg="Sale saved offline. Loyalty updated and reward redeemed if selected." }
       .onFailure { msg=it.message ?: "Sale could not be saved" }
     }
    }){Text("Complete sale")}
   }
  }
  Text(msg,color=Color(0xFFDDB36C));Text("Saved bills: ${sales.size}",color=Color(0xFFFFF4DF))
 }
}
