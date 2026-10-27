package com.radiowave.app

import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/** RFC 1928/1929 tunnel with remote DNS; no process-wide authenticator or DNS changes. */
internal class Socks5SocketFactory(
    private val host: String, private val port: Int,
    private val username: String, private val password: String
) : SocketFactory() {
    override fun createSocket(): Socket = object : Socket() {
        override fun connect(endpoint: SocketAddress, timeout: Int) {
            val target = endpoint as InetSocketAddress
            try {
                super.connect(InetSocketAddress(this@Socks5SocketFactory.host, this@Socks5SocketFactory.port), timeout)
                val previousTimeout = soTimeout
                soTimeout = if (timeout > 0) timeout else 10_000
                val input = DataInputStream(getInputStream())
                val output = getOutputStream()
                val auth = username.isNotEmpty() || password.isNotEmpty()
                output.write(byteArrayOf(5, 1, if (auth) 2 else 0)); output.flush()
                if (input.readUnsignedByte() != 5 || input.readUnsignedByte() != if (auth) 2 else 0)
                    throw IOException("SOCKS5 authentication method rejected")
                if (auth) {
                    val user = username.toByteArray(Charsets.UTF_8)
                    val pass = password.toByteArray(Charsets.UTF_8)
                    require(user.size in 1..255 && pass.size in 1..255)
                    output.write(byteArrayOf(1, user.size.toByte())); output.write(user)
                    output.write(pass.size); output.write(pass); output.flush()
                    if (input.readUnsignedByte() != 1 || input.readUnsignedByte() != 0)
                        throw IOException("SOCKS5 authentication failed")
                }
                val name = target.hostString.toByteArray(Charsets.UTF_8)
                require(name.size in 1..255)
                output.write(byteArrayOf(5, 1, 0, 3, name.size.toByte())); output.write(name)
                output.write(target.port ushr 8); output.write(target.port and 255); output.flush()
                if (input.readUnsignedByte() != 5) throw IOException("Invalid SOCKS5 response")
                val status = input.readUnsignedByte()
                if (status != 0) throw IOException("SOCKS5 connect failed ($status)")
                if (input.readUnsignedByte() != 0) throw IOException("Invalid SOCKS5 response")
                val addressLength = when (input.readUnsignedByte()) {
                    1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte()
                    else -> throw IOException("Invalid SOCKS5 address")
                }
                input.readFully(ByteArray(addressLength + 2))
                soTimeout = previousTimeout
            } catch (error: Exception) { close(); throw error }
        }
        override fun connect(endpoint: SocketAddress) = connect(endpoint, 10_000)
    }
    override fun createSocket(host: String, port: Int): Socket = createSocket().apply {
        connect(InetSocketAddress.createUnresolved(host, port))
    }
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
        createSocket().apply { bind(InetSocketAddress(local, localPort)); connect(InetSocketAddress.createUnresolved(host, port)) }
    override fun createSocket(host: InetAddress, port: Int): Socket = createSocket(host.hostAddress!!, port)
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
        createSocket(host.hostAddress!!, port, local, localPort)
}
