package ios.silv.libgemini.gemini

import io.ktor.http.Url
import io.ktor.network.tls.TLSConfigBuilder

internal actual fun TLSConfigBuilder.applyPlatformTlsConfig(
    url: Url,
    port: Int,
    certPEM: ByteArray,
    keyPEM: ByteArray
) {

}