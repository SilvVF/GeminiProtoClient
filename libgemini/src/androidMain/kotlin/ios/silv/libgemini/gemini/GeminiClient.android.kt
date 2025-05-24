package ios.silv.libgemini.gemini

import android.annotation.SuppressLint
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.http.Url
import io.ktor.network.tls.TLSConfigBuilder
import ios.silv.core.logcat.logcat
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.X509TrustManager
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

private fun X509Certificate.sha256PublicKeyFingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(publicKey.encoded)
    return hash.joinToString(":") { "%02X".format(it) }
}

@SuppressLint("CustomX509TrustManager")
@OptIn(ExperimentalTime::class)
private fun createTofuTrustManager(
    store: DataStore<Preferences>,
    host: String,
    port: String
): X509TrustManager {
    return object : X509TrustManager {

        private val fingerprintKey = stringPreferencesKey("fingerprint_$host:$port")
        private val expiryKey = longPreferencesKey("expiry_$host:$port")

        private fun loadStoredFingerprint() = runBlocking {
            store.data.map { it[fingerprintKey] }.firstOrNull()
        }

        private fun loadExpiration() = runBlocking {
            store.data.map { it[expiryKey] }.firstOrNull() ?: -1
        }

        private fun setExpiration(expiry: Date?) = runBlocking {
            store.edit {
                it[expiryKey] = expiry?.toInstant()?.epochSecond ?: -1
            }
        }

        private fun setStoredFingerprint(fingerprint: String) = runBlocking {
            store.edit {
                it[fingerprintKey] = fingerprint
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            throw UnsupportedOperationException("Client trust not supported")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            logcat { "checking server trust $host:$port chain = ${chain.size}" }
            val cert = chain.getOrNull(0)
                ?:throw CertificateException("no certificate found for $host:$port")

            val fingerprint = cert.sha256PublicKeyFingerprint()
            val storedFingerprint = loadStoredFingerprint()
            val storedExpiration = loadExpiration()

            val epochSeconds = System.currentTimeMillis() / 1000

            if (storedFingerprint == null || epochSeconds > storedExpiration) {
                logcat { "First time connecting, trusting fingerprint: $fingerprint" }
                setStoredFingerprint(fingerprint)
                setExpiration(cert.notAfter)
            } else if (storedFingerprint != fingerprint) {
                logcat { "server not trusted $host:$port" }
                throw CertificateException("Server certificate fingerprint mismatch")
            } else {
                logcat { "verified server $host:$port expiresAt=$epochSeconds" }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }
    }
}

internal actual fun TLSConfigBuilder.applyPlatformTofuConfig(url: Url, store: DataStore<Preferences>) {
    this.random = SecureRandom()
    this.trustManager = createTofuTrustManager(store, url.host, "${url.port}")
}