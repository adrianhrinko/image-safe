package com.safetica.datasafe.interfaces

import androidx.lifecycle.LiveData
import com.safetica.datasafe.extensions.exists
import com.safetica.datasafe.extensions.isSecured
import com.safetica.datasafe.extensions.reset
import com.safetica.datasafe.model.Configuration
import com.safetica.datasafe.model.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface IDatabaseManager {
    //image
    suspend fun insertImages(vararg images: Image)
    suspend fun updateImages(vararg images: Image)
    suspend fun deleteImages(vararg images: Image)
    fun getAllImagesLiveData(): LiveData<List<Image>>
    fun getAllImagesLiveData(encrypted: Boolean): LiveData<List<Image>>
    fun getAllImages(): List<Image>
    fun getAllImages(encrypted: Boolean): List<Image>
    suspend fun refreshState()

    //configuration
    suspend fun insertConfigs(vararg configuration: Configuration)
    suspend fun updateConfigs(vararg configuration: Configuration)
    suspend fun deleteConfigs(vararg configuration: Configuration)
    suspend fun getAllConfigs(): List<Configuration>
    suspend fun getAllConfigs(name: String): List<Configuration>
}