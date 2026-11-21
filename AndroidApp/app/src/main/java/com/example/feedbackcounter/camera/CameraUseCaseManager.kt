package com.example.feedbackcounter.camera

import android.annotation.SuppressLint
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.feedbackcounter.ui.TrackedBox
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraUseCaseManager(
    private val lifecycleOwner: LifecycleOwner
) {
    private val context = lifecycleOwner as android.content.Context
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null
    private var motionAnalyzer: MotionTrackingAnalyzer? = null

    private var callbacks: Callbacks = Callbacks()

    data class Callbacks(
        val onTracks: ((List<TrackedBox>) -> Unit)? = null,
        val onL2R: (() -> Unit)? = null,
        val onR2L: (() -> Unit)? = null
    )

    fun setCallbacks(cb: Callbacks) {
        callbacks = cb
        // Recreate analyzer with latest callbacks by rebinding use cases
        ensureProvider { bind(attachPreview = (previewView != null)) }
    }

    fun startAnalysisOnly() {
        ensureProvider { bind(attachPreview = false) }
    }

    fun attachPreview(view: PreviewView) {
        previewView = view
        ensureProvider { bind(attachPreview = true) }
    }

    fun detachPreview() {
        previewView = null
        ensureProvider { bind(attachPreview = false) }
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    private fun ensureProvider(then: () -> Unit) {
        val existing = cameraProvider
        if (existing != null) {
            then()
            return
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            then()
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("RestrictedApi")
    private fun bind(attachPreview: Boolean) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        // Prefer 640x480 (4:3) to reduce load.
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        motionAnalyzer = MotionTrackingAnalyzer(
            lensFacing = CameraSelector.LENS_FACING_FRONT,
            callbacks = callbacks
        )

        analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.setAnalyzer(cameraExecutor, motionAnalyzer!!) }

        val useCases = mutableListOf<androidx.camera.core.UseCase>()

        if (attachPreview && previewView != null) {
            preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(previewView!!.surfaceProvider) }
            useCases += preview!!
        }

        useCases += analysis!!

        provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
    }
}
