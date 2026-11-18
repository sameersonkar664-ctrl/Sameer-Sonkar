package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.model.GameCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Night Drive 3D", appName)
    }

    @Test
    fun `catalog contains all vehicles and day night levels`() {
        assertEquals(4, GameCatalog.cars.size)
        assertEquals(5, GameCatalog.levels.size)
        assertTrue(GameCatalog.levels.any { it.isNight })
        assertTrue(GameCatalog.levels.any { it.isCycle })
    }

    @Test
    fun `preferences repository persists best distance and selected car`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = GamePreferencesRepository(context)
        repo.saveSelectedCar(2)
        repo.recordRun(distanceMeters = 840, clearedLevelId = 2)
        val stats = repo.statsFlow.first()
        assertEquals(2, stats.selectedCarIdx)
        assertTrue(stats.bestDistance >= 840)
        assertTrue(stats.highestClearedLevel >= 2)
    }
}
