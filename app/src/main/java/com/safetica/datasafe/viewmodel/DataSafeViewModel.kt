package com.safetica.datasafe.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Parcelable
import androidx.lifecycle.*
import com.safetica.datasafe.interfaces.ICryptoManager
import com.safetica.datasafe.interfaces.IDatabaseManager
import com.safetica.datasafe.utils.FileUtility
import com.safetica.datasafe.model.Image
import kotlinx.coroutines.*
import javax.crypto.SecretKey
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Provides functionality and data from data layer to user interface (BaseActivity)
 * @property app context
 * @property databaseManager
 * @property cryptoManager to provide cryptographic operations
 */
class DataSafeViewModel @Inject constructor(val app: Application, private val databaseManager: IDatabaseManager, private val cryptoManager: ICryptoManager) : AndroidViewModel(app), CoroutineScope {
    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    private var token: MutableLiveData<SecretKey> = MutableLiveData<SecretKey>().apply {
        value = null
    }

    var sentUris: ArrayList<Uri>? = null

    /**
     * @return token
     */
    val tokenValue: SecretKey?
        get() = token.value

    /**
     * Sets token to null
     */
    fun unsetToken() {
        token.value = null
    }

    /**
     * Sets token
     * @param value
     */
    fun setToken(value: SecretKey) {
        token.value = value
    }

    /**
     * Registers callback on token field
     * @param owner who owns lifecycle (component in which the callback is being registered)
     * @param - callback which reacts on value changes of token field
     */
    fun tokenSetEvent(owner: LifecycleOwner, observer: Observer<SecretKey>) {
        token.observe(owner, observer)
    }

    /**
     * Registers callback to database
     * @param encrypted - boolean which specifies if encrypted or unencrypted images should be returned
     * @param owner who owns lifecycle (component in which the callback is being registered)
     * @param observer - callback which reacts on changes in db
     */
    fun getImages(encrypted: Boolean?, owner: LifecycleOwner, observer: Observer<List<Image>>) {
        launch {
            databaseManager.refreshState()
            withContext(Dispatchers.Main) {
                if (encrypted == null) {
                    databaseManager.getAllImagesLiveData().observe(owner, observer)
                } else {
                    databaseManager.getAllImagesLiveData(encrypted).observe(owner, observer)
                }
            }
        }
    }

    /**
     * Get images from database
     * @param encrypted - boolean which specifies if encrypted or unencrypted images should be returned
     * @return list of Images from database
     */
    suspend fun getImages(encrypted: Boolean?) = withContext(Dispatchers.IO){
        databaseManager.refreshState()
        return@withContext if (encrypted == null) {
            databaseManager.getAllImages()
        } else {
            databaseManager.getAllImages(encrypted)
        }
    }

    /**
     * Delete images from database
     * @param images
     */
    fun deleteImages(vararg images: Image): Job {
        return launch {
            FileUtility.delete(app, *images)
            databaseManager.deleteImages(*images)
        }
    }

    /**
     * Update images in database
     * @param images
     */
    fun updateImages(vararg images: Image): Job {
        return launch {
            databaseManager.updateImages(*images)
        }
    }

    /**
     * Inserts images into database
     * @param images
     */
    fun insertImages(vararg images: Image): Job {
        return launch {
            databaseManager.insertImages(*images)
        }
    }

    /**
     * Extracts sent uris (of images) from [parcelables] objects
     * Uris are then saved to sentUris field
     * @param parcelables objects which holds uris of images which was sent into the app using Intent component
     */
    fun processImageUris(vararg parcelables: Parcelable) {
        val imageUris = ArrayList<Uri>()
        parcelables.forEach { parcelable ->
            val uri = parcelable as? Uri

            uri?.let {
                imageUris.add(it)
            }
        }

        if (imageUris.isNotEmpty()) {
            sentUris = imageUris
        }
    }

    val hasExtraUris: Boolean get() = !sentUris.isNullOrEmpty()

    val hasJustOneExtraUri: Boolean get() = sentUris?.size ?: 0 == 1

    /**
     * Provides Images which was sent to the app from another app
     * @return list of Images
     */
    suspend fun getSentImages() = withContext(Dispatchers.IO) {
        val sentImages = ArrayList<Image>()
        sentUris?.forEach {
            val image = cryptoManager.getImageModel(it)
            image?.let {
                sentImages.add(image)
            }
        }
        return@withContext sentImages
    }

    /**
     * Decrypts image in app (before it is displayed)
     * @param image (encrypted)
     * @return ByteArray which contains decrypted image if successful, null otherwise
     */
    suspend fun decryptImage(image: Image): ByteArray? {
        var result: ByteArray? = null
        tokenValue?.let {
            result = cryptoManager.decryptInApp(image, it)
        }
        return result
    }

    override fun onCleared() {
        coroutineContext.cancelChildren()
        super.onCleared()
    }
}