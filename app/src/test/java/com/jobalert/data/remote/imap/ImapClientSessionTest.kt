package com.jobalert.data.remote.imap

import com.jobalert.domain.model.EmailAccount
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImapClientSessionTest {

    @Test
    fun `buildSessionProps contains host and port`() {
        val account = EmailAccount(email = "u@example.com", host = "imap.example.com", port = 993, useSsl = true)
        val props = ImapClient.buildSessionProps(account)
        assertNotNull(props["mail.imaps.host"])
        assertNotNull(props["mail.imaps.port"])
    }

    @Test
    fun `buildSessionProps does not include SASL props`() {
        val account = EmailAccount(email = "u@example.com", host = "imap.example.com")
        val props = ImapClient.buildSessionProps(account)
        assertNull(props["mail.imaps.sasl.enable"])
        assertNull(props["mail.imaps.sasl.mechanisms"])
    }
}
