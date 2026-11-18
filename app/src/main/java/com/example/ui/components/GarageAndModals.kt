package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CarSpec
import com.example.model.GameCatalog
import com.example.model.LevelSpec
import com.example.ui.GameUiState
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NvgGreen
import com.example.ui.theme.NvgGreenDark
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SkyCyanDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun GarageStartModal(
    state: GameUiState,
    onSelectCar: (Int) -> Unit,
    onSelectLevel: (Int) -> Unit,
    onStartDrive: () -> Unit,
    onConvertToPdf: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB803060C))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xF00B121E),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, CockpitBorder),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Hero Artwork Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_garage_hero),
                        contentDescription = "Night Vision Drive 3D Highway Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC0B121E)),
                                ),
                            ),
                    )
                    Surface(
                        color = NvgGreen.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, NvgGreen.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.badge_hd_gyro),
                            style = MaterialTheme.typography.labelSmall,
                            color = NvgGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.title_game),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.subtitle_controls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(14.dp))
                SectionLabel(text = stringResource(R.string.section_select_vehicle))
                Spacer(modifier = Modifier.height(6.dp))

                // 2x2 Car Grid
                val cars = GameCatalog.cars
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowIdx in cars.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CarSelectionCard(
                                car = cars[rowIdx],
                                selected = state.selectedCarIdx == rowIdx,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelectCar(rowIdx) },
                            )
                            if (rowIdx + 1 < cars.size) {
                                CarSelectionCard(
                                    car = cars[rowIdx + 1],
                                    selected = state.selectedCarIdx == rowIdx + 1,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSelectCar(rowIdx + 1) },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SectionLabel(text = stringResource(R.string.section_select_level))
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(GameCatalog.levels) { idx, level ->
                        LevelPillCard(
                            level = level,
                            selected = state.selectedLevelIdx == idx,
                            onClick = { onSelectLevel(idx) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartDrive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NvgGreen,
                        contentColor = Color(0xFF03150A),
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_start_drive"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_start_drive),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onConvertToPdf,
                    border = BorderStroke(1.dp, SkyCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SkyCyanDark.copy(alpha = 0.55f),
                        contentColor = SkyCyan,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_convert_pdf_menu"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_convert_pdf),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = TextMuted,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CarSelectionCard(
    car: CarSpec,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) SkyCyan.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) SkyCyan else Color.White.copy(alpha = 0.12f)),
        modifier = modifier.testTag("car_card_${car.id}"),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = car.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(car.accentColor),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = car.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun LevelPillCard(
    level: LevelSpec,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) NvgGreen.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) NvgGreen else Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.testTag("level_pill_${level.id}"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "LVL ${level.id}: ${level.name}",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val targetStr = level.targetMeters?.let { "${it}m" } ?: "Endless"
            Text(
                text = "${level.timeLabel} · $targetStr",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
fun ResultModal(
    isVictory: Boolean,
    state: GameUiState,
    onPrimaryAction: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    val badgeColor = if (isVictory) NvgGreen else DangerRed
    val title = if (isVictory) {
        "Level ${state.selectedLevel.id} Complete!"
    } else {
        stringResource(R.string.title_vehicle_disabled)
    }
    val nextLevel = GameCatalog.levels.getOrNull(state.selectedLevelIdx + 1)
    val subtitle = if (isVictory) {
        if (nextLevel != null) {
            "Cleared ${state.selectedLevel.name}. Next: Level ${nextLevel.id} ${nextLevel.name}."
        } else {
            "All highway sectors mastered! Continue into endless day-night mode."
        }
    } else {
        stringResource(R.string.desc_vehicle_disabled)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB803060C))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color(0xF00B121E),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, CockpitBorder),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = stringResource(
                            if (isVictory) R.string.label_sector_cleared else R.string.label_collision_detected,
                        ).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ScoreSummaryBox(
                        label = stringResource(if (isVictory) R.string.label_stage_distance else R.string.label_distance),
                        value = "${state.hud.distanceMeters} m",
                        modifier = Modifier.weight(1f),
                    )
                    ScoreSummaryBox(
                        label = stringResource(if (isVictory) R.string.label_total_best else R.string.label_best_run),
                        value = "${state.bestDistance} m",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NvgGreen,
                        contentColor = Color(0xFF03150A),
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(if (isVictory) "btn_next_level" else "btn_retry_level"),
                ) {
                    Text(
                        text = stringResource(if (isVictory) R.string.action_next_level else R.string.action_retry_level),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onExportPdf,
                    border = BorderStroke(1.dp, SkyCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SkyCyanDark.copy(alpha = 0.45f),
                        contentColor = SkyCyan,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_export_pdf_result"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_export_run_pdf),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenGarage,
                    border = BorderStroke(1.dp, CockpitBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CockpitSurface,
                        contentColor = TextPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_result_garage"),
                ) {
                    Text(
                        text = stringResource(
                            if (isVictory) R.string.action_back_garage else R.string.action_change_car_level,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreSummaryBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                color = TextPrimary,
            )
        }
    }
}
