# One-off generator for DefaultExercisesHelper.kt
items = []

def E(id, name, region, pattern, tier, prim, sec, note, mm=None):
    items.append((id, name, region, pattern, tier, prim, sec, note, mm))

E(1, "Deadlift (Barbell)", "LOWER", "HINGE", "TIER_1",
  ["HAMSTRINGS", "GLUTES", "LOWER_BACK"], ["TRAPS_UPPER", "FOREARMS", "QUADS"],
  "Brace core, neutral spine. Push floor away, don't pull.")
E(2, "Back Squat (Barbell)", "LOWER", "SQUAT", "TIER_1",
  ["QUADS", "GLUTES", "ADDUCTORS"], ["LOWER_BACK", "ABS"],
  "Knees track toes. Depth to parallel or below.")
E(4, "Bicep Curl (Dumbbell)", "UPPER", "ISOLATION_ELBOW_FLEXION", "TIER_3",
  ["BICEPS"], ["FOREARMS"], "Control eccentric. No swinging.")
E(5, "Triceps Pushdown (Cable)", "UPPER", "ISOLATION_ELBOW_EXTENSION", "TIER_3",
  ["TRICEPS_LATERAL"], [], "Elbows pinned. Full extension at bottom.")
E(7, "Bench Press (Barbell)", "UPPER", "PUSH_HORIZONTAL", "TIER_1",
  ["CHEST_MIDDLE", "DELT_FRONT", "TRICEPS_LATERAL"], ["CHEST_UPPER"],
  "Retract scapula. Slight arch. Bar to lower chest.", "COMPOUND")
E(8, "Split Squat (Barbell)", "LOWER", "LUNGE", "TIER_2",
  ["QUADS", "GLUTES"], ["ADDUCTORS", "CALVES"], "Front knee over ankle. Upright torso.")
E(9, "Calf Raise (Machine)", "LOWER", "ISOLATION_PLANTAR_FLEXION", "TIER_3",
  ["CALVES"], [], "Full range: deep stretch to peak contraction.")
E(10, "Decline Bench Press (Barbell)", "UPPER", "PUSH_HORIZONTAL", "TIER_2",
  ["CHEST_LOWER", "TRICEPS_LATERAL"], ["DELT_FRONT"], "Bar to lower chest. Control descent.")
E(11, "Incline Dumbbell Press", "UPPER", "PUSH_HORIZONTAL", "TIER_2",
  ["CHEST_UPPER", "DELT_FRONT"], ["TRICEPS_LATERAL"],
  "30-45\u00b0 incline. Dumbbells to shoulder level.")
E(12, "Seated Cable Row", "UPPER", "PULL_HORIZONTAL", "TIER_1",
  ["LATS", "TRAPS_MID"], ["BICEPS", "DELT_REAR"],
  "Retract scapula at peak. No excessive torso swing.")
E(13, "Triceps Extension (Single Arm)", "UPPER", "ISOLATION_ELBOW_EXTENSION", "TIER_3",
  ["TRICEPS_LONG"], [], "Upper arm stationary. Full extension.")
E(14, "Machine Shoulder Press", "UPPER", "PUSH_VERTICAL", "TIER_2",
  ["DELT_FRONT", "DELT_SIDE"], ["TRICEPS_LATERAL", "TRAPS_UPPER"],
  "Back against pad. Full ROM.")
E(15, "Dips (Bodyweight)", "UPPER", "PUSH_VERTICAL", "TIER_2",
  ["CHEST_LOWER", "TRICEPS_LATERAL"], ["DELT_FRONT"],
  "Slight forward lean for chest. Control depth.")
E(16, "Abdominal Crunch (Machine)", "CORE", "CORE_FLEXION", "TIER_3",
  ["ABS"], [], "Exhale on contraction. Don't pull neck.")
E(17, "Bench Press (Paused)", "UPPER", "PUSH_HORIZONTAL", "TIER_1",
  ["CHEST_MIDDLE", "TRICEPS_LATERAL"], ["DELT_FRONT"],
  "Pause 1-2 sec on chest. Eliminates stretch reflex.")
E(18, "Seated Leg Curl (Machine)", "LOWER", "ISOLATION_KNEE_FLEXION", "TIER_3",
  ["HAMSTRINGS"], [], "Squeeze hamstrings. Control negative.")
E(100, "Overhead Press (Barbell)", "UPPER", "PUSH_VERTICAL", "TIER_1",
  ["DELT_FRONT", "TRICEPS_LATERAL"], ["TRAPS_UPPER", "ABS"], "Brace core. Bar path straight.")
E(101, "Pull Up (Bodyweight)", "UPPER", "PULL_VERTICAL", "TIER_1",
  ["LATS", "BICEPS"], ["TRAPS_MID", "FOREARMS"], "Chin over bar. Full hang at bottom.")
E(102, "Chin Up", "UPPER", "PULL_VERTICAL", "TIER_2",
  ["LATS", "BICEPS"], ["FOREARMS"], "Palms face you. Squeeze at top.")
E(103, "Romanian Deadlift (Barbell)", "LOWER", "HINGE", "TIER_1",
  ["HAMSTRINGS", "GLUTES"], ["LOWER_BACK", "FOREARMS"], "Slight knee bend. Push hips back.")
E(104, "Leg Press", "LOWER", "SQUAT", "TIER_2",
  ["QUADS", "GLUTES"], ["ADDUCTORS"], "Feet shoulder-width. Don't lock knees.")
E(105, "Bulgarian Split Squat (Dumbbell)", "LOWER", "LUNGE", "TIER_2",
  ["QUADS", "GLUTES"], ["ADDUCTORS", "ABS"], "Narrow stance. Front knee tracks toe.")
E(106, "Lat Pulldown (Wide Grip)", "UPPER", "PULL_VERTICAL", "TIER_2",
  ["LATS", "TRAPS_MID"], ["BICEPS", "DELT_REAR"], "Pull to upper chest. Squeeze lats.")
E(107, "Dumbbell Row", "UPPER", "PULL_HORIZONTAL", "TIER_2",
  ["LATS", "TRAPS_MID"], ["BICEPS", "FOREARMS"], "Hinge at hip. Pull elbow past torso.")
E(108, "Face Pull (Cable)", "UPPER", "ISOLATION_SHOULDER_EXTENSION", "TIER_3",
  ["DELT_REAR", "TRAPS_MID"], ["BICEPS", "TRAPS_UPPER"],
  "External rotation at end. Thumbs back.")
E(109, "Lateral Raise (Dumbbell)", "UPPER", "ISOLATION_SHOULDER_ABDUCTION", "TIER_3",
  ["DELT_SIDE"], ["TRAPS_UPPER"], "Slight bend in elbow. Lead with elbows.")
E(110, "Leg Extension (Machine)", "LOWER", "ISOLATION_KNEE_EXTENSION", "TIER_3",
  ["QUADS"], [], "Squeeze quads at top. Control descent.")
E(111, "Hip Thrust (Barbell)", "LOWER", "HINGE", "TIER_1",
  ["GLUTES"], ["HAMSTRINGS", "ABS"], "Chin tucked. Full hip extension at top.")
E(112, "Skullcrusher (EZ Bar)", "UPPER", "ISOLATION_ELBOW_EXTENSION", "TIER_3",
  ["TRICEPS_LONG", "TRICEPS_LATERAL"], [],
  "Upper arms ~45\u00b0. Lower to forehead/ear level.")
E(113, "Hammer Curl (Dumbbell)", "UPPER", "ISOLATION_ELBOW_FLEXION", "TIER_3",
  ["BICEPS", "FOREARMS"], [], "Neutral grip. Control throughout.")
E(114, "Front Squat (Barbell)", "LOWER", "SQUAT", "TIER_1",
  ["QUADS", "ABS"], ["GLUTES"], "Elbows high. Upright torso.")
E(115, "Walking Lunges", "LOWER", "LUNGE", "TIER_2",
  ["QUADS", "GLUTES"], ["CALVES", "ABS"], "90\u00b0 angles. Step length for balance.")
E(116, "Incline Dumbbell Fly", "UPPER", "ISOLATION_SHOULDER_FLEXION", "TIER_3",
  ["CHEST_UPPER"], ["DELT_FRONT"], "Slight bend in elbows. Stretch at bottom.")
E(117, "Pec Deck / Machine Fly", "UPPER", "PUSH_HORIZONTAL", "TIER_3",
  ["CHEST_MIDDLE"], ["DELT_FRONT"], "Controlled stretch and squeeze.")
E(118, "Dumbbell Shoulder Press (Seated)", "UPPER", "PUSH_VERTICAL", "TIER_2",
  ["DELT_FRONT", "DELT_SIDE"], ["TRICEPS_LATERAL"], "Back support. Full ROM.")
E(119, "Reverse Fly (Dumbbell)", "UPPER", "ISOLATION_SHOULDER_EXTENSION", "TIER_3",
  ["DELT_REAR"], ["TRAPS_MID"], "Hinge forward. Thumbs point back.")
E(120, "Hanging Leg Raise", "CORE", "CORE_FLEXION", "TIER_3",
  ["ABS"], ["FOREARMS"], "Control swing. Exhale as legs rise.")
E(121, "Plank", "CORE", "CORE_STABILITY", "TIER_3",
  ["ABS", "OBLIQUES"], [], "Neutral spine. Squeeze glutes.")
E(122, "Cable Woodchopper", "CORE", "CORE_STABILITY", "TIER_3",
  ["OBLIQUES"], ["ABS"], "Rotate from core. Control both phases.")
E(123, "Sumo Deadlift", "LOWER", "HINGE", "TIER_1",
  ["HAMSTRINGS", "GLUTES", "QUADS"], ["ADDUCTORS", "LOWER_BACK"],
  "Wide stance. Vertical torso. Push knees out.")
E(124, "Barbell Shrug", "UPPER", "PULL_VERTICAL", "TIER_3",
  ["TRAPS_UPPER"], ["FOREARMS"], "Full elevation. Hold peak 1 sec.")
E(125, "Farmer's Walk", "FULL", "CARRY", "TIER_2",
  ["FOREARMS", "TRAPS_UPPER"], ["CALVES"], "Upright posture. Controlled steps.")
E(126, "Glute Bridge (Barbell)", "LOWER", "HINGE", "TIER_2",
  ["GLUTES"], ["HAMSTRINGS"], "Drive through heels. Squeeze glutes at top.")
E(127, "Goblet Squat", "LOWER", "SQUAT", "TIER_2",
  ["QUADS", "GLUTES"], ["ABS"], "Elbows inside knees. Push knees out.")
E(128, "Barbell Row (Pendlay)", "UPPER", "PULL_HORIZONTAL", "TIER_1",
  ["LATS", "TRAPS_MID", "LOWER_BACK"], ["BICEPS", "DELT_REAR"],
  "Back parallel. Pull to lower chest.")
E(129, "Cable Crossover", "UPPER", "ISOLATION_SHOULDER_FLEXION", "TIER_3",
  ["CHEST_LOWER", "CHEST_MIDDLE"], [], "Slight bend. Squeeze pecs at center.")
E(130, "Preacher Curl (EZ Bar)", "UPPER", "ISOLATION_ELBOW_FLEXION", "TIER_3",
  ["BICEPS"], [], "Upper arms on pad. Full stretch at bottom.")
E(131, "Push Up", "UPPER", "PUSH_HORIZONTAL", "TIER_3",
  ["CHEST_MIDDLE", "TRICEPS_LATERAL"], ["ABS", "DELT_FRONT"],
  "Core tight. Full lockout at top.")
E(132, "Rotary Torso (Machine)", "CORE", "CORE_FLEXION", "TIER_3",
  ["OBLIQUES"], ["ABS"], "Rotate from hips. Controlled twist.", "ISOLATION")
E(133, "Incline press (Machine)", "UPPER", "PUSH_HORIZONTAL", "TIER_2",
  ["CHEST_UPPER"], ["CHEST_MIDDLE", "TRICEPS_LONG", "TRICEPS_LATERAL"],
  "Back flat on pad. Full ROM.", "ISOLATION")
E(134, "Hip Adduction", "LOWER", "OTHER", "TIER_3",
  ["ADDUCTORS"], [], "Squeeze thighs together. Controlled tempo.", "ISOLATION")
E(135, "Hip Abduction", "LOWER", "OTHER", "TIER_3",
  ["ABDUCTORS"], [], "Push knees out. Squeeze glutes.", "ISOLATION")
E(136, "Romanian Deadlift (Dumbbell)", "LOWER", "HINGE", "TIER_1",
  ["HAMSTRINGS", "GLUTES"], ["LOWER_BACK", "FOREARMS"],
  "Hinge at hips. Dumbbells close to legs.", "COMPOUND")
E(137, "Side Plank", "CORE", "CORE_STABILITY", "TIER_3",
  ["GLUTES", "OBLIQUES"], ["LOWER_BACK", "ABS"],
  "Stack feet/legs. Don't let hips sag.", "ISOLATION")
E(138, "Eccentric Heel Drop", "LOWER", "OTHER", "TIER_3",
  ["CALVES"], [], "Slow 3-sec lower. Support on way up.", "ISOLATION")
E(139, "Chest Press (Machine Wide)", "UPPER", "PUSH_HORIZONTAL", "TIER_2",
  ["CHEST_UPPER", "CHEST_MIDDLE"], ["TRICEPS_LONG", "TRICEPS_LATERAL"],
  "Full stretch. Don't lock elbows.", "COMPOUND")
E(140, "Chest Press (Machine)", "UPPER", "PUSH_HORIZONTAL", "TIER_1",
  ["CHEST_MIDDLE"],
  ["CHEST_UPPER", "CHEST_LOWER", "DELT_FRONT", "DELT_SIDE", "TRICEPS_LONG", "TRICEPS_LATERAL"],
  "Back flat. Full ROM.", "COMPOUND")
E(141, "Row (Machine)", "UPPER", "PULL_HORIZONTAL", "TIER_1",
  ["LATS", "TRAPS_MID", "TRAPS_UPPER", "DELT_REAR"],
  ["LOWER_BACK", "BICEPS", "FOREARMS"], "Retract scapula. Squeeze at peak.", "COMPOUND")
E(142, "Prone Leg Curl", "LOWER", "ISOLATION_KNEE_FLEXION", "TIER_2",
  ["HAMSTRINGS"], [], "Hips down. Squeeze hamstrings.", "ISOLATION")
E(143, "Side Raises (Dumbbell)", "UPPER", "ISOLATION_SHOULDER_ABDUCTION", "TIER_3",
  ["DELT_SIDE"], [], "Slight bend in elbow. Lead with elbows.", "ISOLATION")
E(144, "Incline Barbell Press", "UPPER", "PUSH_HORIZONTAL", "TIER_2",
  ["CHEST_UPPER"], ["CHEST_MIDDLE", "TRICEPS_LONG", "TRICEPS_LATERAL"],
  "30-45\u00b0 incline. Bar to upper chest.", "ISOLATION")
E(145, "Glute (Machine)", "LOWER", "OTHER", "TIER_3",
  ["GLUTES"], [], "Squeeze glutes at peak. Control negative.", "ISOLATION")
E(146, "Hack Squat", "FULL", "SQUAT", "TIER_1",
  ["QUADS", "GLUTES"], ["HIPFLEXORS", "ABS", "HAMSTRINGS"],
  "Back flat on pad. Feet shoulder-width.", "COMPOUND")
E(147, "Cable Straight Leg Raises", "LOWER", "OTHER", "TIER_3",
  ["HIPFLEXORS"], ["ABS"],
  "Stand tall; avoid leaning back. Lift knee to parallel, pause 1s, lower slowly.", "ISOLATION")
E(148, "Cable Straight Back Kicks", "LOWER", "OTHER", "TIER_3",
  ["GLUTES"], ["HAMSTRINGS"],
  "Keep spine neutral; squeeze glute at top. Lead with heel.", "ISOLATION")
E(149, "Low Row (Machine)", "UPPER", "PULL_HORIZONTAL", "TIER_1",
  ["LATS"], ["TRAPS_MID", "TRAPS_UPPER", "DELT_REAR", "BICEPS"],
  "Chest supported; pull elbows back. Squeeze lats.", "COMPOUND")

BR = "BodyRegion"
TM = "TargetMuscle"

def map_list(xs, enum_name):
    if not xs:
        return "emptyList()"
    return "listOf(" + ", ".join(f"{enum_name}.{x}" for x in xs) + ")"

lines = [
    "package com.liftpath.helpers",
    "",
    "import com.liftpath.models.BodyRegion",
    "import com.liftpath.models.ExerciseLibraryItem",
    "import com.liftpath.models.Mechanics",
    "import com.liftpath.models.MovementPattern",
    "import com.liftpath.models.TargetMuscle",
    "import com.liftpath.models.Tier",
    "",
    "object DefaultExercisesHelper {",
    "",
    "    const val CATALOG_VERSION = 1",
    "",
    "    fun getPopularDefaults(): List<ExerciseLibraryItem> {",
    "        return listOf(",
]
for tup in items:
    id_, name, region, pattern, tier, prim, sec, note, mm = tup
    name_esc = name.replace("\\", "\\\\").replace('"', '\\"')
    note_esc = note.replace("\\", "\\\\").replace('"', '\\"')
    mm_line = ""
    if mm:
        mm_line = f",\n                manualMechanics = Mechanics.{mm}"
    block = (
        f"            ExerciseLibraryItem(\n"
        f"                id = {id_},\n"
        f'                name = "{name_esc}",\n'
        f"                region = {BR}.{region},\n"
        f"                pattern = MovementPattern.{pattern},\n"
        f"                tier = Tier.{tier},\n"
        f"                primaryTargets = {map_list(prim, TM)},\n"
        f"                secondaryTargets = {map_list(sec, TM)},\n"
        f'                note = "{note_esc}"{mm_line}\n'
        f"            ),"
    )
    lines.append(block)

lines.append("        )")
lines.append("    }")
lines.append("}")

out_path = "app/src/main/java/com/liftpath/helpers/DefaultExercisesHelper.kt"
with open(out_path, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("written", out_path, "count", len(items))
