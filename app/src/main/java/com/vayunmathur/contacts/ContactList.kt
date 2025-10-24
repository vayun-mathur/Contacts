package com.vayunmathur.contacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.SortedMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.encoding.Base64

@Composable
fun ContactList(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    LaunchedEffect(Unit) {
        contacts = Contact.getAllContacts(context).sortedBy { it.name }
    }

    // Partition the contacts
    val (favorites, otherContacts) = contacts.partition { it.isFavorite }

    // Group the non-favorite contacts by their first letter
    val groupedContacts: SortedMap<Char, List<Contact>> = otherContacts
        .groupBy { it.name.first().uppercaseChar() }
        .toSortedMap()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Use contentPadding for space at the top/bottom
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
        // This spaces out every item in the list
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Favorites Section ---
        if (favorites.isNotEmpty()) {
            // Add the "Favorites" header as its own item
            item {
                FavoritesHeader()
            }
            // Add the list of favorite contacts
            items(favorites, key = { it.id }) { contact ->
                ContactItem(navController, contact)
            }
        }

        // --- Alphabetical Sections ---
        groupedContacts.forEach { (letter, contactsInGroup) ->
            // Add the letter header (e.g., "A")
            item {
                LetterHeader(letter)
            }
            // Add the list of contacts for that letter
            items(contactsInGroup, key = { it.id }) { contact ->
                ContactItem(navController, contact)
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


@Composable
fun ContactItem(navController: NavController, contact: Contact) {
    // Apply the clip and background to the ListItem itself
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                navController.navigate(ContactDetailsScreen(Json.encodeToString(contact)))
            }, // Rounded corners for each item
        headlineContent = {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        leadingContent = {
            // Use a simple Box for consistent alignment
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                contact.photo?.let {
                    val bitmap by remember(it) {
                        mutableStateOf<Bitmap>(BitmapFactory.decodeByteArray(Base64.decode(it), 0, Base64.decode(it).size))
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(), // Assuming photo is a Bitmap
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
                                color = getAvatarColor(contact.id), // Use dynamic color
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = contact.name.first().uppercase(),
                            color = Color.White, // Ensure text is visible on colored background
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        // Remove the trailing star icon
        trailingContent = null,
        // Set the item's background color
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // Use a theme color
        )
    )
}
