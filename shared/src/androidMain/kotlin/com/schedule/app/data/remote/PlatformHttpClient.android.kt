package com.schedule.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

// Windows-ROOT trust fallback сюда не переносим — Android использует
// собственное системное хранилище доверенных сертификатов и с этой
// проблемой (PKIX path building failed за корпоративным HTTPS-инспектором)
// не сталкивался; специфика именно у JVM-truststore на Windows.
actual fun createPlatformHttpClient(
    connectTimeoutMs: Long,
    requestTimeoutMs: Long,
): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = connectTimeoutMs
        requestTimeoutMillis = requestTimeoutMs
    }
}
