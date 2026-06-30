package com.smsdemon.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smsdemon.model.SmsLog

/**
 * Single Room database instance for the entire application.
 *
 * Uses the classic double-checked-locking singleton pattern.
 * Access via [AppDatabase.getInstance].
 */
@Database(
    entities = [SmsLog::class],
    version  = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton [AppDatabase], creating it if necessary.
         *
         * @param context Any context; the application context is retrieved internally
         *                to avoid leaking Activity/Service contexts.
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_demon.db"
                )
                    .fallbackToDestructiveMigration()   // OK for a log database
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
