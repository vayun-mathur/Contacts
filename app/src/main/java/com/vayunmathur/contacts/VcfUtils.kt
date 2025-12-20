package com.vayunmathur.contacts

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object VcfUtils {
    suspend fun exportContacts(context: Context, contacts: List<Contact>, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            outputStream.bufferedWriter().use { writer ->
                for (contact in contacts) {
                    val details = contact.getDetails(context)
                    writer.write("BEGIN:VCARD\n")
                    writer.write("VERSION:3.0\n")
                    // N:LastName;FirstName;MiddleName;Prefix;Suffix
                    writer.write("N:${contact.lastName};${contact.firstName};${contact.middleName};${contact.namePrefix};${contact.nameSuffix}\n")
                    writer.write("FN:${contact.name}\n")
                    if (contact.companyName.isNotEmpty()) {
                        writer.write("ORG:${contact.companyName}\n")
                    }
                    
                    for (phone in details.phoneNumbers) {
                        val type = when(phone.type) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "HOME"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "WORK"
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "CELL"
                            else -> "VOICE"
                        }
                        writer.write("TEL;TYPE=$type:${phone.number}\n")
                    }
                    
                    for (email in details.emails) {
                        val type = when(email.type) {
                            ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "HOME"
                            ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "WORK"
                            else -> "INTERNET"
                        }
                        writer.write("EMAIL;TYPE=$type:${email.address}\n")
                    }
                    
                    for (addr in details.addresses) {
                        // ADR is complex, we only have formatted address.
                        // We put it in the street address field (index 2) as a fallback.
                        // ADR:;;Street Address;City;State;Zip;Country
                        // Since we only have formattedAddress, we can try to put it there.
                        writer.write("ADR;TYPE=HOME:;;${addr.formattedAddress};;;;\n")
                    }
                    
                    writer.write("END:VCARD\n")
                }
            }
        }
    }

    suspend fun importContacts(context: Context, inputStream: InputStream) {
         withContext(Dispatchers.IO) {
            val contactsToSave = mutableListOf<Pair<Contact, ContactDetails>>()
            val reader = inputStream.bufferedReader()
            var currentContact: ContactBuilder? = null
            
            reader.forEachLine { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("BEGIN:VCARD")) {
                    currentContact = ContactBuilder()
                } else if (trimmedLine.startsWith("END:VCARD")) {
                    currentContact?.let { builder ->
                        val newContact = Contact(
                            id = 0,
                            lookupKey = "",
                            namePrefix = builder.namePrefix,
                            firstName = builder.firstName,
                            middleName = builder.middleName,
                            lastName = builder.lastName,
                            nameSuffix = builder.nameSuffix,
                            companyName = builder.companyName,
                            photo = null,
                            isFavorite = false
                        )
                        val newDetails = ContactDetails(
                            phoneNumbers = builder.phoneNumbers,
                            emails = builder.emails,
                            addresses = builder.addresses,
                            dates = emptyList()
                        )
                        contactsToSave.add(newContact to newDetails)
                    }
                    currentContact = null
                } else if (currentContact != null) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val keyParts = parts[0].split(";")
                        val key = keyParts[0]
                        val value = parts[1]
                        
                        when (key) {
                            "N" -> {
                                val nParts = value.split(";")
                                currentContact?.lastName = nParts.getOrElse(0) { "" }
                                currentContact?.firstName = nParts.getOrElse(1) { "" }
                                currentContact?.middleName = nParts.getOrElse(2) { "" }
                                currentContact?.namePrefix = nParts.getOrElse(3) { "" }
                                currentContact?.nameSuffix = nParts.getOrElse(4) { "" }
                            }
                            "FN" -> {
                                // Fallback if N is empty?
                                if (currentContact?.firstName.isNullOrEmpty() && currentContact?.lastName.isNullOrEmpty()) {
                                    val names = value.split(" ")
                                    if (names.isNotEmpty()) currentContact?.firstName = names[0]
                                    if (names.size > 1) currentContact?.lastName = names.drop(1).joinToString(" ")
                                }
                            }
                            "ORG" -> currentContact?.companyName = value
                            "TEL" -> {
                                var type = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                                if (keyParts.any { it.contains("HOME") }) type = ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                                if (keyParts.any { it.contains("WORK") }) type = ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                                currentContact?.phoneNumbers?.add(PhoneNumber(0, value, type))
                            }
                            "EMAIL" -> {
                                var type = ContactsContract.CommonDataKinds.Email.TYPE_HOME
                                if (keyParts.any { it.contains("WORK") }) type = ContactsContract.CommonDataKinds.Email.TYPE_WORK
                                currentContact?.emails?.add(Email(0, value, type))
                            }
                            "ADR" -> {
                                val adrParts = value.split(";")
                                val street = adrParts.getOrElse(2) { "" }
                                val city = adrParts.getOrElse(3) { "" }
                                val state = adrParts.getOrElse(4) { "" }
                                val zip = adrParts.getOrElse(5) { "" }
                                val country = adrParts.getOrElse(6) { "" }
                                val fullAddr = listOf(street, city, state, zip, country).filter { it.isNotEmpty() }.joinToString(", ")
                                currentContact?.addresses?.add(Address(0, fullAddr, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME))
                            }
                        }
                    }
                }
            }
            
            for ((contact, details) in contactsToSave) {
                contact.save(context, details, null)
            }
         }
    }
    
    private class ContactBuilder {
        var namePrefix = ""
        var firstName = ""
        var middleName = ""
        var lastName = ""
        var nameSuffix = ""
        var companyName = ""
        val phoneNumbers = mutableListOf<PhoneNumber>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
    }
}
