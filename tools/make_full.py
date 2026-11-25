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
#  buy and cheap to sell, or sell-only (never for sale: elytra, totems, trims...). Tools have a fixed buy ladder there.
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

COLOURS = ['WHITE', 'ORANGE', 'MAGENTA', 'LIGHT_BLUE', 'YELLOW', 'LIME', 'PINK', 'GRAY', 'LIGHT_GRAY', 'CYAN', 'PURPLE',
           'BLUE', 'BROWN', 'GREEN', 'RED', 'BLACK']
WOODS = set()
for n in value:
    if n.endswith('_PLANKS'):
        WOODS.add(n[:-7])
WOODS.add('BAMBOO')
WOODS |= {'CRIMSON', 'WARPED'}


def has(n, *subs):
    return any(s in n for s in subs)


def cat(n):
    if n.endswith('_ORE') or n in ('RAW_IRON', 'RAW_GOLD', 'RAW_COPPER', 'COAL', 'CHARCOAL', 'DIAMOND', 'EMERALD', 'LAPIS_LAZULI',
                                    'QUARTZ', 'AMETHYST_SHARD', 'REDSTONE', 'IRON_INGOT', 'GOLD_INGOT', 'COPPER_INGOT',
                                    'IRON_NUGGET', 'GOLD_NUGGET', 'COPPER_NUGGET', 'NETHERITE_INGOT', 'NETHERITE_SCRAP',
                                    'ANCIENT_DEBRIS', 'RAW_IRON_BLOCK', 'RAW_GOLD_BLOCK', 'RAW_COPPER_BLOCK') \
            or n in ('IRON_BLOCK', 'GOLD_BLOCK', 'DIAMOND_BLOCK', 'EMERALD_BLOCK', 'LAPIS_BLOCK', 'COAL_BLOCK', 'REDSTONE_BLOCK',
                     'NETHERITE_BLOCK', 'AMETHYST_BLOCK', 'COPPER_BLOCK', 'QUARTZ_BLOCK'):
        return '01 Ores, metals and gems'
    if has(n, '_SWORD', '_PICKAXE', '_AXE', '_SHOVEL', '_HOE', '_SPEAR') or n in (
            'BOW', 'CROSSBOW', 'TRIDENT', 'MACE', 'SHIELD', 'FISHING_ROD', 'SHEARS', 'FLINT_AND_STEEL', 'ARROW',
            'SPECTRAL_ARROW', 'BRUSH', 'CARROT_ON_A_STICK', 'WARPED_FUNGUS_ON_A_STICK', 'SPYGLASS', 'COMPASS',
            'RECOVERY_COMPASS', 'CLOCK', 'LEAD', 'NAME_TAG', 'BUCKET', 'WIND_CHARGE', 'FIRE_CHARGE', 'ELYTRA', 'TOTEM_OF_UNDYING'):
        return '02 Tools and weapons'
    if has(n, '_HELMET', '_CHESTPLATE', '_LEGGINGS', '_BOOTS', 'HORSE_ARMOR', 'NAUTILUS_ARMOR', 'WOLF_ARMOR', 'HARNESS') \
            or n in ('SADDLE',):
        return '03 Armour and mounts'
    if n.endswith('_WOOL') or has(n, 'CARPET', '_BED', 'BANNER', '_DYE', 'CONCRETE', 'TERRACOTTA', 'STAINED_GLASS', 'CANDLE',
                                  'CUSHION', 'SHULKER_BOX', 'BUNDLE') or n.endswith('WOOL_STAIRS') or n.endswith('WOOL_SLAB') \
            or n in ('PAINTING', 'ITEM_FRAME', 'GLOW_ITEM_FRAME'):
        return '05 Colours and decoration'
    for w in WOODS:
        if n == w + '_LOG' or n == w + '_STEM' or n == w + '_WOOD' or n == w + '_HYPHAE' or n.startswith(w + '_') \
                or n.startswith('STRIPPED_' + w) or n == w + '_BLOCK':
            if not has(n, 'LEAVES', 'SAPLING', 'PROPAGULE', 'NYLIUM', 'FUNGUS', 'ROOTS', 'WART'):
                if w in ('BAMBOO',) and n in ('BAMBOO',):
                    break
                return '04 Wood'
    if has(n, 'REDSTONE', 'PISTON', 'REPEATER', 'COMPARATOR', 'OBSERVER', 'HOPPER', 'DROPPER', 'DISPENSER', 'LEVER', 'RAIL',
           'TNT', 'DAYLIGHT', 'NOTE_BLOCK', 'JUKEBOX', 'TARGET', 'LIGHTNING_ROD', 'TRIPWIRE', 'BULB', 'CRAFTER',
           'PRESSURE_PLATE', '_BUTTON', 'MINECART', 'LECTERN', 'SCULK_SENSOR', 'SCULK_SHRIEKER', 'CAULDRON', 'BREWING',
           'FURNACE', 'SMOKER', 'LANTERN', 'TORCH', 'CAMPFIRE', 'BEACON', 'END_ROD', 'LODESTONE', 'RESPAWN_ANCHOR',
           'ENCHANTING_TABLE', 'ANVIL', 'GRINDSTONE', 'STONECUTTER', 'LOOM', 'SMITHING_TABLE', 'CARTOGRAPHY', 'FLETCHING',
           'COMPOSTER', 'BARREL', 'CHEST', 'ENDER_CHEST', 'CRAFTING_TABLE', 'BOOKSHELF', 'BELL', 'CONDUIT', 'GLOWSTONE',
           'REDSTONE_LAMP', 'SEA_LANTERN', 'SHROOMLIGHT', 'FROGLIGHT', 'IRON_BARS', 'IRON_DOOR', 'IRON_TRAPDOOR', 'CHAIN',
           'COPPER_DOOR', 'COPPER_TRAPDOOR', 'COPPER_BARS', 'COPPER_GRATE', 'COPPER_GOLEM_STATUE', 'LADDER', 'SCAFFOLDING',
           'FLOWER_POT', 'DECORATED_POT', 'ARMOR_STAND', 'MAP', 'BOOK', 'PAPER', 'SIGN', 'SHELF', 'BOWL', 'GLASS_BOTTLE'):
        if has(n, 'COPPER') and has(n, 'CUT_', 'CHISELED') :
            return '08 Copper'
        return '06 Redstone, utility and workstations'
    if has(n, 'COPPER') and n not in ('COPPER_INGOT',):
        return '08 Copper'
    if has(n, 'NETHER', 'SOUL_', 'BLACKSTONE', 'BASALT', 'CRIMSON', 'WARPED', 'MAGMA', 'GLOWSTONE', 'END_STONE', 'PURPUR',
           'CHORUS', 'SHULKER_SHELL', 'DRAGON', 'GHAST', 'BLAZE', 'ENDER_', 'WITHER', 'ECHO', 'SCULK', 'BREEZE', 'CRYING',
           'OBSIDIAN', 'HEAVY_CORE', 'GILDED', 'WART', 'WEEPING', 'TWISTING', 'SPORE', 'CREAKING', 'RESIN', 'PALE_'):
        return '09 Nether, End and deep dark'
    if has(n, 'BEEF', 'PORKCHOP', 'CHICKEN', 'MUTTON', 'RABBIT', 'COD', 'SALMON', 'TROPICAL_FISH', 'PUFFERFISH', 'BREAD', 'COOKIE',
           'CAKE', 'PIE', 'STEW', 'SOUP', 'APPLE', 'CARROT', 'POTATO', 'BEETROOT', 'MELON', 'PUMPKIN', 'BERRIES', 'KELP', 'HONEY',
           'SUGAR', 'EGG', 'WHEAT', 'COCOA', 'MILK', 'MUSHROOM', 'FUNGUS', 'ROTTEN', 'SPIDER_EYE', 'GOLDEN_DANDELION'):
        if n in ('PUMPKIN_SEEDS', 'MELON_SEEDS', 'BEETROOT_SEEDS', 'WHEAT_SEEDS', 'TURTLE_EGG', 'SNIFFER_EGG', 'EGG') :
            return '10 Farming and plants'
        return '07 Food'
    if has(n, 'LEAVES', 'SAPLING', 'PROPAGULE', 'FLOWER', 'TULIP', 'ORCHID', 'ALLIUM', 'BLUET', 'DAISY', 'CORNFLOWER', 'LILAC',
           'ROSE', 'PEONY', 'POPPY', 'DANDELION', 'SUNFLOWER', 'LILY', 'GRASS', 'FERN', 'VINE', 'MOSS', 'AZALEA', 'DRIPLEAF',
           'SEEDS', 'CACTUS', 'BAMBOO', 'SUGAR_CANE', 'SEAGRASS', 'SEA_PICKLE', 'CORAL', 'KELP', 'BUSH', 'SHRUB', 'PETALS',
           'ROOTS', 'LEAF_LITTER', 'WILDFLOWERS', 'DEAD_BUSH', 'PITCHER', 'TORCHFLOWER', 'EYEBLOSSOM', 'LICHEN', 'FIREFLY',
           'MYCELIUM', 'PODZOL', 'GRASS_BLOCK', 'DIRT', 'MUD', 'CLAY', 'SAND', 'GRAVEL', 'SNOW', 'ICE', 'SPONGE', 'FROGSPAWN',
           'HAY_BLOCK', 'BEE', 'BEEHIVE', 'HONEYCOMB', 'MANGROVE_ROOTS', 'EGG'):
        return '10 Farming and plants'
    if has(n, 'BONE', 'STRING', 'FEATHER', 'LEATHER', 'GUNPOWDER', 'SLIME', 'INK_SAC', 'PHANTOM', 'RABBIT_', 'SCUTE', 'NAUTILUS',
           'PRISMARINE', 'HEART_OF_THE_SEA', 'TURTLE', 'HEAD', 'SKULL', 'TEAR', 'MAGMA_CREAM', 'ARMADILLO'):
        return '11 Mob drops'
    if has(n, 'STONE', 'COBBLE', 'DEEPSLATE', 'GRANITE', 'DIORITE', 'ANDESITE', 'TUFF', 'BRICK', 'SANDSTONE', 'QUARTZ', 'CALCITE',
           'DRIPSTONE', 'TERRACOTTA', 'GLASS', 'CINNABAR', 'SULFUR', 'AMETHYST', 'WALL', 'SLAB', 'STAIRS', 'OBSIDIAN',
           'PACKED', 'CRACKED', 'CHISELED', 'POLISHED', 'SMOOTH', 'CUT_', 'ROOTED', 'MOSSY', 'END_STONE'):
        return '00 Stone and building blocks'
    return '12 Everything else'


def build(subset):
    groups = {}
    for n, v in subset.items():
        groups.setdefault(cat(n), []).append((n, v))
    titles = {
        '00 Stone and building blocks': 'STONE, SOIL AND BUILDING BLOCKS',
        '01 Ores, metals and gems': 'ORES, METALS AND GEMS',
        '02 Tools and weapons': 'TOOLS AND WEAPONS',
        '03 Armour and mounts': 'ARMOUR AND MOUNTS',
        '04 Wood': 'WOOD (every wood family, including poplar)',
        '05 Colours and decoration': 'COLOURS (wool, concrete, glass, terracotta, dyes) AND DECORATION',
        '06 Redstone, utility and workstations': 'REDSTONE, UTILITY AND WORKSTATIONS',
        '07 Food': 'FOOD',
        '08 Copper': 'COPPER',
        '09 Nether, End and deep dark': 'NETHER, END AND DEEP DARK',
        '10 Farming and plants': 'FARMING, PLANTS AND NATURAL BLOCKS',
        '11 Mob drops': 'MOB DROPS AND SEA ITEMS',
        '12 Everything else': 'EVERYTHING ELSE',
    }
    out = []
    for key in sorted(groups):
        items = sorted(groups[key])
        out.append('  # ' + '=' * 70)
        out.append('  #  %s  (%d items)' % (titles[key], len(items)))
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
    names = {
        '00 Stone and building blocks': 'Stone, soil and building blocks', '01 Ores, metals and gems': 'Ores, metals and gems',
        '02 Tools and weapons': 'Tools and weapons', '03 Armour and mounts': 'Armour and mounts',
        '04 Wood': 'Wood', '05 Colours and decoration': 'Colours and decoration',
        '06 Redstone, utility and workstations': 'Redstone, utility and workstations', '07 Food': 'Food',
        '08 Copper': 'Copper', '09 Nether, End and deep dark': 'Nether, End and deep dark',
        '10 Farming and plants': 'Farming, plants and natural blocks', '11 Mob drops': 'Mob drops and sea items',
        '12 Everything else': 'Everything else'}

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
        md.append('## %s (%d)' % (names[key], len(allg[key])))
        md.append('')
        md.append('| Item | You get | You pay |')
        md.append('|---|---:|---:|')
        for n in sorted(allg[key]):
            md.append('| %s | %s | %s |' % (n.replace('_', ' ').title(), rs(final[n][0]), rs(final[n][1])))
        md.append('')
    open(sys.argv[4], 'w', encoding='utf8', newline='').write('\n'.join(md))
    print('price list rows', len(final))
