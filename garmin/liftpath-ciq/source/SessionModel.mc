import Toybox.Lang;

//! The watch's copy of the phone's session, plus the bits of state the phone deliberately does
//! not track.
//!
//! `cursor` and the two deltas are watch-local on purpose. If the phone owned "which exercise
//! is selected", every scroll would be a round trip over Bluetooth and the UI would lag behind
//! the button. Keeping them here is what lets the phone stay a dumb projection with no notion
//! of a session cursor at all.
class SessionModel {

    // Which value the up/down buttons currently adjust.
    enum {
        FIELD_REPS = 0,
        FIELD_KG = 1,
        FIELD_EXERCISE = 2
    }

    public var active as Boolean = false;
    public var restRemaining as Number = 0;
    public var exercises as Array = [];
    public var versionMismatch as Boolean = false;

    public var cursor as Number = 0;
    public var field as Number = FIELD_REPS;

    // Offsets from whatever the phone suggested, reset when the cursor moves — a new exercise
    // has its own baseline and carrying an old ±10 kg across would be actively dangerous.
    private var _repsDelta as Number = 0;
    private var _kgDelta as Float = 0.0;

    // Used when the phone has no plan target to offer, i.e. a manually added exercise.
    private const DEFAULT_REPS = 8;
    private const KG_STEP = 2.5;

    function initialize() {
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

        var list = data[Protocol.KEY_EXERCISES];
        exercises = (list instanceof Array) ? list : [];

        // The exercise under the cursor may have been deleted on the phone while we were
        // looking at it.
        if (cursor >= exercises.size()) {
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

    //! delta is +1 or -1; what it means depends on the active field.
    function adjust(delta as Number) as Void {
        if (field == FIELD_REPS) {
            _repsDelta += delta;
        } else if (field == FIELD_KG) {
            _kgDelta += delta * KG_STEP;
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
    function resetDeltas() as Void {
        _repsDelta = 0;
        _kgDelta = 0.0;
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
