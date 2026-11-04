package com.drmagic.ringtonerecorder

import java.io.File
import java.io.RandomAccessFile

/** Minimal 16-bit PCM WAV helpers. */
object WavWriter {
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2

    fun writeHeader(raf: RandomAccessFile, dataLen: Int, rate: Int = SAMPLE_RATE, ch: Int = CHANNELS) {
        val byteRate = rate * ch * 2
        raf.seek(0)
        fun i(v: Int) { raf.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())) }
        fun s(v: Int) { raf.write(byteArrayOf(v.toByte(), (v shr 8).toByte())) }
        raf.writeBytes("RIFF"); i(36 + dataLen); raf.writeBytes("WAVE")
        raf.writeBytes("fmt "); i(16); s(1); s(ch); i(rate); i(byteRate); s(ch * 2); s(16)
        raf.writeBytes("data"); i(dataLen)
    }

    /** Copy [startMs, endMs) of a WAV written by this app into a new WAV file. */
    fun trim(src: File, dst: File, startMs: Long, endMs: Long) {
        val frame = CHANNELS * 2
        val bytesPerMs = SAMPLE_RATE * frame / 1000.0
        val total = (src.length() - 44).toInt()
        var start = ((startMs * bytesPerMs).toInt() / frame) * frame
        var end = ((endMs * bytesPerMs).toInt() / frame) * frame
        start = start.coerceIn(0, total); end = end.coerceIn(start, total)
        RandomAccessFile(src, "r").use { input ->
            RandomAccessFile(dst, "rw").use { out ->
                out.setLength(0)
                writeHeader(out, end - start)
                input.seek(44L + start)
                val buf = ByteArray(8192); var left = end - start
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size, left)); if (n <= 0) break
                    out.write(buf, 0, n); left -= n
                }
            }
        }
    }

    fun durationMs(f: File): Long = ((f.length() - 44).coerceAtLeast(0) * 1000L) / (SAMPLE_RATE * CHANNELS * 2)
}
