package com.northwoods.playerzero.karoo

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Player Zero for Karoo settings: board name, Polaris token, live status.
 * Save applies immediately (the extension restarts its connector — no Karoo reboot).
 */
class SettingsActivity : Activity() {

    companion object {
        private const val TAG = "PlayerZero.Settings"
        const val PREFS_NAME = "playerzero_prefs"
        const val KEY_BOARD_TOKEN = "board_token"   // Polaris static token, 40 hex chars
        const val KEY_BOARD_NAME = "board_name"     // BLE advertisement name, e.g. "ow452500"
        private const val REQUEST_CODE_PERMS = 42
    }

    private lateinit var boardNameInput: EditText
    private lateinit var boardTokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var permissionBanner: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val statusTick = object : Runnable {
        override fun run() { refreshStatus(); ui.postDelayed(this, 2_000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val root = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F5F5F5")) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1F2937"))
            setPadding(24, 32, 24, 32)
        }
        header.addView(TextView(this).apply {
            text = "Player Zero"
            textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        header.addView(TextView(this).apply {
            // Runtime read on purpose: Kotlin constant-folds BuildConfig.VERSION_NAME and an incremental
            // build can ship a stale banner. The release script cleans first as well.
            text = "v" + runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrDefault(BuildConfig.VERSION_NAME) + " — Onewheel on the Karoo"
            textSize = 14f; setTextColor(Color.parseColor("#CCCCCC")); gravity = Gravity.CENTER
        })
        layout.addView(header)
        layout.addView(spacer(16))

        permissionBanner = TextView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#F8D7DA")); setTextColor(Color.parseColor("#721C24"))
            setPadding(16, 16, 16, 16); textSize = 13f
            setOnClickListener { ensurePermissions() }
        }
        layout.addView(permissionBanner)

        layout.addView(label("Board name (BLE name, e.g. ow452500)"))
        boardNameInput = EditText(this).apply {
            setText(prefs.getString(KEY_BOARD_NAME, "") ?: "")
            hint = "leave blank to match any 'ow*' board"; textSize = 14f; setSingleLine(true); setPadding(16, 16, 16, 16)
        }
        layout.addView(boardNameInput)
        layout.addView(spacer(8))

        layout.addView(label("Polaris token (40 hex chars — docs/POLARIS_TOKEN_EXTRACTION.md)"))
        boardTokenInput = EditText(this).apply {
            setText(prefs.getString(KEY_BOARD_TOKEN, "") ?: "")
            hint = "(leave blank for legacy MD5 auth)"; textSize = 14f; setSingleLine(true); setPadding(16, 16, 16, 16)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(boardTokenInput)
        layout.addView(spacer(16))

        layout.addView(Button(this).apply {
            text = "Save & connect"
            setOnClickListener { save() }
        })
        layout.addView(spacer(16))

        statusText = TextView(this).apply {
            text = ""; textSize = 13f; setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#E8E8E8")); setTextColor(Color.parseColor("#1F2937"))
        }
        layout.addView(statusText)
        layout.addView(spacer(16))
        layout.addView(label("Data fields (add to a ride page under Player Zero): Board Battery · Motor Temp · Safety Headroom · Board Amps · Board Speed. Battery reads --% until the board is authenticated."))

        root.addView(layout)
        setContentView(root)

        importConfigIfPresent()
        ensurePermissions()
    }

    override fun onResume() { super.onResume(); updatePermissionBanner(); ui.post(statusTick) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(statusTick) }

    private fun save() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BOARD_TOKEN, boardTokenInput.text.toString().trim())
            .putString(KEY_BOARD_NAME, boardNameInput.text.toString().trim())
            .apply()
        Log.i(TAG, "Settings saved")
        val ext = PlayerZeroExtension.instance
        if (ext == null) {
            statusText.text = "Saved. Extension not running — restart the Karoo once."
        } else {
            ext.restartConnector()
            statusText.text = "Saved. Connecting…"
        }
    }

    private fun refreshStatus() {
        val ext = PlayerZeroExtension.instance
        val ow = ext?.onewheel
        val b = ext?.board
        statusText.text = when {
            ext == null -> "Extension not running — restart the Karoo once."
            ow == null -> "No board configured. Enter a name and/or token, then Save & connect."
            else -> buildString {
                append(ow.lastStatus)
                if (b != null && b.boardBatteryPct >= 0) {
                    append("\nBattery ${b.boardBatteryPct}%")
                    if (b.motorTempC >= 0) append(" · motor ${b.motorTempC}°C")
                    if (b.safetyHeadroom >= 0) append(" · headroom ${b.safetyHeadroom}")
                    append(" · ${"%.1f".format(b.speed * 2.23694)} mph")
                }
            }
        }
    }

    // ── Runtime permissions (BLE scan needs Location on Karoo 2 / Nearby devices on Karoo 3) ──

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasAllPermissions(): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun ensurePermissions() {
        if (hasAllPermissions()) { updatePermissionBanner(); return }
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        Log.i(TAG, "Requesting ${missing.size} permission(s): ${missing.joinToString()}")
        ActivityCompat.requestPermissions(this, missing, REQUEST_CODE_PERMS)
        updatePermissionBanner()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_PERMS) return
        Log.i(TAG, "Permission result: allGranted=${grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }}")
        updatePermissionBanner()
        if (hasAllPermissions()) PlayerZeroExtension.instance?.restartConnector()
    }

    private fun updatePermissionBanner() {
        runOnUiThread {
            if (hasAllPermissions()) {
                permissionBanner.visibility = View.GONE
            } else {
                permissionBanner.visibility = View.VISIBLE
                permissionBanner.text = "⚠ Bluetooth/Location permission not granted — the board scan cannot run. Tap to allow" +
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) " \"Nearby devices\"." else " Location.")
            }
        }
    }

    // ── One-time config import (adb push config.json /sdcard/Android/data/<pkg>/files/config.json) ──

    private fun extractJsonValue(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    private fun importConfigIfPresent() {
        try {
            val f = getExternalFilesDir(null)?.let { java.io.File(it, "config.json") } ?: return
            if (!f.exists()) return
            val json = f.readText()
            val name = extractJsonValue(json, "board_name")
            val token = extractJsonValue(json, "polaris_token")
            if (name == null && token == null) return
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                if (name != null) putString(KEY_BOARD_NAME, name)
                if (token != null) putString(KEY_BOARD_TOKEN, token)
            }.apply()
            if (name != null) boardNameInput.setText(name)
            if (token != null) boardTokenInput.setText(token)
            Log.i(TAG, "Imported config from ${f.path}: board=$name token=${if (token != null) "set" else "none"}")
            f.delete()
            statusText.text = "Imported config. Connecting…"
            PlayerZeroExtension.instance?.restartConnector()
        } catch (e: Exception) {
            Log.w(TAG, "Config import failed: ${e.message}")
        }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Color.parseColor("#555555")); setPadding(0, 8, 0, 4)
    }
    private fun spacer(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h) }
}
