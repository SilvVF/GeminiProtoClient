package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.ktor.http.Url
import io.ktor.network.tls.TLSConfigBuilder
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
internal actual fun TLSConfigBuilder.applyPlatformTofuConfig(
    url: Url,
    store: DataStore<Preferences>
) {
    val host: String = url.host
    val port: Int = url.port

}
