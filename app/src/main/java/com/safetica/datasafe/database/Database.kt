package com.safetica.datasafe.database

import androidx.room.RoomDatabase
import com.safetica.datasafe.model.Configuration
import com.safetica.datasafe.model.Image

/**
 * Room persistence library - Database
 */
@androidx.room.Database(entities = [Image::class, Configuration::class], version = 1, exportSchema = false)
abstract class Database : RoomDatabase() {

    abstract fun imageDao(): ImageDao
    abstract fun configDao(): ConfigDao

    companion object {
        const val DATABASE_NAME = "com.safetica.datasafe.db"
    }
}