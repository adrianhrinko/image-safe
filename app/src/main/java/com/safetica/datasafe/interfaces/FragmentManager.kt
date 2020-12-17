package com.safetica.datasafe.interfaces

import androidx.fragment.app.Fragment

interface FragmentManager {
    fun showFragment(fragment: Fragment, backstack: Boolean)
}