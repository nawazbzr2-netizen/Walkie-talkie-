# PhoneLink

Phone-to-phone calls, video calls, chat, voice messages and file transfer — **without phone numbers** —
over Wi-Fi, mobile hotspot, Bluetooth or the internet.

| Feature | Wi-Fi / Hotspot | Bluetooth | Internet |
|---|---|---|---|
| Device list with IP address, tap to connect | ✅ (mDNS + subnet scan + manual IP) | ✅ paired phones | ✅ by ID code |
| Several phones at once | ✅ ("Connect all") | ✅ | ✅ |
| Voice call, no time limit | ✅ WebRTC | ✅ audio stream | ✅ WebRTC |
| Video call | ✅ | ❌ (Bluetooth is too slow) | ✅ |
| Text messages | ✅ | ✅ | ✅ |
| Voice messages (hold mic) | ✅ | ✅ | ✅ |
| Files of any type | ✅ fast | ✅ slow | ✅ via server (small files) |
| Incoming call ringtone | ✅ | ✅ | ✅ |
| Auto-reconnect after drop | ✅ | ✅ | ✅ |
| Call-stuck protection (30s timeout + full cleanup) | ✅ | ✅ | ✅ |

See docs/GUIDE_UR.md for Urdu guide.

Build: push to GitHub → Actions → Build APK → download artifact PhoneLink-debug-apk.
