package com.safetica.datasafe.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.safetica.datasafe.model.Image

/**
 * Room persistence library - DAO
 */
@Dao
interface ImageDao {
    @Query("SELECT * FROM image ORDER BY image.lm DESC")
    fun getAll(): List<Image>

    @Query("SELECT * FROM image ORDER BY image.lm DESC")
    fun getAllLiveData(): LiveData<List<Image>>

    @Query("SELECT * FROM image WHERE image.ims <= 0 ORDER BY image.lm DESC")
    fun getAllUnencryptedLiveData(): LiveData<List<Image>>

    @Query("SELECT * FROM image WHERE image.ims > 0 ORDER BY image.lm DESC")
    fun getAllEncryptedLiveData(): LiveData<List<Image>>

    @Query("SELECT * FROM image WHERE image.ims <= 0 ORDER BY image.lm DESC")
    fun getAllUnencrypted(): List<Image>

    @Query("SELECT * FROM image WHERE image.ims > 0 ORDER BY image.lm DESC")
    fun getAllEncrypted(): List<Image>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg image: Image)

    @Update
    fun update(vararg image: Image)

    @Delete
    fun delete(vararg image: Image)
}