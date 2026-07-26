# Changelog

All notable changes to **Hail Async** (`com.shams.srk.hail`) are documented here.

Version format: **`34.52.<revision>-async`**

---

## [34.52.18-async] — 2026-07-27

### Fixed
- Apps search: pressing Back no longer wipes filtered results (aligned with Home)

---

## [34.52.17-async] — 2026-07-27

### Build
- Release APKs now signed with a **dedicated release keystore** (`signing.properties` local only)

---

## [34.52.16-async] — 2026-07-27

### Added
- 📜 **Fast-scroll slider on Home** — same edge control as Apps, on every tag page

### Fixed
- `hailasync://` deep links (scheme check matched the manifest)
- Auto-freeze screen-off receiver on Android 13+ (`RECEIVER_NOT_EXPORTED`)
- Empty tags after backup import no longer crash Home
- Settings crash on unknown/corrupt preference values
- Removing a tag now reassigns **all** apps (not only the visible list)
- Settings export no longer reports success when the file wasn’t written
- Tag working-mode picker no longer offers a dead “Default” backend
- Freeze/unfreeze via API/shortcuts refreshes app state immediately
- Tag reorder refreshes Home tabs; pager pages bind to tag id (off-screen list bugs)
- Atomic apps/tags file writes; meta-cache saves on a single worker thread

### Smoothness
- Stronger 120 Hz window; Settings no longer fights AppBar nested-scroll
- Apps pull-to-refresh clears install cache; search labels loaded once

---

## [34.52.15-async] — 2026-07-27

### Smoothness
- Window + surface refresh-rate hints for HyperOS 120 Hz panels
- Settings: disable AppBar lift-on-scroll while Compose preferences are open

---

## [34.52.14-async] — 2026-07-26

### Smoothness
- Tag switches: single settle-only refresh (no stacked list rebuilds mid-swipe)
- Removed post-paint `notifyDataSetChanged` (DiffUtil-only soft refresh)
- Icons: memory-only on UI thread; disk decode stays off-main
- Home↔Apps: cached installed-apps list; Apps RV animator off
- Nav transitions: short fade only (no translate stutter)
- Settings: icon-pack query remembered; nested-scroll interop; label cache

---

## [34.52.13-async] — 2026-07-26

### Performance
- 💾 **Persistent app meta cache** — labels, system/install flags, freeze state on disk
- 🖼 **Disk icon cache** — icons reload from `cache/icons` without PackageManager
- Home paints from disk cache on cold start, then refreshes live in the background

---

## [34.52.12-async] — 2026-07-26

### Performance & launch
- 🚀 **SplashScreen** brand landing (snowflake) kept until Home list is ready
- Soft landing animation: content rise-in · bottom nav slide · FAB pop
- Deeper background warm of PackageManager caches at process start
- Splash timeout safety (1.8s) so launch never hangs

---

## [34.52.11-async] — 2026-07-26

### Performance
- ⚡ **Faster cold start** — cache PackageManager lookups / labels / freeze state on `AppInfo`
- Home list filter+sort moved off the main thread
- Defer AutoFreeze service scan, icon preload, and 120 Hz prefer until after first frame
- Icon pack: skip work when unset; parse `appfilter` once
- Lower ViewPager offscreen pages (2 → 1)

---

## [34.52.10-async] — 2026-07-25

### Added
- 📌 **Reorder pinned apps** — move to top / up / down / end, or **Drag to reorder**
- Persistent `pinOrder` (saved + settings backup)
- Pushpin marker on pinned Home icons

### Improved
- 🧭 **Bottom nav layout** — Home · Apps · Settings clustered; **Search** gets a wide thumb zone
- Theme-accent borders on nav chips; icons ~+15% larger (28dp)
- Home long-press actions use stable action IDs (safer menu)

### Notes
- Side-by-side install: `com.shams.srk.hail`
- Shizuku Hide still needs **root Shizuku** on HyperOS (ADB Shizuku lacks `MANAGE_USERS`)

---

## [34.52.09-async] — 2026-07-25

### Added
- **About** entry at the bottom of **Settings** (removed from bottom navigation)
- Bottom nav **Search** occupies the former About slot (Home · Apps · Settings · Search)
- Package identity **`com.shams.srk.hail`** · deep link scheme **`hailasync://`**
- Version scheme **`34.52.xx-async`**

### Changed
- App display name → **Hail Async**
- README rewritten for this fork’s feature set

### Fixed / hardened (cumulative on this fork)
- Grey icons after Unfreeze Visible / All (`FLAG_STOPPED` misread)
- HyperOS launch crash around theme / display refresh
- Apps checkbox vs fast-scroll overlap; row tap toggles selection
- Mode-isolated freeze/unfreeze across tags
- Nav panel transitions; boot restore for opt-in AutoFreeze

### Inherited from earlier Async revisions
- Per-tag working modes · Freeze FAB mode label
- Premium theme pack (AMOLED + neon + earth tones)
- Side-by-side install vs stock Hail
- Chunked freeze IO · icon load race fixes

---

## Earlier fork tags (pre-async numbering)

| Tag | Notes |
|-----|--------|
| 1.15.0 | Apps UX + transitions + boot restore |
| 1.14.1 | Launch crash hotfixes |
| 1.14.0 | Grey-icon root cause + theme expansion |
| 1.13.x | Mode isolation · themes · Enter-to-launch |
| 1.12.x | Side-by-side package · toolbar chrome |
| 1.11.x | Tag modes · Search FAB era |

---

[34.52.10-async]: https://github.com/shamshuddinmgm/Hail/releases/tag/v34.52.10-async
[34.52.09-async]: https://github.com/shamshuddinmgm/Hail/releases
