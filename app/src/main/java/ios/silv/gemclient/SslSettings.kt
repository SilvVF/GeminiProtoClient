package ios.silv.gemclient

import android.content.Context
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SslSettings {

    private val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

    fun configure(context: Context) {
//        context.assets.open("keystore.bks").use { keyStoreFile ->
//            keyStore.load(keyStoreFile, BuildConfig.KEYSTORE_PASSWORD.toCharArray())
//        }
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

        return object : X509TrustManager {
            override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
            }

            override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return emptyArray()
            }

        }
        //return getTrustManagerFactory()?.trustManagers?.first { it is X509TrustManager } as X509TrustManager
    }
}