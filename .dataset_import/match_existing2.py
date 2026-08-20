import json, re

with open('c:/Projects/fitness/.dataset_import/exercises.json', encoding='utf-8') as f:
    dataset = json.load(f)

with open('c:/Projects/fitness/.dataset_import/existing_84.txt', encoding='utf-8') as f:
    existing = [line.strip().split('|', 1) for line in f if line.strip()]

with open('c:/Projects/fitness/.dataset_import/existing_84_equipment.txt', encoding='utf-8') as f:
    eq_map = dict(line.strip().split('|', 1) for line in f if line.strip())

# Equipment enum -> acceptable dataset equipment strings
EQUIP_BUCKET = {
    'BARBELL': {'barbell', 'olympic barbell'},
    'DUMBBELL': {'dumbbell'},
    'CABLE': {'cable'},
    'MACHINE': {'leverage machine', 'sled machine'},
    'BODYWEIGHT': {'body weight', 'assisted'},
    'KETTLEBELL': {'kettlebell'},
    'EZ_BAR': {'ez barbell'},
    'BANDS': {'band', 'resistance band'},
    'SMITH_MACHINE': {'smith machine'},
    'OTHER': set(),  # no filter, e.g. trap bar, farmers walk implements
}

STOPWORDS = {'the','a','an','with','and','single','arm','seated','standing','close','grip'}

def tokens(name):
    n = re.sub(r'\(.*?\)', ' ', name.lower())
    n = n.replace(chr(39), '')
    n = re.sub(r'[^a-z0-9\s]', ' ', n)
    words = [w for w in n.split() if w and w not in STOPWORDS]
    return set(words)

def score(a_tokens, b_tokens):
    if not a_tokens or not b_tokens:
        return 0.0
    inter = a_tokens & b_tokens
    union = a_tokens | b_tokens
    return len(inter) / len(union)

results = []
for eid, ename in existing:
    etoks = tokens(ename)
    eq = eq_map.get(eid, 'OTHER')
    allowed = EQUIP_BUCKET.get(eq, set())

    candidates = dataset
    if allowed:
        filtered = [r for r in dataset if r['equipment'] in allowed]
        if filtered:
            candidates = filtered

    best = None
    best_score = -1
    for rec in candidates:
        s = score(etoks, tokens(rec['name']))
        if s > best_score:
            best_score = s
            best = rec

    results.append((eid, ename, eq, best['id'] if best else '-', best['name'] if best else '-',
                     best['equipment'] if best else '-', round(best_score, 3)))

with open('c:/Projects/fitness/.dataset_import/match_results2.txt', 'w', encoding='utf-8') as out:
    for r in results:
        out.write(f"{r[0]}|{r[1]}|{r[2]}|{r[3]}|{r[4]}|{r[5]}|{r[6]}\n")

# summary
high = sum(1 for r in results if r[6] >= 0.5)
med = sum(1 for r in results if 0.3 <= r[6] < 0.5)
low = sum(1 for r in results if r[6] < 0.3)
print(f"high(>=0.5): {high}, med(0.3-0.5): {med}, low(<0.3): {low}, total: {len(results)}")
