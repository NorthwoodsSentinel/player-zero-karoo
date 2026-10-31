# Kintsugi

Kintsugi is the Japanese art of mending broken pottery with gold, so the repair becomes part of the object's history instead of something hidden.

This file is the same idea for code. Every fix here leaves a seam in three places, all carrying the same gold line:

- a short `kintsugi:` comment at the repair site;
- a `Kintsugi:` trailer on the commit;
- an entry in this append-only ledger.

The seam shows the break; it doesn't hide it. Security fixes describe what kind of thing was mended, never how to exploit it.

A repair is entered as "review pending". The reviewer is recorded only after the review has happened, in a later commit that names the verdict and the commit it reviewed, so this ledger never claims a review before it exists.

| Date | What broke | How it was mended | Mended by / reviewed by | Gold line |
|---|---|---|---|---|
| 2026-10-31 | `decode_polaris_token.py` saved the board token to a file at a path only one machine had, before tightening its permissions; each guard added around that file (creation mode, folder checks, Windows and macOS access rules) exposed another way to read it. The token guide pointed at maintainer-only paths and tools. | The file write is removed: the script prints the token for the user to paste into the extension, and writes nothing. The guide was rewritten for any reader with a placeholder token. | Margin / Codex read-only audit: script verified to only read the capture and print (rounds 6-8); round 8's remaining finding concerned the test oracle only; merged at cb1b21d on Rob's direction 2026-10-31 | *The key was copied into a drawer that every lock-maker kept finding new ways to open; now it is handed straight to the one who needs it, and there is no drawer.* |
