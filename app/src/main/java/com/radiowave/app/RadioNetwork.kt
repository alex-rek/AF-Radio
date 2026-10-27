package com.radiowave.app

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal data class ProxyConfig(
    val type: String = "off", val host: String = "", val port: String = "8080",
    val username: String = "", val password: String = ""
) {
    fun isValid(): Boolean = type == "off" ||
        (type in setOf("http", "socks5") && host.isNotBlank() &&
            host.none { it.isWhitespace() || it in "/@?#" } &&
            port.toIntOrNull() in 1..65535 &&
            (type != "socks5" || (username.isEmpty() && password.isEmpty()) ||
                (username.toByteArray().size in 1..255 && password.toByteArray().size in 1..255)))

    // Deliberately omit credentials from diagnostics.
    override fun toString() = "ProxyConfig(type=$type)"
}

internal object RadioNetwork {
    @Volatile private var preferences: SharedPreferences? = null
    private var cachedConfig: ProxyConfig? = null
    private var cachedClient: OkHttpClient? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("af_radio_proxy", Context.MODE_PRIVATE)
    }
    fun config(): ProxyConfig {
        val prefs = preferences ?: return ProxyConfig()
        return ProxyConfig(prefs.getString("type", "off")!!, prefs.getString("host", "")!!,
            prefs.getString("port", "8080")!!, prefs.getString("username", "")!!, prefs.getString("password", "")!!)
    }
    fun save(config: ProxyConfig) {
        require(config.isValid())
        requireNotNull(preferences).edit()
            .putString("type", config.type).putString("host", config.host).putString("port", config.port)
            .putString("username", config.username).putString("password", config.password).apply()
    }
    @Synchronized fun client(): OkHttpClient {
        val config = config()
        if (cachedConfig != config) {
            cachedClient?.connectionPool?.evictAll()
            cachedClient = buildClient(config)
            cachedConfig = config
        }
        return requireNotNull(cachedClient)
    }

    internal fun buildClient(config: ProxyConfig): OkHttpClient {
        require(config.isValid())
        val builder = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).followSslRedirects(true)
        when (config.type) {
            "http" -> {
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port.toInt())))
                if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
                    builder.proxyAuthenticator { _, response ->
                        if (response.request.header("Proxy-Authorization") != null) null
                        else response.request.newBuilder().header("Proxy-Authorization",
                            Credentials.basic(config.username, config.password)).build()
                    }
                }
            }
            "socks5" -> {
                builder.proxy(Proxy.NO_PROXY)
                builder.socketFactory(Socks5SocketFactory(config.host, config.port.toInt(), config.username, config.password))
                // Keep destination names for the SOCKS handshake; resolve them at the proxy.
                builder.dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 1)))
                })
            }
        }
        return builder.build()
    }
}
