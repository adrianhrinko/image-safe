package com.safetica.datasafe.activity

import android.os.Bundle

/**
 * Implements onLoggedIn() method that shows GalleryFragment or DetailFragment
 */
class GalleryActivity : BaseActivity() {

    override fun onLoggedIn(savedInstanceState: Bundle?) {
        if (!loggedIn) {
            if(viewModel.hasJustOneExtraUri) {
                showFragment(detailFragment, false)
            } else {
                showFragment(galleryFragment, false)
            }
        }
    }

}
