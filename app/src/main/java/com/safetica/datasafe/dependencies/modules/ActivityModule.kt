package com.safetica.datasafe.dependencies.modules

import com.safetica.datasafe.activity.*
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivityModule {
    @ContributesAndroidInjector(modules = [FragmentModule::class])
    abstract fun contributeSafeActivity(): GalleryActivity
    @ContributesAndroidInjector(modules = [FragmentModule::class])
    abstract fun contributeEncryptionActivity(): EncryptionActivity
    @ContributesAndroidInjector(modules = [FragmentModule::class])
    abstract fun contributeDecryptionActivity(): DecryptionActivity
    @ContributesAndroidInjector(modules = [FragmentModule::class])
    abstract fun contributeImportActivity(): ImportActivity
}