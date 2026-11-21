package com.example.feedbackcounter.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.roundToInt

object YPlaneSampler {

    /**
     * Samples the Y plane into an upright grayscale image (uint8) with optional horizontal mirror.
     * This avoids creating Bitmap objects and stays fast on CPU.
     */
    fun sampleToGrayU8(
        image: ImageProxy,
        outW: Int,
        outH: Int,
        rotationDegrees: Int,
        mirrorHorizontal: Boolean
    ): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val srcW = image.width
        val srcH = image.height

        val out = ByteArray(outW * outH)

        fun yAt(x: Int, y: Int): Int {
            val idx = y * rowStride + x
            return buffer.get(idx).toInt() and 0xFF
        }

        // We treat output as upright (portrait/landscape agnostic) after applying rotation.
        // Map each output pixel to a source pixel.
        for (oy in 0 until outH) {
            val fy = oy.toFloat() / outH
            for (ox in 0 until outW) {
                val fx = ox.toFloat() / outW

                var sx: Int
                var sy: Int

                when ((rotationDegrees % 360 + 360) % 360) {
                    90 -> {
                        // output x,y corresponds to source (x' = (1-fy), y' = fx)
                        sx = ((1f - fy) * (srcW - 1)).roundToInt()
                        sy = (fx * (srcH - 1)).roundToInt()
                    }
                    180 -> {
                        sx = ((1f - fx) * (srcW - 1)).roundToInt()
                        sy = ((1f - fy) * (srcH - 1)).roundToInt()
                    }
                    270 -> {
                        sx = (fy * (srcW - 1)).roundToInt()
                        sy = ((1f - fx) * (srcH - 1)).roundToInt()
                    }
                    else -> { // 0
                        sx = (fx * (srcW - 1)).roundToInt()
                        sy = (fy * (srcH - 1)).roundToInt()
                    }
                }

                if (mirrorHorizontal) {
                    sx = (srcW - 1) - sx
                }

                out[oy * outW + ox] = yAt(sx.coerceIn(0, srcW - 1), sy.coerceIn(0, srcH - 1)).toByte()
            }
        }

        return out
    }

    // Absolute get from ByteBuffer without changing position
    private fun ByteBuffer.get(index: Int): Byte {
        return this.duplicate().apply { position(index) }.get()
    }
}
