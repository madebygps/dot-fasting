package com.madebygps.dotfasting.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madebygps.dotfasting.domain.AppSettings
import com.madebygps.dotfasting.domain.FastingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun optInsStartOffAndSettingsPersistWithoutPreset() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.filesDir.resolve("settings-test-${UUID.randomUUID()}.preferences_pb")
        var job = SupervisorJob()
        var refreshes = 0
        try {
            var preferences = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file },
            )
            var settings = SettingsStore(context, preferences) { refreshes++ }
            assertEquals(AppSettings(), settings.settings.first())
            settings.setHighlightArgb(0xFF4BD6A0)
            settings.setLastGoalMillis(90_000)
            settings.setNotificationsEnabled(true)
            settings.setGlyphEnabled(true)
            settings.setCountDown(true)
            assertEquals(5, refreshes)
            job.cancelAndJoin()
            job = SupervisorJob()
            preferences = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file },
            )
            settings = SettingsStore(context, preferences) { refreshes++ }
            assertEquals(AppSettings(0xFF4BD6A0, 90_000, true, true, true), settings.settings.first())
            settings.setLastGoalMillis(null)
            assertEquals(null, settings.settings.first().lastGoalMillis)
            assertTrue(runCatching { settings.setLastGoalMillis(0) }.exceptionOrNull() is FastingException.InvalidGoal)
            assertTrue(runCatching { settings.setHighlightArgb(0x004BD6A0) }.exceptionOrNull() is FastingException.InvalidHighlight)
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }
}
