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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vayunmathur.contacts.ui.theme.ContactsTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            var hasPermissions by remember { mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) }
            ContactsTheme {
                if (!hasPermissions) {
                    NoPermissionsScreen(permissions) { hasPermissions = it }
                } else {
                    val viewModel: ContactViewModel = viewModel()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                viewModel.loadContacts()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    if ((intent?.scheme == "content" || intent?.scheme == "file") && intent?.type?.contains("vcard") == true && intent?.data != null) {
                         var showDialog by remember { mutableStateOf(true) }
                         val scope = rememberCoroutineScope()
                         
                         if(showDialog) {
                             AlertDialog(
                                 onDismissRequest = {
                                     showDialog = false
                                     finish()
                                 },
                                 title = { Text("Import Contacts") },
                                 text = { Text("Do you want to import contacts from this file?") },
                                 confirmButton = {
                                     TextButton(onClick = {
                                         scope.launch {
                                             try {
                                                 contentResolver.openInputStream(intent.data!!)?.use { inputStream ->
                                                     VcfUtils.importContacts(this@MainActivity, inputStream)
                                                     viewModel.loadContacts()
                                                 }
                                             } catch (e: Exception) {
                                                 e.printStackTrace()
                                             }
                                             showDialog = false
                                             // After import, navigate to the main contact list, clearing the intent to avoid re-triggering
                                             intent = Intent(this@MainActivity, MainActivity::class.java)
                                             // We don't finish(), we just let it fall through to Navigation below
                                         }
                                     }) {
                                         Text("Yes")
                                     }
                                 },
                                 dismissButton = {
                                     TextButton(onClick = {
                                         showDialog = false
                                         finish()
                                     }) {
                                         Text("No")
                                     }
                                 }
                             )
                         } else {
                             // After dialog is dismissed (and confirmed), show normal navigation
                             Navigation(viewModel)
                         }
                    }
                    else if (intent.action == Intent.ACTION_PICK || intent.action == Intent.ACTION_GET_CONTENT) {
                        var type = intent.type
                        if (intent.data.toString().contains("phones")) {
                            type = CDKPhone.CONTENT_ITEM_TYPE
                        }
                        val contacts by viewModel.contacts.collectAsState()
                        ContactListPick(type, contacts) {
                            val intent = Intent().apply {
                                data = it
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    } else {
                        Navigation(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun NoPermissionsScreen(permissions: Array<String>, setHasPermissions: (Boolean) -> Unit) {
    val permissionRequestor = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsResult ->
        setHasPermissions(permissionsResult.values.all { it })
    }
    LaunchedEffect(Unit) {
        permissionRequestor.launch(permissions)
    }
    Scaffold {
        Box(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            Button(
                {
                    permissionRequestor.launch(permissions)
                }, Modifier.align(Alignment.Center)
            ) {
                Text(text = "Please grant contacts permission")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Navigation(viewModel: ContactViewModel) {
    val backStack = rememberNavBackStack(ContactsScreen)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()


    Scaffold(contentWindowInsets = WindowInsets.displayCutout
    ) { paddingValues ->
        NavDisplay(modifier = Modifier.padding(paddingValues).consumeWindowInsets(paddingValues),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategy = listDetailStrategy,
            entryProvider = entryProvider {
                entry<ContactsScreen>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Select a contact to view details")
                    }
                })) {
                    ContactList(
                        viewModel = viewModel,
                        backStack = backStack,
                        onContactClick = { contact ->
                            if(backStack.last() is ContactDetailsScreen || backStack.last() is EditContactScreen) {
                                backStack[backStack.lastIndex] = ContactDetailsScreen(contact.id)
                            } else {
                                backStack.add(ContactDetailsScreen(contact.id))
                            }
                        },
                        onAddContactClick = {
                            if(backStack.last() is ContactDetailsScreen) {
                                backStack.removeAt(backStack.lastIndex)
                            }
                            backStack.add(EditContactScreen(null))
                        }
                    )
                }
                entry<ContactDetailsScreen>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    ContactDetailsPage(
                        viewModel = viewModel,
                        contactId = key.contactId,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { id -> backStack.add(EditContactScreen(id)) },
                        onDelete = { backStack.removeLastOrNull() },
                        showBackButton = true
                    )
                }
                entry<EditContactScreen>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    EditContactPage(backStack, viewModel, key.contactId)
                }
            }
        )
    }
}

@Serializable
object ContactsScreen : NavKey

@Serializable
data class ContactDetailsScreen(val contactId: Long) : NavKey

@Serializable
data class EditContactScreen(val contactId: Long?) : NavKey
