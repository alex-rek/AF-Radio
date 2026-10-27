package com.radiowave.app

import java.io.DataInputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ProxyNetworkTest {
    @Test fun validatesProxyAndSocksCredentials() {
        assertTrue(ProxyConfig().isValid())
        assertFalse(ProxyConfig("http", "http://localhost", "8080").isValid())
        assertFalse(ProxyConfig("http", "localhost", "0").isValid())
        assertFalse(ProxyConfig("socks5", "localhost", "1080", "user", "").isValid())
        assertTrue(ProxyConfig("socks5", "localhost", "1080", "user", "pass").isValid())
        assertFalse(ProxyConfig("socks5", "localhost", "1080", "u".repeat(256), "pass").isValid())
        assertFalse(ProxyConfig(password = "secret").toString().contains("secret"))
    }

    @Test fun socksAuthenticatesAndResolvesDestinationRemotely() = socksRequest(false)
    @Test fun socksRejectsBadCredentialsWithoutSendingRequest() = socksRequest(true)

    private fun socksRequest(reject: Boolean) {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            val job = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = DataInputStream(socket.getInputStream())
                    val output = socket.getOutputStream()
                    assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte())
                    assertEquals(2, input.readUnsignedByte())
                    output.write(byteArrayOf(5, 2)); output.flush()
                    assertEquals(1, input.readUnsignedByte())
                    fun field(): String = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.UTF_8)
                    assertEquals("test-user", field()); assertEquals("test-password", field())
                    output.write(byteArrayOf(1, if (reject) 1 else 0)); output.flush()
                    if (!reject) {
                        assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte())
                        assertEquals(0, input.readUnsignedByte()); assertEquals(3, input.readUnsignedByte())
                        assertEquals("only-at-proxy.invalid", field()); assertEquals(80, input.readUnsignedShort())
                        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80)); output.flush()
                        val reader = input.bufferedReader()
                        assertEquals("GET /radio HTTP/1.1", reader.readLine())
                        while (!reader.readLine().isNullOrEmpty()) { }
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nradio".toByteArray())
                        output.flush()
                    }
                }
            }
            val client = RadioNetwork.buildClient(ProxyConfig("socks5", "127.0.0.1", server.localPort.toString(), "test-user", "test-password"))
            try {
                try {
                    client.newCall(Request.Builder().url("http://only-at-proxy.invalid/radio").build()).execute().use {
                        assertFalse(reject); assertEquals("radio", it.body!!.string())
                    }
                    assertFalse(reject)
                } catch (error: java.io.IOException) { if (!reject) throw error }
                job.get(8, TimeUnit.SECONDS)
            } finally { client.connectionPool.evictAll(); executor.shutdownNow() }
        }
    }

    @Test fun suppliedProxyHandlesHttpHttpsCatalogAndAudio() {
        val host = System.getenv("AF_TEST_PROXY_HOST")
        assumeTrue(!host.isNullOrBlank())
        for ((type, port) in listOf("http" to "8080", "socks5" to "1080")) {
            val config = ProxyConfig(type, host!!, port, System.getenv("AF_TEST_PROXY_USER").orEmpty(), System.getenv("AF_TEST_PROXY_PASSWORD").orEmpty())
            val client = RadioNetwork.buildClient(config)
            try {
                for (url in listOf("http://example.com/", "https://api.github.com/repos/alex-rek/AF-Radio/releases/latest",
                    "https://de1.api.radio-browser.info/json/stations/search?limit=1&hidebroken=true",
                    "https://stream.radioparadise.com/mp3-192")) {
                    client.newCall(Request.Builder().url(url).header("User-Agent", "AF-Radio-Test").build()).execute().use {
                        assertTrue("$type: ${it.code}", it.isSuccessful)
                        assertTrue(it.body!!.byteStream().read() >= 0)
                    }
                }
            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
        }
    }
}
