package com.vayunmathur.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

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

    fun saveContact(contact: Contact, details: ContactDetails) {
        viewModelScope.launch(Dispatchers.IO) {
            contact.save(getApplication(), details)
            loadContacts()
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