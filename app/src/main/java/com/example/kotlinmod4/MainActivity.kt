package com.example.kotlinmod4

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val compassViewModel: CompassViewModel by viewModels()

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var compassView: CompassView
    private lateinit var azimuthText: TextView
    private lateinit var errorText: TextView

    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        compassView = findViewById(R.id.compassView)
        azimuthText = findViewById(R.id.azimuthText)
        errorText = findViewById(R.id.errorText)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (accelerometer == null || magnetometer == null) {
            showSensorError()
        } else {
            renderAzimuth(compassViewModel.azimuth)
        }
    }

    override fun onResume() {
        super.onResume()
        if (accelerometer == null || magnetometer == null) return

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, gravityValues.size)
                hasGravity = true
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticValues, 0, magneticValues.size)
                hasMagnetic = true
            }
        }

        if (!hasGravity || !hasMagnetic) return

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            gravityValues,
            magneticValues
        )

        if (!success) return

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val azimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()

        compassViewModel.azimuth = azimuth
        renderAzimuth(azimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun renderAzimuth(azimuth: Float) {
        errorText.visibility = View.GONE
        compassView.visibility = View.VISIBLE
        azimuthText.visibility = View.VISIBLE

        compassView.animateToAzimuth(azimuth)
        azimuthText.text = "Азимут: ${azimuth.toInt()}°"
    }

    private fun showSensorError() {
        compassView.visibility = View.INVISIBLE
        azimuthText.visibility = View.INVISIBLE
        errorText.visibility = View.VISIBLE
        errorText.text = "Устройство не поддерживает датчик ориентации"
    }
}
