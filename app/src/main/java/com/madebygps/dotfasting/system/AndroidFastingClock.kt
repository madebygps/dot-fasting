package com.madebygps.dotfasting.system

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.madebygps.dotfasting.domain.ClockSnapshot
import com.madebygps.dotfasting.domain.FastingClock
import com.madebygps.dotfasting.domain.FastingException

class AndroidFastingClock(context: Context) : FastingClock {
    private val resolver = context.applicationContext.contentResolver

    override fun snapshot(): ClockSnapshot {
        val bootCount = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)
        if (bootCount < 0) throw FastingException.ClockUnavailable()
        return ClockSnapshot(System.currentTimeMillis(), SystemClock.elapsedRealtime(), "boot:$bootCount")
    }
}
