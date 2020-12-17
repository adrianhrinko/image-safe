package com.safetica.datasafe.manager
import androidx.lifecycle.LiveData
import com.safetica.datasafe.database.Database
import com.safetica.datasafe.extensions.exists
import com.safetica.datasafe.extensions.isSecured
import com.safetica.datasafe.extensions.reset
import com.safetica.datasafe.interfaces.IDatabaseManager
import com.safetica.datasafe.model.Configuration
import com.safetica.datasafe.model.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides methods to access data in the database
 * @property database
 */
@Singleton
class DatabaseManager @Inject constructor(private val database: Database): IDatabaseManager {

    //image
    override suspend fun insertImages(vararg images: Image) = withContext(Dispatchers.IO) {
        database.imageDao().insert(*images)
    }


    override suspend fun updateImages(vararg images: Image) = withContext(Dispatchers.IO) {
        database.imageDao().update(*images)
    }


    override suspend fun deleteImages(vararg images: Image) = withContext(Dispatchers.IO) {
        database.imageDao().delete(*images)
    }


    override fun getAllImagesLiveData(): LiveData<List<Image>> {
        return database.imageDao().getAllLiveData()
    }

    override fun getAllImagesLiveData(encrypted: Boolean): LiveData<List<Image>> {
        if (encrypted) return database.imageDao().getAllEncryptedLiveData()
        return database.imageDao().getAllUnencryptedLiveData()
    }

    override fun getAllImages(): List<Image> {
        return database.imageDao().getAll()
    }

    override fun getAllImages(encrypted: Boolean): List<Image> {
        if (encrypted) return database.imageDao().getAllEncrypted()
        return database.imageDao().getAllUnencrypted()
    }

    /**
     * Refreshes state of database on start of the app (checking if all secured photos still exists)
     * Photos which are removed from device will be also removed from db.
     */
    override suspend fun refreshState() = withContext(Dispatchers.IO) {
        val images = database.imageDao().getAll()
        val toDelete = ArrayList<Image>()
        val toUpdate = ArrayList<Image>()

        images.forEach {
            if (!it.exists()) {
                toDelete.add(it)
            } else if (!it.isSecured()) {
                it.reset()
                toUpdate.add(it)
            }
        }

        database.imageDao().delete(*toDelete.toTypedArray())
        database.imageDao().update(*toUpdate.toTypedArray())
    }

    //configuration
    override suspend fun insertConfigs(vararg configuration: Configuration) = withContext(Dispatchers.Default) {
        database.configDao().insert(*configuration)
    }

    override suspend fun updateConfigs(vararg configuration: Configuration) = withContext(Dispatchers.Default) {
        database.configDao().update(*configuration)
    }

    override suspend fun deleteConfigs(vararg configuration: Configuration) = withContext(Dispatchers.Default) {
        database.configDao().delete(*configuration)
    }

    override suspend fun getAllConfigs(): List<Configuration> = withContext(Dispatchers.Default) {
        database.configDao().getAll()
    }

    override suspend fun getAllConfigs(name: String) = withContext(Dispatchers.Default) {
        database.configDao().getAll(name)
    }

}