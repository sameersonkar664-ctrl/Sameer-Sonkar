package com.example

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.GamePhase
import com.example.model.PdfUiState
import com.example.pdf.GamePdfConverter
import com.example.ui.GameViewModel
import com.example.ui.components.BottomControlsBar
import com.example.ui.components.GarageStartModal
import com.example.ui.components.NvgOpticsOverlay
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.ResultModal
import com.example.ui.components.TopHudOverlay
import com.example.ui.theme.NightDriveTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            NightDriveTheme {
                NightVisionDriveApp()
            }
        }
    }
}

@Composable
fun NightVisionDriveApp(
    viewModel: GameViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.pdfState.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            viewModel.savePdfToUri(uri)
        }
    }

    BackHandler(enabled = pdfState !is PdfUiState.Hidden) {
        viewModel.dismissPdfPreview()
    }

    BackHandler(enabled = pdfState is PdfUiState.Hidden && uiState.hud.phase != GamePhase.MENU) {
        viewModel.openGarage()
    }

    val glSurfaceView = remember(context) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(viewModel.renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceView.onResume()
                    viewModel.onResumeLifecycle()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView.onPause()
                    viewModel.onPauseLifecycle()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
    ) {
        // 3D Viewport + HUD + Modals
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(uiState.hud.phase) {
                    var cumulativeDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (uiState.hud.phase == GamePhase.PLAYING) {
                                cumulativeDx = 0f
                                viewModel.renderer.dragActive = true
                                viewModel.renderer.dragSteer = 0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (uiState.hud.phase == GamePhase.PLAYING) {
                                cumulativeDx += dragAmount
                                viewModel.renderer.dragSteer = (cumulativeDx / 110f).coerceIn(-1f, 1f)
                            }
                        },
                        onDragEnd = {
                            viewModel.renderer.dragActive = false
                            viewModel.renderer.dragSteer = 0f
                        },
                        onDragCancel = {
                            viewModel.renderer.dragActive = false
                            viewModel.renderer.dragSteer = 0f
                        },
                    )
                },
        ) {
            AndroidView(
                factory = { glSurfaceView },
                modifier = Modifier.fillMaxSize(),
            )

            NvgOpticsOverlay(visible = uiState.hud.nvgActive)

            TopHudOverlay(
                state = uiState,
                onToggleTilt = viewModel::toggleTilt,
                onToggleNvg = viewModel::toggleNvg,
                onToggleMute = viewModel::toggleMute,
                onOpenGarage = viewModel::openGarage,
                onExportPdf = viewModel::generateAndPreviewPdf,
            )

            // Phase Overlays
            when (uiState.hud.phase) {
                GamePhase.MENU -> {
                    GarageStartModal(
                        state = uiState,
                        onSelectCar = viewModel::selectCar,
                        onSelectLevel = viewModel::selectLevel,
                        onStartDrive = viewModel::startDrive,
                        onConvertToPdf = viewModel::generateAndPreviewPdf,
                    )
                }

                GamePhase.LEVEL_UP -> {
                    ResultModal(
                        isVictory = true,
                        state = uiState,
                        onPrimaryAction = viewModel::nextLevel,
                        onExportPdf = viewModel::generateAndPreviewPdf,
                        onOpenGarage = viewModel::openGarage,
                    )
                }

                GamePhase.GAME_OVER -> {
                    ResultModal(
                        isVictory = false,
                        state = uiState,
                        onPrimaryAction = viewModel::startDrive,
                        onExportPdf = viewModel::generateAndPreviewPdf,
                        onOpenGarage = viewModel::openGarage,
                    )
                }

                GamePhase.PLAYING -> Unit
            }

            // Toast Banner
            androidx.compose.animation.AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                Surface(
                    color = Color(0xEB0F172A),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, SkyCyan.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Bottom Steering & Pedal Controls
        BottomControlsBar(
            nvgActive = uiState.hud.nvgActive,
            onLeftHold = { viewModel.renderer.leftPressed = it },
            onRightHold = { viewModel.renderer.rightPressed = it },
            onBrakeHold = { viewModel.renderer.brakePressed = it },
            onGasHold = { viewModel.renderer.gasPressed = it },
            onToggleNvg = viewModel::toggleNvg,
        )
    }

    PdfPreviewDialog(
        pdfState = pdfState,
        onSavePdf = {
            savePdfLauncher.launch("Night_Vision_Drive_3D_Report.pdf")
        },
        onSharePdf = {
            viewModel.getCurrentPdfFile()?.let { file ->
                GamePdfConverter.sharePdf(context, file)
            }
        },
        onPrintPdf = {
            val activity = context as? Activity ?: return@PdfPreviewDialog
            viewModel.getCurrentPdfFile()?.let { file ->
                GamePdfConverter.printPdf(activity, file)
            }
        },
        onDismiss = viewModel::dismissPdfPreview,
    )
}
