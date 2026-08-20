import json, csv
from collections import Counter

with open('c:/Projects/fitness/.dataset_import/exercises.json', encoding='utf-8') as f:
    dataset = json.load(f)
by_id = {r['id']: r for r in dataset}

used_ds_ids = set()
with open('c:/Projects/fitness/.dataset_import/existing_84_final.csv', encoding='utf-8') as f:
    r = csv.DictReader(f)
    for row in r:
        if row['ds_id']:
            used_ds_ids.add(row['ds_id'])
print('already used ds ids:', len(used_ds_ids))

EQUIPMENT_MAP = {
    'dumbbell': 'DUMBBELL', 'barbell': 'BARBELL', 'olympic barbell': 'BARBELL',
    'ez barbell': 'EZ_BAR', 'cable': 'CABLE', 'leverage machine': 'MACHINE',
    'sled machine': 'MACHINE', 'smith machine': 'SMITH_MACHINE',
    'body weight': 'BODYWEIGHT', 'assisted': 'BODYWEIGHT', 'kettlebell': 'KETTLEBELL',
    'resistance band': 'BANDS', 'band': 'BANDS',
    'medicine ball': 'OTHER', 'stability ball': 'OTHER', 'bosu ball': 'OTHER',
    'roller': 'OTHER', 'wheel roller': 'OTHER', 'rope': 'OTHER', 'hammer': 'OTHER',
    'trap bar': 'OTHER', 'weighted': 'OTHER', 'elliptical machine': 'OTHER',
    'stationary bike': 'OTHER', 'upper body ergometer': 'OTHER',
    'stepmill machine': 'OTHER', 'skierg machine': 'OTHER', 'tire': 'OTHER',
}

BODYPART_REGION = {
    'chest': 'UPPER', 'back': 'UPPER', 'shoulders': 'UPPER', 'upper arms': 'UPPER',
    'lower arms': 'UPPER', 'waist': 'CORE', 'upper legs': 'LOWER', 'lower legs': 'LOWER',
    'neck': 'UPPER', 'cardio': 'FULL',
}

def target_to_muscle(target, name_lower):
    t = target.lower()
    if t == 'pectorals':
        if 'incline' in name_lower: return 'CHEST_UPPER'
        if 'decline' in name_lower: return 'CHEST_LOWER'
        return 'CHEST_MIDDLE'
    if t == 'lats': return 'LATS'
    if t == 'traps': return 'TRAPS_UPPER'
    if t == 'upper back': return 'TRAPS_MID'
    if t == 'spine': return 'LOWER_BACK'
    if t == 'delts':
        if 'lateral' in name_lower or 'side raise' in name_lower: return 'DELT_SIDE'
        if 'rear' in name_lower or 'reverse' in name_lower: return 'DELT_REAR'
        return 'DELT_FRONT'
    if t == 'levator scapulae': return 'TRAPS_UPPER'
    if t == 'biceps': return 'BICEPS'
    if t == 'triceps':
        if 'overhead' in name_lower or 'french' in name_lower or 'behind head' in name_lower: return 'TRICEPS_LONG'
        return 'TRICEPS_LATERAL'
    if t == 'forearms': return 'FOREARMS'
    if t == 'abs': return 'ABS'
    if t == 'serratus anterior': return 'CHEST_UPPER'
    if t == 'quads': return 'QUADS'
    if t == 'hamstrings': return 'HAMSTRINGS'
    if t == 'glutes': return 'GLUTES'
    if t == 'adductors': return 'ADDUCTORS'
    if t == 'abductors': return 'ABDUCTORS'
    if t == 'calves': return 'CALVES'
    if t == 'cardiovascular system': return None
    return None

FAMILY_RULES = [
    ('close_grip_bench', 'PUSH_HORIZONTAL', 'UPPER', ['close-grip bench', 'close grip bench'], [], None),
    ('incline_press', 'PUSH_HORIZONTAL', 'UPPER', ['incline'], ['fly', 'flye', 'curl', 'row', 'raise', 'shrug', 'extension'], 'PRESS_LIKE'),
    ('decline_press', 'PUSH_HORIZONTAL', 'UPPER', ['decline'], ['fly', 'flye', 'curl', 'row', 'raise', 'shrug', 'extension'], 'PRESS_LIKE'),
    ('chest_fly', 'ISOLATION_SHOULDER_FLEXION', 'UPPER', ['fly', 'flye', 'crossover', 'cross-over', 'cross over'], ['leg'], None),
    ('dips', 'PUSH_VERTICAL', 'UPPER', [' dip', 'dips'], ['hip'], None),
    ('overhead_press', 'PUSH_VERTICAL', 'UPPER', ['overhead press', 'shoulder press', 'military press'], ['incline', 'decline'], None),
    ('pull_up', 'PULL_VERTICAL', 'UPPER', ['pull up', 'pull-up', 'pullup', 'chin up', 'chin-up', 'chinup'], [], None),
    ('lat_pulldown', 'PULL_VERTICAL', 'UPPER', ['pulldown', 'pull-down', 'pull down'], [], None),
    ('face_pull', 'ISOLATION_SHOULDER_EXTENSION', 'UPPER', ['face pull'], [], None),
    ('reverse_fly', 'ISOLATION_SHOULDER_EXTENSION', 'UPPER', ['reverse fly', 'rear delt fly', 'rear fly', 'reverse flye'], [], None),
    ('lateral_raise', 'ISOLATION_SHOULDER_ABDUCTION', 'UPPER', ['lateral raise', 'side raise'], ['front', 'rear', 'reverse'], None),
    ('front_raise', 'ISOLATION_SHOULDER_FLEXION', 'UPPER', ['front raise'], [], None),
    ('upright_row', 'PULL_VERTICAL', 'UPPER', ['upright row'], [], None),
    ('shrug', 'PULL_VERTICAL', 'UPPER', ['shrug'], [], None),
    ('row_horizontal', 'PULL_HORIZONTAL', 'UPPER', ['row'], ['upright'], None),
    ('leg_press', 'SQUAT', 'LOWER', ['leg press'], [], None),
    ('split_squat', 'LUNGE', 'LOWER', ['split squat', 'lunge', 'step up', 'step-up', 'stepup', 'bulgarian'], [], None),
    ('squat', 'SQUAT', 'LOWER', ['squat'], [], None),
    ('rdl', 'HINGE', 'LOWER', ['romanian deadlift', 'rdl', 'stiff leg deadlift', 'stiff-leg deadlift', 'single leg deadlift'], [], None),
    ('good_morning', 'HINGE', 'LOWER', ['good morning'], [], None),
    ('deadlift', 'HINGE', 'LOWER', ['deadlift'], [], None),
    ('hip_thrust', 'HINGE', 'LOWER', ['hip thrust', 'glute bridge'], [], None),
    ('back_extension', 'HINGE', 'LOWER', ['back extension', 'hyperextension'], [], None),
    ('leg_curl', 'ISOLATION_KNEE_FLEXION', 'LOWER', ['leg curl', 'nordic curl', 'hamstring curl'], [], None),
    ('leg_extension', 'ISOLATION_KNEE_EXTENSION', 'LOWER', ['leg extension'], [], None),
    ('calf_raise', 'ISOLATION_PLANTAR_FLEXION', 'LOWER', ['calf raise', 'heel raise', 'heel drop', 'calf press'], [], None),
    ('hip_adduction', 'OTHER', 'LOWER', ['hip adduction', 'adductor'], [], None),
    ('hip_abduction', 'OTHER', 'LOWER', ['hip abduction', 'abductor'], [], None),
    ('cable_leg_raises', 'OTHER', 'LOWER', ['leg raise'], [], 'CABLE_ONLY'),
    ('ab_crunch', 'CORE_FLEXION', 'CORE', ['crunch', 'sit-up', 'sit up', 'situp', 'leg raise', 'v-up', 'v up', 'curl-up', 'curl up'], [], None),
    ('plank', 'CORE_STABILITY', 'CORE', ['plank'], [], None),
    ('cable_woodchopper', 'CORE_STABILITY', 'CORE', ['woodchop', 'wood chop', 'chop'], [], None),
    ('rotary_torso', 'CORE_FLEXION', 'CORE', ['rotary torso', 'torso rotation', 'russian twist', 'twist'], [], None),
    ('ab_wheel', 'CORE_STABILITY', 'CORE', ['ab wheel', 'wheel rollout', 'wheel rollerout', 'rollerout', 'rollout'], [], None),
    ('farmers_walk', 'CARRY', 'FULL', ['farmer', 'carry', 'suitcase walk', 'yoke walk'], [], None),
    ('kettlebell_swing', 'HINGE', 'FULL', ['kettlebell swing'], [], None),
    ('neck_flexion', 'OTHER', 'UPPER', ['neck flexion', 'neck curl'], [], None),
    ('neck_extension', 'OTHER', 'UPPER', ['neck extension', 'neck raise'], [], None),
    ('neck_lateral', 'OTHER', 'UPPER', ['neck side', 'neck lateral'], [], None),
    ('forearm_wrist', 'OTHER', 'UPPER', ['wrist curl', 'wrist extension', 'wrist roller', 'forearm', 'finger curl'], [], None),
    ('triceps_extension', 'ISOLATION_ELBOW_EXTENSION', 'UPPER', ['triceps extension', 'skullcrusher', 'skull crusher', 'triceps pushdown', 'triceps kickback', 'tricep kickback', 'french press', 'triceps dip'], [], None),
    ('glute_machine', 'OTHER', 'LOWER', ['glute kickback', 'kickback'], [], None),
    ('biceps_curl', 'ISOLATION_ELBOW_FLEXION', 'UPPER', ['curl'], ['leg', 'wrist', 'back extension'], 'CURL_ARM_ONLY'),
    ('cardio_machine', 'OTHER', 'FULL', [], [], 'CARDIO_EQUIPMENT'),
]

# Explicit dataset-id overrides for keyword-ambiguous cases where the dataset's own `target`
# field disambiguates better than name keywords alone (e.g. "dumbbell kickback" with no
# "triceps"/"glute" word in the name at all -- resolved by checking target == triceps/glutes).
def kickback_family_override(name_lower, target_lower):
    if 'kickback' not in name_lower:
        return None
    if target_lower == 'triceps':
        return ('triceps_extension', 'ISOLATION_ELBOW_EXTENSION', 'UPPER')
    if target_lower == 'glutes':
        return ('glute_machine', 'OTHER', 'LOWER')
    return None

# Names that are joke/mistranslated entries or passive stretches (belong to the dedicated
# Stretch feature, not the strength-training library) -- excluded outright regardless of family match.
NAME_EXCLUDE = {
    'potty squat',
    'assisted side lying adductor stretch',
    'neck side stretch',
}

def matches_equipment_filter(equip, name_lower, filt):
    if filt is None: return True
    if filt == 'PRESS_LIKE': return 'press' in name_lower or 'bench' in name_lower
    if filt == 'CABLE_ONLY': return equip == 'cable'
    if filt == 'CURL_ARM_ONLY': return True
    if filt == 'CARDIO_EQUIPMENT': return equip in ('elliptical machine', 'stationary bike', 'upper body ergometer', 'stepmill machine', 'skierg machine')
    return True

def assign_family(name, equip, target):
    n = name.lower()
    if n in NAME_EXCLUDE:
        return None, None, None
    override = kickback_family_override(n, (target or '').lower())
    if override:
        return override
    for family_id, pattern, region, any_of, none_of, eqfilt in FAMILY_RULES:
        if any_of and not any(kw in n for kw in any_of):
            continue
        if any(kw in n for kw in none_of):
            continue
        if not matches_equipment_filter(equip, n, eqfilt):
            continue
        return family_id, pattern, region
    return None, None, None

TIER1_FAMILIES = {'deadlift', 'squat', 'chest_press', 'overhead_press', 'row_horizontal', 'rdl', 'hip_thrust', 'pull_up', 'good_morning'}
TIER1_EQUIP = {'BARBELL', 'SMITH_MACHINE', 'BODYWEIGHT'}
TIER2_FAMILIES = {'split_squat', 'leg_press', 'lat_pulldown', 'incline_press', 'decline_press', 'dips', 'farmers_walk', 'kettlebell_swing', 'close_grip_bench', 'shrug', 'upright_row', 'back_extension'}

def assign_tier(family_id, equip):
    if family_id in TIER1_FAMILIES and equip in TIER1_EQUIP:
        return 'TIER_1'
    if family_id in TIER2_FAMILIES:
        return 'TIER_2'
    if family_id in TIER1_FAMILIES:
        return 'TIER_2'
    return 'TIER_3'

def assign_angle(name):
    n = name.lower()
    if 'incline' in n: return 'INCLINE'
    if 'decline' in n: return 'DECLINE'
    return None

def assign_grip(name):
    n = name.lower()
    if 'wide grip' in n or 'wide-grip' in n: return 'WIDE'
    if 'close grip' in n or 'close-grip' in n: return 'CLOSE'
    if 'underhand' in n or 'reverse grip' in n or 'supinated' in n: return 'UNDERHAND'
    if 'neutral grip' in n or 'hammer' in n or 'neutral' in n: return 'NEUTRAL'
    if 'overhand' in n or 'pronated' in n: return 'OVERHAND'
    return None

def assign_laterality(name):
    n = name.lower()
    if any(k in n for k in ['single leg', 'single arm', 'one arm', 'one leg', 'alternate', 'unilateral']):
        return 'UNILATERAL'
    return 'BILATERAL'

results = []
for rec in dataset:
    if rec['id'] in used_ds_ids:
        continue
    equip_raw = rec['equipment']
    equip_enum = EQUIPMENT_MAP.get(equip_raw)
    if equip_enum is None:
        continue
    family_id, pattern, region_from_family = assign_family(rec['name'], equip_raw, rec['target'])
    if family_id is None:
        continue
    region = BODYPART_REGION.get(rec['body_part'], region_from_family or 'FULL')
    tier = assign_tier(family_id, equip_enum)
    name_lower = rec['name'].lower()
    primary = target_to_muscle(rec['target'], name_lower)
    secondary = []
    for sm in rec.get('secondary_muscles', []):
        m = target_to_muscle(sm, name_lower)
        if m and m != primary and m not in secondary:
            secondary.append(m)
    results.append({
        'ds_id': rec['id'], 'ds_name': rec['name'], 'ds_equipment': equip_raw,
        'family_id': family_id, 'pattern': pattern, 'region': region, 'tier': tier,
        'equipment': equip_enum, 'angle': assign_angle(rec['name']), 'grip': assign_grip(rec['name']),
        'laterality': assign_laterality(rec['name']),
        'primary': primary, 'secondary': secondary,
        'body_part': rec['body_part'], 'target': rec['target'],
        'image': rec['image'], 'gif_url': rec['gif_url'],
    })

print(f"{len(results)} candidates got a confident family assignment (out of {len(dataset)-len(used_ds_ids)} available)")

fam_counts = Counter(r['family_id'] for r in results)
for fam, cnt in fam_counts.most_common():
    print(f"  {fam}: {cnt}")

with open('c:/Projects/fitness/.dataset_import/new_candidates_all.json', 'w', encoding='utf-8') as f:
    json.dump(results, f, indent=1)
