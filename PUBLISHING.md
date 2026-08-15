# Publishing Releases

This document explains how to publish IC2M releases to GitHub for other agents/devs.

## How It Works

GitHub Actions automatically builds and publishes a release when you push a version tag.

### The Workflow

1. Make changes, commit, push to `main`
2. Update `version` in `mod.hjson`
3. Commit and push
4. Create and push a tag matching the version
5. GitHub Actions builds the jar and creates a release

### Step-by-Step

```bash
# 1. Make your changes
git add .
git commit -m "Description of changes"
git push

# 2. Update version in mod.hjson (must match the tag you'll create)
# Edit mod.hjson: version: "0.2.0"

# 3. Commit the version bump
git add mod.hjson
git commit -m "Bump version to 0.2.0"
git push

# 4. Create and push the tag
git tag v0.2.0
git push origin v0.2.0
```

That's it. GitHub Actions will:
- Build the jar with `./gradlew jar`
- Create a release at `github.com/JugglerMaster/ic2m/releases/tag/v0.2.0`
- Attach `ic2m.jar` to the release

### Important Rules

1. **Tag must match mod.hjson version** — If `mod.hjson` says `0.2.0`, the tag must be `v0.2.0`
2. **Version bump must be committed BEFORE tagging** — The workflow checks out code at the tag, so mod.hjson must already have the correct version
3. **Push the version commit before pushing the tag** — Otherwise the release jar will have the wrong version

### Common Mistakes

| Mistake | Result |
|---|---|
| Tag pushed before version commit | Release jar has old version number |
| Tag doesn't match mod.hjson | Mindustry shows wrong version |
| Forgot to push version commit | Update notification doesn't appear |

### The Correct Order

```
1. git commit (changes)
2. git push
3. git commit (version bump in mod.hjson)
4. git push
5. git tag v0.X.X
6. git push origin v0.X.X
```

### Files Involved

- `.github/workflows/release.yml` — GitHub Actions workflow (don't modify unless needed)
- `mod.hjson` — Contains `version` field that Mindustry checks
- `build.gradle` — Builds the jar (no changes needed for releases)

### How Mindustry Checks for Updates

Mindustry compares the `version` field in `mod.hjson` from the installed jar against the latest GitHub release. If they differ, it shows an "Update" button.

### Debugging

Check if a release was created:
```bash
curl -s https://api.github.com/repos/JugglerMaster/ic2m/releases | python3 -c "import sys,json; data=json.load(sys.stdin); [print(f'{r[\"tag_name\"]} - {r[\"published_at\"]}') for r in data]"
```

Check GitHub Actions status:
```bash
curl -s https://api.github.com/repos/JugglerMaster/ic2m/actions/runs | python3 -c "import sys,json; data=json.load(sys.stdin); [print(f'{r[\"name\"]} - {r[\"status\"]}') for r in data.get('workflow_runs',[])]"
```
