package com.toolnagy.ringtonemanager.ui.detail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolnagy.ringtonemanager.data.model.Contact
import com.toolnagy.ringtonemanager.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _ringtoneSetResult = MutableStateFlow<RingtoneSetResult?>(null)
    val ringtoneSetResult: StateFlow<RingtoneSetResult?> = _ringtoneSetResult.asStateFlow()

    init {
        loadContact()
    }

    private fun loadContact() {
        viewModelScope.launch {
            val contacts = contactRepository.getContacts()
            _contact.value = contacts.firstOrNull { it.id == contactId }
        }
    }

    fun setRingtone(uri: Uri) {
        viewModelScope.launch {
            val success = contactRepository.setCustomRingtone(contactId, uri)
            _ringtoneSetResult.value = if (success) RingtoneSetResult.SUCCESS else RingtoneSetResult.ERROR
            if (success) loadContact()
        }
    }

    fun clearResult() {
        _ringtoneSetResult.value = null
    }
}

enum class RingtoneSetResult { SUCCESS, ERROR }
