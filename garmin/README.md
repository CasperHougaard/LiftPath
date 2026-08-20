# Garmin Fenix 8 — live logging from the wrist

See the next set's prescription on the watch, adjust it, log it, and get buzzed when rest ends —
while the phone stays fully usable and both views agree.

**Status: phone side is in the build and compiles. Watch side and the transport are not.**
The Kotlin under `app/src/main/java/com/liftpath/watch/` is live; `phone-spike/` and
`liftpath-ciq/` are staged here because they need the Connect IQ SDK and its `.aar`, neither of
which is in this repo.

## How it fits together

```
ActiveTrainingActivity  --implements-->  WatchLink.Host
                                             |
                                          WatchLink          (no Garmin, no workout logic)
                                             |
GarminBridge            --implements-->  WatchLink.Transport
      |
   Connect IQ Mobile SDK -> Garmin Connect Mobile -> Fenix 8 -> LiftPathApp.mc
```

`WatchLink` exists so neither end knows the other: the activity never mentions Garmin, the
bridge never mentions workouts. That is what keeps the `.aar` out of the main compile path —
and it means the phone half was buildable and testable before any watch existed.

| Piece | Where | In build? |
|---|---|---|
| `WatchProtocol.kt` | `app/.../watch/` | yes |
| `WatchLink.kt` | `app/.../watch/` | yes |
| Host implementation | `ActiveTrainingActivity.kt` | yes |
| `GarminBridge.kt` | `garmin/phone-spike/` | no — needs the `.aar` |
| `GarminDiagnosticsActivity.kt` | `garmin/phone-spike/` | no — needs the `.aar` |
| Watch app | `garmin/liftpath-ciq/source/` | no — needs the CIQ SDK |

### What it does and does not do

Works: prescription display, reps/load adjustment, logging a set, rest countdown with a wrist
buzz, rest skip. A set logged from the watch goes through `updateExercises()` exactly as a
phone-logged one does, so superset highlighting, intent locking and the draft write all happen
for free — and `JsonHelper` backfills `familyIdSnapshot` on save the same way.

Does not: start or finish a workout, add or remove exercises, log warmup sets or timed holds.
Those stay on the phone.

**One limitation to understand before relying on it.** `WatchLink` is a *mirror*, not an owner:
`ActiveTrainingActivity` remains the source of truth and republishes on every `persistDraft()`.
So the watch works only while that activity is alive. A *paused* activity is fine — phone in a
pocket, screen off, which is the normal case — but if Android reclaims it under memory pressure
the watch goes idle until you reopen the screen. Fixing that properly means moving the session
into a foreground service (`services/RestTimerService.kt` is the precedent). That is the next
step, and it is deliberately not this one: it is a large change to a 2330-line activity and
there was no reason to take that risk before knowing the transport works at all.

## The assumption this all rests on

> Will Garmin Connect Mobile relay messages for a **sideloaded** Connect IQ app?

The Mobile SDK is written for store-published apps and we are never publishing. If GCM refuses
to relay for an app it has not catalogued, none of the transport works — and no amount of
Android-side work fixes it.

`GarminBridge` treats the catalogue lookup as diagnostic only, never as a gate, so it will run
either way. But you find out on first connection, and it is the thing to check first if nothing
arrives. If it does refuse: recent Fenix devices expose `Toybox.BluetoothLowEnergy` with the
watch as central, and Android can be a `BluetoothGattServer` peripheral, bypassing GCM
entirely. More work, worse battery, not a dead end.

## Bringing it up

### 1. Watch side

1. Install the **Connect IQ SDK Manager**, and in it an SDK plus the Fenix 8 device profile.
2. Install the **Monkey C** extension for VS Code.
3. `Ctrl+Shift+P` → **Monkey C: New Project** → `watch-app`, and pick your Fenix 8 in the
   product list. Let the wizard generate the skeleton: it produces a valid launcher icon and
   the correct product-id strings, which are SDK-version-specific and fail cryptically when
   hand-typed.
4. Copy `liftpath-ciq/source/*.mc` over the generated `source/`, deleting the wizard's stubs.
5. Apply the three edits marked in `liftpath-ciq/manifest.reference.xml` to the generated
   `manifest.xml` — app id, products, `Communications` permission. Do **not** copy that
   reference file over your manifest; it has no real product ids in it.
6. Build for device, then copy the `.prg` into `GARMIN/APPS/` on the watch over USB.

After sideloading, GCM may not notice the new app until it re-syncs the watch's app list. If
the phone sees nothing at all, toggle Bluetooth and reopen Garmin Connect before concluding the
transport is broken.

### 2. Phone side

1. Download the **Connect IQ Mobile SDK for Android** from Garmin's developer site (a `.aar`,
   not on Maven) to `app/libs/connectiq.aar`.
2. In `app/build.gradle.kts`:
   ```kotlin
   implementation(files("libs/connectiq.aar"))
   ```
   A file dependency, not a `flatDir` repository: `settings.gradle` sets
   `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, which rejects per-project repositories.
3. Move `phone-spike/*.kt` → `app/src/main/java/com/liftpath/garmin/`.
4. In `app/src/main/AndroidManifest.xml`:
   ```xml
   <queries>
       <package android:name="com.garmin.android.apps.connectmobile"/>
   </queries>

   <activity android:exported="false"
             android:name="com.liftpath.garmin.GarminDiagnosticsActivity"
             android:screenOrientation="portrait"/>
   ```
   **The `<queries>` entry is not optional.** Without it, package visibility hides Garmin
   Connect and the SDK reports no devices — indistinguishable from an unpaired watch. Same trap
   `CLAUDE.md` documents for `com.tripath`. Permissions come from the `.aar`'s own manifest and
   merge automatically; `<queries>` is the one thing the merger cannot infer.
5. Launch `GarminDiagnosticsActivity` and read the log:
   ```
   adb shell am start -n com.liftpath/com.liftpath.garmin.GarminDiagnosticsActivity
   ```

Once it works, `LiftPathApplication` should own a single long-lived `GarminBridge` instead, and
the diagnostics screen should stop creating its own — two bridges attached to `WatchLink` at
once means the second silently replaces the first.

### 3. Using it

Start a workout on the phone, then open LiftPath on the watch. Note that **the phone cannot
launch the watch app** — Connect IQ has no remote launch, so that second step is always manual.

| Input | Effect |
|---|---|
| START | Log the set as shown. During rest: skip it. |
| UP / DOWN | Adjust the active field |
| MENU (hold) | Cycle active field: reps → kg → exercise |

Three no-look inputs is all the Fenix offers, which is why one of them has to be a mode switch
rather than a fourth value.

## Diagnosing

| Symptom | Cause |
|---|---|
| Watch shows "No session", workout open on phone | Check `WatchLink.hasActiveSession()` in the diagnostics screen. If true, it is the transport; if false, the activity was reclaimed — see the mirror limitation above. |
| `no devices` in the log | Almost always the missing `<queries>` entry. |
| `NOT SENT` on the watch | GCM refused the handoff: not running, or watch unpaired. |
| Nothing either direction | App id mismatch. `manifest.xml` `id` vs `GarminBridge.APP_ID`, dashes stripped. Silent on both ends. |
| "Version skew" on the watch | `WatchProtocol.VERSION` != `Protocol.VERSION`. Rebuild the watch app. |
| Numbers wrong but present | The `EX_*` indices drifted between `WatchProtocol.kt` and `Protocol.mc`. |
