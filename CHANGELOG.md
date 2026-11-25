# Changelog

All notable changes to ArkaSMPEconomy. Versions follow `MAJOR.MINOR.PATCH`: a fix raises the last number, a new
feature the middle one, a breaking change the first. Every release raises the version.

## 1.5.0

### Changed
- **Tools cost more.** Wooden to diamond tools have a fixed buy price (per material unit: wood 10, stone 25,
  copper 70, iron 180, gold 300, diamond 1,600; pickaxe and axe 3 units, hoe and sword 2, shovel and spear 1). A stone
  pickaxe is now ₹75, iron ₹540, diamond ₹4,800. Selling prices are unchanged.
- **Netherite is very expensive.** Netherite scrap and ancient debris cost ₹25,000 (was ₹4,500), so a netherite ingot
  costs about ₹100,000, netherite tools about ₹104,000 to ₹108,000, netherite armour about ₹103,000 to ₹105,000 and a netherite block about
  ₹900,000. They follow the scrap price automatically.
- In the shop, tools and armour stay in their own categories however much they cost (before, anything over ₹1,500
  moved to **Rare & Special**).

### Removed from buying (they can still be sold)
- Totem of undying, elytra, dragon head, dragon egg, end crystal, mace, heavy core, trident and all armour trim
  smithing templates can no longer be bought. Ghast tears are sell-only as well, so an end crystal cannot be built
  from bought parts (dried ghast is therefore not for sale either).
- The shop now **pays** for them: dragon egg ₹2,500, dragon head ₹1,000, elytra ₹900, heavy core ₹500, mace about ₹478,
  silence trim ₹400, trident ₹250, rarer trims ₹200, other trims ₹120, totem ₹150, end crystal ₹100. The hourly and
  daily selling limits still apply. Before, the dragon egg and trims could not be sold at all.
- The netherite upgrade template is still bought at ₹3,000 and cannot be sold (as before).

### Notes for servers already running
`base-prices.yml` is not replaced on update. Rename it (for example to `base-prices-old.yml`) and restart to receive the
new prices. Admin changes made with `/shopadmin` are kept in `price-overrides.yml` and survive.

### Tests
- New `ShippedPricesCheck` reads the shipped `base-prices.yml`: tool ladder rises tier by tier, the rare items are in
  `no-buy` and not in `no-sell`, and their sell prices stay modest. The price loop check prints no notes.

## 1.4.0

Requires ArkaSMPCore 1.2.0 or newer.

### Added
- **Every item has its own price** in `base-prices.yml`: about 1,500 items written for Minecraft 26.3 from the game's
  own item and recipe data (previously most of them were calculated on the fly). The file now has a `base:` part for
  raw items, a `crafted:` part for everything made from recipes, `premium:`, `drops:` and the no-sell and no-buy lists.
- **PRICE_LIST.md**: the full table of what you get and what you pay for every item, for players and admins.
- 26.3 items: poplar wood set, wool stairs and slabs, concrete stairs and slabs, cushions, straw bed, spears for every
  tool material, copper chests, chains, lanterns, bars, doors, trapdoors, lightning rods and golem statues in every
  rust stage (and waxed), cinnabar and sulfur stone families, nautilus armour, copper horse armour, new egg colours and more.
- Shop now reads the "transmute" recipes of newer game versions (for example dyeing shulker boxes) for the loop check.
- Worn anvils (chipped, damaged) can be sold; bells and the loot-only banner patterns are premium items.

### Changed
- **Never cheaper to buy than to build** now covers every crafted item, and is worked out from the raw items upwards,
  so that breaking a netherite block into ingots cannot make netherite cheap.
- Loop check: items that cannot be bought are no longer treated as if they could be, which stops emerald and emerald
  blocks pulling each other's sell price down to nothing.
- Prices of crafted items that used to be rounded up twice are now exact, so a few buy prices are slightly lower.

Your existing `base-prices.yml` is not overwritten. To get the complete list, rename it (for example to
`base-prices-old.yml`), restart so the new one is created, then copy back any prices you changed.

## 1.3.0

Requires ArkaSMPCore 1.2.0 or newer.

### Added
- **Many more items in the shop**: natural blocks (grass, mycelium, podzol, mud, moss, sculk, coral, dripstone,
  nylium), ores as blocks, stripped wood and hyphae, leaves, saplings, plants and flowers, weathered copper, and
  items from newer game versions (poplar wood set, wool stairs and slabs and similar, matched by name patterns and
  recipes).
- **Netherite tools and armour** (smithing recipes) and all tools, weapons and armour are priced from their recipes.
- **Premium items**: elytra, totem of undying, end crystal, nether star, trident, heart of the sea, enchanted golden
  apple, heavy core, skulls, netherite scrap, shulker shell, saddle, smithing templates, music discs, pottery sherds
  and more are bought at very high prices and sell for very little. Everything crafted from them (beacon, conduit,
  mace, netherite gear, shulker boxes) inherits those costs.
- **Rare & Special** shop category for everything that costs 1,500 rupees or more (`rules.rare-from-rupees`).
- **Loop check**: after pricing, every recipe (crafting, un-crafting, smelting, stonecutting, smithing) and the ore
  and block mining yields in `drops:` are checked. Any sell price that could make money through buy, process, sell is
  lowered automatically, so a wrong price cannot create infinite money. Tested on 300 random price tables.
- A crafted item can never cost less than buying its ingredients one by one.
- Shop categories learn new wood families by themselves.

### Changed
- Rare drops sell for much less (netherite scrap 100 -> 25 rupees, ghast tear, echo shard, shulker shell,
  nautilus shell, phantom membrane, scutes) while their buy prices are far higher.
- Elytra, nether stars, tridents, hearts of the sea, enchanted golden apples and skulls can now be sold (cheaply).
- Unknown item names in `base-prices.yml` (from other game versions) are summarised in one log line.

### Fixed
- Turtle scutes are now priced (the old name `SCUTE` does not exist; the item is `TURTLE_SCUTE`).
- Leaves, mushrooms and petals are listed under Farming instead of Colours.

Your existing `base-prices.yml` is not overwritten. To get the new prices and items, rename it (for example to
`base-prices-old.yml`), restart so the new one is created, then copy back any prices you changed.

## 1.2.0

Requires ArkaSMPCore 1.2.0 or newer.

### Added
- **Safe upgrades and downgrades** (see ArkaSMPCore): a snapshot of `economy.db` and the other data files is saved to
  `data-backups/` on every version change, and data from a newer format is never opened by an older jar.
- The balance database now records its format (`PRAGMA user_version`). An `economy.db` from a newer jar is not opened
  by an older one.

### Fixed
- An unreadable data file is kept as `<name>.broken-<time>` and never overwritten by a later failure.

## 1.1.0

Requires ArkaSMPCore 1.1.0 or newer.

### Added
- **Automatic config updates.** `config.yml` stores the plugin version in `config-version`. On a new version the
  plugin backs up the old file (`config-backups/`), adds the new options with their comments, keeps all your
  values, and raises the version. Do not edit `config-version` by hand.
- **Item shop**: `/shop`, `/sell`, `/worth`, `/shopadmin`, with a price engine that derives prices from recipes so
  no crafting loop can print money, and anti-farm sell limits.
- **`/sellgui`**: a chest to drop items in and sell with one button.
- `MoneyCharger` service (warp cost and claim-block purchases in ArkaSMPCore).

### Fixed
- The database writer survives unexpected errors instead of stopping silently.
- A corrupt data file is backed up as `.broken` instead of being overwritten.
- Saving no longer tries to schedule work after the plugin is disabled.

## 1.0.0
- First release: rupee balances, `/bal`, `/pay`, `/baltop`, hourly play-time reward, safe `/trade`.
