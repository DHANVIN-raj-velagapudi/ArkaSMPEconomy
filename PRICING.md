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
| Netherite scrap | ₹100 (buy disabled) | rarest renewable-by-effort item |
| Boss items, enchanted books, potions | not traded | infinite or NBT-based |

Examples: iron ingot sells ₹2 and costs ₹6. A diamond pickaxe is made from 3 diamonds and 2 sticks, so it sells for at most about ₹85 and costs about ₹255. A cobblestone stack sells for ₹3.20.

## Known exploit routes and the fix
| Route | Fix |
|---|---|
| Buy cheap, sell dear | buy = 3x sell |
| Craft up in value | 95% rule on every recipe |
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
After a week of play, check `/arkaeco log` and `/shopadmin top-earners`. If players hit the daily cap too often, raise `daily-cap-rupees`; if money piles up, lower `item-hour-cap-rupees` first, then add a sell tax.
