package com.toolnagy.ringtonemanager.data.model

import android.net.Uri

data class Contact(
    val id: String,
    val name: String,
    val photoUri: Uri?,
    val phoneNumbers: List<String>,
    val customRingtoneUri: Uri?
)
