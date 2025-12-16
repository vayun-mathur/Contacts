package com.vayunmathur.contacts

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlin.io.encoding.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsPage(backStack: NavBackStack<NavKey>, viewModel: ContactViewModel, contactId: Long) {
    val contact = viewModel.getContact(contactId)
    if (contact == null) {
        Text("Contact not found")
        return
    }
    val context = LocalContext.current
    val details = contact.getDetails(context)
    var isFavorite by remember { mutableStateOf(contact.isFavorite) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* No title in the reference image */ },
                navigationIcon = {
                    IconButton({ backStack.removeAt(backStack.lastIndex) }) {
                        Icon(painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newFavoriteState = !isFavorite
                        isFavorite = newFavoriteState
                        Contact.setFavorite(context, contact.id, newFavoriteState)
                    }) {
                        Icon(
                            if (!isFavorite) painterResource(R.drawable.outline_star_24) else painterResource(R.drawable.baseline_star_24),
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { backStack.add(EditContactScreen(contact.id)) }) {
                        Icon(painterResource(R.drawable.outline_edit_24),
                            contentDescription = "Edit"
                        )
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            Contact.delete(context, contact)
                            withContext(Dispatchers.Main) {
                                backStack.removeAt(backStack.lastIndex)
                            }
                        }
                    }) {
                        Icon(painterResource(R.drawable.outline_delete_24),
                            contentDescription = "Delete"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            item {
                ProfileHeader(contact)
            }

            item {
                ActionButtonsRow(details.phoneNumbers.firstOrNull()?.number, details.emails.firstOrNull()?.address)
            }

            items(details.phoneNumbers, key = { it.id }) { phone ->
                DetailItem(
                    icon = painterResource(R.drawable.outline_call_24),
                    data = formatPhoneNumber(phone.number),
                    label = phone.typeString(context),
                    trailingIcon = painterResource(R.drawable.outline_chat_24),
                    onTrailingIconClick = {
                        val intent = Intent(Intent.ACTION_SENDTO)
                        intent.data = "sms:${phone.number}".toUri()
                        context.startActivity(intent)
                    }
                )
            }
            items(details.emails, key = { it.id }) { email ->
                DetailItem(
                    icon = painterResource(R.drawable.outline_mail_24),
                    data = email.address,
                    label = email.typeString(context)
                )
            }
            items(details.addresses, key = { it.id }) { address ->
                DetailItem(
                    icon = painterResource(R.drawable.outline_location_on_24),
                    data = address.formattedAddress,
                    label = address.typeString(context),
                    trailingIcon = painterResource(R.drawable.outline_directions_24),
                    onTrailingIconClick = {
                        val gmmIntentURI = "geo:0,0?q=${Uri.encode(address.formattedAddress)}".toUri()
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentURI)
                        context.startActivity(mapIntent)
                    }
                )
            }

            if(details.dates.isNotEmpty()) {
                item {
                    GroupedSection(title = "About ${contact.firstName}") {
                        details.dates.forEach { event ->
                            val eventIcon = when (event.typeString(context).lowercase()) {
                                "birthday" -> painterResource(R.drawable.outline_cake_24)
                                else -> painterResource(R.drawable.outline_event_24)
                            }
                            val date = event.startDate.format(LocalDate.Format {
                                monthName(MonthNames.ENGLISH_FULL)
                                chars(" ")
                                day()
                                chars(", ")
                                year()
                            })
                            ListItem(
                                headlineContent = { Text(date) },
                                supportingContent = { Text(event.typeString(context)) },
                                leadingContent = {
                                    Icon(eventIcon, event.typeString(context))
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(contact: Contact) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            contact.photo?.let {
                val bitmap by remember(it) {
                    mutableStateOf<Bitmap>(BitmapFactory.decodeByteArray(Base64.decode(it), 0, Base64.decode(it).size)) }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "${contact.name} photo",
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (contact.photo == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(getAvatarColor(contact.id)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.first().uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        Text(
            text = contact.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = contact.companyName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ActionButtonsRow(number: String?, email: String?) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionButton(icon = painterResource(R.drawable.outline_call_24), label = "Call", active = number != null) {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = "tel:$number".toUri()
            context.startActivity(intent)
        }
        ActionButton(icon = painterResource(R.drawable.outline_sms_24), label = "Message", active = number != null) {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = "sms:$number".toUri()
            context.startActivity(intent)
        }
        ActionButton(icon = painterResource(R.drawable.outline_videocam_24), label = "Video", showBadge = true, active = number != null) {

        }
        ActionButton(icon = painterResource(R.drawable.outline_mail_24), label = "Email", active = email != null) {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = "mailto:$email".toUri()
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionButton(
    icon: Painter,
    label: String,
    showBadge: Boolean = false,
    active: Boolean = true,
    action: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BadgedBox(
            badge = {}
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { if(active) action() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun DetailItem(
    icon: Painter,
    data: String,
    label: String,
    trailingIcon: Painter? = null,
    onTrailingIconClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(data, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(label, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { Icon(icon, label) },
            trailingContent = {
                if (trailingIcon != null && onTrailingIconClick != null) {
                    IconButton(onClick = onTrailingIconClick) {
                        Icon(trailingIcon, "Action")
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun GroupedSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        content()
    }
}

fun formatPhoneNumber(numberString: String, defaultRegion: String = "US"): String {
    val phoneUtil = PhoneNumberUtil.getInstance()

    return try {
        val phoneNumber = phoneUtil.parse(numberString, defaultRegion)
        if (!phoneUtil.isValidNumber(phoneNumber)) return numberString
        val regionOfNumber = phoneUtil.getRegionCodeForNumber(phoneNumber)
        val formatType = if (regionOfNumber == defaultRegion) {
            PhoneNumberUtil.PhoneNumberFormat.NATIONAL
        } else {
            PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
        }

        phoneUtil.format(phoneNumber, formatType)

    } catch (e: NumberParseException) {
        println("Error parsing number: ${e.message}")
        numberString
    }
}