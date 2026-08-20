import csv, os, urllib.request, urllib.error, time
from concurrent.futures import ThreadPoolExecutor, as_completed

BASE = 'https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/'
STAGING = 'c:/Projects/fitness/.dataset_import/staging'

with open('c:/Projects/fitness/.dataset_import/media_manifest.csv', encoding='utf-8') as f:
    rows = list(csv.DictReader(f))

def dest_path(dest_relpath):
    # dest_relpath like "exercises/images/7.jpg" or "exercises/gifs/7.gif"
    parts = dest_relpath.split('/')
    kind = parts[1]  # images or gifs
    fname = parts[2]
    return os.path.join(STAGING, kind, fname)

def fetch_one(row):
    src = row['src_path']
    dest = dest_path(row['dest_relpath'])
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return ('skip', dest)
    url = BASE + src
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'curl/8.0'})
            with urllib.request.urlopen(req, timeout=20) as resp:
                data = resp.read()
            with open(dest, 'wb') as f:
                f.write(data)
            return ('ok', dest)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return ('404', url)
            time.sleep(1)
        except Exception as e:
            time.sleep(1)
    return ('fail', url)

results = {'ok': 0, 'skip': 0, '404': 0, 'fail': 0}
failures = []
with ThreadPoolExecutor(max_workers=12) as ex:
    futures = [ex.submit(fetch_one, row) for row in rows]
    for i, fut in enumerate(as_completed(futures)):
        status, info = fut.result()
        results[status] += 1
        if status in ('404', 'fail'):
            failures.append(info)
        if (i + 1) % 100 == 0:
            print(f"{i+1}/{len(rows)} processed... {results}")

print('DONE', results)
if failures:
    print('FAILURES:')
    for f in failures:
        print(' ', f)
