package com.northwoods.playerzero.karoo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.hammerhead.karooext.extension.KarooExtension

/**
 * Player Zero for Karoo — the Onewheel's numbers on the bike computer.
 *
 * Connects straight to a Onewheel GT / GT-S over BLE (Polaris token or legacy MD5 auth) and
 * exposes battery, motor temp, safety headroom, amps and speed as Karoo data fields. Nothing
 * is uploaded anywhere. Born 2026-08-26 when the connector moved out of veloVigil so a
 * cyclist's Settings never carries a board token.
 *
 * Proof of connection is the battery field: it reads `--%` until the board's first battery
 * notification, then a real percentage. The board is authenticated only after that value.
 */
class PlayerZeroExtension : KarooExtension("playerzero", BuildConfig.VERSION_NAME) {

    companion object {
        private const val TAG = "PlayerZero"
        var instance: PlayerZeroExtension? = null
    }

    val board = BoardState()
    var onewheel: OnewheelConnector? = null
        private set

    override val types by lazy {
        listOf(
            BoardBatteryDataType(extension),
            MotorTempDataType(extension),
            HeadroomDataType(extension),
            BoardAmpsDataType(extension),
            BoardSpeedDataType(extension),
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "Player Zero for Karoo v${BuildConfig.VERSION_NAME} starting")
        startConnector(prefs())
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

    /** Settings calls this after Save so a new name/token takes effect without a Karoo reboot. */
    fun restartConnector() {
        Log.i(TAG, "Restarting connector with saved settings")
        onewheel?.stop()
        onewheel = null
        board.reset()
        startConnector(prefs())
    }

    private fun startConnector(prefs: SharedPreferences) {
        val token = prefs.getString(SettingsActivity.KEY_BOARD_TOKEN, "") ?: ""
        val boardName = prefs.getString(SettingsActivity.KEY_BOARD_NAME, "") ?: ""
        if (token.isEmpty() && boardName.isEmpty()) {
            Log.i(TAG, "No board name or token configured — open Player Zero settings on the Karoo")
            return
        }
        Log.i(TAG, "Starting OnewheelConnector (board=${boardName.ifEmpty { "any ow*" }}, auth=${if (token.length == 40) "polaris" else "legacy"})")
        val ow = OnewheelConnector(this, board, token, boardName)
        ow.onBoardConnected = {
            board.boardConnected = true
            board.lastDataAt = System.currentTimeMillis()
        }
        ow.onBoardDisconnected = {
            board.boardConnected = false
        }
        ow.onBoardAuthenticated = {
            Log.i(TAG, "Board authenticated — battery=${board.boardBatteryPct}% — fields are live")
        }
        ow.onTelemetryUpdate = {
            board.lastDataAt = System.currentTimeMillis()
        }
        ow.start()
        onewheel = ow
    }

    override fun onDestroy() {
        Log.i(TAG, "Player Zero for Karoo stopping")
        onewheel?.stop()
        onewheel = null
        instance = null
        super.onDestroy()
    }
}
