package com.example.feedbackcounter.ui

import android.app.Application
import android.graphics.Paint
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feedbackcounter.MainViewModel
import com.example.feedbackcounter.data.SyncMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    onBeep: () -> Unit,
    ensureCameraStarted: () -> Unit,
    stopCamera: () -> Unit,
    setCameraCallbacks: (
        onTracks: (List<TrackedBox>) -> Unit,
        onL2R: () -> Unit,
        onR2L: () -> Unit
    ) -> Unit,
    attachPreview: (PreviewView) -> Unit,
    detachPreview: () -> Unit
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }

    val vm: MainViewModel = viewModel(
        factory = remember(app) { ViewModelProvider.AndroidViewModelFactory.getInstance(app) }
    )

    val ui by vm.uiState.collectAsState()
    val tracks by vm.tracks.collectAsState()

    // カメラ解析のコールバック登録（カメラ未起動でも pending に保持され、起動後に適用される）
    LaunchedEffect(Unit) {
        setCameraCallbacks(
            { vm.updateTracks(it) },
            { vm.incrementPassL2R() },
            { vm.incrementPassR2L() }
        )
    }

    // ✅ デフォルト停止。スイッチONのときだけカメラ解析を開始
    LaunchedEffect(ui.peopleCountingEnabled) {
        if (ui.peopleCountingEnabled) {
            ensureCameraStarted()
        } else {
            stopCamera()
            vm.updateTracks(emptyList())
        }
    }

    // 設定はタブではなく、右上のハンバーガーメニューから開く。
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val tabs = remember { listOf("アンケート", "人数") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feedback Counter") },
                actions = {
                    TextButton(onClick = { vm.syncNow() }) { Text("SYNC") }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Text("☰", fontSize = 28.sp)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("設定") },
                                onClick = {
                                    menuExpanded = false
                                    settingsOpen = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        text = { Text(title) }
                    )
                }
            }

            when (tab) {
                0 -> SurveyTab(
                    ui = ui,
                    tracks = tracks,
                    onTogglePeopleCounting = { enabled ->
                        vm.setPeopleCountingEnabled(enabled)
                    },
                    onGood = { onBeep(); vm.incrementGood() },
                    onNormal = { onBeep(); vm.incrementNormal() },
                    onBad = { onBeep(); vm.incrementBad() }
                )
                1 -> PeopleTab(
                    ui = ui,
                    tracks = tracks,
                    attachPreview = attachPreview,
                    detachPreview = detachPreview
                )
            }
        }
    }

    if (settingsOpen) {
        Dialog(
            onDismissRequest = { settingsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("設定", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { settingsOpen = false }) {
                            Text("閉じる")
                        }
                    }
                    HorizontalDivider()
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        SettingsContent(
                            ui = ui,
                            onSurveyTitleChange = vm::setSurveyTitle,
                            onEndpointChange = vm::setEndpoint,
                            onAuthTokenChange = vm::setAuthToken,
                            onSyncModeChange = vm::setSyncMode,
                            onSyncIntervalChange = vm::setSyncIntervalMinutes,
                            onClearSurvey = vm::clearSurveyCounters,
                            onClearPeople = vm::clearPeopleCounters
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyTab(
    ui: UiState,
    tracks: List<TrackedBox>,
    onTogglePeopleCounting: (Boolean) -> Unit,
    onGood: () -> Unit,
    onNormal: () -> Unit,
    onBad: () -> Unit
) {
    // 設定画面など別タブへ移動しても表示状態を保持する。
    // 検出表示・回答数を隠しても集計やカメラ解析には影響しない。
    var showDetection by rememberSaveable { mutableStateOf(true) }
    var showAnswerCounts by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                ui.surveyTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("人数", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = ui.peopleCountingEnabled,
                onCheckedChange = onTogglePeopleCounting
            )
        }

        // 表示切替は集計値の横に配置。回答ボタンの誤タップを避ける。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { showAnswerCounts = !showAnswerCounts }) {
                Text(if (showAnswerCounts) "回答数を隠す" else "回答数を表示")
            }
        }

        // 残りの画面領域を回答ボタンに配分する。
        // 各ボタン内の文字サイズは、その時点で測定されたボタン幅に応じて変化する。
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SurveyAnswerColumn("よかった", "〇", ui.good, Color(0xFF2E7D32), showAnswerCounts, Modifier.weight(1f), onGood)
            SurveyAnswerColumn("普通", "△", ui.normal, Color(0xFFF9A825), showAnswerCounts, Modifier.weight(1f), onNormal)
            SurveyAnswerColumn("悪かった", "×", ui.bad, Color(0xFFC62828), showAnswerCounts, Modifier.weight(1f), onBad)
        }

        // 最下部に固定。閉じても再表示のための操作欄は残す。
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { showDetection = !showDetection },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    if (showDetection) "検出状況  ▲  タップして隠す" else "検出状況  ▼  タップして表示",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showDetection) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "検出中: ${tracks.size}  |  左→右: ${ui.passL2R}  右→左: ${ui.passR2L}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SurveyAnswerColumn(
    label: String,
    symbol: String,
    count: Int,
    color: Color,
    showCount: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // dp は画面密度を反映するため、実際の表示領域（縦横）に追従する。
            val labelSize = (maxWidth.value * 0.135f).coerceIn(13f, 44f).sp
            val symbolSize = (maxWidth.value * 0.17f).coerceIn(16f, 54f).sp
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = color,
                    contentColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        fontSize = labelSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        symbol,
                        fontSize = symbolSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        if (showCount) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "回答数  $count",
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 14.sp,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PeopleTab(
    ui: UiState,
    tracks: List<TrackedBox>,
    attachPreview: (PreviewView) -> Unit,
    detachPreview: () -> Unit
) {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val previewHeight = screenH * 0.25f

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("人数カウント（動態検知・ID追跡）", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .height(previewHeight)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CameraPreviewWithOverlay(
                    tracks = tracks,
                    attachPreview = attachPreview,
                    detachPreview = detachPreview
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("左→右", ui.passL2R.toString())
                StatCard("右→左", ui.passR2L.toString())
                StatCard("検出中", tracks.size.toString())
            }
        }

        Text("※通過判定は「出現側」と「消失側」でカウントします（中央線の横切りだけでは数えません）。")
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    tracks: List<TrackedBox>,
    attachPreview: (PreviewView) -> Unit,
    detachPreview: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val targetAspect = 4f / 3f

        val (boxW, boxH) = if (w.value / h.value > targetAspect) {
            val hh = h.value
            val ww = hh * targetAspect
            ww.dp to hh.dp
        } else {
            val ww = w.value
            val hh = ww / targetAspect
            ww.dp to hh.dp
        }

        Box(
            modifier = Modifier.size(boxW, boxH),
            contentAlignment = Alignment.Center
        ) {
            val ctx = LocalContext.current

            val previewView = remember {
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            DisposableEffect(previewView) {
                attachPreview(previewView)
                onDispose { detachPreview() }
            }

            val centerLineColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paint = Paint().apply {
                    color = Color.White.toArgb()
                    textSize = 28f
                    isAntiAlias = true
                }

                val cx = size.width * 0.5f
                drawLine(
                    color = centerLineColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx()
                )

                tracks.forEach { t ->
                    val r = Rect(
                        left = t.left.coerceIn(0f, 1f) * size.width,
                        top = t.top.coerceIn(0f, 1f) * size.height,
                        right = t.right.coerceIn(0f, 1f) * size.width,
                        bottom = t.bottom.coerceIn(0f, 1f) * size.height
                    )

                    drawRect(
                        color = Color(0xAA00FFFF),
                        topLeft = Offset(r.left, r.top),
                        size = androidx.compose.ui.geometry.Size(r.width, r.height),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            "#${t.id}",
                            r.left + 6.dp.toPx(),
                            (r.top + 24.dp.toPx()).coerceAtLeast(24.dp.toPx()),
                            paint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SettingsContent(
    ui: UiState,
    onSurveyTitleChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onSyncModeChange: (SyncMode) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onClearSurvey: () -> Unit,
    onClearPeople: () -> Unit
) {
    var title by remember(ui.surveyTitle) { mutableStateOf(ui.surveyTitle) }
    var endpoint by remember(ui.endpoint) { mutableStateOf(ui.endpoint) }
    var authToken by remember(ui.authToken) { mutableStateOf(ui.authToken) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; onSurveyTitleChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("アンケートタイトル") },
            singleLine = true
        )

        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it; onEndpointChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endpoint (Google Apps Script / Webhook URL)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = authToken,
            onValueChange = { authToken = it; onAuthTokenChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("端末認証トークン") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Text("端末ID: ${ui.deviceId.ifBlank { "初回送信時に生成" }}", style = MaterialTheme.typography.bodySmall)
        Text("トークンはGAS側のScript Propertiesにも登録してください。HTTPSのみ送信します。", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Endpointへの自動送信", style = MaterialTheme.typography.titleMedium)
        SyncMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSyncModeChange(mode) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = ui.syncMode == mode,
                    onClick = { onSyncModeChange(mode) }
                )
                Spacer(Modifier.width(8.dp))
                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (ui.syncMode == SyncMode.PERIODIC) {
            Text("送信間隔：${ui.syncIntervalMinutes} 分", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { onSyncIntervalChange(ui.syncIntervalMinutes - 1) },
                    enabled = ui.syncIntervalMinutes > 1
                ) { Text("－") }
                Slider(
                    value = ui.syncIntervalMinutes.toFloat(),
                    onValueChange = { onSyncIntervalChange(it.toInt().coerceIn(1, 60)) },
                    valueRange = 1f..60f,
                    steps = 58,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                OutlinedButton(
                    onClick = { onSyncIntervalChange(ui.syncIntervalMinutes + 1) },
                    enabled = ui.syncIntervalMinutes < 60
                ) { Text("＋") }
            }
            Text("1～60分。設定後、指定間隔ごとに現在の集計値を送信します。", style = MaterialTheme.typography.bodySmall)
        }
        Text("上部のSYNCボタンは設定に関係なく手動送信します。", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onClearSurvey) { Text("アンケート結果をクリア") }
            Button(onClick = onClearPeople) { Text("人数カウンタをクリア") }
        }

        HorizontalDivider()
        Text("最終同期: ${ui.lastSyncStatus}")
        Text("※HTTPの場合は「http://...」も設定できます（usesCleartextTraffic を許可しています）。")
    }
}
