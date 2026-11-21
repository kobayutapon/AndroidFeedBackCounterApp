package com.example.feedbackcounter.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.feedbackcounter.camera.CameraUseCaseManager.Callbacks
import com.example.feedbackcounter.ui.TrackedBox
import kotlin.math.abs

class MotionTrackingAnalyzer(
    private val lensFacing: Int,
    callbacks: Callbacks
) : ImageAnalysis.Analyzer {

    // Use small working resolution for CPU motion tracking
    private val workW = 160
    private val workH = 120

    private val detector = MotionBlobDetector(workW, workH)
    private val tracker = SimpleMultiTracker()

    @Volatile
    private var cb: Callbacks = callbacks

    fun setCallbacks(callbacks: Callbacks) {
        this.cb = callbacks
    }

    override fun analyze(image: ImageProxy) {
        try {
            val rotation = image.imageInfo.rotationDegrees
            val mirrored = (lensFacing == CameraSelector.LENS_FACING_FRONT)

            val gray = YPlaneSampler.sampleToGrayU8(
                image = image,
                outW = workW,
                outH = workH,
                rotationDegrees = rotation,
                mirrorHorizontal = mirrored
            )

            val blobs = detector.detect(gray)

            val (tracks, finished) = tracker.update(blobs)

            // finished tracks -> count by entry/exit side
            finished.forEach { fin ->
                if (fin.countDir == CountDir.L2R) cb.onL2R?.invoke()
                if (fin.countDir == CountDir.R2L) cb.onR2L?.invoke()
            }

            // active tracks -> overlay
            cb.onTracks?.invoke(
                tracks.map {
                    TrackedBox(
                        id = it.id,
                        left = it.left,
                        top = it.top,
                        right = it.right,
                        bottom = it.bottom
                    )
                }
            )
        } catch (_: Throwable) {
            // ignore (keeps analyzer alive)
        } finally {
            image.close()
        }
    }
}
