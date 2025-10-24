package com.vayunmathur.contacts

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


data class ContactDetails(
    val phoneNumbers: List<PhoneNumber>,
    val emails: List<Email>,
    val addresses: List<Address>,
    val dates: List<Event>
)

typealias CDKEmail = ContactsContract.CommonDataKinds.Email
typealias CDKPhone = ContactsContract.CommonDataKinds.Phone
typealias CDKStructuredPostal = ContactsContract.CommonDataKinds.StructuredPostal
typealias CDKEvent = ContactsContract.CommonDataKinds.Event

interface ContactDetail<T: ContactDetail<T>> {
    val type: Int
    val value: String
    fun withType(type: Int): T
    fun withValue(value: String): T
    fun typeString(context: Context): String

    companion object {
        @OptIn(ExperimentalTime::class)
        inline fun <reified T: ContactDetail<T>> default(): T {
            return when (T::class) {
                PhoneNumber::class -> PhoneNumber("", CDKPhone.TYPE_MOBILE)
                Email::class -> Email("", CDKEmail.TYPE_HOME)
                Address::class -> Address("", CDKStructuredPostal.TYPE_HOME)
                Event::class -> Event(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date, CDKEvent.TYPE_BIRTHDAY)
                else -> throw IllegalArgumentException("Unknown type")
            } as T
        }
    }
}

data class PhoneNumber(val number: String, override val type: Int): ContactDetail<PhoneNumber> {
    override val value: String
        get() = number

    override fun withType(type: Int) = PhoneNumber(number, type)
    override fun withValue(value: String) = PhoneNumber(value, type)

    override fun typeString(context: Context) = CDKPhone.getTypeLabel(context.resources, type, "").toString()
}
data class Email(val address: String, override val type: Int): ContactDetail<Email> {
    override val value: String
        get() = address

    override fun withType(type: Int) = Email(address, type)
    override fun withValue(value: String) = Email(value, type)

    override fun typeString(context: Context) = CDKEmail.getTypeLabel(context.resources, type, "").toString()
}
data class Address(val formattedAddress: String, override val type: Int): ContactDetail<Address> {
    override val value: String
        get() = formattedAddress

    override fun withType(type: Int) = Address(formattedAddress, type)
    override fun withValue(value: String) = Address(value, type)

    override fun typeString(context: Context) = CDKStructuredPostal.getTypeLabel(context.resources, type, "").toString()
}


data class Event(val startDate: LocalDate, override val type: Int): ContactDetail<Event> {
    override val value: String
        get() = startDate.format(LocalDate.Formats.ISO)

    override fun withType(type: Int) = Event(startDate, type)
    override fun withValue(value: String) = Event(LocalDate.parse(value), type)

    override fun typeString(context: Context) = CDKEvent.getTypeLabel(context.resources, type, "").toString()
}

private fun getPhotoBytes(context: Context, photoUri: String?): ByteArray? {
    if (photoUri.isNullOrEmpty()) {
        return null
    }
    return try {
        val uri = photoUri.toUri()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            // Read stream into byte array
            val buffer = ByteArray(8192)
            var bytesRead: Int
            val output = ByteArrayOutputStream()
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.toByteArray()
        }
    } catch (e: Exception) {
        // Log the error or handle it appropriately
        println("Error reading photo bytes from URI: $photoUri, Error: ${e.message}")
        null
    }
}

@Serializable
data class Contact(
    val id: Long,
    val namePrefix: String,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val nameSuffix: String,
    val companyName: String,
    val photo: String?,
    val isFavorite: Boolean
) {
    val name: String
        get() = listOfNotNull(namePrefix.ifEmpty { null }, firstName, middleName.ifEmpty { null }, lastName, nameSuffix.ifEmpty { null }).joinToString(" ")

    fun save(context: Context, details: ContactDetails) {
        val ops = ArrayList<ContentProviderOperation>()

        // Name
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(id.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, namePrefix.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middleName.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, nameSuffix.ifEmpty { null })
                .build()
        )

        // Company
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(id.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, companyName)
                .build()
        )

        if(photo != null) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, id)
                    .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, Base64.decode(photo))
                    .build()
            )
        } else {
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(id.toString(), ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    )
                    .build()
            )
        }

        // Phone numbers
        details.phoneNumbers.forEach { phoneNumber ->
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Phone.TYPE} = ?",
                        arrayOf(id.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phoneNumber.type.toString())
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber.number)
                    .build()
            )
        }

        // Emails
        details.emails.forEach { email ->
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Email.TYPE} = ?",
                        arrayOf(id.toString(), ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, email.type.toString())
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                    .build()
            )
        }

        // Addresses
        details.addresses.forEach { address ->
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.StructuredPostal.TYPE} = ?",
                        arrayOf(id.toString(), ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, address.type.toString())
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.formattedAddress)
                    .build()
            )
        }

        // Dates
        details.dates.forEach { event ->
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                        arrayOf(id.toString(), ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, event.type.toString())
                    )
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, event.startDate.format(LocalDate.Formats.ISO))
                    .build()
            )
        }

        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Contacts.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Contacts._ID} = ?",
                    arrayOf(id.toString())
                )
                .withValue(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
                .build()
        )

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    companion object {
        fun setFavorite(context: Context, contactId: Long, isFavorite: Boolean) {
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
            }
            contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            )
        }
        fun getAllContacts(context: Context): List<Contact> {
            val contacts = mutableListOf<Contact>()
            val contentResolver = context.contentResolver
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.STARRED
            )
            val cursor = contentResolver.query(uri, projection, null, null, null)

            cursor?.use {
                while (it.moveToNext()) {
                    val contactId = it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    val photoThumbnailUriString = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))
                    val isFavorite = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
                    var photo: String? = null
                    var namePrefix = ""
                    var firstName = ""
                    var middleName = ""
                    var lastName = ""
                    var nameSuffix = ""
                    var companyName = ""

                    val nameCursor = contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                            ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                            ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                            ContactsContract.CommonDataKinds.StructuredName.SUFFIX
                        ),
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                        null
                    )

                    nameCursor?.use { nc ->
                        if (nc.moveToFirst()) {
                            namePrefix = nc.getString(nc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.PREFIX)) ?: ""
                            firstName = nc.getString(nc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)) ?: ""
                            middleName = nc.getString(nc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME)) ?: ""
                            lastName = nc.getString(nc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)) ?: ""
                            nameSuffix = nc.getString(nc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.SUFFIX)) ?: ""
                        }
                    }

                    if ((firstName.isEmpty() && lastName.isEmpty()) && displayName != null) {
                        val parts = displayName.split(" ", limit = 2)
                        firstName = parts.getOrNull(0) ?: ""
                        lastName = parts.getOrNull(1) ?: ""
                    }

                    val organizationCursor = contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                        null
                    )
                    organizationCursor?.use { oc ->
                        if (oc.moveToFirst()) {
                            companyName = oc.getString(oc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY))
                        }
                    }

                    if (photoThumbnailUriString != null) {
                        val photoUri = photoThumbnailUriString.toUri()
                        var inputStream: InputStream? = null
                        try {
                            inputStream = contentResolver.openInputStream(photoUri)
                            val photoBmp = BitmapFactory.decodeStream(inputStream)

                            val byteArrayOutputStream = ByteArrayOutputStream()
                            photoBmp.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
                            photo = Base64.encode(byteArrayOutputStream.toByteArray())


                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            inputStream?.close()
                        }
                    }

                    contacts.add(
                        Contact(
                            id = contactId,
                            namePrefix = namePrefix,
                            firstName = firstName,
                            middleName = middleName,
                            lastName = lastName,
                            nameSuffix = nameSuffix,
                            companyName = companyName,
                            photo = photo,
                            isFavorite = isFavorite
                        )
                    )
                }
            }
            return contacts
        }
    }
}

fun Contact.getDetails(context: Context): ContactDetails {
    val contentResolver = context.contentResolver
    val contactId = id.toString()

    // Phone Numbers
    val phoneNumbers = mutableListOf<PhoneNumber>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null,
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(contactId),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
            phoneNumbers.add(PhoneNumber(number, type))
        }
    }

    // Emails
    val emails = mutableListOf<Email>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        null,
        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
        arrayOf(contactId),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val email = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE))
            emails.add(Email(email, type))
        }
    }

    // Addresses
    val addresses = mutableListOf<Address>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
        null,
        "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
        arrayOf(contactId),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val address = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.TYPE))
            addresses.add(Address(address, type))
        }
    }

    // Dates
    val dates = mutableListOf<Event>()
    contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.CommonDataKinds.Event.TYPE
        ),
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(contactId, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val date = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE))
            dates.add(Event(LocalDate.parse(date), type))
        }
    }

    return ContactDetails(phoneNumbers.distinct(), emails.distinct(), addresses.distinct(), dates.distinct())
}