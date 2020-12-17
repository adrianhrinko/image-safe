package com.safetica.datasafe.interfaces
import com.safetica.datasafe.enums.PassStrength
import javax.crypto.spec.SecretKeySpec

interface ILoginManager {
    suspend fun setPassword(password: String, confirmation: String): SecretKeySpec?
    suspend fun validatePassword(pass: String?): SecretKeySpec?
    suspend fun isPasswordSet(): Boolean
    suspend fun generateToken(password: String): SecretKeySpec
    fun getPassphraseStrength(password: String): PassStrength
}