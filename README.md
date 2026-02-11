# test_app_l4s_ping_test

Android app to measure **TCP connect RTT** (a practical "ping-like" test) to a configurable domain/port, with a toggle for ECN marking:
- **Not-ECT** (0x00)
- **ECT(0)** (0x02)
- **ECT(1) / L4S** (0x01)

> Note: ICMP ping requires raw sockets (root/privileged). This app uses TCP connect timing instead.

## Build an APK without local setup (GitHub Actions)
1. Create a new GitHub repo and upload the contents of this folder.
2. Go to **Actions** → run **Build Debug APK** (or just push to `main`).
3. Download the `app-debug.apk` artifact and install it on your Pixel.

## Install on Pixel
- Transfer the downloaded `app-debug.apk` to your phone and open it.
- You may need to allow installing unknown apps for your file manager/Chrome.

## Verify ECT(1) actually leaves the device
Some networks/middleboxes remark ECN bits. Capture on your AP/router uplink and confirm **ECT(1)** on outgoing packets.
