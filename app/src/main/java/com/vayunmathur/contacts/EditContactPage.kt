package com.vayunmathur.contacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactPage(backStack: NavBackStack<NavKey>, viewModel: ContactViewModel, contactId: Long?) {
    val contact = remember { contactId?.let { viewModel.getContact(it) } }
    val details = contact?.details
    val context = LocalContext.current

    var namePrefix by remember { mutableStateOf(contact?.name?.namePrefix ?: "") }
    var firstName by remember { mutableStateOf(contact?.name?.firstName ?: "") }
    var middleName by remember { mutableStateOf(contact?.name?.middleName ?: "") }
    var lastName by remember { mutableStateOf(contact?.name?.lastName ?: "") }
    var nameSuffix by remember { mutableStateOf(contact?.name?.nameSuffix ?: "") }
    var company by remember { mutableStateOf(contact?.org?.company ?: "") }
    var noteContent by remember { mutableStateOf(contact?.note?.content ?: "") }
    var photo by remember { mutableStateOf(contact?.photo) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            val value = Base64.encode(byteArrayOutputStream.toByteArray())
            photo = photo?.withValue(value) ?: Photo(0, value)
        }
    }
    val phoneNumbers = remember { mutableStateListOf(*details?.phoneNumbers?.toTypedArray()?:emptyArray()) }
    val emails = remember { mutableStateListOf(*details?.emails?.toTypedArray()?:emptyArray()) }
    val dates = remember { mutableStateListOf(*details?.dates?.toTypedArray()?:emptyArray()) }
    val addresses = remember { mutableStateListOf(*details?.addresses?.toTypedArray()?:emptyArray()) }

    Scaffold(
        contentWindowInsets = WindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(if (contact == null) "Add contact" else "Edit contact") },
                navigationIcon = {
                    IconButton(onClick = { backStack.removeAt(backStack.lastIndex) }) {
                        Icon(painterResource(R.drawable.outline_close_24), contentDescription = "Close")
                    }
                },
                actions = {
                    Button(onClick = {
                        val newContact = Contact(
                            contact?.id ?: 0,
                            contact?.lookupKey ?: "",
                            contact?.isFavorite ?: false,
                            ContactDetails(
                                phoneNumbers,
                                emails,
                                addresses,
                                dates,
                                listOfNotNull(photo),
                                listOf(Name(contact?.name?.id ?: 0, namePrefix, firstName, middleName, lastName, nameSuffix)),
                                listOf(Organization(contact?.org?.id ?: 0, company)),
                                listOf(Note(contact?.note?.id ?: 0, noteContent))
                            )
                        )
                        viewModel.saveContact(newContact)
                        backStack.removeAt(backStack.lastIndex)
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            AddPictureSection(photo?.photo, {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, {
                photo = null
            })
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                leadingIcon = { NamePrefixChooser(namePrefix) { namePrefix = it } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = middleName,
                onValueChange = { middleName = it },
                label = { Text("Middle name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                trailingIcon = { NameSuffixChooser(nameSuffix) { nameSuffix = it } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Company") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            DetailsSection(
                "Phone",
                phoneNumbers,
                painterResource(R.drawable.outline_call_24),
                KeyboardType.Phone,
                VisualTransformation.None,
                listOf(CDKPhone.TYPE_MOBILE, CDKPhone.TYPE_HOME, CDKPhone.TYPE_WORK, CDKPhone.TYPE_OTHER)
            )
            Spacer(Modifier.height(8.dp))

            DetailsSection(
                "Email",
                emails,
                painterResource(R.drawable.outline_mail_24),
                KeyboardType.Email,
                VisualTransformation.None,
                listOf(CDKEmail.TYPE_HOME, CDKEmail.TYPE_WORK, CDKEmail.TYPE_OTHER, CDKEmail.TYPE_MOBILE)
            )

            Spacer(Modifier.height(16.dp))

            DateDetailsSection(
                dates,
                painterResource(R.drawable.outline_event_24),
                listOf(CDKEvent.TYPE_BIRTHDAY, CDKEvent.TYPE_ANNIVERSARY, CDKEvent.TYPE_OTHER)
            )

            Spacer(Modifier.height(12.dp))

            DetailsSection(
                "Addresses",
                addresses,
                painterResource(R.drawable.outline_event_24),
                KeyboardType.Text,
                VisualTransformation.None,
                listOf(CDKStructuredPostal.TYPE_HOME, CDKStructuredPostal.TYPE_WORK, CDKStructuredPostal.TYPE_OTHER)
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                label = { Text("Note") },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.outline_edit_24),
                        contentDescription = "Note"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun getCountryFlagEmoji(phoneNumber: String): String {
    val phoneUtil = PhoneNumberUtil.getInstance()
    return try {
        val numberProto = phoneUtil.parse(phoneNumber, "")
        val regionCode = phoneUtil.getRegionCodeForNumber(numberProto)
        val firstLetter = Character.codePointAt(regionCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(regionCode, 1) - 0x41 + 0x1F1E6
        String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    } catch (_: Exception) {
        ""
    }
}

val namePrefixes = listOf("None", "Dr", "Mr", "Mrs", "Ms")
val nameSuffixes = listOf("None", "Jr", "Sr", "I", "II", "III", "IV", "V")

@Composable
fun NamePrefixChooser(namePrefix: String, onNamePrefixChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(namePrefix)
        Icon(
            painterResource(R.drawable.baseline_arrow_drop_down_24),
            contentDescription = null
        )
        DropdownMenu(expanded, { expanded = false }) {
            namePrefixes.forEach { prefix ->
                DropdownMenuItem(text = { Text(prefix) }, onClick = {
                    onNamePrefixChange(if(prefix == "None") "" else prefix)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun NameSuffixChooser(nameSuffix: String, onNameSuffixChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(nameSuffix)
        Icon(painterResource(R.drawable.baseline_arrow_drop_down_24), null)
        DropdownMenu(expanded, { expanded = false }) {
            nameSuffixes.forEach { suffix ->
                DropdownMenuItem(text = { Text(suffix) }, onClick = {
                    onNameSuffixChange(suffix)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun ColumnScope.DateDetailsSection(
    details: SnapshotStateList<Event>,
    icon: Painter,
    options: List<Int>
) {
    val detailType = "Dates"
    val context = LocalContext.current
    details.forEachIndexed { index, detail ->
        var openDialog by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = detail.startDate.format(LocalDate.Format {
                    monthName(MonthNames.ENGLISH_FULL)
                    chars(" ")
                    day()
                    chars(", ")
                    year()
                }),
                onValueChange = { },
                readOnly = true,
                label = { Text(detailType) },
                trailingIcon = {
                    Row {
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        TextButton({ dropdownExpanded = true }) {
                            Text(detail.typeString(context))
                            Icon(
                                painterResource(R.drawable.baseline_arrow_drop_down_24),
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    onClick = {
                                        details[index] = detail.withType(option)
                                        dropdownExpanded = false
                                    },
                                    text = { Text(ContactDetail.default<Event>().withType(option).typeString(context)) }
                                )
                            }
                        }
                        IconButton(onClick = { details.removeAt(index) }) {
                            Icon(
                                painterResource(R.drawable.baseline_remove_circle_outline_24),
                                "Remove $detailType"
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { openDialog = true }
            )
        }

        if (openDialog) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = detail.startDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            )
            DatePickerDialog(
                onDismissRequest = { openDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            details[index] = detail.withValue(
                                Instant.fromEpochMilliseconds(it).toLocalDateTime(
                                TimeZone.UTC).date.format(LocalDate.Formats.ISO))
                        }
                        openDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openDialog = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (details.isEmpty()) {
        FilledTonalButton(
            onClick = { details += ContactDetail.default<Event>() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add $detailType")
        }
    } else {
        TextButton(
            onClick = { details += ContactDetail.default<Event>() },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Add $detailType")
        }
    }
}

@Composable
private inline fun <reified T : ContactDetail<T>> ColumnScope.DetailsSection(
    detailType: String,
    details: SnapshotStateList<T>,
    icon: Painter,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation,
    options: List<Int>
) {
    val context = LocalContext.current
    details.forEachIndexed { index, detail ->
        OutlinedTextField(
            value = detail.value,
            onValueChange = { newNumber ->
                details[index] = detail.withValue(newNumber)
            },
            visualTransformation = visualTransformation,
            label = { Text(detailType) },
            leadingIcon = {
                if (detail is PhoneNumber) {
                    Text(getCountryFlagEmoji(detail.value))
                }
            },
            trailingIcon = {
                Row {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    TextButton({ dropdownExpanded = true }) {
                        Text(detail.typeString(context))
                        Icon(
                            painterResource(R.drawable.baseline_arrow_drop_down_24),
                            contentDescription = null
                        )
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                onClick = {
                                    details[index] = detail.withType(option)
                                    dropdownExpanded = false
                                },
                                text = { Text(ContactDetail.default<T>().withType(option).typeString(context)) }
                            )
                        }
                    }
                    IconButton(onClick = { details.removeAt(index) }) {
                        Icon(
                            painterResource(R.drawable.baseline_remove_circle_outline_24),
                            "Remove phone"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
    if(details.isEmpty()) {
        FilledTonalButton(
            onClick = { details += ContactDetail.default<T>() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add $detailType")
        }
    } else {
        TextButton(
            onClick = { details += ContactDetail.default<T>() },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Add $detailType")
        }
    }
}

@Composable
private fun AddPictureSection(photo: String?, onClick: () -> Unit, removePhoto: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) {
                val decoded = Base64.decode(photo)
                val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Contact photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painterResource(R.drawable.outline_add_photo_alternate_24),
                    contentDescription = "Add picture",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick) {
                Text(
                    text = if (photo != null) "Change" else "Add picture",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (photo != null) {
                TextButton(removePhoto) {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
