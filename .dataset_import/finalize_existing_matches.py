import json

with open('c:/Projects/fitness/.dataset_import/exercises.json', encoding='utf-8') as f:
    dataset = json.load(f)
by_id = {r['id']: r for r in dataset}

# base matches from the automated pass (score, ds_id) -- id -> ds_id
with open('c:/Projects/fitness/.dataset_import/match_results2.txt', encoding='utf-8') as f:
    auto = {}
    for line in f:
        parts = line.rstrip('\n').split('|')
        auto[parts[0]] = parts[3]  # our_id -> ds_id

# Manual overrides after semantic review (our_id -> ds_id or None to reject/unmatch)
OVERRIDES = {
    '2': '0043',    # barbell full squat
    '8': '2810',    # barbell split squat v. 2
    '15': '0251',   # chest dip
    '100': '1456',  # barbell standing close grip military press (closest standing barbell OHP)
    '104': '1463',  # sled 45 deg leg press (side pov)
    '106': '2330',  # cable lat pulldown full range of motion
    '107': '0293',  # dumbbell bent over row
    '112': '1748',  # ez bar lying close grip triceps extension behind head (skullcrusher)
    '117': '0596',  # lever seated fly (pec deck / machine fly)
    '128': '3017',  # barbell pendlay row
    '129': '1270',  # cable upper chest crossovers
    '131': '0662',  # push-up (plain)
    '142': '0586',  # lever lying leg curl (prone)
    '148': '0860',  # cable kickback
    '156': '0857',  # wheel rollerout
    '158': '0030',  # barbell close-grip bench press
    '159': '0594',  # lever seated calf raise
    '167': '3013',  # low glute bridge on floor
    # Rejected -- no confident dataset match, keep vector icon fallback
    '108': None,   # Face Pull (Cable) - no cable face pull in dataset
    '111': None,   # Hip Thrust (Barbell) - dataset has no barbell hip thrust
    '121': None,   # Plank - no plain plank
    '122': None,   # Cable Woodchopper - no match
    '132': None,   # Rotary Torso (Machine) - no match
    '138': None,   # Eccentric Heel Drop - no match
    '145': None,   # Glute (Machine) - no match
    '147': None,   # Cable Straight Leg Raises - no cable leg raise
    '160': None,   # Nordic Curl - no match
}

with open('c:/Projects/fitness/.dataset_import/existing_84.txt', encoding='utf-8') as f:
    existing = [line.strip().split('|', 1) for line in f if line.strip()]

# Confidence threshold for auto-accepting the token-overlap match when no override given
with open('c:/Projects/fitness/.dataset_import/match_results2.txt', encoding='utf-8') as f:
    scores = {}
    for line in f:
        parts = line.rstrip('\n').split('|')
        scores[parts[0]] = float(parts[6])

ACCEPT_THRESHOLD = 0.25  # below this and no override => unmatched (already hand-reviewed everything down to 0.2)

final = []
for eid, ename in existing:
    if eid in OVERRIDES:
        ds_id = OVERRIDES[eid]
    elif scores.get(eid, 0) >= ACCEPT_THRESHOLD:
        ds_id = auto[eid]
    else:
        ds_id = None

    if ds_id is None:
        final.append((eid, ename, None))
    else:
        rec = by_id[ds_id]
        final.append((eid, ename, rec))

matched = sum(1 for _,_,r in final if r is not None)
print(f"{matched} / {len(final)} matched")

import csv
with open('c:/Projects/fitness/.dataset_import/existing_84_final.csv', 'w', encoding='utf-8', newline='') as out:
    w = csv.writer(out)
    w.writerow(['our_id','our_name','ds_id','ds_name','ds_equipment','ds_image','ds_gif','ds_target','ds_muscle_group'])
    for eid, ename, rec in final:
        if rec is None:
            w.writerow([eid, ename, '', '', '', '', '', '', ''])
        else:
            w.writerow([eid, ename, rec['id'], rec['name'], rec['equipment'], rec['image'], rec['gif_url'], rec['target'], rec['muscle_group']])
print('wrote existing_84_final.csv')
