import Toybox.Lang;

//! Mirror of `app/src/main/java/com/liftpath/watch/WatchProtocol.kt`.
//!
//! There is no shared module between the two repos halves — this file and that one are the
//! contract, duplicated. The `EX_*` indices are positional offsets into each exercise array;
//! if they drift apart, fields shift silently by one rather than failing. Change both, and
//! bump VERSION when you do: the watch refuses a state whose version it does not recognise,
//! which turns a skew into a visible message instead of wrong numbers on your wrist.
module Protocol {

    const VERSION = 1;

    // --- state: phone -> watch ---
    const KEY_VERSION = "v";
    const KEY_ACTIVE = "a";
    const KEY_REST = "r";
    const KEY_EXERCISES = "x";

    // Indices within one exercise array. Must match WatchProtocol.EX_* exactly.
    const EX_ID = 0;
    const EX_NAME = 1;
    const EX_SETS_DONE = 2;
    const EX_SETS_TARGET = 3;
    const EX_REPS_TARGET = 4;
    const EX_KG = 5;
    const EX_BODYWEIGHT = 6;

    // --- commands: watch -> phone ---
    const KEY_COMMAND = "c";
    const CMD_LOG_SET = "log";
    const CMD_REST_START = "rest";
    const CMD_REST_STOP = "rstop";
    const CMD_SYNC = "sync";

    const KEY_EXERCISE_ID = "id";
    const KEY_REPS = "rp";
    const KEY_KG = "kg";
    const KEY_RPE = "rpe";
    const KEY_SECONDS = "s";
}
