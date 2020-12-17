package com.safetica.datasafe.manager

import android.app.Application
import android.content.Context
import com.safetica.imagesafe.manager.base.PreferenceManagerBase
import javax.inject.Inject
import javax.inject.Singleton

/**
 *  Provides properties for access to key-value pair storage
 */
@Singleton
class PreferenceManager @Inject constructor(context: Application): PreferenceManagerBase(context) {

    companion object {
        private const val LAST_SCAN_KEY = "LAST_SCAN"
        private const val TIMER_SECONDS_KEY = "TIMER_SECONDS"
        private const val PASSWORD_TRIES_KEY = "PASSWORD_TRIES"
    }

    var lastScanned: Long
        get() = getLong(LAST_SCAN_KEY, 0)
        set(value) = setLong(LAST_SCAN_KEY, value)

    var loginTimerSecondsRemain: Long
        get() = getLong(TIMER_SECONDS_KEY, 0)
        set(value) = setLong(TIMER_SECONDS_KEY, value)

    var loginPasswordTriesRemain: Int
        get() = getInt(PASSWORD_TRIES_KEY, 3)
        set(value) = setInt(PASSWORD_TRIES_KEY, value)
}