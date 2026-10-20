import json, subprocess, sys

sha = {}
for line in open('.ocr/prs.txt', encoding='utf-8'):
    line = line.strip()
    if not line:
        continue
    n, b, s = line.split('|')
    sha[int(n)] = s

cs = json.load(open('.ocr/all-comments.json', encoding='utf-8'))
meds = [c for c in cs if c.get('severity') == 'medium']
print(f'medium 共 {len(meds)} 条\n')
bad = 0
for c in meds:
    pr = c['_pr']; s = sha[pr]; p = c.get('path')
    sl = c.get('start_line') or 0; el = c.get('end_line') or sl
    r = subprocess.run(['git', 'show', f'{s}:{p}'], capture_output=True,
                       text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        print(f"❌ #{pr} {p} — 该提交树中无此文件"); bad += 1; continue
    lines = r.stdout.split('\n')
    if sl > len(lines):
        print(f"❌ #{pr} {p}:{sl} — 超出文件长度({len(lines)}行) ← 位置不可信"); bad += 1; continue
    print(f"── #{pr} {p}:{sl}-{el}")
    for i in range(max(1, sl - 2), min(len(lines), el + 2) + 1):
        mark = '>' if sl <= i <= el else ' '
        print(f"  {mark}{i:>5}| {lines[i-1][:118]}")
    print()
print(f'位置不可信的条数: {bad}')
