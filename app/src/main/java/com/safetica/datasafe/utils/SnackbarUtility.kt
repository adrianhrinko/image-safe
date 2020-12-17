package com.safetica.datasafe.utils

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.safetica.datasafe.R


object SnackbarUtility {

    fun showSnackbar(context: Context, view: View?, message: Int, action: ((v: View) -> Unit)? = null) {
        view?.let {
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)

            action?.let {
                snackbar.duration = BaseTransientBottomBar.LENGTH_INDEFINITE
                snackbar.setAction(R.string.ok) { view -> it.invoke(view); snackbar.dismiss()}
            }

            snackbar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
            snackbar.view.findViewById<TextView>(R.id.snackbar_text)
                .setTextColor(ContextCompat.getColor(context, R.color.colorCritical))
            snackbar.setActionTextColor(ContextCompat.getColor(context, R.color.colorPrimaryDark))
            snackbar.show()
        }
    }
}