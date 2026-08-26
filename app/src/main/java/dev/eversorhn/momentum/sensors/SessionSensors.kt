package dev.eversorhn.momentum.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The instruments a phone already carries, put to work during a session.
 *
 * - **Barometer** for climb. GPS altitude is noisy to tens of metres; air pressure resolves a
 *   single flight of stairs, which is what makes a hill count.
 * - **Step counter** for cadence — steps per minute, the one running figure pace cannot tell
 *   you, and the only one that still works on a treadmill.
 *
 * Every one of them is optional: a phone without the sensor simply reports nothing, and nothing
 * above this class has to care.
 */
class SessionSensors(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)

    private val pressure: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val stepCounter: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // --- climb ---
    private var lastAltitude: Double? = null
    private var smoothedAltitude: Double? = null
    private var climbMeters = 0.0

    // --- cadence ---
    private var firstStepCount: Long? = null
    private var lastStepCount: Long = 0
    private var lastStepAtMillis = 0L
    private var recentCadence: Int? = null


    /** Positive altitude gain since the session started, in metres. Null without a barometer. */
    val climb: Double? get() = if (pressure == null) null else climbMeters

    /** Steps per minute over the last stretch. Null without a step counter, or before enough steps. */
    val cadence: Int? get() = recentCadence

    /** Total steps this session. Null without a step counter. */
    val steps: Long? get() = firstStepCount?.let { lastStepCount - it }

    val hasBarometer: Boolean get() = pressure != null
    val hasStepCounter: Boolean get() = stepCounter != null

    fun start() {
        pressure?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepCounter?.let { manager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        runCatching { manager?.unregisterListener(this) }
    }

    fun reset() {
        lastAltitude = null; smoothedAltitude = null; climbMeters = 0.0
        firstStepCount = null; lastStepCount = 0; lastStepAtMillis = 0L; recentCadence = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> onPressure(event.values.firstOrNull() ?: return)
            Sensor.TYPE_STEP_COUNTER -> onSteps((event.values.firstOrNull() ?: return).toLong())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onPressure(hPa: Float) {
        val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa).toDouble()
        // Air pressure wanders with weather and with a door opening; a slow filter plus a dead
        // band keeps that out while still catching a real slope.
        val smoothed = smoothedAltitude?.let { it * 0.9 + altitude * 0.1 } ?: altitude
        smoothedAltitude = smoothed
        val previous = lastAltitude
        if (previous == null) {
            lastAltitude = smoothed
            return
        }
        val delta = smoothed - previous
        if (abs(delta) >= CLIMB_DEAD_BAND_METERS) {
            if (delta > 0) climbMeters += delta
            lastAltitude = smoothed
        }
    }

    private fun onSteps(total: Long) {
        val now = System.currentTimeMillis()
        if (firstStepCount == null) {
            firstStepCount = total
            lastStepCount = total
            lastStepAtMillis = now
            return
        }
        val stepDelta = total - lastStepCount
        val seconds = (now - lastStepAtMillis) / 1000.0
        if (seconds >= CADENCE_WINDOW_SECONDS && stepDelta > 0) {
            recentCadence = (stepDelta / seconds * 60.0).roundToInt().takeIf { it in 20..260 }
            lastStepCount = total
            lastStepAtMillis = now
        }
    }

    private companion object {
        /** Below this, a change is weather or a door, not a hill. */
        const val CLIMB_DEAD_BAND_METERS = 1.0
        const val CADENCE_WINDOW_SECONDS = 8.0
    }
}
