package ios.silv.gemini

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8LineTo
import ios.silv.core_android.log.logcat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory

internal fun buildTlsCertificateIfNeeded(certPEM: ByteArray, keyPEM: ByteArray): Result<KeyStore> {
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

@Throws(IOException::class)
suspend fun getHeader(conn: ByteReadChannel): Result<Header> = runCatching {
    val result = StringBuilder()
    val completed = conn.readUTF8LineTo(result, 4096)
    if (!completed)  {
        logcat("GeminiClient") { result.toString() }
        error("Header not formatted correctly")
    }

    val line = result.toString()
    logcat("GeminiClient") { line }
    val fields = line.split(" ")

    if (fields.size < 2) {
        error("Header not formatted correctly")
    }

    val status = fields[0].toIntOrNull() ?: error("Unexpected status value ${fields[0]}")

    val meta = if (line.length <= 3) "" else line.substring(fields[0].length + 1)

    if (meta.length > MetaMaxLength) {
        error("Meta string is too long")
    }

    Header(status, meta)
}
