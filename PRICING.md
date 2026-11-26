# ArkaSMPEconomy Stage 2: how prices are decided

`base-prices.yml` lists **every item in the shop on its own line**, about 1,500 of them, written for Minecraft 26.3 from the game's own item and recipe data. The full table with what you get and what you pay is [PRICE_LIST.md](PRICE_LIST.md).

The file has two parts:
- **`base:`** (about 360 items) are the real decisions: stone, dirt, crops, ores, mob drops, plants, and items with no recipe.
- **`crafted:`** (about 1,000 items) is everything made from other items: tools, armour, planks, stairs, doors, redstone parts, dyed blocks. Each price was worked out with rule 2 and 3 below and is written down so every item can be read and changed.

Rare items (elytra, totem, netherite...) are in the **`premium:`** part and are described further down. Admin and unobtainable items are left out on purpose.

## The five rules
1. **Buy is 3x sell.** Buying then selling the same item always loses two thirds.
2. **Crafted sell price is at most 95% of its ingredients.** For every recipe the engine checks `sell(output) x amount <= sum(sell(ingredients)) x 0.95`. If an item has several recipes, the cheapest one wins. Compacting ingots into blocks and back can never gain.
3. **Smelted/cooked sell price equals the raw item.** Smelting is free of profit and free of penalty.
4. **Unpriced means off.** An item with no base price and no recipe path (potions, spawn eggs, spawners, command blocks, heads) cannot be bought or sold.
5. **Inherited restrictions.** If an item is on the no-buy list, anything crafted from it is also not for sale, unless it can also be made some other way from things that are for sale. That blocks emerald blocks being bought "through the back door".
6. **Never cheaper to buy than to build.** A crafted item costs at least what its cheapest recipe costs when every ingredient is bought from the raw items (and every premium item at its premium price). This is why a beacon always costs more than a nether star, and why breaking a netherite block down into ingots cannot be used to get cheap netherite.

## Limits on farms and alts (per player)
| Limit | Default | Stops |
|---|---|---|
| Per-item-type earnings per hour | ₹600 (rare items: at least 2 units) | one automatic farm giving unlimited money |
| Daily total from selling | ₹18,000 | grinding every item type all day |
| Minimum active play-time before selling | 2 hours | fresh alt accounts farming |
| `/sell all` confirm above | ₹2,000 | accidents |

Items with enchantments, a custom name or lore, damage, or contents (shulker boxes, bundles) are never accepted. Only plain, full-durability items are bought.

## Where the numbers come from
| Group | Sell price | Reason |
|---|---|---|
| Stone, dirt, sand, gravel, netherrack | ₹0.02 – 0.10 | infinite by hand |
| Logs, bamboo, saplings | ₹0.40 / ₹0.05 | tree farms |
| Wheat, carrot, potato, cane, kelp | ₹0.05 – 0.30 | automatic farms |
| Mob drops (bone, string, gunpowder) | ₹0.40 – 1.00 | mob farms |
| Coal, copper, redstone, lapis | ₹0.40 – 1.00 | common ores |
| Iron | ₹2 | iron golem farms exist |
| Gold | ₹4 | piglin farms exist |
| Emerald | ₹4 (buy disabled) | villager trade loops |
| Diamond | ₹30 | the main hand-mined reward |
| Netherite scrap | sells ₹25, costs ₹40,000 | rarest renewable-by-effort item, see Premium items |
| Enchanted books, potions, spawners, spawn eggs, command blocks and other admin items | not traded | infinite, NBT-based or admin-only |

Examples: iron ingot sells ₹2 and costs ₹6. A diamond pickaxe is made from 3 diamonds and 2 sticks, so it sells for at most about ₹85 and costs ₹4,800 (tools have their own price ladder, see below). A cobblestone stack sells for ₹3.20.

## Premium items: very expensive to buy, cheap to sell
Rare things are listed in the `premium:` section of `base-prices.yml` with their own sell and buy price. The shop pays little for them and charges a lot, so nobody can fund a purchase by selling a few of them.

| Item | Sells for | Costs |
|---|---|---|
| Nether star | ₹120 | ₹100,000 |
| Heart of the sea | ₹60 | ₹50,000 |
| Enchanted golden apple | ₹40 | ₹40,000 |
| Netherite scrap / ancient debris | ₹25 | ₹40,000 |
| Shulker shell | ₹12 | ₹6,000 |
| Nautilus shell, echo shard | ₹12 / ₹8 | ₹4,000 / ₹4,000 |
| Saddle, netherite upgrade template, music discs, pottery sherds, horse armour | a few rupees | ₹300 to ₹3,500 (horse armour) |

Anything crafted from a premium item can never cost less than its parts, so a beacon costs more than its nether star, a conduit more than its heart of the sea, and a netherite pickaxe about ₹168,000 (it needs an ingot, which is 4 netherite scrap). **Rare & Special** in the shop is a fixed list of trophies and collectibles (nether star, shulker shells and boxes, music discs, pottery sherds, banner patterns, trims, heads, totems...), not everything that happens to be dear: diamond armour, netherite ingots or a brewing stand stay with their own family.

### Sell-only items: the shop pays, but never sells
These items are in the `no-buy:` list, so nobody can buy them, and they have a sell price in `premium:` that follows effort and rarity. It is good money, but the hourly and daily limits still apply, so none of them can be farmed into a fortune.

| Item | Sells for | Where it comes from |
|---|---|---|
| Dragon egg | ₹2,500 | one per world |
| Dragon head | ₹1,000 | end city ships |
| Elytra | ₹900 | end city ships |
| Heavy core | ₹500 | ominous vaults |
| Mace | about ₹478 | made from a heavy core (never for sale) |
| Silence armour trim | ₹400 | ancient city, rarest trim |
| Wither skeleton skull | ₹200 | wither skeletons, rare drop |
| Beacon | about ₹117 | made from a nether star (never for sale) |
| Zombie, creeper, skeleton and piglin heads | ₹20 | mob drops (charged creeper, skeleton kills) |
| Trident | ₹250 | drowned |
| Rarer trims (bolt, eye, flow, host, raiser, shaper, spire, vex, ward, wayfinder) | ₹200 | structures, often one chance |
| Other armour trims | ₹120 | structures |
| Totem of undying | ₹150 | evokers |
| End crystal | ₹100 | needs a ghast tear |

Ghast tears are sell-only too (₹5): otherwise a player could buy a tear and craft an end crystal, which is a way round the ban. For the same reason anything that needs one is not for sale (dried ghast). The netherite upgrade template can still be bought, because netherite gear needs it; it cannot be sold.

## Mob drops that are very dear to buy
Blaze rod ₹3,000, breeze rod ₹5,000, leather ₹150 and slime ball ₹200 have a high buy price in `premium:`, while the shop still pays the old low price (₹3, ₹3, ₹0.60, ₹1). Anything made from them can never cost less than its parts, so blaze powder is ₹1,500, magma cream ₹1,700, a wind charge ₹1,250, a slime block ₹1,800, a brewing stand about ₹3,000, a leather chestplate ₹1,200 and a book ₹151.

## Stage 2: hard to get, so very dear
Items that are not legendary but take real effort are priced at 100 times their sell price or more: the shop pays a few rupees and charges hundreds or thousands. Name tag ₹3,000, ender pearl ₹600 (an eye of ender about ₹2,100), rabbit's foot ₹600, sponge ₹1,500, crying obsidian ₹500, spore blossom ₹300, prismarine shard ₹60 and crystals ₹150, glow ink sac ₹160, bell ₹3,000, phantom membrane ₹400, scutes ₹200 and ₹500, music discs ₹5,000, pottery sherds ₹2,000, banner patterns ₹2,000 (Mojang ₹6,000), turtle egg and bee nest ₹1,500, wither rose ₹3,000, dragon's breath ₹1,500, sculk catalyst and shrieker ₹1,500, froglights ₹600. Things made from them cost at least their parts, so a sea lantern is about ₹990 and a respawn anchor about ₹3,000. The name tag is crafted from a nugget and paper in this game version, so what the shop pays for one is pushed down to the recipe cost automatically.

## Armour: the same ladder as tools
Armour has a fixed buy price per unit of material (helmet 5 units, chestplate 8, leggings 7, boots 4): copper ₹70, chainmail ₹240, iron ₹180, gold ₹300, diamond ₹1,600. An iron chestplate costs ₹1,440, a gold one ₹2,400 and a diamond one ₹12,800. Netherite armour is a diamond piece plus an ingot plus the template, so it costs about ₹170,000 to ₹176,000. Leather armour follows the leather price. Selling is unchanged.

## Only in the world: bought, never sold
Water, lava and powder snow buckets, fish buckets, tadpole and axolotl buckets and bottles o' enchanting have a buy price (₹25 to ₹3,000) and cannot be sold back.

## Tools: a fixed ladder
Wooden to diamond tools have a fixed buy price in `premium:`, worked out as a price per unit of material: wood ₹10, stone ₹25, copper ₹70, iron ₹180, golden ₹300, diamond ₹1,600. A pickaxe or axe is 3 units, a hoe or sword 2, a shovel or spear 1. So a stone pickaxe costs ₹75, an iron pickaxe ₹540 and a diamond pickaxe ₹4,800. The sell price is unchanged (at most 95% of the ingredients), so selling a tool never pays more than before.

Netherite tools have no line of their own. They are a diamond tool plus a netherite ingot plus the upgrade template, so they cost about ₹164,000 to ₹168,000, and they follow the scrap price automatically.

## The loop check (why buy and sell cannot be farmed)
After all prices are worked out, the engine looks at **every** recipe, not only the ones it used for pricing: crafting, un-crafting (block back to ingots), smelting, stonecutting, smithing (netherite), and the mining yields in `drops:` (an ore block gives up to 7.7 raw copper with Fortune III). For every item it finds the cheapest way to obtain it by buying and processing, across any number of steps. If an item would sell for more than 95% of that, its **sell price is lowered** until it cannot. Buy prices are never lowered.

So a wrong number in `base-prices.yml`, or a new recipe in a game update, can only make an item sell for less. It cannot create an endless buy, process, sell money loop. The server log lists any item that was lowered.

Two more rules: an item can never be bought for less than buying its ingredients one by one, and every item costs at least 3x what it sells for unless it is a premium item.

What the check cannot cover is effort that has no price: farming a crop you planted from one seed, or a mob farm. That is what the per-item hourly cap, the daily cap and the minimum play time are for.

## What is not in the shop
Admin and unobtainable items are left out on purpose: command blocks, structure blocks, jigsaw, barrier, light, bedrock, end portal frames, reinforced deepslate, spawners, spawn eggs, infested blocks, budding amethyst, the dragon egg, debug stick, potions and enchanted books. Emeralds can be sold but not bought, so villager trading cannot be skipped. An item with no entry in `base-prices.yml` and no recipe is simply not traded.

Items that a newer game version adds and that are not in the file yet are priced from their recipes automatically (and not traded at all when they have no recipe). Your own `base-prices.yml` is never overwritten on update, so to pick up the complete list of a new release on an existing server, rename it and restart to get the new default, then copy your changes back.

## Known exploit routes and the fix
| Route | Fix |
|---|---|
| Buy cheap, sell dear | buy = 3x sell |
| Craft up in value | 95% rule on every recipe, re-checked across all recipes by the loop check |
| Buy parts, craft, sell | a crafted item never costs less than its parts; sell price is capped below the cheapest way to get it |
| Buy an ore block and mine it with Fortune | `drops:` section, so ore prices stay above what the ore yields |
| Buy a rare item cheaply through a recipe | premium prices carry through every recipe that uses them |
| Compact then un-compact | 9 ingots = 1 block less 5%; the reverse recipe returns exactly 9 ingots |
| Villager loops (wheat for emeralds) | emerald ₹4 sells lower than 22 wheat; emerald cannot be bought |
| Iron/gold/mob farms | per-item hourly cap |
| Many alt accounts | min play hours, per-account daily cap, Gatekeeper account limit |
| Sell items that hold others (shulkers, bundles) | refused |
| Sell damaged or enchanted tools | refused |
| Free infinite items (water/milk buckets, leaves, rotten flesh) | not sellable |
| Dupe glitches | selling takes the items out first; purchases are logged; Anti-Dupe plugin stays on |
| Rounding tricks (selling 1 at a time) | prices are stored in paise and rounded down per sale |

## Tuning
After a week of play, check `/arkaeco log`. If players hit the daily cap too often, raise `daily-cap-rupees`; if money piles up, lower `item-hour-cap-rupees` first, then add a sell tax.
