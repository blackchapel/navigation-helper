# Working on RideLink

## Branch / PR / merge workflow

- Never push new work directly to `main`. Create a feature branch for it.
- Push the feature branch and let the user run the manual **Test
  Release** workflow (`.github/workflows/build-apk.yml`,
  `workflow_dispatch` with a `branch` input) against it themselves.
- Only after the user has tested that build and explicitly approved it,
  open a PR from the feature branch into `main`.
- **Never merge a PR without the user's explicit go-ahead**, even after
  it's open and even if CI on it is green. Merging to `main`
  auto-triggers `.github/workflows/release.yml` (the signed **Production
  Release** build) — that's exactly why the approval gate matters.

## Docs: read before writing code, update after

Before starting any change, read all three of these -- they're the
fastest way to pick up real context (architecture, what already exists,
what's already known to be broken or risky) without re-deriving it from
source or repeating work already flagged as a gap:

- `README.md` — user/build-facing: what the app does, how to build it,
  how to test it.
- `docs/SYSTEM_STATE.md` — technical snapshot: architecture, module map,
  what's implemented, the release pipeline as it actually exists.
- `docs/KNOWN_GAPS.md` — open limitations/risks. Check this before
  assuming something is unhandled -- it may already be a known,
  deliberate trade-off rather than an oversight.

Then, any change that touches app behavior, the build, or the release
pipeline updates all three of these in the same branch/PR — don't let
them drift. That includes `docs/KNOWN_GAPS.md`: add newly-found gaps,
and remove/update ones this change actually resolves.

## Sandbox constraint

This environment usually cannot compile Kotlin/Android locally (no local
Gradle/Android SDK access) — GitHub Actions is the only real compiler
check. Review diffs carefully before pushing; a failed CI round-trip
costs several minutes each time.
