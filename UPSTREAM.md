# Provenance and changes

Upstream: https://github.com/MeteorDevelopment/baritone

Pinned commit: `a63a7aeaf29780ca9e98c5a4b4a8453e4b7c0a6c` (Minecraft 26.2).

Meteor reference: MeteorDevelopment/meteor-client commit `2385f7b4bb9de1a698b4503254bd32f30fd7a87d`; binary 26.2-35.

This is an unofficial development fork. It builds actual Baritone source into a single Fabric replacement jar with a Meteor addon entrypoint. AutoStore additions originated in the earlier 1.x addon. Existing registrations migrate without old quarry/corridor restrictions.

## Final behavior in 2.6.7 — November 5, 2026

- Preserve native MineProcess and BuilderProcess ownership during temporary storage navigation. Only explicit clear-area selections opt the builder into storage. New builder jobs terminate previous supervision before replacing the schematic.
- Preserve equipment and initial selection-clearing supplies, support mixed new drops, nearest registered storage, fallback/cooldowns, and coordinate-free status.
- Replace one-tick click bursts and confirmation reopens with paced, server-confirmed transfers in one menu. Use the pinned vanilla stale-state/no-slot resync path; protect each transaction and the aggregate load. Reconcile a closed/unavailable chest automatically at the same destination without replaying an unconfirmed transfer.
- Extend the 5% reserve to selection clearing, add tick-level worn-tool checks and explicit selected-slot synchronization, and require an authoritative inventory snapshot for a spare-tool swap. Track unconfirmed wear conservatively and hard-stop below 5% or when no suitable spare exists.
- Keep passive inventory/Esc/Xaero screens and suppress focus-loss pause. Raise effective vanilla background FPS to at least 20 during the job; retain the process-scoped macOS idle-sleep assertion.
- Extend eligibility-before-quota scanning from sand to all mining targets. Retain the sand elevation and empty-goal fixes.
- Add user-requested no-progress recovery: refresh native route/search, then one deliberate relog if still stuck. A separate server-progress heuristic detects repeated unsuccessful mining/corrections despite client movement and relogs directly. Recovery alone may restart the saved mine target after verified rejoin. Storage never issues mining/build commands. Explicit stops and unrelated disconnects never auto-resume. Deliberate recovery now accepts server inventory/position rollback.
- Retain the 2.1 nested Nether Pathfinder discovery fix and 2.2 Gradle resource-version tracking.
- 2.6.1: count unfinished mining attempts; use 10-second desync / 15+15-second idle windows and a two-second reconnect countdown; freeze short activity pauses; retry after cooldown rather than fault. Expose coordinate-free recovery status.
- 2.6.1: reconcile changed storage from a new full server snapshot instead of faulting; wait/refresh stale opening contents; preserve protected-item selection on every retry.
- 2.6.1: nearest-first bounded local bulk-material search, current-height stone/end-stone discovery, 30-second target cooldowns and generation-guarded scan results.
- 2.6.2: add sustained-damage/low-progress recovery independent of combat/eating pauses and local attacker visibility; use server damage notifications and real progress, bypass ordinary cooldown for sustained danger, retain transaction/manual-stop guards and tool protection.
- 2.6.3: bypass GPU rendering/presentation for unfocused managed clients; preserve normal event polling, scheduled packet processing, game-thread ticks, targeting and GUI housekeeping with 20 Hz pacing. No focus spoofing or input capture. Normal graphics resume when focused or the bot is paused/stopped.
- 2.6.4: include birch/other logs and stems in nearby/current-height exploration; count unsuccessful access-block breaks in desync recovery; use the same eligibility predicate for recovery and detailed coordinate-free blocker status.
- 2.6.5: treat open screens as recoverable startup activity waits instead of disarming supervision; automatically close only the originally captured startup chest/shulker with an empty cursor, retaining explicit pause and transaction guards.
- 2.6.6: retry failed storage routes with alternate approaches, open either double-chest half and refresh joined/split chest layouts outside pending transfers. Verify partial/full/multiple-container load completion.
- 2.6.7: exhausted storage during supervised unlimited multiplayer #mine triggers rate-limited relog, then prioritizes storage immediately after restoring the saved target.
- Expand tests and isolated loader probes. Actual gameplay and reconnects remain untested.

Original Baritone sources remain LGPL-3.0-or-later; see LICENSE (included in the jar as LICENSE-Baritone). AutoStore additions are GPL-3.0-only; see LICENSE-AutoStore. The combined distribution includes both notices and complete corresponding source. Original author notices are retained. The nested Nether Pathfinder jar retains its notices/resources. Minecraft, Meteor and Xaero are external dependencies and are not redistributed here.
