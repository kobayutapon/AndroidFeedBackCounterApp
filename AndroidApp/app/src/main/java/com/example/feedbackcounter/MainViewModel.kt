package com.example.feedbackcounter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.feedbackcounter.data.AppDataStore
import com.example.feedbackcounter.data.EndpointClient
import com.example.feedbackcounter.data.SyncMode
import com.example.feedbackcounter.ui.TrackedBox
import com.example.feedbackcounter.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppDataStore(app.applicationContext)
    private val endpointClient = EndpointClient()
    private val syncMutex = Mutex()

    init {
        viewModelScope.launch { prefs.ensureDeviceId() }
        // This timer is tied to the running app's ViewModel. Changing mode/period
        // cancels the previous timer; no WorkManager 15-minute minimum is imposed.
        viewModelScope.launch {
            combine(prefs.syncMode, prefs.syncIntervalMinutes) { mode, interval -> mode to interval }
                .distinctUntilChanged()
                .collectLatest { (mode, interval) ->
                    if (mode == SyncMode.PERIODIC) {
                        while (true) {
                            delay(interval * 60_000L)
                            sendCounts(request = SyncRequest.PERIODIC)
                        }
                    }
                }
        }
    }

    private val _tracks = MutableStateFlow<List<TrackedBox>>(emptyList())
    val tracks: StateFlow<List<TrackedBox>> = _tracks.asStateFlow()
    private data class Counts(
        val good: Int,
        val normal: Int,
        val bad: Int,
        val passL2R: Int,
        val passR2L: Int,
    )

    private data class UiParts(
        val c: Counts,
        val endpoint: String,
        val lastSync: String,
        val surveyTitle: String,
    )



    private val countsFlow: Flow<Counts> = run {
        // 2引数combineのみで段階的に集約（環境差で多引数combineが無い場合に備える）
        val p1: Flow<Pair<Int, Int>> =
            combine(prefs.good, prefs.normal) { good, normal -> good to normal }

        val p2: Flow<Triple<Int, Int, Int>> =
            combine(p1, prefs.bad) { (good, normal), bad -> Triple(good, normal, bad) }

        val p3: Flow<Pair<Triple<Int, Int, Int>, Int>> =
            combine(p2, prefs.passL2R) { gnb, passL2R -> gnb to passL2R }

        combine(p3, prefs.passR2L) { (gnb, passL2R), passR2L ->
            Counts(
                good = gnb.first,
                normal = gnb.second,
                bad = gnb.third,
                passL2R = passL2R,
                passR2L = passR2L
            )
        }
    }

val uiState: StateFlow<UiState> = run {
    // NOTE: Flow.combine の多引数オーバーロードが環境差で不足することがあるため、
    // 2引数combineのみで段階的に集約している。
    val partsFlow: Flow<UiParts> = run {
        val p1: Flow<Pair<Counts, String>> =
            combine(countsFlow, prefs.endpoint) { c, endpoint -> c to endpoint }

        val p2: Flow<Triple<Counts, String, String>> =
            combine(p1, prefs.lastSync) { (c, endpoint), lastSync ->
                Triple(c, endpoint, lastSync)
            }

        combine(p2, prefs.surveyTitle) { (c, endpoint, lastSync), surveyTitle ->
            UiParts(c, endpoint, lastSync, surveyTitle)
        }
    }

    val withPeople = combine(partsFlow, prefs.peopleCountingEnabled) { p, peopleEnabled ->
        UiState(
            surveyTitle = p.surveyTitle,
            peopleCountingEnabled = peopleEnabled,
            good = p.c.good,
            normal = p.c.normal,
            bad = p.c.bad,
            passL2R = p.c.passL2R,
            passR2L = p.c.passR2L,
            endpoint = p.endpoint,
            lastSyncStatus = p.lastSync,
        )
    }
    val withToken = combine(withPeople, prefs.authToken) { state, token -> state.copy(authToken = token) }
    val withDevice = combine(withToken, prefs.deviceId) { state, id -> state.copy(deviceId = id) }
    val withMode = combine(withDevice, prefs.syncMode) { state, mode -> state.copy(syncMode = mode) }
    combine(withMode, prefs.syncIntervalMinutes) { state, minutes ->
        state.copy(syncIntervalMinutes = minutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}

fun setEndpoint(url: String) = viewModelScope.launch { prefs.setEndpoint(url) }
    fun setAuthToken(token: String) = viewModelScope.launch { prefs.setAuthToken(token) }
    fun setSyncMode(mode: SyncMode) = viewModelScope.launch { prefs.setSyncMode(mode) }
    fun setSyncIntervalMinutes(minutes: Int) = viewModelScope.launch { prefs.setSyncIntervalMinutes(minutes) }

    fun setSurveyTitle(title: String) = viewModelScope.launch { prefs.setSurveyTitle(title) }

    fun setPeopleCountingEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setPeopleCountingEnabled(enabled) }

    fun incrementGood() = viewModelScope.launch { prefs.incrementGood(); sendCounts(request = SyncRequest.CHANGE) }
    fun incrementNormal() = viewModelScope.launch { prefs.incrementNormal(); sendCounts(request = SyncRequest.CHANGE) }
    fun incrementBad() = viewModelScope.launch { prefs.incrementBad(); sendCounts(request = SyncRequest.CHANGE) }

    fun incrementPassL2R() = viewModelScope.launch { prefs.incrementPassL2R(); sendCounts(request = SyncRequest.CHANGE) }
    fun incrementPassR2L() = viewModelScope.launch { prefs.incrementPassR2L(); sendCounts(request = SyncRequest.CHANGE) }

    fun clearSurveyCounters() = viewModelScope.launch { prefs.clearSurveyCounters(); sendCounts(request = SyncRequest.CHANGE) }
    fun clearPeopleCounters() = viewModelScope.launch { prefs.clearPeopleCounters(); sendCounts(request = SyncRequest.CHANGE) }

    fun updateTracks(tracks: List<TrackedBox>) {
        _tracks.value = tracks
    }

    // The toolbar SYNC action is always manual, even in DISABLED mode.
    fun syncNow() = viewModelScope.launch { sendCounts(request = SyncRequest.MANUAL) }

    private enum class SyncRequest { MANUAL, CHANGE, PERIODIC }

    private suspend fun sendCounts(request: SyncRequest) {
        syncMutex.withLock {
            val snapshot = prefs.readCountsSnapshot()
            if (request == SyncRequest.CHANGE && snapshot.mode != SyncMode.ON_CHANGE) return@withLock
            if (request == SyncRequest.PERIODIC && snapshot.mode != SyncMode.PERIODIC) return@withLock
            if (snapshot.endpoint.isBlank()) {
                prefs.setLastSyncStatus("Skipped (endpoint not set)")
                return@withLock
            }
            if (snapshot.token.isBlank()) {
                prefs.setLastSyncStatus("NG: 認証トークンが未設定です")
                return@withLock
            }
            try {
                // First retry any persisted request with its original requestId.
                // Then create one fresh snapshot so changes made while offline are sent.
                val pendingBefore = prefs.readPendingPayload()
                if (pendingBefore != null) {
                    sendOne(snapshot.endpoint, snapshot.token, pendingBefore)
                    prefs.acknowledgePendingPayload(pendingBefore)
                }
                // Avoid an identical second append on a pure retry if data didn't change.
                val current = prefs.readCountsSnapshot()
                val shouldSendFresh = pendingBefore == null ||
                    !sameCountsAndTitle(pendingBefore, current)
                if (shouldSendFresh) {
                    val fresh = prefs.getOrCreatePendingPayload()
                    sendOne(current.endpoint, current.token, fresh)
                    prefs.acknowledgePendingPayload(fresh)
                }
                prefs.setLastSyncStatus("OK (保存確認済)")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                prefs.setLastSyncStatus("NG: ${e.message}")
            }
        }
    }

    private suspend fun sendOne(endpoint: String, token: String, payload: String) {
        withContext(Dispatchers.IO) { endpointClient.postCounts(endpoint, payload, token) }
    }

    private fun sameCountsAndTitle(payload: String, snapshot: AppDataStore.CountsSnapshot): Boolean {
        val obj = org.json.JSONObject(payload)
        return obj.optString("surveyTitle") == snapshot.surveyTitle &&
            obj.optInt("good", -1) == snapshot.good &&
            obj.optInt("normal", -1) == snapshot.normal &&
            obj.optInt("bad", -1) == snapshot.bad &&
            obj.optInt("passL2R", -1) == snapshot.passL2R &&
            obj.optInt("passR2L", -1) == snapshot.passR2L
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    }
}
