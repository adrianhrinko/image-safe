package com.safetica.datasafe.extensions
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.safetica.datasafe.manager.CryptoManager
import com.safetica.datasafe.utils.FileUtility
import com.safetica.datasafe.model.Image
import java.io.File

fun Image.exists(): Boolean {
    return File(this.path).exists()
}

fun Image.isSecured(): Boolean {
    return CryptoManager.isSecured(this.path)
}

fun Image.toUri(): Uri {
    return File(this.path).toUri()
}

/**
 * Resets image data to default values
 */
fun Image.reset(): Image {
    this.key = null
    this.iv = null
    this.imageSize = 0
    this.lastModified = System.currentTimeMillis()
    return this
}

fun Image.getContentUri(context: Context): Uri {
    return FileUtility.getContentUriForPath(this.path, context)
}