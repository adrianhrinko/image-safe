package com.safetica.datasafe.activity

import android.os.Bundle

/**
 * Implements onLoggedIn() method that shows ImportFragment
 */
class ImportActivity : BaseActivity() {
    override fun onLoggedIn(savedInstanceState: Bundle?) {
        if (!loggedIn) {
            showFragment(importFragment, false)
        }
    }

}
