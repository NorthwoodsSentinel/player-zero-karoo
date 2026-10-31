# Player Zero for Karoo

The Onewheel's numbers on the bike computer. A Hammerhead Karoo 2/3 extension that connects
straight to a Onewheel GT / GT-S over Bluetooth and puts **battery, motor temp, safety headroom,
amps and speed** on a ride page, so you look down instead of pulling out a phone.

Nothing leaves the Karoo. No account, no backend, no upload.

Sibling of the phone app [Player Zero](https://github.com/NorthwoodsSentinel/player-zero-app), whose
verified BLE recipe this uses (Polaris token → 20-frame setup burst → token re-poke every 6 s).
The connector was born inside [veloVigil](https://github.com/velovigil/velovigil-karoo) and moved
out on 2026-08-26 so a cyclist's app never carries a board token.

## Install (sideload)

1. Karoo: Settings → System → About → tap Build number 7× → Developer options → USB debugging on.
2. `adb install player-zero-karoo-v<version>.apk` (or copy the APK to the Karoo's Download folder and
   open it from Settings → System → File Manager).
3. Open **Player Zero** on the Karoo. Allow Location (Karoo 2) / Nearby devices (Karoo 3) — the BLE scan
   cannot run without it.
4. Enter your board's BLE name (e.g. `owXXXXXX`) and its Polaris token
   ([how to get it](docs/POLARIS_TOKEN_EXTRACTION.md)). **Save & connect.**
5. Add the fields to a ride page: they live under **Player Zero** in the data-field picker.

Or push the config in one go, no typing on the Karoo. The token is a password, so it is piped straight to
the Karoo and never written to a file on your computer (the extension removes its copy once imported):

```sh
printf '{"board_name":"owXXXXXX","polaris_token":"<40 hex>"}' \
  | adb shell 'cat > /sdcard/Android/data/com.northwoods.playerzero.karoo/files/config.json'
adb shell am start -n com.northwoods.playerzero.karoo/.SettingsActivity
```

The token will be in your shell history after this; clear that line (or start the command with a space if
your shell is set to ignore those) if other people use this computer.

## Proof it's connected

**Board Battery** reads `--%` until the board answers with its first battery value, then a real
percentage. That value only arrives after the token is accepted, so a number on the screen *is* the
receipt that the board is authenticated and streaming. The Settings page shows the same status line
and refreshes every two seconds.

`adb logcat -s PlayerZero PlayerZero.OW` prints one line per first value received
(`First value: battery = 60%`), so a bench check never needs a database.

## Build

```sh
scripts/release.sh          # clean → assembleRelease → zipalign → apksigner → verify
```

Needs an Android SDK, a `GITHUB_TOKEN` with `read:packages` for Hammerhead's `karoo-ext`, and the
signing keystore (not in the repo).

## License

MIT. NorthWoods Sentinel.
