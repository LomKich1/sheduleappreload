package com.schedule.app.data.remote

import com.schedule.app.util.Logger
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val TAG = "NetworkTrust"

/**
 * ТОЛЬКО desktopMain, не commonMain: использует okhttp3/java.security/
 * javax.net.ssl напрямую — ни один из этих пакетов не существует на
 * Kotlin/Native (iOS). Если положить это в commonMain, сборка под iOS
 * (iosArm64/iosSimulatorArm64/iosX64) просто не скомпилируется.
 *
 * Фикс для случая "скачивание файлов не работает на ПК колледжа, хотя
 * GitHub вроде бы не заблокирован". Причина почти наверняка в том, что
 * учебный антивирус/файрвол делает HTTPS-инспекцию (подменяет TLS-сертификат
 * на лету, как это по умолчанию делает Kaspersky Endpoint Security и
 * подобные): Windows этому сертификату доверяет (он вписан в системное
 * хранилище), поэтому браузер видит GitHub нормально. А JVM — нет: у неё
 * СВОЙ truststore (cacerts), который про сторонний CA ничего не знает.
 * Результат — SSLHandshakeException: PKIX path building failed, при том что
 * сеть по факту ничего не блокирует.
 *
 * Фикс: на Windows у JVM есть спец-тип кейстора "Windows-ROOT", который
 * читает сертификаты прямо из системного хранилища — то же самое, чему
 * доверяет браузер (включая тот CA, что вписал антивирус). Собираем
 * TrustManager, который принимает сертификат, если его признаёт ЛИБО
 * обычный cacerts, ЛИБО Windows-хранилище — а не только оба сразу.
 *
 * На macOS/Linux (desktopMain собирается и под них тоже) — no-op:
 * "Windows-ROOT" там просто не существует (часть JDK-провайдера SunMSCAPI,
 * специфичного для Windows), и текущая проблема на этих платформах не
 * воспроизводилась.
 */
internal fun OkHttpClient.Builder.withWindowsTrustFallback(): OkHttpClient.Builder {
    val isWindows = System.getProperty("os.name")
        ?.contains("Windows", ignoreCase = true) == true
    if (!isWindows) return this

    return try {
        val windowsRoot = KeyStore.getInstance("Windows-ROOT").apply { load(null, null) }

        val windowsTm = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(windowsRoot) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        // null → обычный системный cacerts JVM, как если бы мы вообще
        // ничего не трогали — это дефолтное поведение OkHttp.
        val defaultTm = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        val combinedTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                defaultTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    defaultTm.checkServerTrusted(chain, authType)
                } catch (fromDefault: CertificateException) {
                    try {
                        windowsTm.checkServerTrusted(chain, authType)
                    } catch (_: CertificateException) {
                        // Ни один источник не признал сертификат валидным —
                        // кидаем исходную ошибку от cacerts, она информативнее.
                        throw fromDefault
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                defaultTm.acceptedIssuers + windowsTm.acceptedIssuers
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(combinedTm), SecureRandom())
        }

        Logger.d(TAG, "Windows-ROOT trust fallback подключен")
        sslSocketFactory(sslContext.socketFactory, combinedTm)
    } catch (e: Exception) {
        // Не должно падать само приложение из-за диагностического фикса —
        // если что-то пошло не так (странная сборка JVM без SunMSCAPI и т.п.),
        // просто едем дальше на обычном cacerts, как было раньше.
        Logger.e(TAG, "Не удалось подключить Windows-ROOT trust fallback", e)
        this
    }
}
