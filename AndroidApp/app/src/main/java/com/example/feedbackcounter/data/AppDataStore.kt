package com.example.feedbackcounter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Context.ds by preferencesDataStore(name = "feedback_counter_prefs")

class AppDataStore(private val context: Context) {

    private object Keys {
        val GOOD = intPreferencesKey("good")
        val NORMAL = intPreferencesKey("normal")
        val BAD = intPreferencesKey("bad")
        val PASS_L2R = intPreferencesKey("pass_l2r")
        val PASS_R2L = intPreferencesKey("pass_r2l")
        val ENDPOINT = stringPreferencesKey("endpoint")
        val LAST_SYNC = stringPreferencesKey("last_sync")
        val LAST_SYNC_EPOCH = longPreferencesKey("last_sync_epoch") // optional
        val SURVEY_TITLE = stringPreferencesKey("survey_title")
        val PEOPLE_COUNTING_ENABLED = booleanPreferencesKey("people_counting_enabled")
        val SYNC_MODE = stringPreferencesKey("sync_mode")
        val SYNC_INTERVAL_MIN = intPreferencesKey("sync_interval_min")
    }

    val good: Flow<Int> = context.ds.data.map { it[Keys.GOOD] ?: 0 }
    val normal: Flow<Int> = context.ds.data.map { it[Keys.NORMAL] ?: 0 }
    val bad: Flow<Int> = context.ds.data.map { it[Keys.BAD] ?: 0 }
    val passL2R: Flow<Int> = context.ds.data.map { it[Keys.PASS_L2R] ?: 0 }
    val passR2L: Flow<Int> = context.ds.data.map { it[Keys.PASS_R2L] ?: 0 }
    val surveyTitle: Flow<String> = context.ds.data.map { it[Keys.SURVEY_TITLE] ?: "アンケート" }
    val peopleCountingEnabled: Flow<Boolean> = context.ds.data.map { it[Keys.PEOPLE_COUNTING_ENABLED] ?: false }
    val endpoint: Flow<String> = context.ds.data.map { it[Keys.ENDPOINT] ?: "" }
    val lastSync: Flow<String> = context.ds.data.map { it[Keys.LAST_SYNC] ?: "" }
    // Existing installations retain the former on-change delivery behavior.
    val syncMode: Flow<SyncMode> = context.ds.data.map {
        SyncMode.fromStored(it[Keys.SYNC_MODE])
    }
    val syncIntervalMinutes: Flow<Int> = context.ds.data.map {
        (it[Keys.SYNC_INTERVAL_MIN] ?: 5).coerceIn(1, 60)
    }

    suspend fun setSyncMode(mode: SyncMode) = context.ds.edit { it[Keys.SYNC_MODE] = mode.name }
    suspend fun setSyncIntervalMinutes(minutes: Int) = context.ds.edit {
        it[Keys.SYNC_INTERVAL_MIN] = minutes.coerceIn(1, 60)
    }

    data class CountsSnapshot(
        val endpoint: String,
        val good: Int,
        val normal: Int,
        val bad: Int,
        val passL2R: Int,
        val passR2L: Int,
        val mode: SyncMode
    )

    // Atomic snapshot: do not rely on StateFlow/UI propagation after a counter write.
    suspend fun readCountsSnapshot(): CountsSnapshot = context.ds.data.first().let { p ->
        CountsSnapshot(
            endpoint = p[Keys.ENDPOINT] ?: "",
            good = p[Keys.GOOD] ?: 0,
            normal = p[Keys.NORMAL] ?: 0,
            bad = p[Keys.BAD] ?: 0,
            passL2R = p[Keys.PASS_L2R] ?: 0,
            passR2L = p[Keys.PASS_R2L] ?: 0,
            mode = SyncMode.fromStored(p[Keys.SYNC_MODE])
        )
    }

    suspend fun incrementGood() = context.ds.edit { it[Keys.GOOD] = (it[Keys.GOOD] ?: 0) + 1 }
    suspend fun incrementNormal() = context.ds.edit { it[Keys.NORMAL] = (it[Keys.NORMAL] ?: 0) + 1 }
    suspend fun incrementBad() = context.ds.edit { it[Keys.BAD] = (it[Keys.BAD] ?: 0) + 1 }
    suspend fun incrementPassL2R() = context.ds.edit { it[Keys.PASS_L2R] = (it[Keys.PASS_L2R] ?: 0) + 1 }
    suspend fun incrementPassR2L() = context.ds.edit { it[Keys.PASS_R2L] = (it[Keys.PASS_R2L] ?: 0) + 1 }

    suspend fun setSurveyTitle(title: String) = context.ds.edit { it[Keys.SURVEY_TITLE] = title }
    suspend fun setPeopleCountingEnabled(enabled: Boolean) = context.ds.edit {
        it[Keys.PEOPLE_COUNTING_ENABLED] = enabled
    }

    suspend fun setEndpoint(url: String) = context.ds.edit { it[Keys.ENDPOINT] = url.trim() }

    suspend fun setLastSyncStatus(text: String) {
        val now = System.currentTimeMillis()
        val stamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(now))
        context.ds.edit {
            it[Keys.LAST_SYNC] = "$stamp  $text"
            it[Keys.LAST_SYNC_EPOCH] = now
        }
    }

    // Backward-compatible alias (MainViewModel expects these names)
    suspend fun clearSurvey() = clearSurveyCounters()

    // Backward-compatible alias (MainViewModel expects these names)
    suspend fun clearPeople() = clearPeopleCounters()


    suspend fun clearSurveyCounters() = context.ds.edit {
        it[Keys.GOOD] = 0
        it[Keys.NORMAL] = 0
        it[Keys.BAD] = 0
    }

    suspend fun clearPeopleCounters() = context.ds.edit {
        it[Keys.PASS_L2R] = 0
        it[Keys.PASS_R2L] = 0
    }
}
