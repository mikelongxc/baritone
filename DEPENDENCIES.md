# Pinned compatibility baseline

| Component | Version / source |
| --- | --- |
| Minecraft | 26.2, official unobfuscated client classes |
| Java | 25 |
| Fabric Loader | 0.19.3 compile baseline; loader checks also passed with installed 0.19.5; includes MixinExtras 0.5.4 |
| Meteor | 26.2-35, exact archived binary below |
| Baritone | Built from included MeteorDevelopment/baritone sources, commit `a63a7aeaf29780ca9e98c5a4b4a8453e4b7c0a6c` |
| Gradle | Wrapper 9.5.0 |
| Nether pathfinder | 1.6, repackaged with Fabric discovery metadata as nested mod 1.6.0 |

Meteor SHA-256: `c84e494dc066133bb1a45bba34d3148f26474f3272bb5865475d63222fc352ae`

Minecraft client SHA-1: `2dc72797acbc1b63fc16a11c4ac393605f453754`

Use the [official Meteor archive](https://meteorclient.com/archive) for the matching Meteor version. Keep Meteor installed; replace its separate Baritone jar with this fork. Snapshot version strings alone are not unique, so the fork checks Meteor's full binary hash before initializing its storage controller. The old Baritone runtime hash gate has been removed because this release builds Baritone itself.

All download URLs and checksums are in `tools/dependencies.json`. The Java libraries added for the source build include jsr305 3.0.2, JetBrains annotations 26.0.2, MixinExtras Fabric 0.5.4 and nether-pathfinder 1.6. Runtime MixinExtras comes from Fabric Loader; the loader smoke test excludes the extra compile copy. Orbit comes from Meteor's nested jar. The runtime artifact does not bundle Minecraft or Meteor.

The original Nether Pathfinder jar has no `fabric.mod.json`. The `packagePathfinder` build task retains its classes/native resources and adds metadata from `src/dependencyMetadata/nether-pathfinder/`. The outer mod declares the resulting nested mod as a required dependency. This corrects the 2.0.0 packaging error; merely listing the unmodified plain jar in the outer `jars` array was insufficient.
