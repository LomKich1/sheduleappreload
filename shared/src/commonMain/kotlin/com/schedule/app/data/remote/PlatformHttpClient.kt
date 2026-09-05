package com.schedule.app.data.remote

import io.ktor.client.HttpClient

/**
 * Собирается через expect/actual, а не общим кодом в commonMain — потому что
 * тюнинг движка (Windows-ROOT trust fallback на desktop, см.
 * WindowsTrustFallback.kt) специфичен для конкретного Ktor-engine
 * (OkHttp на Android/desktop, Darwin на iOS), и это не выразить в общем коде
 * без явной привязки к типу engine.
 *
 * Таймауты — единственное, что везде настраивается одинаково (плагин
 * HttpTimeout из ktor-client-core портативен, работает на всех таргетах,
 * включая iOS, без привязки к engine) — поэтому они передаются параметрами,
 * а не зашиваются в каждый actual по отдельности.
 *
 * connectTimeoutMs/requestTimeoutMs — те же значения, что раньше стояли явно
 * в OkHttpClient.Builder у GitHubApi/YandexDiskApi (12s/60s и 12s/30s
 * соответственно) — при переезде на голый Ktor HttpClient() эти таймауты
 * молча потерялись (у Ktor без плагина HttpTimeout таймаутов нет вообще,
 * запрос может зависнуть навсегда).
 */
expect fun createPlatformHttpClient(
    connectTimeoutMs: Long,
    requestTimeoutMs: Long,
): HttpClient
