# Polaris Token Extraction — GT-S Board

Goal: obtain the 20-byte static auth token that the Future Motion app writes to the Onewheel UART on connect. Once captured, this token unlocks your board's telemetry characteristics over BLE without your phone in the loop, so the Karoo extension can read them directly.

**One-time procedure. The token is board-specific and persists across firmware updates.**

## What you need

- Android phone with the Onewheel FM app installed and able to connect to your board
- USB cable + a laptop
- ADB installed (`platform-tools`), or a recent Android Studio
- Your board nearby, charged, not currently connected to any other BLE master

## Step 1 — Enable HCI snoop log on Android

1. On your Android phone: **Settings → About phone → tap "Build number" 7 times** (enables Developer options)
2. **Settings → System → Developer options**
3. Scroll to **"Enable Bluetooth HCI snoop log"** → toggle **ON**
4. Scroll to **"Bluetooth HCI snoop log"** (the detail pane) → ensure enabled
5. **Restart Bluetooth**: Settings → Connected devices → Bluetooth → toggle OFF, wait 3s, toggle ON
   *(the snoop log only starts capturing after a BT restart following enablement)*

## Step 2 — Capture an unlock

1. Close the FM app (force-stop it so the connection fully drops)
2. Open the FM app fresh
3. Let it discover and connect to your board — wait until battery % shows (that confirms unlock)
4. **Within 10 seconds of unlock**, stop the capture:
   - If you need a clean cut: back out of FM app, disable BT on phone
   - (the shorter the capture, the faster the decode)

## Step 3 — Pull the snoop log to your laptop

**Option A — Via ADB (preferred):**

```bash
# Connect phone via USB, enable USB debugging in Developer options
adb devices   # should show your phone

# Different Android vendors put the log in different paths. Try these in order:
adb pull /data/misc/bluetooth/logs/btsnoop_hci.log ~/Downloads/btsnoop.log
adb pull /sdcard/Android/data/com.android.bluetooth/files/btsnoop_hci.log ~/Downloads/btsnoop.log
adb pull /sdcard/btsnoop_hci.log ~/Downloads/btsnoop.log

# If none worked, use the bug report method (always works):
adb bugreport ~/Downloads/bugreport.zip
# Inside the zip: FS/data/misc/bluetooth/logs/btsnoop_hci.log
```

**Option B — Via Android bug report (no-ADB-needed fallback):**

1. Dial **`*#*#284#*#*`** on your phone keypad (some OEMs; see alt below)
2. A bug report starts generating, then prompts to share/save
3. Save the zip, extract, find `btsnoop_hci.log` inside

**Option C — Manufacturer-specific:**
- Samsung: Settings → Developer options → "Take bug report" → share to Drive/email
- Pixel: Settings → System → Developer options → "Take bug report"

## Step 4 — Decode the token

From a checkout of this repo, on the machine that has the log:

```bash
python3 scripts/decode_polaris_token.py ~/Downloads/btsnoop.log
```

Expected output (the token below is a placeholder; yours will be 40 hex characters):

```
[*] parsing btsnoop: ~/Downloads/btsnoop.log
[✓] TOKEN (hex, 40 chars): <40 hex characters>
    Paste it into the extension's settings (or keep it in a password manager).
    Nothing was written to disk. Treat it like a password: it never rotates.
```

The script never writes the token to disk; copy it from the terminal. Add `--verbose` to see which BLE writes it considered.

## Step 5 — Verify

Enter the board name and token in the extension's settings (or import them with `config.json`, see the README). Battery %, motor temp, amps and headroom should show real values instead of `0000`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `btsnoop_hci.log` doesn't exist | BT not restarted after enable | Toggle BT off/on, repeat capture |
| Empty log file | Snoop enabled but filter blocks capture | Some OEMs require `adb shell svc bluetooth disable` / `enable` cycle |
| Token decode finds no writes to `f3ff` | FM app never actually reached unlock (board didn't respond) | Close FM app fully, reboot board, try again. Make sure board shows green ready-state before opening app |
| Multiple candidate tokens | FM app did multiple handshake attempts | First one after CCCD subscribe on `f3fe` is the real unlock write |

## Security note

This token is not a universal key: it's specific to your board's pairing identity. It's what the FM app has been using all along; you're just mirroring what it already knows. This is interoperability work on hardware you own.

**Treat the token like a password.** It does not expire or rotate, so anyone who has it can read your board's telemetry over BLE when they're in range. Keep it out of git, screenshots, issues and chat logs. If it leaks, the only remedy is re-pairing the board.

## What happens after

Once the token is stored, the Karoo extension reads it from the `SettingsActivity` field (pasted once, or imported with `config.json`). `OnewheelConnector.kt` runs the static-token auth path, and the Karoo data fields show live board state. Nothing leaves the Karoo; there is no backend.

The token stays valid until FM firmware changes the auth scheme (stable since Polaris fw 6215).
