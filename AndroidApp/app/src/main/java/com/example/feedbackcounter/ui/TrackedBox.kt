package com.example.feedbackcounter.ui

/** Normalized coordinates (0..1) in the analysis coordinate system (upright). */
data class TrackedBox(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
