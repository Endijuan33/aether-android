# Wireless Debugging: notification Pair & Connect

Android 11+ is required. AETHER manages this Android device; it does not connect to another phone or silently enable debugging.

1. Open AETHER and tap **PAIR & CONNECT**. Allow notification permission on Android 13+ and local-network permission on Android 17/SDK 37.
2. Tap **Open Developer options**. If hidden, use Android Settings → About phone → Build number seven times.
3. Enable **Wireless debugging** and open **Pair device with pairing code**. Keep that system dialog open.
4. Expand **AETHER — Pairing Service Found** in the notification shade. Tap **PAIR**, enter the six-digit code in **Wi-Fi pairing code**, and submit.
5. AETHER performs the existing TLS/SPAKE2 pairing protocol, discovers the separate ADB connection service, establishes transport, and verifies `id -u` returns exactly `2000`. Only then does the dashboard show **Connected — shell verified**.

Pairing and connection are separate verified states. Pairing success alone does not enable management. If connection fails, keep Wireless debugging enabled and use **Reconnect with existing pairing identity**; no code is needed unless Android revoked the identity.

## Notification and discovery recovery

- Permission denied or pairing channel blocked: open **notification settings** or **app permissions** from the guide. Alternatively, tap **Start in-app discovery fallback** and enter the discovered code while keeping the system dialog open (split screen may help).
- Local-network permission denied on SDK 37: allow it in Android app permissions. In-app/manual setup cannot bypass Android network protection.
- Service not found: open the system pairing-code dialog, check Wi-Fi and foreground/background access. AETHER stops discovery after a bounded session; retry explicitly.
- The native inline keyboard is controlled by SystemUI/OEM. AETHER strictly accepts exactly six ASCII digits, including leading zeroes, and rejects spaces, letters or extra digits.
- Code rejected/expired: use the current code; duplicate and stale replies are ignored.
- Notification dismissed/service stopped: reopen AETHER and start a new session. Codes are never redelivered after process recreation.
- OEM notification shade missing inline reply: use the in-app fallback. No notification-listener permission or third-party app is required.

## Advanced manual setup

Under **Settings → Advanced manual pairing & connection**, enter the device’s current local IP, pairing port and code. Then use the separate connection port displayed in Wireless debugging. Saved ports are hints and may be stale; automatic flow always discovers a fresh endpoint. Cancel any active notification session before using manual setup.

## Revocation and lifecycle

Android **Wireless debugging → Paired devices** owns key revocation. AETHER retains its AndroidKeyStore identity; it never stores the code. The short pairing foreground service stops after success/failure/cancellation. Shell access lasts only while AETHER’s process/session remains alive. Process death disconnects ADB; restart and explicitly reconnect. Package mutations are never replayed.

Hardware validation still pending. The implementation and automated fixtures do not prove device/OEM interoperability. See [notification-pairing-redesign.md](notification-pairing-redesign.md) and [limitations.md](limitations.md).
