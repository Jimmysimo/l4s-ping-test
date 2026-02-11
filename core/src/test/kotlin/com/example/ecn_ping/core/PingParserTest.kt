package com.example.ecn_ping.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PingParserTest {

    @Test
    fun `parses tx rx and loss`() {
        val raw = """
            3 packets transmitted, 2 received, 33% packet loss, time 2003ms
            round-trip min/avg/max = 10.1/20.2/30.3 ms
        """.trimIndent()

        val s = PingParser.parse(raw)
        assertEquals(3, s.transmitted)
        assertEquals(2, s.received)
        assertEquals(33.0, s.packetLossPct)
        assertEquals(10.1, s.minMs)
        assertEquals(20.2, s.avgMs)
        assertEquals(30.3, s.maxMs)
    }
}
