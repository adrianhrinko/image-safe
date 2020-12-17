package com.safetica.datasafe.dependencies.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.safetica.datasafe.dependencies.ViewModelFactory
import com.safetica.datasafe.dependencies.ViewModelKey
import com.safetica.datasafe.viewmodel.DataSafeViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
internal abstract class ViewModelModule {

    @Binds
    internal abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory


    @Binds
    @IntoMap
    @ViewModelKey(DataSafeViewModel::class)
    protected abstract fun dataSafeViewModel(dataSafeViewModel: DataSafeViewModel): ViewModel
}