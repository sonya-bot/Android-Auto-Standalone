package com.example.androidautoselfheadunit.aap.security

import android.annotation.SuppressLint
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@SuppressLint("TrustAllX509TrustManager")
class NoCheckTrustManager : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        // No check
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        // No check
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
