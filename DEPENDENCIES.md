# Pinned compatibility baseline

| Component | Version / source |
| --- | --- |
| Minecraft | 26.3, official unobfuscated client classes |
| Java | 25 |
| Fabric Loader | 0.19.5 compile/runtime baseline; includes MixinExtras 0.5.5 |
| Meteor | 26.3-15, exact archived binary below |
| Baritone | Built from included MeteorDevelopment/baritone sources, commit `1cdb177f196fb49d11b59367a526b237e34dda97` |
| Gradle | Wrapper 9.5.0 |
| Nether pathfinder | 1.6, repackaged with Fabric discovery metadata as nested mod 1.6.0 |

Meteor SHA-256: `0a80eb3dcfe3c0e64bc9629bdde444c4a0a187033cd5c0d608474fbac745e5fd`

Minecraft client SHA-1: `e877b6a07acd633fb3bb475002175cec036e7b87`

Use the [official Meteor archive](https://meteorclient.com/archive) for the matching Meteor version. Keep Meteor installed; replace its separate Baritone jar with this fork. Snapshot version strings alone are not unique, so the fork checks Meteor's full binary hash before initializing its storage controller. The old Baritone runtime hash gate has been removed because this release builds Baritone itself.

All download URLs and checksums are in `tools/dependencies.json`. The Java libraries added for the source build include jsr305 3.0.2, JetBrains annotations 26.0.2, MixinExtras Fabric 0.5.4 and nether-pathfinder 1.6. Runtime MixinExtras comes from Fabric Loader; the loader smoke test excludes the extra compile copy. Orbit comes from Meteor's nested jar. The runtime artifact does not bundle Minecraft or Meteor.

The original Nether Pathfinder jar has no `fabric.mod.json`. The `packagePathfinder` build task retains its classes/native resources and adds metadata from `src/dependencyMetadata/nether-pathfinder/`. The outer mod declares the resulting nested mod as a required dependency. This corrects the 2.0.0 packaging error; merely listing the unmodified plain jar in the outer `jars` array was insufficient.

Trail mode optionally uses installed Trouser Streak 1.7.0 for Minecraft 26.3 (mod id streak-addon). The adapter reads its four chunk sets reflectively; AutoStore itself still works without this addon. The expanded probe verifies the field contract against the installed jar, SHA-256 `c0558aed1e07e7ca27285b9fc0d62a2a0876ef413d577f2c496eb7af8b21ee6e`. No Trouser code or binary is redistributed.
