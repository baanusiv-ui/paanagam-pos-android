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

        // A reversal can make the raw ledger's coffee total negative when a
        // customer has already redeemed a reward earned by a later-refunded
        // bill. Negative paid coffees must not wrap progress to 9 or create
        // phantom negative milestone rewards. The negative reward adjustment
        // remains in the ledger as debt and future milestones offset it.
        val nonNegativeCoffeeTotal = coffeeTotal.coerceAtLeast(0L)
        val earnedMilestones = Math.floorDiv(nonNegativeCoffeeTotal, 10L)
        val rewardBalance = Math.addExact(earnedMilestones, rewardAdjustments)

        return LoyaltyBalance(
            coffeeProgress = Math.floorMod(nonNegativeCoffeeTotal, 10L).toInt(),
            freeCoffeesAvailable = Math.toIntExact(rewardBalance.coerceAtLeast(0L)),
            lifetimeCoffeeCount = Math.toIntExact(lifetime.coerceAtLeast(0L))
        )
    }
}
