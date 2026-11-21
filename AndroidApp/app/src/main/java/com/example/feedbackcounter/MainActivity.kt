package com.example.feedbackcounter

import android.Manifest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.feedbackcounter.camera.CameraUseCaseManager
import com.example.feedbackcounter.ui.AppRoot
import com.example.feedbackcounter.ui.TrackedBox
import com.example.feedbackcounter.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    private var cameraManager: CameraUseCaseManager? = null
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)

    private var pendingCallbacks: CameraUseCaseManager.Callbacks? = null

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen (hide system bars)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            AppTheme {
                AppRoot(
                    onBeep = { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80) },
                    ensureCameraStarted = { ensureCameraPermissionAndStart() },
                    stopCamera = { stopCamera() },
                    setCameraCallbacks = { onTracks, onL2R, onR2L ->
                        val cb = CameraUseCaseManager.Callbacks(
                            onTracks = onTracks,
                            onL2R = onL2R,
                            onR2L = onR2L
                        )
                        pendingCallbacks = cb
                        cameraManager?.setCallbacks(cb)
                    },
                    attachPreview = { previewView -> cameraManager?.attachPreview(previewView) },
                    detachPreview = { cameraManager?.detachPreview() }
                )
            }
        }

        // Start camera analysis in background (if permission already granted)
        ensureCameraPermissionAndStart()
    }

    private fun stopCamera() {
        cameraManager?.shutdown()
        cameraManager = null
    }

private fun ensureCameraPermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (granted) startCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        if (cameraManager != null) return
        cameraManager = CameraUseCaseManager(this)
        pendingCallbacks?.let { cameraManager?.setCallbacks(it) }
        cameraManager?.startAnalysisOnly()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.shutdown()
        cameraManager = null
        tone.release()
    }
}
