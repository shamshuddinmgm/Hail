# Hail Async

**Freeze apps your way — per-tag modes, premium themes, side-by-side with stock Hail.**

Fork of [chaoscreater/Hail](https://github.com/chaoscreater/Hail) · upstream [aistra0528/Hail](https://github.com/aistra0528/Hail)

[![Version](https://img.shields.io/badge/version-34.52.17--async-1B6CA8)](#)
[![Package](https://img.shields.io/badge/id-com.shams.srk.hail-39FF14)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-23-blue)](#)
[![License](https://img.shields.io/badge/license-GPL--3.0-lightgrey)](LICENSE)

---

## Why Hail Async?

| | |
|---|---|
| **Side-by-side** | Install next to stock / chaoscreater Hail (`com.shams.srk.hail`) |
| **Per-tag modes** | Default = Disable, another tag = Suspend — no cross-talk |
| **Thumb-first chrome** | Freeze FAB + Search on bottom nav; lean toolbar |
| **Premium themes** | AMOLED, neon sci-fi, dark gray, chocolate, midnight, ember |
| **Smoother** | Chunked freeze, icon race fixes, nav transitions, 120 Hz prefer |

---

## Features we added / improved

### Working modes & tags
- Per-tag working mode presets (Settings + long-press tag)
- Freeze FAB uses **current tag** mode (shows short label: Dis / Sus / Hide / …)
- Unfreeze only reverses the **same mode family** (Suspend ≠ Enable)
- `frozenMode` remembered per app for correct restore / export

### Home & Apps UX
- Bottom nav: compact **Home · Apps · Settings** cluster + wide **Search** (theme-accent borders)
- **Reorder pinned apps** (move / drag); pin order persisted
- About lives in **Settings** (not bottom nav)
- Toolbar: Home / Whitelist / Search / Multiselect · Shortcuts in ⋮
- Row tap on Apps selects/deselects (checkbox clear of fast-scroll)
- Search Enter launches top result (unfreeze + launch)
- Fast-scroll slider on Apps (non-overlapping)
- Panel transitions (fade / slide)

### Themes
- Follow system · Light · Dark
- AMOLED Black · Dark Gray · Chocolate Brown · Midnight Navy · Ember Forge
- Neon Hacker · Neon Cybertron · Neon Plasma · Neon Ice  
  Theme-aware fonts, shapes, nav accents, Compose Settings look

### Stability & system
- Post-unfreeze icons no longer stuck grey (`FLAG_STOPPED` false positive fixed)
- Launch crash harden (HyperOS theme / display)
- Boot restore for AutoFreeze **only if** user enabled freeze-after-lock
- Safer `AutoFreezeService` accessor · fast-scroll leak detach

### Identity
| | |
|---|---|
| App name | **Hail Async** |
| Package | `com.shams.srk.hail` |
| Version | `34.52.17-async` |
| Deep links | `hailasync://…` |

---

## Download

Release APK (when published): see **[Releases](../../releases)**.

Sideload debug/release builds from CI or local:

```text
Hail-v34.52.17-async.apk
```

> **Note:** New package id = fresh install. Export settings from your previous Hail+ build before switching.

---

## Working modes (Shizuku / root / owner / …)

Same freeze backends as upstream Hail:

- **Disable** · **Hide** · **Suspend** · **Stop**
- Privileges: Shizuku, Root (SU), Device Owner, Dhizuku, Island, Privileged

Per-tag presets let you mix (e.g. user apps Disable, system apps Suspend on HyperOS).

---

## Screenshots

Add device screenshots under `fastlane/metadata/` or Releases as you capture them.

---

## Build

```bash
./gradlew :app:assembleRelease
# APK → app/build/outputs/apk/release/Hail-v34.52.17-async.apk
```

JDK 17 · Android SDK 36

---

## Credits

- [aistra0528/Hail](https://github.com/aistra0528/Hail) — original
- [chaoscreater/Hail](https://github.com/chaoscreater/Hail) — QoL base
- Hail Async — packaging, tag modes, themes, UX & stability work on this fork

---

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
