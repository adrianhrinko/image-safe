package com.safetica.datasafe.dependencies

import android.app.Application
import com.safetica.datasafe.App
import com.safetica.datasafe.dependencies.modules.*
import dagger.BindsInstance
import dagger.Component
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton


@Component(modules = [DatabaseModule::class, ManagerModule::class,
    ActivityModule::class, FragmentModule::class, ServiceModule::class,
    ViewModelModule::class, AndroidSupportInjectionModule::class])
@Singleton
interface  AppComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder
        fun build(): AppComponent
    }

    fun inject(app: App)
}