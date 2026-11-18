package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.sign

class TiltSteeringController(
    private val context: Context,
    private val onSteerChanged: (Float) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var active = false
    private var zeroBaseline: Float? = null

    val isAvailable: Boolean
        get() = accelSensor != null

    fun setEnabled(enabled: Boolean): Boolean {
        if (enabled == active) return active
        if (enabled) {
            val sensor = accelSensor ?: return false
            zeroBaseline = null
            val registered = sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
            ) == true
            active = registered
            if (!registered) onSteerChanged(0f)
            return registered
        } else {
            stop()
            return false
        }
    }

    fun stop() {
        active = false
        zeroBaseline = null
        sensorManager?.unregisterListener(this)
        onSteerChanged(0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!active || event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val rotation = runCatching {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
        }.getOrNull() ?: Surface.ROTATION_0

        // Normalize lateral tilt across portrait and landscape orientations
        val rawLateral = when (rotation) {
            Surface.ROTATION_90 -> event.values[1]
            Surface.ROTATION_270 -> -event.values[1]
            Surface.ROTATION_180 -> event.values[0]
            else -> -event.values[0]
        }

        if (zeroBaseline == null) {
            zeroBaseline = rawLateral
        }
        val delta = rawLateral - (zeroBaseline ?: 0f)
        // Deadzone +/- 0.55 m/s^2 (~3.2 deg), full lock at +/- 3.8 m/s^2
        val deadzone = 0.55f
        val steer = if (abs(delta) < deadzone) {
            0f
        } else {
            ((delta - sign(delta) * deadzone) / 3.25f).coerceIn(-1f, 1f)
        }
        onSteerChanged(steer)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
