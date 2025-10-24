package com.vayunmathur.contacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.SortedMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.encoding.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactList(navController: NavController) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    var isInSelectionMode by remember { mutableStateOf(false) }
    var selectedContactIds by remember { mutableStateOf(emptySet<Long>()) }

    val scope = rememberCoroutineScope()

    BackHandler(enabled = isInSelectionMode) {
        isInSelectionMode = false
        selectedContactIds = emptySet()
    }

    LaunchedEffect(Unit) {
        contacts = Contact.getAllContacts(context).sortedBy { it.name }
    }

    val (favorites, otherContacts) = contacts.partition { it.isFavorite }

    val groupedContacts: SortedMap<Char, List<Contact>> = otherContacts
        .groupBy { it.name.first().uppercaseChar() }
        .toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    if (isInSelectionMode) {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                selectedContactIds.forEach { id ->
                                    Contact.delete(context, contacts.first { it.id == id })
                                }

                                withContext(Dispatchers.Main) {
                                    contacts = contacts.filterNot { it.id in selectedContactIds }
                                    isInSelectionMode = false
                                    selectedContactIds = emptySet()
                                }
                            }
                        }) {
                            Icon(painterResource(R.drawable.outline_delete_24),
                                contentDescription = "Delete selected contacts"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Favorites Section ---
            if (favorites.isNotEmpty()) {
                item {
                    FavoritesHeader()
                }
                // Add the list of favorite contacts
                items(favorites, key = { it.id }) { contact ->
                    val isSelected = selectedContactIds.contains(contact.id)

                    ContactItem(
                        contact = contact,
                        isSelected = isSelected,
                        onClick = {
                            if (isInSelectionMode) {
                                val newSelection = if (isSelected) {
                                    selectedContactIds - contact.id
                                } else {
                                    selectedContactIds + contact.id
                                }
                                if (newSelection.isEmpty()) {
                                    isInSelectionMode = false
                                }
                                selectedContactIds = newSelection
                            } else {
                                navController.navigate(ContactDetailsScreen(Json.encodeToString(contact)))
                            }
                        },
                        onLongClick = {
                            isInSelectionMode = true
                            selectedContactIds = selectedContactIds + contact.id
                        }
                    )
                }
            }

            // --- Alphabetical Sections ---
            groupedContacts.forEach { (letter, contactsInGroup) ->
                item {
                    LetterHeader(letter)
                }
                items(contactsInGroup, key = { it.id }) { contact ->
                    val isSelected = selectedContactIds.contains(contact.id)

                    ContactItem(
                        contact = contact,
                        isSelected = isSelected,
                        onClick = {
                            if (isInSelectionMode) {
                                val newSelection = if (isSelected) {
                                    selectedContactIds - contact.id
                                } else {
                                    selectedContactIds + contact.id
                                }
                                if (newSelection.isEmpty()) {
                                    isInSelectionMode = false
                                }
                                selectedContactIds = newSelection
                            } else {
                                navController.navigate(ContactDetailsScreen(Json.encodeToString(contact)))
                            }
                        },
                        onLongClick = {
                            isInSelectionMode = true
                            selectedContactIds = selectedContactIds + contact.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painterResource(R.drawable.baseline_star_24), "Favorites",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LetterHeader(letter: Char, modifier: Modifier = Modifier) {
    Text(
        text = letter.toString(),
        modifier = modifier.padding(vertical = 8.dp, horizontal = 4.dp), // Add slight horizontal padding
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

fun getAvatarColor(id: Long): Color {
    val colors = listOf(
        Color(0xFF6C3800),
        Color(0xFF00502A),
        Color(0xFF8B0053),
        Color(0xFF891916),
        Color(0xFF004B5B),
        Color(0xFF5528A1),
    )
    val index = (id % colors.size).toInt()
    return colors[index]
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    contact: Contact,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        headlineContent = {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                contact.photo?.let {
                    val bitmap by remember(it) {
                        mutableStateOf<Bitmap>(BitmapFactory.decodeByteArray(Base64.decode(it), 0, Base64.decode(it).size))
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "${contact.name} photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                if (contact.photo == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = getAvatarColor(contact.id),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.first().uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },

        trailingContent = null,

        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    )
}