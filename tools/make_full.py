"""Builds the complete base-prices.yml: every priced item gets its own explicit line, grouped for reading.
usage: make_full.py SRC.yml DST.yml WORKDIR [PRICE_LIST.md]"""
import re
import sys

SRC = sys.argv[1]          # current base-prices.yml
DST = sys.argv[2]          # where to write the new base-prices.yml
S = sys.argv[3].replace('\\', '/').rstrip('/') + '/'   # work folder with the GenPrices output (prices.tsv, priced.tsv, prices_before.tsv)

text = open(SRC, encoding='utf8').read().replace('\r\n', '\n')
i_base = text.index('\nbase:\n') + len('\nbase:\n')
i_prem = text.index('\n# ------------------------------------------------------------------------------\n#  PREMIUM items')
pre = text[:i_base]
HEADER = """# ==============================================================================
#  base-prices.yml  -  the price of EVERY item in the shop, one line per item (sell price, in rupees)
#
#  Written for Minecraft 26.3 from the game's own item and recipe data: every item that a player can get in
#  survival has a price below, from dirt to netherite gear. Only admin items and items that carry hidden data are
#  left out (command blocks, bedrock, spawners, spawn eggs, potions, enchanted books, maps, mob buckets...).
#
#  The shop sells to a player at  buy-multiplier x the sell price  (3x), and never for less than it would cost to
#  buy the ingredients one by one. Rare items are in the "premium:" section further down: either very expensive to
#  buy and cheap to sell, or sell-only (never for sale: elytra, totems, trims...). Tools and armour have a fixed buy
#  ladder there, and hard-to-get items (name tags, ender pearls, discs, sponges...) cost 100x or more of what they pay.
#
#  Safety: after reading this file the plugin checks EVERY recipe (crafting, un-crafting, smelting, stonecutting,
#  smithing) and every ore yield in "drops:". If any way of buying, processing and selling could make money, the
#  sell price of that item is lowered until it cannot (it says so in the server log). So a typing mistake in
#  this file can only make an item sell for less, never create money.
#
#  The list has two parts. "base:" holds the raw items (blocks you mine, crops, mob drops, anything with no recipe
#  or found in the wild): these are the real decisions. "crafted:" holds everything that is made from other items
#  (tools, armour, planks, stairs, redstone parts...), already worked out as  95% of its ingredients  and written
#  down so you can see and change it. Crafted items are also never sold for less than their recipe costs to buy,
#  and are not for sale when they need an item that is not for sale (emeralds).
#
#  You can change any number. An item that is NOT listed here (and has no recipe from listed items) is not
#  bought or sold at all. Admin overrides made with /shopadmin are kept separately and win over this file.
#  This file is NOT replaced when the plugin updates. To get new prices from a new release, rename it and restart.
# ==============================================================================
"""
pre = HEADER + pre[pre.index('\nrules:\n'):]
post = text[i_prem:]

# premium keys (exact or pattern): these items are priced in the premium section only
prem_text = post[post.index('\npremium:\n'):post.index('\ndrops:')]
prem_keys = [m.group(1).strip('"') for m in re.finditer(r'^  ("?[A-Z0-9_*]+"?):', prem_text, re.M)]
prem_re = [re.compile('^' + k.replace('*', '.*') + '$') for k in prem_keys]


def is_premium(n):
    return any(r.match(n) for r in prem_re)


priced = {}
for line in open(S + 'priced.tsv', encoding='utf8'):
    f = line.rstrip('\n').split('\t')
    if len(f) == 2:
        priced[f[0]] = int(f[1])
sell = {}
for line in open(S + 'prices.tsv', encoding='utf8'):
    f = line.rstrip('\n').split('\t')
    if f[1]:
        sell[f[0]] = int(f[1])

depth0 = {}
for line in open(S + 'prices_before.tsv', encoding='utf8'):
    f = line.rstrip('\n').split('\t')
    depth0[f[0]] = int(f[3])

# value to write: what the engine priced; if the loop check lowered it, the lowered value
value = {}
for n, p in priced.items():
    if is_premium(n):
        continue
    v = p
    if n in sell and sell[n] < p:
        v = sell[n]
    value[n] = v

CATS = {}
for line in open(S + 'cats.tsv', encoding='utf8'):
    f = line.rstrip(chr(10)).split(chr(9))
    if len(f) == 2:
        CATS[f[0]] = f[1]
# the shop menu order and names (ShopCategories.java); the number keeps the sections in menu order
ORDER = ['blocks', 'wood', 'stone', 'colors', 'ores', 'copper', 'redstone', 'tools', 'armor', 'food', 'farming', 'mobs',
         'nether', 'misc', 'rare']
TITLES = {
    'blocks': 'BUILDING BLOCKS (dirt, sand, gravel, clay, ice, snow)',
    'wood': 'WOOD (every wood family, including poplar)',
    'stone': 'STONE AND BRICKS',
    'colors': 'COLORS AND GLASS (wool, concrete, terracotta, glass, banners, beds, candles, dyes)',
    'ores': 'ORES AND METALS (ores, ingots, gems, netherite)',
    'copper': 'COPPER (every block, door, bar, lantern and statue, in every weathering stage)',
    'redstone': 'REDSTONE AND TRANSPORT',
    'tools': 'TOOLS AND WEAPONS',
    'armor': 'ARMOR AND MOUNTS (armour, horse armour, saddle, lead, name tag)',
    'food': 'FOOD',
    'farming': 'FARMING AND PLANTS',
    'mobs': 'MOB DROPS',
    'nether': 'NETHER, END AND DEEP DARK',
    'misc': 'UTILITY AND DECORATION',
    'rare': 'RARE AND SPECIAL (trophies, collectibles, shulker boxes)',
}


NAMES = {'blocks': 'Building blocks', 'wood': 'Wood', 'stone': 'Stone and bricks', 'colors': 'Colors and glass',
         'ores': 'Ores and metals', 'copper': 'Copper', 'redstone': 'Redstone and transport', 'tools': 'Tools and weapons',
         'armor': 'Armor and mounts', 'food': 'Food', 'farming': 'Farming and plants', 'mobs': 'Mob drops',
         'nether': 'Nether, End and deep dark', 'misc': 'Utility and decoration', 'rare': 'Rare and special'}


def cat(n):
    c = CATS.get(n, 'misc')
    return '%02d %s' % (ORDER.index(c), c)


def build(subset):
    groups = {}
    for n, v in subset.items():
        groups.setdefault(cat(n), []).append((n, v))
    out = []
    for key in sorted(groups):
        items = sorted(groups[key])
        out.append('  # ' + '=' * 70)
        out.append('  #  %s  (%d items)' % (TITLES[key.split(' ',1)[1]], len(items)))
        out.append('  # ' + '=' * 70)
        width = max(len(n) for n, _ in items) + 1
        for n, v in items:
            out.append('  %s %s' % ((n + ':').ljust(width + 1), '%.2f' % (v / 100.0)))
        out.append('')
    return out


raw = {n: v for n, v in value.items() if depth0.get(n, 0) == 0}
crafted = {n: v for n, v in value.items() if depth0.get(n, 0) > 0}
out = [pre.rstrip('\n'), '']
out += build(raw)
out += ['# ------------------------------------------------------------------------------',
        '#  CRAFTED items: made from other items. Each price is what the recipe rule gives (95% of the ingredients,',
        '#  smelting = the raw price), written out so every item can be read and changed on its own line.',
        '# ------------------------------------------------------------------------------',
        'crafted:', '']
out += build(crafted)
body = '\n'.join(out)
open(DST, 'w', encoding='utf8', newline='').write(body + post)
print('hand priced', len(raw), 'crafted', len(crafted), 'premium skipped', len(priced) - len(value))


# ---- optional: readable price list for players (markdown) ----
if len(sys.argv) > 4:
    final = {}
    for line in open(S + 'prices.tsv', encoding='utf8'):
        f = line.rstrip('\n').split('\t')
        s_ = int(f[1]) if f[1] else None
        b_ = int(f[2]) if f[2] else None
        if s_ or b_:
            final[f[0]] = (s_, b_)
    allg = {}
    for n in final:
        allg.setdefault(cat(n), []).append(n)
    def rs(v):
        return '-' if v is None else '{:,.2f}'.format(v / 100.0)

    md = ['# ArkaSMPEconomy price list', '',
          'Every item the shop trades, with what the shop **pays you** (sell) and what you **pay** (buy), in rupees.',
          'A dash means that way is closed: the item cannot be sold to the shop, or cannot be bought from it.',
          'Generated from `base-prices.yml` and the Minecraft 26.3 recipes, so it matches a fresh install. Servers can change',
          'any price with `/shopadmin set`, and `/worth` always shows the live price. See [PRICING.md](PRICING.md) for how the',
          'prices are built and why none of them can be looped.', '',
          '**%d items.**' % len(final), '']
    for key in sorted(allg):
        md.append('## %s (%d)' % (NAMES[key.split(' ',1)[1]], len(allg[key])))
        md.append('')
        md.append('| Item | You get | You pay |')
        md.append('|---|---:|---:|')
        for n in sorted(allg[key]):
            md.append('| %s | %s | %s |' % (n.replace('_', ' ').title(), rs(final[n][0]), rs(final[n][1])))
        md.append('')
    open(sys.argv[4], 'w', encoding='utf8', newline='').write('\n'.join(md))
    print('price list rows', len(final))
