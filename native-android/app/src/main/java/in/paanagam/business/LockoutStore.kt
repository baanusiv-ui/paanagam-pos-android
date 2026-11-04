package `in`.paanagam.business

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Persistent PIN-throttling state that (a) is written synchronously with commit(),
 * so killing the process cannot drop a failed attempt, and (b) measures lockouts on the
 * monotonic clock, so changing the wall clock does not shorten them. After a reboot the
 * monotonic clock restarts, so a lockout still pending is conservatively restarted in full.
 * Use one instance per prefs name: "paanagam_login_security" and "paanagam_reauth_security".
 */
internal class LockoutStore(context: Context, prefsName: String) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun failedAttempts(id: String): Int = prefs.getInt("attempts_$id", 0)

    fun remainingMillis(id: String): Long {
        val duration = prefs.getLong("duration_$id", 0L)
        if (duration == 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        if (prefs.getInt("boot_$id", bootCount) != bootCount) {
            prefs.edit().putLong("until_$id", now + duration).putInt("boot_$id", bootCount).commit()
            return duration
        }
        val remaining = (prefs.getLong("until_$id", 0L) - now).coerceAtLeast(0L)
        if (remaining == 0L) prefs.edit().remove("duration_$id").commit()
        return remaining
    }

    /** Records a failed PIN. Returns (attempts so far, lockout duration in ms or 0 if none). */
    fun recordFailure(id: String): Pair<Int, Long> {
        val attempts = PinLockoutPolicy.nextFailedAttempt(failedAttempts(id))
        val editor = prefs.edit().putInt("attempts_$id", attempts)
        var duration = 0L
        if (PinLockoutPolicy.isLockoutRequired(attempts)) {
            duration = PinLockoutPolicy.lockoutDurationMillis(attempts)
            editor.putLong("duration_$id", duration)
                .putLong("until_$id", SystemClock.elapsedRealtime() + duration)
                .putInt("boot_$id", bootCount)
        }
        check(editor.commit()) { "Could not persist the PIN attempt counter." }
        return attempts to duration
    }

    fun clear(id: String) {
        prefs.edit().remove("attempts_$id").remove("duration_$id").remove("until_$id").remove("boot_$id").commit()
    }
}
