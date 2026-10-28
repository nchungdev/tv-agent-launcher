package com.gathin.tvhome.feature.main.data

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher


class LocalAdb(context: Context) {

    class OffException(cause: Throwable?) : IOException("adb is not listening on port $PORT", cause)

    class NotAllowedException : IOException("the TV did not allow this key")

    fun interface Output {
        @Throws(IOException::class)
        fun write(chunk: ByteArray)
    }

    private val context: Context = context.applicationContext

    @Throws(IOException::class)
    fun shell(command: String, mayPrompt: Boolean = true): String {
        val output = ByteArrayOutputStream()
        Session().use { session ->
            session.run(command, mayPrompt) { chunk -> output.write(chunk) }
        }
        return output.toString("UTF-8")
    }

    inner class Session : Closeable {
        private val socket = Socket()

        @Throws(IOException::class)
        fun run(command: String, mayPrompt: Boolean, output: Output) {
            try {
                socket.connect(InetSocketAddress("127.0.0.1", PORT), CONNECT_MS)
            } catch (e: IOException) {
                throw OffException(e)
            }
            socket.tcpNoDelay = true
            socket.soTimeout = REPLY_MS
            val `in` = DataInputStream(socket.getInputStream())
            val out = socket.getOutputStream()
            signIn(socket, `in`, out, mayPrompt)
            send(out, OPEN, 1, 0, ("shell:$command\u0000").toByteArray(StandardCharsets.UTF_8))
            var remote = 0
            while (true) {
                val m = read(`in`)
                if (m.command == OKAY) {
                    remote = m.arg0
                } else if (m.command == WRTE) {
                    output.write(m.data)
                    send(out, OKAY, 1, m.arg0, ByteArray(0))
                } else if (m.command == CLSE) {
                    if (remote != 0) send(out, CLSE, 1, remote, ByteArray(0))
                    return
                }
            }
        }

        override fun close() {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun signIn(s: Socket, `in`: DataInputStream, out: OutputStream, mayPrompt: Boolean) {
        val key = key()
        send(out, CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray(StandardCharsets.US_ASCII))
        var signed = false
        while (true) {
            val m = read(`in`)
            if (m.command == CNXN) return
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw IOException("unexpected adb reply")
            if (!signed) {
                send(out, AUTH, AUTH_SIGNATURE, 0, sign(key.private, m.data))
                signed = true
            } else {
                if (!mayPrompt) throw NotAllowedException()
                send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(key.public as RSAPublicKey))
                s.soTimeout = PROMPT_MS
                try {
                    val reply = read(`in`)
                    if (reply.command != CNXN) throw NotAllowedException()
                } catch (_: SocketTimeoutException) {
                    throw NotAllowedException()
                } catch (_: EOFException) {
                    throw NotAllowedException()
                }
                s.soTimeout = REPLY_MS
                return
            }
        }
    }

    private var cachedKey: KeyPair? = null

    @Synchronized
    @Throws(IOException::class)
    private fun key(): KeyPair {
        cachedKey?.let { return it }
        val priv = File(context.filesDir, "adbkey")
        val pub = File(context.filesDir, "adbkey.pub")
        return try {
            val rsa = KeyFactory.getInstance("RSA")
            if (priv.isFile && pub.isFile) {
                val k = KeyPair(
                    rsa.generatePublic(X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                    rsa.generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath())))
                )
                cachedKey = k
                k
            } else {
                val gen = KeyPairGenerator.getInstance("RSA")
                gen.initialize(KEY_BITS)
                val pair = gen.generateKeyPair()
                write(priv, pair.private.encoded)
                write(pub, pair.public.encoded)
                cachedKey = pair
                pair
            }
        } catch (e: GeneralSecurityException) {
            throw IOException(e)
        }
    }

    private class Message(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data: ByteArray
    )

    companion object {
        const val PORT = 5555
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val VERSION = 0x01000001
        private const val MAX_DATA = 256 * 1024
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private const val CONNECT_MS = 2000
        private const val REPLY_MS = 10_000
        const val PROMPT_MS = 60_000
        private const val KEY_BITS = 2048
        private val SHA1_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14
        )

        private fun write(file: File, bytes: ByteArray) {
            FileOutputStream(file).use { out -> out.write(bytes) }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun sign(key: PrivateKey, token: ByteArray): ByteArray {
            val size = KEY_BITS / 8
            val block = ByteArray(size)
            val tail = SHA1_PREFIX.size + token.size
            block[1] = 0x01
            for (i in 2 until size - tail - 1) block[i] = 0xff.toByte()
            System.arraycopy(SHA1_PREFIX, 0, block, size - tail, SHA1_PREFIX.size)
            System.arraycopy(token, 0, block, size - token.size, token.size)
            return try {
                val c = Cipher.getInstance("RSA/ECB/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key)
                c.doFinal(block)
            } catch (e: GeneralSecurityException) {
                throw IOException(e)
            }
        }

        @JvmStatic
        fun publicKey(key: RSAPublicKey): ByteArray {
            val words = KEY_BITS / 32
            val n = key.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = r32.subtract(n.mod(r32).modInverse(r32))
            val rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(n)
            val b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(words)
            b.putInt(n0inv.toInt())
            putLittleEndian(b, n, words * 4)
            putLittleEndian(b, rr, words * 4)
            b.putInt(key.publicExponent.toInt())
            val text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tvhome@launcher\u0000"
            return text.toByteArray(StandardCharsets.US_ASCII)
        }

        private fun putLittleEndian(b: ByteBuffer, value: BigInteger, length: Int) {
            val big = value.toByteArray()
            for (i in 0 until length) {
                val at = big.size - 1 - i
                b.put(if (at >= 0) big[at] else 0)
            }
        }

        private fun send(out: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray) {
            val h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            var sum = 0
            for (x in data) sum += x.toInt() and 0xff
            h.putInt(command).putInt(arg0).putInt(arg1).putInt(data.size).putInt(sum).putInt(command.inv())
            out.write(h.array())
            out.write(data)
            out.flush()
        }

        private fun read(`in`: DataInputStream): Message {
            val header = ByteArray(24)
            `in`.readFully(header)
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = h.getInt()
            val arg0 = h.getInt()
            val arg1 = h.getInt()
            val length = h.getInt()
            if (length < 0 || length > MAX_DATA) throw IOException("bad adb message")
            val data = ByteArray(length)
            `in`.readFully(data)
            return Message(command, arg0, arg1, data)
        }
    }
}
