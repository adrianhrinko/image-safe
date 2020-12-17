package com.safetica.datasafe.dependencies.modules

import com.safetica.datasafe.service.CryptoService
import dagger.Module
import dagger.android.ContributesAndroidInjector


@Module
abstract class ServiceModule {

    @ContributesAndroidInjector
    abstract fun contributeEncryptionService(): CryptoService

}