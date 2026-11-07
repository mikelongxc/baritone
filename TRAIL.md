# Trail flight beta — Minecraft 26.3

The command is **#trail**. It uses Trouser Streak NewerNewChunks for evidence and this fork's native Baritone Elytra process for movement. It does not buy, require or copy BepHax.

## Setup

Carry/equip:

- A healthy Elytra; optional healthy spare Elytra anywhere in the main inventory.
- Ordinary non-explosive fireworks. Launch requires at least 64 loose rockets, or enough accessible supplies to withdraw that amount first.
- One or more **fireworks-only shulker boxes in your inventory**. Mixed-content boxes are deliberately excluded. Empty boxes are retained.
- A pickaxe above the rounded 5% durability reserve, in inventory or hotbar.
- At least one empty inventory slot before refueling. More free slots allow larger refills.

Stand on reachable solid terrain or start airborne with adequate fuel, look in the direction of a trail and run **#trail**. NewerNewChunks is enabled automatically; its existing detector settings are retained. Its chunk classifications need to work on the server. No existing/new-chunk heuristic can reveal terrain the server has not sent.

## Commands

- `#trail` / `#trail start`: start or resume the locally saved route and any unfinished supply-box recovery.
- `#trail new`: stop the current run and start a fresh route from the current heading. A placed supply box must be recovered before resetting the route.
- `#trail status`: state, reason, rocket count and evidence count; no coordinates.
- `#trail pause` / `#pause`: land to pause; finish a pending supply stop and recover its shulker before pausing.
- `#trail resume` / `#resume`: continue a paused run.
- `#trail stop` / `#stop`: cancel immediately. This releases flight control even if airborne. Use pause for a controlled landing.

Trail flight and mining/selection clearing are separate jobs. Stop mining before starting a trail. A newly active mining/building job causes trail supervision to relinquish control.

## What it does

It follows connected chunks classified as old/previously visited, using new chunks as boundaries. Old-generation and upgraded-old classifications are also accepted. Fresh terrain observed during this run never becomes a self-created old trail. Heading, visited chunks, bounded evidence and supply-box intent are saved locally under an account/server/dimension hash. Public status hides locations; the checkpoint itself necessarily stores local navigation positions with private file permissions.

When evidence ends, it makes at most eight probes around the last lead, then lands and periodically checks for a new lead. It does not fly indefinitely across fresh terrain. #trail new allows a deliberate new search direction.

At 32 loose rockets or less, or at 10% Elytra durability (rounded up, minimum 32 remaining), it asks native Baritone to find a landing. On the ground it swaps to a healthy spare if available, places a supply shulker on a reachable solid support, opens it, and withdraws rockets with paced quick-moves and complete server snapshots. The target is 192 loose rockets, subject to inventory space and supply. It reserves a pickup slot, mines only its placed supply box with a healthy pickaxe, waits for its recovery into inventory, then resumes the saved trail. At least 64 rockets are required to launch, avoiding immediate land/relaunch loops.

One transfer or equipment swap is outstanding at a time. Rejected or changed transfers are reconciled from server contents. A lost menu is reopened; an unconfirmed click is not blindly replayed. Ordinary mixed/valuable-item shulkers are never selected for fuel. The job waits for fuel, a healthy tool/Elytra, available space or recoverable placement instead of dropping gear. Unexpected non-rocket contents in the placed box stop that run and leave the box intact.

AutoEat, AutoTool and KillAura can remain enabled. AutoTool yields during the supply stop. New eating/combat equipment actions yield briefly during an outstanding inventory transaction; explicit manual stop remains available. Background ticking and menu handling use the same machinery as AutoStore.

## Current limits

This is an experimental first implementation. Unit tests and isolated Minecraft/Fabric/Mixin fixtures pass; real flight, landing, server placement, inventory timing, pickup and resuming after refuel have not been gameplay-tested.

This version refuels from **carried shulkers**, not ender-chest stock. It does not automatically build platforms over the End void or lava, mend Elytra with XP, collect supplies from a remote stash, or reconnect a trail job after disconnect. On a later connection, #trail start restores the same scoped checkpoint. Missing/dead players or a different connection/world stop the active run. Stop does not discard an unfinished supply-box checkpoint.

A safe landing surface is essential. Native Baritone cannot guarantee a landing over a large void/lava region; the reserve is not a guarantee against every flight hazard. Detector ambiguity, server-obfuscated terrain and client/server movement desync can still affect routing. A stalled flight is reset once, then asked to land and pause. FreeLook/Inventory/Esc/Xaero background support does not override unrelated container ownership or a held cursor stack.

Trouser Streak's four internal set names are checked in the installed 1.7.0 binary. Future addon changes may require an adapter update. No XaeroPlus fallback adapter is included in this version.
