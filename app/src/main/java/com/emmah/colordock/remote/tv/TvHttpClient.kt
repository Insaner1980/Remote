package com.emmah.colordock.remote.tv

import android.net.Network
import okhttp3.OkHttpClient
import javax.net.ssl.SSLContext

internal fun tvHttpClient(
    network: Network,
    target: TvTarget,
    trust: TvCertificateTrust,
): OkHttpClient {
    val tls = SSLContext.getInstance("TLSv1.3").apply { init(null, arrayOf(trust), null) }
    return OkHttpClient
        .Builder()
        .socketFactory(network.socketFactory)
        .sslSocketFactory(tls.socketFactory, trust)
        .hostnameVerifier { hostname, ssl ->
            target.matchesHost(hostname) &&
                runCatching {
                    TvCertificateTrust.fingerprint(ssl.peerCertificates[0].encoded) == trust.fingerprint
                }.getOrDefault(false)
        }.followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
}
