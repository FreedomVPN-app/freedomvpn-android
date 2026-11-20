package com.v2ray.ang.freedom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InviteLinkTest {
    @Test
    fun prefersCabinetLink() {
        val info = CabinetApi.ReferralInfo(
            referralLink = "https://cabinet.example.com/?ref=abc",
            botReferralLink = "https://t.me/bot?start=abc",
        )
        assertEquals("https://cabinet.example.com/?ref=abc", inviteLink(info))
    }

    @Test
    fun fallsBackToBotLink() {
        val info = CabinetApi.ReferralInfo(referralLink = "", botReferralLink = " https://t.me/bot?start=abc ")
        assertEquals("https://t.me/bot?start=abc", inviteLink(info))
    }

    @Test
    fun noUsableLink() {
        assertNull(inviteLink(CabinetApi.ReferralInfo()))
        assertNull(inviteLink(CabinetApi.ReferralInfo(referralLink = "javascript:alert(1)")))
    }
}
