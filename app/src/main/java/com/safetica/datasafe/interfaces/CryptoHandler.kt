package com.safetica.datasafe.interfaces

import android.net.Uri
import com.safetica.datasafe.model.Image
import javax.crypto.SecretKey

interface CryptoHandler {
    fun encrypt(vararg uris: Uri)
    fun decrypt(vararg images: Image)
    fun import(token: SecretKey, vararg uris: Uri)
    fun rewriteKeys(oldToken: SecretKey, newToken: SecretKey)
}