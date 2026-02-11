package com.example.ecn_ping.core

/**
 * ECN bits are the lowest 2 bits of the DS field (Traffic Class / TOS byte).
 *
 * Not-ECT = 0b00 (0x00)
 * ECT(1)  = 0b01 (0x01)  (used by L4S discussions)
 * ECT(0)  = 0b10 (0x02)
 */
enum class PingMode(val label: String, val tosHex: String?) {
    DEFAULT("Default (no ECN override)", null),
    NOT_ECT("Force Not-ECT (0x00)", "0x00"),
    ECT1("ECT(1) (0x01)", "0x01"),
    ECT0("ECT(0) (0x02)", "0x02"),
}
