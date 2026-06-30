package com.smsdemon.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.smsdemon.repository.SmsRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for [LogActivity].
 *
 * Converts the [SmsRepository.allLogs] Flow into LiveData so the RecyclerView
 * adapter can observe it from the Activity without lifecycle concerns.
 */
class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)

    /**
     * All [com.smsdemon.model.SmsLog] entries, newest first.
     * Automatically updates when the database changes.
     */
    val logs = repository.allLogs.asLiveData()

    /**
     * Clears all log entries from the database.
     * Runs on the background dispatcher inside the repository.
     */
    fun clearLogs() {
        viewModelScope.launch { repository.clearLogs() }
    }
}
