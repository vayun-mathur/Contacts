package com.vayunmathur.contacts

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@Serializable
data class ContactDetails(
    val phoneNumbers: List<PhoneNumber>,
    val emails: List<Email>,
    val addresses: List<Address>,
    val dates: List<Event>,
    val photos: List<Photo>,
    val names: List<Name>
) {
    fun all(): List<ContactDetail<*>> {
        return phoneNumbers + emails + addresses + dates + photos + names
    }
}

typealias CDKEmail = ContactsContract.CommonDataKinds.Email
typealias CDKPhone = ContactsContract.CommonDataKinds.Phone
typealias CDKStructuredPostal = ContactsContract.CommonDataKinds.StructuredPostal
typealias CDKEvent = ContactsContract.CommonDataKinds.Event
typealias CDKPhoto = ContactsContract.CommonDataKinds.Photo
typealias CDKSName = ContactsContract.CommonDataKinds.StructuredName

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
data class Photo(override val id: Long, val photo: String): ContactDetail<Photo> {
    override val type: Int = 0
    override val value: String
        get() = photo

    override fun withType(type: Int) = throw UnsupportedOperationException("Cannot change type of photo")
    override fun withValue(value: String) = Photo(id, value)

    override fun typeString(context: Context) = throw UnsupportedOperationException("Photo doesn't have type")
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
data class Name(
    override val id: Long,
    val namePrefix: String,
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val nameSuffix: String
): ContactDetail<Name> {
    override val type: Int = 0
    override val value: String
        get() = listOfNotNull(namePrefix.ifEmpty { null }, firstName.ifEmpty { null }, middleName.ifEmpty { null }, lastName.ifEmpty { null }, nameSuffix.ifEmpty { null }).joinToString(" ")

    override fun withType(type: Int) = throw UnsupportedOperationException("Cannot change type of name")
    override fun withValue(value: String) = throw UnsupportedOperationException("Cannot change value of name")

    override fun typeString(context: Context) = throw UnsupportedOperationException("Name doesn't have type")
}

@Serializable
data class Contact(
    val id: Long,
    val lookupKey: String,
    val companyName: String,
    val isFavorite: Boolean,
    val details: ContactDetails
) {
    val name: Name
        get() = details.names.first()

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
            is Name -> this
                .withValue(ContactsContract.Data.MIMETYPE, CDKSName.CONTENT_ITEM_TYPE)
                .withValue(CDKSName.PREFIX, detail.namePrefix)
                .withValue(CDKSName.GIVEN_NAME, detail.firstName)
                .withValue(CDKSName.MIDDLE_NAME, detail.middleName)
                .withValue(CDKSName.FAMILY_NAME, detail.lastName)
                .withValue(CDKSName.SUFFIX, detail.nameSuffix)
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
            val cursor = contentResolver.query(uri, projection, if(contactId == null) null else "${ContactsContract.Contacts._ID} = ?", listOfNotNull(contactId?.toString()).toTypedArray(), null)

            val contacts = mutableListOf<Contact>()

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                    val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    val isFavorite = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
                    var companyName = ""

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

                    var details = getDetails(context, id)
                    if((details.names.isEmpty() || (details.names.first().firstName.isEmpty() && details.names.first().lastName.isEmpty())) && displayName != null) {
                        val firstName = displayName.split(" ").first()
                        val lastName = displayName.split(" ").last()
                        if(firstName.isEmpty() && lastName.isEmpty()) continue
                        details = details.copy(names = listOf(Name(details.names.firstOrNull()?.id?:0, "", firstName, "", lastName, "")))
                    }

                    println(details.names.first())

                    contacts += Contact(
                        id = id,
                        lookupKey = lookupKey,
                        companyName = companyName,
                        isFavorite = isFavorite,
                        details
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

    fun <T: ContactDetail<T>> queryData(projection: List<String>, mimeType: String, datumFromCursor: (Cursor) -> T?): List<T> {
        val data = mutableListOf<T>()
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection.toTypedArray(),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, mimeType),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                datumFromCursor(cursor)?.let {
                    data.add(it)
                }
            }
        }
        return data;
    }

    val phoneNumbers = queryData(listOf(CDKPhone._ID, CDKPhone.NUMBER, CDKPhone.TYPE), CDKPhone.CONTENT_ITEM_TYPE) {
        val id = it.getLong(it.getColumnIndexOrThrow(CDKPhone._ID))
        val number = it.getString(it.getColumnIndexOrThrow(CDKPhone.NUMBER))
        val type = it.getInt(it.getColumnIndexOrThrow(CDKPhone.TYPE))
        PhoneNumber(id, number, type)
    }

    val emails = queryData(listOf(CDKEmail._ID, CDKEmail.ADDRESS, CDKEmail.TYPE), CDKEmail.CONTENT_ITEM_TYPE) {
        val id = it.getLong(it.getColumnIndexOrThrow(CDKEmail._ID))
        val email = it.getString(it.getColumnIndexOrThrow(CDKEmail.ADDRESS))
        val type = it.getInt(it.getColumnIndexOrThrow(CDKEmail.TYPE))
        Email(id, email, type)
    }

    val addresses = queryData(listOf(CDKStructuredPostal._ID, CDKStructuredPostal.FORMATTED_ADDRESS, CDKStructuredPostal.TYPE), CDKStructuredPostal.CONTENT_ITEM_TYPE) {
        val id = it.getLong(it.getColumnIndexOrThrow(CDKStructuredPostal._ID))
        val address = it.getString(it.getColumnIndexOrThrow(CDKStructuredPostal.FORMATTED_ADDRESS))
        val type = it.getInt(it.getColumnIndexOrThrow(CDKStructuredPostal.TYPE))
        Address(id, address, type)
    }

    // Dates
    val dates = queryData(listOf(CDKEvent._ID, CDKEvent.START_DATE, CDKEvent.TYPE), CDKEvent.CONTENT_ITEM_TYPE) { cursor ->
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event._ID))
        val date = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE))
        val type = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE))
        val localDate = if(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            LocalDate.parse(date, LocalDate.Formats.ISO)
        } else if(date.matches(Regex("\\d{8}"))) {
            LocalDate.parse(date, LocalDate.Format { year(); monthNumber(); day() })
        } else {
            return@queryData null
        }
        Event(id,localDate, type)
    }

    // Photos
    val photos = queryData(listOf(CDKPhoto._ID, CDKPhoto.PHOTO), CDKPhoto.CONTENT_ITEM_TYPE) { cursor ->
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(CDKPhoto._ID))
        val photo = Base64.encode(cursor.getBlob(cursor.getColumnIndexOrThrow(CDKPhoto.PHOTO)))
        Photo(id, photo)
    }

    val names = queryData(listOf(
        CDKSName._ID,
        CDKSName.PREFIX,
        CDKSName.GIVEN_NAME,
        CDKSName.MIDDLE_NAME,
        CDKSName.FAMILY_NAME,
        CDKSName.SUFFIX
    ), CDKSName.CONTENT_ITEM_TYPE) { cursor ->
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(CDKSName._ID))
        val namePrefix = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(CDKSName.PREFIX)) ?: ""
        val firstName = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(CDKSName.GIVEN_NAME)) ?: ""
        val middleName = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(CDKSName.MIDDLE_NAME)) ?: ""
        val lastName = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(CDKSName.FAMILY_NAME)) ?: ""
        val nameSuffix = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(CDKSName.SUFFIX)) ?: ""

        Name(id, namePrefix, firstName, middleName, lastName, nameSuffix)
    }

    return ContactDetails(phoneNumbers.distinct(), emails.distinct(), addresses.distinct(), dates.distinct(), photos.distinct(), names.distinct())
}
