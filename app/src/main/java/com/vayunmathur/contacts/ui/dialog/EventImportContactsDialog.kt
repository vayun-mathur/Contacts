package com.vayunmathur.contacts.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.vayunmathur.contacts.ContactViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@Composable
fun EventImportContactsDialog(
    uriString: String?,
    viewModel: ContactViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Import Contacts") },
        text = { Text("Do you want to import contacts from this file?") },
        confirmButton = {
            TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        uriString?.let {
                            val uri = it.toUri()
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                com.vayunmathur.contacts.VcfUtils.importContacts(context, inputStream)
                                viewModel.loadContacts()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                onConfirm()
            }) { Text("Yes") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("No") }
        }
    )
}

