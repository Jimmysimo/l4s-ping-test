# ECN Ping (Android)

This app runs ICMP echo requests using the **system `ping` binary** on Android and optionally overrides the IP DS field (TOS/Traffic Class) using the `-Q` flag.

It supports three primary modes:
- **Default**: do not call `-Q` at all (true baseline)
- **ECT(1)**: `-Q 0x01` (sets ECN bits to `01`)
- **ECT(0)**: `-Q 0x02` (sets ECN bits to `10`)

> Why this approach?
> 
> Standard Android app sockets do not reliably propagate ECN marking to the wire on modern devices (e.g., Pixel 8).
> This tool follows the same methodology as a Windows terminal script: delegate packet generation to a mature system tool (`ping`)
> that already knows how to set the DS field.

---

## How it works

- The app locates a usable ping command (usually `/system/bin/ping` on Pixel devices).
- It builds a ping command with:
  - `-c` count
  - `-i` interval
  - `-W` timeout
  - `-s` payload size
  - optional `-Q <tos>` where `<tos>` is one of `0x00`, `0x01`, `0x02`
- It executes the command with `ProcessBuilder` and parses the summary output.

Core logic (command building + parsing) lives in the `:core` module so we can unit test it without Android.

---

## Test cases

Unit tests are in `core/src/test/...` and validate:

1. **DEFAULT mode omits `-Q`** to preserve a true baseline.
2. **ECT(1) mode includes `-Q 0x01`**
3. **ECT(0) mode includes `-Q 0x02`**
4. **NOT_ECT mode includes `-Q 0x00`** (included for completeness)

Parser test validates extraction of tx/rx/loss and rtt min/avg/max.

CI runs these tests with `:core:test` before building the APK.

---

## Verifying ECN bits on the wire (PCAP)

A unit test cannot prove the on-wire DS field bits (that requires packet capture).
To verify the ECN bits:

### Recommended LAN method
1. Run a simple TCP/ICMP capture point:
   - capture on the destination host (Linux `tcpdump`) **or**
   - capture on Android itself (requires a tcpdump binary) **or**
   - make your PC the capture endpoint

2. In Wireshark, inspect the IP header:
   - **Internet Protocol v4 → Differentiated Services Field**
   - Confirm ECN changes between Not-ECT / ECT(1) / ECT(0)

---

## Build locally

If you have Android Studio installed:

- Open the project
- Build **Debug** APK

---

## GitHub Actions

Workflow: `.github/workflows/android-debug-apk.yml`

On every push/PR:
- runs `:core:test`
- builds `:app:assembleDebug`
- uploads the debug APK as an artifact

Download the artifact from the Actions run and install it on your Pixel 8.

---

## Notes / limitations

- ICMP permissions depend on device configuration. Most Pixels allow unprivileged ping via the system `ping` binary.
- If your build of ping does not support `-Q`, the app will show an error in the raw output.
