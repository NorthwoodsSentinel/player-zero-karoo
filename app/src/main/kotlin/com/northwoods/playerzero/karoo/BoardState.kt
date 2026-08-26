package com.northwoods.playerzero.karoo

/**
 * The board's last-known state. Written by [OnewheelConnector] on every BLE notification,
 * read once a second by the Karoo data fields in [BoardDataTypes].
 *
 * Field names are kept identical to veloVigil's TelemetryBuffer (where this connector was born)
 * so the connector ports without edits. Nothing here leaves the Karoo — Player Zero for Karoo
 * has no backend.
 */
class BoardState {
    @Volatile var boardBatteryPct: Int = -1
    @Volatile var motorTempC: Int = -1
    @Volatile var safetyHeadroom: Int = -1
    @Volatile var boardCurrentAmps: Double = 0.0
    @Volatile var boardRpm: Int = 0
    @Volatile var boardSpeedRaw: Int = 0
    @Volatile var pitchDeg: Double = 0.0
    @Volatile var rollDeg: Double = 0.0
    /** m/s, derived from motor RPM and the wheel circumference in [OnewheelConnector]. */
    @Volatile var speed: Double = 0.0
    /** Rough electrical watts (amps × pack volts); the connector fills it. */
    @Volatile var power: Int = 0
    @Volatile var boardConnected: Boolean = false
    /** Wall-clock millis of the last notification, for the fields' staleness check. */
    @Volatile var lastDataAt: Long = 0L

    fun reset() {
        boardBatteryPct = -1; motorTempC = -1; safetyHeadroom = -1
        boardCurrentAmps = 0.0; boardRpm = 0; boardSpeedRaw = 0
        pitchDeg = 0.0; rollDeg = 0.0; speed = 0.0; power = 0
        boardConnected = false
    }
}
