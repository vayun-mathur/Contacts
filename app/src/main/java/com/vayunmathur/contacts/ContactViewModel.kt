package com.vayunmathur.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _currentContactDetails = MutableStateFlow<ContactDetails?>(null)
    val currentContactDetails: StateFlow<ContactDetails?> = _currentContactDetails.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _contacts.value = Contact.getAllContacts(getApplication())
        }
    }

    fun getContact(contactId: Long): Contact? {
        return contacts.value.find { it.id == contactId }
    }

    fun getContactFlow(contactId: Long): Flow<Contact?> {
        return contacts.map { contacts -> contacts.find { it.id == contactId } }
    }

    fun loadContactDetails(contactId: Long) {
        viewModelScope.launch {
            val contact = Contact.getContact(getApplication(), contactId)
            if (contact != null) {
                _currentContactDetails.value = withContext(Dispatchers.IO) {
                    contact.getDetails(getApplication())
                }
                _contacts.value = contacts.value.map { if (it.id == contactId) contact else it }
            } else {
                _currentContactDetails.value = null
            }
        }
    }

    fun saveContact(contact: Contact, details: ContactDetails) {
        viewModelScope.launch(Dispatchers.IO) {
            val contactId = contact.id
            contact.save(getApplication(), details, currentContactDetails.value)

            if (contactId == 0L) {
                loadContacts()
            } else {
                val updatedContact = Contact.getContact(getApplication(), contactId)
                withContext(Dispatchers.Main) {
                    if (updatedContact != null) {
                        val index = _contacts.value.indexOfFirst { it.id == updatedContact.id }
                        if (index != -1) {
                            val newList = _contacts.value.toMutableList()
                            newList[index] = updatedContact
                            _contacts.value = newList
                        }
                    }
                }
                loadContactDetails(contactId)
            }
        }
    }

    fun deleteContacts(contactIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            contactIds.forEach { id ->
                getContact(id)?.let { contact ->
                    Contact.delete(getApplication(), contact)
                }
            }
            loadContacts()
        }
    }
}