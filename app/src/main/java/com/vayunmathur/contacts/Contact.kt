package com.vayunmathur.contacts

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import android.provider.ContactsContract


@Serializable
data class ContactDetails(
    val phoneNumbers: List<PhoneNumber>,
    val emails: List<Email>,
    val addresses: List<Address>,
    val dates: List<Event>,
    val photos: List<Photo>
) {
    fun all(): List<ContactDetail<*>> {
        return phoneNumbers + emails + addresses + dates + photos
    }
}

typealias CDKEmail = ContactsContract.CommonDataKinds.Email
typealias CDKPhone = ContactsContract.CommonDataKinds.Phone
typealias CDKStructuredPostal = ContactsContract.CommonDataKinds.StructuredPostal
typealias CDKEvent = ContactsContract.CommonDataKinds.Event
typealias CDKPhoto = ContactsContract.CommonDataKinds.Photo

interface ContactDetail<T: ContactDetail<T>> {
    val id: Long
    val type: Int
    val value: String
    fun withType(type: Int): T
    fun withValue(value: String): T
    fun typeString(context: Context): String

    companion object {
        @OptIn(ExperimentalTime::class)
        inline fun <reified T: ContactDetail<T>> default(): T {
            return when (T::class) {
                PhoneNumber::class -> PhoneNumber(0, "", CDKPhone.TYPE_MOBILE)
                Email::class -> Email(0, "", CDKEmail.TYPE_HOME)
                Address::class -> Address(0, "", CDKStructuredPostal.TYPE_HOME)
                Event::class -> Event(0, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date, CDKEvent.TYPE_BIRTHDAY)
                else -> throw IllegalArgumentException("Unknown type")
            } as T
        }
    }
}

@Serializable
data class PhoneNumber(override val id: Long, val number: String, override val type: Int): ContactDetail<PhoneNumber> {
    override val value: String
        get() = number

    override fun withType(type: Int) = PhoneNumber(id, number, type)
    override fun withValue(value: String) = PhoneNumber(id, value, type)

    override fun typeString(context: Context) = CDKPhone.getTypeLabel(context.resources, type, "").toString()
}

@Serializable
data class Email(override val id: Long, val address: String, override val type: Int): ContactDetail<Email> {
    override val value: String
        get() = address

    override fun withType(type: Int) = Email(id, address, type)
    override fun withValue(value: String) = Email(id, value, type)

    override fun typeString(context: Context) = CDKEmail.getTypeLabel(context.resources, type, "").toString()
}

@Serializable
data class Address(override val id: Long, val formattedAddress: String, override val type: Int): ContactDetail<Address> {
    override val value: String
        get() = formattedAddress

    override fun withType(type: Int) = Address(id, formattedAddress, type)
    override fun withValue(value: String) = Address(id, value, type)

    override fun typeString(context: Context) = CDKStructuredPostal.getTypeLabel(context.resources, type, "").toString()
}

@Serializable
data class Photo(override val id: Long, val photo: String, override val type: Int): ContactDetail<Address> {
    override val value: String
        get() = photo

    override fun withType(type: Int) = Photo(id, photo, type)
    override fun withValue(value: String) = Photo(id, value, type)

    override fun typeString(context: Context) = ""
}

@Serializable
data class Event(override val id: Long, val startDate: LocalDate, override val type: Int): ContactDetail<Event> {
    override val value: String
        get() = startDate.format(LocalDate.Formats.ISO)

    override fun withType(type: Int) = Event(id, startDate, type)
    override fun withValue(value: String) = Event(id, LocalDate.parse(value), type)

    override fun typeString(context: Context) = CDKEvent.getTypeLabel(context.resources, type, "").toString()
}

@Serializable
data class Contact(
    val id: Long,
    val lookupKey: String,
    val namePrefix: String,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val nameSuffix: String,
    val companyName: String,
    val isFavorite: Boolean,
    val details: ContactDetails
) {
    val name: String
        get() = listOfNotNull(namePrefix.ifEmpty { null }, firstName, middleName.ifEmpty { null }, lastName, nameSuffix.ifEmpty { null }).joinToString(" ")

    val photo: Photo?
        get() = details.photos.firstOrNull()

    fun save(context: Context, newDetails: ContactDetails, oldDetails: ContactDetails) {
        if (id == 0L) {
            insert(context, newDetails)
        } else {
            update(context, newDetails, oldDetails)
        }
    }

    private fun getRawContactId(context: Context): String? {
        var rawContactId: String? = null
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            ContactsContract.RawContacts.CONTACT_ID + "=?",
            arrayOf(id.toString()),
            null
        )

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                // We just take the first RawContact found.
                // In complex cases (e.g., Google vs WhatsApp contacts), you might want to filter by account type.
                val idIndex = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                if (idIndex != -1) {
                    rawContactId = cursor.getString(idIndex)
                }
            }
            cursor.close()
        }
        return rawContactId
    }

    private fun insertOrUpdate(isContact: Boolean = false): ContentProviderOperation.Builder {
        return if(id == 0L) {
            if(isContact) {
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            } else {
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            }
        } else {
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(id.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
        }
    }

    private fun insert(context: Context, details: ContactDetails) {
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(insertOrUpdate(true)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build())

        // Name
        ops.add(insertOrUpdate()
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, namePrefix.ifEmpty { null })
            .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName.ifEmpty { null })
            .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middleName.ifEmpty { null })
            .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.ifEmpty { null })
            .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, nameSuffix.ifEmpty { null })
            .build())

        // Company
        ops.add(insertOrUpdate()
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, companyName)
            .build())

        details.all().forEach {
            ops.add(createInsertOperation(it))
        }

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun update(context: Context, newDetails: ContactDetails, oldDetails: ContactDetails) {
        val ops = ArrayList<ContentProviderOperation>()

        // Name
        ops.add(insertOrUpdate()
                .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, namePrefix.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, middleName.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName.ifEmpty { null })
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, nameSuffix.ifEmpty { null })
                .build()
        )

        // Company
        ops.add(insertOrUpdate()
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, companyName)
                .build()
        )

        // details
        val rawContactID = getRawContactId(context)
        handleDetailUpdates(ops, oldDetails.all(), newDetails.all(), rawContactID)

        // Favorite
        ops.add(insertOrUpdate()
                .withValue(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
                .build()
        )

        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun handleDetailUpdates(ops: MutableList<ContentProviderOperation>, currentDetails: List<ContactDetail<*>>, newDetails: List<ContactDetail<*>>, rawContactID: String?) {
        val currentIds = currentDetails.map { it.id }.toSet()
        val newIds = newDetails.map { it.id }.toSet()

        val idsToDelete = currentIds - newIds
        idsToDelete.forEach { id ->
            ops.add(createDeleteOperation(id))
        }

        newDetails.forEach { detail ->
            if (detail.id == 0L) { // New item
                ops.add(createInsertOperation(detail, rawContactID))
            } else { // Existing item, check if it has changed
                val oldDetail = currentDetails.find { it.id == detail.id }
                if (oldDetail != null && oldDetail != detail) {
                    ops.add(createUpdateOperation(detail))
                }
            }
        }
    }

    private fun createInsertOperation(detail: ContactDetail<*>, rawContactId: String? = null): ContentProviderOperation {
        val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
        if (rawContactId != null) {
            builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
        } else {
            builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        }

        return builder.completeOperation(detail)
    }

    private fun createUpdateOperation(detail: ContactDetail<*>): ContentProviderOperation {
        return ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(detail.id.toString()))
            .completeOperation(detail)
    }

    private fun createDeleteOperation(dataId: Long): ContentProviderOperation {
        return ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(dataId.toString()))
            .build()
    }

    fun ContentProviderOperation.Builder.completeOperation(detail: ContactDetail<*>): ContentProviderOperation {
        return when (detail) {
            is PhoneNumber -> this
                .withValue(ContactsContract.Data.MIMETYPE, CDKPhone.CONTENT_ITEM_TYPE)
                .withValue(CDKPhone.NUMBER, detail.number)
                .withValue(CDKPhone.TYPE, detail.type)
                .build()
            is Email -> this
                .withValue(ContactsContract.Data.MIMETYPE, CDKEmail.CONTENT_ITEM_TYPE)
                .withValue(CDKEmail.ADDRESS, detail.address)
                .withValue(CDKEmail.TYPE, detail.type)
                .build()
            is Address -> this
                .withValue(ContactsContract.Data.MIMETYPE, CDKStructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(CDKStructuredPostal.FORMATTED_ADDRESS, detail.formattedAddress)
                .withValue(CDKStructuredPostal.TYPE, detail.type)
                .build()
            is Event -> this
                .withValue(ContactsContract.Data.MIMETYPE, CDKEvent.CONTENT_ITEM_TYPE)
                .withValue(CDKEvent.START_DATE, detail.startDate.format(LocalDate.Formats.ISO))
                .withValue(CDKEvent.TYPE, detail.type)
                .build()
            is Photo -> this
                .withValue(ContactsContract.Data.MIMETYPE, CDKPhoto.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                .withValue(CDKPhoto.PHOTO, Base64.decode(detail.photo))
                .build()

            else -> throw IllegalArgumentException("Unknown detail type")
        }
    }

    companion object {

        private fun getContacts(context: Context, contactId: Long?): List<Contact> {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED,
            )
            val cursor = contentResolver.query(uri, projection, if(contactId == null) null else "${ContactsContract.Contacts._ID} = ?", arrayOf(contactId.toString()), null)

            val contacts = mutableListOf<Contact>()

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                    val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    val isFavorite = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
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

                    contacts += Contact(
                        id = id,
                        lookupKey = lookupKey,
                        namePrefix = namePrefix,
                        firstName = firstName,
                        middleName = middleName,
                        lastName = lastName,
                        nameSuffix = nameSuffix,
                        companyName = companyName,
                        isFavorite = isFavorite,
                        getDetails(context, id)
                    )
                }
            }
            return contacts
        }

        fun setFavorite(context: Context, contactId: Long, isFavorite: Boolean) {
            context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
                },
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            )
        }

        fun getContact(context: Context, contactId: Long): Contact? {
            return getContacts(context, contactId).firstOrNull()
        }

        fun getAllContacts(context: Context): List<Contact> {
            return getContacts(context, null)
        }

        fun delete(context: Context, contact: Contact) {
            context.contentResolver.delete(
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contact.lookupKey),
                null, null
            )
        }
    }
}

fun getDetails(context: Context, id: Long): ContactDetails {
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
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID))
            val number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE))
            phoneNumbers.add(PhoneNumber(id, number, type))
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
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email._ID))
            val email = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE))
            emails.add(Email(id, email, type))
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
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal._ID))
            val address = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.TYPE))
            addresses.add(Address(id, address, type))
        }
    }

    // Dates
    val dates = mutableListOf<Event>()
    contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Event._ID,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.CommonDataKinds.Event.TYPE
        ),
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(contactId, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event._ID))
            val date = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE))
            val localDate = if(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                LocalDate.parse(date, LocalDate.Formats.ISO)
            } else if(date.matches(Regex("\\d{8}"))) {
                LocalDate.parse(date, LocalDate.Format { year(); monthNumber(); day() })
            } else {
                continue
            }
            dates.add(Event(id,localDate, type))
        }
    }

    // Dates
    val photos = mutableListOf<Photo>()
    contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(
            CDKPhoto._ID,
            CDKPhoto.PHOTO,
        ),
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(contactId, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(CDKPhoto._ID))
            val photo = cursor.getString(cursor.getColumnIndexOrThrow(CDKPhoto.PHOTO))
            photos.add(Photo(id,photo, 0))
        }
    }

    return ContactDetails(phoneNumbers.distinct(), emails.distinct(), addresses.distinct(), dates.distinct(), photos.distinct())
}
