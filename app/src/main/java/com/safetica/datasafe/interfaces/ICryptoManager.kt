package com.safetica.datasafe.interfaces

import android.net.Uri
import com.safetica.datasafe.extensions.getRealFilePath
import com.safetica.datasafe.model.Image
import javax.crypto.SecretKey

interface ICryptoManager {
    //ENCRYPT
    suspend fun encrypt(progress: Progress, token: SecretKey, vararg uris: Uri): Array<Image>

    //REWRITE KEY
    suspend fun rewriteKey(progress: Progress, oldToken: SecretKey, newToken: SecretKey, vararg uris: Uri): Array<Image>
    suspend fun import(progress: Progress, oldToken: SecretKey, newToken: SecretKey, vararg images: Image): Array<Image>

    //DECRYPT
    suspend fun decrypt(progress: Progress, token: SecretKey, vararg images: Image): Array<Image>
    suspend fun decryptInApp(image: Image, token: SecretKey): ByteArray?

    //MODEL
    fun getImageModel(uri: Uri): Image?
}