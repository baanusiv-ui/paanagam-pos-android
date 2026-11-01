package in.paanagam.business
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
import in.paanagam.business.data.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable fun PosScreen(db: PaanagamDatabase) {
 val catalog by db.dao().catalog().collectAsState(initial=emptyList())
 val sales by db.dao().sales().collectAsState(initial=emptyList())
 val scope=rememberCoroutineScope()
 var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Beverages") }; var price by remember { mutableStateOf("") }
 var cart by remember { mutableStateOf<Map<String,Pair<CatalogItem,Int>>>(emptyMap()) }; var payment by remember { mutableStateOf("CASH") }; var msg by remember { mutableStateOf("") }
 val total=cart.values.sumOf { it.first.pricePaise*it.second }
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
  if(cart.isNotEmpty()) {
   Text("Total ₹%.2f".format(total/100.0),color=Color(0xFFDDB36C),style=MaterialTheme.typography.titleLarge)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    FilterChip(selected=payment=="CASH",onClick={payment="CASH"},label={Text("Cash")})
    FilterChip(selected=payment=="UPI",onClick={payment="UPI"},label={Text("UPI")})
    Button(onClick={val id=UUID.randomUUID().toString();val lines=cart.values.map{(i,q)->SaleLine(UUID.randomUUID().toString(),id,i.id,i.name,q,i.pricePaise,i.pricePaise*q)};scope.launch{db.dao().completeSale(Sale(id,System.currentTimeMillis(),total,payment),lines);cart=emptyMap();msg="Sale saved offline"}}){Text("Complete sale")}
   }
  }
  Text(msg,color=Color(0xFFDDB36C));Text("Saved bills: ${sales.size}",color=Color(0xFFFFF4DF))
 }
}
