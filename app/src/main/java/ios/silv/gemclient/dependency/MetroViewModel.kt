package ios.silv.gemclient.dependency

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import ios.silv.gemclient.ui.UiEvent
import ios.silv.gemclient.ui.UiState

@Composable
inline fun <reified P: Presenter> metroPresenter(): P {
    return metroPresenterProviderFactory().create(P::class.java)
}

@Composable
inline fun <reified VM : ViewModel> metroViewModel(
    viewModelStoreOwner: ViewModelStoreOwner =
        checkNotNull(LocalViewModelStoreOwner.current) {
            "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
        },
    key: String? = null,
): VM {
    return viewModel(viewModelStoreOwner, key, factory = metroViewModelProviderFactory())
}

@Composable
fun metroPresenterProviderFactory(): PresenterFactory {
    return LocalMetroPresenterFactory.current
}

@Composable
fun metroViewModelProviderFactory(): ViewModelProvider.Factory {
    return (LocalActivity.current as HasDefaultViewModelProviderFactory)
        .defaultViewModelProviderFactory
}