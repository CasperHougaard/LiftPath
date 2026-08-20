TARGET = 'c:/Projects/fitness/app/src/main/java/com/liftpath/helpers/DefaultExercisesHelper.kt'

with open(TARGET, encoding='utf-8') as f:
    content = f.read()

def read_frag(name):
    with open(f'c:/Projects/fitness/.dataset_import/{name}', encoding='utf-8') as f:
        return f.read()

# --- 1. Insert new ExerciseFamily entries before DEFAULT_FAMILIES closing paren ---
anchor1 = '        ExerciseFamily("close_grip_bench",  "Close-Grip Press",          MovementPattern.PUSH_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.CHEST_MIDDLE)),\n    )\n'
assert content.count(anchor1) == 1, f"anchor1 count={content.count(anchor1)}"
replacement1 = (
    '        ExerciseFamily("close_grip_bench",  "Close-Grip Press",          MovementPattern.PUSH_HORIZONTAL,              BodyRegion.UPPER, listOf(TargetMuscle.TRICEPS_LONG, TargetMuscle.TRICEPS_LATERAL, TargetMuscle.CHEST_MIDDLE)),\n'
    + read_frag('gen_new_families.kt.txt')
    + '    )\n'
)
content = content.replace(anchor1, replacement1, 1)

# --- 2. Make illustrationRes nullable with default null ---
anchor2 = '        @DrawableRes val illustrationRes: Int\n    )'
assert content.count(anchor2) == 1, f"anchor2 count={content.count(anchor2)}"
content = content.replace(anchor2, '        @DrawableRes val illustrationRes: Int? = null\n    )', 1)

# --- 3. Insert new DEFAULT_EXERCISE_META_MAP entries before its closing paren ---
anchor3 = '        167 to "hip_thrust"\n    )'
assert content.count(anchor3) == 1, f"anchor3 count={content.count(anchor3)}"
replacement3 = (
    '        167 to "hip_thrust",\n'
    + read_frag('gen_meta_map.kt.txt').rstrip('\n').rstrip(',') + '\n'
    + '    )'
)
content = content.replace(anchor3, replacement3, 1)

# --- 4. Insert new DEFAULT_EXERCISE_FULL_META entries before its closing paren, then add
#         DefaultExerciseMedia class + DEFAULT_EXERCISE_MEDIA_MAP right after that map ---
anchor4 = '        167 to DefaultExerciseMeta("hip_thrust",        Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL, R.drawable.ex_glute_bridge_bodyweight)\n    )\n'
assert content.count(anchor4) == 1, f"anchor4 count={content.count(anchor4)}"
media_class_and_map = (
    '\n'
    '    // --- Per-exercise media metadata (imported from hasaneyldrm/exercises-dataset) ---\n'
    '\n'
    '    private data class DefaultExerciseMedia(\n'
    '        val instructions: String? = null,\n'
    '        val imageAssetPath: String? = null,\n'
    '        val gifAssetPath: String? = null,\n'
    '        val sourceDatasetId: String? = null\n'
    '    )\n'
    '\n'
    '    private val DEFAULT_EXERCISE_MEDIA_MAP: Map<Int, DefaultExerciseMedia> = mapOf(\n'
    + read_frag('gen_media_map.kt.txt').rstrip('\n').rstrip(',') + '\n'
    + '    )\n'
)
replacement4 = (
    '        167 to DefaultExerciseMeta("hip_thrust",        Equipment.BODYWEIGHT, null,                  Laterality.BILATERAL, R.drawable.ex_glute_bridge_bodyweight),\n'
    + read_frag('gen_full_meta.kt.txt').rstrip('\n').rstrip(',') + '\n'
    + '    )\n'
    + media_class_and_map
)
content = content.replace(anchor4, replacement4, 1)

# --- 5. Extend getPopularDefaults() to also join the media map ---
anchor5 = '''    fun getPopularDefaults(): List<ExerciseLibraryItem> {
        return rawDefaults().map { exercise ->
            val meta = DEFAULT_EXERCISE_FULL_META[exercise.id] ?: return@map exercise
            exercise.copy(
                familyId = meta.familyId,
                equipment = meta.equipment,
                angle = meta.angle,
                laterality = meta.laterality,
                illustrationRes = meta.illustrationRes
            )
        }
    }'''
assert content.count(anchor5) == 1, f"anchor5 count={content.count(anchor5)}"
replacement5 = '''    fun getPopularDefaults(): List<ExerciseLibraryItem> {
        return rawDefaults().map { exercise ->
            var result = exercise
            val meta = DEFAULT_EXERCISE_FULL_META[exercise.id]
            if (meta != null) {
                result = result.copy(
                    familyId = meta.familyId,
                    equipment = meta.equipment,
                    angle = meta.angle,
                    laterality = meta.laterality,
                    illustrationRes = meta.illustrationRes
                )
            }
            val media = DEFAULT_EXERCISE_MEDIA_MAP[exercise.id]
            if (media != null) {
                result = result.copy(
                    instructions = media.instructions,
                    imageAssetPath = media.imageAssetPath,
                    gifAssetPath = media.gifAssetPath,
                    sourceDatasetId = media.sourceDatasetId
                )
            }
            result
        }
    }'''
content = content.replace(anchor5, replacement5, 1)

# --- 6. Insert new ExerciseLibraryItem literals before rawDefaults() closing paren ---
anchor6 = '''            ExerciseLibraryItem(
                id = 167,
                name = "Glute Bridge (Bodyweight)",
                region = BodyRegion.LOWER,
                pattern = MovementPattern.HINGE,
                tier = Tier.TIER_3,
                primaryTargets = listOf(TargetMuscle.GLUTES),
                secondaryTargets = listOf(TargetMuscle.HAMSTRINGS),
                note = "Drive through heels. Squeeze glutes at top. Progress to single leg.",
                manualMechanics = Mechanics.ISOLATION,
                exerciseType = ExerciseType.BODYWEIGHT
            ),
        )'''
assert content.count(anchor6) == 1, f"anchor6 count={content.count(anchor6)}"
replacement6 = anchor6.replace(
    '            ),\n        )',
    '            ),\n' + read_frag('gen_raw_defaults.kt.txt').rstrip('\n') + '\n        )'
)
content = content.replace(anchor6, replacement6, 1)

# --- 7. Bump CATALOG_VERSION to 5 ---
anchor7 = 'const val CATALOG_VERSION = 4'
assert content.count(anchor7) == 1, f"anchor7 count={content.count(anchor7)}"
content = content.replace(anchor7, 'const val CATALOG_VERSION = 5', 1)

with open(TARGET, 'w', encoding='utf-8') as f:
    f.write(content)

print('Patched successfully.')
print('New file length (lines):', content.count('\n'))
