import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

//! LiftPath on the wrist: see the next set's prescription, adjust it, log it.
//!
//! The phone owns the session (see `WatchLink.kt`); this app holds a projection of it and
//! sends commands back. It cannot start or finish a workout — that stays on the phone, partly
//! because those decisions want a real screen and partly because Connect IQ has no way for the
//! phone to launch us, so the watch is never guaranteed to be running.
class LiftPathApp extends Application.AppBase {

    private var _model as SessionModel;
    private var _view as SessionView or Null;

    //! Local rest countdown. The phone publishes state only when the session changes, so it
    //! will never send us a tick per second — nor should it, over Bluetooth. We take the
    //! remaining seconds once and count down ourselves; the phone stays the timer of record and
    //! any correction arrives with the next real state push.
    private var _ticker as Timer.Timer or Null = null;

    function initialize() {
        AppBase.initialize();
        _model = new SessionModel();
    }

    function onStart(state as Dictionary or Null) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    function onStop(state as Dictionary or Null) as Void {
        stopTicker();
        Communications.registerForPhoneAppMessages(null);
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        var view = new SessionView(_model);
        _view = view;

        // Ask for a push rather than waiting for the phone to happen to change something.
        // This doubles as the connectivity check: if the screen stays on "No session" while a
        // workout is open on the phone, the transport is at fault, not the session.
        Communications.transmit(
            { Protocol.KEY_COMMAND => Protocol.CMD_SYNC },
            null,
            new CommandListener(view, "synced")
        );

        return [view, new SessionDelegate(_model, view)];
    }

    function onPhoneMessage(msg as Communications.Message) as Void {
        var data = msg.data;
        if (!(data instanceof Dictionary)) {
            return;
        }

        var accepted = _model.applyState(data as Dictionary);
        var view = _view;
        if (view != null) {
            // A fresh state supersedes whatever confirmation was on screen.
            view.clearToast();
        }

        if (accepted) {
            syncTicker();
        }
        WatchUi.requestUpdate();
    }

    //! The ticker runs only while resting, so an idle app costs nothing.
    private function syncTicker() as Void {
        if (_model.restRemaining > 0) {
            if (_ticker == null) {
                var ticker = new Timer.Timer();
                ticker.start(method(:onTick), 1000, true);
                _ticker = ticker;
            }
        } else {
            stopTicker();
        }
    }

    private function stopTicker() as Void {
        var ticker = _ticker;
        if (ticker != null) {
            ticker.stop();
            _ticker = null;
        }
    }

    function onTick() as Void {
        if (_model.restRemaining <= 0) {
            stopTicker();
            return;
        }

        _model.restRemaining -= 1;
        if (_model.restRemaining == 0) {
            stopTicker();
            buzz();
        }
        WatchUi.requestUpdate();
    }

    //! The actual reason to wear this on your wrist: rest ending without having to look at a
    //! phone. Guarded because `vibrate` is absent on devices without a motor and calling it
    //! would be a hard error rather than a no-op.
    private function buzz() as Void {
        if (Attention has :vibrate) {
            Attention.vibrate([
                new Attention.VibeProfile(100, 400),
                new Attention.VibeProfile(0, 200),
                new Attention.VibeProfile(100, 400)
            ]);
        }
    }
}
