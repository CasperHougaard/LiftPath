import json
from collections import defaultdict

with open('c:/Projects/fitness/.dataset_import/new_candidates_all.json', encoding='utf-8') as f:
    candidates = json.load(f)

NOISE_MARKERS = ['(male)', '(female)', 'pov)', 'v. 2', 'v.2', 'v. 3', '(on knees)', '(kneeling)']

def is_noisy(name):
    n = name.lower()
    return any(m in n for m in NOISE_MARKERS)

filtered = [c for c in candidates if not is_noisy(c['ds_name'])]
print(f"{len(filtered)} candidates after dropping pose/gender/version-variant noise (from {len(candidates)})")

# Prefer shorter (more canonical-sounding) names within each (family, equipment) bucket
filtered.sort(key=lambda c: len(c['ds_name']))

PER_FAMILY_EQUIPMENT_CAP = 2
PER_FAMILY_CAP = 10

family_equip_count = defaultdict(int)
family_count = defaultdict(int)
selected = []

for c in filtered:
    key_fe = (c['family_id'], c['equipment'])
    if family_equip_count[key_fe] >= PER_FAMILY_EQUIPMENT_CAP:
        continue
    if family_count[c['family_id']] >= PER_FAMILY_CAP:
        continue
    family_equip_count[key_fe] += 1
    family_count[c['family_id']] += 1
    selected.append(c)

print(f"{len(selected)} selected after per-family/equipment variety caps")

from collections import Counter
fam_counts = Counter(c['family_id'] for c in selected)
for fam, cnt in fam_counts.most_common():
    print(f"  {fam}: {cnt}")

with open('c:/Projects/fitness/.dataset_import/new_selected.json', 'w', encoding='utf-8') as f:
    json.dump(selected, f, indent=1)
