import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

//! LiftPath on the wrist: see the next set's prescription, adjust it, log it.
//!
//! The phone owns the session (see `WatchLink.kt`); this app holds a projection of it and
//! sends commands back. It cannot start or finish a workout — that stays on the phone, partly
//! because those decisions want a real screen and partly because Connect IQ has no way for the
//! phone to launch us, so the watch is never guaranteed to be running.
class LiftPathApp extends Application.AppBase {

    //! Watchdog cadence. Coarse on purpose: it only has to notice a lost command or an
    //! unanswered probe, and both tolerances are measured in seconds.
    private const WATCHDOG_MS = 2000;

    //! How long the phone may stay quiet before we ask whether it is still there.
    private const PROBE_AFTER_S = 60;

    //! How long a probe may go unanswered before the phone is treated as gone.
    private const PROBE_GRACE_S = 6;

    private var _model as SessionModel;
    private var _view as SessionView or Null;

    private var _watchdog as Timer.Timer or Null = null;
    private var _probeSentAt as Number = 0;
    private var _probeOutstanding as Boolean = false;

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
        stopWatchdog();
        Communications.registerForPhoneAppMessages(null);
    }

    private function stopWatchdog() as Void {
        var timer = _watchdog;
        if (timer != null) {
            timer.stop();
            _watchdog = null;
        }
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        var view = new SessionView(_model);
        _view = view;

        // Ask for a push rather than waiting for the phone to happen to change something.
        // No confirmation toast: the arriving state changes the whole screen, which is a better
        // signal than a green word, and a "synced" that only means "Garmin Connect took it" is
        // exactly the false positive this app used to show when nothing was listening.
        probe();
        startWatchdog();

        return [view, new SessionDelegate(_model, view)];
    }

    // PhoneAppMessage, not Message: registerForPhoneAppMessages hands its callback a
    // PhoneAppMessage, and the type checker rejects the wider Message. Communications.Message
    // is still the right type for transmit() listeners, so this is one signature, not a rename.
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data;
        if (!(data instanceof Dictionary)) {
            return;
        }

        // Recorded before applyState, and regardless of what it makes of the contents: this is
        // the one place that learns the phone is alive, and a version-skewed message proves that
        // just as well as a good one.
        _model.markHeard();
        _probeOutstanding = false;

        var accepted = _model.applyState(data as Dictionary);
        var view = _view;
        if (view != null) {
            // The phone acted, so anything in flight has landed. Otherwise a fresh state simply
            // supersedes whatever confirmation was on screen.
            if (view.hasPending()) {
                view.confirmPending();
            } else {
                view.clearToast();
            }
        }

        if (accepted) {
            syncTicker();
        }
        WatchUi.requestUpdate();
    }

    //! Ask the phone for a fresh projection. Also the liveness test: what matters is not whether
    //! Garmin Connect accepts this, but whether a state push comes back.
    private function probe() as Void {
        _probeSentAt = Time.now().value();
        _probeOutstanding = true;
        Communications.transmit(
            { Protocol.KEY_COMMAND => Protocol.CMD_SYNC },
            null,
            new CommandListener(_view)
        );
    }

    private function startWatchdog() as Void {
        if (_watchdog != null) {
            return;
        }
        var timer = new Timer.Timer();
        timer.start(method(:onWatchdog), WATCHDOG_MS, true);
        _watchdog = timer;
    }

    //! Decides two things the rest of the app cannot: whether an in-flight command was lost, and
    //! whether the phone has stopped answering.
    //!
    //! Silence alone is never treated as a fault. The phone publishes on mutation, so a quiet
    //! stretch during a set is completely normal; concluding staleness from a plain timeout would
    //! false-alarm constantly. Only an unanswered probe counts.
    function onWatchdog() as Void {
        var now = Time.now().value();
        var view = _view;
        var dirty = false;

        if (view != null) {
            var wasPending = view.hasPending();
            view.expirePending(now);
            dirty = wasPending && !view.hasPending();
        }

        // An outstanding probe has had its grace period and nothing came back.
        if (_probeOutstanding && now - _probeSentAt >= PROBE_GRACE_S) {
            _probeOutstanding = false;
            if (!_model.stale) {
                _model.stale = true;
                dirty = true;
            }
        }

        // Re-probe only after a quiet stretch, and never while one is already outstanding.
        // _probeSentAt rate-limits this on its own, including the never-heard-anything case.
        if (!_probeOutstanding && now - _probeSentAt >= PROBE_AFTER_S) {
            probe();
        }

        if (dirty) {
            WatchUi.requestUpdate();
        }
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
