# Validation — AutoStore 2.6.7 — November 5, 2026

## Publication checkout validation

The fresh publication checkout exposed a packaging test that still opened the old 2.6.0 jar. The test now receives the current jar path and version directly from Gradle, so it checks the artifact just built and works without earlier build outputs. A clean build and all 140 tests were rerun for this checkout. Runtime behavior remains 2.6.7; repository contact links point to this fork.

## Completed

- Java 25 / Gradle 9.5.0 offline build passed against the pinned Minecraft 26.2 and Meteor 26.2-35 dependencies.
- **140 JUnit checks passed**, zero failures/errors/skips: 34 storage-loop, 16 job/recovery, 10 sustained-damage, 12 tool, 18 bytecode-contract, 10 search/cooldown, 8 transfer, 7 cooperation, 7 persistence, 6 policy, 6 depot-selection, 3 alternate-approach, 2 idle-sleep and 1 packaging tests.
- Recovery regressions cover the 10-second desync threshold, 15+15-second ordinary idle escalation, repeated relog requests, isolated-attempt rejection, real-progress resets, activity-time exclusion without erasing the timer, and explicit stop/new-job reset.
- Bytecode checks verify that unfinished start/continue mining attempts reach the watcher; idle monitoring has no grounded/safe-path requirement; deliberate relog retains scope/alive/chunk checks without the old inventory/position equality check; changed opening contents request refresh instead of throwing; and transfer retries require a fresh full server reply.
- Storage tests exercise retry/reconciliation without replaying pending clicks, changed-content rebasing from fresh state, opening-time fallback, continued transfer in the same menu, all-full waiting, protected-item policies and native job retention.
- Search tests cover dense-chunk iteration order hiding closer targets, an eligible face across a chunk boundary, exposure preference before the candidate limit, exclusions not consuming quota, and 30-second blacklist expiry with stable snapshots.
- Existing tool tests cover the rounded 5% reserve, the reported 98/2031 netherite case, unconfirmed wear budgeting, full-inventory spares and slot mappings.
- Isolated Fabric Loader 0.19.5 probes passed with Meteor alone and with copied Xaero World Map 1.44.2, Minimap 26.4.2, XaeroPlus 2.35.0, Fabric API 0.161.0 and Trouser Streak 1.6.9.
- Probes transform the relevant Minecraft/Meteor/Baritone hooks and exercise actual tool durability methods, the vanilla no-slot container-refresh branch, bulk-material versus ore/legit exploration policy, native goal construction, dependency hashes and the macOS ARM64 Nether Pathfinder capability.

- New damage tests cover the ten-second threshold, zero/two-block progress, sharply reduced mining rate, normal progress, item-gain fallback, delayed drops, isolated hits, gaps, reset after relog and exclusion during pauses/transactions. A wiring contract confirms damage recording precedes local attacker lookup, combat is not an eligibility gate, and urgent recovery bypasses the ordinary cooldown.

- Background contracts verify that vanilla event polling, queued packet processing and game ticks occur outside/before the bypassed render frame; the background branch retains time/UI updates, picking, pending-task drain and pacing and does not steal focus or duplicate ticks.
- Both loader probes invoke the **actual transformed Minecraft.renderFrame** on minimal in-memory fixtures with no GPU surface. An unfocused managed deposit returns successfully, while focused, manually paused and disarmed states reach the normal surface access. This tests the real cancellation branch and normal fallback without opening a window, starting a client/world or connecting to a server. The fixture has no player/world, so in-world target picking remains covered by the method contract rather than gameplay.
- Read-only diagnostics on running local JVMs observed game-thread stacks in graphics operations, including OpenGL buffer presentation. This supports removing background drawing from that thread; it does not prove that every reported deposit stall has the same cause. No game state or server connection was changed during diagnosis.

- The new recovery contract verifies that access-block attempts are not filtered out by the requested mining target and that diagnostics use the actual eligibility predicate. Both loader probes check birch and other vanilla logs/stems against local exploration policy, with ore and explicit legit-mining policy preserved.

- Startup regressions retain supervision through a long screen/activity wait, continue the same native job and storage after clearing it, and retain explicit stop. The bytecode contract rejects a fatal external-screen check in startup validation and verifies captured chest/shulker identity, cursor, manual-pause and transaction guards before automatic close.

- Multi-chest regression fills a partial chest, skips a full second chest, confirms the remaining transfer in the third with BatchTransfer, and resumes the same native job. Route tests cover alternate-approach success and exhaustion/fallback. Geometry tests cover both double-chest halves, elevated approaches, excluded failed positions and finite exhaustion. Bytecode checks tie arrival/open to actual reach and guard layout changes during pending transfers.

- Storage-failure recovery regressions verify the WAITING_STORAGE trigger, shared transaction/tool/cursor/pause guards, retained 60-second cooldown, target restoration before storage priority, a less-full post-relog inventory still being deposited, and refusal to restart stopped/pending-transfer states.

## Limits

No Minecraft client/world was started and no live server was accessed. Minimal zero-initialized class fixtures were used in the isolated probe. Loader entrypoints are deliberately not run. Real mining on a stone/end-stone island, server transaction delays, background operation and deliberate reconnects have not been gameplay-tested. These checks establish source/build/isolated-load behavior, not a guarantee against a particular server's anti-cheat or world geometry.

The complete installed modpack was not verified. The earlier 2.6.0 all-mods probe could not bootstrap ViaFabricPlus without normal client initialization; the 2.6.7 expanded probe uses only the explicit subset above.

Desync detection is heuristic. A long legitimate break without server-visible progress can resemble a stuck break. Same-player/server/dimension and alive checks remain on rejoin; server inventory and position rollback are now accepted. Cursor-held items or another open container wait instead of being manipulated. Login queues/authentication plugins, rejected logins and unrelated disconnects remain outside deliberate recovery. Ordinary relog requests have a 60-second cooldown. Sustained-damage recovery bypasses that cooldown and requires a fresh ten-second episode after each rejoin.

Changed storage is rebased only after a full server snapshot or confirmed reopen. Each next click re-evaluates actual current stacks and equipment protection; the old click is never replayed blindly. The resync request uses vanilla stale-state behavior. A server rejecting it may require normal reopen reconciliation. Truly unavailable pending storage can remain waiting; all full/unavailable registered containers trigger rate-limited deliberate reconnect for supervised unlimited multiplayer #mine; selection clearing and single-player retry in-world. Unregistered containers are not automatically used.

The 5% tool stop, manual stop, death/world changes and unrecoverable code/dependency/persistence failures remain stop conditions. Custom server durability rules and independent mods bypassing ordinary break paths are not proven safe. No claim is made that every possible error can be recovered automatically.

Selection clearing retains storage and tool support; automatic relog is for unlimited #mine. Initial clearing supplies are retained conservatively. Registered storage should be outside the cleared area.

The damage heuristic can also trigger on sustained environmental damage or legitimate combat with sufficiently reduced mining. It does not prove server/client desync or identify a mob or anti-cheat plugin. The transaction and explicit-pause exclusions still apply. Sustained damage recovery has not been tested against a live server.

## Pending gameplay checks

1. Exhaust the registered storage pass during multiplayer #mine; verify immediate eligible relog, saved target restoration and storage-first retry, including inventory rollback to below the nearly-full threshold. Verify a second failure respects the 60-second cooldown and Cancel/manual pause still win.
2. Fill one registered chest, partly fill another, and leave a third empty; verify overflow across the load. Block the saved standing spot and test alternate approaches, opening either double-chest half, and joining/splitting a registered chest before transfer.
3. Start #mine/#sel ca while a chest/shulker is open; verify automatic empty-cursor closure. Test held cursor, manual pause, replacing the menu, other container types, and continuation without reissuing the job.
4. Take repeated server damage for ten seconds while mining progress is absent or greatly reduced; verify KillAura/AutoEat do not suppress the timer, ordinary cooldown is bypassed, and relog resumes safely from actual server inventory. Verify ordinary successful mining, isolated hits and manual pauses do not cause this recovery.
5. Repeatedly fail to break one target while client movement continues; verify detection after about ten eligible seconds, two-second countdown and automatic resume after server position/inventory rollback.
6. Stand idle with no progress; verify route refresh at 15 seconds and relog after another 15. Exercise eating/combat pauses and cooldown retry.
7. Change chest contents while opening and between transfers; verify automatic full refresh/rebase, no blind click replay and protected equipment retention. Test missing replies, full chests and fallback.
8. Mine birch through obstructing leaves and stone/end stone around a chunk edge; verify local exposed targets are selected, failed exclusions expire and discovery stays near the current elevation.
9. Run two accounts, leave the mining account unfocused through an entire deposit, then focus it and verify inventory/storage and continuation. Its background image is intentionally not redrawn. Also check minimize, passive inventory/Xaero screens, #sel ca continuation, AutoEat/AutoTool/KillAura and the below-5% stop with server delays.



## Reproduce

```sh
python3 tools/bootstrap.py
./gradlew --no-daemon --offline build
python3 tools/loader_smoke.py --java-home "$JAVA_HOME" --work-dir /tmp/new-autostore-loader-test
```

Use a fresh empty loader directory. `--loader-jar` selects another compatible loader. Add copied test mods with `--extra-mod` and `--xaero-check`. TEST-RESULTS.txt records the release summary; local JUnit reports are generated in build/reports/tests/test.
