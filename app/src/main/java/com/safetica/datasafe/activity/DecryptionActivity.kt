package com.safetica.datasafe.activity

import android.os.Bundle

/**
 * Implements onLoggedIn() method that shows CryptoFragment in decryption mode
 */
class DecryptionActivity: BaseActivity() {
    override fun onLoggedIn(savedInstanceState: Bundle?) {
        if (!loggedIn) {
            showFragment(decryptionFragment, false)
        }
    }

}
