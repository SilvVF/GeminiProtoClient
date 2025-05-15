package ios.silv.libgemini.gemini

import io.ktor.http.Url
import io.ktor.network.tls.TLSConfigBuilder
import io.ktor.network.tls.addKeyStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

private fun buildTlsCertificateIfNeeded(certPEM: ByteArray, keyPEM: ByteArray): Result<KeyStore> {
    return runCatching {

        if (certPEM.isEmpty() || keyPEM.isEmpty()) {
            error("byte array was empty when trying to build cert")
        }

        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(ByteArrayInputStream(certPEM))

        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val privateKey: PrivateKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(keyPEM))


        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null) // Initialize an empty keystore
        keyStore.setCertificateEntry("certificate", cert)
        keyStore.setKeyEntry("privateKey", privateKey, null, arrayOf(cert))

        keyStore
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
}

internal actual fun TLSConfigBuilder.applyPlatformTlsConfig(
    url: Url,
    port: Int,
    certPEM: ByteArray,
    keyPEM: ByteArray
) {
    random = SecureRandom()
    val keystore = buildTlsCertificateIfNeeded(certPEM, keyPEM).getOrNull()
    if (keystore != null) {
        addKeyStore(keystore, null, "certificate")
    } else {
        trustManager = getTrustManager()
    }
}