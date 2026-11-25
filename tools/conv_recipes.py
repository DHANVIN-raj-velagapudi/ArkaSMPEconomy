"""Converts the vanilla 26.3 data in mcdata/ into two plain files the Java price generator reads.
items.txt   : one item id per line (upper case)
recipes.tsv : out <TAB> amount <TAB> kind(c=craft, s=smelt) <TAB> slot,slot,...   (a slot is A|B|C choices)
"""
import glob
import json
import os
import sys

root = sys.argv[1]
outdir = sys.argv[2]


def load(p):
    with open(p, encoding='utf8') as f:
        return json.load(f)


tags = {}
for f in glob.glob(os.path.join(root, 'data/minecraft/tags/item/**/*.json'), recursive=True):
    rel = os.path.relpath(f, os.path.join(root, 'data/minecraft/tags/item')).replace('\\', '/')[:-5]
    tags[rel] = load(f)['values']


def resolve_tag(name, seen=()):
    name = name.replace('minecraft:', '')
    if name in seen:
        return []
    out = []
    for v in tags.get(name, []):
        if isinstance(v, dict):
            v = v.get('id', '')
        if v.startswith('#'):
            out += resolve_tag(v[1:], seen + (name,))
        else:
            out.append(v.replace('minecraft:', ''))
    return out


def ing(x):
    """ingredient -> list of item ids"""
    if isinstance(x, list):
        r = []
        for y in x:
            r += ing(y)
        return r
    if isinstance(x, dict):
        x = x.get('item') or x.get('tag') and '#' + x['tag'] or ''
    if x.startswith('#'):
        return resolve_tag(x[1:])
    return [x.replace('minecraft:', '')]


def res(r):
    if isinstance(r, str):
        return r.replace('minecraft:', ''), 1, False
    return r['id'].replace('minecraft:', ''), r.get('count', 1), bool(r.get('components'))


items = sorted(os.path.splitext(os.path.basename(f))[0]
               for f in glob.glob(os.path.join(root, 'assets/minecraft/items/*.json')))
item_set = set(items)

rows = []
skipped = {}
for f in sorted(glob.glob(os.path.join(root, 'data/minecraft/recipe/*.json'))):
    d = load(f)
    t = d['type'].replace('minecraft:', '')
    slots = None
    kind = 'c'
    if t == 'crafting_shaped':
        slots = []
        for row in d['pattern']:
            for c in row:
                if c != ' ':
                    slots.append(ing(d['key'][c]))
    elif t == 'crafting_shapeless':
        slots = [ing(x) for x in d['ingredients']]
    elif t in ('smelting', 'blasting', 'smoking', 'campfire_cooking'):
        slots = [ing(d['ingredient'])]
        kind = 's'
    elif t == 'stonecutting':
        slots = [ing(d['ingredient'])]
    elif t == 'smithing_transform':
        slots = [ing(d['template']), ing(d['base']), ing(d['addition'])]
    elif t == 'crafting_transmute':
        slots = [ing(d['input']), ing(d['material'])]
    else:
        skipped[t] = skipped.get(t, 0) + 1
        continue
    if not isinstance(d.get('result'), (dict, str)) or (isinstance(d['result'], dict) and 'id' not in d['result']):
        skipped['noresult'] = skipped.get('noresult', 0) + 1
        continue
    out, n, comp = res(d['result'])
    if comp or out not in item_set or not slots or any(len(s) == 0 for s in slots):
        skipped['bad:' + t] = skipped.get('bad:' + t, 0) + 1
        continue
    slots = [[x for x in s if x in item_set] for s in slots]
    if any(len(s) == 0 for s in slots):
        skipped['bad:' + t] = skipped.get('bad:' + t, 0) + 1
        continue
    rows.append((out.upper(), n, kind, ','.join('|'.join(x.upper() for x in s) for s in slots)))

with open(os.path.join(outdir, 'items.txt'), 'w', encoding='utf8', newline='\n') as w:
    w.write('\n'.join(i.upper() for i in items) + '\n')
with open(os.path.join(outdir, 'recipes.tsv'), 'w', encoding='utf8', newline='\n') as w:
    for r in rows:
        w.write('\t'.join(str(x) for x in r) + '\n')
print('items', len(items), 'recipes', len(rows), 'skipped', skipped)
