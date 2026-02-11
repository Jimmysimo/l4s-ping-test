package com.example.ecn_ping.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PingCommandBuilderTest {

    @Test
    fun `DEFAULT mode omits -Q for true baseline`() {
        val cmd = PingCommandBuilder.build(
            pingBinary = "ping",
            host = "192.168.1.1",
            mode = PingMode.DEFAULT,
            count = 3,
            intervalSeconds = 0.2,
            timeoutSeconds = 2,
            payloadBytes = 56,
        )
        assertTrue(cmd.argv.contains("ping"))
        assertTrue(!cmd.argv.contains("-Q"), "DEFAULT should not include -Q")
        assertEquals("192.168.1.1", cmd.argv.last())
    }

    @Test
    fun `ECT1 mode includes -Q 0x01`() {
        val cmd = PingCommandBuilder.build("ping", "example.com", PingMode.ECT1)
        val qIndex = cmd.argv.indexOf("-Q")
        assertTrue(qIndex >= 0, "Expected -Q flag")
        assertEquals("0x01", cmd.argv[qIndex + 1])
    }

    @Test
    fun `ECT0 mode includes -Q 0x02`() {
        val cmd = PingCommandBuilder.build("ping", "example.com", PingMode.ECT0)
        val qIndex = cmd.argv.indexOf("-Q")
        assertTrue(qIndex >= 0, "Expected -Q flag")
        assertEquals("0x02", cmd.argv[qIndex + 1])
    }

    @Test
    fun `NOT_ECT includes -Q 0x00`() {
        val cmd = PingCommandBuilder.build("ping", "example.com", PingMode.NOT_ECT)
        val qIndex = cmd.argv.indexOf("-Q")
        assertTrue(qIndex >= 0, "Expected -Q flag")
        assertEquals("0x00", cmd.argv[qIndex + 1])
    }
}
