package com.safetica.datasafe.database

import androidx.room.*
import com.safetica.datasafe.model.Configuration

/**
 * Room persistence library - DAO
 */
@Dao
interface ConfigDao {
    @Query("SELECT * FROM configuration")
    fun getAll(): List<Configuration>

    @Query("SELECT * FROM configuration WHERE n = :name")
    fun getAll(name: String): List<Configuration>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg configuration: Configuration)

    @Update
    fun update(vararg configuration: Configuration)

    @Delete
    fun delete(vararg configuration: Configuration)
}