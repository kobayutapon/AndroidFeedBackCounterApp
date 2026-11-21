package com.example.feedbackcounter.ui

import com.example.feedbackcounter.data.SyncMode

data class UiState(
    val surveyTitle: String = "アンケート",
    val peopleCountingEnabled: Boolean = false,
    val good: Int = 0,
    val normal: Int = 0,
    val bad: Int = 0,
    val passL2R: Int = 0,
    val passR2L: Int = 0,
    val endpoint: String = "",
    val authToken: String = "",
    val deviceId: String = "",
    val syncMode: SyncMode = SyncMode.ON_CHANGE,
    val syncIntervalMinutes: Int = 5,
    val lastSyncStatus: String = ""
)
