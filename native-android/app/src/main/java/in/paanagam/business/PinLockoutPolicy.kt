package `in`.paanagam.business

/**
 * Pure PIN throttling rules. Persist the returned attempt count and deadline in app-private storage.
 * A successful authentication must clear both persisted values.
 */
internal object PinLockoutPolicy {
    const val MAX_DELAY_MILLIS = 15 * 60 * 1000L
    private const val FIRST_LOCKOUT_ATTEMPT = 5
    private const val INITIAL_DELAY_MILLIS = 30 * 1000L

    fun nextFailedAttempt(previousAttempts: Int): Int =
        if (previousAttempts == Int.MAX_VALUE) Int.MAX_VALUE else (previousAttempts.coerceAtLeast(0) + 1)

    fun isLockoutRequired(attempts: Int): Boolean = attempts >= FIRST_LOCKOUT_ATTEMPT

    fun lockoutDurationMillis(attempts: Int): Long {
        require(isLockoutRequired(attempts)) { "Lockout starts after five failed attempts." }
        val exponent = (attempts - FIRST_LOCKOUT_ATTEMPT).coerceAtMost(5)
        return (INITIAL_DELAY_MILLIS * (1L shl exponent)).coerceAtMost(MAX_DELAY_MILLIS)
    }

    fun lockoutDeadlineMillis(nowMillis: Long, attempts: Int): Long =
        Math.addExact(nowMillis, lockoutDurationMillis(attempts))
}
