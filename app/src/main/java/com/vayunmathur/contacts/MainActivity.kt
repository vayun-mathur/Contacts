package com.vayunmathur.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vayunmathur.contacts.ui.theme.ContactsTheme
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            var hasPermissions by remember {
                mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })
            }
            val permissionRequestor = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                hasPermissions = permissions.values.all { it }
            }
            LaunchedEffect(Unit) {
                if (!hasPermissions) {
                    permissionRequestor.launch(permissions)
                }
            }
            ContactsTheme {
                if(!hasPermissions) {
                    Scaffold {
                        Box(modifier = Modifier
                            .padding(it)
                            .fillMaxSize()) {
                            Button({
                                permissionRequestor.launch(permissions)
                            }, Modifier.align(Alignment.Center)) {
                                Text(text = "Please grant contacts permission")
                            }
                        }
                    }
                } else {
                    if(intent.action == Intent.ACTION_PICK || intent.action == Intent.ACTION_GET_CONTENT) {
                        var type = intent.type
                        if(intent.data.toString().contains("phones")) {
                            type = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        }
                        println(type)
                        ContactListPick(type) {
                            val intent = Intent().apply {
                                data = it
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    } else {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = ContactsScreen) {
                            composable<ContactsScreen> {
                                ContactList(navController)
                            }
                            composable<ContactDetailsScreen> {
                                ContactDetailsPage(
                                    navController,
                                    Json.decodeFromString(it.toRoute<ContactDetailsScreen>().contact)
                                )
                            }
                            composable<EditContactScreen> {
                                val contact = it.toRoute<EditContactScreen>().contact?.let { Json.decodeFromString<Contact>(it) }
                                EditContactPage(
                                    navController,
                                    contact
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Serializable
object ContactsScreen

@Serializable
data class ContactDetailsScreen(val contact: String)

@Serializable
data class EditContactScreen(val contact: String?)