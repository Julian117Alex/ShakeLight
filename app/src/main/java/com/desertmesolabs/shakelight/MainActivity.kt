package com.desertmesolabs.shakelight

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

import android.Manifest
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private var _isFlashOn = mutableStateOf(false)
    private val isFlashOn: State<Boolean> get() = _isFlashOn
    private val REQUEST_CAMERA = 100


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Ask for CAMERA permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA
            )
        } else {
            // 2️⃣ Only start your service once you’ve got permission
            startShakeService()
        }

        // 2️⃣ **Re-initialize your cameraManager & cameraId here** **
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        // 3️⃣ Then set your Compose UI
        setContent {
            MaterialTheme {
                FlashlightScreen(
                    isFlashOn = isFlashOn.value,
                    onToggle  = { toggleFlashlight() }
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA
            && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startShakeService()
        } else {
            // inform the user you need camera permission
        }
    }

    private fun startShakeService() {
        Intent(this, ShakeDetectionService::class.java).also { svc ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(svc)
            else
                startService(svc)
        }
    }

    private fun toggleFlashlight() {
        cameraId?.let {
            try {
                val newState = !_isFlashOn.value
                cameraManager.setTorchMode(it, newState)
                _isFlashOn.value = newState
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun FlashlightScreen(isFlashOn: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Shake (in background) or tap the button:")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onToggle) {
            Text(if (isFlashOn) "Turn OFF Flashlight" else "Turn ON Flashlight")
        }
    }
}
