package `in`.paanagam.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockoutPolicyTest {
    @Test
    fun lockoutStartsOnFifthFailure() {
        assertEquals(5, PinLockoutPolicy.nextFailedAttempt(4))
        assertFalse(PinLockoutPolicy.isLockoutRequired(4))
        assertTrue(PinLockoutPolicy.isLockoutRequired(5))
    }

    @Test
    fun lockoutBackoffDoublesAndCapsAtFifteenMinutes() {
        assertEquals(30_000L, PinLockoutPolicy.lockoutDurationMillis(5))
        assertEquals(60_000L, PinLockoutPolicy.lockoutDurationMillis(6))
        assertEquals(120_000L, PinLockoutPolicy.lockoutDurationMillis(7))
        assertEquals(240_000L, PinLockoutPolicy.lockoutDurationMillis(8))
        assertEquals(480_000L, PinLockoutPolicy.lockoutDurationMillis(9))
        assertEquals(900_000L, PinLockoutPolicy.lockoutDurationMillis(10))
        assertEquals(900_000L, PinLockoutPolicy.lockoutDurationMillis(100))
    }

    @Test
    fun deadlineUsesSuppliedClockAndDuration() {
        assertEquals(1_030_000L, PinLockoutPolicy.lockoutDeadlineMillis(1_000_000L, 5))
    }

    @Test
    fun failedAttemptCounterDoesNotOverflow() {
        assertEquals(Int.MAX_VALUE, PinLockoutPolicy.nextFailedAttempt(Int.MAX_VALUE))
        assertEquals(1, PinLockoutPolicy.nextFailedAttempt(-10))
    }
}
