package `in`.paanagam.business.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LoyaltyLedgerRulesTest {
    private fun entry(type: String, coffee: Int, reward: Int, lifetime: Int) =
        LoyaltyLedgerEntry(type, "9999999999", "sale-$type", type, coffee, reward, lifetime, 1L)

    @Test fun tenPaidCoffeesEarnOneReward() {
        val balance = LoyaltyLedgerRules.calculate(listOf(entry("EARN", 10, 0, 10)))
        assertEquals(0, balance.coffeeProgress)
        assertEquals(1, balance.freeCoffeesAvailable)
        assertEquals(10, balance.lifetimeCoffeeCount)
    }

    @Test fun redemptionConsumesRewardWithoutChangingCoffeeProgress() {
        val balance = LoyaltyLedgerRules.calculate(listOf(
            entry("EARN", 10, 0, 10),
            entry("REDEEM", 0, -1, 0)
        ))
        assertEquals(0, balance.coffeeProgress)
        assertEquals(0, balance.freeCoffeesAvailable)
    }

    @Test fun refundOfEarnedCoffeeAfterRewardWasRedeemedCreatesRewardOffset() {
        val balance = LoyaltyLedgerRules.calculate(listOf(
            entry("EARN", 10, 0, 10),
            entry("REDEEM", 0, -1, 0),
            entry("REVERSAL_EARN", -10, 0, -10)
        ))
        assertEquals(0, balance.coffeeProgress)
        assertEquals(-1, balance.freeCoffeesAvailable)
        assertEquals(0, balance.lifetimeCoffeeCount)
    }

    @Test fun refundOfRedemptionRestoresReward() {
        val balance = LoyaltyLedgerRules.calculate(listOf(
            entry("EARN", 10, 0, 10),
            entry("REDEEM", 0, -1, 0),
            entry("REVERSAL_REDEEM", 0, 1, 0)
        ))
        assertEquals(1, balance.freeCoffeesAvailable)
    }

    @Test fun refundsCanReduceProgressWithoutGoingNegative() {
        val balance = LoyaltyLedgerRules.calculate(listOf(
            entry("EARN", 7, 0, 7),
            entry("REVERSAL_EARN", -3, 0, -3)
        ))
        assertEquals(4, balance.coffeeProgress)
        assertEquals(4, balance.lifetimeCoffeeCount)
    }
}
