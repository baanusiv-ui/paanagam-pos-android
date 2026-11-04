package `in`.paanagam.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.PaanagamDatabase
import `in`.paanagam.business.data.Supplier
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SuppliersScreen(db: PaanagamDatabase) {
    val suppliers by db.dao().suppliers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Supplier register", color = Color(0xFF16845B), style = MaterialTheme.typography.titleLarge)
        Text("Save supplier details for purchase records. Existing purchase entries remain unchanged.", color = Color(0xFF65736B), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("Supplier / business name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(contact, { contact = it }, label = { Text("Contact person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(gstin, { gstin = it.uppercase().take(15) }, label = { Text("GSTIN (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Address / notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = {
            if (name.isBlank()) message = "Supplier name is required."
            else scope.launch {
                runCatching {
                    db.dao().insertSupplier(Supplier(UUID.randomUUID().toString(), name.trim(), contact.trim(), phone.trim(), gstin.trim(), notes.trim(), true, System.currentTimeMillis()))
                }.onSuccess {
                    name = ""; contact = ""; phone = ""; gstin = ""; notes = ""
                    message = "Supplier saved."
                }.onFailure { message = it.message ?: "Could not save supplier." }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save supplier") }
        if (message.isNotBlank()) Text(message, color = Color(0xFF16845B))
        Text("Saved suppliers • ${suppliers.size}", color = Color(0xFF20332B), style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            suppliers.forEach { supplier ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(supplier.name, color = Color(0xFF20332B), style = MaterialTheme.typography.titleSmall)
                            Text(if (supplier.active) "ACTIVE" else "INACTIVE", color = if (supplier.active) Color(0xFF16845B) else Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                        if (supplier.contactPerson.isNotBlank()) Text(supplier.contactPerson, color = Color(0xFF65736B))
                        if (supplier.phone.isNotBlank()) Text(supplier.phone, color = Color(0xFF65736B))
                        if (supplier.gstin.isNotBlank()) Text("GSTIN: ${supplier.gstin}", color = Color(0xFF65736B))
                        if (supplier.notes.isNotBlank()) Text(supplier.notes, color = Color(0xFF65736B))
                        TextButton(onClick = {
                            scope.launch { db.dao().updateSupplier(supplier.copy(active = !supplier.active)); message = if (supplier.active) "Supplier deactivated." else "Supplier reactivated." }
                        }) { Text(if (supplier.active) "Deactivate" else "Reactivate") }
                    }
                }
            }
        }
    }
}
