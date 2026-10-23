# Wireless debugging setup

AETHER pairs with the device over Android's official Wireless debugging
flow (Android 11+, API 30+). Pairing is system-mediated: Android shows the
code, you type it into AETHER. There is no silent activation and no way to
bypass the system dialogs — that is by design.

## Prerequisites

- Android 11 or newer (minSdk 30). See [limitations.md](limitations.md).
- AETHER and the target device on the same Wi-Fi network (pairing with the
  device you are holding = same device, loopback/LAN).

## Steps

### 1. Enable Developer options

1. Open **Settings → About phone**.
2. Tap **Build number** seven times until "You are now a developer" appears.

### 2. Turn on Wireless debugging

1. Open **Settings → System → Developer options**.
2. Enable **Wireless debugging**.

### 3. Pair the device

1. In **Developer options → Wireless debugging**, tap **Pair device with
   pairing code**.
2. Android shows a dialog with a **6-digit pairing code**, an **IP address**,
   and a **pairing port** (e.g. `192.168.1.5:37123`).

### 4. Enter the code in AETHER

1. Open AETHER → **Connect** (or follow the onboarding guide).
2. Enter the **IP address**, the **pairing port**, and the **6-digit code**
   shown by the system dialog. You can paste the code; non-digits are
   filtered automatically.
3. Tap **Pair**. AETHER performs the TLS + SPAKE2 handshake
   (`adb pair` equivalent) and the device authorizes this app's key.
   Pairing succeeds once; the authorization persists until you revoke it
   in system settings (**Wireless debugging → paired devices**).

### 5. Connect

1. In system settings, **Wireless debugging** also shows the separate
   **ADB connection port** (different from the pairing port).
2. Enter it in AETHER and tap **Connect**. The dashboard shows the
   connection state, endpoint details and diagnostics.

## Pairing port vs connection port

They are **not the same**:

| Endpoint | Purpose | When used |
|---|---|---|
| Pairing port (e.g. `:37123`) | One-time trust establishment (TLS + SPAKE2) | Only during pairing |
| ADB connection port (e.g. `:45679`) | The actual ADB session | Every connect |

Both are ephemeral — the system may assign new ones. If pairing starts
failing with a previously working port, check system settings for the
current values.

## Revoking access

**Settings → System → Developer options → Wireless debugging → Paired
devices** — remove the entry for this app's key. AETHER cannot unpair
itself from the device side; revocation is a system operation.

## Troubleshooting

See [troubleshooting.md](troubleshooting.md) for pairing failures,
connection refused, timeouts, auth failures and network changes.
