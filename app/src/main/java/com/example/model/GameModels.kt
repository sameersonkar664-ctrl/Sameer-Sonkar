package com.example.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import java.io.File

data class CarSpec(
    val id: String,
    val name: String,
    val colorInt: Int,
    val accentColor: Color,
    val maxSpeed: Float,
    val handling: Float,
    val battDrain: Float,
    val description: String,
) {
    val topKmhDisplay: Int
        get() = when (id) {
            "viper" -> 150
            "shadow" -> 168
            "apex" -> 186
            else -> 204
        }
}

data class LevelSpec(
    val id: Int,
    val name: String,
    val timeLabel: String,
    val targetMeters: Int?, // null = Endless
    val skyColor: Int,
    val fogColor: Int,
    val fogDensity: Float,
    val ambientColor: Int,
    val ambientIntensity: Float,
    val sunColor: Int,
    val sunIntensity: Float,
    val roadColor: Int,
    val trafficRate: Float,
    val speedMult: Float,
    val isNight: Boolean,
    val isCycle: Boolean = false,
)

enum class GamePhase {
    MENU,
    PLAYING,
    LEVEL_UP,
    GAME_OVER,
}

data class HudSnapshot(
    val speedKmh: Int = 0,
    val speedRatio: Float = 0f,
    val distanceMeters: Int = 0,
    val batteryPercent: Int = 100,
    val nvgActive: Boolean = false,
    val phase: GamePhase = GamePhase.MENU,
)

data class RunRecord(
    val timestampLabel: String,
    val carName: String,
    val levelName: String,
    val distanceMeters: Int,
    val cleared: Boolean,
    val avgBatteryLeft: Int,
)

sealed interface PdfUiState {
    data object Hidden : PdfUiState
    data object Generating : PdfUiState
    data class Preview(
        val file: File,
        val pages: List<Bitmap>,
        val statusMessage: String? = null,
    ) : PdfUiState
    data class Error(val message: String) : PdfUiState
}

object GameCatalog {
    val cars: List<CarSpec> = listOf(
        CarSpec(
            id = "viper",
            name = "Viper GT",
            colorInt = 0x0EA5E9,
            accentColor = Color(0xFF38BDF8),
            maxSpeed = 76f,
            handling = 1.08f,
            battDrain = 8.5f,
            description = "Balanced sports coupe · Sharp steering",
        ),
        CarSpec(
            id = "shadow",
            name = "Shadow SUV",
            colorInt = 0x475569,
            accentColor = Color(0xFF94A3B8),
            maxSpeed = 66f,
            handling = 0.92f,
            battDrain = 5.0f,
            description = "Armored 4x4 · 2x NVG battery life",
        ),
        CarSpec(
            id = "apex",
            name = "Apex Hyper",
            colorInt = 0xEF4444,
            accentColor = Color(0xFFF87171),
            maxSpeed = 92f,
            handling = 1.15f,
            battDrain = 9.5f,
            description = "Ultra-fast supercar · High top speed",
        ),
        CarSpec(
            id = "phantom",
            name = "Phantom Muscle",
            colorInt = 0xF59E0B,
            accentColor = Color(0xFFFBBF24),
            maxSpeed = 82f,
            handling = 1.24f,
            battDrain = 8.0f,
            description = "Drift interceptor · Snappy response",
        ),
    )

    val levels: List<LevelSpec> = listOf(
        LevelSpec(
            id = 1,
            name = "Sunset Highway",
            timeLabel = "DUSK",
            targetMeters = 500,
            skyColor = 0xD97742,
            fogColor = 0x9A4C32,
            fogDensity = 0.0085f,
            ambientColor = 0xFFB380,
            ambientIntensity = 0.95f,
            sunColor = 0xFFD29D,
            sunIntensity = 1.3f,
            roadColor = 0x1F2430,
            trafficRate = 1.25f,
            speedMult = 1.0f,
            isNight = false,
        ),
        LevelSpec(
            id = 2,
            name = "Midnight Blackout",
            timeLabel = "NIGHT",
            targetMeters = 750,
            skyColor = 0x02040A,
            fogColor = 0x02040A,
            fogDensity = 0.022f,
            ambientColor = 0x111A2E,
            ambientIntensity = 0.25f,
            sunColor = 0x4B6584,
            sunIntensity = 0.25f,
            roadColor = 0x0D111A,
            trafficRate = 0.98f,
            speedMult = 1.08f,
            isNight = true,
        ),
        LevelSpec(
            id = 3,
            name = "Morning Daylight",
            timeLabel = "DAY",
            targetMeters = 1000,
            skyColor = 0x38BDF8,
            fogColor = 0x7DD3FC,
            fogDensity = 0.0065f,
            ambientColor = 0xE0F2FE,
            ambientIntensity = 1.3f,
            sunColor = 0xFFFFFF,
            sunIntensity = 1.6f,
            roadColor = 0x2A3140,
            trafficRate = 0.82f,
            speedMult = 1.15f,
            isNight = false,
        ),
        LevelSpec(
            id = 4,
            name = "Tactical Night Ops",
            timeLabel = "NIGHT FOG",
            targetMeters = 1300,
            skyColor = 0x010306,
            fogColor = 0x030712,
            fogDensity = 0.028f,
            ambientColor = 0x0F172A,
            ambientIntensity = 0.18f,
            sunColor = 0x334155,
            sunIntensity = 0.2f,
            roadColor = 0x090D14,
            trafficRate = 0.72f,
            speedMult = 1.22f,
            isNight = true,
        ),
        LevelSpec(
            id = 5,
            name = "Endless Day-Night",
            timeLabel = "CYCLE",
            targetMeters = null,
            skyColor = 0x38BDF8,
            fogColor = 0x7DD3FC,
            fogDensity = 0.01f,
            ambientColor = 0xDBEAFE,
            ambientIntensity = 1.0f,
            sunColor = 0xFFFFFF,
            sunIntensity = 1.3f,
            roadColor = 0x1E2533,
            trafficRate = 0.68f,
            speedMult = 1.26f,
            isNight = false,
            isCycle = true,
        ),
    )
}
