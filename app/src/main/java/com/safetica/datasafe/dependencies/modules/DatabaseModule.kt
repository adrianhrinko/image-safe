package com.safetica.datasafe.dependencies.modules

import android.app.Application

import androidx.room.Room
import com.safetica.datasafe.database.Database
import dagger.Module
import javax.inject.Singleton
import dagger.Provides



@Module
class DatabaseModule {

    @Provides
    @Singleton
    fun database(application: Application): Database {
        return Room.databaseBuilder(application, Database::class.java, Database.DATABASE_NAME).build()
    }

}