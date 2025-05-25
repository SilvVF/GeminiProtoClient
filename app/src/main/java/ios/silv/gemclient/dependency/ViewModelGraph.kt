package ios.silv.gemclient.dependency

import android.app.Application
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Extends
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import ios.silv.shared.di.ViewModelScope
import kotlin.reflect.KClass

@DependencyGraph(ViewModelScope::class)
interface ViewModelGraph {

    @Provides
    fun provideNavKey(navKey: ios.silv.shared.NavKey?): ios.silv.shared.NavKey = requireNotNull(navKey)

    @Multibinds
    val viewModelProviders: Map<KClass<out ViewModel>, Provider<ViewModel>>

    @Provides
    fun provideApplication(creationExtras: CreationExtras): Application =
        creationExtras[APPLICATION_KEY]!!

    @Provides
    fun provideSavedStateHandle(creationExtras: CreationExtras): SavedStateHandle =
        creationExtras.createSavedStateHandle()


    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Extends appGraph: AppGraph,
            @Provides creationExtras: CreationExtras,
            @Provides navKey: ios.silv.shared.NavKey?,
        ): ViewModelGraph
    }
}