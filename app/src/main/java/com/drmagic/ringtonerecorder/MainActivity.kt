package com.drmagic.ringtonerecorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var startBar: SeekBar
    private lateinit var endBar: SeekBar
    private lateinit var rangeLabel: TextView
    private lateinit var sourceGroup: RadioGroup
    private var player: MediaPlayer? = null
    private var micThread: Thread? = null
    @Volatile private var micRecording = false
    private var durationMs = 0L

    private val projectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_MODE, "playback")
                .putExtra(CaptureService.EXTRA_CODE, res.resultCode)
                .putExtra(CaptureService.EXTRA_DATA, res.data))
            status.text = "Recording phone audio… press Stop when done."
        } else status.text = "Screen-audio capture permission was declined."
    }

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { runOnUiThread { onRecordingReady() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        root.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 26f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = "Pick your sound"; textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 0, 0, pad) })

        sourceGroup = RadioGroup(this)
        listOf("1. Sound playing on the phone now", "2. Microphone (sounds around you)", "3. A segment of music you're listening to")
            .forEachIndexed { i, t -> sourceGroup.addView(RadioButton(this).apply { id = 100 + i; text = t }) }
        sourceGroup.check(100)
        root.addView(sourceGroup)

        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "Record"; setOnClickListener { startRecording() } })
        row.addView(Button(this).apply { text = "Stop"; setOnClickListener { stopRecording() } })
        root.addView(row)

        status = TextView(this).apply { text = "Choose a source and tap Record."; setPadding(0, pad, 0, pad) }
        root.addView(status)

        rangeLabel = TextView(this); root.addView(rangeLabel)
        root.addView(TextView(this).apply { text = "Start" })
        startBar = SeekBar(this); root.addView(startBar)
        root.addView(TextView(this).apply { text = "End" })
        endBar = SeekBar(this); root.addView(endBar)
        val l = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                if (startBar.progress > endBar.progress) {
                    if (s === startBar) endBar.progress = startBar.progress else startBar.progress = endBar.progress
                }
                updateRange()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        startBar.setOnSeekBarChangeListener(l); endBar.setOnSeekBarChangeListener(l)

        val row2 = LinearLayout(this)
        row2.addView(Button(this).apply { text = "Preview"; setOnClickListener { preview() } })
        row2.addView(Button(this).apply { text = "Set as ringtone"; setOnClickListener { setRingtone() } })
        root.addView(row2)
        root.addView(TextView(this).apply {
            textSize = 12f; setPadding(0, pad, 0, 0)
            text = "Note: apps that block audio capture (and calls) can't be recorded. Only use music you have the rights to."
        })
        setContentView(ScrollView(this).apply { addView(root) })

        ContextCompat.registerReceiver(this, doneReceiver, IntentFilter(CaptureService.ACTION_DONE), ContextCompat.RECEIVER_NOT_EXPORTED)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)
        if (CaptureService.outFile(this).exists()) onRecordingReady()
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startRecording() {
        if (!hasMic()) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return }
        player?.release(); player = null
        if (sourceGroup.checkedRadioButtonId == 101) startMic()
        else {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMic() {
        val rate = WavWriter.SAMPLE_RATE
        val buf = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2, 8192)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, buf)
        micRecording = true
        status.text = "Recording microphone… press Stop when done."
        micThread = Thread {
            RandomAccessFile(CaptureService.outFile(this), "rw").use { raf ->
                raf.setLength(0); WavWriter.writeHeader(raf, 0)
                val b = ByteArray(buf); var total = 0
                rec.startRecording()
                while (micRecording) { val n = rec.read(b, 0, b.size); if (n > 0) { raf.write(b, 0, n); total += n } }
                rec.stop(); rec.release()
                WavWriter.writeHeader(raf, total)
            }
            runOnUiThread { onRecordingReady() }
        }.also { it.start() }
    }

    private fun stopRecording() {
        if (micRecording) { micRecording = false; return }
        if (CaptureService.isRecording)
            startService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
    }

    private fun onRecordingReady() {
        durationMs = WavWriter.durationMs(CaptureService.outFile(this))
        val max = (durationMs / 100).toInt().coerceAtLeast(1)   // 0.1 s steps
        startBar.max = max; endBar.max = max
        startBar.progress = 0; endBar.progress = minOf(max, 300) // default first 30 s
        status.text = "Recorded %.1f s. Trim with the sliders, preview, then set it.".format(durationMs / 1000.0)
        updateRange()
    }

    private fun updateRange() {
        rangeLabel.text = "Clip: %.1f s → %.1f s".format(startBar.progress / 10.0, endBar.progress / 10.0)
    }

    private fun trimmedFile(): File? {
        val src = CaptureService.outFile(this)
        if (!src.exists() || endBar.progress <= startBar.progress) { toast("Record something and pick a range first."); return null }
        val dst = File(cacheDir, "trimmed.wav")
        WavWriter.trim(src, dst, startBar.progress * 100L, endBar.progress * 100L)
        return dst
    }

    private fun preview() {
        val f = trimmedFile() ?: return
        player?.release()
        player = MediaPlayer().apply { setDataSource(f.path); prepare(); start() }
    }

    private fun setRingtone() {
        if (!Settings.System.canWrite(this)) {
            toast("Allow \"Modify system settings\", then tap Set again.")
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        val f = trimmedFile() ?: return
        val name = "Pick Your Sound ${System.currentTimeMillis() / 1000}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.wav")
            put(MediaStore.Audio.Media.TITLE, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: run { toast("Couldn't save."); return }
        contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri)
        toast("Ringtone set!"); status.text = "Saved to Ringtones/ and set as your ringtone."
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() { unregisterReceiver(doneReceiver); player?.release(); micRecording = false; super.onDestroy() }
}
