# Baritone AutoStore 2.8.0-beta.1 — Minecraft 26.3

A source fork of Baritone for Meteor 26.3-15. Baritone owns mining and selection clearing; AutoStore temporarily takes control to deposit items in registered chests or placed shulkers. Existing registrations remain compatible.

Download the runtime jar from [Releases](https://github.com/mikelongxc/baritone/releases/tag/autostore-2.8.0-beta.1). See [validation results](VALIDATION.md) and [upstream provenance](UPSTREAM.md).

## Trail flight and carried-shulker refueling

This beta adds `#trail` for Minecraft 26.3 with Trouser Streak 1.7.0. See [TRAIL.md](TRAIL.md) for supplies, commands, behavior and limits. Existing AutoStore commands and registrations remain compatible.

Background managed chest/shulker transactions now keep the server menu open without initializing or presenting its screen. Menu transitions also skip native global cursor queries while the bot is unfocused. Focused/manual containers retain normal GUI behavior. Storage opens explicitly send the facing rotation before the interaction packet. `#store status` now includes background/focus/owned-menu/snapshot diagnostics without coordinates. These changes address the storage GUI dependency; the exact reported live stall has not been reproduced.

## Install and use

1. Fully quit every Minecraft instance. A running game keeps its loaded jar even if you replace or trash that file.
2. Keep Meteor 26.3-15, Fabric API and your other compatible mods. Install only `baritone-autostore-26.3-autostore-2.8.0-beta.1.jar`; remove older Baritone/AutoStore runtime jars. Do not install the sources jar or source ZIP.
3. Launch Minecraft 26.3 with Java 25 and Fabric Loader 0.19.5 or newer.
4. Look at each chest or already placed shulker from a reachable standing position and run `#store`. Previously saved chests do not need re-registration.
5. Run `#mine minecraft:sand`, or mark a selection with `#sel pos1` and `#sel pos2`, then run `#sel ca`.

The inventory reserve is one slot by default. Storage trips choose the nearest eligible registered container, fall back when it is full/unreachable, and retry exhausted destinations after a cooldown. Native pathfinding can break/place access blocks during the trip. Bring ordinary building blocks and appropriate tools. No blocks are created automatically. Keep storage outside the area you ask Baritone to clear.

Commands:

- `#store count` — number of saved containers.
- `#store list` — saved names and last result, without coordinates.
- `#store status` — storage state, tool durability/reserve and recovery timers.
- `#store clear` / `#store clear name` — stop the managed job and remove all/one registration.
- `#pause`, `#resume`, `#stop` — explicit controls; stopping cancels pending recovery.

## Starting with a screen/container open

A screen is no longer a fatal startup validation error. The managed job remains armed while a blocking screen is open and continues automatically when it clears. If a new #mine or #sel ca begins with an ordinary chest/placed shulker already open, AutoStore captures that exact menu and closes it automatically once the cursor is empty. It does not transfer/drop the cursor stack, close a replacement menu you subsequently open, or close while manually paused or a transaction is pending. Inventory, Esc and Xaero retain their passive-screen behavior. Other container types wait instead of being closed automatically.

For an old session already stuck at `PAUSED: Close the other container or blocking screen before starting`, close the blocking screen and rerun the same #mine command. The startup fix takes effect after a full client restart onto 2.8.0-beta.1.

## Deposits and background operation

The chest stays open for the entire load. AutoStore sends one quick-move, obtains authoritative server contents, then sends the next. This is intentionally paced to server replies rather than a burst of clicks in one tick. It does not close/reopen for each stack, and no confirmation reopen is needed. Normal transfers are checked for item/component conservation. Unexpected differences are reconciled against fresh server contents. A missing reply or closed chest retains the pending transfer, returns to the same storage without breaking/placing blocks, and retries confirmation automatically. Unavailable storage is retried every 30 seconds. Only an authoritative result allows another transfer; changed contents are refreshed automatically. A transfer is never blindly replayed.

Implementation detail: vanilla can omit echoes for correctly predicted clicks. A stale-state PICKUP of slot -1 requests a full container snapshot. Slot -1 is a no-op in the pinned vanilla implementation; it is not the outside/drop slot -999. If a server rejects this resync request, automatic reconciliation uses a normal reopen of the same chest instead. The loader probe exercises the actual vanilla no-op branch; live server compatibility is not established.

Inventory, Esc and the main Xaero map may remain open. Storage temporarily replaces and then restores those passive screens. A cursor-held stack, unrelated chest/container, or a chat draft at the storage boundary still waits. While a managed job or deliberate recovery is active and the window is unfocused, AutoStore bypasses world/UI drawing and GPU surface presentation. The ordinary game-thread loop still polls window events, processes packets and runs game ticks; target picking and UI housekeeping continue, paced at 20 iterations per second. This removes the background graphics/presentation wait from the deposit loop. No focus spoofing or mouse capture is used. The background window's picture remains still until it regains focus; then normal rendering resumes. Explicit #pause, #stop and idle clients retain normal rendering. Saved video settings are unchanged. Resource tasks still drain, and third-party tick suspension or OS sleep remains outside this guarantee.

On macOS, an active managed job prevents idle system sleep using a process-scoped `caffeinate` assertion. Explicit Sleep, lid closure, shutdown and loss of network still suspend operation. AutoEat, AutoTool and KillAura may remain enabled; eating/combat yield movement. An in-flight deposit is confirmed before yielding its menu.

Storage changes are recoverable. While opening, a stale or changed snapshot requests a fresh server view; if that container stays unavailable, the bot tries other registered storage. An unexpected inventory/container difference after a transfer requires a full authoritative snapshot (or a normal reopen), then discards the old transfer baseline and selects from the items actually present. It never blindly replays the old click. No “inventory changed—inspect manually” stop is used for these ordinary storage changes. If all registered containers are full/unavailable, unlimited multiplayer #mine requests deliberate relog recovery and retries storage; other jobs periodically check storage in-world. A held cursor or unrelated user-opened container waits without being manipulated.

## Full storage and alternate routes

A load can span multiple registered containers: fill the current chest, confirm that transfer, skip full/filtered containers, and continue with the nearest untried registered chest. Mining resumes once the output load is deposited. If every registered destination is full or unavailable during a supervised unlimited multiplayer `#mine`, automatically relog, restore the saved mining target and immediately retry storage before allowing mining to continue. The first eligible request starts the existing two-second reconnect countdown; further reconnects share the 60-second recovery cooldown. Fresh server inventory is used even if it differs from before disconnect, and remaining outputs still go to storage if the inventory is no longer nearly full. `#store status` shows the storage-recovery countdown. Selection clearing and single-player keep the 30-second in-world retry.

Storage arrival now requires actual opening reach, not merely returning to the registration spot. If that spot is blocked or its route fails, Baritone tries a finite set of alternate positions around either chest half before skipping that destination. Already failed positions are excluded. Normal travel may break/place permitted access blocks; storage and its support remain protected. A double chest can be opened through either half. A registered chest gaining/losing its second half refreshes its layout before a new transaction; an in-flight transfer retains its original layout until reconciled. Other removed/replaced containers are still skipped. No registration or profile reset is required for this update.

## Selection clearing

Only `#sel ca` (and its clear-area aliases) opts the builder into storage. Ordinary schematic builds/fills do not. The builder's original schematic, layers and completion state remain active during storage trips; AutoStore does not issue another clear command. Completion, replacement by another job, or `#stop` ends supervision.

Unlike `#mine`, clearing can produce mixed drops. It deposits newly acquired ordinary items while retaining at least the starting quantity of each ordinary supply. Whole stacks are retained conservatively when splitting would cross that baseline. Equipment, food, enchanted/customized items, portable shulkers, ender chests, totems, rockets, and explicitly protected items/slots are excluded. This means an inventory already full of starting supplies may require freeing space before clearing. Explicit container item filters still apply.

## Tool protection

Both `#mine` and managed `#sel ca` retire tools at 5% remaining durability, rounded up: netherite 102/2031, diamond 79/1561. A netherite shovel at 98/2031 is ineligible. Suitable spares are selected from all 36 inventory slots. A full inventory is supported: the spare and worn tool exchange slots, keeping the worn tool. If no spare exists, an error stops the job; supply a tool and explicitly restart. Attempting to mine with a tool already below 5% also shows an error and stops, even if a spare exists. Replacement happens at the rounded reserve boundary before crossing below 5%.

Normal break callbacks, tool selection, outgoing mining packets on the client thread, and a tick-level wear check enforce the reserve. Each locally completed break reserves worst-case wear per hotbar slot; near the boundary, mining waits for a full server inventory snapshot rather than reusing a stale durability number. Unbreaking savings are not assumed before confirmation. A below-floor break is blocked immediately; native job cancellation is queued outside its current callback to avoid reentrant builder corruption. Inventory-to-hotbar swaps wait for a server snapshot; lack of confirmation stops mining. Hotbar selection is explicitly synchronized. The guard does not protect armor or independent automation outside the managed job. Arbitrary mods sending custom mining packets and custom server damage rules are not proven safe by these checks.

## Storage-failure relog

“No usable storage” now reaches the reconnect coordinator instead of being excluded because mining is paused for storage. It triggers only after the storage pass is exhausted and depositable outputs remain. It does not relog in the middle of a transfer/tool swap, during explicit pause, with a held cursor or an unrelated open container, or after the native job ends. These conditions wait until safe or remain stopped as requested. An inventory filled only with protected supplies uses the separate inventory-space message and does not trigger reconnects. Fully full chests still cannot accept items after reconnect, so subsequent failed passes may request another rate-limited relog. The existing Cancel/Esc, rejected-login and world/player checks apply.

This uses the existing unlimited multiplayer #mine recovery mechanism. It does not add selection-clear reconnect/restart or single-player reload.

## Stuck mining recovery

For an active, unlimited `#mine` supervised by storage:

- Repeated attempted breaks with no target-item gains or confirmed mining/access-block changes trigger deliberate relog after **10 seconds**. Start/continue attempts count even if the client never finishes the break, including leaves and access blocks obstructing the requested target; they are sampled at most twice per second, requiring eight samples. Five position corrections with no progress also qualify. Local walking, flying and ordinary server chat do not reset this check.
- Ordinary stationary/no-item progress refreshes the native route/search after **15 seconds**, then requests relog after another **15 seconds**. An unsafe-to-cancel path or a false client on-ground flag no longer prevents the watchdog from counting time. Route cancellation itself still respects native path safety.
- Brief eating/combat/tool waits freeze the timers instead of erasing them. Explicit pause/stop or a new job resets them. No recovery runs during a deposit or tool swap.
- A **two-second** reconnect screen provides Cancel/Esc. After rejoin and three seconds of chunk settling, the job resumes using the server's actual position and inventory. Position/inventory rollback does not prompt for inspection. Same player/server/dimension, a living player and an empty cursor are still required. The tool guard checks the new inventory before allowing mining.
- Continued stalls can request another relog automatically, with a minimum **60-second** interval between attempts. Cooldown no longer faults/stops the job. Rejected login, login timeout, explicit cancellation, a different world/player, death, and unrelated disconnects do not blindly restart a job.

`#store status` includes the idle/desync timers, retry cooldown and exact eligibility blocker without coordinates. Tool waits, storage state, cursor/screen holds, explicit pause and native mining ending are distinguished. Detection is heuristic; it does not identify an anti-cheat plugin. Server queues/authentication plugins and third-party tick suspension are not automated. Selection clearing retains storage/tool support but not automatic relog/restart.

### Sustained damage recovery

During an active unlimited `#mine`, server-reported damage lasting at least ten seconds with little mining progress triggers urgent relog and automatic restart of the saved target. The attacker does not have to exist at the client's apparent location. At least three hit notifications are required, with gaps no longer than four seconds. One hit or occasional separated hits do not trigger it.

Little progress means at most two confirmed mining/access-block breaks or target-item gains in the latest ten seconds, or less than 25% of the ten-second mining rate immediately before the damage began (when that baseline is at least eight). Block confirmations and collected items are counted separately and their maximum is used, so delayed drops do not double-count a break. Local movement, fireworks, chat and speculative client block destruction do not count as mining progress.

KillAura combat and AutoEat do not pause this damage timer. It bypasses the ordinary 60-second relog cooldown; each subsequent relog requires a new ten-second damage episode. Explicit pause/stop, pending deposits, tool swaps/confirmation, a held cursor and unrelated containers still exclude it. The two-second reconnect countdown, automatic server-state resume and 5% tool protection remain in force. `#store status` shows the damage timer.

## Logs, stone/end stone and nearby mining

Birch and other vanilla logs/stems, log-tagged blocks, sand, red sand, stone, end stone, deepslate and netherrack first scan a bounded cube six blocks around the player. Eligible candidates are ranked before applying the 64-target default limit, with exposed faces preferred. The existing loaded-chunk search remains the fallback. This avoids filling the limit from a distant corner of one dense chunk while ignoring closer blocks across its boundary.

Those bulk materials explore near the current elevation instead of defaulting to the ore exploration depth. Failed-target exclusions expire after 30 seconds; route recovery also clears them. Late scans from a replaced/reset job cannot overwrite its new search state. Ore and explicit legit-mining elevation policies remain native, as do breakability, height and exposure restrictions. Unloaded/obfuscated blocks, genuinely unreachable terrain and restrictive Baritone settings can still limit discovery.

## Elytra lava fix

The native flight map now marks all 512 bytes of a solid 16×16×16 section as blocked, at the correct section offset. Previously it marked just two bytes, leaving lava/solid sections mostly classified as empty. This addresses [upstream issue #5113](https://github.com/cabaletta/baritone/issues/5113). Restart Minecraft to load the new code and rebuild in-memory flight data. This is a terrain-map correction, not a guarantee against every lava collision, server correction or incorrect predicted terrain.

## Validation and limits

171 automated tests pass. Twelve additional real chunk-packing cases cover lava, solid and air sections. Isolated loader probes exercise transformations, dependency checks, native pathfinder initialization, real ItemStack durability methods including 98/2031, and the vanilla no-slot container-refresh branch. See VALIDATION.md and TEST-RESULTS.txt for the tested mod combinations.

No Minecraft client/world was started and no live server was accessed. Selection clearing, paced storage, focus/minimize behavior, tool swaps and deliberate relog have not been gameplay-tested. These are code and isolated-runtime checks, not a claim of unattended gameplay reliability. Servers with queues/authentication plugins or custom inventory protocols may require manual intervention. No private coordinates are included in this distribution.

Quantity-limited `#mine 64 minecraft:sand` keeps tool protection but does not opt into auto-storage/recovery. Only placed chests/shulkers are supported; no portable-shulker deployment, crafting, sorting or external tool restocking.

## Build

Use Java 25 and Python 3:

```sh
python3 tools/bootstrap.py
./gradlew --no-daemon --offline build
python3 tools/loader_smoke.py --java-home "$JAVA_HOME" --work-dir /tmp/autostore-loader-check
```

Use a new empty loader directory. `--loader-jar` selects the loader; `--extra-mod` adds copied test mods; `--xaero-check` adds map classification checks. Dependencies are hash-pinned and not redistributed. See DEPENDENCIES.md and UPSTREAM.md for sources and licenses.

## Repository builds

Use Java 25, run `python3 tools/bootstrap.py`, then `./gradlew --no-daemon --offline build`. Downloaded dependencies are checksum-verified and ignored by Git. Runtime and source jars are written to `build/libs/`. The upstream CI workflows are retained for provenance; release validation was run locally as documented in VALIDATION.md.
