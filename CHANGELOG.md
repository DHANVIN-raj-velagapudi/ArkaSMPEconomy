# Changelog

All notable changes to ArkaSMPEconomy. Versions follow `MAJOR.MINOR.PATCH`: a fix raises the last number, a new
feature the middle one, a breaking change the first. Every release raises the version.

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
