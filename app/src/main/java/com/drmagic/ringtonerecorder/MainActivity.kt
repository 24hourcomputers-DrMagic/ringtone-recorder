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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.RandomAccessFile

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var startBar: SeekBar
    private lateinit var endBar: SeekBar
    private lateinit var rangeLabel: TextView
    private lateinit var sourceGroup: RadioGroup
    private lateinit var houseAd: View
    private var player: MediaPlayer? = null
    private var micThread: Thread? = null
    @Volatile private var micRecording = false
    private var durationMs = 0L
    private val srcIds = IntArray(3) { android.view.View.generateViewId() }

    private val projectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_MODE, "playback")
                .putExtra(CaptureService.EXTRA_CODE, res.resultCode)
                .putExtra(CaptureService.EXTRA_DATA, res.data))
            status.text = getString(R.string.recording_playback)
            refreshHouseAd()
        } else status.text = getString(R.string.capture_declined)
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
        root.addView(TextView(this).apply { text = getString(R.string.tagline); textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 0, 0, pad) })

        sourceGroup = RadioGroup(this)
        listOf(getString(R.string.src_playback), getString(R.string.src_mic), getString(R.string.src_music))
            .forEachIndexed { i, t -> sourceGroup.addView(RadioButton(this).apply { id = srcIds[i]; text = t }) }
        sourceGroup.check(srcIds[0])
        root.addView(sourceGroup)

        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = getString(R.string.record); setOnClickListener { startRecording() } })
        row.addView(Button(this).apply { text = getString(R.string.stop); setOnClickListener { stopRecording() } })
        root.addView(row)

        status = TextView(this).apply { text = getString(R.string.choose_source); setPadding(0, pad, 0, pad) }
        root.addView(status)

        rangeLabel = TextView(this); root.addView(rangeLabel)
        root.addView(TextView(this).apply { text = getString(R.string.start) })
        startBar = SeekBar(this); root.addView(startBar)
        root.addView(TextView(this).apply { text = getString(R.string.end) })
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
        row2.addView(Button(this).apply { text = getString(R.string.preview); setOnClickListener { preview() } })
        row2.addView(Button(this).apply { text = getString(R.string.set_ringtone); setOnClickListener { setRingtone() } })
        root.addView(row2)
        root.addView(TextView(this).apply {
            textSize = 12f; setPadding(0, pad, 0, 0)
            text = getString(R.string.note)
        })
        houseAd = buildHouseAd(pad)
        root.addView(houseAd)
        setContentView(ScrollView(this).apply { addView(root) })
        refreshHouseAd()

        ContextCompat.registerReceiver(this, doneReceiver, IntentFilter(CaptureService.ACTION_DONE), ContextCompat.RECEIVER_NOT_EXPORTED)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)
        if (CaptureService.outFile(this).exists()) onRecordingReady()
    }

    private fun buildHouseAd(pad: Int): View {
        val density = resources.displayMetrics.density
        val card = MaterialCardView(this).apply {
            radius = 12 * density
            cardElevation = 2 * density
            useCompatPadding = true
            isClickable = true
            isFocusable = true
            contentDescription = "${getString(R.string.house_ad_title)}. ${getString(R.string.house_ad_body)}"
            setOnClickListener { openHelpAndWin() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = pad
            layoutParams = lp
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad / 2, pad)
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = getString(R.string.house_ad_title)
            textSize = 12f
        })
        textCol.addView(TextView(this).apply {
            text = getString(R.string.house_ad_body)
            textSize = 16f
            setPadding(0, (4 * density).toInt(), 0, 0)
        })
        row.addView(textCol)
        row.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = getString(R.string.house_ad_dismiss)
            setBackgroundColor(0)
            setOnClickListener {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AD_DISMISSED, true).apply()
                refreshHouseAd()
            }
        })
        card.addView(row)
        return card
    }

    private fun openHelpAndWin() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.house_ad_url))))
    }

    private fun isBusy(): Boolean =
        micRecording || CaptureService.isRecording || player?.isPlaying == true

    private fun refreshHouseAd() {
        if (!::houseAd.isInitialized) return
        val dismissed = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_AD_DISMISSED, false)
        houseAd.visibility = if (dismissed || isBusy()) View.GONE else View.VISIBLE
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startRecording() {
        if (!hasMic()) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return }
        player?.release(); player = null
        if (sourceGroup.checkedRadioButtonId == srcIds[1]) startMic()
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
        status.text = getString(R.string.recording_mic)
        refreshHouseAd()
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
        status.text = getString(R.string.recorded_fmt, durationMs / 1000.0)
        updateRange()
        refreshHouseAd()
    }

    private fun updateRange() {
        rangeLabel.text = getString(R.string.clip_fmt, startBar.progress / 10.0, endBar.progress / 10.0)
    }

    private fun trimmedFile(): File? {
        val src = CaptureService.outFile(this)
        if (!src.exists() || endBar.progress <= startBar.progress) { toast(getString(R.string.need_range)); return null }
        val dst = File(cacheDir, "trimmed.wav")
        WavWriter.trim(src, dst, startBar.progress * 100L, endBar.progress * 100L)
        return dst
    }

    private fun preview() {
        val f = trimmedFile() ?: return
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(f.path)
            setOnCompletionListener { refreshHouseAd() }
            prepare()
            start()
        }
        refreshHouseAd()
    }

    private fun setRingtone() {
        if (!Settings.System.canWrite(this)) {
            toast(getString(R.string.allow_write_settings))
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
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: run { toast(getString(R.string.save_failed)); return }
        contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri)
        toast(getString(R.string.ringtone_set)); status.text = getString(R.string.saved_and_set)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() { unregisterReceiver(doneReceiver); player?.release(); micRecording = false; super.onDestroy() }

    companion object {
        private const val PREFS = "pys"
        private const val PREF_AD_DISMISSED = "house_ad_dismissed"
    }
}
