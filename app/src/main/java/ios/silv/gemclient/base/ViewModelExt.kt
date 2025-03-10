package ios.silv.gemclient.base

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Creates a `ViewModel`.
 *
 * The [provider] should instantiate the `ViewModel` directly.
 *
 */
@Suppress("UNCHECKED_CAST")
public inline fun <reified T : ViewModel> ComponentActivity.buildViewModel(crossinline provider: () -> T): Lazy<T> =
    viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = provider() as T
        }
    }

@Composable
@Suppress("UNCHECKED_CAST")
public inline fun <reified T : ViewModel> rememberViewModel(
    crossinline provider: () -> T,
): T {
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = provider() as T
        }
    )
}

@Suppress("UNCHECKED_CAST")
public inline fun <reified T : ViewModel> ComponentActivity.stateViewModel(
    crossinline provider: (handle: SavedStateHandle) -> T,
): Lazy<T> =
    viewModels {
        object : AbstractSavedStateViewModelFactory(this@stateViewModel, null) {
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle,
            ): T = provider(handle) as T
        }
    }
