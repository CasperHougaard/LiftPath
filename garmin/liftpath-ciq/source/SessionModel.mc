import Toybox.Lang;
import Toybox.Time;

//! The watch's copy of the phone's session, plus the bits of state the phone deliberately does
//! not track.
//!
//! `cursor` and the two deltas are watch-local on purpose. If the phone owned "which exercise
//! is selected", every scroll would be a round trip over Bluetooth and the UI would lag behind
//! the button. Keeping them here is what lets the phone stay a dumb projection with no notion
//! of a session cursor at all.
class SessionModel {

    // Which value the up/down buttons currently adjust. Kg first: you decide the load before
    // the reps, and landing on a fresh exercise (resetDeltas) always starts here.
    enum {
        FIELD_KG = 0,
        FIELD_REPS = 1,
        FIELD_EXERCISE = 2
    }

    public var active as Boolean = false;
    public var restRemaining as Number = 0;
    public var exercises as Array = [];
    public var versionMismatch as Boolean = false;

    //! Epoch seconds of the last message from the phone; 0 means we have never heard from it.
    //!
    //! Silence on its own means nothing — the phone publishes on mutation, not on a clock, so a
    //! quiet minute mid-set is normal and a plain timeout would cry wolf constantly. Staleness is
    //! only ever concluded from an *unanswered probe*; see LiftPathApp's watchdog.
    public var lastHeardAt as Number = 0;

    //! Set by the watchdog when a probe went unanswered. While true the view must not show a
    //! prescription: numbers that look current are what invite logging into a dead session.
    public var stale as Boolean = false;

    public var cursor as Number = 0;
    public var field as Number = FIELD_KG;

    // Offsets from whatever the phone suggested, reset when the cursor moves — a new exercise
    // has its own baseline and carrying an old ±10 kg across would be actively dangerous.
    private var _repsDelta as Number = 0;
    private var _kgDelta as Float = 0.0;

    // Used when the phone has no plan target to offer, i.e. a manually added exercise.
    private const DEFAULT_REPS = 8;
    //! Fallback only. The real step arrives per exercise at Protocol.EX_KG_STEP; see kgStep().
    private const KG_STEP = 2.5;

    function initialize() {
    }

    //! Any well-formed message from the phone, including one we go on to reject for a version
    //! skew — a skewed message still proves something is listening, which is the fact this
    //! records. Session semantics are applyState's business.
    function markHeard() as Void {
        lastHeardAt = Time.now().value();
        stale = false;
    }

    //! Replaces everything the phone owns. Returns false on a version skew, in which case the
    //! previous state is left untouched — stale numbers are safer than misread ones.
    function applyState(data as Dictionary) as Boolean {
        var version = data[Protocol.KEY_VERSION];
        if (!(version instanceof Number) || version != Protocol.VERSION) {
            versionMismatch = true;
            return false;
        }
        versionMismatch = false;

        var activeFlag = data[Protocol.KEY_ACTIVE];
        active = (activeFlag instanceof Number) && activeFlag == 1;

        var rest = data[Protocol.KEY_REST];
        restRemaining = (rest instanceof Number) ? rest : 0;

        // Captured before exercises is overwritten: this is how the cursor survives a reorder
        // between two pushes (superset formation, warmup/cooldown pinning both reorder the
        // phone's list in place), not just a resize. A pure index clamp would otherwise silently
        // repoint the cursor at whatever now sits at that index — a different exercise than the
        // one being looked at, with no sign anything changed.
        var previousId = exerciseId();

        var list = data[Protocol.KEY_EXERCISES];
        exercises = (list instanceof Array) ? list : [];

        var found = -1;
        if (previousId >= 0) {
            for (var i = 0; i < exercises.size(); i += 1) {
                if (numberAt(exercises[i] as Array, Protocol.EX_ID, -1) == previousId) {
                    found = i;
                    break;
                }
            }
        }

        if (found >= 0) {
            cursor = found;
        } else if (cursor >= exercises.size()) {
            // The exercise under the cursor was actually removed on the phone (not just moved).
            cursor = exercises.size() > 0 ? exercises.size() - 1 : 0;
            resetDeltas();
        }
        return true;
    }

    function current() as Array or Null {
        if (cursor < 0 || cursor >= exercises.size()) {
            return null;
        }
        return exercises[cursor] as Array;
    }

    function exerciseName() as String {
        var ex = current();
        if (ex == null) {
            return "";
        }
        var name = ex[Protocol.EX_NAME];
        return (name instanceof String) ? name : "?";
    }

    function exerciseId() as Number {
        var ex = current();
        if (ex == null) {
            return -1;
        }
        return numberAt(ex, Protocol.EX_ID, -1);
    }

    function setsDone() as Number {
        var ex = current();
        return ex == null ? 0 : numberAt(ex, Protocol.EX_SETS_DONE, 0);
    }

    //! 0 means open-ended — a manually added exercise with no plan behind it.
    function setsTarget() as Number {
        var ex = current();
        return ex == null ? 0 : numberAt(ex, Protocol.EX_SETS_TARGET, 0);
    }

    function isBodyweight() as Boolean {
        var ex = current();
        return ex != null && numberAt(ex, Protocol.EX_BODYWEIGHT, 0) == 1;
    }

    function reps() as Number {
        var ex = current();
        if (ex == null) {
            return 0;
        }
        var base = numberAt(ex, Protocol.EX_REPS_TARGET, 0);
        if (base <= 0) {
            base = DEFAULT_REPS;
        }
        var value = base + _repsDelta;
        return value < 1 ? 1 : value;
    }

    function kg() as Float {
        var ex = current();
        if (ex == null) {
            return 0.0;
        }
        var value = floatAt(ex, Protocol.EX_KG, 0.0) + _kgDelta;
        return value < 0.0 ? 0.0 : value;
    }

    //! Kilograms one press of +/- moves, for the exercise under the cursor.
    //!
    //! The phone resolves this from the exercise's equipment and sends it per exercise, so the
    //! wrist steps by 2.5 on a barbell and 5 on a cable stack rather than by one hardcoded
    //! number. Falls back to KG_STEP when the value is missing (an older phone build) or zero
    //! (equipment with no weight ladder, e.g. bands) — a 0 step would freeze the control.
    function kgStep() as Float {
        var ex = current();
        if (ex == null) {
            return KG_STEP;
        }
        var step = floatAt(ex, Protocol.EX_KG_STEP, KG_STEP);
        return step > 0.0 ? step : KG_STEP;
    }

    //! delta is +1 or -1; what it means depends on the active field.
    function adjust(delta as Number) as Void {
        if (field == FIELD_REPS) {
            _repsDelta += delta;
        } else if (field == FIELD_KG) {
            _kgDelta += delta * kgStep();
        } else {
            moveCursor(delta);
        }
    }

    function cycleField() as Void {
        field = (field + 1) % 3;
    }

    function fieldLabel() as String {
        if (field == FIELD_REPS) {
            return "reps";
        } else if (field == FIELD_KG) {
            return "kg";
        }
        return "exercise";
    }

    //! Called by SessionDelegate right after sending a log, before resetDeltas clears the
    //! numbers that decided it. If that log fills the current exercise's target, moves the
    //! cursor to the next incomplete one so the common straight-through-the-plan case never
    //! needs a manual MENU-cycle to advance.
    //!
    //! Deliberately not run from applyState / on every incoming push — see SessionDelegate.mc
    //! for why. This is a one-shot, locally-triggered guess: if the log is later NOT CONFIRMED,
    //! the cursor has already moved and will not roll back on its own.
    function advanceIfComplete() as Void {
        var target = setsTarget();
        if (target <= 0 || setsDone() + 1 < target) {
            return;
        }

        var n = exercises.size();
        for (var step = 1; step <= n; step += 1) {
            var idx = (cursor + step) % n;
            var row = exercises[idx] as Array;
            var rowTarget = numberAt(row, Protocol.EX_SETS_TARGET, 0);
            var rowDone = numberAt(row, Protocol.EX_SETS_DONE, 0);
            if (rowTarget <= 0 || rowDone < rowTarget) {
                cursor = idx;
                resetDeltas();
                return;
            }
        }
        // Every exercise is complete (or there is only the one) — nothing to advance to.
    }

    private function moveCursor(delta as Number) as Void {
        if (exercises.size() == 0) {
            return;
        }
        var next = cursor + delta;
        if (next < 0) {
            next = exercises.size() - 1;
        } else if (next >= exercises.size()) {
            next = 0;
        }
        cursor = next;
        resetDeltas();
    }

    //! Called after a successful log too: the phone's next projection carries the load we just
    //! used as the new suggestion, so holding the delta would double-count it.
    //!
    //! Also resets `field` to kg. "Starting fresh" means starting the same way every time — kg
    //! first, per the workflow this app is built around — whether that fresh start is a new
    //! exercise or just the next set of the same one.
    function resetDeltas() as Void {
        _repsDelta = 0;
        _kgDelta = 0.0;
        field = FIELD_KG;
    }

    private function numberAt(row as Array, index as Number, fallback as Number) as Number {
        if (index >= row.size()) {
            return fallback;
        }
        var value = row[index];
        return (value instanceof Number) ? value : fallback;
    }

    private function floatAt(row as Array, index as Number, fallback as Float) as Float {
        if (index >= row.size()) {
            return fallback;
        }
        var value = row[index];
        if (value instanceof Float) {
            return value;
        }
        if (value instanceof Number) {
            return value.toFloat();
        }
        return fallback;
    }
}
