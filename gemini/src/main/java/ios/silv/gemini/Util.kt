package ios.silv.gemini

import ios.silv.core_android.log.logcat
import kotlinx.io.Source
import kotlinx.io.readLineStrict
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
internal fun consumeHeader(source: Source): Result<Header> = runCatching {
    val line =  source.readLineStrict(4096)

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
