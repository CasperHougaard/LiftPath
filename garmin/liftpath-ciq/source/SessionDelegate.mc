import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

//! Input mapping. The Fenix 8 gives three inputs you can hit without looking at the watch —
//! START, UP, DOWN — plus MENU as a hold. That is not enough for "pick exercise, set reps, set
//! load, log", so one input has to be a mode switch: MENU cycles what UP/DOWN adjust.
class SessionDelegate extends WatchUi.BehaviorDelegate {

    private var _model as SessionModel;
    private var _view as SessionView;

    function initialize(model as SessionModel, view as SessionView) {
        BehaviorDelegate.initialize();
        _model = model;
        _view = view;
    }

    //! START: log the set exactly as shown, or skip the rest timer if one is running. There is
    //! nothing to log mid-rest, and skipping is the only thing that button is ever wanted for
    //! at that point.
    function onSelect() as Boolean {
        if (_model.restRemaining > 0) {
            send({ Protocol.KEY_COMMAND => Protocol.CMD_REST_STOP }, "skipped");
            _model.restRemaining = 0;
            return true;
        }

        var id = _model.exerciseId();
        if (id < 0) {
            return true;
        }

        send({
            Protocol.KEY_COMMAND => Protocol.CMD_LOG_SET,
            Protocol.KEY_EXERCISE_ID => id,
            Protocol.KEY_REPS => _model.reps(),
            Protocol.KEY_KG => _model.kg()
        }, "logged");

        // Before resetDeltas: advanceIfComplete reads the same pre-log numbers that decided
        // what was just sent, to judge whether this set fills the exercise's target.
        _model.advanceIfComplete();

        // The phone's next projection carries the load we just used as the new suggestion, so
        // keeping the offset would apply it twice.
        _model.resetDeltas();
        return true;
    }

    function onNextPage() as Boolean {
        return step(-1);
    }

    function onPreviousPage() as Boolean {
        return step(1);
    }

    function onMenu() as Boolean {
        _model.cycleField();
        _view.clearToast();
        WatchUi.requestUpdate();
        return true;
    }

    private function step(delta as Number) as Boolean {
        _model.adjust(delta);
        _view.clearToast();
        WatchUi.requestUpdate();
        return true;
    }

    //! Mark it in flight *before* transmitting, so the screen stops looking loggable the instant
    //! the button goes down rather than a round trip later.
    private function send(payload as Dictionary, confirmation as String) as Void {
        _view.pending(confirmation);
        Communications.transmit(payload, null, new CommandListener(_view));
    }
}

//! Listener for a command that mutates the session.
//!
//! It reports failure only. Success is not knowable here: `onComplete` means Garmin Connect
//! accepted the handoff, and nothing more — the phone may be uninstalled, killed, or simply not
//! listening. The confirmation is the state push the phone sends after applying the change, so
//! `SessionView.confirmPending` owns the green message and this class never shows one.
class CommandListener extends Communications.ConnectionListener {

    // Nullable because the startup probe is sent from the app, which holds the view as an
    // optional. A probe with nothing to draw on is still worth sending: it is what sets
    // SessionModel.stale, and that lives on the model rather than the view.
    private var _view as SessionView or Null;

    function initialize(view as SessionView or Null) {
        ConnectionListener.initialize();
        _view = view;
    }

    function onComplete() as Void {
        // Accepted for delivery. Says nothing about whether it was applied, so nothing to do:
        // the pending indicator stays up until a state push confirms it or it times out.
    }

    //! Garmin Connect Mobile refused the handoff, so the phone never saw this. Say so loudly —
    //! silently dropping a logged set is the one failure that makes the watch worse than not
    //! having it at all.
    function onError() as Void {
        var view = _view;
        if (view != null) {
            view.failPending("NOT SENT");
        }
    }
}
