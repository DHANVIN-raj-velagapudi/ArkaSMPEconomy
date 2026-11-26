package com.arka.smp.economy;

import org.bukkit.Material;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sorts every item into exactly one shop category by its name. The rules run top to bottom and the first one that
 * matches wins, so an item can never be in two categories, and a whole family (all spears, all horse armour, every
 * stripped log, every copper block) always lands together. The order of {@link #ALL} is the order in the menu.
 *
 * The rules look only at the item name (plus "is it edible"), so they can be tested without a server and they also
 * work for items that a newer game version adds. Owners can move single items with shop-categories.yml.
 */
final class ShopCategories {
    private ShopCategories() {
    }

    /** The icon is a material name, looked up only when a menu is drawn, so the rules below work without a server. */
    record Cat(String id, String icon, String name) {
        Material material() {
            return Material.matchMaterial(icon) != null ? Material.matchMaterial(icon) : Material.CHEST;
        }
    }

    static final List<Cat> ALL = List.of(
            new Cat("blocks", "GRASS_BLOCK", "Building Blocks"),
            new Cat("wood", "OAK_LOG", "Wood"),
            new Cat("stone", "STONE_BRICKS", "Stone & Bricks"),
            new Cat("colors", "CYAN_WOOL", "Colors & Glass"),
            new Cat("ores", "DIAMOND", "Ores & Metals"),
            new Cat("copper", "COPPER_BLOCK", "Copper"),
            new Cat("redstone", "REDSTONE", "Redstone & Transport"),
            new Cat("tools", "IRON_PICKAXE", "Tools & Weapons"),
            new Cat("armor", "IRON_CHESTPLATE", "Armor & Mounts"),
            new Cat("food", "COOKED_BEEF", "Food"),
            new Cat("farming", "WHEAT", "Farming & Plants"),
            new Cat("mobs", "BONE", "Mob Drops"),
            new Cat("nether", "NETHERRACK", "Nether, End & Deep Dark"),
            new Cat("misc", "CHEST", "Utility & Decoration"),
            new Cat("rare", "NETHER_STAR", "Rare & Special"));

    private static final List<String> BASE_WOODS = List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
            "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED", "PALE_OAK");
    /** Every wood family on this server (new game versions add woods such as poplar): learned from the *_PLANKS items. */
    private static volatile List<String> WOODS = BASE_WOODS;

    static void learnWoods(java.util.Collection<String> itemNames) {
        java.util.TreeSet<String> all = new java.util.TreeSet<>(BASE_WOODS);
        for (String n : itemNames) if (n.endsWith("_PLANKS")) all.add(n.substring(0, n.length() - 7));
        WOODS = List.copyOf(all);
    }

    static final List<String> COLORS = List.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK",
            "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK");
    private static final List<String> COLOR_THINGS = List.of("_WOOL", "_WOOL_STAIRS", "_WOOL_SLAB", "_CARPET", "_BED",
            "_BANNER", "_CONCRETE", "_CONCRETE_POWDER", "_CONCRETE_STAIRS", "_CONCRETE_SLAB", "_TERRACOTTA",
            "_GLAZED_TERRACOTTA", "_STAINED_GLASS", "_STAINED_GLASS_PANE", "_CANDLE", "_DYE", "_BUNDLE", "_CUSHION");
    private static final List<String> COPPER_STAGES = List.of("EXPOSED_", "WEATHERED_", "OXIDIZED_");

    /** Collectibles and trophies: things nobody needs to build with but everybody wants to show off. */
    private static final Set<String> RARE = Set.of("NETHER_STAR", "ENCHANTED_GOLDEN_APPLE", "HEART_OF_THE_SEA", "CONDUIT",
            "ECHO_SHARD", "SHULKER_SHELL", "NAUTILUS_SHELL", "SNIFFER_EGG", "CREAKING_HEART", "RECOVERY_COMPASS",
            "DISC_FRAGMENT_5", "TOTEM_OF_UNDYING", "DRAGON_EGG", "DRAGON_HEAD", "DRAGON_BREATH", "HEAVY_CORE", "BEACON",
            "END_CRYSTAL", "WITHER_ROSE", "WITHER_SKELETON_SKULL", "SKELETON_SKULL", "ZOMBIE_HEAD", "CREEPER_HEAD",
            "PIGLIN_HEAD", "PLAYER_HEAD", "NETHERITE_UPGRADE_SMITHING_TEMPLATE", "GOAT_HORN", "ENCHANTED_BOOK",
            "EXPERIENCE_BOTTLE", "TRIAL_KEY", "OMINOUS_TRIAL_KEY", "OMINOUS_BOTTLE");

    private static final Set<String> MOBS = Set.of("BONE", "BONE_BLOCK", "STRING", "GUNPOWDER", "SPIDER_EYE", "ROTTEN_FLESH",
            "ENDER_PEARL", "ENDER_EYE", "BLAZE_ROD", "BLAZE_POWDER", "BREEZE_ROD", "SLIME_BALL", "SLIME_BLOCK", "LEATHER",
            "RABBIT_HIDE", "RABBIT_FOOT", "FEATHER", "INK_SAC", "GLOW_INK_SAC", "PHANTOM_MEMBRANE", "SCUTE",
            "TURTLE_SCUTE", "ARMADILLO_SCUTE", "GHAST_TEAR", "MAGMA_CREAM", "EGG", "TURTLE_EGG", "PRISMARINE_SHARD",
            "PRISMARINE_CRYSTALS", "FERMENTED_SPIDER_EYE");

    private static final Set<String> TOOLS = Set.of("BOW", "CROSSBOW", "TRIDENT", "MACE", "SHEARS", "FISHING_ROD",
            "FLINT_AND_STEEL", "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW", "BRUSH", "SPYGLASS", "COMPASS", "CLOCK",
            "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "FIRE_CHARGE", "WIND_CHARGE", "BUCKET", "FIREWORK_ROCKET");

    private static final Set<String> ORE_ITEMS = Set.of("COAL", "CHARCOAL", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "QUARTZ",
            "AMETHYST_SHARD", "AMETHYST_BLOCK", "AMETHYST_CLUSTER", "SMALL_AMETHYST_BUD", "MEDIUM_AMETHYST_BUD",
            "LARGE_AMETHYST_BUD", "NETHERITE_SCRAP", "ANCIENT_DEBRIS", "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK",
            "EMERALD_BLOCK", "LAPIS_BLOCK", "COAL_BLOCK", "NETHERITE_BLOCK", "RAW_IRON_BLOCK", "RAW_GOLD_BLOCK",
            "RAW_COPPER_BLOCK");

    private static final Set<String> REDSTONE = Set.of("REDSTONE", "REDSTONE_BLOCK", "REDSTONE_TORCH", "REDSTONE_LAMP",
            "PISTON", "STICKY_PISTON", "REPEATER", "COMPARATOR", "OBSERVER", "HOPPER", "DROPPER", "DISPENSER", "LEVER",
            "TNT", "DAYLIGHT_DETECTOR", "TARGET", "NOTE_BLOCK", "JUKEBOX", "TRIPWIRE_HOOK", "CRAFTER",
            "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "ACTIVATOR_RAIL", "MINECART");

    private static final Set<String> NETHER = Set.of("NETHERRACK", "NETHER_WART", "NETHER_WART_BLOCK", "WARPED_WART_BLOCK",
            "SHROOMLIGHT", "SOUL_SAND", "SOUL_SOIL", "MAGMA_BLOCK", "GLOWSTONE", "GLOWSTONE_DUST", "CRYING_OBSIDIAN",
            "OBSIDIAN", "END_STONE", "CHORUS_FLOWER", "CHORUS_FRUIT", "CHORUS_PLANT", "POPPED_CHORUS_FRUIT", "END_ROD",
            "SCULK", "SCULK_VEIN", "SCULK_CATALYST", "SCULK_SHRIEKER", "DRIED_GHAST", "WEEPING_VINES", "TWISTING_VINES",
            "NETHER_SPROUTS", "CRIMSON_FUNGUS", "WARPED_FUNGUS", "CRIMSON_NYLIUM", "WARPED_NYLIUM", "CRIMSON_ROOTS",
            "WARPED_ROOTS", "GILDED_BLACKSTONE");

    private static final Set<String> BLOCKS = Set.of("DIRT", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM",
            "GRASS_BLOCK", "DIRT_PATH", "SAND", "RED_SAND", "GRAVEL", "CLAY", "CLAY_BALL", "MUD", "SNOW", "SNOW_BLOCK",
            "SNOWBALL", "ICE", "PACKED_ICE", "BLUE_ICE", "SPONGE", "WET_SPONGE", "COBWEB", "FLINT", "RESIN_CLUMP",
            "RESIN_BLOCK");

    private static final Set<String> FARMING = Set.of("WHEAT", "SUGAR", "SUGAR_CANE", "BAMBOO", "CACTUS", "KELP", "DRIED_KELP_BLOCK",
            "SEAGRASS", "SEA_PICKLE", "LILY_PAD", "VINE", "GLOW_LICHEN", "HANGING_ROOTS", "MOSS_BLOCK", "MOSS_CARPET",
            "PALE_MOSS_BLOCK", "PALE_MOSS_CARPET", "PALE_HANGING_MOSS", "SPORE_BLOSSOM", "SMALL_DRIPLEAF", "BIG_DRIPLEAF",
            "BROWN_MUSHROOM", "RED_MUSHROOM", "MUSHROOM_STEM", "BROWN_MUSHROOM_BLOCK", "RED_MUSHROOM_BLOCK", "HAY_BLOCK",
            "PUMPKIN", "CARVED_PUMPKIN", "MELON", "BONE_MEAL", "COCOA_BEANS", "FROGSPAWN", "LEAF_LITTER", "BEEHIVE",
            "BEE_NEST", "HONEYCOMB", "HONEYCOMB_BLOCK", "HONEY_BLOCK", "MANGROVE_ROOTS", "MUDDY_MANGROVE_ROOTS",
            "DEAD_BUSH", "PITCHER_POD", "BUSH", "FIREFLY_BUSH", "SHORT_GRASS", "TALL_GRASS", "SHORT_DRY_GRASS", "TALL_DRY_GRASS", "FERN",
            "LARGE_FERN", "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY", "CORNFLOWER",
            "LILY_OF_THE_VALLEY", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "TORCHFLOWER", "PITCHER_PLANT",
            "PINK_PETALS", "WILDFLOWERS", "CLOSED_EYEBLOSSOM", "OPEN_EYEBLOSSOM", "CACTUS_FLOWER", "AZALEA",
            "FLOWERING_AZALEA", "COMPOSTER", "GOLDEN_DANDELION");

    private static boolean ends(String n, String... suffixes) {
        for (String s : suffixes) if (n.endsWith(s)) return true;
        return false;
    }

    private static boolean has(String n, String... parts) {
        for (String s : parts) if (n.contains(s)) return true;
        return false;
    }

    static String of(Material m) {
        return of(m.name(), m.isEdible());
    }

    /** The category id for an item name. Pure: no server needed. */
    static String of(String n, boolean edible) {
        // 1. trophies and collectibles
        if (RARE.contains(n) || n.startsWith("MUSIC_DISC_") || ends(n, "_ARMOR_TRIM_SMITHING_TEMPLATE", "_POTTERY_SHERD",
                "_BANNER_PATTERN", "SHULKER_BOX") || n.equals("SHULKER_BOX")) return "rare";
        // 2. gear: tools and weapons, armour and mounts (before any material rule, so every wood, stone, copper or gold
        //    tool stays with the tools)
        if (ends(n, "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD", "_SPEAR") || TOOLS.contains(n)
                || ends(n, "_BUCKET")) return "tools";
        if (ends(n, "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_HORSE_ARMOR", "_NAUTILUS_ARMOR", "_HARNESS")
                || n.equals("WOLF_ARMOR") || n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("SADDLE")
                || n.equals("LEAD") || n.equals("NAME_TAG")) return "armor";
        // 3. ores, metals and gems (every *_ORE, raw metal, ingot, nugget and storage block)
        if (ends(n, "_ORE", "_INGOT", "_NUGGET") || n.startsWith("RAW_") || ORE_ITEMS.contains(n)) return "ores";
        // 4. copper: every copper block, door, bar, lantern, bulb and statue (and lightning rods) in every weathering stage
        if (has(n, "COPPER", "LIGHTNING_ROD")) return "copper";
        // 5. mob drops (every egg a chicken lays too; the sniffer and dragon eggs were taken by rule 1)
        if (MOBS.contains(n) || ends(n, "_SCUTE", "_EGG")) return "mobs";
        // 6. wood families (logs, planks and everything made of them) - plants such as leaves and saplings go to farming
        if (ends(n, "_LEAVES", "_SAPLING", "_PROPAGULE", "_PETALS", "_SHRUB", "_FUNGUS", "_NYLIUM", "_ROOTS", "_WART_BLOCK")) {
            return NETHER.contains(n) ? "nether" : "farming";
        }
        if (n.startsWith("STRIPPED_") && ends(n, "_LOG", "_WOOD", "_STEM", "_HYPHAE", "_BLOCK")) return "wood";
        if (n.equals("BAMBOO_BLOCK") || n.startsWith("BAMBOO_")) return "wood";
        for (String w : WOODS) {
            if (n.startsWith(w + "_")) return "wood";
        }
        // 7. colours and glass
        for (String c : COLORS) {
            if (n.startsWith(c + "_")) {
                String rest = n.substring(c.length());
                if (COLOR_THINGS.contains(rest)) return "colors";
            }
        }
        if (has(n, "GLASS") && !n.equals("GLASS_BOTTLE") || n.equals("TERRACOTTA") || n.equals("CANDLE")
                || n.endsWith("_BED")) return "colors";
        // 8. redstone and transport
        if (REDSTONE.contains(n) || ends(n, "_MINECART", "_PRESSURE_PLATE", "_BUTTON", "_RAIL")) return "redstone";
        // 9. the Nether, the End and the deep dark
        if (NETHER.contains(n)) return "nether";
        // 10. farming, plants and bees
        if (FARMING.contains(n) || has(n, "CORAL", "SEEDS", "_TULIP", "_ORCHID", "_AZALEA", "_MUSHROOM")) return "farming";
        // 11. food
        if (edible || n.equals("CAKE") || n.equals("GLISTERING_MELON_SLICE") || ends(n, "_STEW", "_SOUP")) return "food";
        // 12. stone and bricks (and every slab, stair and wall that is left)
        if (has(n, "STONE", "COBBLE", "DEEPSLATE", "TUFF", "BASALT", "ANDESITE", "DIORITE", "GRANITE", "CALCITE", "BRICK",
                "PURPUR", "PRISMARINE", "QUARTZ", "CINNABAR", "SULFUR", "PACKED_MUD", "POLISHED_", "CHISELED_", "CRACKED_",
                "MOSSY_", "SMOOTH_") || ends(n, "_SLAB", "_STAIRS", "_WALL")) {
            if (!has(n, "LODESTONE", "GRINDSTONE", "STONECUTTER", "GLOWSTONE", "REDSTONE", "CHISELED_BOOKSHELF")) return "stone";
        }
        // 13. natural blocks
        if (BLOCKS.contains(n)) return "blocks";
        return "misc";
    }

    /**
     * Sort key inside a category: colours group by item (all banners together, in rainbow order), copper groups by
     * block and then by weathering stage, everything else is alphabetical.
     */
    static Comparator<String> order(String cat) {
        return Comparator.comparing((String n) -> baseOf(cat, n)).thenComparingInt(n -> stageOf(cat, n)).thenComparing(n -> n);
    }

    private static String baseOf(String cat, String n) {
        if (cat.equals("colors")) {
            for (String c : COLORS) if (n.startsWith(c + "_") && COLOR_THINGS.contains(n.substring(c.length()))) return n.substring(c.length());
        } else if (cat.equals("copper")) {
            String s = n.startsWith("WAXED_") ? n.substring(6) : n;
            for (String st : COPPER_STAGES) if (s.startsWith(st)) return s.substring(st.length());
            return s;
        }
        return n;
    }

    private static int stageOf(String cat, String n) {
        if (cat.equals("colors")) {
            for (int i = 0; i < COLORS.size(); i++) if (n.startsWith(COLORS.get(i) + "_") && n.length() > COLORS.get(i).length()
                    && COLOR_THINGS.contains(n.substring(COLORS.get(i).length()))) return i;
            return -1;
        }
        if (cat.equals("copper")) {
            boolean waxed = n.startsWith("WAXED_");
            String s = waxed ? n.substring(6) : n;
            int stage = 0;
            for (int i = 0; i < COPPER_STAGES.size(); i++) if (s.startsWith(COPPER_STAGES.get(i))) stage = i + 1;
            return stage + (waxed ? 4 : 0);
        }
        return 0;
    }

    static Cat find(String id) {
        for (Cat c : ALL) if (c.id().equals(id)) return c;
        return ALL.get(ALL.size() - 1);
    }

    static boolean exists(String id) {
        for (Cat c : ALL) if (c.id().equals(id)) return true;
        return false;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
