# Changelog

All notable changes to ArkaSMPEconomy. Versions follow `MAJOR.MINOR.PATCH`: a fix raises the last number, a new
feature the middle one, a breaking change the first. Every release raises the version.

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
