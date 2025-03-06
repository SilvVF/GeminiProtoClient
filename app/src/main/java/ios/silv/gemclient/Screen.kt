package ios.silv.gemclient

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.core.DefaultComposeKey
import com.zhuinden.simplestackextensions.services.DefaultServiceProvider
import kotlinx.parcelize.Parcelize

abstract class Screen : DefaultComposeKey(), Parcelable, DefaultServiceProvider.HasServices {

    override val saveableStateProviderKey: Any get() = this

    override fun getScopeTag(): String = toString()

    override fun bindServices(serviceBinder: ServiceBinder) {}
}