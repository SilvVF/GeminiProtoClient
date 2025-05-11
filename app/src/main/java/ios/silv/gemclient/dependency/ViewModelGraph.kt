package ios.silv.gemclient.dependency

import android.app.Application
import androidx.annotation.Nullable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Extends
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemclient.ui.UiEvent
import ios.silv.gemclient.ui.UiState
import kotlin.reflect.KClass

@DependencyGraph(ViewModelScope::class)
interface ViewModelGraph {

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
        ): ViewModelGraph
    }
}

@Immutable
interface Presenter

@DependencyGraph(PresenterScope::class)
interface PresenterGraph {

    @Multibinds
    val presenterProviders: Map<KClass<out Presenter>, Provider<Presenter>>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Extends appGraph: AppGraph,
        ): PresenterGraph
    }
}