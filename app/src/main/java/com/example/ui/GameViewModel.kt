package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEngine
import com.example.data.GamePreferencesRepository
import com.example.engine.EngineEventListener
import com.example.engine.HighwayGlRenderer
import com.example.model.CarSpec
import com.example.model.GameCatalog
import com.example.model.GamePhase
import com.example.model.HudSnapshot
import com.example.model.LevelSpec
import com.example.model.PdfUiState
import com.example.model.RunRecord
import com.example.pdf.GamePdfConverter
import com.example.pdf.PdfReportData
import com.example.sensor.TiltSteeringController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameUiState(
    val selectedCarIdx: Int = 0,
    val selectedLevelIdx: Int = 0,
    val bestDistance: Int = 0,
    val totalRuns: Int = 0,
    val highestClearedLevel: Int = 0,
    val tiltEnabled: Boolean = false,
    val muted: Boolean = false,
    val hud: HudSnapshot = HudSnapshot(),
    val recentRuns: List<RunRecord> = emptyList(),
) {
    val selectedCar: CarSpec
        get() = GameCatalog.cars.getOrElse(selectedCarIdx) { GameCatalog.cars[0] }

    val selectedLevel: LevelSpec
        get() = GameCatalog.levels.getOrElse(selectedLevelIdx) { GameCatalog.levels[0] }
}

class GameViewModel(application: Application) : AndroidViewModel(application), EngineEventListener {

    private val prefsRepo = GamePreferencesRepository(application)
    private val soundEngine = SoundEngine()
    private val tiltController = TiltSteeringController(application) { steer ->
        renderer.tiltSteer = steer
    }

    val renderer: HighwayGlRenderer = HighwayGlRenderer(this)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _pdfState = MutableStateFlow<PdfUiState>(PdfUiState.Hidden)
    val pdfState: StateFlow<PdfUiState> = _pdfState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            val initial = prefsRepo.statsFlow.first()
            val carIdx = initial.selectedCarIdx.coerceIn(GameCatalog.cars.indices)
            val lvlIdx = initial.selectedLevelIdx.coerceIn(GameCatalog.levels.indices)
            soundEngine.isMuted = initial.muted
            renderer.selectCar(GameCatalog.cars[carIdx])
            renderer.selectLevel(GameCatalog.levels[lvlIdx])
            _uiState.update {
                it.copy(
                    selectedCarIdx = carIdx,
                    selectedLevelIdx = lvlIdx,
                    bestDistance = initial.bestDistance,
                    totalRuns = initial.totalRuns,
                    highestClearedLevel = initial.highestClearedLevel,
                    muted = initial.muted,
                )
            }

            prefsRepo.statsFlow.collect { stored ->
                _uiState.update {
                    it.copy(
                        bestDistance = stored.bestDistance,
                        totalRuns = stored.totalRuns,
                        highestClearedLevel = stored.highestClearedLevel,
                        muted = stored.muted,
                    )
                }
            }
        }
    }

    fun selectCar(idx: Int) {
        val safeIdx = idx.coerceIn(GameCatalog.cars.indices)
        val car = GameCatalog.cars[safeIdx]
        renderer.selectCar(car)
        _uiState.update { it.copy(selectedCarIdx = safeIdx) }
        viewModelScope.launch { prefsRepo.saveSelectedCar(safeIdx) }
    }

    fun selectLevel(idx: Int) {
        val safeIdx = idx.coerceIn(GameCatalog.levels.indices)
        val level = GameCatalog.levels[safeIdx]
        renderer.selectLevel(level)
        _uiState.update { it.copy(selectedLevelIdx = safeIdx) }
        viewModelScope.launch { prefsRepo.saveSelectedLevel(safeIdx) }
    }

    fun startDrive() {
        soundEngine.startEngineLoopIfNeeded()
        val state = _uiState.value
        renderer.startRun(state.selectedCar, state.selectedLevel)
    }

    fun nextLevel() {
        val nextIdx = (_uiState.value.selectedLevelIdx + 1).coerceAtMost(GameCatalog.levels.lastIndex)
        selectLevel(nextIdx)
        startDrive()
    }

    fun openGarage() {
        soundEngine.updateEngine(playing = false, ratio = 0f)
        renderer.openMenu()
    }

    fun toggleNvg() {
        val before = renderer.currentNvgState()
        val toggled = renderer.toggleNvg()
        if (toggled) {
            soundEngine.playNvgToggle(activating = !before)
        }
    }

    fun toggleTilt() {
        val next = !_uiState.value.tiltEnabled
        if (next && !tiltController.isAvailable) {
            showToast("Motion sensor unavailable on this device")
            return
        }
        val active = tiltController.setEnabled(next)
        renderer.tiltEnabled = active
        _uiState.update { it.copy(tiltEnabled = active) }
        showToast(
            if (active) "Tilt ON — Tilt phone left/right to steer"
            else "Tilt steering disabled",
        )
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.muted
        soundEngine.isMuted = nextMuted
        _uiState.update { it.copy(muted = nextMuted) }
        viewModelScope.launch { prefsRepo.saveMuted(nextMuted) }
    }

    fun onPauseLifecycle() {
        soundEngine.updateEngine(playing = false, ratio = 0f)
        if (_uiState.value.tiltEnabled) {
            tiltController.stop()
        }
    }

    fun onResumeLifecycle() {
        if (_uiState.value.tiltEnabled) {
            val active = tiltController.setEnabled(true)
            renderer.tiltEnabled = active
        }
    }

    fun generateAndPreviewPdf() {
        if (_pdfState.value is PdfUiState.Generating) return
        _pdfState.value = PdfUiState.Generating
        val snapshotState = _uiState.value
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val reportData = PdfReportData(
                        selectedCar = snapshotState.selectedCar,
                        selectedLevel = snapshotState.selectedLevel,
                        hud = snapshotState.hud,
                        bestDistance = snapshotState.bestDistance,
                        totalRuns = snapshotState.totalRuns,
                        highestClearedLevel = snapshotState.highestClearedLevel,
                        recentRuns = snapshotState.recentRuns,
                    )
                    val file = GamePdfConverter.generateMissionPdf(getApplication(), reportData)
                    val pages = GamePdfConverter.renderPreviewPages(file)
                    file to pages
                }
            }.onSuccess { (file, pages) ->
                _pdfState.value = PdfUiState.Preview(file = file, pages = pages)
            }.onFailure { err ->
                _pdfState.value = PdfUiState.Error(err.message ?: "Failed to generate PDF")
            }
        }
    }

    fun savePdfToUri(destUri: Uri) {
        val current = _pdfState.value as? PdfUiState.Preview ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    GamePdfConverter.copyPdfToUri(getApplication(), current.file, destUri)
                }
            }.onSuccess {
                _pdfState.value = current.copy(statusMessage = "PDF saved to selected folder")
                showToast("PDF saved successfully")
            }.onFailure { err ->
                showToast("Could not save PDF: ${err.message ?: "error"}")
            }
        }
    }

    fun getCurrentPdfFile(): File? = (_pdfState.value as? PdfUiState.Preview)?.file

    fun dismissPdfPreview() {
        (_pdfState.value as? PdfUiState.Preview)?.pages?.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        _pdfState.value = PdfUiState.Hidden
    }

    fun showToast(message: String) {
        _toastMessage.value = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2600L)
            _toastMessage.value = null
        }
    }

    override fun onHudTick(snapshot: HudSnapshot) {
        soundEngine.updateEngine(
            playing = snapshot.phase == GamePhase.PLAYING,
            ratio = snapshot.speedRatio,
        )
        _uiState.update { it.copy(hud = snapshot) }
    }

    override fun onBatteryPickedUp() {
        soundEngine.playBatteryPickup()
    }

    override fun onNvgAutoOff(reasonLowBattery: Boolean) {
        soundEngine.playNvgToggle(activating = false)
        showToast(if (reasonLowBattery) "NVG Battery too low!" else "NVG Battery Depleted!")
    }

    override fun onLevelCleared(distanceMeters: Int, batteryLeft: Int) {
        soundEngine.updateEngine(playing = false, ratio = 0f)
        soundEngine.playLevelComplete()
        recordCompletedRun(distanceMeters, batteryLeft, cleared = true)
    }

    override fun onPlayerCrashed(distanceMeters: Int, batteryLeft: Int) {
        soundEngine.updateEngine(playing = false, ratio = 0f)
        soundEngine.playCrash()
        recordCompletedRun(distanceMeters, batteryLeft, cleared = false)
    }

    private fun recordCompletedRun(distanceMeters: Int, batteryLeft: Int, cleared: Boolean) {
        val state = _uiState.value
        val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val record = RunRecord(
            timestampLabel = timeLabel,
            carName = state.selectedCar.name,
            levelName = "L${state.selectedLevel.id} ${state.selectedLevel.name}",
            distanceMeters = distanceMeters,
            cleared = cleared,
            avgBatteryLeft = batteryLeft,
        )
        _uiState.update { current ->
            current.copy(
                bestDistance = maxOf(current.bestDistance, distanceMeters),
                recentRuns = (listOf(record) + current.recentRuns).take(12),
            )
        }
        viewModelScope.launch {
            prefsRepo.recordRun(
                distanceMeters = distanceMeters,
                clearedLevelId = if (cleared) state.selectedLevel.id else null,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tiltController.stop()
        soundEngine.release()
        dismissPdfPreview()
    }
}
