package com.drmagic.ringtonerecorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decode a user-picked audio file (mp3, m4a, wav, ogg, and other device-supported types) to the app's 44.1 kHz 16-bit stereo WAV. */
object AudioImporter {
    class ImportException(val messageRes: Int) : Exception()

    private const val MAX_DURATION_MS = 5 * 60 * 1000
    private const val TIMEOUT_US = 10_000L

    fun importToWav(context: Context, uri: Uri, dest: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (_: Exception) {
            extractor.release()
            throw ImportException(R.string.import_failed)
        }
        try {
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw ImportException(R.string.import_no_audio)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw ImportException(R.string.import_unsupported)
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val dur = format.getLong(MediaFormat.KEY_DURATION) / 1000
                if (dur > MAX_DURATION_MS) throw ImportException(R.string.import_too_long)
            }
            val pcm = File(context.cacheDir, "import-pcm.tmp")
            try {
                val meta = if (mime == MediaFormat.MIMETYPE_AUDIO_RAW || mime == "audio/raw") {
                    extractRaw(extractor, format, pcm)
                } else {
                    decode(extractor, format, mime, pcm)
                }
                writeWav(pcm, dest, meta.inRate, meta.inCh)
            } finally {
                pcm.delete()
            }
        } finally {
            extractor.release()
        }
    }

    private data class PcmMeta(val inRate: Int, val inCh: Int, val bytesPerSample: Int)

    private fun extractRaw(extractor: MediaExtractor, format: MediaFormat, pcm: File): PcmMeta {
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
            format.getInteger(MediaFormat.KEY_PCM_ENCODING) else android.media.AudioFormat.ENCODING_PCM_16BIT
        val bps = bytesPerSample(encoding)
        RandomAccessFile(pcm, "rw").use { out ->
            out.setLength(0)
            val buf = ByteBuffer.allocate(64 * 1024)
            var total = 0L
            val maxBytes = maxPcmBytes(rate, ch, bps)
            while (true) {
                buf.clear()
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) break
                val bytes = ByteArray(n)
                buf.position(0); buf.get(bytes)
                val converted = toLittle16(bytes, encoding)
                out.write(converted)
                total += converted.size
                if (total > maxBytes) throw ImportException(R.string.import_too_long)
                extractor.advance()
            }
        }
        if (pcm.length() < 4) throw ImportException(R.string.import_no_audio)
        return PcmMeta(rate, ch, 2)
    }

    private fun decode(extractor: MediaExtractor, format: MediaFormat, mime: String, pcm: File): PcmMeta {
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (_: Exception) {
            throw ImportException(R.string.import_unsupported)
        }
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            var outRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else WavWriter.SAMPLE_RATE
            var outCh = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sawFormat = false
            RandomAccessFile(pcm, "rw").use { out ->
                out.setLength(0)
                var total = 0L
                var spins = 0
                while (!outputDone) {
                    if (++spins > 200_000) throw ImportException(R.string.import_failed)
                    if (!inputDone) {
                        val inIx = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inIx >= 0) {
                            val inBuf = codec.getInputBuffer(inIx)!!
                            val n = extractor.readSampleData(inBuf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outIx = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outCh = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            sawFormat = true
                        }
                        else -> if (outIx >= 0) {
                            if (!sawFormat) {
                                val of = codec.outputFormat
                                if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outCh = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                sawFormat = true
                            }
                            val buf = codec.getOutputBuffer(outIx)
                            if (buf != null && info.size > 0) {
                                val bytes = ByteArray(info.size)
                                buf.position(info.offset)
                                buf.get(bytes)
                                val converted = toLittle16(bytes, encoding)
                                out.write(converted)
                                total += converted.size
                                val bps = 2
                                if (total > maxPcmBytes(outRate, outCh.coerceAtLeast(1), bps)) {
                                    codec.releaseOutputBuffer(outIx, false)
                                    throw ImportException(R.string.import_too_long)
                                }
                            }
                            codec.releaseOutputBuffer(outIx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            }
            if (pcm.length() < 4) throw ImportException(R.string.import_no_audio)
            return PcmMeta(outRate, outCh.coerceAtLeast(1), 2)
        } catch (e: ImportException) {
            throw e
        } catch (_: Exception) {
            throw ImportException(R.string.import_failed)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        android.media.AudioFormat.ENCODING_PCM_8BIT -> 1
        android.media.AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    private fun maxPcmBytes(rate: Int, ch: Int, bps: Int): Long =
        (MAX_DURATION_MS / 1000L) * rate * ch * bps

    private fun toLittle16(src: ByteArray, encoding: Int): ByteArray {
        return when (encoding) {
            android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                val out = ByteArray(src.size * 2)
                for (i in src.indices) {
                    val s = ((src[i].toInt() and 0xFF) - 128) shl 8
                    out[i * 2] = s.toByte()
                    out[i * 2 + 1] = (s shr 8).toByte()
                }
                out
            }
            android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                val bb = ByteBuffer.wrap(src).order(ByteOrder.nativeOrder())
                val n = src.size / 4
                val out = ByteArray(n * 2)
                for (i in 0 until n) {
                    val f = bb.float.coerceIn(-1f, 1f)
                    val s = (f * Short.MAX_VALUE).toInt()
                    out[i * 2] = s.toByte()
                    out[i * 2 + 1] = (s shr 8).toByte()
                }
                out
            }
            else -> {
                // 16-bit: MediaCodec is typically native endian. Convert to little-endian.
                if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) src
                else {
                    val out = src.copyOf()
                    var i = 0
                    while (i + 1 < out.size) {
                        val a = out[i]; out[i] = out[i + 1]; out[i + 1] = a
                        i += 2
                    }
                    out
                }
            }
        }
    }

    private fun writeWav(pcm: File, dest: File, inRate: Int, inCh: Int) {
        val inFrames = (pcm.length() / (2L * inCh)).toInt()
        if (inFrames <= 0) throw ImportException(R.string.import_no_audio)
        val outRate = WavWriter.SAMPLE_RATE
        val outCh = WavWriter.CHANNELS
        val outFrames = if (inRate == outRate) inFrames
            else ((inFrames.toLong() * outRate) / inRate).toInt().coerceAtLeast(1)
        RandomAccessFile(pcm, "r").use { input ->
            RandomAccessFile(dest, "rw").use { out ->
                out.setLength(0)
                WavWriter.writeHeader(out, outFrames * outCh * 2, outRate, outCh)
                val inBuf = ShortArray(inCh)
                fun readFrame(frame: Int): Boolean {
                    val at = frame.toLong() * inCh * 2
                    if (at + inCh * 2 > pcm.length()) return false
                    input.seek(at)
                    for (c in 0 until inCh) {
                        val b0 = input.read()
                        val b1 = input.read()
                        if (b0 < 0 || b1 < 0) return false
                        inBuf[c] = ((b1 shl 8) or (b0 and 0xFF)).toShort()
                    }
                    return true
                }
                val last = ShortArray(outCh)
                for (i in 0 until outFrames) {
                    val src = if (inRate == outRate) i.toDouble() else i.toDouble() * inRate / outRate
                    val i0 = src.toInt().coerceIn(0, inFrames - 1)
                    val i1 = (i0 + 1).coerceAtMost(inFrames - 1)
                    val t = (src - i0).toFloat()
                    if (!readFrame(i0)) break
                    val a0 = inBuf.copyOf()
                    if (i1 != i0) readFrame(i1)
                    val a1 = inBuf
                    for (ch in 0 until outCh) {
                        val c = ch.coerceAtMost(inCh - 1)
                        val a = a0[c].toFloat()
                        val b = a1[c].toFloat()
                        last[ch] = (a + (b - a) * t).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        val s = last[ch].toInt()
                        out.write(byteArrayOf(s.toByte(), (s shr 8).toByte()))
                    }
                }
            }
        }
        if (dest.length() <= 44) throw ImportException(R.string.import_no_audio)
    }
}
