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
import androidx.navigation.NavController
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalEncodingApi::class)
@Composable
fun EditContactPage(navController: NavController, contact: Contact) {
    val context = LocalContext.current

    var namePrefix by remember { mutableStateOf(contact.namePrefix) }
    var firstName by remember { mutableStateOf(contact.firstName) }
    var middleName by remember { mutableStateOf(contact.middleName) }
    var lastName by remember { mutableStateOf(contact.lastName) }
    var nameSuffix by remember { mutableStateOf(contact.nameSuffix) }
    var company by remember { mutableStateOf(contact.companyName) }
    var photo by remember { mutableStateOf(contact.photo) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            photo = Base64.encode(byteArrayOutputStream.toByteArray())
        }
    }

    val phoneNumbers = remember { mutableStateListOf<PhoneNumber>() }
    val emails = remember { mutableStateListOf<Email>() }
    val dates = remember { mutableStateListOf<Event>() }
    val addresses = remember { mutableStateListOf<Address>() }

    LaunchedEffect(Unit) {
        val details = contact.getDetails(context)
        phoneNumbers.addAll(details.phoneNumbers)
        emails.addAll(details.emails)
        dates.addAll(details.dates)
        addresses.addAll(details.addresses)

        if (phoneNumbers.isEmpty()) {
            phoneNumbers.add(PhoneNumber(0, "", CDKPhone.TYPE_MOBILE))
        }
        if (emails.isEmpty()) {
            emails.add(Email(0, "", CDKEmail.TYPE_HOME))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit contact") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.outline_close_24), contentDescription = "Close")
                    }
                },
                actions = {
                    Button(onClick = {
                        val newContact = Contact(
                            contact.id,
                            contact.lookupKey,
                            namePrefix,
                            firstName,
                            middleName,
                            lastName,
                            nameSuffix,
                            company,
                            photo,
                            contact.isFavorite
                        )
                        val contactDetails = ContactDetails(
                            phoneNumbers,
                            emails,
                            addresses,
                            dates
                        )
                        newContact.save(context, contactDetails)
                        navController.navigateUp()
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            AddPictureSection(photo, {
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
                "Dates",
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
        Icon(
            painterResource(R.drawable.baseline_arrow_drop_down_24),
            contentDescription = null
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    detailType: String,
    details: SnapshotStateList<Event>,
    icon: Painter,
    options: List<Int>
) {
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
private inline fun <reified T: ContactDetail<T>> ColumnScope.DetailsSection(
    detailType: String,
    details: SnapshotStateList<T>,
    icon: Painter,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation,
    options: List<Int>) {
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

@OptIn(ExperimentalEncodingApi::class)
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
