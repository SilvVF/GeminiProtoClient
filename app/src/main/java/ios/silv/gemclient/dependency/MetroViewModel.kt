package ios.silv.gemclient.dependency

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.createGraphFactory
import ios.silv.gemclient.App
import kotlin.reflect.cast


@Composable
inline fun <reified VM : ViewModel> metroViewModel(
    args: ios.silv.shared.NavKey,
    activity: Activity = checkNotNull(LocalActivity.current) {
        "no activity"
    },
    key: String? = null
): VM = viewModel {
    val viewModelGraph = createGraphFactory<ViewModelGraph.Factory>()
        .create((activity.application as App).appGraph, this, args)

    println(viewModelGraph.viewModelProviders)
    val provider =
        viewModelGraph.viewModelProviders[VM::class]
            ?: throw IllegalArgumentException("Unknown model class ${VM::class}")

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    VM::class.cast(provider())
}

@Composable
fun metroViewModelProviderFactory(): ViewModelProvider.Factory {
    return (LocalActivity.current as HasDefaultViewModelProviderFactory)
        .defaultViewModelProviderFactory
}