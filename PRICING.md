# ArkaSMPEconomy Stage 2: how prices are decided

Only about 190 prices are hand-chosen (`src/main/resources/base-prices.yml`). Everything else is calculated, so no loop can make money.

## The five rules
1. **Buy is 3x sell.** Buying then selling the same item always loses two thirds.
2. **Crafted sell price is at most 95% of its ingredients.** For every recipe the engine checks `sell(output) x amount <= sum(sell(ingredients)) x 0.95`. If an item has several recipes, the cheapest one wins. Compacting ingots into blocks and back can never gain.
3. **Smelted/cooked sell price equals the raw item.** Smelting is free of profit and free of penalty.
4. **Unpriced means off.** An item with no base price and no recipe path (potions, spawn eggs, spawners, command blocks, heads) cannot be bought or sold.
5. **Inherited restrictions.** If an item is on the no-buy list, anything crafted from it is also not for sale. That blocks netherite gear and beacons being bought "through the back door".

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
| Netherite scrap | sells ₹25, costs ₹4,500 | rarest renewable-by-effort item, see Premium items |
| Enchanted books, potions, spawners, spawn eggs, command blocks and other admin items | not traded | infinite, NBT-based or admin-only |

Examples: iron ingot sells ₹2 and costs ₹6. A diamond pickaxe is made from 3 diamonds and 2 sticks, so it sells for at most about ₹85 and costs about ₹255. A cobblestone stack sells for ₹3.20.

## Premium items: very expensive to buy, cheap to sell
Rare things are listed in the `premium:` section of `base-prices.yml` with their own sell and buy price. The shop pays little for them and charges a lot, so nobody can fund a purchase by selling a few of them.

| Item | Sells for | Costs |
|---|---|---|
| Elytra | ₹150 | ₹90,000 |
| Nether star | ₹120 | ₹100,000 |
| Totem of undying | ₹30 | ₹30,000 |
| End crystal | ₹20 | ₹20,000 |
| Trident | ₹40 | ₹40,000 |
| Heart of the sea | ₹60 | ₹50,000 |
| Enchanted golden apple | ₹40 | ₹40,000 |
| Heavy core | ₹30 | ₹25,000 |
| Wither skeleton skull | ₹10 | ₹30,000 |
| Netherite scrap / ancient debris | ₹25 | ₹4,500 |
| Shulker shell | ₹12 | ₹6,000 |
| Nautilus shell, echo shard, ghast tear | ₹12 / ₹8 / ₹5 | ₹4,000 / ₹4,000 / ₹3,500 |
| Saddle, smithing templates, music discs, pottery sherds, horse armour | a few rupees | ₹150 to ₹3,000 |

Anything crafted from a premium item can never cost less than its parts, so a beacon costs more than its nether star, a conduit more than its heart of the sea, and a netherite pickaxe over ₹21,000. Everything that costs ₹1,500 or more is listed under **Rare & Special** in the shop.

## The loop check (why buy and sell cannot be farmed)
After all prices are worked out, the engine looks at **every** recipe, not only the ones it used for pricing: crafting, un-crafting (block back to ingots), smelting, stonecutting, smithing (netherite), and the mining yields in `drops:` (an ore block gives up to 7.7 raw copper with Fortune III). For every item it finds the cheapest way to obtain it by buying and processing, across any number of steps. If an item would sell for more than 95% of that, its **sell price is lowered** until it cannot. Buy prices are never lowered.

So a wrong number in `base-prices.yml`, or a new recipe in a game update, can only make an item sell for less. It cannot create an endless buy, process, sell money loop. The server log lists any item that was lowered.

Two more rules: an item can never be bought for less than buying its ingredients one by one, and every item costs at least 3x what it sells for unless it is a premium item.

What the check cannot cover is effort that has no price: farming a crop you planted from one seed, or a mob farm. That is what the per-item hourly cap, the daily cap and the minimum play time are for.

## What is not in the shop
Admin and unobtainable items are left out on purpose: command blocks, structure blocks, jigsaw, barrier, light, bedrock, end portal frames, reinforced deepslate, spawners, spawn eggs, infested blocks, budding amethyst, the dragon egg, debug stick, potions and enchanted books. Emeralds can be sold but not bought, so villager trading cannot be skipped. An item with no entry in `base-prices.yml` and no recipe is simply not traded.

New game versions work without changes: wood families, wool stairs and slabs, copper tools and similar items are priced from their recipes, and name patterns such as `*_LEAVES`, `*_SAPLING` and `*CORAL*` cover new variants. Your own `base-prices.yml` is never overwritten on update, so to pick up new items on an existing server, rename it and restart to get the new default, then copy your changes back.

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
