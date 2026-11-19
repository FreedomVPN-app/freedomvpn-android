package com.v2ray.ang.freedom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OfferNotifierTest {
    private fun offer(id: Int, pct: Int?, active: Boolean = true, claimed: Boolean = false) =
        CabinetApi.PromoOffer(id = id, discountPercent = pct, isActive = active, isClaimed = claimed)

    @Test
    fun picksOnlyNewActiveUnclaimedDiscounts() {
        val offers = listOf(
            offer(1, 30),
            offer(2, 15, claimed = true),
            offer(3, 20, active = false),
            offer(4, null),
            offer(5, 10),
        )
        assertEquals(listOf(1, 5), OfferNotifier.newOffers(offers, emptySet()).map { it.id })
        assertEquals(listOf(5), OfferNotifier.newOffers(offers, setOf(1)).map { it.id })
        assertEquals(emptyList<Int>(), OfferNotifier.newOffers(emptyList(), emptySet()).map { it.id })
    }

    @Test
    fun notifiedIdsRoundTripAndStayBounded() {
        assertEquals(setOf(1, 2, 3), OfferNotifier.parseNotified("1, 2,x,3,"))
        assertEquals(emptySet<Int>(), OfferNotifier.parseNotified(null))
        val many = (1..80).toList()
        val encoded = OfferNotifier.encodeNotified(many)
        assertEquals((31..80).toSet(), OfferNotifier.parseNotified(encoded))
    }
}
