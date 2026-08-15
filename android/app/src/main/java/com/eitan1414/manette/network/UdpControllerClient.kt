package com.eitan1414.manette.network

import com.eitan1414.manette.model.InputSnapshot
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class UdpControllerClient : AutoCloseable {
    private val socket = DatagramSocket()
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "manette-udp").apply { isDaemon = true }
    }
    private val latest = AtomicReference(InputSnapshot())
    private val sequence = AtomicInteger(0)

    @Volatile
    private var target: InetSocketAddress? = null

    init {
        worker.scheduleAtFixedRate(::sendLatest, 0, 16, TimeUnit.MILLISECONDS)
    }

    fun configure(ip: String, port: Int = DEFAULT_PORT): Boolean {
        return runCatching {
            val address = InetAddress.getByName(ip.trim())
            target = InetSocketAddress(address, port)
            true
        }.getOrDefault(false)
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
            .put(0.toByte()) // player 1 / channel 0
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

    override fun close() {
        target = null
        worker.shutdownNow()
        socket.close()
    }

    companion object {
        const val DEFAULT_PORT = 4405
        private const val MAGIC = 0x4D4E5450 // MNTP
        private const val PACKET_SIZE = 20
        private const val PROTOCOL_VERSION: Byte = 1
    }
}
