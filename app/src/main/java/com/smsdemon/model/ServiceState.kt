package com.smsdemon.model

/**
 * Simple sealed hierarchy representing the runtime state of [SmsSenderService].
 * Observed by the UI layer via LiveData so the buttons/status label stay in sync.
 */
sealed class ServiceState {
    /** Service is not running; no SMS will be sent. */
    object Stopped : ServiceState()

    /**
     * Service is active and will send SMS on the configured interval.
     *
     * @param intervalMinutes Interval currently in use.
     */
    data class Running(val intervalMinutes: Int) : ServiceState()
}
