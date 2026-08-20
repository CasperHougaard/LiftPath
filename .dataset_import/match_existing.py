import json, re, difflib

with open('c:/Projects/fitness/.dataset_import/exercises.json', encoding='utf-8') as f:
    dataset = json.load(f)

with open('c:/Projects/fitness/.dataset_import/existing_84.txt', encoding='utf-8') as f:
    existing = [line.strip().split('|', 1) for line in f if line.strip()]

def normalize(name):
    n = name.lower()
    n = re.sub(r'\(.*?\)', ' ', n)  # drop parenthetical equipment
    n = re.sub(r'[^a-z0-9\s]', ' ', n)
    n = re.sub(r'\s+', ' ', n).strip()
    return n

# Build lookup of dataset normalized names -> list of records
dataset_by_norm = {}
for rec in dataset:
    norm = normalize(rec['name'])
    dataset_by_norm.setdefault(norm, []).append(rec)

results = []
for eid, ename in existing:
    norm = normalize(ename)
    exact = dataset_by_norm.get(norm)
    if exact:
        results.append((eid, ename, 'EXACT', exact[0]['id'], exact[0]['name'], exact[0]['equipment'], 1.0))
        continue
    # fuzzy match against all dataset names
    best = None
    best_score = 0
    for rec in dataset:
        score = difflib.SequenceMatcher(None, norm, normalize(rec['name'])).ratio()
        if score > best_score:
            best_score = score
            best = rec
    results.append((eid, ename, 'FUZZY', best['id'], best['name'], best['equipment'], round(best_score,3)))

for r in results:
    print(f"{r[0]}|{r[1]}|{r[2]}|{r[3]}|{r[4]}|{r[5]}|{r[6]}")
