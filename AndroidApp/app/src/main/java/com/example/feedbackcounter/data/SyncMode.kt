package com.example.feedbackcounter.data

/** Automatic delivery policy. Manual SYNC remains available in every mode. */
enum class SyncMode(val label: String) {
    DISABLED("送信しない"),
    ON_CHANGE("カウントが変化したタイミングで送信"),
    PERIODIC("定期的に送信");

    companion object {
        fun fromStored(value: String?): SyncMode =
            entries.firstOrNull { it.name == value } ?: ON_CHANGE
    }
}
