package com.breathe2unlock.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

class UsageStatsReader(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** Returns an approximate total foreground-use duration for the current local day. */
    fun todayUsageMillis(): Long {
        if (!hasUsageAccess()) return 0L

        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                ?.filterNot { it.packageName == context.packageName }
                ?.sumOf { it.totalTimeInForeground }
                ?: 0L
        } catch (_: SecurityException) {
            0L
        } catch (_: Exception) {
            0L
        }
    }
}
