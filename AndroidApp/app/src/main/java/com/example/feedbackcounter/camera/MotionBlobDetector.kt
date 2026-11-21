package com.example.feedbackcounter.camera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Blob(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cx: Float,
    val cy: Float,
    val areaNorm: Float
)

class MotionBlobDetector(
    private val w: Int,
    private val h: Int
) {
    private val bg = FloatArray(w * h)
    private var bgInit = false

    // Tunables
    private val alpha = 0.03f
    private val threshold = 20f
    private val minAreaPx = 120
    private val maxAreaPx = (w * h * 0.55f).toInt()

    fun detect(grayU8: ByteArray): List<Blob> {
        if (grayU8.size != w * h) return emptyList()

        val mask = ByteArray(w * h)

        if (!bgInit) {
            for (i in grayU8.indices) bg[i] = (grayU8[i].toInt() and 0xFF).toFloat()
            bgInit = true
            return emptyList()
        }

        // background update & motion mask
        for (i in grayU8.indices) {
            val v = (grayU8[i].toInt() and 0xFF).toFloat()
            val b = bg[i]
            val diff = abs(v - b)
            // update
            bg[i] = b + alpha * (v - b)
            mask[i] = if (diff > threshold) 1 else 0
        }

        // Simple denoise: remove isolated pixels by 3x3 vote (cheap)
        val denoised = ByteArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var s = 0
                val idx = y * w + x
                for (dy in -1..1) for (dx in -1..1) s += mask[(y + dy) * w + (x + dx)].toInt()
                denoised[idx] = if (s >= 4) 1 else 0
            }
        }

        // Connected components (4-neighbor)
        val visited = ByteArray(w * h)
        val blobs = ArrayList<Blob>(8)

        fun push(stack: IntArray, sp: Int, v: Int): Int {
            stack[sp] = v
            return sp + 1
        }

        val stack = IntArray(w * h / 4 + 1)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (denoised[idx].toInt() == 0 || visited[idx].toInt() != 0) continue

                var sp = 0
                sp = push(stack, sp, idx)
                visited[idx] = 1

                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var count = 0
                var sumX = 0
                var sumY = 0

                while (sp > 0) {
                    val v = stack[--sp]
                    val vx = v % w
                    val vy = v / w
                    count++
                    sumX += vx
                    sumY += vy
                    minX = min(minX, vx); maxX = max(maxX, vx)
                    minY = min(minY, vy); maxY = max(maxY, vy)

                    val n1 = v - 1
                    val n2 = v + 1
                    val n3 = v - w
                    val n4 = v + w

                    if (vx > 0 && denoised[n1].toInt() != 0 && visited[n1].toInt() == 0) { visited[n1] = 1; sp = push(stack, sp, n1) }
                    if (vx < w - 1 && denoised[n2].toInt() != 0 && visited[n2].toInt() == 0) { visited[n2] = 1; sp = push(stack, sp, n2) }
                    if (vy > 0 && denoised[n3].toInt() != 0 && visited[n3].toInt() == 0) { visited[n3] = 1; sp = push(stack, sp, n3) }
                    if (vy < h - 1 && denoised[n4].toInt() != 0 && visited[n4].toInt() == 0) { visited[n4] = 1; sp = push(stack, sp, n4) }
                }

                if (count < minAreaPx || count > maxAreaPx) continue

                val cx = (sumX.toFloat() / count) / (w - 1).toFloat()
                val cy = (sumY.toFloat() / count) / (h - 1).toFloat()

                val l = minX.toFloat() / (w - 1).toFloat()
                val t = minY.toFloat() / (h - 1).toFloat()
                val r = maxX.toFloat() / (w - 1).toFloat()
                val b = maxY.toFloat() / (h - 1).toFloat()

                blobs += Blob(
                    left = l, top = t, right = r, bottom = b,
                    cx = cx, cy = cy,
                    areaNorm = count.toFloat() / (w * h).toFloat()
                )
            }
        }

        return blobs
    }
}
