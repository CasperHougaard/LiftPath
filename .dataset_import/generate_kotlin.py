import json

with open('c:/Projects/fitness/.dataset_import/existing_patches_final.json', encoding='utf-8') as f:
    existing_patches = json.load(f)
with open('c:/Projects/fitness/.dataset_import/new_final.json', encoding='utf-8') as f:
    new_final = json.load(f)

def kt_str(s):
    if s is None:
        return 'null'
    escaped = s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('$', '\\$')
    return f'"{escaped}"'

# --- 1. New ExerciseFamily entries (forearm_wrist, cardio_machine) ---
new_families_kt = (
    '        ExerciseFamily("forearm_wrist",    "Forearm / Wrist",           MovementPattern.OTHER,                        BodyRegion.UPPER, listOf(TargetMuscle.FOREARMS)),\n'
    '        ExerciseFamily("cardio_machine",    "Cardio Machine",            MovementPattern.OTHER,                        BodyRegion.FULL,  emptyList()),\n'
)
with open('c:/Projects/fitness/.dataset_import/gen_new_families.kt.txt', 'w', encoding='utf-8') as f:
    f.write(new_families_kt)

# --- 2. New DEFAULT_EXERCISE_META_MAP entries (id -> familyId) ---
lines = []
for n in new_final:
    lines.append(f'        {n["our_id"]} to "{n["family_id"]}",')
with open('c:/Projects/fitness/.dataset_import/gen_meta_map.kt.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

# --- 3. New DEFAULT_EXERCISE_FULL_META entries (named args, illustrationRes omitted) ---
lines = []
for n in new_final:
    angle = f'ExerciseAngle.{n["angle"]}' if n['angle'] else 'null'
    lat = f'Laterality.{n["laterality"]}' if n['laterality'] else 'null'
    lines.append(
        f'        {n["our_id"]} to DefaultExerciseMeta(familyId = "{n["family_id"]}", '
        f'equipment = Equipment.{n["equipment"]}, angle = {angle}, laterality = {lat}),'
    )
with open('c:/Projects/fitness/.dataset_import/gen_full_meta.kt.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

# --- 4. DEFAULT_EXERCISE_MEDIA_MAP entries (existing 75 patches + 222 new) ---
lines = []
for p in existing_patches:
    lines.append(
        f'        {p["our_id"]} to DefaultExerciseMedia(\n'
        f'            instructions = {kt_str(p["instructions"])},\n'
        f'            imageAssetPath = {kt_str(p["imageAssetPath"])},\n'
        f'            gifAssetPath = {kt_str(p["gifAssetPath"])},\n'
        f'            sourceDatasetId = {kt_str(p["ds_id"])}\n'
        f'        ),'
    )
for n in new_final:
    lines.append(
        f'        {n["our_id"]} to DefaultExerciseMedia(\n'
        f'            instructions = {kt_str(n["instructions"])},\n'
        f'            imageAssetPath = {kt_str(n["imageAssetPath"])},\n'
        f'            gifAssetPath = {kt_str(n["gifAssetPath"])},\n'
        f'            sourceDatasetId = {kt_str(n["ds_id"])}\n'
        f'        ),'
    )
with open('c:/Projects/fitness/.dataset_import/gen_media_map.kt.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

# --- 5. New ExerciseLibraryItem(...) literals for rawDefaults() ---
def targets_kt(names):
    if not names:
        return 'emptyList()'
    return 'listOf(' + ', '.join(f'TargetMuscle.{t}' for t in names) + ')'

lines = []
for n in new_final:
    primary = [n['primary']] if n['primary'] else []
    secondary = n['secondary'] or []
    extra = []
    if n['equipment'] == 'BODYWEIGHT':
        extra.append('                exerciseType = ExerciseType.BODYWEIGHT,')
    if n['family_id'] == 'plank':
        extra.append('                targetMetric = ExerciseTargetMetric.TIME,')
    extra_kt = ('\n' + '\n'.join(extra)) if extra else ''
    note = n['note'] or ''
    lines.append(
        f'            ExerciseLibraryItem(\n'
        f'                id = {n["our_id"]},\n'
        f'                name = {kt_str(n["name"])},\n'
        f'                region = BodyRegion.{n["region"]},\n'
        f'                pattern = MovementPattern.{n["pattern"]},\n'
        f'                tier = Tier.{n["tier"]},\n'
        f'                primaryTargets = {targets_kt(primary)},\n'
        f'                secondaryTargets = {targets_kt(secondary)},\n'
        f'                note = {kt_str(note)}{"," if extra else ""}{extra_kt}\n'
        f'            ),'
    )
with open('c:/Projects/fitness/.dataset_import/gen_raw_defaults.kt.txt', 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

print('Generated all Kotlin fragments.')
print('families:', new_families_kt.count('ExerciseFamily'))
print('meta_map lines:', len(new_final))
print('full_meta lines:', len(new_final))
print('media_map entries:', len(existing_patches) + len(new_final))
print('raw_defaults entries:', len(new_final))
