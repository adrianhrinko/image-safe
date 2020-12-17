package com.safetica.datasafe.extensions

import android.content.Context
import android.net.Uri
import com.safetica.datasafe.manager.CryptoManager
import com.safetica.datasafe.utils.PathUtil

/**
 * Converts uri to absolute path
 * @param context of the app
 */
fun Uri.getRealFilePath(context: Context): String? {
    return PathUtil.getPath(context, this)
}

/**
 * Checks if file with given Uri is secured by our application
 * @param context of the app
 * @return true if it is secured by our app, false otherwise
 */
fun Uri.isSecured(context: Context): Boolean {
    val path = this.getRealFilePath(context)

    path?.let {
        return CryptoManager.isSecured(path)
    }

    return false
}