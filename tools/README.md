# Price tools

These scripts rebuild the complete `base-prices.yml` and `PRICE_LIST.md` for a new Minecraft version. They are for
maintainers; servers do not need them.

The shop prices come from the game's own data, so nothing is guessed. You need the client jar of the version (for
example `.minecraft/versions/26.4/26.4.jar`), Python 3 and a JDK.

1. **Extract the game data** (only these folders are needed) into a new empty folder:
   `data/minecraft/recipe`, `data/minecraft/tags/item`, `assets/minecraft/items`.
2. **Convert the recipes**:
   `python -I tools/conv_recipes.py <extracted-folder> <work-folder>`
   This writes `items.txt` (every item) and `recipes.tsv` (every recipe, with tags resolved).
3. **Decide the raw prices**: edit `src/main/resources/base-prices.yml`. Put the new raw items (blocks you mine,
   plants, mob drops, anything without a recipe) under `base:`, rare items under `premium:`, and delete the old
   `crafted:` section. Items that stay unpriced are not traded.
4. **Work out the prices**: compile `tools/GenPrices.java` against the plugin classes and the Paper API (it lives in the
   package `com.arka.smp.economy`) and run it:
   `java ... com.arka.smp.economy.GenPrices base-prices.yml <work>/items.txt <work>/recipes.tsv <work>`
   It prints the loop-check notes and writes `unpriced.txt` (look through it: only admin items should remain),
   `prices.tsv` and `priced.tsv`. Copy `prices.tsv` to `prices_before.tsv` (it tells which items are crafted).
5. **Write the final file**:
   `python -I tools/make_full.py base-prices.yml new-base-prices.yml <work> PRICE_LIST.md`
   Then run GenPrices once more on the new file: it must print no loop-check notes.

Run `PriceEngineCheck` and `PriceArbitrageCheck` (in `src/test`) after any change to the engine.
