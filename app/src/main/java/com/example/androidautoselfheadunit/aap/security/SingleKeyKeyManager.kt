package com.example.androidautoselfheadunit.aap.security

import android.content.Context
import android.util.Base64
import com.example.androidautoselfheadunit.R
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

internal class SingleKeyKeyManager(context: Context) : X509ExtendedKeyManager() {
    private val alias = "android-auto-head-unit"
    private val delegate: X509KeyManager

    init {
        val certificate =
            context.resources.openRawResource(R.raw.cert).use { stream ->
                CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
            }
        val privateKey = loadPrivateKey(context)
        val keyStore =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setKeyEntry(alias, privateKey, charArrayOf(), arrayOf(certificate))
            }
        val factory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, charArrayOf())
            }
        delegate = factory.keyManagers.filterIsInstance<X509KeyManager>().first()
    }

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String = alias

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String = alias

    override fun getCertificateChain(requestedAlias: String?): Array<X509Certificate> =
        delegate.getCertificateChain(alias)

    override fun getPrivateKey(requestedAlias: String?): PrivateKey = delegate.getPrivateKey(alias)

    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String> =
        arrayOf(alias)

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = null

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String>? = null

    private fun loadPrivateKey(context: Context): PrivateKey {
        val pem = context.resources.openRawResource(R.raw.privkey).bufferedReader().use { it.readText() }
        val encoded =
            pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .filterNot(Char::isWhitespace)
        val spec = PKCS8EncodedKeySpec(Base64.decode(encoded, Base64.DEFAULT))
        return KeyFactory.getInstance("RSA").generatePrivate(spec)
    }
}
