package smu.ai.graduation_project.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val auth = Firebase.auth
    val db = Firebase.firestore

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign up") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                enabled = !isLoading
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                enabled = !isLoading
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                enabled = !isLoading
            )

            if (isLoading) {
                CircularProgressIndicator()
            }

            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Fill in all fields.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                isLoading = false
                                Toast.makeText(
                                    context,
                                    task.exception?.message ?: "Sign up failed.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@addOnCompleteListener
                            }

                            val user = auth.currentUser
                            val profileUpdate = userProfileChangeRequest {
                                displayName = name.trim()
                            }

                            user?.updateProfile(profileUpdate)
                                ?.addOnCompleteListener {
                                    val uid = user?.uid
                                    if (uid == null) {
                                        isLoading = false
                                        onSignUpSuccess()
                                        return@addOnCompleteListener
                                    }

                                    db.collection("users").document(uid)
                                        .set(
                                            mapOf(
                                                "nickname" to name.trim(),
                                                "mail" to email.trim(),
                                                "points" to 0,
                                                "level" to "Lv.1"
                                            )
                                        )
                                        .addOnSuccessListener {
                                            isLoading = false
                                            onSignUpSuccess()
                                        }
                                        .addOnFailureListener { error ->
                                            isLoading = false
                                            Toast.makeText(
                                                context,
                                                error.message ?: "Profile save failed.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onSignUpSuccess()
                                        }
                                }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
            ) {
                Text("Create account")
            }
        }
    }
}
