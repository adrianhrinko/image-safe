package com.safetica.datasafe.activity

import android.os.Bundle


/**
 * Implements onLoggedIn() method that shows CryptoFragment in encryption mode
 */
class EncryptionActivity : BaseActivity() {
    override fun onLoggedIn(savedInstanceState: Bundle?) {
        if (!loggedIn) {
            showFragment(encryptionFragment, false)
        }
    }
}
