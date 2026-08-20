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

    private function send(payload as Dictionary, confirmation as String) as Void {
        Communications.transmit(payload, null, new CommandListener(_view, confirmation));
    }
}

class CommandListener extends Communications.ConnectionListener {

    private var _view as SessionView;
    private var _confirmation as String;

    function initialize(view as SessionView, confirmation as String) {
        ConnectionListener.initialize();
        _view = view;
        _confirmation = confirmation;
    }

    function onComplete() as Void {
        _view.toast(_confirmation);
    }

    //! Garmin Connect Mobile refused the handoff, so the phone never saw this. Say so loudly —
    //! silently dropping a logged set is the one failure that makes the watch worse than not
    //! having it at all.
    function onError() as Void {
        _view.toast("NOT SENT");
    }
}
