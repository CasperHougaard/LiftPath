# Garmin Fenix 8 — live logging from the wrist

See the next set's prescription on the watch, adjust it, log it, and get buzzed when rest ends —
while the phone stays fully usable and both views agree.

**Status: both sides build, and the `.prg` sideloads. End-to-end messaging is still unproven.**
`app/.../watch/` and `app/.../garmin/` are live; the Connect IQ companion SDK comes from Maven
Central, so nothing needs downloading by hand. `liftpath-ciq/` builds a signed `.prg` for
`fenix847mm` against SDK 9.2.0. The only account-gated step left is installing the SDK itself.

Verified on an emulator: `GarminBridge` initialises and reports `init failed: GCM_NOT_INSTALLED`,
which is the correct answer where Garmin Connect is absent. That confirms the dependency, the
manifest merge and the `WatchLink` wiring — it says nothing about whether messages flow.

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
| `GarminBridge.kt` | `app/.../garmin/` | yes |
| `GarminDiagnosticsActivity.kt` | `app/.../garmin/` | yes — debug builds export it |
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

**Partially answered.** Against a real Pixel + Fenix 8 47mm, with no watch app installed yet:

```
SDK ready
device fenix 8 - 47mm (UNKNOWN)
watch app not catalogued (expected when sideloaded)
state send failed: FAILURE_DURING_TRANSFER
```

So GCM does enumerate the watch to us and `registerForAppEvents` succeeds for an app id it has
never catalogued — the two places a store-only policy would most plausibly have blocked us. The
send failure is not evidence either way: there is no app on the watch to receive it yet. The
question stays open until the `.prg` is sideloaded, but it is now narrowed to the last hop.

`(UNKNOWN)` for device status is not a disconnection. `GarminBridge` never calls
`registerForDeviceEvents`, so it only ever sees the snapshot status from `getConnectedDevices()`.
Worth adding when the transport is real; harmless now.

## Bringing it up

### 1. Watch side

`liftpath-ciq/` is now a complete, buildable project — `manifest.xml`, `monkey.jungle`,
`resources/` and `source/` are all committed, so the **New Project wizard is no longer part of
this.** `manifest.reference.xml` is kept only as the commentary on *why* each field is what it
is; nothing reads it.

The wizard was originally required because product-id strings are SDK-version-specific and fail
obscurely when hand-typed. That turned out to be avoidable: the installed SDK states them
itself. Every directory under `%APPDATA%\Garmin\ConnectIQ\Devices\` is a valid product id, and
each one's `compiler.json` carries the `deviceId`, the `launcherIcon` dimensions and the memory
limit per app type. Read the value from there instead of guessing it.

1. Install the **Connect IQ SDK Manager**, sign in, and pull an SDK plus the Fenix 8 device
   profile. Verified against SDK **9.2.0**.
2. Generate a developer key once — builds are signed, and there is no default key:
   ```sh
   openssl genrsa -out developer_key.pem 4096
   openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key -nocrypt
   ```
   Keep it **outside the repo** (`~/.Garmin/developer_key`). It is a private signing key.
   For VS Code builds, point `monkeyC.developerKeyPath` at it.
3. Build:
   ```sh
   SDK=~/AppData/Roaming/Garmin/ConnectIQ/Sdks/connectiq-sdk-win-9.2.0-2026-06-09-92a1605b2
   cd garmin/liftpath-ciq
   "$SDK/bin/monkeyc.bat" -f monkey.jungle -d fenix847mm -o LiftPath.prg \
       -y ~/.Garmin/developer_key -w
   ```
   Sanity check on the output: a `.prg` starts with the magic `D0 00 D0 0D`, and the app-id
   bytes appear near offset `0x33`. If those 16 bytes are not
   `672412e7d04c4f2fb161521358b4010f`, the manifest and `GarminBridge.APP_ID` have drifted and
   messages will vanish silently.
4. Copy `LiftPath.prg` into `GARMIN/APPS/` on the watch over USB.

**The watch must be in MTP USB mode before step 4.** A Fenix defaults to `USB Mode = Garmin`,
where it enumerates as `VID_091E&PID_0003` bound to the legacy `grmnusb` driver — the Garmin
Express protocol, which exposes no filesystem at all. There is no drive letter and no portable
device, so there is nothing to copy into, and this looks like a dead cable. Set
**Settings → System → USB Mode → MTP** on the watch; it then enumerates as
`VID_091E&PID_51B8`, class `WPD`, service `WUDFWpdMtp`, and appears under This PC as
`fenix 8 - 47mm`.

MTP means no drive letter even in the working case, so `Copy-Item` cannot reach it. Use the
Shell COM API — navigate This PC → device → storage → `GARMIN` → `APPS` and `CopyHere`. MTP
writes are asynchronous, so poll for the file rather than trusting the call to have finished.

Two smaller traps around the mode switch: the stale `PID_0003` device nodes linger as
`Present: False` phantoms and `Get-PnpDevice -PresentOnly` has been seen to return them anyway,
so check the `Present` field rather than trusting the filter. And **while the watch is plugged
in over MTP, `getConnectedDevices()` returns empty** — the phone loses it, and the diagnostics
screen reports `no devices` even though `<queries>` and the pairing are both fine. Unplug the
watch before testing the transport.

After sideloading, GCM may not notice the new app until it re-syncs the watch's app list. If
the phone sees nothing at all, toggle Bluetooth and reopen Garmin Connect before concluding the
transport is broken.

### 2. Phone side — already done, described here for maintenance

Nothing to do; this section records what was wired and why, since the original plan assumed a
hand-downloaded `.aar` and that is no longer how the SDK ships.

1. The dependency is a normal Maven coordinate in `app/build.gradle.kts`:
   ```kotlin
   implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.4.0")
   ```
   Garmin publishes the companion SDK to Maven Central as of 2.x. No `app/libs/`, and in
   particular no `flatDir` repository — `settings.gradle` sets
   `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, which would reject one.
2. `GarminBridge.kt` and `GarminDiagnosticsActivity.kt` live in
   `app/src/main/java/com/liftpath/garmin/`. Every SDK symbol they use (`ConnectIQ`, `IQApp`,
   `IQDevice`, `IQConnectType`, `IQMessageStatus`, the four listener interfaces) is present in
   2.4.0 with matching signatures, so they compile unchanged from the spike.
3. `<queries><package android:name="com.garmin.android.apps.connectmobile"/></queries>` is in
   `app/src/main/AndroidManifest.xml`. Without it, package visibility hides Garmin Connect and
   the SDK reports no devices — indistinguishable from an unpaired watch. Same trap `CLAUDE.md`
   documents for `com.tripath`.

   Worth knowing: the SDK's *own* manifest declares this `<queries>` block too, so the merger
   would contribute it either way. It is stated explicitly regardless — it is cheap, and the
   alternative is depending on a transitive manifest for a silent-failure mode.
4. The diagnostics activity is `exported="false"` in the main manifest and re-declared
   `exported="true"` in `app/src/debug/AndroidManifest.xml` via `tools:replace`. That split
   exists because `adb shell am start` cannot launch a non-exported activity — it fails with
   `Permission Denial: ... not exported` — and adb launch is the only entry point the screen
   has. Release builds keep it unexported.
5. Launch it and read the log:
   ```
   adb shell am start -n com.liftpath/com.liftpath.garmin.GarminDiagnosticsActivity
   adb logcat -s GarminBridge
   ```
   On a device without Garmin Connect you get `init failed: GCM_NOT_INSTALLED` and the SDK
   pops its own install prompt. That is success for this step, not a fault.

**Done, and it mattered more than it looked.** `LiftPathApplication` owns one long-lived bridge
through `WatchTransport`; the diagnostics screen only attaches a log sink. While that screen
owned the *only* bridge, the transport existed solely while it was open — and the watch cannot
tell "nobody is listening" from "no workout running". `Communications.transmit` only needs
Garmin Connect to accept the message, so the watch would show a green **synced** for a sync
nothing ever answered, then sit on it. Two bridges at once is the other failure to avoid:
`attachTransport` keeps the newest, so the older goes deaf silently.

Toasts also expire on their own now (`SessionView.TOAST_MS`) rather than waiting for a state push
to clear them, since the case where no push follows is exactly the case where nothing is
listening. That was the first half of the fix; see **A state push is the only confirmation**
below for the part that matters more.

### 3. Using it

Start a workout on the phone, then open LiftPath on the watch. Note that **the phone cannot
launch the watch app** — Connect IQ has no remote launch, so that second step is always manual.

| Input | Effect |
|---|---|
| START | Log the set as shown. During rest: skip it. |
| UP / DOWN | Adjust the active field |
| MENU (hold) | Cycle active field: kg → reps → exercise |

Three no-look inputs is all the Fenix offers, which is why one of them has to be a mode switch
rather than a fourth value. Landing on a fresh exercise — including via the auto-advance below —
always starts back on kg; the workflow this app is built around is deciding the load before the
reps.

**Two considered and rejected inputs, and why.** The Fenix 8 47mm's device profile reports
`isTouch: true`, but its default gesture map sends a bare tap to `onSelect` — the same action as
pressing START to log a set, so an accidental screen brush mid-lift could misfire a log. And the
BACK/lap button, the one genuinely idle physical input, has exiting the app as its default job on
every Garmin watch app; there is no documented way to get that back once `onBack()` is overridden
and marked handled. Both stay unused for the same reason: it must not be possible to commit
something by accident.

**Exercise selection auto-advances.** Logging a set that fills the current exercise's target
(`SessionModel.advanceIfComplete`, called from `SessionDelegate.onSelect` right after sending the
log) moves the cursor to the next incomplete exercise on its own, so the straight-through-the-plan
case never needs a manual MENU-cycle. It is a one-shot, watch-local guess made from the numbers
that were just sent, not something re-derived from the phone's confirmation — deliberately not run
from every incoming state push, since the watch cannot tell "this confirms the set I just logged"
apart from "this is the 60-second watchdog probe reply," and advancing on every push would yank
the cursor off wherever a lifter deliberately navigated back to (e.g. to add a bonus set to an
already-complete exercise). If a log is later `NOT CONFIRMED`, the cursor has already moved and
will not roll back — recoverable via the manual override below, and failures already surface
loudly.

Manual override is unchanged and still the way to go out of plan order (supersets, skipping,
revisiting a completed exercise): MENU-hold to `FIELD_EXERCISE`, then UP/DOWN. The exercise name
renders in blue while that field is active — plain grey was easy enough to miss that it read as
"the exercise never changes" when someone pressed UP/DOWN without noticing which field they were
actually adjusting.

Separately, `SessionModel.applyState` now re-finds the same exercise by its `EX_ID` rather than
trusting the cursor's index across a push. The phone's exercise list really does reorder in place
(superset formation, warmup/cooldown pinning), and a pure index clamp would have silently
repointed the cursor at whatever now sits at that index — a different exercise than the one being
looked at, with nothing on screen to show it happened.

## Diagnosing

| Symptom | Cause |
|---|---|
| Watch shows "No session", workout open on phone | Check `WatchLink.hasActiveSession()` in the diagnostics screen. If true, it is the transport; if false, the activity was reclaimed — see the mirror limitation above. |
| Watch shows **No phone** | A probe went unanswered. The phone app is not running, or Bluetooth dropped. Distinct from "No session", which means the phone answered and has no workout open. |
| Watch shows **NOT CONFIRMED** after a log | The command was accepted by GCM but no state push followed. It may or may not have landed — check the set list on the phone. |
| Watch stuck on yellow **sending** | A state push never arrived and the watchdog has not timed it out yet. If it persists, the phone applied nothing. |
| `no paired devices` in the log | Almost always the missing `<queries>` entry. |
| `device ... -> NOT_CONNECTED` | The watch is paired but not reachable. Check first whether it is plugged into USB — MTP mode takes it off the phone. |
| `NOT SENT` on the watch | GCM refused the handoff: not running, or watch unpaired. |
| Nothing either direction | App id mismatch. `manifest.xml` `id` vs `GarminBridge.APP_ID`, dashes stripped. Silent on both ends. |
| "Version skew" on the watch | `WatchProtocol.VERSION` != `Protocol.VERSION`. Rebuild the watch app. |
| Numbers wrong but present | The `EX_*` indices drifted between `WatchProtocol.kt` and `Protocol.mc`. |

### The bridge subscribes to devices; it must never snapshot them

`GarminBridge` registers for device events on every device from
[`ConnectIQ.getKnownDevices`][known] and attaches when one reports `CONNECTED`. **Do not replace
this with a `getConnectedDevices()` check.** It did exactly that originally — one snapshot at
`onSdkReady`, and if the list came back empty it logged "no devices" and gave up for the lifetime
of the process.

Empty is the *normal* case at startup, not an edge case: while the watch is plugged into USB for a
sideload, before Bluetooth settles after a reboot, any time the watch is out of range. And since
the bridge is created in `Application.onCreate`, "gave up for the lifetime of the process" meant
until a cold start — which closing the app does **not** do. So the transport would be permanently
deaf with no way to recover from the UI, presenting on the wrist as **No phone** no matter how
many times you restarted things.

`getKnownDevices` is the one that lists paired-but-not-connected devices, which is what makes a
subscription possible at all. `getConnectedDevices` cannot see the watch you are waiting for.

[known]: https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/

### A state push is the only confirmation

`Communications.transmit` reporting `onComplete` means Garmin Connect accepted the handoff. It
does **not** mean the phone applied anything — it may be uninstalled, killed, or simply not
listening. Two bugs came out of treating it as proof, and both are fixed:

- The startup `CMD_SYNC` toasted a green **synced** on accept, so a watch talking to nobody
  reported success and then sat on it. There is no success toast now: the arriving state redraws
  the screen, which is a better signal than a word.
- Worse, `START` toasted **logged** on accept, so a dropped set showed as a recorded one. That is
  precisely the failure `SessionDelegate` calls "the one failure that makes the watch worse than
  not having it at all", and it was live.

A mutating command now shows a yellow **sending** immediately, and only turns green when a state
push arrives — which the phone always sends after applying one, via `persistDraft`. If no push
follows within `CONFIRM_TIMEOUT_S`, the watch says **NOT CONFIRMED** rather than guessing: the
command may have been applied and the reply lost, and claiming either outcome would be a lie.
`onError` is still a definite **NOT SENT**, since GCM refusing the handoff is knowable.

### Staleness, and why a plain timeout would not do

`WatchLink.detachHost` publishes `IDLE` on a graceful teardown. It cannot cover a killed process,
a swipe from recents, a dropped Bluetooth link, a flat battery, or a reclaimed activity — a dying
process gets no last word out, which is why the fix has to be watch-side.

The obvious version — "no state for N seconds means stale" — is wrong. The phone publishes on
mutation, not on a clock, so a quiet stretch mid-set is completely normal and a timeout would cry
wolf constantly. A heartbeat is also wrong: per-second traffic over Bluetooth is exactly what the
protocol avoids.

So staleness is only ever concluded from an **unanswered probe**. `LiftPathApp`'s watchdog sends
`CMD_SYNC` after `PROBE_AFTER_S` of silence and marks `SessionModel.stale` if nothing comes back
within `PROBE_GRACE_S`. At most one probe a minute, only while the app is open, and only when
nothing has arrived. A stale screen draws **No phone** *ahead of* rest and the prescription — the
point is that no plausible-looking set is left on screen to log into a dead session.

Useful side effect: the probe's reply corrects rest-timer drift, since the watch counts down
locally between pushes.
| Watch invisible over USB — no drive, no portable device | `USB Mode = Garmin` on the watch. It binds to `grmnusb` as "Garmin USB GPS" and exposes no filesystem. Switch it to MTP. |
| `Invalid ... Method(msg as Message)` at build | `registerForPhoneAppMessages` hands its callback a `PhoneAppMessage`, not a `Message`. Only that one signature; `transmit` listeners still take `Message`. |
