package `in`.paanagam.business.data

data class LoyaltyBalance(
    val coffeeProgress: Int,
    val freeCoffeesAvailable: Int,
    val lifetimeCoffeeCount: Int
)

object LoyaltyLedgerRules {
    fun calculate(entries: List<LoyaltyLedgerEntry>): LoyaltyBalance {
        val coffeeTotal = entries.fold(0L) { total, entry ->
            Math.addExact(total, entry.coffeeDelta.toLong())
        }
        val rewardAdjustments = entries.fold(0L) { total, entry ->
            Math.addExact(total, entry.rewardDelta.toLong())
        }
        val lifetime = entries.fold(0L) { total, entry ->
            Math.addExact(total, entry.lifetimeDelta.toLong())
        }
        return LoyaltyBalance(
            coffeeProgress = Math.floorMod(coffeeTotal, 10L).toInt(),
            freeCoffeesAvailable = Math.toIntExact(Math.addExact(Math.floorDiv(coffeeTotal, 10L), rewardAdjustments)),
            lifetimeCoffeeCount = Math.toIntExact(lifetime.coerceAtLeast(0L))
        )
    }
}
