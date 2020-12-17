package com.safetica.datasafe.manager

import com.safetica.datasafe.constants.Constants
import com.safetica.datasafe.enums.PassStrength
import com.safetica.datasafe.extensions.aesKey
import com.safetica.datasafe.interfaces.IDatabaseManager
import com.safetica.datasafe.interfaces.ILoginManager
import com.safetica.datasafe.model.Configuration
import com.safetica.datasafe.utils.ByteUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spongycastle.crypto.generators.SCrypt
import java.nio.ByteBuffer
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides methods for password setup, authentication, key generation and more...
 * @property databaseManager
 */
@Singleton
class LoginManager @Inject constructor(private val databaseManager: IDatabaseManager): ILoginManager {

    companion object {
        private const val COMPLEXITY = 12
        private const val SALT_SIZE = 64
        private const val HASH_SIZE = 64
        private const val BLOCK_SIZE = 8
        private const val PARALLEL_PARAM = 1
        private const val PARAM_SIZE = 4
        private const val PARAMS_SIZE = PARAM_SIZE * 3
        private val TOKEN_SALT = "qHgxS-K*zvB*ahkqqKKM+6PSP7R4xwRkG!YskRxVFwWQC2JfF&jPM4bw2zxDxS3eESc6myWg?&k8jCw?_V#*9xsL8y3V&#jAAMV+E-u_@L452-^5XET@#pvMFUp%wwBM".toByteArray(
            Charsets.US_ASCII)
        private const val TOKEN_SIZE = 16
    }

    /**
     * Converts password strength number to PassStrength
     * @param password
     * @return PassStrength
     */
    override fun getPassphraseStrength(password: String): PassStrength {
        val score = calculatePasswordStrength(password)
        return when {
            score < 5 -> PassStrength.Weak
            score < 8 -> PassStrength.Medium
            else -> PassStrength.Strong
        }
    }

    /**
     * Sets password - generates hash and saves it into the database
     * @param password
     * @param confirmation of the [password] (must be exactly the same as [password])
     * @return SecretKey (token) for encryption and decryption if pass is valid, null otherwise
     */
    override suspend fun setPassword(password: String, confirmation: String) = withContext(Dispatchers.IO) {

        if (password.isEmpty() || confirmation.isEmpty()) return@withContext null

        val passBytes = password.toByteArray(Charset.defaultCharset())
        val confirmationBytes = confirmation.toByteArray(Charset.defaultCharset())

        if(!ByteUtil.compareBytes(passBytes, confirmationBytes)) return@withContext null

        val passHash = generatePasswordHash(password)

        val passConfig = Configuration(Constants.ENTITY.CONFIGURATION.VALUES.NAME_PASSWORD, passHash)

        databaseManager.insertConfigs(passConfig)
        return@withContext generateToken(password)

    }

    /**
     * Generetes token (key for encryption and decryption) from password
     * @param password
     * @return SecretKey (token) for encryption and decryption
     */
    override suspend fun generateToken(password: String) = withContext(Dispatchers.IO) {
        return@withContext generateHash(password, TOKEN_SALT, COMPLEXITY, BLOCK_SIZE, PARALLEL_PARAM, TOKEN_SIZE).aesKey()
    }

    /**
     * Validates password
     * @param pass
     * @return SecretKey (token) for encryption and decryption if pass is valid, null otherwise
     */
    override suspend fun validatePassword(pass: String?) = withContext(Dispatchers.IO) {
        if (pass.isNullOrEmpty()) return@withContext null

        val storedPass = getPassword()

        if (storedPass == null || storedPass.isEmpty()) return@withContext null

        val complexity = getComplexity(storedPass)
        val blockSize = getBlockSize(storedPass)
        val parallelParam = getParallelParam(storedPass)
        val salt = getSalt(storedPass)
        val hash = getHash(storedPass)

        val testHash = generateHash(pass, salt, complexity, blockSize, parallelParam, hash.size)

        if(!ByteUtil.compareBytes(hash, testHash)) return@withContext null

        return@withContext generateToken(pass)
    }


    //##################################################################################################################

    /**
     * Computes password strength
     * Source: https://www.javacodeexamples.com/check-password-strength-in-java-example/668
     * @param password
     * @return password strength number 0-10
     */
    private fun calculatePasswordStrength(password: String): Int {

        var passScore = 0

        if (password.length < 6)
            return 0
        else if (password.length >= 10)
            passScore += 2
        else
            passScore += 1

        /*
    * if password contains 2 digits, add 2 to score.
    * if contains 1 digit add 1 to score
    */
        if (password.matches("(?=.*[0-9].*[0-9]).*".toRegex()))
            passScore += 2
        else if (password.matches("(?=.*[0-9]).*".toRegex()))
            passScore += 1
        else
            return 0

        //if password contains 1 lower case letter, add 2 to score
        if (password.matches("(?=.*[a-z]).*".toRegex()))
            passScore += 2

        /*
    * if password contains 2 upper case letters, add 2 to score.
    * if contains only 1 then add 1 to score.
    */
        if (password.matches("(?=.*[A-Z].*[A-Z]).*".toRegex()))
            passScore += 2
        else if (password.matches("(?=.*[A-Z]).*".toRegex()))
            passScore += 1
        else
            return 0

        /*
    * if password contains 2 special characters, add 2 to score.
    * if contains only 1 special character then add 1 to score.
    */
        if (password.matches("(?=.*[~!@#$%^&*()_-].*[~!@#$%^&*()_-]).*".toRegex()))
            passScore += 2
        else if (password.matches("(?=.*[~!@#$%^&*()_-]).*".toRegex()))
            passScore += 1

        return passScore

    }


    /**
     * Generates password BLOB from password
     * @param password
     * @return password BLOB
     */
    private fun generatePasswordHash(password: String): ByteArray {

        val salt = ByteUtil.getRandomBytes(SALT_SIZE)

        val hash = generateHash(password, salt, COMPLEXITY, BLOCK_SIZE, PARALLEL_PARAM, HASH_SIZE)

        val bytes = ByteBuffer.allocate( PARAMS_SIZE + SALT_SIZE + HASH_SIZE)
        val complexityBytes = ByteBuffer.allocate(PARAM_SIZE).putInt(COMPLEXITY)
        val blockSizeBytes = ByteBuffer.allocate(PARAM_SIZE).putInt(BLOCK_SIZE)
        val parallelParam = ByteBuffer.allocate(PARAM_SIZE).putInt(PARALLEL_PARAM)
        bytes.put(complexityBytes.array())
        bytes.put(blockSizeBytes.array())
        bytes.put(parallelParam.array())
        bytes.put(salt)
        bytes.put(hash)

        return bytes.array()
    }


    /**
     * Generates hash from password
     * @param pass password
     * @param salt
     * @param complexity N parameter
     * @param blockSize r parameter
     * @param parallelParam p parameter
     * @param size size of generated hash in bytes
     * @return password hash
     */
    private fun generateHash(pass: String, salt: ByteArray, complexity: Int, blockSize: Int, parallelParam: Int, size: Int): ByteArray {
        val trueComplexity = Math.pow(2.0, complexity.toDouble()).toInt()
        return SCrypt.generate(pass.toByteArray(Charsets.US_ASCII), salt, trueComplexity, blockSize, parallelParam, size)
    }

    /**
     * Extracts password hash from password BLOB
     * @param storedPass password BLOB
     * @return password hash
     */
    private fun getHash(storedPass: ByteArray) =
        storedPass.copyOfRange(PARAMS_SIZE + SALT_SIZE, storedPass.size)

    /**
     * Extracts cryptographic salt from password BLOB
     * @param storedPass password BLOB
     * @return cryptographic salt
     */
    private fun getSalt(storedPass: ByteArray) =
        storedPass.copyOfRange(PARAMS_SIZE, PARAMS_SIZE + SALT_SIZE)

    /**
     * Extracts N parameter from password BLOB
     * @param storedPass password BLOB
     * @return N parameter for scrypt function
     */
    private fun getComplexity(storedPass: ByteArray): Int {
        val complexity = storedPass.copyOfRange(0, PARAM_SIZE)
        return ByteBuffer.wrap(complexity).int
    }

    /**
     * Extracts r parameter from password BLOB
     * @param storedPass password BLOB
     * @return r parameter for scrypt function
     */
    private fun getBlockSize(storedPass: ByteArray): Int {
        val blockSize = storedPass.copyOfRange(PARAM_SIZE, PARAM_SIZE + PARAM_SIZE)
        return ByteBuffer.wrap(blockSize).int
    }

    /**
     * Extracts p parameter from password BLOB
     * @param storedPass password BLOB
     * @return p parameter for scrypt function
     */
    private fun getParallelParam(storedPass: ByteArray): Int {
        val parallelParam = storedPass.copyOfRange(PARAM_SIZE + PARAM_SIZE, PARAMS_SIZE)
        return ByteBuffer.wrap(parallelParam).int
    }

    /**
     * @return password BLOB from database
     */
    private suspend fun getPassword() =
        databaseManager.getAllConfigs(Constants.ENTITY.CONFIGURATION.VALUES.NAME_PASSWORD).firstOrNull()?.data


    /**
     * @return true if password is set
     */
    override suspend fun isPasswordSet() =
        databaseManager.getAllConfigs(Constants.ENTITY.CONFIGURATION.VALUES.NAME_PASSWORD).isNotEmpty()

}