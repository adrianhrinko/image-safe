package com.safetica.datasafe.extensions

import android.content.Intent
import android.net.Uri
import java.util.ArrayList

/**
 * Gets uris from Intent
 * @return list of uris
 */
fun Intent.getUris(): ArrayList<Uri> {
    val clipData = this.clipData
    val uris = ArrayList<Uri>()

    if (clipData != null) {
        repeat(clipData.itemCount) { i ->
            uris.add(clipData.getItemAt(i).uri)
        }
    } else {
        this.data?.let {
            uris.add(it)
        }
    }

    return uris
}