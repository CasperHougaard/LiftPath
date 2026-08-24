import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

//! Drawn straight onto the Dc rather than through Rez layouts. That is not laziness: the
//! content is four lines of text whose position depends on which of three states we are in,
//! and expressing that as three layouts plus visibility toggles is more code, not less.
class SessionView extends WatchUi.View {

    //! Long enough to read a confirmation mid-set without looking twice, short enough that a
    //! stale one cannot be mistaken for the current state.
    private const TOAST_MS = 2500;

    //! How long a sent-but-unconfirmed command may sit before we call it lost.
    private const CONFIRM_TIMEOUT_S = 8;

    private var _model as SessionModel;
    private var _toast as String or Null = null;
    private var _toastTimer as Timer.Timer or Null = null;

    // A command handed to Garmin Connect but not yet confirmed by a state push, and when it was
    // sent. Carries the success wording so confirmPending can show it once the phone answers.
    private var _pending as String or Null = null;
    private var _pendingAt as Number = 0;

    function initialize(model as SessionModel) {
        View.initialize();
        _model = model;
    }

    //! Transient confirmation. Deliberately covers the numbers: after pressing START the only
    //! thing worth knowing is whether it got through.
    //!
    //! It expires on a timer rather than waiting for the next state push. Waiting was wrong: a
    //! confirmation that never clears reads as a live status light, and the one case where no
    //! state push follows is exactly the case where nothing on the phone is listening — so the
    //! screen would sit on a green success for a sync that was never answered.
    function toast(message as String) as Void {
        _toast = message;
        var existing = _toastTimer;
        if (existing != null) {
            existing.stop();
        }
        var timer = new Timer.Timer();
        timer.start(method(:onToastExpired), TOAST_MS, false);
        _toastTimer = timer;
        WatchUi.requestUpdate();
    }

    function onToastExpired() as Void {
        _toastTimer = null;
        // Redraw whatever the model actually says now, which for an unanswered sync is
        // "No session" — the honest answer.
        clearToast();
        WatchUi.requestUpdate();
    }

    function clearToast() as Void {
        _toast = null;
        var timer = _toastTimer;
        if (timer != null) {
            timer.stop();
            _toastTimer = null;
        }
    }

    //! A mutating command has been sent but not confirmed.
    //!
    //! Nothing may claim success here. `Communications.transmit` reporting onComplete means only
    //! that Garmin Connect accepted the handoff — not that the phone applied it. Treating that as
    //! a logged set is how a dropped set shows up as a green confirmation, which is worse than
    //! showing no watch at all.
    function pending(successLabel as String) as Void {
        clearToast();
        _pending = successLabel;
        _pendingAt = Time.now().value();
        WatchUi.requestUpdate();
    }

    //! Called when a state push arrives: the phone acted, so the command landed.
    function confirmPending() as Void {
        var label = _pending;
        if (label == null) {
            return;
        }
        _pending = null;
        toast(label);
    }

    //! No state push followed the command. It may have been applied and the reply lost, so the
    //! wording says what we actually know rather than claiming a failure we cannot prove.
    function expirePending(now as Number) as Void {
        if (_pending == null || now - _pendingAt < CONFIRM_TIMEOUT_S) {
            return;
        }
        _pending = null;
        toast("NOT CONFIRMED");
    }

    function failPending(message as String) as Void {
        _pending = null;
        toast(message);
    }

    function hasPending() as Boolean {
        return _pending != null;
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var cx = dc.getWidth() / 2;
        var h = dc.getHeight();

        if (_model.versionMismatch) {
            line(dc, cx, h * 42 / 100, Graphics.FONT_SMALL, Graphics.COLOR_RED, "Version skew");
            line(dc, cx, h * 58 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, "rebuild watch app");
            return;
        }

        if (_toast != null) {
            line(dc, cx, h / 2, Graphics.FONT_MEDIUM, Graphics.COLOR_GREEN, _toast);
            return;
        }

        // Deliberately not green and deliberately covering the numbers: in flight is not done,
        // and the screen should not look loggable again until we know the last one landed.
        //
        // Bound to a local: the type checker cannot carry a null-check on a member across the
        // call, so reading _pending directly here is a PolyType warning.
        var inFlight = _pending;
        if (inFlight != null) {
            line(dc, cx, h * 42 / 100, Graphics.FONT_MEDIUM, Graphics.COLOR_YELLOW, "sending");
            line(dc, cx, h * 60 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, inFlight);
            return;
        }

        // Ahead of rest and prescription on purpose. An unanswered phone must not leave a
        // plausible-looking set on screen for you to log into a session that has ended.
        if (_model.stale) {
            line(dc, cx, h * 38 / 100, Graphics.FONT_SMALL, Graphics.COLOR_RED, "No phone");
            line(dc, cx, h * 56 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, "phone not answering");
            line(dc, cx, h * 70 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, "check Bluetooth");
            return;
        }

        // Resting is the one time the watch has something more useful to say than the
        // prescription, so it takes the whole screen.
        if (_model.restRemaining > 0) {
            line(dc, cx, h * 30 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, "REST");
            line(dc, cx, h * 48 / 100, Graphics.FONT_NUMBER_MEDIUM, Graphics.COLOR_WHITE, clock(_model.restRemaining));
            line(dc, cx, h * 74 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, "START = skip");
            return;
        }

        if (_model.current() == null) {
            line(dc, cx, h * 42 / 100, Graphics.FONT_SMALL, Graphics.COLOR_WHITE, "No session");
            line(dc, cx, h * 58 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, "start it on the phone");
            return;
        }

        // Blue while picking an exercise, matching the "+/- exercise" accent below — a plain
        // grey name here was easy enough to miss that it read as "the exercise never changes".
        var nameColor = (_model.field == SessionModel.FIELD_EXERCISE) ? Graphics.COLOR_BLUE : Graphics.COLOR_LT_GRAY;
        line(dc, cx, h * 20 / 100, Graphics.FONT_XTINY, nameColor, _model.exerciseName());
        // FONT_LARGE, not FONT_NUMBER_*: the number fonts carry digits and separators only, so
        // the "x" in "8 x 60.0" would come out blank.
        line(dc, cx, h * 43 / 100, Graphics.FONT_LARGE, Graphics.COLOR_WHITE, prescription());
        line(dc, cx, h * 65 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, setCounter());
        line(dc, cx, h * 81 / 100, Graphics.FONT_XTINY, Graphics.COLOR_BLUE, "+/- " + _model.fieldLabel());
    }

    private function prescription() as String {
        var reps = _model.reps();
        var load = _model.kg();
        if (_model.isBodyweight()) {
            // Unloaded bodyweight work has no number worth showing, and "0.0 kg" reads as an
            // error rather than as "just your body".
            if (load == 0.0) {
                return reps.toString() + " reps";
            }
            return reps.toString() + " +" + load.format("%.1f");
        }
        return reps.toString() + " x " + load.format("%.1f");
    }

    private function setCounter() as String {
        var next = _model.setsDone() + 1;
        var target = _model.setsTarget();
        if (target > 0) {
            return "set " + next.toString() + " of " + target.toString();
        }
        // No plan behind this exercise, so there is no total to count towards.
        return "set " + next.toString();
    }

    private function clock(seconds as Number) as String {
        var mins = seconds / 60;
        var secs = seconds % 60;
        return mins.toString() + ":" + secs.format("%02d");
    }

    private function line(
        dc as Dc,
        cx as Number,
        y as Number,
        font as Graphics.FontType,
        colour as Number,
        text as String
    ) as Void {
        dc.setColor(colour, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, y, font, text, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
