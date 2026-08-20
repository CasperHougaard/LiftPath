import json, csv, re

with open('c:/Projects/fitness/.dataset_import/exercises.json', encoding='utf-8') as f:
    dataset = json.load(f)
by_id = {r['id']: r for r in dataset}

FAMILY_NOTES = {
    'ab_crunch': "Exhale on contraction. Don't pull on your neck.",
    'squat': "Knees track toes. Depth to parallel or below.",
    'biceps_curl': "Control the eccentric. No swinging.",
    'rotary_torso': "Rotate from the core, not the arms. Controlled tempo.",
    'split_squat': "Front knee tracks over the ankle. Upright torso.",
    'forearm_wrist': "Full range of motion. Light load, high control.",
    'row_horizontal': "Retract the scapula at the top. Avoid using momentum.",
    'calf_raise': "Full stretch at the bottom, full contraction at the top.",
    'triceps_extension': "Elbows stay pinned. Full extension at the bottom.",
    'overhead_press': "Brace the core. Keep the bar path straight overhead.",
    'decline_press': "Bar to lower chest. Control the descent.",
    'chest_fly': "Slight bend in the elbows. Stretch at the bottom, squeeze at the top.",
    'shrug': "Full elevation. Hold the peak for a second.",
    'deadlift': "Neutral spine. Push the floor away, don't pull.",
    'incline_press': "30-45 degree incline. Press to shoulder level.",
    'pull_up': "Full hang at the bottom. Chin over the bar.",
    'dips': "Slight forward lean for chest emphasis. Control the depth.",
    'lat_pulldown': "Pull to the upper chest. Squeeze the lats at the bottom.",
    'front_raise': "Slight bend in the elbows. Don't swing the weight.",
    'leg_curl': "Squeeze the hamstrings at the top. Control the negative.",
    'upright_row': "Lead with the elbows. Stop at shoulder height.",
    'lateral_raise': "Slight bend in the elbow. Lead with the elbows, not the hands.",
    'rdl': "Slight knee bend. Push the hips back, keep the bar close.",
    'close_grip_bench': "Hands just inside shoulder width. Elbows tucked.",
    'ab_wheel': "Brace hard. Don't let the hips sag.",
    'back_extension': "Hinge at the hips. Don't hyperextend the lower back at the top.",
    'good_morning': "Slight knee bend. Hinge at the hips, chest up.",
    'leg_press': "Feet shoulder-width. Don't lock out the knees.",
    'plank': "Neutral spine. Squeeze the glutes and brace the core.",
    'hip_abduction': "Push through the outer thigh. Controlled tempo.",
    'cardio_machine': "Maintain steady form. Build pace gradually.",
    'hip_adduction': "Squeeze the inner thighs together. Controlled tempo.",
    'hip_thrust': "Chin tucked. Full hip extension at the top.",
    'leg_extension': "Squeeze the quads at the top. Control the descent.",
    'farmers_walk': "Upright posture. Controlled, even steps.",
}

def clean_name(raw):
    n = raw.replace('в°', '°')  # fix mojibake "в°" -> "°"
    words = n.split(' ')
    out = []
    for w in words:
        if '-' in w:
            out.append('-'.join(p.capitalize() if p.isalpha() else p for p in w.split('-')))
        else:
            out.append(w.capitalize())
    name = ' '.join(out)
    name = name.replace('Ez ', 'EZ ').replace('Rdl', 'RDL')
    return name

def build_instructions(rec):
    steps = rec.get('instruction_steps', {}).get('en', [])
    if not steps:
        return None
    return '\n'.join(f"{i+1}. {s}" for i, s in enumerate(steps))

# --- Existing 84: patch in media fields, keep id/name/family unchanged ---
existing_patches = []
with open('c:/Projects/fitness/.dataset_import/existing_84_final.csv', encoding='utf-8') as f:
    for row in csv.DictReader(f):
        if not row['ds_id']:
            continue
        rec = by_id[row['ds_id']]
        existing_patches.append({
            'our_id': int(row['our_id']),
            'our_name': row['our_name'],
            'ds_id': row['ds_id'],
            'instructions': build_instructions(rec),
            'imageAssetPath': f"exercises/images/{row['our_id']}.jpg",
            'gifAssetPath': f"exercises/gifs/{row['our_id']}.gif",
            'src_image': rec['image'],
            'src_gif': rec['gif_url'],
        })

print(f"{len(existing_patches)} existing-84 patches")

# --- New exercises: assign ids starting at 200 ---
with open('c:/Projects/fitness/.dataset_import/new_selected.json', encoding='utf-8') as f:
    new_candidates = json.load(f)

new_final = []
next_id = 200
for c in new_candidates:
    rec = by_id[c['ds_id']]
    our_id = next_id
    next_id += 1
    new_final.append({
        'our_id': our_id,
        'name': clean_name(c['ds_name']),
        'ds_id': c['ds_id'],
        'family_id': c['family_id'],
        'pattern': c['pattern'],
        'region': c['region'],
        'tier': c['tier'],
        'equipment': c['equipment'],
        'angle': c['angle'],
        'grip': c['grip'],
        'laterality': c['laterality'],
        'primary': c['primary'],
        'secondary': c['secondary'],
        'note': FAMILY_NOTES.get(c['family_id'], ''),
        'instructions': build_instructions(rec),
        'imageAssetPath': f"exercises/images/{our_id}.jpg",
        'gifAssetPath': f"exercises/gifs/{our_id}.gif",
        'src_image': rec['image'],
        'src_gif': rec['gif_url'],
    })

print(f"{len(new_final)} new exercises assigned ids {new_final[0]['our_id']}-{new_final[-1]['our_id']}")

with open('c:/Projects/fitness/.dataset_import/existing_patches_final.json', 'w', encoding='utf-8') as f:
    json.dump(existing_patches, f, indent=1)
with open('c:/Projects/fitness/.dataset_import/new_final.json', 'w', encoding='utf-8') as f:
    json.dump(new_final, f, indent=1)

# Media fetch manifest (both sets)
manifest = []
for p in existing_patches:
    manifest.append((p['src_image'], p['imageAssetPath']))
    manifest.append((p['src_gif'], p['gifAssetPath']))
for n in new_final:
    manifest.append((n['src_image'], n['imageAssetPath']))
    manifest.append((n['src_gif'], n['gifAssetPath']))

with open('c:/Projects/fitness/.dataset_import/media_manifest.csv', 'w', encoding='utf-8', newline='') as f:
    w = csv.writer(f)
    w.writerow(['src_path', 'dest_relpath'])
    for src, dest in manifest:
        w.writerow([src, dest])

print(f"{len(manifest)} media files to fetch")

# new families needed beyond the original 40
new_family_ids = set(n['family_id'] for n in new_final)
print('families used by new exercises:', sorted(new_family_ids))
