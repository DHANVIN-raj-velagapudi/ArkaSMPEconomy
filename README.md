# ArkaSMPEconomy

A rupee economy and item shop for **Paper 1.21+** (Java 21+), built to run next to
[ArkaSMPCore](https://github.com/DHANVIN-raj-velagapudi/ArkaSMPCore). It replaces Essentials' economy and a shop
plugin with one lightweight plugin and tap-only menus (Bedrock friendly).

## Features

- Balances stored as whole paise (no floating-point errors), SQLite with a single writer thread
- `/bal`, `/pay`, `/baltop`, an hourly play-time reward and `/trade` (a safe two-player trade window)
- **Shop**: `/shop` (buy and sell menus), `/sell`, `/sellgui` (a chest you drop items into and sell in one click),
  `/worth`, and `/shopadmin` for administration
- **Price engine**: prices are derived from craft recipes and base prices ([PRICING.md](PRICING.md)); selling has
  hourly and daily per-item limits and a minimum play time, to stop farms and alt abuse
- Provides the `MoneyProvider` and `MoneyCharger` services that ArkaSMPCore uses for warp costs and claim blocks
- PlaceholderAPI expansion (`%arkaeco_...%`)

## Requirements

- Paper (or a Paper fork) 1.21.4 or newer, Java 21+
- [ArkaSMPCore](https://github.com/DHANVIN-raj-velagapudi/ArkaSMPCore) installed on the server
- Optional: PlaceholderAPI

## Build

Build and install ArkaSMPCore first (`mvn -q -B install` in its folder), then:

```
mvn -q -B package
```

The jar is written to `target/ArkaSMPEconomy-1.0.0.jar`.

## Install

1. Put the jar next to ArkaSMPCore in `plugins/` and start the server once.
2. Edit `plugins/ArkaSMPEconomy/config.yml` and `base-prices.yml` to taste.
3. If you used Essentials' economy before, disable its economy commands (see its `commands.yml` / `disabled-commands`)
   so `/bal` and `/pay` are not handled twice.

## License

Copyright (C) 2026 Velagapudi Dhanvin Raj

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU Affero General Public License, version 3** as published by the Free Software Foundation. It is distributed in
the hope that it will be useful, but **without any warranty**. See [LICENSE](LICENSE).

If you run a modified version on a server that other people connect to, the AGPL requires you to offer those
players the source code of your modified version.
