package com.safetica.datasafe.dependencies.modules
import android.app.Application
import com.safetica.datasafe.database.Database
import com.safetica.datasafe.interfaces.ICryptoManager
import com.safetica.datasafe.interfaces.IDatabaseManager
import com.safetica.datasafe.interfaces.ILoginManager
import com.safetica.datasafe.interfaces.ImageTransformation
import com.safetica.datasafe.manager.CryptoManager
import com.safetica.datasafe.manager.DatabaseManager
import com.safetica.datasafe.manager.LoginManager
import com.safetica.datasafe.utils.Pixelator
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ManagerModule {

    @Provides
    @Singleton
    fun imageTransformation(): ImageTransformation {
        return Pixelator()
    }

    @Provides
    @Singleton
    fun cryptoManager(application: Application, transformation: ImageTransformation): ICryptoManager {
        return CryptoManager(application, transformation)
    }

    @Provides
    @Singleton
    fun loginManager(databaseManager: IDatabaseManager): ILoginManager {
        return LoginManager(databaseManager)
    }

    @Provides
    @Singleton
    fun databaseManager(databaseManager: Database): IDatabaseManager {
        return DatabaseManager(databaseManager)
    }
}