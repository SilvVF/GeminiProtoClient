package ios.silv.gemclient.dependency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Extends
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.base.LocalNavController
import kotlin.reflect.KClass

val LocalMetroPresenterFactory = staticCompositionLocalOf<PresenterFactory> { error("") }

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
            @Provides navController: NavController,
        ): PresenterGraph
    }
}

interface PresenterFactory {
    fun <T: Presenter> create(modelClass: Class<T>, navController: NavController): T
}

abstract class PresenterScope private constructor()

/** A [MapKey] annotation for binding ViewModels in a multibinding map. */
@MapKey
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PresenterKey(val value: KClass<out Presenter>)

/**
 * A [ViewModelProvider.Factory] that uses an injected map of [KClass] to [Provider] of [ViewModel]
 * to create ViewModels.
 */
@ContributesBinding(AppScope::class)
@Inject
class MetroPresenterFactory(
    private val appGraph: AppGraph,
): PresenterFactory {

    override fun <T : Presenter> create(modelClass: Class<T>, navController: NavController): T {
        val presenterGraph = createGraphFactory<PresenterGraph.Factory>()
            .create(appGraph, navController)

        println(presenterGraph.presenterProviders)

        val provider =
            presenterGraph.presenterProviders[modelClass.kotlin]
                ?: throw IllegalArgumentException("Unknown model class $modelClass")

        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        return modelClass.cast(provider())
    }
}

@Composable
inline fun <reified P : Presenter> metroPresenter(): P {
    val factory = LocalMetroPresenterFactory.current
    val navController = LocalNavController.current

    return remember(factory, navController) {
        logcat(tag = "metroPresenter") { "created factory ${P::class}" }
        factory.create(
            P::class.java,
            navController
        )
    }
}
