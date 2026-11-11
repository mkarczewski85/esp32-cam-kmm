package com.example.esp32cam.camera

class MjpegParser(private val onFrame: (ByteArray) -> Unit) {

    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    fun feed(data: ByteArray, length: Int) {
        if (length <= 0) return
        ensureCapacity(size + length)
        data.copyInto(buffer, size, 0, length)
        size += length
        extractFrames()
    }

    fun reset() {
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var newCapacity = buffer.size
        while (newCapacity < required) newCapacity *= 2
        buffer = buffer.copyOf(newCapacity)
    }

    private fun extractFrames() {
        while (true) {
            val soi = indexOf(SOI, from = 0)
            if (soi == -1) {
                size = 0
                return
            }
            val eoi = indexOf(EOI, from = soi + SOI.size)
            if (eoi == -1) {
                if (soi > 0) {
                    buffer.copyInto(buffer, 0, soi, size)
                    size -= soi
                }
                return
            }
            val frameEnd = eoi + EOI.size
            onFrame(buffer.copyOfRange(soi, frameEnd))
            buffer.copyInto(buffer, 0, frameEnd, size)
            size -= frameEnd
        }
    }

    private fun indexOf(marker: ByteArray, from: Int): Int {
        var i = from
        while (i + marker.size <= size) {
            var matched = true
            for (j in marker.indices) {
                if (buffer[i + j] != marker[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
            i++
        }
        return -1
    }

    private companion object {
        const val INITIAL_CAPACITY = 256 * 1024
        val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }
}
