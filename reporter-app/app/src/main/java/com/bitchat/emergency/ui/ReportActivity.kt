package com.bitchat.emergency.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bitchat.emergency.R
import com.bitchat.emergency.location.GpsCapture
import com.bitchat.emergency.model.EmergencyType
import com.bitchat.emergency.model.IncidentReport
import com.bitchat.emergency.model.Severity
import com.bitchat.emergency.queue.ReportQueue
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var btnTrapped: Button
    private lateinit var btnInjured: Button
    private lateinit var btnFire: Button
    private lateinit var btnNeedEvac: Button
    
    private lateinit var rgSeverity: RadioGroup
    private lateinit var tvCasCount: TextView
    private lateinit var btnCasMinus: Button
    private lateinit var btnCasPlus: Button
    
    private lateinit var etNotes: EditText
    private lateinit var btnMic: ImageButton
    private lateinit var btnSend: Button
    private lateinit var btnClear: Button

    private var selectedType: EmergencyType? = null
    private var casualtyCount = 0

    private lateinit var gpsCapture: GpsCapture
    private lateinit var reportQueue: ReportQueue

    private val speechResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                val currentText = etNotes.text.toString()
                etNotes.setText(if (currentText.isEmpty()) spokenText else "$currentText $spokenText")
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startSpeechRecognition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        gpsCapture = GpsCapture(this)
        reportQueue = ReportQueue(this)

        initViews()
        setupListeners()
        requestAllPermissions()
    }

    /**
     * Request all runtime permissions needed for the app:
     * - Location (GPS capture)
     * - POST_NOTIFICATIONS (Android 13+, required for foreground service notification)
     * - BLE permissions (Android 12+, required for scan/advertise)
     */
    private fun requestAllPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // BLE runtime permissions (API 31+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        // POST_NOTIFICATIONS (API 33+) — required for foreground service notifications
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    private fun initViews() {
        btnTrapped = findViewById(R.id.btn_type_trapped)
        btnInjured = findViewById(R.id.btn_type_injured)
        btnFire = findViewById(R.id.btn_type_fire)
        btnNeedEvac = findViewById(R.id.btn_type_need_evac)
        
        rgSeverity = findViewById(R.id.rg_severity)
        tvCasCount = findViewById(R.id.tv_cas_count)
        btnCasMinus = findViewById(R.id.btn_cas_minus)
        btnCasPlus = findViewById(R.id.btn_cas_plus)
        
        etNotes = findViewById(R.id.et_notes)
        btnMic = findViewById(R.id.btn_mic)
        btnSend = findViewById(R.id.btn_send)
        btnClear = findViewById(R.id.btn_clear)
    }

    private fun setupListeners() {
        val typeButtons = mapOf(
            btnTrapped to EmergencyType.TRAPPED,
            btnInjured to EmergencyType.INJURED,
            btnFire to EmergencyType.FIRE,
            btnNeedEvac to EmergencyType.NEED_EVAC
        )

        for ((button, type) in typeButtons) {
            button.setOnClickListener {
                selectedType = type
                updateTypeButtons(button)
            }
        }

        btnCasMinus.setOnClickListener {
            if (casualtyCount > 0) {
                casualtyCount--
                tvCasCount.text = casualtyCount.toString()
            }
        }

        btnCasPlus.setOnClickListener {
            if (casualtyCount < 999) {
                casualtyCount++
                tvCasCount.text = casualtyCount.toString()
            }
        }

        btnMic.setOnClickListener {
            handleMicClick()
        }

        btnSend.setOnClickListener {
            submitReport()
        }

        btnClear.setOnClickListener {
            resetForm()
        }
    }

    private fun updateTypeButtons(selectedButton: Button) {
        val allButtons = listOf(btnTrapped, btnInjured, btnFire, btnNeedEvac)
        for (button in allButtons) {
            if (button == selectedButton) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.button_selected))
                button.setTextColor(Color.BLACK)
            } else {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.button_unselected))
                button.setTextColor(Color.WHITE)
            }
        }
    }

    private fun handleMicClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
        }
    }



    private fun submitReport() {
        val type = selectedType
        if (type == null) {
            Toast.makeText(this, "Please select an emergency type", Toast.LENGTH_SHORT).show()
            return
        }

        val severityId = rgSeverity.checkedRadioButtonId
        if (severityId == -1) {
            Toast.makeText(this, "Please select a severity level", Toast.LENGTH_SHORT).show()
            return
        }

        val severity = when (severityId) {
            R.id.rb_sev_low -> Severity.LOW
            R.id.rb_sev_medium -> Severity.MEDIUM
            R.id.rb_sev_high -> Severity.HIGH
            R.id.rb_sev_critical -> Severity.CRITICAL
            else -> Severity.MEDIUM
        }

        val (lat, lon) = gpsCapture.getLocation()
        val notes = etNotes.text.toString()

        val report = IncidentReport(
            emergencyType = type,
            casualtyCount = casualtyCount,
            severity = severity,
            notes = notes,
            timestamp = System.currentTimeMillis(),
            latitude = lat,
            longitude = lon
        )

        reportQueue.enqueue(report)
        Toast.makeText(this, "Report queued successfully", Toast.LENGTH_SHORT).show()
        resetForm()
    }

    private fun resetForm() {
        selectedType = null
        val allButtons = listOf(btnTrapped, btnInjured, btnFire, btnNeedEvac)
        for (button in allButtons) {
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.button_unselected))
            button.setTextColor(Color.WHITE)
        }
        rgSeverity.clearCheck()
        casualtyCount = 0
        tvCasCount.text = "0"
        etNotes.text.clear()
    }
}
