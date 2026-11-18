package com.drmagic.ringtonerecorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
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
    private lateinit var contactsStatus: TextView
    private lateinit var resetContactBtn: View
    private var player: MediaPlayer? = null
    private var micThread: Thread? = null
    @Volatile private var micRecording = false
    @Volatile private var importing = false
    private var durationMs = 0L
    private var lastSavedRingtoneUri: Uri? = null
    private var lastContactUri: Uri? = null
    private var lastContactName: String? = null
    private val srcIds = IntArray(3) { View.generateViewId() }
    private val srcIcons = intArrayOf(R.drawable.ic_source_phone, R.drawable.ic_mic_cartoon, R.drawable.ic_source_music)

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.EXTRA_MODE, "playback")
                .putExtra(CaptureService.EXTRA_CODE, res.resultCode)
                .putExtra(CaptureService.EXTRA_DATA, res.data))
            status.text = getString(R.string.recording_playback)
            refreshHouseAd()
        } else status.text = getString(R.string.capture_declined)
    }

    private val openAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importAudio(uri)
    }

    private val pickContact = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) assignRingtoneToContact(uri)
    }

    private val requestContactsPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CONTACTS] == true &&
            result[Manifest.permission.WRITE_CONTACTS] == true
        if (granted) pickContact.launch(null)
        else onContactsPermissionDenied()
    }

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { runOnUiThread { onRecordingReady() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ draws edge-to-edge for apps targeting 35+, and Android 16 (API 36) removes the
        // opt-out. Draw behind transparent bars with dark icons (the UI is always light cream) and pad
        // the content by the system-bar / cutout insets below.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(color(R.color.cream))
        }

        val header = card(root, R.color.sun)
        header.gravity = Gravity.CENTER_HORIZONTAL
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_header_mascot)
            adjustViewBounds = true
            contentDescription = getString(R.string.header_art)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160))
        })
        header.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setTextColor(color(R.color.ink))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        header.addView(TextView(this).apply {
            text = getString(R.string.tagline)
            textSize = 18f
            setTextColor(color(R.color.ink_soft))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })

        val sourceCard = card(root, R.color.sky)
        sourceGroup = RadioGroup(this)
        listOf(getString(R.string.src_playback), getString(R.string.src_mic), getString(R.string.src_music))
            .forEachIndexed { i, t ->
                sourceGroup.addView(RadioButton(this).apply {
                    id = srcIds[i]
                    text = t
                    setTextColor(color(R.color.ink))
                    setCompoundDrawablesRelativeWithIntrinsicBounds(srcIcons[i], 0, 0, 0)
                    compoundDrawablePadding = dp(10)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
            }
        sourceGroup.check(srcIds[0])
        sourceCard.addView(sourceGroup)

        val actions = card(root, R.color.coral)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton(getString(R.string.record), R.drawable.ic_mic_cartoon, true) { startRecording() }, weight())
        row.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 0) })
        row.addView(actionButton(getString(R.string.stop), R.drawable.ic_stop_cartoon, false) { stopRecording() }, weight())
        actions.addView(row)
        actions.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(8)) })
        actions.addView(actionButton(getString(R.string.upload_own), R.drawable.ic_upload_cartoon, false) { startImport() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        status = TextView(this).apply {
            text = getString(R.string.choose_source)
            setTextColor(color(R.color.ink))
            textSize = 15f
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        root.addView(status)

        val trim = card(root, R.color.mint)
        rangeLabel = TextView(this).apply { setTextColor(color(R.color.ink)) }
        trim.addView(rangeLabel)
        trim.addView(TextView(this).apply { text = getString(R.string.start); setTextColor(color(R.color.ink_soft)) })
        startBar = SeekBar(this); trim.addView(startBar)
        trim.addView(TextView(this).apply { text = getString(R.string.end); setTextColor(color(R.color.ink_soft)) })
        endBar = SeekBar(this); trim.addView(endBar)
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
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row2.addView(actionButton(getString(R.string.preview), R.drawable.ic_speaker_cartoon, false) { preview() }, weight())
        row2.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 0) })
        row2.addView(actionButton(getString(R.string.set_ringtone), R.drawable.ic_bell_cartoon, true) { setRingtone() }, weight())
        trim.addView(row2)

        val contacts = card(root, R.color.lavender)
        val contactHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        contactHead.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_contacts_cartoon)
            contentDescription = getString(R.string.contacts_action)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(8) }
        })
        contactHead.addView(TextView(this).apply {
            text = getString(R.string.contacts_prompt)
            textSize = 15f
            setTextColor(color(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        contacts.addView(contactHead)
        contacts.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(8)) })
        contacts.addView(actionButton(getString(R.string.contacts_action), R.drawable.ic_contacts_cartoon, true) { onChooseFriend() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        contactsStatus = TextView(this).apply {
            setTextColor(color(R.color.ink_soft))
            setPadding(0, dp(8), 0, 0)
        }
        contacts.addView(contactsStatus)
        resetContactBtn = actionButton(getString(R.string.contacts_reset), R.drawable.ic_bell_cartoon, false) { resetContactRingtone() }.apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        contacts.addView(resetContactBtn)

        root.addView(TextView(this).apply {
            textSize = 12f
            setTextColor(color(R.color.ink_soft))
            setPadding(dp(8), dp(4), dp(8), dp(8))
            text = getString(R.string.note)
        })
        houseAd = buildHouseAd(pad)
        root.addView(houseAd)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.cream))
            addView(root)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        refreshHouseAd()

        ContextCompat.registerReceiver(this, doneReceiver, IntentFilter(CaptureService.ACTION_DONE), ContextCompat.RECEIVER_NOT_EXPORTED)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)
        if (CaptureService.outFile(this).exists()) onRecordingReady()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun card(parent: LinearLayout, stroke: Int): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(color(R.color.card))
            strokeColor = color(stroke)
            strokeWidth = dp(2)
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(inner)
        parent.addView(card)
        return inner
    }

    private fun actionButton(label: String, icon: Int, filled: Boolean, click: () -> Unit): MaterialButton {
        return MaterialButton(this, null, if (filled) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            setIconResource(icon)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8)
            iconSize = dp(24)
            cornerRadius = dp(20)
            setOnClickListener { click() }
        }
    }

    private fun buildHouseAd(pad: Int): View {
        val density = resources.displayMetrics.density
        val card = MaterialCardView(this).apply {
            radius = 16 * density
            cardElevation = 2 * density
            setCardBackgroundColor(color(R.color.card))
            strokeColor = color(R.color.mint)
            strokeWidth = dp(2)
            useCompatPadding = true
            isClickable = true
            isFocusable = true
            contentDescription = "${getString(R.string.house_ad_title)}. ${getString(R.string.house_ad_body)}"
            setOnClickListener { openHelpAndWin() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = pad / 2
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
            setTextColor(color(R.color.ink_soft))
        })
        textCol.addView(TextView(this).apply {
            text = getString(R.string.house_ad_body)
            textSize = 16f
            setTextColor(color(R.color.ink))
            setPadding(0, dp(4), 0, 0)
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
        micRecording || CaptureService.isRecording || player?.isPlaying == true || importing

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

    private fun startImport() {
        if (micRecording || CaptureService.isRecording) { toast(getString(R.string.stop_first)); return }
        player?.release(); player = null
        openAudio.launch(arrayOf("audio/*"))
    }

    private fun importAudio(uri: Uri) {
        importing = true
        refreshHouseAd()
        status.text = getString(R.string.importing)
        Thread {
            try {
                AudioImporter.importToWav(this, uri, CaptureService.outFile(this))
                runOnUiThread {
                    importing = false
                    onRecordingReady()
                    status.text = getString(R.string.import_ok, durationMs / 1000.0)
                    refreshHouseAd()
                }
            } catch (e: AudioImporter.ImportException) {
                runOnUiThread {
                    importing = false
                    status.text = getString(e.messageRes)
                    toast(getString(e.messageRes))
                    refreshHouseAd()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    importing = false
                    status.text = getString(R.string.import_failed)
                    toast(getString(R.string.import_failed))
                    refreshHouseAd()
                }
            }
        }.start()
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

    private fun saveClipToRingtones(): Uri? {
        val f = trimmedFile() ?: return null
        val name = "Pick Your Sound ${System.currentTimeMillis() / 1000}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.wav")
            put(MediaStore.Audio.Media.TITLE, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/")
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: run { toast(getString(R.string.save_failed)); return null }
        try {
            contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                ?: run { toast(getString(R.string.save_failed)); return null }
            values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            lastSavedRingtoneUri = uri
            return uri
        } catch (_: Exception) {
            toast(getString(R.string.save_failed)); return null
        }
    }

    private fun setRingtone() {
        if (!Settings.System.canWrite(this)) {
            toast(getString(R.string.allow_write_settings))
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        val uri = saveClipToRingtones() ?: return
        RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, uri)
        toast(getString(R.string.ringtone_set)); status.text = getString(R.string.saved_and_set)
    }

    private fun hasContactsPerms() =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun onChooseFriend() {
        if (lastSavedRingtoneUri == null && (endBar.progress <= startBar.progress || !CaptureService.outFile(this).exists())) {
            toast(getString(R.string.contacts_need_sound)); return
        }
        if (hasContactsPerms()) {
            pickContact.launch(null)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.contacts_rationale_title)
            .setMessage(R.string.contacts_rationale)
            .setPositiveButton(R.string.contacts_continue) { _, _ ->
                requestContactsPerms.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            }
            .setNegativeButton(R.string.contacts_not_now, null)
            .show()
    }

    private fun onContactsPermissionDenied() {
        toast(getString(R.string.contacts_permission_denied))
        if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)
        ) {
            AlertDialog.Builder(this)
                .setMessage(R.string.contacts_permission_denied)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                .setNegativeButton(R.string.contacts_not_now, null)
                .show()
        }
    }

    private fun contactName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null } ?: getString(R.string.contact_fallback)
    }

    private fun assignRingtoneToContact(contactUri: Uri) {
        val ringtone = lastSavedRingtoneUri ?: saveClipToRingtones() ?: run {
            toast(getString(R.string.contacts_need_sound)); return
        }
        lastSavedRingtoneUri = ringtone
        val name = contactName(contactUri)
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtone.toString())
        }
        val n = try { contentResolver.update(contactUri, values, null, null) } catch (_: Exception) { -1 }
        if (n > 0) {
            lastContactUri = contactUri
            lastContactName = name
            contactsStatus.text = getString(R.string.contacts_assigned, name)
            resetContactBtn.visibility = View.VISIBLE
            toast(getString(R.string.contacts_assigned, name))
        } else toast(getString(R.string.contacts_failed))
    }

    private fun resetContactRingtone() {
        val contactUri = lastContactUri ?: return
        val name = lastContactName ?: getString(R.string.contact_fallback)
        val values = ContentValues().apply { putNull(ContactsContract.Contacts.CUSTOM_RINGTONE) }
        val n = try { contentResolver.update(contactUri, values, null, null) } catch (_: Exception) { -1 }
        if (n > 0) {
            contactsStatus.text = getString(R.string.contacts_reset_ok, name)
            resetContactBtn.visibility = View.GONE
            lastContactUri = null
            toast(getString(R.string.contacts_reset_ok, name))
        } else toast(getString(R.string.contacts_failed))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() { unregisterReceiver(doneReceiver); player?.release(); micRecording = false; super.onDestroy() }

    companion object {
        private const val PREFS = "pys"
        private const val PREF_AD_DISMISSED = "house_ad_dismissed"
    }
}
