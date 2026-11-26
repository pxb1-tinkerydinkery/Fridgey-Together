package lying.fengfeng.foodrecords.ui.settings.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SharedFridgeOption() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var showDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Shared fridge (preview)")
        Text(user?.email?.let { "Signed in as $it" } ?: "Sign in with your household account.")
        Text("Account sign-in only. Inventory sharing is not enabled in this build.")
        if (user == null) {
            Button(onClick = { error = ""; showDialog = true }, enabled = !busy) {
                Text("Sign in")
            }
        } else {
            TextButton(onClick = { auth.signOut(); password = ""; email = ""; error = "" }) {
                Text("Sign out")
            }
        }
    }

    if (showDialog && user == null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) { showDialog = false; password = ""; error = "" }
            },
            title = { Text("Sign in to Fridgey Together") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it; error = "" },
                        label = { Text("Email") }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; error = "" },
                        label = { Text("Password") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (error.isNotEmpty()) Text(error)
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                    onClick = {
                        busy = true
                        error = ""
                        auth.signInWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                busy = false
                                password = ""
                                if (task.isSuccessful) {
                                    showDialog = false
                                } else {
                                    error = "Unable to sign in. Check your account details and internet connection."
                                }
                            }
                    }
                ) { Text(if (busy) "Signing in…" else "Sign in") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    showDialog = false; password = ""; error = ""
                }) { Text("Cancel") }
            }
        )
    }
}
