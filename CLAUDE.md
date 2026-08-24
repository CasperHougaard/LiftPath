# LiftPath — Claude Code Rules

## Design System Contract

The app has four user-selectable colour palettes (Settings → Appearance). That is only
possible because colour is expressed as **theme attributes**, not colour resources.

**Never write a colour into a layout, drawable or Kotlin file.** Use a token:

| Instead of | Use |
|---|---|
| `@color/anything` or `#RRGGBB` in XML | `?attr/lpInk`, `?attr/lpSurface`, … |
| `ContextCompat.getColor(ctx, R.color.x)` | `ctx.lpColor(R.attr.lpX)` (`helpers/ThemeColors.kt`) |
| `android:textSize` + `android:textStyle` | one `android:textAppearance="@style/TextAppearance.LP.*"` |
| a new corner radius | `@dimen/lp_radius_sm` / `_md` / `_lg` — there are only three |

A static `@color/` cannot follow the selected palette, so using one silently pins that view
to a single theme. The legacy `fitness_*` / `intent_*` / `pr_*` palette was deleted for
exactly this reason — do not reintroduce it.

**Adding a colour** means adding a *role* to `lp_attrs.xml` and binding it in all four
`ThemeOverlay.LiftPath.*` blocks. An unbound token renders magenta on purpose, so a missed
binding is impossible to ship unnoticed. Adding a fifth palette should require **zero**
layout changes: one `lp_palette_*.xml` pair, one overlay, one `LiftPathTheme` entry.

**Two things that are deliberately not theme attributes**, and why:

- Animated-vectors loaded via `srcCompat` do not resolve theme attributes reliably, so
  `lp_logo_trace` is drawn white and tinted at the point of use with
  `app:tint="?attr/lpAccent"`. Same trick for `lp_hero_glow` and `lp_dot`.
- The swatches in the Appearance picker reference `paper_*` / `chalk_*` / … directly. They
  must show all four palettes at once, and `?attr/` only ever resolves the *active* one.

Palettes are applied by `LiftPathApplication` in `onActivityPreCreated` as an **overlay**,
not a full theme — `setTheme` merges, so a full theme would clobber the dialog windowing on
`RestTimerDialogActivity`. That is why no activity needs exempting.

**Key files:**

| File | Role |
|---|---|
| `app/src/main/res/values/lp_attrs.xml` | The token roles. Start here. |
| `app/src/main/res/values/lp_palette_*.xml` (+ `values-night/`) | The four palettes' values |
| `app/src/main/res/values/themes.xml` | Base theme + the four palette overlays |
| `app/src/main/res/values/lp_type.xml` | 6 prose sizes + 4 metric sizes. Archivo for prose, JetBrains Mono for numbers that *change*. |
| `app/src/main/res/values/lp_styles.xml` | Card / button / input / chip styles — cards are hairline, never shadowed |
| `app/src/main/res/values/lp_dimens.xml` | Radii + the airy/dense spacing scales |
| `app/src/main/java/com/liftpath/helpers/AppearanceManager.kt` | Palette enum + persistence |
| `app/src/main/java/com/liftpath/helpers/Motion.kt` | Shared entrance/press motion |
| `app/src/main/java/com/liftpath/views/AmbientFlowView.kt` | The ambient background: an AGSL flow field. Hosted by 23 layouts. |
| `app/src/main/res/values/lp_floats.xml` (+ `values-night/`) | Non-colour tunables that still need a day/night split |

## Motion Contract

Two pieces of motion are shared rather than per-screen, and both have a failure mode worth
knowing about.

**The ambient background** is `AmbientFlowView` — a full-screen AGSL fragment shader, not a
drawable. It reads `?attr/lpAccent` through `lpColor` and its alpha ceiling from
`lp_ambient_intensity`, which has a `values-night` override because the same alpha is a much
smaller perceptual step for a light accent on a dark canvas than the reverse. It starts and
stops itself from `onVisibilityAggregated`; **never add a `start()` call for it** — needing one
is exactly what the old animated-vector got wrong, and two of its 23 host layouts silently
shipped a frozen background as a result. `RuntimeShader` throws on a shader error, so
construction is guarded: a broken shader degrades to a flat wash and logs, rather than killing
every screen that hosts it.

**The cold-start reveal** lives in `MainActivity.animateSplashExit` and spans three files that
must stay in step:

- `lp_logo_trace.xml`'s stroke is *pixel-identical* to the filled `ic_app_logo` — a 12-wide
  stroke down the mark's centrelines with butt caps and a miter join. Change the width, caps or
  join and the trace still animates but no longer lands on the mark.
- Its `objectAnimator` duration is mirrored by `MainActivity.TRACE_MS`. `srcCompat` gives no
  completion callback for an `<animated-vector>`, so the bloom is timed against that number.
- The splash view is faded out *first*, not last. `windowSplashScreenBackground` is opaque and
  the splash sits above this activity's content, so anything animated underneath it is
  invisible — which is why the previous version of this reveal could not be seen at all.

`WorkoutFragment`'s entrance waits on `MainActivity.onEntranceReady` so the cascade plays after
the reveal instead of behind it. A gated caller must call `Motion.prepareEntrance` first (or the
cards sit at full opacity and then blink out to rise), and the gate carries a timeout — a
hand-off that never arrives would otherwise leave a permanently blank home screen.

## Navigation Contract

`MainActivity` is a bottom-nav host with four fragment destinations: `WorkoutFragment`
(nav id `nav_workout`), `PlanFragment` (`nav_plan`), `ProgressHubFragment`,
`LibraryFragment`. Everything below a tab (edit screens, pickers, the active workout) stays
an Activity.

Tabs are named after what they *do*, not when you use them — the earlier "Today"/"Train"
pair described neither. Each tab owns one job and does not duplicate another's:

| Tab | Job |
|---|---|
| Workout | Start or resume a session; readiness, what's up next, what was last |
| Plan | Author plans and rotations |
| Progress | Every number, including the only date-axis charts in the app |
| Library | The exercise catalogue |

Analytics belong in Progress. The Workout tab lost its stat tiles for that reason, and its
one link into Progress (the momentum card) goes through `openProgress(subTab)`.

**`MainActivity` must remain the `LAUNCHER` activity and keep its class name.** It is also
the `targetActivity` of the `ViewPermissionUsageActivity` alias that Health Connect
launches for `VIEW_PERMISSION_USAGE`; renaming it or repointing the alias breaks that deep
link. That constraint is why the home *content* lives in `WorkoutFragment` rather than in a
new host class.

Two rules that are easy to get wrong:

- **Only the `setOnItemSelectedListener` swaps fragments.** Programmatic navigation goes
  through `selectTab`, which sets `selectedItemId` and lets the bar fire the listener.
  Assigning `selectedItemId` *inside* the listener re-enters it and recurses until the
  stack overflows — this was a real crash, not a hypothetical.
- **`ProgressPagerAdapter` takes a `Fragment`, not a `FragmentActivity`**, so its six pages
  live in the hub's *child* FragmentManager. Passing the activity orphans them when the tab
  is hidden.

Tabs use add/show/hide rather than replace, so each tab keeps its view state — which is
what stops the Progress charts rebuilding on every visit. Insets are applied once, in
`MainActivity.applyWindowInsets`; fragment layouts must not set `fitsSystemWindows`.

## Backup Coverage Contract

`BackupManager.kt` enumerates what gets backed up. It cannot discover new state on its own, so
**any new persisted state must be added to it or it will be silently lost on a phone swap.**

When you add either of the following, update `BackupManager`:

- a new file written to `context.filesDir` → add its name to `BACKED_UP_FILES`
- a new `getSharedPreferences(...)` file → add its name to `BACKED_UP_PREFS`

Deliberately excluded: `cacheDir` (the active-workout draft) and `backup_settings` itself —
that one describes *this device's* backup wiring, and restoring it onto a new phone would point
it at a folder URI it has no permission for.

Bump `BackupBundle.CURRENT_FORMAT_VERSION` only when the envelope shape changes, not when
adding files or prefs to the lists — old bundles stay readable, they just carry less.

**Key files:**

| File | Role |
|---|---|
| `app/src/main/java/com/liftpath/helpers/BackupManager.kt` | Collect + restore all local state |
| `app/src/main/java/com/liftpath/helpers/BackupScheduler.kt` | When backups run (hooked into `JsonHelper.writeTrainingData`) |
| `app/src/main/java/com/liftpath/helpers/LocalFolderBackupHelper.kt` | SAF folder destination |
| `app/src/main/java/com/liftpath/helpers/DriveAuthHelper.kt` | Play Services Authorization API — gets the Drive access token |
| `app/src/main/java/com/liftpath/helpers/DriveBackupHelper.kt` | Google Drive destination |
| `GOOGLE_DRIVE_SETUP.md` | One-time OAuth client registration |

## Workout Plan Import/Export Contract

`WorkoutPlanMarkdownHelper.kt` encodes the markdown schema used for AI-generated plan
import/export. When any of the following change, verify the helper still works end-to-end:

- `WorkoutPlan` fields (`DataModels.kt`)
- `PlanExerciseSlot` fields (`DataModels.kt`)
- `ExerciseLibraryItem` fields or IDs (`DataModels.kt` / `DefaultExercisesHelper.kt`)
- `SetIntent` enum values (`DataModels.kt`)

**Checklist after data-model changes:**

1. `buildSpecMarkdown` still emits correct column order and enum value strings.
2. `parsePlansFromMarkdown` column indices still match (column 0 = name, 1 = id, 2 = sets,
   3 = reps, 4 = intent, 5 = rpe, 6 = rest, 7 = notes, 8 = family id, 9 = time (sec)).
3. The field reference table in the export doc is still accurate.
4. Round-trip test: export spec → add a `## Plan:` section with real exercise IDs → import →
   confirm all fields (intent, setsTarget, rpeTarget, restTimeSeconds, durationSeconds) are
   populated correctly. For a timed exercise (e.g. Plank), Reps is blank and Time (sec) is set.
5. Circuits round-trip too: a `### Circuit:` block (`Suggested rounds:` / `Rest (sec):` lines +
   its own `| Exercise Name | Exercise ID | Reps | Time (sec) | Load (kg) | Notes |` table) is
   matched by name against existing stored circuits, and a `__circuit__: <name>` row inside a
   `## Plan:` table (Sets = suggested rounds, Rest (sec) = rest between rounds) resolves to that
   template. A name matching nothing lands in `unresolvedCircuitNames`, not a crash.

**Key files:**

| File | Role |
|---|---|
| `app/src/main/java/com/liftpath/helpers/WorkoutPlanMarkdownHelper.kt` | Build spec + parse import |
| `app/src/main/java/com/liftpath/helpers/JsonHelper.kt` | `exportWorkoutPlanSpec` / `importWorkoutPlans` |
| `app/src/main/java/com/liftpath/fragments/PlanFragment.kt` | UI entry points (⋮ menu) |
| `app/src/main/res/menu/menu_workout_plans.xml` | Popup menu items |

## TriPath Integration Contract

LiftPath can read training load, recovery and energy figures from the TriPath app
(`C:\Projects\TriPath`, package `com.tripath`) over a read-only ContentProvider. It is a personal,
sideloaded integration: **one-way pull, and entirely optional.**

`TriPathConnection.isActive(context)` is the only gate. It is false unless TriPath is installed,
its provider answered a handshake, *and* the user connected it in Settings. When false, LiftPath
must behave exactly as it did before this feature existed — no TriPath card on the readiness
dashboard, no Fuel page in Progress, and a fatigue curve built from Health Connect alone. Any new
consumer goes through that gate, and through `ExternalLoadProvider` rather than reading storage
directly.

**The contract is duplicated in two repos** — there is no shared module:

| Side | File |
|---|---|
| TriPath (producer) | `app/src/main/java/com/tripath/data/local/share/TriPathShareContract.kt` |
| LiftPath (consumer) | `app/src/main/java/com/liftpath/helpers/TriPathContract.kt` |

**Bump `CONTRACT_VERSION` in both whenever a column is added, removed or re-typed.** Columns are
read by name through the `optInt`/`optFloat`/… helpers in `TriPathCursor.kt`, so a version skew
degrades to nulls rather than crashing — but Settings shows the mismatch, and it should be fixed.

Two things that are easy to get wrong:

- **The provider is not permission-protected**, and must not be. The two apps are signed with
  different keys, so a `signature` permission would always be denied. `TriPathShareProvider`
  verifies `callingPackage` instead, which the framework derives from the binder UID.
- **LiftPath needs `<package android:name="com.tripath"/>` in `<queries>`.** Without it, package
  visibility hides the provider and the integration silently never connects.

Deduplication is exact, not heuristic: TriPath's `WorkoutLog.connectId` and LiftPath's
`ExternalActivity.id` are both the Health Connect `metadata.id` for the same session.
`ExternalLoadProvider` resolves the duplicate in TriPath's favour, since its row carries TSS and
heart-rate zones where LiftPath's carries only a duration. TriPath sessions of type `STRENGTH` are
dropped — that load is LiftPath's own, from actual sets and RPE.

**Key files:**

| File | Role |
|---|---|
| `helpers/TriPathConnection.kt` | Install check, handshake, enable toggle — the gate |
| `helpers/TriPathSyncHelper.kt` | Pulls days + workouts into local storage |
| `helpers/TriPathStorageHelper.kt` | `tripath_data.json` cache (registered in `BackupManager`) |
| `helpers/TriPathFatigueMapper.kt` | TSS → fatigue, sleep/HRV → recovery factor, TSB → thresholds |
| `helpers/ExternalLoadProvider.kt` | Single merged, deduplicated source of external load |
| `helpers/LiftingBurnEstimator.kt` | LiftPath's own kcal estimate, for cross-checking TriPath's |
| `fragments/ProgressFuelFragment.kt` | Progress > Fuel (conditional page) |

## Circuit Contract

A circuit is a fixed list of exercises done back-to-back and repeated for rounds, rest only
between rounds. Four things a future change can silently break:

- **A circuit row is not `isSpecialElement`.** It uses a negative sentinel `exerciseId`
  (`CIRCUIT_ROW_ID_BASE = -100`, then -101, …) exactly like warmup/cooldown do, but it is its own
  `slotType == CIRCUIT` with its own `isCircuit` check. Any call site that filters "not warmup,
  not cooldown" to mean "a real exercise" (auto-expand, muscle overview, default-intent seeding,
  set targets) must also exclude `isCircuit`, or a circuit row gets treated as a loggable exercise.
- **Suggested rounds and rest ride on existing plan fields, not new ones.** A plan's circuit slot
  (`PlanExerciseSlot(slotType = CIRCUIT, circuitId = …)`) reuses `setsTarget` for the *suggested*
  round count (nullable — a plan may leave it open) and `restTimeSeconds` for the rest between
  rounds. `CircuitTemplate` carries its own defaults; the slot's values override them via
  `CircuitStore.templateToInstance(template, slot)`.
- **A logged circuit set is an ordinary `ExerciseEntry`**, not a special shape: `groupId` =
  the `CircuitInstance.instanceId`, `groupType = GroupType.CIRCUIT`, `setNumber` = the round
  number. This is what lets Progress, PRs and muscle maps read circuit sets with no special-casing.
- **Round counts are suggestions, never limits.** `CircuitInstance.suggestedRounds` is display
  only ("round 2 of 3"); nothing may treat it as a stopping condition or clamp `setNumber` to it.
  The runner always offers another round past a suggestion, and `CircuitStore.instanceToLog`
  reports rounds actually run, not the suggestion.

**Key files:**

| File | Role |
|---|---|
| `helpers/CircuitStore.kt` | Template ↔ instance ↔ logged-set conversions; the only place round math happens |
| `activities/CircuitRunnerActivity.kt` | The full-screen runner: round clock, station stopwatch, rest countdown |
| `activities/EditCircuitActivity.kt` | Authors a `CircuitTemplate` |
| `components/CircuitPickerBottomSheet.kt` | "Which circuit?" — used from the active workout |
| `components/CircuitRoundLogBottomSheet.kt` | Logs one round: a station row per exercise |
| `adapters/ActiveExercisesAdapter.kt` | `VIEW_TYPE_CIRCUIT` / `bindCircuitViewHolder` — the row in a live workout |

Plan-embedded circuits work end to end: `EditWorkoutPlanActivity`'s **Add circuit** button and
`PlanExerciseAdapter`'s circuit row author a `PlanExerciseSlot(slotType = CIRCUIT)`;
`ActiveTrainingActivity.applyPlan` and `PlanRotationHelper` resolve it; `WorkoutPlanMarkdownHelper`
round-trips it through `### Circuit:` blocks and `__circuit__:` rows (see the Workout Plan
Import/Export Contract above).

**6 starter circuits** ship via `DefaultCircuitsHelper.seedIfNeeded`, called from
`JsonHelper.normalizeTrainingData` (every read — cheap no-op once seeded) and from
`CatalogMergeHelper.handleMergeResult` (immediate chance right after new exercises are accepted).
Stations are matched **by normalized exercise name, not id** — a catalog merge assigns a new
exercise `maxId + 1`, not its catalog id — and a circuit resolving fewer than two stations is
skipped, not added half-empty, so it retries on a later read once the rest of its exercises exist.
The 10 bodyweight/band exercises they need (Wall Sit, Single-Leg Glute Bridge, etc.) are catalog
ids 300-309 in `DefaultExercisesHelper` (`CATALOG_VERSION` 6).

## Garmin Watch Contract

A Connect IQ app on the Fenix 8 can drive live logging from the wrist. Like TriPath this is a
personal, sideloaded integration and **entirely optional** — but unlike TriPath it is two-way,
so the failure modes are worse and the layering matters more.

`WatchLink` is the only thing either side talks to. `ActiveTrainingActivity` implements
`WatchLink.Host` and never mentions Garmin; `GarminBridge` implements `WatchLink.Transport` and
never mentions workouts. **Keep it that way.** The Connect IQ Mobile SDK is a `.aar` that is not
in the repo, so any Garmin import outside `com/liftpath/garmin/` breaks the build for anyone who
has not downloaded it. That layering is also why the phone half is testable with no watch
present.

**The wire format is duplicated in two languages** — there is no shared module:

| Side | File |
|---|---|
| Phone | `app/src/main/java/com/liftpath/watch/WatchProtocol.kt` |
| Watch | `garmin/liftpath-ciq/source/Protocol.mc` |

Exercises are sent as **positional arrays**, not keyed maps, because Monkey C dictionaries cost
real watch memory. So the `EX_*` index constants are load-bearing: if the two files drift, fields
shift silently by one rather than failing. Change both together, and **bump `VERSION` in both**
— the watch refuses a state whose version it does not recognise, which turns a skew into a
visible message instead of wrong numbers on a wrist.

Three things that are easy to get wrong:

- **`WatchLink` is a mirror, not an owner.** `ActiveTrainingActivity` is still the source of
  truth and republishes on every `persistDraft()` — which is the single publish point, so any
  new mutation path gets watch updates for free *provided it persists the draft*. The cost is
  that the watch only works while that activity is alive. Paused is fine; reclaimed is not.
  Moving the session into a foreground service is the fix, and is not done.
- **LiftPath needs `<package android:name="com.garmin.android.apps.connectmobile"/>` in
  `<queries>`.** Without it package visibility hides Garmin Connect and the SDK reports no
  devices — indistinguishable from an unpaired watch. Exactly the TriPath trap.
- **Never gate on `getApplicationInfo`.** It reports a sideloaded app as not-installed even when
  messaging works, so treating it as a precondition would refuse to run on the only setup this
  is built for. It is diagnostic output, nothing more.

Commands from the watch must stay advisory. Connect IQ has no way for the phone to launch the
watch app, and Bluetooth drops constantly, so the phone can never wait on the watch or assume it
is present.

**Key files:**

| File | Role |
|---|---|
| `watch/WatchProtocol.kt` | Wire format + command parsing |
| `watch/WatchLink.kt` | Host/Transport mediator — the seam |
| `activities/ActiveTrainingActivity.kt` | `WatchLink.Host`: projection + command application |
| `garmin/phone-spike/GarminBridge.kt` | Connect IQ transport (staged, needs the `.aar`) |
| `garmin/liftpath-ciq/source/` | The Monkey C watch app |
| `garmin/README.md` | Setup, wiring steps, symptom-to-cause table |
