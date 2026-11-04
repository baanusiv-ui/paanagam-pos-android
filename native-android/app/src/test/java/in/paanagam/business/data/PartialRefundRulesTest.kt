package `in`.paanagam.business.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PartialRefundRulesTest {
    @Test fun calculatesLineRefundInExactPaise() {
        assertEquals(2500L, PartialRefundRules.lineRefundAmount(1250L, 3, 1, 2))
    }

    @Test fun rejectsRefundQuantityAboveRemainingLineQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            PartialRefundRules.lineRefundAmount(1000L, 2, 1, 2)
        }
    }

    @Test fun allocatesSplitTenderByCumulativeProportionWithoutExceedingOriginalTender() {
        val first = PartialRefundRules.allocateTender(10001L, 6001L, 4000L, 0L, 0L, 0L, 3333L)
        val second = PartialRefundRules.allocateTender(10001L, 6001L, 4000L, 3333L, first.cashPaise, first.upiPaise, 3334L)
        val last = PartialRefundRules.allocateTender(10001L, 6001L, 4000L, 6667L, first.cashPaise + second.cashPaise, first.upiPaise + second.upiPaise, 3334L)
        assertEquals(6001L, first.cashPaise + second.cashPaise + last.cashPaise)
        assertEquals(4000L, first.upiPaise + second.upiPaise + last.upiPaise)
    }

    @Test fun rejectsCumulativeRefundAboveOriginalBill() {
        assertThrows(IllegalArgumentException::class.java) {
            PartialRefundRules.allocateTender(1000L, 500L, 500L, 900L, 450L, 450L, 101L)
        }
    }

    @Test fun rejectsPreviouslyRecordedTenderThatDoesNotMatchRefundedTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            PartialRefundRules.allocateTender(1000L, 500L, 500L, 100L, 80L, 10L, 50L)
        }
    }
}
