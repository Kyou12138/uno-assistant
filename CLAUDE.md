# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- Release keystore + release build (PowerShell):
  - `powershell -ExecutionPolicy Bypass -File scripts/create-release-keystore.ps1`
  - The script ends with: `./gradlew.bat :app:assembleRelease`

## High-level architecture

- **App entry + settings UI (Compose):** `app/src/main/java/com/unoassistant/overlay/MainActivity.kt`
  - Single activity that renders the control screen (overlay permission, start/stop, max opponents, opponent alpha) and writes settings via `OverlayStateRepository`.

- **Foreground service lifecycle:** `app/src/main/java/com/unoassistant/overlay/OverlayForegroundService.kt`
  - Runs as a foreground service to keep the overlay alive; start/stop actions call `OverlayPanelManager.show()/hide()`.

- **Overlay window manager (core behavior):** `app/src/main/java/com/unoassistant/overlay/OverlayPanelManager.kt`
  - Creates/updates the control bar overlay plus per‑opponent overlay windows using `WindowManager`.
  - Handles add/remove opponents, lock/unlock, reset colors, drag/snap positioning, collapsed state, and visual updates.

- **State + persistence:** `app/src/main/java/com/unoassistant/overlay/persist/OverlayStateRepository.kt`
  - In‑process state cache with DataStore backing (see same package for store/serialization helpers).
  - UI/service/overlay read/write via this repository.

- **Android components/permissions:** `app/src/main/AndroidManifest.xml`
  - Declares overlay permission and foreground service components.

## Product/spec references

- Requirements: `requirement.md`
- MVP validation checklist: `docs/overlay-validation.md`
- OpenSpec change: `openspec/changes/overlay-color-marker/specs/overlay-color-marker/spec.md`
