package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.GameUiState
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitPanel
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NvgGreen
import com.example.ui.theme.NvgGreenDark
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SkyCyanDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun NvgOpticsOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
    ) {
        val pulse = rememberInfiniteTransition(label = "nvg_rec")
        val dotAlpha by pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
            label = "dot_alpha",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Radial NVG goggle vignette
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xC2001206)),
                        center = center,
                        radius = size.maxDimension * 0.62f,
                    ),
                )
                // Subtle horizontal CRT scanlines
                val step = 6.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color(0x28000000),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    y += step
                }
                // Corner tactical brackets
                val pad = 12.dp.toPx()
                val arm = 26.dp.toPx()
                val stroke = 2.dp.toPx()
                // Top-left
                drawLine(NvgGreen, Offset(pad, pad), Offset(pad + arm, pad), stroke)
                drawLine(NvgGreen, Offset(pad, pad), Offset(pad, pad + arm), stroke)
                // Top-right
                drawLine(NvgGreen, Offset(size.width - pad, pad), Offset(size.width - pad - arm, pad), stroke)
                drawLine(NvgGreen, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), stroke)
                // Bottom-left
                drawLine(NvgGreen, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), stroke)
                drawLine(NvgGreen, Offset(pad, size.height - pad), Offset(pad, size.height - pad - arm), stroke)
                // Bottom-right
                drawLine(NvgGreen, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - arm, size.height - pad), stroke)
                drawLine(NvgGreen, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - arm), stroke)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 102.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(NvgGreen.copy(alpha = dotAlpha)),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.label_nvg_rec),
                    style = MaterialTheme.typography.labelMedium,
                    color = NvgGreen,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopHudOverlay(
    state: GameUiState,
    onToggleTilt: () -> Unit,
    onToggleNvg: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenGarage: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val nvg = state.hud.nvgActive
    val borderColor = if (nvg) NvgGreen.copy(alpha = 0.6f) else CockpitBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Telemetry Card
            Surface(
                color = CockpitPanel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudStatBlock(
                        label = stringResource(R.string.label_speed),
                        value = "${state.hud.speedKmh}",
                        unit = "km/h",
                        nvgActive = nvg,
                    )
                    HudStatBlock(
                        label = stringResource(R.string.label_dist),
                        value = "${state.hud.distanceMeters}",
                        unit = "m",
                        nvgActive = nvg,
                    )
                    Column(modifier = Modifier.widthIn(min = 78.dp)) {
                        Row(
                            modifier = Modifier.width(86.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.label_nvg_batt),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                            Text(
                                text = "${state.hud.batteryPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.hud.batteryPercent <= 25) DangerRed else TextPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(86.dp)
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.14f)),
                        ) {
                            val fillColor = if (state.hud.batteryPercent <= 25) DangerRed else NvgGreen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(state.hud.batteryPercent.coerceIn(0, 100) / 100f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(fillColor),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // HUD Action Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudActionPill(
                    label = if (state.tiltEnabled) "TILT: ON" else "TILT: OFF",
                    icon = Icons.Filled.ScreenRotation,
                    active = state.tiltEnabled,
                    activeColor = SkyCyan,
                    activeBg = SkyCyanDark,
                    testTag = "btn_tilt_toggle",
                    onClick = onToggleTilt,
                )
                HudActionPill(
                    label = if (nvg) "NVG: ON" else "NVG: OFF",
                    icon = Icons.Filled.Visibility,
                    active = nvg,
                    activeColor = NvgGreen,
                    activeBg = NvgGreenDark,
                    testTag = "btn_nvg_top",
                    onClick = onToggleNvg,
                )
                HudActionPill(
                    label = if (state.muted) "SFX: OFF" else "SFX: ON",
                    icon = if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    active = false,
                    activeColor = SkyCyan,
                    activeBg = SkyCyanDark,
                    testTag = "btn_sfx_toggle",
                    onClick = onToggleMute,
                )
                HudActionPill(
                    label = stringResource(R.string.label_garage),
                    icon = Icons.Filled.DirectionsCar,
                    active = false,
                    activeColor = SkyCyan,
                    activeBg = SkyCyanDark,
                    testTag = "btn_open_garage",
                    onClick = onOpenGarage,
                )
                HudActionPill(
                    label = stringResource(R.string.label_pdf),
                    icon = Icons.Filled.PictureAsPdf,
                    active = true,
                    activeColor = SkyCyan,
                    activeBg = SkyCyanDark,
                    testTag = "btn_export_pdf_top",
                    onClick = onExportPdf,
                )
            }
        }

        // Level Progress Strip
        val lvl = state.selectedLevel
        val target = lvl.targetMeters
        val progressFraction = if (target == null) 1f else (state.hud.distanceMeters / target.toFloat()).coerceIn(0f, 1f)
        Surface(
            color = CockpitPanel,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, borderColor),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 170.dp, max = 230.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "LVL ${lvl.id} · ${lvl.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (target == null) "${state.hud.distanceMeters} m · Endless" else "${state.hud.distanceMeters} / $target m",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(SkyCyan, NvgGreen))),
                    )
                }
            }
        }
    }
}

@Composable
private fun HudStatBlock(
    label: String,
    value: String,
    unit: String,
    nvgActive: Boolean,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = if (nvgActive) NvgGreen else TextPrimary,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = if (nvgActive) NvgGreen.copy(alpha = 0.8f) else TextMuted,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

@Composable
private fun HudActionPill(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    activeBg: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (active) activeBg else CockpitPanel,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) activeColor else CockpitBorder),
        modifier = Modifier.testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeColor else TextPrimary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (active) activeColor else TextPrimary,
            )
        }
    }
}

@Composable
fun BottomControlsBar(
    nvgActive: Boolean,
    onLeftHold: (Boolean) -> Unit,
    onRightHold: (Boolean) -> Unit,
    onBrakeHold: (Boolean) -> Unit,
    onGasHold: (Boolean) -> Unit,
    onToggleNvg: () -> Unit,
) {
    Surface(
        color = Color(0xFF070B12),
        border = BorderStroke(1.dp, CockpitBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldControlButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = null,
                    contentDesc = "Steer Left",
                    accentColor = SkyCyan,
                    testTag = "ctrl_steer_left",
                    onHoldChange = onLeftHold,
                )
                HoldControlButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = null,
                    contentDesc = "Steer Right",
                    accentColor = SkyCyan,
                    testTag = "ctrl_steer_right",
                    onHoldChange = onRightHold,
                )
            }

            Surface(
                onClick = onToggleNvg,
                color = if (nvgActive) NvgGreenDark else CockpitSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (nvgActive) NvgGreen else CockpitBorder),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("ctrl_nvg_bottom"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.label_nvg_btn),
                        tint = if (nvgActive) NvgGreen else TextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.label_nvg_btn),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = if (nvgActive) NvgGreen else TextPrimary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldControlButton(
                    icon = null,
                    label = stringResource(R.string.label_brake),
                    contentDesc = "Brake",
                    accentColor = DangerRed,
                    testTag = "ctrl_brake",
                    onHoldChange = onBrakeHold,
                )
                HoldControlButton(
                    icon = null,
                    label = stringResource(R.string.label_gas),
                    contentDesc = "Accelerate",
                    accentColor = NvgGreen,
                    testTag = "ctrl_gas",
                    onHoldChange = onGasHold,
                )
            }
        }
    }
}

@Composable
private fun HoldControlButton(
    icon: ImageVector?,
    label: String?,
    contentDesc: String,
    accentColor: Color,
    testTag: String,
    onHoldChange: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) Color(0xFF1E293B) else Color(0xFF111827))
            .border(
                width = 1.dp,
                color = if (pressed) accentColor else CockpitBorder,
                shape = RoundedCornerShape(10.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onHoldChange(true)
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                            onHoldChange(false)
                        }
                    },
                )
            }
            .padding(horizontal = 13.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = if (pressed) accentColor else TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = if (pressed) accentColor else TextPrimary,
            )
        }
    }
}
