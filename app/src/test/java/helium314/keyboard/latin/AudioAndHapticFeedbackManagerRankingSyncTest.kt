// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import helium314.keyboard.latin.dogakdogak.AppClickCountRepository
import helium314.keyboard.latin.dogakdogak.ClickCountRepository
import helium314.keyboard.latin.dogakdogak.PrefsKeys
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AudioAndHapticFeedbackManagerRankingSyncTest {
    private lateinit var context: Context
    private lateinit var syncPrefs: SharedPreferences
    private lateinit var counterPrefs: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        syncPrefs = DeviceProtectedUtils.getSharedPreferences(context)
        syncPrefs.edit().clear().commit()
        counterPrefs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            (context.createDeviceProtectedStorageContext() ?: context)
                .getSharedPreferences("dogakdogak_counters", Context.MODE_PRIVATE)
        } else {
            context.getSharedPreferences("dogakdogak_counters", Context.MODE_PRIVATE)
        }
        counterPrefs.edit().clear().commit()
        runCatching {
            WorkManager.initialize(
                context,
                Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).build()
            )
        }
    }

    @Test
    fun flushPendingCounters_touchOnlyImeUsage_updatesCountersAndEnqueuesSync() {
        val manager = newManager()
        val clickRepo = newClickRepo(counterPrefs)
        clickRepo.setCurrentUserId("user1")
        val appRepo = newAppRepo(counterPrefs)
        appRepo.setCurrentUserId("user1")

        setField(manager, "mAppContext", context)
        setField(manager, "mPrefs", syncPrefs)
        setField(manager, "mClickCountRepo", clickRepo)
        setField(manager, "mAppClickCountRepo", appRepo)
        setField(manager, "mPendingTouchDelta", 3L)

        @Suppress("UNCHECKED_CAST")
        val pendingAppTouches = getField(manager, "mPendingAppTouchDeltas") as MutableMap<String, Long>
        pendingAppTouches["com.everytime.v2"] = 3L

        invokeFlushPendingCounters(manager)

        assertEquals(3L, clickRepo.getDailyTouchesValue())
        assertEquals(mapOf("com.everytime.v2" to 3L), appRepo.getAllDailyTouches())
        assertTrue(syncPrefs.getLong(PrefsKeys.RANKING_SYNC_LAST_ENQUEUED_AT, 0L) > 0L)
    }

    private fun newManager(): AudioAndHapticFeedbackManager {
        val constructor = AudioAndHapticFeedbackManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun newClickRepo(prefs: SharedPreferences): ClickCountRepository {
        val constructor = ClickCountRepository::class.java.getDeclaredConstructor(SharedPreferences::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(prefs)
    }

    private fun newAppRepo(prefs: SharedPreferences): AppClickCountRepository {
        val constructor = AppClickCountRepository::class.java.getDeclaredConstructor(SharedPreferences::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(prefs)
    }

    private fun invokeFlushPendingCounters(manager: AudioAndHapticFeedbackManager) {
        val method = AudioAndHapticFeedbackManager::class.java.getDeclaredMethod("flushPendingCounters")
        method.isAccessible = true
        method.invoke(manager)
    }

    private fun setField(target: Any, name: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun getField(target: Any, name: String): Any? {
        val field: Field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }
}
