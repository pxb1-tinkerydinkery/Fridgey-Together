package lying.fengfeng.foodrecords.ui.settings.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import java.time.LocalDate
import java.util.UUID

private data class SharedFood(
    val uuid: String,
    val foodName: String,
    val foodType: String,
    val amount: Long,
    val expiryDate: String,
    val tips: String,
    val revision: Long
) {
    fun fields() = mapOf(
        "uuid" to uuid, "foodName" to foodName, "foodType" to foodType,
        "amount" to amount, "expiryDate" to expiryDate, "tips" to tips,
        "revision" to revision
    )
}

private fun DocumentSnapshot.asSharedFood(): SharedFood? {
    return runCatching {
        SharedFood(
            id, getString("foodName") ?: return null,
            getString("foodType") ?: "",
            getLong("amount") ?: return null,
            getString("expiryDate") ?: return null,
            getString("tips") ?: "",
            getLong("revision") ?: return null
        )
    }.getOrNull()
}

@Composable
internal fun SharedInventory() {
    val db = remember { FirebaseFirestore.getInstance() }
    val foods = remember { db.collection("households").document("home").collection("foods") }
    var items by remember { mutableStateOf<List<SharedFood>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connecting to household…") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SharedFood?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<SharedFood?>(null) }

    DisposableEffect(foods) {
        val listener = foods.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
            if (failure != null || snapshot == null) {
                connected = false
                items = emptyList()
                status = "Cannot access shared fridge. Check household permissions and connection."
            } else if (snapshot.metadata.isFromCache) {
                connected = false
                status = "Waiting for server connection. Changes are disabled."
            } else {
                items = snapshot.documents.mapNotNull { it.asSharedFood() }
                    .sortedWith(compareBy<SharedFood> { it.expiryDate }.thenBy { it.foodName })
                connected = true
                status = "Connected to shared fridge"
            }
        }
        onDispose { listener.remove() }
    }

    fun changeAmount(food: SharedFood, delta: Long) {
        if (!connected || busy) return
        busy = true
        error = ""
        val ref = foods.document(food.uuid)
        db.runTransaction { transaction ->
            val current = transaction.get(ref).asSharedFood()
                ?: kotlin.error("Item no longer exists")
            val next = current.amount + delta
            if (next < 1) transaction.delete(ref)
            else if (next <= 100000L) transaction.update(
                ref, mapOf("amount" to next, "revision" to current.revision + 1)
            )
            else kotlin.error("Quantity limit exceeded")
            Unit
        }.addOnCompleteListener { task ->
            busy = false
            if (!task.isSuccessful) error = "Could not change quantity. Refresh and try again."
        }
    }

    Text(status)
    if (error.isNotBlank()) Text(error)
    Button(enabled = connected && !busy, onClick = {
        editing = null
        showEditor = true
    }) { Text("Add shared food") }
    if (connected && items.isEmpty()) Text("Your shared fridge is empty.")
    items.forEach { food ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(food.foodName + " • " + food.amount)
                if (food.foodType.isNotBlank()) Text(food.foodType)
                Text("Expiry: " + food.expiryDate)
                if (food.tips.isNotBlank()) Text(food.tips)
                Row {
                    TextButton(enabled = connected && !busy,
                        onClick = { changeAmount(food, -1) }) { Text("−1") }
                    TextButton(enabled = connected && !busy,
                        onClick = { changeAmount(food, 1) }) { Text("+1") }
                    TextButton(enabled = connected && !busy, onClick = {
                        editing = food
                        showEditor = true
                    }) { Text("Edit") }
                    TextButton(enabled = connected && !busy,
                        onClick = { removing = food }) { Text("Remove") }
                }
            }
        }
    }

    if (showEditor) {
        val original = editing
        SharedFoodEditor(original, connected && !busy,
            onDismiss = { if (!busy) showEditor = false },
            onSave = { food ->
                if (connected && !busy) {
                    busy = true
                    error = ""
                    val ref = foods.document(food.uuid)
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(ref)
                        if (original == null) {
                            check(!snapshot.exists()) { "Item already exists" }
                            transaction.set(ref, food.fields())
                        } else {
                            val current = snapshot.asSharedFood() ?: kotlin.error("Item no longer exists")
                            check(current.revision == original.revision) { "Item changed elsewhere" }
                            transaction.set(ref, food.copy(revision = current.revision + 1).fields())
                        }
                        Unit
                    }.addOnCompleteListener { task ->
                        busy = false
                        if (task.isSuccessful) showEditor = false
                        else {
                            error = "Save failed. The item may have changed on the other phone. Try again."
                            showEditor = false
                        }
                    }
                }
            }
        )
    }

    removing?.let { food ->
        AlertDialog(
            onDismissRequest = { if (!busy) removing = null },
            title = { Text("Remove " + food.foodName + "?") },
            text = { Text("This removes the item from both phones.") },
            confirmButton = {
                Button(enabled = connected && !busy, onClick = {
                    busy = true
                    error = ""
                    val ref = foods.document(food.uuid)
                    db.runTransaction { transaction ->
                        val current = transaction.get(ref).asSharedFood()
                            ?: kotlin.error("Item no longer exists")
                        check(current.revision == food.revision) { "Item changed elsewhere" }
                        transaction.delete(ref)
                        Unit
                    }.addOnCompleteListener { task ->
                        busy = false
                        removing = null
                        if (!task.isSuccessful) error = "Remove failed. Refresh and try again."
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { removing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedFoodEditor(
    original: SharedFood?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (SharedFood) -> Unit
) {
    var name by remember { mutableStateOf(original?.foodName ?: "") }
    var category by remember { mutableStateOf(original?.foodType ?: "") }
    var amount by remember { mutableStateOf(original?.amount?.toString() ?: "1") }
    var expiry by remember { mutableStateOf(original?.expiryDate ?: LocalDate.now().toString()) }
    var notes by remember { mutableStateOf(original?.tips ?: "") }
    val uuid = remember { original?.uuid ?: UUID.randomUUID().toString() }
    val count = amount.toLongOrNull()
    val validDate = runCatching { LocalDate.parse(expiry).toString() == expiry }.getOrDefault(false)
    val valid = name.isNotBlank() && name.length <= 200 && category.length <= 100 &&
        count != null && count in 1L..100000L && validDate && expiry.length == 10 && notes.length <= 2000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Add shared food" else "Edit shared food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Food name") }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Quantity (1–100000)") }, singleLine = true)
                OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(enabled = enabled && valid, onClick = {
                onSave(SharedFood(uuid, name.trim(), category.trim(), count ?: 1L, expiry, notes, original?.revision ?: 1L))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
