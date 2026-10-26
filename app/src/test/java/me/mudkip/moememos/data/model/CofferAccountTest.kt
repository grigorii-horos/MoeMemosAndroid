package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CofferAccountTest {
    @Test
    fun cofferAccountRoundTripsThroughUserData() {
        val info = MemosAccount(host = "https://c.lan", remoteIdentifier = "1")
        val key = Account.Coffer(info).accountKey()
        val parsed = Account.parseUserData(UserData(accountKey = key, coffer = info))
        assertTrue(parsed is Account.Coffer)
        assertEquals(key, parsed!!.accountKey())
        assertEquals(UserData.AccountCase.COFFER, UserData(coffer = info).accountCase)
    }
}
