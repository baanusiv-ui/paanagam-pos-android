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

    @Test fun perUnitFifoCostAllocatesRoundingRemaindersWithoutLosingPaise() {
        val takes = listOf(
            Triple(0, 1L, 500L),
            Triple(1, 1L, 500L),
            Triple(2, 1L, 500L)
        )
        val allocated = (0..2).map { calculateFifoUnitCostPaise(it, takes) }
        assertEquals(1L, allocated.sum())
        assertEquals(calculateFifoCostPaise(takes.map { it.second to it.third }), allocated.sum())
    }

    @Test fun perUnitFifoCostCombinesMultipleLotsForSameUnit() {
        val takes = listOf(Triple(0, 1L, 500L), Triple(0, 1L, 500L), Triple(1, 2L, 500L))
        assertEquals(1L, calculateFifoUnitCostPaise(0, takes))
        assertEquals(1L, calculateFifoUnitCostPaise(1, takes))
    }

    @Test fun coffeePowderReductionMustBe250GramMultiple() {
        assertTrue(isCoffeePowderReductionAllowed(true, "g", -250000L))
        assertFalse(isCoffeePowderReductionAllowed(true, "g", -250001L))
    }

    @Test fun positiveCountGainsAndNonCoffeeAdjustmentsAreNotRestricted() {
        assertTrue(isCoffeePowderReductionAllowed(true, "g", 12345L))
        assertTrue(isCoffeePowderReductionAllowed(false, "kg", -12345L))
    }

    @Test fun refundMustMatchOriginalCashAndUpiSplitExactly() {
        assertTrue(isRefundSplitValid(10000L, 4000L, 6000L, 4000L, 6000L))
        assertFalse(isRefundSplitValid(10000L, 4000L, 6000L, 10000L, 0L))
        assertFalse(isRefundSplitValid(10000L, 4000L, 6000L, 4000L, 5000L))
        assertFalse(isRefundSplitValid(10000L, -1L, 10001L, -1L, 10001L))
    }
}
