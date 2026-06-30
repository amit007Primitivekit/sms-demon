package com.smsdemon.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single SMS-send attempt stored in the local Room database.
 *
 * @param id          Auto-generated primary key.
 * @param phoneNumber Destination number.
 * @param message     Resolved message (placeholders already substituted).
 * @param randomValue The {random} value used for this message.
 * @param counter     The {counter} value used for this message.
 * @param timestamp   Unix epoch millis when the attempt occurred.
 * @param success     Whether SmsManager.sendTextMessage() completed without exception.
 * @param errorMsg    Optional error description if success == false.
 */
@Entity(tableName = "sms_log")
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val message: String,
    val randomValue: String,
    val counter: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val errorMsg: String? = null
)
