package com.aslwiptv.data

import android.content.Context
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

enum class AppProxyType { HTTP, SOCKS }

data class ProxySettings(
    val enabled: Boolean = false,
    val type: AppProxyType = AppProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = ""
) {
    fun validate(): String? {
        if (!enabled) return null
        if (host.isBlank()) return "Inserisci l’indirizzo del proxy."
        if (port !in 1..65535) return "La porta deve essere compresa tra 1 e 65535."
        if (type == AppProxyType.SOCKS && (username.isNotBlank() || password.isNotBlank())) {
            return "L’autenticazione è disponibile solo per proxy HTTP."
        }
        return null
    }
}

/** Shared network stack for playlist imports, Xtream and Media3 playback. */
class AppNetwork(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("network", Context.MODE_PRIVATE)
    private val systemProxySelector = ProxySelector.getDefault()

    private val appProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val settings = proxySettings()
            if (!settings.enabled || settings.host.isBlank() || settings.port !in 1..65535) {
                return systemProxySelector?.select(uri) ?: listOf(Proxy.NO_PROXY)
            }
            val type = if (settings.type == AppProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
            return listOf(Proxy(type, InetSocketAddress(settings.host.trim(), settings.port)))
        }

        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
            if (!proxySettings().enabled) systemProxySelector?.connectFailed(uri, sa, ioe)
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(appProxySelector)
        .proxyAuthenticator(Authenticator { route, response ->
            val settings = proxySettings()
            val isHttpProxy = route?.proxy?.type() == Proxy.Type.HTTP
            if (!settings.enabled || !isHttpProxy || settings.username.isBlank() ||
                response.request.header("Proxy-Authorization") != null
            ) {
                null
            } else {
                response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(settings.username, settings.password))
                    .build()
            }
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** API/import requests are bounded; the streaming client intentionally has no total call timeout. */
    val apiClient: OkHttpClient = client.newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun proxySettings(): ProxySettings = ProxySettings(
        enabled = preferences.getBoolean("proxy_enabled", false),
        type = runCatching { AppProxyType.valueOf(preferences.getString("proxy_type", AppProxyType.HTTP.name)!!) }
            .getOrDefault(AppProxyType.HTTP),
        host = preferences.getString("proxy_host", "").orEmpty(),
        port = preferences.getInt("proxy_port", 8080),
        username = preferences.getString("proxy_username", "").orEmpty(),
        password = preferences.getString("proxy_password", "").orEmpty()
    )

    fun saveProxySettings(settings: ProxySettings) {
        require(settings.validate() == null) { settings.validate().orEmpty() }
        preferences.edit()
            .putBoolean("proxy_enabled", settings.enabled)
            .putString("proxy_type", settings.type.name)
            .putString("proxy_host", settings.host.trim())
            .putInt("proxy_port", settings.port)
            .putString("proxy_username", settings.username)
            .putString("proxy_password", settings.password)
            .apply()
    }

    suspend fun testConnectivity(): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://connectivitycheck.gstatic.com/generate_204")
                .header("Cache-Control", "no-cache")
                .build()
            client.newBuilder().callTimeout(20, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                check(response.code == 204) { "La verifica rete ha restituito HTTP ${response.code}." }
            }
            "Connessione Internet e instradamento proxy/VPN funzionanti."
        }
    }
}


