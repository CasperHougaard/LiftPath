import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! Drawn straight onto the Dc rather than through Rez layouts. That is not laziness: the
//! content is four lines of text whose position depends on which of three states we are in,
//! and expressing that as three layouts plus visibility toggles is more code, not less.
class SessionView extends WatchUi.View {

    private var _model as SessionModel;
    private var _toast as String or Null = null;

    function initialize(model as SessionModel) {
        View.initialize();
        _model = model;
    }

    //! Transient confirmation, shown until the next state push replaces it. Deliberately
    //! covers the numbers: after pressing START the only thing worth knowing is whether it
    //! got through.
    function toast(message as String) as Void {
        _toast = message;
        WatchUi.requestUpdate();
    }

    function clearToast() as Void {
        _toast = null;
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

        line(dc, cx, h * 20 / 100, Graphics.FONT_XTINY, Graphics.COLOR_LT_GRAY, _model.exerciseName());
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
