# AutoStore 2.8.0-beta.2 validation — 2026-11-07

## Verified

- Java 25 / Gradle 9.5.0 build against Minecraft 26.3, Meteor 26.3-15, Fabric Loader 0.19.5. All 128 pinned dependency artifacts verified.
- 175 JUnit tests pass. Storage coverage includes full/partial/alternate chests, transaction confirmation, changed contents, retained pending transfers, route fallback, exhausted-storage relog eligibility and post-rejoin storage priority. Tool tests cover the rounded 5% reserve, 98/2031 netherite, unconfirmed wear and inventory spares. Recovery, passive screens, native job ownership and packaging are also covered.
- Isolated Fabric/Mixin probe passes with Meteor alone and with Sodium 0.9.2, Fabric API 0.165.2, Trouser Streak 1.7.0, Xaero World Map 1.47.2, Minimap 26.6.2 and XaeroPlus 2.37.2.
- A second expanded probe passes with every installed top-level mod except ViaFabricPlus (16 total, including AutoStore in place of stock Baritone). This also includes Distant Horizons, Continuity, Freecam, Mod Menu and the remaining utility mods.
- Probes transform relevant Minecraft/Meteor/Baritone classes, including ComeCommand (the target missing from the incompatible standalone build). Exercise the actual transformed unfocused rendering bypass and focused/paused/disarmed fallback, actual ItemStack durability methods, vanilla no-slot PICKUP, native exploration policy, exact dependency hashes and macOS ARM64 native pathfinder capability.
- Twelve synthetic chunk cases invoke the actual NetherPathfinderContext.writeChunkData. Lava, netherrack and air at section indices 0, 1, 7 and 23 verify all occupancy bytes, untouched adjacent sections and buffer guards. A separate test jar restoring the original two-byte calculation fails on lava, confirming that this regression detects the bug.

## Beta 2 regression checks

- Native MineProcess fixtures verify that storage resume clears cooldowns/candidates/exploration hints, advances the search generation, and preserves the same filter and quantity. The rescan is deferred until native mining regains control; inactive jobs remain untouched. A bytecode contract checks that the controller calls this only for MINE in RESUMING, excluding selection clearing.
- Actual transformed background frames drain queued ClientLevel world/lighting updates in every active storage state with no GPU surface. The previous beta 1 jar fails this new regression with an unprocessed queue, providing a negative control.
- Per-process diagnostic histories are bounded to 160 entries. Tests cover separate process files, transitions, tick gaps and failure to write diagnostics without disrupting operation.
- The 16-mod expanded loader probe passes, excluding ViaFabricPlus as described below. Local thread samples did not capture the reported RETURNING stall. They do not establish a conflict with a second account. Live focus behavior remains unverified.

## Retained trail/background checks

- 31 new tests cover connected trail direction, turns, new-chunk barriers, self-trail exclusion, bounded search/memory, persistence and scoping, fuel/Elytra/tool thresholds, and server-confirmed transfer outcomes including partial, rejected, changed, stale and duplicate replies.
- The isolated probe invokes the actual transformed Gui.setScreen on owned background chest fixtures: opening and closing return without GUI/native initialization while the server menu remains installed. Focused/unowned cases reach vanilla GUI code. The transformed MouseHandler.resyncMousePosition skips native SDL queries only for the managed unfocused job. Render-frame bypass is exercised through every active AutoStore storage state.
- Real 26.3 ChunkPos values exercise the Trouser adapter. Expanded tests verify the four private chunk-set fields against the installed Trouser Streak 1.7.0 class.
- Real ItemStack / ItemContainerContents / ItemStackTemplate code exercises rocket-only shulkers, rocket totals, mixed-item rejection and empty-box recovery. Synthetic component defaults are bound only in the isolated harness, which does not load normal client datapacks.
- All installed top-level mods except ViaFabricPlus pass the expanded isolated probe. ViaFabricPlus remains excluded because the previously documented registry/bootstrap requirement still needs normal client initialization.
- Read-only local JVM stack samples observed the existing background frame bypass running. No storage stall was captured during the bounded sample. The new headless-container and cursor changes remove additional GUI/native dependencies, but the user's exact live symptom is not claimed reproduced or conclusively resolved by these probes.

## Limits

No client/world was started and no live server was accessed. Loader preLaunch and Mixin preparation run; normal game/client entrypoints do not. Minimal in-memory fixtures contain no account, server or real world data. Trail flight, supply placement/break/pickup, server transaction latency and automatic relaunch remain untested in gameplay; see TRAIL.md. This validates code and isolated runtime behavior, not unattended gameplay reliability.

A full installed-modpack probe reached class transformations but could not bootstrap vanilla block registries because ViaFabricPlus requires normal client initialization. This is a harness limitation, not evidence of a gameplay crash. The explicit expanded subset above passes. Live server transactions, reconnects, window focus/minimize and actual Elytra flight remain untested.

The lava fix corrects loaded-section occupancy; it does not guarantee avoidance of all lava hazards or correct prediction from inaccurate terrain data. Desync recovery remains heuristic. Custom server inventory/durability behavior, third-party tick suspension, and OS-forced sleep may affect operation.

Full/unavailable registered storage retries and, for supervised unlimited multiplayer #mine, requests rate-limited relog. Selection clearing retries in-world and does not relog. A held cursor or unrelated container waits; an unconfirmed transfer is never blindly replayed. Manual stop, below-5% tools, no suitable replacement, death/world changes and unrecoverable dependency/persistence failures remain stop conditions. Saved registrations are compatible and are not reset.

## Reproduce

```sh
python3 tools/bootstrap.py
./gradlew --no-daemon --offline clean build
python3 tools/loader_smoke.py --java-home "$JAVA_HOME" --work-dir /tmp/new-autostore-loader-test
```

Use a fresh empty probe directory. Add compatible copied mods with --extra-mod and --xaero-check. Build reports are under build/reports/tests/test. TEST-RESULTS.txt summarizes release results.
