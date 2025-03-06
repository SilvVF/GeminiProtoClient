package ios.silv.gemclient

import android.content.Context
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SslSettings {

    private val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

    fun configure(context: Context) {
        context.assets.open("keystore.bks").use { keyStoreFile ->
            keyStore.load(keyStoreFile, BuildConfig.KEYSTORE_PASSWORD.toCharArray())
        }
    }

    private fun getTrustManagerFactory(): TrustManagerFactory? {
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
    }

    fun getSslContext(): SSLContext? {
        return SSLContext.getInstance("TLS").apply {
            init(null, getTrustManagerFactory()?.trustManagers, null)
        }
    }

    fun getTrustManager(): X509TrustManager {
        return getTrustManagerFactory()?.trustManagers?.first { it is X509TrustManager } as X509TrustManager
    }
}