package com.example.feedbackcounter.camera

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class CountDir { NONE, L2R, R2L }

data class ActiveTrack(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class FinishedTrack(
    val id: Int,
    val countDir: CountDir
)

private data class TrackState(
    val id: Int,
    var cx: Float,
    var cy: Float,
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
    var firstCx: Float,
    var lastCx: Float,
    var firstSide: Int,
    var lastSide: Int,
    var lifeFrames: Int,
    var missFrames: Int,
    var maxAreaNorm: Float
)

class SimpleMultiTracker {
    private var nextId = 1
    private val tracks = LinkedHashMap<Int, TrackState>()

    // Tunables
    private val matchMaxDist = 0.18f        // normalized
    private val maxMissFrames = 10
    private val minLifeFrames = 8
    private val minTravel = 0.30f
    private val centerDeadZone = 0.06f      // ignore jitter around the center line
    private val maxAreaNormAllowed = 0.45f  // too big -> likely camera is too close / background change

    /** Returns (activeTracks, finishedTracks) */
    fun update(blobs: List<Blob>): Pair<List<ActiveTrack>, List<FinishedTrack>> {
        // Reset missFrames
        tracks.values.forEach { it.missFrames++ }

        val assigned = HashSet<Int>() // track ids assigned this frame
        val usedBlobs = BooleanArray(blobs.size)

        // Greedy matching: for each blob find nearest track
        for (bi in blobs.indices) {
            val b = blobs[bi]
            var bestId: Int? = null
            var bestD = 1e9f

            for (t in tracks.values) {
                val d = hypot((b.cx - t.cx).toDouble(), (b.cy - t.cy).toDouble()).toFloat()
                if (d < bestD && d <= matchMaxDist && t.missFrames <= maxMissFrames) {
                    bestD = d
                    bestId = t.id
                }
            }

            if (bestId != null) {
                val t = tracks[bestId]!!
                usedBlobs[bi] = true
                assigned += bestId

                t.cx = b.cx
                t.cy = b.cy
                t.left = b.left
                t.top = b.top
                t.right = b.right
                t.bottom = b.bottom
                t.lastCx = t.cx
                t.lastSide = sideOf(t.cx)
                t.lifeFrames++
                t.missFrames = 0
                t.maxAreaNorm = max(t.maxAreaNorm, b.areaNorm)
            }
        }

        // Create new tracks for unmatched blobs
        for (bi in blobs.indices) {
            if (usedBlobs[bi]) continue
            val b = blobs[bi]
            val id = nextId++
            val s = TrackState(
                id = id,
                cx = b.cx,
                cy = b.cy,
                left = b.left,
                top = b.top,
                right = b.right,
                bottom = b.bottom,
                firstCx = b.cx,
                lastCx = b.cx,
                firstSide = sideOf(b.cx),
                lastSide = sideOf(b.cx),
                lifeFrames = 1,
                missFrames = 0,
                maxAreaNorm = b.areaNorm
            )
            tracks[id] = s
        }

        // Collect finished tracks & prune
        val finished = ArrayList<FinishedTrack>()

        val it = tracks.entries.iterator()
        while (it.hasNext()) {
            val (_, t) = it.next()
            if (t.missFrames <= maxMissFrames) continue

            // Decide count direction on disappearance, based on entry side and exit side
            val dir = decide(t)
            if (dir != CountDir.NONE) {
                finished += FinishedTrack(t.id, dir)
            }
            it.remove()
        }

        val active = tracks.values.map {
            ActiveTrack(it.id, it.left, it.top, it.right, it.bottom)
        }

        return active to finished
    }

    private fun sideOf(cx: Float): Int {
        val x = cx - 0.5f
        return when {
            x < -centerDeadZone -> -1 // left
            x > centerDeadZone -> 1   // right
            else -> 0                 // center band
        }
    }

    private fun decide(t: TrackState): CountDir {
        if (t.lifeFrames < minLifeFrames) return CountDir.NONE
        if (t.maxAreaNorm > maxAreaNormAllowed) return CountDir.NONE

        val start = t.firstSide
        val end = t.lastSide
        if (start == 0 || end == 0) return CountDir.NONE
        if (start == end) return CountDir.NONE

        val travel = abs(t.lastCx - t.firstCx)
        if (travel < minTravel) return CountDir.NONE

        return if (start < end) CountDir.L2R else CountDir.R2L
    }
}
