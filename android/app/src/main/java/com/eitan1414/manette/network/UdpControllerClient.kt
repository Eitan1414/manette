package com.eitan1414.manette.network

import com.eitan1414.manette.model.InputSnapshot
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class UdpControllerClient(
    private val onAck: (Int) -> Unit = {}
) : AutoCloseable {
    private val socket = DatagramSocket().apply { soTimeout = 250 }
    private val sender = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "manette-udp-send").apply { isDaemon = true }
    }
    private val receiver = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "manette-udp-recv").apply { isDaemon = true }
    }
    private val latest = AtomicReference(InputSnapshot())
    private val sequence = AtomicInteger(0)

    @Volatile
    private var target: InetSocketAddress? = null

    @Volatile
    private var channel: Int = 0

    init {
        receiver.execute(::receiveLoop)
        sender.scheduleAtFixedRate(::sendLatest, 0, 16, TimeUnit.MILLISECONDS)
    }

    fun configure(ip: String, channel: Int, port: Int = DEFAULT_PORT): Boolean {
        return runCatching {
            val address = InetAddress.getByName(ip.trim())
            this.channel = channel.coerceIn(0, 6)
            target = InetSocketAddress(address, port)
            true
        }.getOrDefault(false)
    }

    fun setChannel(channel: Int) {
        this.channel = channel.coerceIn(0, 6)
    }

    fun clearTarget() {
        target = null
    }

    fun update(snapshot: InputSnapshot) {
        latest.set(snapshot)
    }

    private fun sendLatest() {
        val destination = target ?: return
        val state = latest.get()
        val seq = sequence.getAndIncrement() and 0xFFFF

        val bytes = ByteBuffer.allocate(PACKET_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(MAGIC)
            .put(PROTOCOL_VERSION)
            .put(channel.toByte())
            .putShort(seq.toShort())
            .putInt(state.buttons)
            .putShort(state.leftX)
            .putShort(state.leftY)
            .putShort(state.rightX)
            .putShort(state.rightY)
            .array()

        runCatching {
            socket.send(DatagramPacket(bytes, bytes.size, destination))
        }
    }

    private fun receiveLoop() {
        val bytes = ByteArray(32)
        while (!socket.isClosed) {
            try {
                val packet = DatagramPacket(bytes, bytes.size)
                socket.receive(packet)
                if (packet.length != ACK_SIZE) continue

                val buffer = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
                    .order(ByteOrder.BIG_ENDIAN)
                val magic = buffer.int
                val version = buffer.get()
                val ackChannel = buffer.get().toInt() and 0xFF
                buffer.short // sequence, useful later for latency stats

                if (magic == ACK_MAGIC && version == PROTOCOL_VERSION && ackChannel in 0..6) {
                    onAck(ackChannel)
                }
            } catch (_: SocketTimeoutException) {
                // Expected: lets the loop re-check socket.isClosed regularly.
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                // Keep the control sender alive even if a malformed packet is received.
            }
        }
    }

    override fun close() {
        target = null
        sender.shutdownNow()
        socket.close()
        receiver.shutdownNow()
    }

    companion object {
        const val DEFAULT_PORT = 4405
        private const val MAGIC = 0x4D4E5450 // MNTP
        private const val ACK_MAGIC = 0x4D4E5441 // MNTA
        private const val PACKET_SIZE = 20
        private const val ACK_SIZE = 8
        private const val PROTOCOL_VERSION: Byte = 2
    }
}
