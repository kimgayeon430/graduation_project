package smu.ai.graduation_project.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.SupabaseStorage
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val db = Firebase.firestore
    val emptyFieldsMessage = stringResource(R.string.signup_toast_empty_fields)
    val signupFailedMessage = stringResource(R.string.signup_toast_failed)
    val profileSaveFailedMessage = stringResource(R.string.signup_toast_profile_save_failed)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedPhotoUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.signup_title)) },
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
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(LightPurple)
                    .border(2.dp, MainPurple, CircleShape)
                    .clickable(enabled = !isLoading) {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                if (pickedPhotoUri != null) {
                    AsyncImage(
                        model = pickedPhotoUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.AddAPhoto, null, tint = MainPurple, modifier = Modifier.size(32.dp))
                }
            }
            Text(stringResource(R.string.signup_photo_label), fontSize = 12.sp, color = MainPurple)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.signup_name_label)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                enabled = !isLoading
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.signup_email_label)) },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                enabled = !isLoading
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.signup_password_label)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                enabled = !isLoading
            )

            if (isLoading) {
                CircularProgressIndicator()
            }

            Button(
                onClick = {
                    if (name.isBlank() || email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, emptyFieldsMessage, Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                isLoading = false
                                Toast.makeText(
                                    context,
                                    task.exception?.message ?: signupFailedMessage,
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

                                    fun saveUserDoc(photoUrl: String?) {
                                        val data = mutableMapOf<String, Any>(
                                            "nickname" to name.trim(),
                                            "mail" to email.trim(),
                                            "points" to 0,
                                            "level" to "Lv.1"
                                        )
                                        if (photoUrl != null) data["photoUrl"] = photoUrl
                                        db.collection("users").document(uid)
                                            .set(data)
                                            .addOnSuccessListener {
                                                isLoading = false
                                                onSignUpSuccess()
                                            }
                                            .addOnFailureListener { error ->
                                                isLoading = false
                                                Toast.makeText(
                                                    context,
                                                    error.message ?: profileSaveFailedMessage,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onSignUpSuccess()
                                            }
                                    }

                                    val photoUri = pickedPhotoUri
                                    if (photoUri != null) {
                                        scope.launch {
                                            val photoUrl = try {
                                                withContext(Dispatchers.IO) {
                                                    val bytes = context.contentResolver.openInputStream(photoUri)
                                                        ?.use { it.readBytes() }
                                                        ?: throw IOException("cannot read picked image")
                                                    SupabaseStorage.upload("avatars/${uid}_${System.currentTimeMillis()}.jpg", bytes)
                                                }
                                            } catch (e: Exception) {
                                                null
                                            }
                                            saveUserDoc(photoUrl)
                                        }
                                    } else {
                                        saveUserDoc(null)
                                    }
                                }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
            ) {
                Text(stringResource(R.string.signup_btn))
            }
        }
    }
}
