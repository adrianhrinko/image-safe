package com.safetica.datasafe.manager

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.safetica.datasafe.constants.Constants.DATASAFE_SIGN
import com.safetica.datasafe.extensions.*
import com.safetica.datasafe.interfaces.ICryptoManager
import com.safetica.datasafe.interfaces.ImageTransformation
import com.safetica.datasafe.model.Image
import com.safetica.datasafe.utils.ByteUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import com.safetica.datasafe.interfaces.Progress
import com.safetica.datasafe.utils.FileUtility
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton


/**
 * This class provides methods for encryption, decryption, import and more.
 * @property context of an application
 * @property transformation to create a fake image
 */
@Singleton
class CryptoManager @Inject constructor(private val context: Application, private val transformation: ImageTransformation): ICryptoManager {

    companion object {
        private val PNG_SIGN = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        private val JPG_SIGN = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        private const val KEY_SIZE = 16
        private const val IV_SIZE = 16
        private const val IMAGE_LENGTH_SIZE = 8
        private var INFO_SIZE = KEY_SIZE + IV_SIZE + IMAGE_LENGTH_SIZE + DATASAFE_SIGN.size
        private const val PIXEL_DENSITY = 16
        private const val TEMP_FILE_NAME = "temp"


        /**
         * Finds out if file with given [filePath] was encrypted by our solution
         * @param filePath of file to check
         * @return true if file was encrypted by our solution, false otherwise
         */
        fun isSecured(filePath: String): Boolean {
            val infoBytes = getInfoBytes(filePath) ?: return false
            return isSecured(infoBytes)
        }

        /**
         * Finds out if given [infoBytes] belong to an image encrypted by our solution
         * @param infoBytes to check
         * @return true if [infoBytes] belong to an image encrypted by our solution, false otherwise
         */
        private fun isSecured(infoBytes: ByteArray): Boolean {
            if (infoBytes.size != INFO_SIZE) return false
            val signature = infoBytes.copyOfRange(KEY_SIZE + IV_SIZE + IMAGE_LENGTH_SIZE, INFO_SIZE)
            return ByteUtil.compareBytes(DATASAFE_SIGN, signature)

        }

        /**
         * Returns bytes that cares iv, key and original file size from encrypted image with given [filePath]
         * @param filePath of image
         * @return ByteArray with information if successful, null otherwise
         */
        private fun getInfoBytes(filePath: String): ByteArray? {
            var infoBytes: ByteArray? = ByteArray(INFO_SIZE)
            var file: RandomAccessFile? = null
            try {
                file = RandomAccessFile(filePath, "r")
                file.seek(file.length() - INFO_SIZE)
                file.read(infoBytes, 0, INFO_SIZE)
            } catch (ex: IOException) {
                Timber.e("Failed to open file $filePath")
                Timber.e(ex)
                infoBytes = null
            } finally {
                file?.close()
            }

            return infoBytes
        }
    }

    //ENCRYPT
    /**
     * Encrypts images with given [uris] using [token] as encryption key and saves them on disc
     * @param progress interface to be able to track progress of encryption
     * @param token to use as a encryption key
     * @param uris to decrypt
     * @return array of successfully encrypted Images
     */
    override suspend fun encrypt(progress: Progress, token: SecretKey, vararg uris: Uri): Array<Image> {
        val images = ArrayList<Image>()

        uris.forEach {uri ->
            val image = encrypt(uri, token)

            image?.let {
                images.add(it)
                progress.update()
            }
        }

        return images.toTypedArray()
    }

    //IMPORT
    /**
     * Rewrites keys saved in images with given [uris] using [oldToken] for decryption and [newToken] for encryption
     * @param progress interface to be able to track progress
     * @param oldToken to decrypt key with
     * @param newToken to encrypt key with
     * @param uris to decrypt
     * @return array of images with successfully rewrited keys
     */
    override suspend fun rewriteKey(progress: Progress, oldToken: SecretKey, newToken: SecretKey, vararg uris: Uri): Array<Image> {
        val images = ArrayList<Image>()

        uris.forEach {uri ->
            val image = rewriteKey(uri, oldToken, newToken)

            image?.let {
                images.add(it)
                progress.update()
            }

        }

        return images.toTypedArray()
    }

    /**
     * Imports given [images]
     * @param progress interface to be able to track progress of import
     * @param oldToken to decrypt key saved in image file
     * @param newToken to encrypt key saved in image file
     * @param images to import
     * @return array of successfully imported images
     */
    override suspend fun import(progress: Progress, oldToken: SecretKey, newToken: SecretKey, vararg images: Image): Array<Image> {
        val result = ArrayList<Image>()

        images.forEach {img ->
            val image = rewriteKey(img.path, oldToken, newToken)

            image?.let {
                result.add(it)
                progress.update()
            }
        }

        return result.toTypedArray()
    }

    //DECRYPT
    /**
     * Decrypts given [images] using [token] as encryption key and saves them on disc
     * @param progress interface to be abble to track progress of decryption
     * @param token to use as a decryption key
     * @param images to decrypt
     * @return ByteArray that contains decrypted image bytes
     */
    override suspend fun decrypt(progress: Progress, token: SecretKey, vararg images: Image): Array<Image> {
        val decrypted = ArrayList<Image>()

        images.forEach {image ->
            val result = decryptOnDisc(image, token)

            result?.let {
                decrypted.add(it)
            }
            progress.update()
        }

        return decrypted.toTypedArray()
    }

    /**
     * Decrypts given [image] using [token] as encryption key
     * @param image to decrypt
     * @param token to use as a decryption key
     * @return ByteArray that contains decrypted image bytes
     */
    override suspend fun decryptInApp(image: Image, token: SecretKey) = withContext(Dispatchers.IO) {

        val cipherInput = getCipherInputStreamToDecrypt(image, token) ?: return@withContext null

        val output = ByteArrayOutputStream(image.imageSize.toInt())

        decrypt(image.imageSize, cipherInput, output)

        return@withContext output.toByteArray()
    }


    //MODEL
    /**
     * Creates instance of Image from image file with given [uri]
     * @param uri to image file
     * @return Image with information about file with given [uri] if successful, null otherwise
     */
    override fun getImageModel(uri: Uri): Image? {

        val filePath = uri.getRealFilePath(context) ?: return null
        return getImageModel(filePath)
    }

    //***********************************************************************

    private suspend fun encrypt(uri: Uri, token: SecretKey): Image? {
        val realPath = uri.getRealFilePath(context)
        realPath?.let {
            return encrypt(realPath, token)
        }
        return null
    }

    /**
     * Encrypts image with a given [path] using [token] as a key
     * @param path to file to encrypt
     * @param token to use as an encryption key
     * @return Image with information about encrypted file if successful, null otherwise
     */
    private suspend fun encrypt(path: String, token: SecretKey) = withContext(Dispatchers.IO) {

        if (isSecured(path)) return@withContext null

        //open streams
        val inputFakeImg = tryOpenFileStream(path) ?: return@withContext null
        val inputTargetImg = tryOpenFileStream(path) ?: return@withContext null

        //get temp file stream
        val tempFile = getTempFile(context)
        val output = FileOutputStream(tempFile, true)

        //initialize cipher input stream
        val key = getRandomKey()
        val iv = getRandomIv()
        val cipherInput = getCipherInputStream(key, iv, inputTargetImg, Cipher.ENCRYPT_MODE)

        //write fake image
        val compressFormat = getFileFormat(path) ?: return@withContext null
        writeFakeImage(inputFakeImg, compressFormat, output)
        val fakeImageSize = tempFile.length()
        if (fakeImageSize == 0L) return@withContext null

        //write encrypted image
        var result = encrypt(cipherInput, output)
        if (!result) return@withContext null

        val imageSize = tempFile.length() - fakeImageSize

        //create image to return
        val encryptedKeyBytes = encryptKey(key, token) ?: return@withContext null
        val image = Image(path,
            imageSize, System.currentTimeMillis(), encryptedKeyBytes, iv.iv)

        //writing info bytes
        val infoBytes = getDataInfoBytes(encryptedKeyBytes, iv, tempFile.length() - fakeImageSize) ?: return@withContext null
        result = writeInfoBytes(output, infoBytes)
        if (!result) return@withContext null

        //copying temp file to
        result = copyFile(tempFile.path, path)
        tempFile.delete()

        if(result) {
            FileUtility.refreshMetadata(path, context)
            return@withContext image
        }

        return@withContext null
    }

    /**
     * Decrypts given [image] with given [token] as a key and saves it on disc
     * @param image to decrypt
     * @param token to use as a key for decryption
     * @return Image with information about decrypted file if successful, null otherwise
     */
    private suspend fun decryptOnDisc(image: Image, token: SecretKey) = withContext(Dispatchers.IO) {

        val cipherInput = getCipherInputStreamToDecrypt(image, token) ?: return@withContext null

        val tempFile = getTempFile(context)
        val output = FileOutputStream(tempFile, true)

        var result: Boolean = decrypt(image.imageSize, cipherInput, output)

        if (result) {
            result = copyFile(tempFile.path, image.path)
        } else {
            return@withContext null
        }

        if (result) {
            FileUtility.refreshMetadata(image.path, context)
            return@withContext image.reset()
        }

        return@withContext null

    }

    /**
     * Decrypts key which is saved in file with given [uri] by [oldToken] and then encrypts it by [newToken]
     * @param path of file to modify
     * @param oldToken for decryption
     * @param newToken for encryption
     * @return Image with rewrited key if successful, null otherwise
     */
    private suspend fun rewriteKey(uri: Uri, oldToken: SecretKey, newToken: SecretKey): Image? {
        val realPath = uri.getRealFilePath(context)
        realPath?.let {
            return rewriteKey(realPath, oldToken, newToken)
        }
        return null
    }

    /**
     * Decrypts key which is saved in file with given [path] by [oldToken] and then encrypts it by [newToken]
     * @param path of file to modify
     * @param oldToken for decryption
     * @param newToken for encryption
     * @return Image with rewrited key if successful, null otherwise
     */
    private suspend fun rewriteKey(path: String, oldToken: SecretKey, newToken: SecretKey) = withContext(Dispatchers.IO) {
        val model = getImageModel(path) ?: return@withContext null
        if (!isValidKey(model, oldToken)) return@withContext null
        val decryptedKey = model.key?.decryptUsingAesEcb(oldToken) ?: return@withContext null
        val encryptedKey = decryptedKey.encryptUsingAesEcb(newToken)

        val result = rewriteKey(path, encryptedKey)

        if (!result) return@withContext null

        return@withContext model.apply { key = encryptedKey }

    }

    /**
     * Founds out if given [token] is valid key to decrypt given [image]
     * @param image to test on
     * @param token to test
     * @return true if [token] is valid key to decrypt [image], false otherwise
     */
    private fun isValidKey(image: Image, token: SecretKey): Boolean {
        val firsBytes = ByteArray(16)

        val input = getCipherInputStreamToDecrypt(image, token) ?: return false
        input.read(firsBytes)
        input.close()

        compressFormat(firsBytes.take(2).toByteArray()) ?: return false

        return true

    }

    /**
     * Rewrites key saved in the file with an image by new [encryptedKey]
     * @param path of file in which the key should be rewrited
     * @param encryptedKey to rewrite with
     * @return true if successful, false otherwise
     */
    private fun rewriteKey(path: String, encryptedKey: ByteArray): Boolean {
        var output: RandomAccessFile? = null

        return try {
            output = RandomAccessFile(path, "rw")
            output.seek(output.length() - INFO_SIZE)
            output.write(encryptedKey)
            true
        } catch (e: IOException) {
            Timber.e("Failed to reencrypt file $path")
            Timber.e(e)
            false
        } finally {
            output?.close()
        }
    }

    /**
     * Securely tries to open FileOutputStream of file with given [path]
     * @param path of file to open
     * @return FileOutputStream if successful, null otherwise
     */
    private fun tryOpenFileStream(path: String): FileInputStream? {
       return try {
            FileInputStream(path)
        } catch (e: IOException) {
            Timber.e("Failed to open file")
            Timber.e(e)
            null
        }
    }

    /**
     * Writes [infoBytes] into [output] stream
     * @param output to write [infoBytes] to
     * @param infoBytes to write
     * @return true if successful, false otherwise
     */
    private fun writeInfoBytes(output: FileOutputStream, infoBytes: ByteArray): Boolean {
        return try {
            output.write(infoBytes)
            output.flush()
            true
        } catch (e: Exception) {
            Timber.e("Encryption failed with exception:")
            Timber.e(e)
            false
        } finally {
            output.close()
        }
    }

    /**
     * Encrypts image from [cipherInput] stream and writes it into [output] stream
     * @param cipherInput to read image from
     * @param output to write encrypted image to
     * @return true if successful, false otherwise
     */
    private fun encrypt(cipherInput: CipherInputStream, output: FileOutputStream): Boolean {
        return try {
            cipherInput.copyTo(output)
            cipherInput.close()
            output.flush()
            true
        } catch (e: Exception) {
            Timber.e("Encryption failed with exception:")
            Timber.e(e)
            output.close()
            false
        } finally {
            cipherInput.close()
        }
    }

    /**
     * Composes values [key], [iv] and [imageSize] into one ByteArray
     * @param key
     * @param iv
     * @param imageSize
     * @return ByteArray if successful, null otherwise
     */
    private fun getDataInfoBytes(key: ByteArray, iv: IvParameterSpec, imageSize: Long): ByteArray? {
        val byteBuffer = ByteBuffer.allocate(INFO_SIZE)
        byteBuffer.put(key)
        byteBuffer.put(iv.iv)
        byteBuffer.put(getFileSizeBytes(imageSize))
        byteBuffer.put(DATASAFE_SIGN)
        return byteBuffer.array()
    }

    /**
     * Transforms Long [value] into it's byte representation
     * @return 8 byte representation of [value]
     */
    private fun getFileSizeBytes(value: Long): ByteArray {
        val buffer = ByteBuffer.allocate(IMAGE_LENGTH_SIZE)
        buffer.putLong(value)
        return buffer.array()
    }

    /**
     * Takes original image from [inputFakeImg] stream, transforms it to fake image and writes to [output] stream
     * @param inputFakeImg image to transform to fake image
     * @param compressFormat to encode final result
     * @param output stream to write the fake image to
     */
    private suspend fun writeFakeImage(inputFakeImg: FileInputStream, compressFormat: Bitmap.CompressFormat, output: FileOutputStream) {
        try {
            var fakeImage = BitmapFactory.decodeStream(inputFakeImg)
            fakeImage = transformation.transform(fakeImage, PIXEL_DENSITY)
            fakeImage.compress(compressFormat, 100, output)
            output.flush()
        } catch (e: Exception) {
            Timber.e("Failed to write fake image:")
            Timber.e(e)
        } finally {
            inputFakeImg.close()
        }
    }

    /**
     * Opens CipherInputStream for given [input] stream.
     * @param key to use
     * @param iv to use
     * @param input stream
     * @param mode encryption/decryption
     * @return CipherInputStream if successful, null otherwise
     */
    private fun getCipherInputStream(key: SecretKey, iv: IvParameterSpec, input: FileInputStream, mode: Int): CipherInputStream {
        val c = Cipher.getInstance("AES/CTR/PKCS5Padding")
        c.init(mode, key, iv)
        return CipherInputStream(input, c)
    }

    /**
     * Returns random iv generated by SecureRandom.
     * @return random iv
     */
    private fun getRandomIv() = ByteUtil.getRandomBytes(IV_SIZE).aesIV()

    /**
     * Returns random key generated by SecureRandom
     * @return random key
     */
    private fun getRandomKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(SecureRandom())
        return keyGenerator.generateKey()
    }

    /**
     * Returns image file format of file with given [path]
     * @param path to file
     * @return image file format if successful, null otherwise
     */
    private fun getFileFormat(path: String): Bitmap.CompressFormat? {
        val signBytes = ByteArray(8)
        val toCheck = FileInputStream(path)
        try {
            toCheck.read(signBytes, 0, 8)
        } catch (e: IOException) {
            Timber.e("Failed to read file $path")
            Timber.e(e)
        } finally {
            toCheck.close()
        }

        return compressFormat(signBytes.take(2).toByteArray())
    }

    /**
     * Returns image format based on [signBytes] provided.
     * @param signBytes of an image file
     * @return image format if successful, null otherwise
     */
    private fun compressFormat(signBytes: ByteArray): Bitmap.CompressFormat? {
        return when {
            ByteUtil.compareBytes(PNG_SIGN, signBytes, false) -> Bitmap.CompressFormat.PNG
            ByteUtil.compareBytes(JPG_SIGN, signBytes, false) -> Bitmap.CompressFormat.JPEG
            else -> null
        }
    }

    /**
     * Creates temp file
     * @param context of application
     * @return temp file
     */
    private fun getTempFile(context: Context): File {
        val tempPath = "${context.cacheDir}/$TEMP_FILE_NAME-${System.currentTimeMillis()}"
        return File(tempPath)
    }

    /**
     * Copies file with given [filePath] to file with [destinationPath]
     * @param filePath to copy from
     * @param destinationPath to copy to
     * @return true if successful, false otherwise
     */
    private fun copyFile(filePath: String, destinationPath: String): Boolean {
        val input = FileInputStream(filePath)
        val output = FileOutputStream(destinationPath, false)

        return try {
            input.copyTo(output)
            output.flush()
            true
        } catch (e: IOException) {
            Timber.e("failed to copy $filePath to $destinationPath")
            false
        } finally {
            input.close()
            output.close()
        }

    }

    /**
     * Returns instance of Image class which holds information about image with given [filePath]
     * @param filePath
     * @return Image
     */
    private fun getImageModel(filePath: String): Image? {

        val fileSize = getFileSize(filePath)

        if (fileSize == 0L) return null

        val infoBytes = getInfoBytes(filePath) ?: return null
        if (!isSecured(infoBytes)) return null

        val key = getKey(infoBytes) ?: return null
        val iv = getIv(infoBytes) ?: return null
        val imageSize = getImageSize(infoBytes) ?: return null

        return Image(filePath, imageSize, System.currentTimeMillis(), key, iv)
    }

    /**
     * Returns size from [infoBytes] of an encrypted image
     * @param infoBytes bytes which hold info about an image
     * @return encrypted image size
     */
    private fun getImageSize(infoBytes: ByteArray): Long? {
        if (infoBytes.size != INFO_SIZE) return null
        return ByteBuffer.wrap(infoBytes.copyOfRange(KEY_SIZE + IV_SIZE, KEY_SIZE + IV_SIZE + IMAGE_LENGTH_SIZE)).long
    }


    /**
     * Returns key bytes from [infoBytes]
     * @param infoBytes bytes which hold info about an image
     * @return key bytes if successful, null otherwise
     */
    private fun getKey(infoBytes: ByteArray): ByteArray? {
        if (infoBytes.size != INFO_SIZE) return null
        return infoBytes.copyOfRange(0, KEY_SIZE)
    }


    /**
     * Returns size of file with given [filePath] in bytes
     * @param filePath path to file
     * @return size of file in bytes
     */
    private fun getFileSize(filePath: String): Long {
        return try {
            val file = File(filePath)
            file.length()
        } catch (ex: IOException) {
            Timber.e("Failed to open file $filePath")
            0
        }
    }

    /**
     * Returns initialisation bytes from [infoBytes]
     * @param infoBytes bytes which hold info about an image
     * @return initialisation vector bytes if successful, null otherwise
     */
    private fun getIv(infoBytes: ByteArray): ByteArray? {
        if (infoBytes.size != INFO_SIZE) return null
        return infoBytes.copyOfRange(KEY_SIZE, KEY_SIZE + IV_SIZE)
    }

    /**
     * Opens CipherInputStream from image for decryption
     * @param model holds information about image to decrypt
     * @param token to decrypt with
     * @return CipherInputStream if successfully opened, null otherwise
     */
    private fun getCipherInputStreamToDecrypt(model: Image, token: SecretKey): CipherInputStream? {
        val keyBytes = model.key
        val iv = model.iv?.aesIV()
        val imageSize = model.imageSize

        if (keyBytes == null || iv == null || imageSize <= 0) return null

        if(!isSecured(model.path)) return null

        val key = decryptKey(keyBytes, token) ?: return null

        val inputTargetImg = FileInputStream(model.path)

        val fileSize = File(model.path).length()
        val fakeImageSize = fileSize - (imageSize + INFO_SIZE)

        inputTargetImg.skip(fakeImageSize)

        return getCipherInputStream(key, iv, inputTargetImg, Cipher.DECRYPT_MODE)
    }


    /**
     * Decrypts [size] bytes from  [cipherInput] stream and writes them into [output] stream
     * @param size bytes to encrypt (must be multiple of 16)
     * @param cipherInput stream to decrypt
     * @param output stream
     * @return true if successfully decrypted, false otherwise
     */
    private fun decrypt(size: Long, cipherInput: CipherInputStream, output: OutputStream): Boolean {
        var countRed = 0
        val buffer = ByteArray(1024)

        return try {
            while (countRed < size) {
                val red = cipherInput.read(buffer)
                output.write(buffer, 0, red)
                countRed += red
            }
            output.flush()
            true
        } catch (e: Exception) {
            Timber.e("Decryption failed with exception: ${e.stackTrace}")
            false
        } finally {
            cipherInput.close()
            output.close()
        }
    }


    /**
     * Decrypts key using token as decryption key
     * @param key key to decrypt
     * @param token by which the key will be decrypted
     * @return if decrypted successfully, then decrypted key as ByteArray, null otherwise
     */
    private fun decryptKey(key: ByteArray, token: SecretKey): SecretKey? {
        return try {
            key.decryptUsingAesEcb(token).aesKey()
        } catch (e: Exception) {
            Timber.e("Failed to decrypt key: ${e.stackTrace}")
            null
        }
    }

    /**
     * Encrypts key by token as encryption key
     * @param key key to encrypt
     * @param token by which the key will be encrypted
     * @return if encrypted successfully, then encrypted key as ByteArray, null otherwise
     */
    private fun encryptKey(key: SecretKey, token: SecretKey): ByteArray? {
        return try {
            key.encoded.encryptUsingAesEcb(token)
        } catch (e: Exception) {
            Timber.e("Failed to encrypt key: ${e.stackTrace}")
            null
        }
    }
}