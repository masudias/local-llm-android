package ai.altri.jam

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String
)

data class SmsMessage(
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1 for received, 2 for sent
)

class ContactSmsHandler(private val context: Context) {
    fun hasRequiredPermissions(): Boolean {
        return hasContactPermission() && hasSmsPermission()
    }

    fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getContact(contactUri: Uri): Contact? {
        if (!hasContactPermission()) return null

        val contentResolver: ContentResolver = context.contentResolver
        var contact: Contact? = null

        contentResolver.query(
            contactUri,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactId =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val displayName =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))

                // Get phone number
                var phoneNumber = ""
                val hasPhone =
                    cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                if (hasPhone > 0) {
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            phoneNumber = phoneCursor.getString(
                                phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            )
                        }
                    }
                }

                contact = Contact(contactId, displayName, phoneNumber)
            }
        }

        return contact
    }

    fun getLastSmsMessages(phoneNumber: String, limit: Int = 20): List<SmsMessage> {
        if (!hasSmsPermission()) return emptyList()

        val messages = mutableListOf<SmsMessage>()
        val contentResolver: ContentResolver = context.contentResolver

        // Normalize phone number by removing spaces and special characters
        val normalizedPhoneNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

        val selection = "${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%$normalizedPhoneNumber%")
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val smsMessage = SmsMessage(
                    address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                    body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                    date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                )
                messages.add(smsMessage)
            }
        }

        return messages
    }
}
