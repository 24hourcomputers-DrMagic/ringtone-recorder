package com.drmagic.ringtonerecorder

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import java.io.File
import java.io.RandomAccessFile

/**
 * Foreground service (type mediaProjection) that records audio to a WAV file.
 * Captures other apps' playback via AudioPlaybackCaptureConfiguration (sources 1 and 3).
 * Microphone recording (source 2) runs in MainActivity while it is on screen.
 */
class CaptureService : Service() {
    companion object {
        const val EXTRA_MODE = "mode"; const val EXTRA_CODE = "code"; const val EXTRA_DATA = "data"
        const val ACTION_STOP = "stop"; const val ACTION_DONE = "com.drmagic.ringtonerecorder.DONE"
        fun outFile(ctx: Context) = File(ctx.filesDir, "recording.wav")
        @Volatile var isRecording = false
    }

    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRecording(); return START_NOT_STICKY }
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: "mic"
        startForegroundNotice(mode)

        val rate = WavWriter.SAMPLE_RATE
        val fmt = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf * 2, 8192)

        record = try {
            if (mode == "playback") {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                @Suppress("DEPRECATION")
                val data: Intent = intent!!.getParcelableExtra(EXTRA_DATA)!!
                projection = mpm.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), data)
                projection!!.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopRecording() }
                }, null)
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                AudioRecord.Builder().setAudioFormat(fmt).setBufferSizeInBytes(bufSize)
                    .setAudioPlaybackCaptureConfig(cfg).build()
            } else {
                AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(fmt).setBufferSizeInBytes(bufSize).build()
            }
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }

        val file = outFile(this)
        isRecording = true
        thread = Thread {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0); WavWriter.writeHeader(raf, 0)
                val buf = ByteArray(bufSize); var total = 0
                record!!.startRecording()
                while (isRecording) {
                    val n = record!!.read(buf, 0, buf.size)
                    if (n > 0) { raf.write(buf, 0, n); total += n }
                }
                WavWriter.writeHeader(raf, total)
            }
            sendBroadcast(Intent(ACTION_DONE).setPackage(packageName))
        }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun stopRecording() {
        if (!isRecording && record == null) { stopSelf(); return }
        isRecording = false
        thread?.join(2000)
        try { record?.stop() } catch (_: Exception) {}
        record?.release(); record = null
        projection?.stop(); projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotice(mode: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("rec", "Recording", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "rec")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (mode == "mic") "Recording microphone…" else "Recording phone audio…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onDestroy() { if (isRecording) stopRecording(); super.onDestroy() }
}
