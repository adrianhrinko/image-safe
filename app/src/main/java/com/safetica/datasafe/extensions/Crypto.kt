package com.safetica.datasafe.extensions

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec




fun ByteArray.sha512(): ByteArray {
    val md = MessageDigest.getInstance("SHA-512")
    return  md.digest(this)
}

fun ByteArray.aesIV(): IvParameterSpec = IvParameterSpec(this)

fun ByteArray.aesKey(): SecretKeySpec = SecretKeySpec(this, "AES")

fun ByteArray.decryptUsingAesEcb(key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key)
    return cipher.doFinal(this)
}

fun ByteArray.encryptUsingAesEcb(key: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher.doFinal(this)
}

fun SecretKey.copy(): SecretKey {
    return this.encoded.aesKey()
}