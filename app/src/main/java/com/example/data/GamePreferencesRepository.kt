package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "nvg3d_prefs")

data class StoredGameStats(
    val bestDistance: Int = 0,
    val totalRuns: Int = 0,
    val highestClearedLevel: Int = 0,
    val selectedCarIdx: Int = 0,
    val selectedLevelIdx: Int = 0,
    val muted: Boolean = false,
)

class GamePreferencesRepository(private val context: Context) {

    private object Keys {
        val BEST_DISTANCE = intPreferencesKey("best_distance")
        val TOTAL_RUNS = intPreferencesKey("total_runs")
        val HIGHEST_CLEARED_LEVEL = intPreferencesKey("highest_cleared_level")
        val SELECTED_CAR_IDX = intPreferencesKey("selected_car_idx")
        val SELECTED_LEVEL_IDX = intPreferencesKey("selected_level_idx")
        val MUTED = booleanPreferencesKey("muted")
    }

    val statsFlow: Flow<StoredGameStats> = context.gameDataStore.data.map { prefs ->
        StoredGameStats(
            bestDistance = prefs[Keys.BEST_DISTANCE] ?: 0,
            totalRuns = prefs[Keys.TOTAL_RUNS] ?: 0,
            highestClearedLevel = prefs[Keys.HIGHEST_CLEARED_LEVEL] ?: 0,
            selectedCarIdx = prefs[Keys.SELECTED_CAR_IDX] ?: 0,
            selectedLevelIdx = prefs[Keys.SELECTED_LEVEL_IDX] ?: 0,
            muted = prefs[Keys.MUTED] ?: false,
        )
    }

    suspend fun recordRun(distanceMeters: Int, clearedLevelId: Int?) {
        context.gameDataStore.edit { prefs ->
            val prevBest = prefs[Keys.BEST_DISTANCE] ?: 0
            if (distanceMeters > prevBest) {
                prefs[Keys.BEST_DISTANCE] = distanceMeters
            }
            prefs[Keys.TOTAL_RUNS] = (prefs[Keys.TOTAL_RUNS] ?: 0) + 1
            if (clearedLevelId != null) {
                val prevCleared = prefs[Keys.HIGHEST_CLEARED_LEVEL] ?: 0
                if (clearedLevelId > prevCleared) {
                    prefs[Keys.HIGHEST_CLEARED_LEVEL] = clearedLevelId
                }
            }
        }
    }

    suspend fun saveSelectedCar(carIdx: Int) {
        context.gameDataStore.edit { prefs ->
            prefs[Keys.SELECTED_CAR_IDX] = carIdx
        }
    }

    suspend fun saveSelectedLevel(levelIdx: Int) {
        context.gameDataStore.edit { prefs ->
            prefs[Keys.SELECTED_LEVEL_IDX] = levelIdx
        }
    }

    suspend fun saveMuted(muted: Boolean) {
        context.gameDataStore.edit { prefs ->
            prefs[Keys.MUTED] = muted
        }
    }
}
