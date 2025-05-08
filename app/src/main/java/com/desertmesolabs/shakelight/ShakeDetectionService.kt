package com.desertmesolabs.shakelight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ShakeDetectionService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID        = "ShakeDetectionServiceChannel"
        private const val NOTIF_ID          = 1
        private const val SHAKE_THRESHOLD   = 10f
        private const val SHAKE_DURATION_MS = 1500L
        private const val SHAKE_GAP_MS      = 500L
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private var isShaking       = false
    private var shakeStartTime  = 0L
    private var lastShakeTime   = 0L
    private var isFlashOn       = false      // ← Track state yourself

    override fun onCreate() {
        super.onCreate()
        Log.d("ShakeService", "...1")
        // 1) Init camera
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        Log.d("ShakeService", "...2")


        // 2) Init sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI
        )
        Log.d("ShakeService", "...3")

        // 3) Go foreground
        createNotificationChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shake Detection Active")
            .setContentText("Shake continuously for 1.5s to toggle flashlight")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Use the overload that specifies the service type on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        Log.d("ShakeService", "...4")

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?) = null

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val (x, y, z) = it.values
            val now       = System.currentTimeMillis()
            val accel     = sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH

            if (accel > SHAKE_THRESHOLD) {
                if (!isShaking) {
                    isShaking = true
                    shakeStartTime = now
                } else if (now - shakeStartTime >= SHAKE_DURATION_MS) {
                    toggleFlashlight()
                    isShaking = false
                }
                lastShakeTime = now
            } else if (isShaking && now - lastShakeTime > SHAKE_GAP_MS) {
                isShaking = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private fun toggleFlashlight() {
        cameraId?.let {
            cameraManager.setTorchMode(it, !isFlashOn)
            isFlashOn = !isFlashOn
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Shake Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).also { ch ->
                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(ch)
            }
        }
    }
}
