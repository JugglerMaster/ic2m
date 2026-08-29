# AGENTS.md

## Releasing a new build / tag
The version Mindustry shows players is read from `mod.hjson` (`version:` field), **not** from git tags.
When cutting a new build, do all of these together:

1. Bump `mod.hjson` `version` to match the intended tag (e.g. tag `v0.2.12` -> `version: "0.2.12"`).
2. Rebuild the jar with `gradlew build` **after** the `mod.hjson` bump, so the distributed jar carries the new version.
3. Commit the `mod.hjson` change.
4. Create and push the matching git tag (`git tag vX.Y.Z && git push origin vX.Y.Z`).

If the `mod.hjson` version is left behind, players will see the old version even though the tag advanced.
