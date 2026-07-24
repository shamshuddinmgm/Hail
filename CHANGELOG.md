# Changelog

All notable changes to **Hail Async** (`com.shams.srk.hail`) are documented here.

Version format: **`34.52.<revision>-async`**

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

[34.52.09-async]: https://github.com/shamshuddinmgm/Hail/releases
