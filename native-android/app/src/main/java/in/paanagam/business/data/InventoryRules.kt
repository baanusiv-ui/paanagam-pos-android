package `in`.paanagam.business.data

import java.math.BigInteger

internal fun calculateFifoCostPaise(lotTakes: List<Pair<Long, Long>>): Long {
    var numerator = BigInteger.ZERO
    lotTakes.forEach { (quantityBaseUnits, unitCostPaise) ->
        require(quantityBaseUnits >= 0L && unitCostPaise >= 0L)
        numerator = numerator.add(BigInteger.valueOf(quantityBaseUnits).multiply(BigInteger.valueOf(unitCostPaise)))
    }
    return numerator.divide(BigInteger.valueOf(1000L)).longValueExact()
}

/** Allocate the exact sale-line/ingredient FIFO cost to one unit without losing paise to repeated rounding. */
internal fun calculateFifoUnitCostPaise(unitIndex: Int, unitLotTakes: List<Triple<Int, Long, Long>>): Long {
    require(unitIndex >= 0)
    require(unitLotTakes.all { it.first >= 0 && it.second >= 0L && it.third >= 0L })
    val prior = calculateFifoCostPaise(unitLotTakes.filter { it.first < unitIndex }.map { it.second to it.third })
    val cumulative = calculateFifoCostPaise(unitLotTakes.filter { it.first <= unitIndex }.map { it.second to it.third })
    return Math.subtractExact(cumulative, prior)
}

internal fun isCoffeePowderReductionAllowed(isCoffeePowder: Boolean, baseUnit: String, varianceBaseUnits: Long): Boolean {
    return !isCoffeePowder || varianceBaseUnits >= 0L ||
        (baseUnit == "g" && varianceBaseUnits % 250000L == 0L)
}

internal fun isRefundSplitValid(originalTotalPaise: Long, originalCashPaise: Long, originalUpiPaise: Long, refundCashPaise: Long, refundUpiPaise: Long): Boolean {
    if (originalTotalPaise < 0L || originalCashPaise < 0L || originalUpiPaise < 0L || refundCashPaise < 0L || refundUpiPaise < 0L) return false
    return runCatching {
        Math.addExact(originalCashPaise, originalUpiPaise) == originalTotalPaise &&
            refundCashPaise == originalCashPaise &&
            refundUpiPaise == originalUpiPaise &&
            Math.addExact(refundCashPaise, refundUpiPaise) == originalTotalPaise
    }.getOrDefault(false)
}
