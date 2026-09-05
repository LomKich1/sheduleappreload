package com.schedule.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

actual fun createPlatformHttpClient(
    connectTimeoutMs: Long,
    requestTimeoutMs: Long,
): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = connectTimeoutMs
        requestTimeoutMillis = requestTimeoutMs
    }
    engine {
        // withWindowsTrustFallback — no-op на macOS/Linux (см. её же
        // комментарий в WindowsTrustFallback.kt), реально работает только
        // на Windows.
        config {
            withWindowsTrustFallback()
        }
    }
}
