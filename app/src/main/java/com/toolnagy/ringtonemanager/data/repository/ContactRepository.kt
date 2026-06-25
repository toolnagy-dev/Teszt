package com.toolnagy.ringtonemanager.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.toolnagy.ringtonemanager.data.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getContacts(query: String = ""): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.CUSTOM_RINGTONE,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        val selection = if (query.isNotBlank())
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?" else null
        val selectionArgs = if (query.isNotBlank()) arrayOf("%$query%") else null
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            val ringtoneCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.CUSTOM_RINGTONE)
            val hasPhoneCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                val photoUriStr = cursor.getString(photoCol)
                val ringtoneUriStr = cursor.getString(ringtoneCol)
                val hasPhone = cursor.getInt(hasPhoneCol) > 0

                val phones = if (hasPhone) getPhoneNumbers(id) else emptyList()

                contacts.add(
                    Contact(
                        id = id,
                        name = name,
                        photoUri = photoUriStr?.let { Uri.parse(it) },
                        phoneNumbers = phones,
                        customRingtoneUri = ringtoneUriStr?.let { Uri.parse(it) }
                    )
                )
            }
        }
        contacts
    }

    private fun getPhoneNumbers(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                cursor.getString(numberCol)?.let { phones.add(it) }
            }
        }
        return phones
    }

    suspend fun setCustomRingtone(contactId: String, ringtoneUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rawContactIds = getRawContactIds(contactId)
            if (rawContactIds.isEmpty()) return@withContext false

            rawContactIds.forEach { rawId ->
                val values = ContentValues().apply {
                    put(ContactsContract.RawContacts.CUSTOM_RINGTONE, ringtoneUri.toString())
                }
                context.contentResolver.update(
                    ContactsContract.RawContacts.CONTENT_URI,
                    values,
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawId)
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getRawContactIds(contactId: String): List<String> {
        val rawIds = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            while (cursor.moveToNext()) {
                rawIds.add(cursor.getString(idCol))
            }
        }
        return rawIds
    }
}
