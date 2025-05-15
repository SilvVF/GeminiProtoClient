package ios.silv.shared.di

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

abstract class PresenterScope private constructor()

/** A [MapKey] annotation for binding ViewModels in a multibinding map. */
@MapKey
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PresenterKey(val value: KClass<out Presenter>)

@Immutable
interface Presenter