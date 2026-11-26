package com.arka.smp.economy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the shop categories on the real list of Minecraft 26.3 items (src/test/resources/items-26.3.txt), no server
 * needed. Every item has exactly one category, and whole families (every spear, every stripped log, every copper
 * block...) are in the same place. Run: java -cp target/classes;target/test-classes;... com.arka.smp.economy.ShopCategoriesCheck
 */
public final class ShopCategoriesCheck {
    static int failed = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failed++;
    }

    static List<String> lines(String resource) throws Exception {
        try (InputStream in = ShopCategoriesCheck.class.getResourceAsStream("/" + resource)) {
            if (in == null) throw new IllegalStateException("missing test resource " + resource);
            List<String> out = new ArrayList<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) if (!l.isBlank()) out.add(l.trim());
            return out;
        }
    }

    /** Fails with the names that broke the rule (at most 8 are printed). */
    static void family(String what, List<String> items, Set<String> edible, java.util.function.Predicate<String> in, String... allowed) {
        Set<String> ok = Set.of(allowed);
        List<String> bad = new ArrayList<>();
        int count = 0;
        for (String n : items) {
            if (!in.test(n)) continue;
            count++;
            String c = ShopCategories.of(n, edible.contains(n));
            if (!ok.contains(c)) bad.add(n + "->" + c);
        }
        check(what + " (" + count + " items)" + (bad.isEmpty() ? "" : "  WRONG: " + bad.subList(0, Math.min(8, bad.size()))),
                bad.isEmpty() && count > 0);
    }

    public static void main(String[] args) throws Exception {
        List<String> items = lines("items-26.3.txt");
        Set<String> edible = new HashSet<>(lines("edible-26.3.txt"));
        ShopCategories.learnWoods(items);

        // every item gets exactly one existing category, always the same one
        Map<String, Integer> sizes = new HashMap<>();
        boolean valid = true;
        for (String n : items) {
            String c = ShopCategories.of(n, edible.contains(n));
            if (!ShopCategories.exists(c) || !c.equals(ShopCategories.of(n, edible.contains(n)))) valid = false;
            sizes.merge(c, 1, Integer::sum);
        }
        check("every one of " + items.size() + " items has one valid category", valid);
        List<String> ids = new ArrayList<>();
        for (ShopCategories.Cat c : ShopCategories.ALL) ids.add(c.id());
        check("15 categories fit the menu (3 rows of 7 at most)", ids.size() <= 21);
        check("no category is empty " + sizes, sizes.keySet().containsAll(ids));
        check("utility & decoration stays small (" + sizes.get("misc") + ")", sizes.get("misc") <= 120);

        // gear
        family("every tool and weapon is in Tools", items, edible,
                n -> n.matches(".*_(PICKAXE|AXE|SHOVEL|HOE|SWORD|SPEAR)"), "tools");
        family("bows, tridents, mace, shears, fishing rod, arrows", items, edible,
                n -> Set.of("BOW", "CROSSBOW", "TRIDENT", "MACE", "SHEARS", "FISHING_ROD", "ARROW", "SPECTRAL_ARROW", "BRUSH").contains(n), "tools");
        family("all buckets are in Tools", items, edible, n -> n.endsWith("_BUCKET") || n.equals("BUCKET"), "tools");
        family("all armour pieces are in Armor & Mounts", items, edible,
                n -> n.matches(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)"), "armor");
        family("horse, nautilus and wolf armour, harnesses, saddle, lead, name tag", items, edible,
                n -> n.endsWith("_HORSE_ARMOR") || n.endsWith("_NAUTILUS_ARMOR") || n.endsWith("_HARNESS")
                        || Set.of("WOLF_ARMOR", "SADDLE", "LEAD", "NAME_TAG", "SHIELD", "ELYTRA").contains(n), "armor");

        // wood
        family("every stripped log, wood, stem and hyphae is in Wood", items, edible,
                n -> n.startsWith("STRIPPED_"), "wood");
        family("planks, logs, stairs and boats of every wood", items, edible,
                n -> n.matches("(OAK|SPRUCE|BIRCH|JUNGLE|ACACIA|DARK_OAK|MANGROVE|CHERRY|PALE_OAK|CRIMSON|WARPED|POPLAR)_(PLANKS|LOG|WOOD|STEM|HYPHAE|STAIRS|SLAB|FENCE|FENCE_GATE|DOOR|TRAPDOOR|BOAT|CHEST_BOAT|SIGN|HANGING_SIGN|BUTTON|PRESSURE_PLATE)"),
                "wood");
        family("leaves, saplings and propagules are in Farming (or Nether for fungus)", items, edible,
                n -> n.endsWith("_LEAVES") || n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE"), "farming");

        // copper (the metal itself stays with the ores)
        family("every copper block, door, bar, lantern, bulb and statue is in Copper", items, edible,
                n -> n.contains("COPPER") && !n.endsWith("_INGOT") && !n.endsWith("_NUGGET") && !n.endsWith("_ORE")
                        && !n.startsWith("RAW_") && !n.matches("COPPER_(PICKAXE|AXE|SHOVEL|HOE|SWORD|SPEAR|HELMET|CHESTPLATE|LEGGINGS|BOOTS|HORSE_ARMOR|NAUTILUS_ARMOR)"),
                "copper");
        family("waxed and weathered copper, lightning rods", items, edible,
                n -> n.startsWith("WAXED_") || n.startsWith("EXPOSED_") || n.startsWith("WEATHERED_") || n.startsWith("OXIDIZED_")
                        || n.endsWith("LIGHTNING_ROD"), "copper");
        family("copper ingot, nugget, raw copper and ore are in Ores", items, edible,
                n -> n.equals("COPPER_INGOT") || n.equals("COPPER_NUGGET") || n.equals("RAW_COPPER") || n.equals("COPPER_ORE")
                        || n.equals("DEEPSLATE_COPPER_ORE") || n.equals("RAW_COPPER_BLOCK"), "ores");

        // ores
        family("every ore, ingot, nugget and raw metal is in Ores & Metals", items, edible,
                n -> n.endsWith("_ORE") || n.endsWith("_INGOT") || n.endsWith("_NUGGET") || n.startsWith("RAW_"), "ores");
        family("netherite scrap, ingot, block and ancient debris are in Ores & Metals", items, edible,
                n -> Set.of("NETHERITE_SCRAP", "NETHERITE_INGOT", "NETHERITE_BLOCK", "ANCIENT_DEBRIS").contains(n), "ores");

        // colours
        family("wool, carpet, beds, banners, concrete, terracotta, stained glass, candles, dyes", items, edible,
                n -> n.matches("(WHITE|ORANGE|MAGENTA|LIGHT_BLUE|YELLOW|LIME|PINK|GRAY|LIGHT_GRAY|CYAN|PURPLE|BLUE|BROWN|GREEN|RED|BLACK)_(WOOL|CARPET|BED|BANNER|CONCRETE|CONCRETE_POWDER|TERRACOTTA|GLAZED_TERRACOTTA|STAINED_GLASS|STAINED_GLASS_PANE|CANDLE|DYE)"),
                "colors");
        family("shulker boxes are Rare & Special, all together", items, edible, n -> n.endsWith("SHULKER_BOX"), "rare");

        // rare collectibles and mob drops
        family("music discs, pottery sherds, banner patterns, armour trims", items, edible,
                n -> n.startsWith("MUSIC_DISC_") || n.endsWith("_POTTERY_SHERD") || n.endsWith("_BANNER_PATTERN")
                        || n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE"), "rare");
        family("blaze rod, ender pearl, slime ball, leather, string, bone, scutes are Mob Drops", items, edible,
                n -> Set.of("BLAZE_ROD", "BLAZE_POWDER", "ENDER_PEARL", "SLIME_BALL", "LEATHER", "STRING", "BONE", "GUNPOWDER",
                        "SPIDER_EYE", "PHANTOM_MEMBRANE", "TURTLE_SCUTE", "ARMADILLO_SCUTE", "FEATHER", "INK_SAC").contains(n), "mobs");
        family("every egg a mob lays is in Mob Drops (the rare eggs aside)", items, edible,
                n -> n.endsWith("_EGG") && !n.equals("SNIFFER_EGG") && !n.equals("DRAGON_EGG") && !n.endsWith("SPAWN_EGG"), "mobs");

        // food and farming
        family("edible items are Food (or a mob drop, Rare or Nether)", items, edible,
                n -> edible.contains(n), "food", "mobs", "rare", "nether");
        family("seeds are in Farming", items, edible, n -> n.endsWith("_SEEDS"), "farming");
        family("flowers and corals are in Farming", items, edible,
                n -> n.endsWith("_TULIP") || n.contains("CORAL") || Set.of("DANDELION", "POPPY", "ALLIUM", "CORNFLOWER", "PEONY", "LILAC").contains(n),
                "farming");

        // stone, nether, redstone, blocks
        family("cobblestone, deepslate, bricks, sandstone, quartz blocks, purpur are in Stone & Bricks", items, edible,
                n -> n.contains("COBBLE") || n.contains("DEEPSLATE") && !n.endsWith("_ORE") || n.contains("SANDSTONE")
                        || n.endsWith("STONE_BRICKS") || n.startsWith("PURPUR"), "stone");
        family("netherrack, soul sand, glowstone, sculk, chorus, obsidian are in Nether, End & Deep Dark", items, edible,
                n -> Set.of("NETHERRACK", "SOUL_SAND", "GLOWSTONE", "SCULK", "CHORUS_FLOWER", "OBSIDIAN", "CRYING_OBSIDIAN", "END_STONE", "SHROOMLIGHT").contains(n),
                "nether");
        family("pistons, rails, minecarts, repeaters, hoppers, TNT are in Redstone & Transport", items, edible,
                n -> n.endsWith("_PISTON") || n.equals("PISTON") || n.endsWith("RAIL") || n.endsWith("MINECART") || n.equals("REPEATER")
                        || n.equals("HOPPER") || n.equals("TNT") || n.equals("OBSERVER") || n.equals("COMPARATOR"), "redstone");
        family("dirt, sand, gravel, clay, ice and snow are Building Blocks", items, edible,
                n -> Set.of("DIRT", "SAND", "GRAVEL", "CLAY", "ICE", "PACKED_ICE", "SNOW_BLOCK", "MUD", "PODZOL").contains(n), "blocks");
        family("chests, furnaces, workstations, torches and lanterns are Utility & Decoration", items, edible,
                n -> Set.of("CHEST", "TRAPPED_CHEST", "FURNACE", "BLAST_FURNACE", "SMOKER", "CRAFTING_TABLE", "ANVIL", "ENCHANTING_TABLE",
                        "BREWING_STAND", "TORCH", "LANTERN", "BARREL", "LOOM", "LECTERN", "BELL", "LODESTONE", "PAINTING", "LADDER").contains(n),
                "misc");

        // the list that belongs to the server owner
        check("category ids are unique", new HashSet<>(ids).size() == ids.size());
        System.out.println(failed == 0 ? "ALL PASSED" : failed + " FAILED");
        if (failed != 0) System.exit(1);
    }
}
