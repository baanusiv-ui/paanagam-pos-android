package `in`.paanagam.business.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRulesTest {
    @Test fun fifoCostAggregatesFractionsAcrossLotsBeforeRounding() {
        assertEquals(1L, calculateFifoCostPaise(listOf(1L to 500L, 1L to 500L)))
    }

    @Test fun fifoCostUsesOriginalLotCostsAndRoundsOnce() {
        assertEquals(1750L, calculateFifoCostPaise(listOf(1000L to 1000L, 500L to 1500L)))
    }

    @Test fun coffeePowderReductionMustBe250GramMultiple() {
        assertTrue(isCoffeePowderReductionAllowed(true, "g", -250000L))
        assertFalse(isCoffeePowderReductionAllowed(true, "g", -250001L))
    }

    @Test fun positiveCountGainsAndNonCoffeeAdjustmentsAreNotRestricted() {
        assertTrue(isCoffeePowderReductionAllowed(true, "g", 12345L))
        assertTrue(isCoffeePowderReductionAllowed(false, "kg", -12345L))
    }
}
