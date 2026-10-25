# Details: one loading skeleton frame (TASK B)

- Branch: `perf/unified-skeleton`
- Base: `6dcc023bb` (`testbuild/all-prs-20261025`, DB24)
- Worktree: `D:\komi-wt-unified-skeleton`
- Date: 2026-10-25

## Task

The Details screen stacked three differently shaped loading shells: a full screen `DetailsSkeleton`
(220dp header block, avatar row, three 72dp cards), then the real `AppHeader` followed by
`LatestReleaseSkeleton` (version chips, two pickers, install row), then the real content. Each shell
had its own padding, header height and row heights, so the page jumped twice.

Collapse them into one frame that keeps the real layout's geometry, and replace its areas in place
as the repository and the latest release arrive.

## What changed

| File | Change |
| --- | --- |
| `components/DetailsSkeleton.kt` | Rewritten. Holds the frame's stand-ins: `DetailsHeaderSkeleton` (the `AppHeader` card), `DetailsAssetSkeleton` (the former `LatestReleaseSkeleton`, unchanged row geometry) and the shared block/line primitives, all sized from personality tokens. |
| `components/sections/SkeletonSections.kt` | New. `skeletonSections()` plus the stats section stand-in, next to the real `sections/Stats.kt` it mirrors. |
| `components/LatestReleaseSkeleton.kt` | Deleted; its rows now live in `DetailsSkeleton.kt` as `DetailsAssetSkeleton`. |
| `components/sections/Header.kt` | `header` fills the header and asset slots itself: two skeletons while `state.repository == null`, `AppHeader` in place after that, `DetailsAssetSkeleton` in the asset slot while `isLatestReleaseLoading`. |
| `DetailsRoot.kt` | Drops the `state.isLoading` early return and its full screen `DetailsSkeleton`. The `LazyColumn` frame is now the only container; `contentPadding` and item gap use `Spacing.lg` / `Spacing.xl` (same 16dp / 24dp as before). Section stand-ins are added only while the repository is missing. |
| `feature/details/CLAUDE.md` | The "latest-release skeleton" note becomes the "one loading frame" note. |

No ViewModel, state field, network, auth, theme, deferred-section or string resource was touched.

## Geometry: what each block is measured from

| Skeleton block | Real component | Sizing source |
| --- | --- | --- |
| Header card surface, 16dp padding | `AppHeader` -> `KomiSurface(Tinted, Corner, Spacing.lg)` | same composable, same tokens |
| Picture 80dp / 84dp | `HeaderPicture` (Classic / Manga) | same values |
| Title, owner, last push, description lines | `RepoTitle` (`displayEmphasized`), `OwnerLine` (`label` / `overline`), `LastPush` (`caption`), description (`body`) | `lineHeight` of the role the line stands in for, converted to dp, so it follows the personality and the system font scale |
| Header footer (dock) 56dp | `PlatformTile` (`Spacing.sm * 2 + 20dp icon + Spacing.xs + labelSmall`) | derived from the tile's own parts |
| Version chips 32dp / 34dp | `VersionTypePicker` filter chips | Classic Material chip / Manga chip |
| Assets row 56dp, version row 60dp | `ReleaseAssetsPicker`, `VersionPicker` (`max(36dp, text) + 2 * 10dp`) | derived, reproduces the previously hand tuned 56 / 60 |
| Install row 56dp | `SmartInstallButton` (`ButtonHeight`) | same value |
| Stats stand-in (title + two rows of three tiles) | `sections/Stats.kt` (20dp lead, title, 10dp gaps, `StatFrame` padding) | tile = `2 * 10dp + labelSmall + numeral` line heights |

For Classic the header card comes out at 16 + 84 + 12 + 20 + 12 + 56 + 16 = 216dp with the picture
row 84dp tall, and the stats stand-in at 20 + 28 + 10 + 68 + 10 + 68 = 204dp, which is what the real
`AppHeader` and `Stats` measure with one description line and one dock row.

## Loading sequence now

1. No repository: one `LazyColumn` (16dp padding, 24dp gap) holding `DetailsHeaderSkeleton`,
   `DetailsAssetSkeleton` and the stats stand-in. The list scrolls, so a short screen can still
   reach the stand-ins; there is no full screen spinner and no fixed height page block.
2. Repository lands: the header slot becomes `AppHeader` in place, at the same position and with the
   same height. The asset slot keeps the exact same skeleton, so nothing above it moves.
3. Latest release lands: the asset slot is replaced in place by the version filter, the pickers and
   the install button.
4. `areSectionsPending` clears: the real stats, release channel, what's new and about sections are
   added below.

After step 2 the page is the real one; the whole page skeleton cannot come back, because
`skeletonSections()` is gated on `state.repository == null` and the header and asset stand-ins are
gated on the same two flags.

Keys (`details-header-skeleton`, `details-header`, `details-asset-skeleton`,
`details-linked-repo-banner`, `details-stats-skeleton`) keep the assets inside the list stable while
the slots swap.

## Verification

Run in `D:\komi-wt-unified-skeleton` (JDK 21, `sdk.dir` copied into the worktree's
`local.properties`):

```
./gradlew spotlessApply
./gradlew detekt
./gradlew :feature:details:presentation:jvmTest
./gradlew :androidApp:assembleDebug
```

Results: `spotlessApply` reported one lint error on the first pass (`compose:param-order-check` on
`SkeletonPicker`), fixed by hand; after that all four tasks are green.

```
> Task :spotlessApply
> Task :detekt
> Task :feature:details:presentation:jvmTest
> Task :androidApp:assembleDebug
BUILD SUCCESSFUL in 38s
```

- `:feature:details:presentation:jvmTest`: 6 suites, 28 tests, 0 failures, 0 skipped (no new tests;
  the presentation tests are state tests and this change touched only composables).
- `:androidApp:assembleDebug`: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`, 69.3 MB,
  written after the last source edit.
- `detekt`: no findings (a first attempt failed `TooManyFunctions` with 12 functions in
  `DetailsSkeleton.kt`; the stats stand-in moved to `components/sections/SkeletonSections.kt`).

## Residual risks

- **No UI or screenshot test.** Nothing in the repo renders Compose in tests for this feature, so the
  geometry is verified by construction (both sides read the same tokens) and by the previews, not by
  an assertion. A golden image test would be the way to lock it.
- **Content driven heights still guess.** The header stand-in shows one description line and one
  platform dock row. A repository with a three line description, a fork chip plus an app switcher
  row, or no releases at all (no dock) will still change the header's height when it lands, by the
  height of the rows it lacks. The asset rows and the stats stand-in are exact.
- **No device run.** The change was verified by compile, detekt and APK assembly only; the scroll
  during the first frame and the in place swap have not been observed on a device or emulator.
- **`agent-reports/TEMPLATE.md` does not exist** in this repository or in any worktree/branch, and no
  `agent-reports/` directory has ever been committed, so this report uses the sections above instead
  of the template's. If the template lives elsewhere, the report should be reshaped to it.
- **`LatestReleaseSkeleton.kt` was deleted**, so a branch that still references that name will
  conflict at merge time. `feature/details/CLAUDE.md` was updated in the same commit.
- **Not a required check, run for information:** `:konsist-test:test` fails on `FileSizeTest` for
  `core/data/.../InstalledAppsRepositoryImpl.kt` (over the 800 line limit). That file is untouched by
  this change and the failure is already present at `6dcc023bb`; `DocsRulesTest` and
  `DesignTokenRulesTest` pass with this change.
