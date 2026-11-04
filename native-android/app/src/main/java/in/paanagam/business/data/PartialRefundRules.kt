package `in`.paanagam.business.data

import java.math.BigInteger

data class RefundTenderAllocation(val cashPaise: Long, val upiPaise: Long)

/** Pure validation and exact paise allocation for cumulative partial refunds. */
internal object PartialRefundRules {
    fun lineRefundAmount(unitPricePaise: Long, originalQuantity: Int, previouslyRefundedQuantity: Int, requestedQuantity: Int): Long {
        require(unitPricePaise >= 0L)
        require(originalQuantity > 0)
        require(previouslyRefundedQuantity in 0..originalQuantity)
        require(requestedQuantity > 0)
        require(requestedQuantity <= originalQuantity - previouslyRefundedQuantity) {
            "Refund quantity exceeds the remaining quantity on this bill line."
        }
        return Math.multiplyExact(unitPricePaise, requestedQuantity.toLong())
    }

    fun allocateTender(
        originalTotalPaise: Long,
        originalCashPaise: Long,
        originalUpiPaise: Long,
        previouslyRefundedPaise: Long,
        previouslyRefundedCashPaise: Long,
        previouslyRefundedUpiPaise: Long,
        thisRefundPaise: Long
    ): RefundTenderAllocation {
        require(originalTotalPaise > 0L)
        require(originalCashPaise >= 0L && originalUpiPaise >= 0L)
        require(Math.addExact(originalCashPaise, originalUpiPaise) == originalTotalPaise)
        require(previouslyRefundedPaise >= 0L && thisRefundPaise > 0L)
        require(previouslyRefundedCashPaise >= 0L && previouslyRefundedUpiPaise >= 0L)
        require(Math.addExact(previouslyRefundedCashPaise, previouslyRefundedUpiPaise) == previouslyRefundedPaise)
        val cumulative = Math.addExact(previouslyRefundedPaise, thisRefundPaise)
        require(cumulative <= originalTotalPaise) { "Refund exceeds the remaining bill value." }
        val targetCash = BigInteger.valueOf(originalCashPaise)
            .multiply(BigInteger.valueOf(cumulative))
            .divide(BigInteger.valueOf(originalTotalPaise))
            .longValueExact()
        val cash = Math.subtractExact(targetCash, previouslyRefundedCashPaise)
        val upi = Math.subtractExact(thisRefundPaise, cash)
        require(cash >= 0L && upi >= 0L)
        require(Math.addExact(previouslyRefundedCashPaise, cash) <= originalCashPaise)
        require(Math.addExact(previouslyRefundedUpiPaise, upi) <= originalUpiPaise)
        return RefundTenderAllocation(cash, upi)
    }
}
