package com.phonelink.app

import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Port used for Wi-Fi / hotspot connections. */
const val PORT = 48500

/** How a phone is connected to us. */
enum class Kind { WIFI, BT, NET }

/** Frame types of the PhoneLink wire protocol (same for Wi-Fi, Bluetooth and internet). */
object FT {
    const val HELLO = 1
    const val CHAT = 2
    const val FILE_META = 3
    const val FILE_ACCEPT = 4
    const val FILE_CHUNK = 5
    const val FILE_END = 6
    const val CALL = 7
    const val AUDIO = 8
    /** Live system / mic audio stream (sender broadcasts, receiver plays). Requires mic permission on sender. */
    const val SYS_AUDIO = 9
    /** Keep-alive / reconnect hint. */
    const val PING = 10
}

/** Frame = [type:1][length:4][payload]. */
object Frames {
    private const val MAX = 4 * 1024 * 1024

    fun encode(type: Int, payload: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(5 + payload.size)
        bb.put(type.toByte())
        bb.putInt(payload.size)
        bb.put(payload)
        return bb.array()
    }

    @Throws(IOException::class)
    fun read(input: DataInputStream): Pair<Int, ByteArray> {
        val type = input.readUnsignedByte()
        val len = input.readInt()
        if (len < 0 || len > MAX) throw IOException("bad frame length $len")
        val buf = ByteArray(len)
        input.readFully(buf)
        return type to buf
    }

    /** Decodes a frame that is already fully in memory (internet relay). */
    fun decode(bytes: ByteArray, offset: Int): Pair<Int, ByteArray>? {
        if (bytes.size - offset < 5) return null
        val bb = ByteBuffer.wrap(bytes, offset, bytes.size - offset)
        val type = bb.get().toInt() and 0xFF
        val len = bb.int
        if (len < 0 || len > bytes.size - offset - 5) return null
        val p = ByteArray(len)
        bb.get(p)
        return type to p
    }
}

/** One connected remote phone, over any transport. */
class Peer(
    val kind: Kind,
    /** IP address (Wi-Fi), MAC address (Bluetooth) or remote ID (internet). */
    val address: String,
    private val raw: (ByteArray) -> Unit,
    private val closer: () -> Unit
) {
    @Volatile var id = ""
    @Volatile var name = address
    @Volatile var registered = false
    @Volatile var closed = false

    val key: String get() = "${kind.name}:$address"

    @Throws(IOException::class)
    fun send(type: Int, payload: ByteArray) {
        if (closed) throw IOException("closed")
        try {
            raw(Frames.encode(type, payload))
        } catch (e: IOException) {
            close()
            throw e
        }
    }

    fun trySend(type: Int, payload: ByteArray): Boolean =
        try { send(type, payload); true } catch (e: IOException) { false }

    fun close() {
        if (closed) return
        closed = true
        try { closer() } catch (_: Exception) {}
    }
}

// ---------- UI models ----------

data class Discovered(val name: String, val host: String, val port: Int)
data class BtDev(val name: String, val mac: String)
data class PeerInfo(val key: String, val name: String, val kind: Kind, val address: String)

/** A chat entry: text, file or voice message. */
data class Msg(
    val id: Long,
    val peerKey: String,
    val mine: Boolean,
    val kind: String,            // "text" | "file" | "voice"
    val text: String = "",       // message text or file name
    val fid: Int = 0,
    val size: Long = 0,
    val done: Long = 0,
    val status: String = "",     // waiting | offer | sending | receiving | done | declined | failed
    val uri: String? = null,
    val time: Long = System.currentTimeMillis()
)
