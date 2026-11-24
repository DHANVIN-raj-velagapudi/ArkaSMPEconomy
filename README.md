# ArkaSMPEconomy

**A complete rupee economy for an SMP: balances, `/pay`, `/baltop`, hourly rewards, safe player-to-player trading and an item shop with a sell chest, all with tap-only menus.**

> **Why this exists.** We run a survival server (Arka SMP) and wanted one clean, safe economy instead of an
> economy plugin plus a shop plugin plus a trade plugin, each with different rules and exploits. So we built a
> single plugin where **prices are calculated by rules** (no money loops), trading cannot be scammed, and
> everything works from menus, which matters because many of our players are on **phones and consoles through
> Geyser/Floodgate**. It works well for us, so we are sharing it with all of you. It is free software (AGPL-3.0).

It is one of three plugins we built together as a suite:

| Plugin | What it does |
|---|---|
| [ArkaSMPCore](https://github.com/DHANVIN-raj-velagapudi/ArkaSMPCore) | Homes, spawn, TPA, RTP, warps, claims, AFK, leaderboards, stats, cleaner |
| **ArkaSMPEconomy** (this repo) | Rupee money, `/pay`, `/baltop`, `/trade`, item shop, `/sellgui` |
| [McBackup](https://github.com/DHANVIN-raj-velagapudi/McBackup) | Incremental hourly/daily/weekly backups to a cloud bucket |

This plugin **requires ArkaSMPCore** (it uses its menu system and shares services with it).

---

## Contents

1. [Features](#features)
2. [Requirements](#requirements)
3. [Installation](#installation)
4. [Player commands](#player-commands)
5. [Admin commands](#admin-commands)
6. [Permissions](#permissions)
7. [How the shop prices work](#how-the-shop-prices-work)
8. [Configuration guide](#configuration-guide)
9. [Placeholders (PlaceholderAPI)](#placeholders-placeholderapi)
10. [Moving from Essentials](#moving-from-essentials)
11. [Working with ArkaSMPCore](#working-with-arkasmpcore)
12. [Status and known limits](#status-and-known-limits)
13. [Building from source](#building-from-source)
14. [Contributing and support](#contributing-and-support)
15. [License](#license)

---

## Features

### Money
- Currency is **rupees** (symbol and amounts configurable). Balances are stored as whole paise in a database, so
  there are **no floating-point errors**
- `/bal`, `/pay` (with a confirmation for large payments, a payment cooldown and a tap-only amount menu),
  `/baltop`, `/paytoggle`
- **Hourly play-time reward**: a fixed amount for each hour of *active* (non-AFK) play, with a daily cap so alt
  accounts cannot farm it
- Every change is written to a **transaction log** that admins can inspect
- Safe storage: a dedicated writer thread (SQLite WAL), a global lock around transfers, and a hard balance limit

### Safe trading (`/trade`)
- A trade window with item offers and rupee offers on both sides, a **Ready** button for each player and a
  countdown before the swap, any change un-readies both players
- Blocked items list (command blocks, spawners, bedrock, ...) and no trading while in combat, AFK or creative
- A warning when the offer is **very lopsided** by shop value (scam protection)
- Block individual players, toggle requests off, and see your trade history
- Items are returned if anything goes wrong

### Item shop
- `/shop` is a menu of categories (building blocks, ores, tools, food, farming, ...) to **buy and sell**
- `/sell hand` and `/sell all`, `/worth` (what an item costs and sells for)
- **`/sellgui`**: a chest opens, you drop in everything you want to sell and press one SELL button
- Only plain, undamaged items are accepted (no enchanted, renamed or container items)
- **Anti-farm limits**: a per-item hourly earnings cap, a daily cap, and a minimum active play time before a player
  may sell (stops fresh alt accounts)
- Big purchases and large `/sell all` actions ask for confirmation

### Admin tools
- `/arkaeco` to give, take, set, reset, freeze accounts, read the log and **import balances from Essentials**
- `/shopadmin` to change single prices, disable items, reset them, and audit the price table

---

## Requirements

| | |
|---|---|
| Server | **Paper** (or a Paper fork), built against the 1.21.4 API (`api-version: 1.21`) |
| Java | 21 or newer |
| Required plugin | [ArkaSMPCore](https://github.com/DHANVIN-raj-velagapudi/ArkaSMPCore) |
| Optional | PlaceholderAPI (placeholders), Geyser + Floodgate (Bedrock players) |

---

## Installation

1. Install **ArkaSMPCore** first.
2. Put `ArkaSMPEconomy-1.0.0.jar` in `plugins/` next to it and start the server once.
3. Edit `plugins/ArkaSMPEconomy/config.yml` (money, rewards, trade, shop settings) and, if you like,
   `base-prices.yml` (shop prices).
4. Run `/arkaeco reload`.
5. Coming from Essentials? Read [Moving from Essentials](#moving-from-essentials) before you start.

New players receive the configured start balance (default ₹1000) the first time they join.

---

## Player commands

No permission is needed for any of these.

| Command | What it does |
|---|---|
| `/bal [player]` | Show a balance (aliases `/balance`, `/money`) |
| `/pay [player] [amount]` | Pay someone. `/pay` alone opens the tap-only menu |
| `/baltop [page]` | The richest players (aliases `/balancetop`, `/topmoney`) |
| `/paytoggle` | Allow or block incoming payments |
| `/trade <player>` | Start a trade. Also `accept`, `deny`, `cancel`, `toggle`, `block <player>`, `unblock <player>`, `history` |
| `/shop` | Open the shop (aliases `/buy`, `/store`) |
| `/sell hand` / `/sell all` | Sell the item in your hand, or everything sellable |
| `/sellgui` | Open the sell chest (aliases `/sg`, `/sellchest`) |
| `/worth [item]` | What an item sells for and costs (alias `/price`) |

---

## Admin commands

### `/arkaeco` (alias `/aeco`), permission `arkaeco.admin`

| Command | What it does |
|---|---|
| `/arkaeco give <player> <amount>` | Add money |
| `/arkaeco take <player> <amount>` | Remove money |
| `/arkaeco set <player> <amount>` | Set a balance |
| `/arkaeco reset <player>` | Reset to the start balance |
| `/arkaeco balance <player>` | Show a balance, even for offline players |
| `/arkaeco freeze <player>` / `unfreeze <player>` | Freeze an account (no paying, trading or rewards) |
| `/arkaeco log [player]` | Read the transaction log |
| `/arkaeco import essentials` | Dry run: shows what would be imported. Add `confirm` to apply, or `force` to import again and overwrite |
| `/arkaeco reload` | Reload the config |
| `/arkaeco flush`, `resume` | Flush the database to disk / return to normal. [McBackup](https://github.com/DHANVIN-raj-velagapudi/McBackup) uses these around a backup |
| `/arkaeco help` | List them in game |

### `/shopadmin`, permission `arkaeco.shop.admin`

| Command | What it does |
|---|---|
| `/shopadmin set <item> <sell> [buy]` | Set a price (saved in `price-overrides.yml`) |
| `/shopadmin disable <item> [sell\|buy\|both]` | Stop an item being sold, bought or both |
| `/shopadmin reset <item>` | Back to the calculated price |
| `/shopadmin info <item>` | Show the current sell and buy price and the category |
| `/shopadmin audit` | Check the price table for loops or mistakes |
| `/shopadmin reload` | Rebuild the price table |

---

## Permissions

| Permission | Default | Meaning |
|---|---|---|
| `arkaeco.admin` | op | All `/arkaeco` admin commands (or give single ones below) |
| `arkaeco.admin.give` / `take` / `set` / `reset` / `freeze` / `log` / `import` / `reload` / `balance` | false | One admin action each |
| `arkaeco.shop.admin` | op | `/shopadmin` |
| `arkaeco.shop.bypass` | false | Skip the minimum play time before selling |
| `arkaeco.trade.bypass` | false | Skip trade safety checks (AFK / combat / creative) |
| `arkaeco.baltop.hide` | false | Hide a player from `/baltop` |

Players need **no** permissions for normal use.

---

## How the shop prices work

Only about 190 prices are chosen by hand (`base-prices.yml`). **Everything else is calculated from crafting
recipes**, so no crafting loop can print money.

1. **Buy price is 3x the sell price**, so buying then selling the same item always loses two thirds.
2. **A crafted item sells for at most 95% of its ingredients**, using the cheapest recipe.
3. **Smelted or cooked items sell for the same as the raw item.**
4. **Items with no price and no recipe path are not for sale** (potions, spawn eggs, spawners, command blocks, heads).
5. **Restrictions are inherited**: anything crafted from a not-for-sale item is also not for sale.

Anti-farm limits per player (all configurable): earnings per item per hour, a daily total, and a minimum number of
active play hours before selling. The full explanation with default numbers is in [PRICING.md](PRICING.md).

---

## Configuration guide

`plugins/ArkaSMPEconomy/config.yml` (reload with `/arkaeco reload`):

| Section | What you can change |
|---|---|
| `currency` | The currency symbol |
| `economy` | Start balance, maximum balance |
| `pay` | Minimum, maximum, confirmation threshold, cooldown, offline payments, menu amounts |
| `baltop` | Page size, refresh time |
| `rewards.playtime` | Reward amount, active minutes per reward, daily cap |
| `trade` | Timeouts, countdown, lopsided warning, blocked items, which states block trading |
| `shop` | Maximum purchase size, confirmation threshold |
| `log` | How many days of transactions to keep |
| `menus`, `messages` | Titles, shop category names and every message (MiniMessage colours) |

`base-prices.yml` holds hand-picked prices, the buy multiplier, the craft factor and the sell limits.

---

## Placeholders (PlaceholderAPI)

Identifier: `arkaeco`.

| Placeholder | Value |
|---|---|
| `%arkaeco_balance%` | Plain number, e.g. `1250.5` |
| `%arkaeco_balance_formatted%` | e.g. `₹1,250.50` |
| `%arkaeco_rank%` | Your `/baltop` position |
| `%arkaeco_top_<n>_name%`, `_value%`, `_raw%` | Player, formatted balance and plain number at rank `n` |

---

## Moving from Essentials

1. Back up your server (and `plugins/Essentials/userdata`).
2. Install ArkaSMPCore and ArkaSMPEconomy, start the server, then run `/arkaeco import essentials`. This is a
   dry run that shows what would happen. Run `/arkaeco import essentials confirm` to copy the balances over.
3. Stop Essentials from handling the same commands. In Essentials' `config.yml`, add the money commands to
   `disabled-commands`, for example `balance`, `bal`, `money`, `pay`, `baltop`, `balancetop`, `eco`, `sell`,
   `worth`. Restart the server.
4. If you have another plugin that provides the Vault economy, remove it so only one economy answers.

---

## Working with ArkaSMPCore

ArkaSMPEconomy publishes two services that ArkaSMPCore picks up automatically:

- **`MoneyProvider`**: read balances (used for the money leaderboard)
- **`MoneyCharger`**: charge rupees (used when a player buys claim blocks or creates a paid player warp)

Without this plugin, ArkaSMPCore still works. The paid features simply stay unavailable.

---

## Status and known limits

We built and tested this for our own server, and we are releasing it as it is. It is **young software**:

- Test on a **copy of your server** first, and keep backups (see [McBackup](https://github.com/DHANVIN-raj-velagapudi/McBackup)).
- Known limits: items inside an open `/sellgui` are lost if the server crashes at that moment; cancelling a trade
  waits for the database to flush.
- Prices are tuned for our server. Check `/shopadmin audit` and adjust to the economy you want.
- Bugs are possible. Please report them (below).

---

## Building from source

Requires JDK 21+ and Maven. Build and install **ArkaSMPCore** first (`mvn -q -B install` in its folder), then:

```
mvn -q -B package
```

The jar is written to `target/ArkaSMPEconomy-1.0.0.jar`.

---

## Contributing and support

- **Found a bug or want a feature?** Open an [issue](../../issues) with your server version, what you did and
  what happened (include the console error if there is one).
- **Want to improve it?** Fork it and send a pull request. Keep changes small and focused.
- Please keep the license: if you build on this and run it publicly, your changes must stay open too.

---

## License

Copyright (C) 2026 Velagapudi Dhanvin Raj

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU Affero General Public License, version 3** as published by the Free Software Foundation. It is distributed in
the hope that it will be useful, but **without any warranty**. See [LICENSE](LICENSE).

If you run a modified version on a server that other people connect to, the AGPL requires you to offer those
players the source code of your modified version.
