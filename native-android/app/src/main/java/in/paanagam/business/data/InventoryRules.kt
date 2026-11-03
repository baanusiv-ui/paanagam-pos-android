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
