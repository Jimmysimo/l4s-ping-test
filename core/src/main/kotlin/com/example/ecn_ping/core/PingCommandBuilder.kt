package com.example.ecn_ping.core

/**
 * Builds a toybox/iputils-style ping command for Android/Linux.
 *
 * We intentionally follow the same *methodology* as a Windows terminal script:
 * execute the system ping binary with a TOS/DS field override (ECN bits).
 *
 * Note: On Android, 'ping' is typically toybox. toybox ping supports -Q for TOS.
 */
object PingCommandBuilder {

    fun build(
        pingBinary: String,
        host: String,
        mode: PingMode,
        count: Int = 5,
        intervalSeconds: Double = 0.2,
        timeoutSeconds: Int = 2,
        payloadBytes: Int = 56,
    ): PingCommand {
        require(host.isNotBlank()) { "host must not be blank" }
        require(count in 1..1000) { "count must be 1..1000" }
        require(intervalSeconds >= 0.2) { "intervalSeconds must be >= 0.2 (toybox limitation on many devices)" }
        require(timeoutSeconds in 1..60) { "timeoutSeconds must be 1..60" }
        require(payloadBytes in 0..1400) { "payloadBytes must be 0..1400" }

        val argv = mutableListOf<String>()
        argv += pingBinary

        // toybox ping flags (also generally compatible with iputils):
        // -c count
        // -i interval seconds
        // -W timeout (per-ping) seconds
        // -s payload size
        argv += listOf("-c", count.toString())
        argv += listOf("-i", intervalSeconds.toString())
        argv += listOf("-W", timeoutSeconds.toString())
        argv += listOf("-s", payloadBytes.toString())

        // TOS / DS field override. We only modify ECN bits (0x00/0x01/0x02).
        // If mode.DEFAULT, we omit -Q entirely for a true baseline.
        mode.tosHex?.let { tos ->
            argv += listOf("-Q", tos)
        }

        argv += host

        return PingCommand(
            argv = argv,
            display = argv.joinToString(" "),
        )
    }
}
