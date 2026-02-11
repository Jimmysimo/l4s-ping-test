package com.example.ecn_ping.core

data class PingSummary(
    val transmitted: Int?,
    val received: Int?,
    val packetLossPct: Double?,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val rawOutput: String,
)

object PingParser {

    // Supports typical 'ping' summary lines.
    // Example:
    // 5 packets transmitted, 5 received, 0% packet loss, time 4006ms
    private val TXRX = Regex("(\d+) packets transmitted, (\d+) (?:packets )?received, (\d+)% packet loss")
    // Example (iputils): rtt min/avg/max/mdev = 9.123/10.456/11.789/0.123 ms
    // Example (toybox): round-trip min/avg/max = 9.123/10.456/11.789 ms
    private val RTT = Regex("(?:rtt|round-trip) (?:min/avg/max(?:/mdev)?|min/avg/max) = ([0-9.]+)/([0-9.]+)/([0-9.]+)")

    fun parse(raw: String): PingSummary {
        val txrx = TXRX.find(raw)
        val transmitted = txrx?.groupValues?.getOrNull(1)?.toIntOrNull()
        val received = txrx?.groupValues?.getOrNull(2)?.toIntOrNull()
        val lossPct = txrx?.groupValues?.getOrNull(3)?.toDoubleOrNull()

        val rtt = RTT.find(raw)
        val minMs = rtt?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val avgMs = rtt?.groupValues?.getOrNull(2)?.toDoubleOrNull()
        val maxMs = rtt?.groupValues?.getOrNull(3)?.toDoubleOrNull()

        return PingSummary(
            transmitted = transmitted,
            received = received,
            packetLossPct = lossPct,
            minMs = minMs,
            avgMs = avgMs,
            maxMs = maxMs,
            rawOutput = raw,
        )
    }
}
