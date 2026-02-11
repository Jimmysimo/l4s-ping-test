# L4S / ECN TCP Connect Latency Test App

This Android app measures TCP connect latency to a configurable host and port while optionally applying an IP Traffic Class byte (DSCP + ECN). It is designed to diagnose latency changes caused by ECN marking, TrafficClass application timing, and network path behavior.

---

## Purpose

The app helps answer questions like:

- Does enabling ECN increase latency?
- Does applying TrafficClass before connect affect handshake performance?
- Is the latency change caused by ECN itself, or by the act of calling setTrafficClass()?
- Does the network path tolerate ECN-marked packets?

---

## What the app measures

Each sample records:

- DNS resolution time
- TrafficClass set time
- TCP connect time
- Total time

These values allow precise isolation of where latency is introduced.

---

## ECN values used

ECN occupies the lowest 2 bits of the DS byte:

| Value | Meaning |
|------|---------|
| 0x00 | Not ECN capable |
| 0x01 | ECT(1) (used by L4S) |
| 0x02 | ECT(0) |
| 0x03 | CE (normally set by network) |

The app allows controlled testing of these values.

---

## User Interface

Inputs:

- Domain: Hostname to test
- Port: TCP port
- Count: Number of samples
- Interval: Delay between samples

TrafficClass modes:

- Default (no setTrafficClass call)
- Force Not‑ECT (0x00)
- ECT(0) (0x02)
- ECT(1) / L4S (0x01)

Checkbox:

- Apply Traffic Class before connect

Buttons:

- Start — runs selected test
- Run Matrix — runs full diagnostic test matrix
- Stop — cancels current run

Output:

- Displays timing results and summary statistics

---

## Source Code Architecture

Main file:

app/src/main/java/com/example/test_app_l4s_ping_test/MainActivity.kt

Primary components:

MainActivity  
Controls UI and test execution

TrafficClassMode  
Defines ECN/TrafficClass test modes

SampleResult  
Stores timing results for each test

tcpConnectTimings()  
Performs DNS resolution, optional TrafficClass application, and TCP connect timing

runSingleCase()  
Runs repeated tests and computes statistics

percentile()  
Computes p50, p90, and p99 latency values

---

## Built‑in Test Matrix

Automatically runs:

- Default baseline
- Force Not‑ECT
- ECT(1)
- ECT(0)

Each tested both:

- Before connect
- After connect

This isolates handshake‑specific vs steady‑state effects.

---

## Building the APK

Local build:

./gradlew assembleDebug

Output:

app/build/outputs/apk/debug/app-debug.apk

---

## GitHub Actions CI

Workflow builds the debug APK automatically and uploads it as an artifact.

File:

.github/workflows/android-debug-apk.yml

---

## How to interpret results

If Default is fast but Force Not‑ECT is slow:

Calling setTrafficClass itself affects behavior.

If ECN is slow only when applied before connect:

Handshake path intolerance to ECN.

If ECN is slow in all cases:

Network or device stack reacting to ECN traffic.

---

## Recommended diagnostic next step

Capture packets on the server using tcpdump or Wireshark to observe:

- SYN retransmissions
- ECN flags
- DS field values

This confirms network‑level behavior.

---

## License

Internal diagnostic tool.
