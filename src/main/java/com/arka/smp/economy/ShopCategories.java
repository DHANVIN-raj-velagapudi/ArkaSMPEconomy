package com.arka.smp.economy;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Sorts every item into one shop category by its name. Order here is the order in the menu. */
final class ShopCategories {
    private ShopCategories() {
    }

    record Cat(String id, Material icon, String name) {
    }

    static final List<Cat> ALL = List.of(
            new Cat("blocks", Material.GRASS_BLOCK, "Building Blocks"),
            new Cat("wood", Material.OAK_LOG, "Wood"),
            new Cat("stone", Material.STONE_BRICKS, "Stone & Bricks"),
            new Cat("colors", Material.CYAN_WOOL, "Colors & Glass"),
            new Cat("ores", Material.DIAMOND, "Ores & Metals"),
            new Cat("redstone", Material.REDSTONE, "Redstone"),
            new Cat("tools", Material.IRON_PICKAXE, "Tools & Weapons"),
            new Cat("armor", Material.IRON_CHESTPLATE, "Armor"),
            new Cat("food", Material.COOKED_BEEF, "Food"),
            new Cat("farming", Material.WHEAT, "Farming & Plants"),
            new Cat("mobs", Material.BONE, "Mob Drops"),
            new Cat("misc", Material.CHEST, "Decoration & Misc"),
            new Cat("rare", Material.NETHER_STAR, "Rare & Special"));

    private static final List<String> BASE_WOODS = List.of("OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
            "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED", "PALE_OAK");
    /** Every wood family on this server (new game versions add woods such as poplar): learned from the *_PLANKS items. */
    private static volatile List<String> WOODS = BASE_WOODS;

    static void learnWoods(java.util.Collection<String> itemNames) {
        java.util.TreeSet<String> all = new java.util.TreeSet<>(BASE_WOODS);
        for (String n : itemNames) if (n.endsWith("_PLANKS")) all.add(n.substring(0, n.length() - 7));
        WOODS = List.copyOf(all);
    }
    private static final List<String> COLORS = List.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
            "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK");
    private static final Set<String> MOB_DROPS = Set.of("BONE", "STRING", "GUNPOWDER", "SPIDER_EYE", "ENDER_PEARL",
            "BLAZE_ROD", "BLAZE_POWDER", "SLIME_BALL", "LEATHER", "FEATHER", "INK_SAC", "GLOW_INK_SAC",
            "PHANTOM_MEMBRANE", "SCUTE", "TURTLE_SCUTE", "ARMADILLO_SCUTE", "GHAST_TEAR", "SHULKER_SHELL", "NAUTILUS_SHELL", "RABBIT_HIDE",
            "RABBIT_FOOT", "MAGMA_CREAM", "ROTTEN_FLESH", "ECHO_SHARD", "BREEZE_ROD", "HONEYCOMB", "EGG",
            "TOTEM_OF_UNDYING", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS");
    private static final Set<String> FARM = Set.of("WHEAT", "SEEDS", "CARROT", "POTATO", "BEETROOT", "SUGAR_CANE",
            "CACTUS", "KELP", "BAMBOO", "VINE", "LILY_PAD", "BONE_MEAL", "SUGAR", "COCOA_BEANS", "NETHER_WART",
            "SWEET_BERRIES", "GLOW_BERRIES", "BROWN_MUSHROOM", "RED_MUSHROOM", "HAY_BLOCK", "PUMPKIN", "MELON",
            "COMPOSTER", "SEA_PICKLE", "FERN", "DANDELION", "POPPY", "SUNFLOWER", "LILAC", "PEONY", "ROSE_BUSH");
    private static final Set<String> REDSTONE_WORDS = Set.of("REDSTONE", "PISTON", "REPEATER", "COMPARATOR",
            "OBSERVER", "HOPPER", "DROPPER", "DISPENSER", "LEVER", "BUTTON", "PRESSURE_PLATE", "TNT", "RAIL",
            "DAYLIGHT_DETECTOR", "TARGET", "NOTE_BLOCK", "TRIPWIRE", "LIGHTNING_ROD", "SCULK_SENSOR", "CRAFTER");

    static String of(Material m) {
        String n = m.name();
        if (n.endsWith("_LEAVES") || n.endsWith("MUSHROOM") || n.endsWith("_SHRUB") || n.endsWith("_PETALS")
                || n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE")) return "farming";
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || n.equals("TURTLE_HELMET") || n.equals("SHIELD")) return "armor";
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.endsWith("_SWORD") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT")
                || n.equals("SHEARS") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL") || n.equals("ARROW")
                || n.equals("SPYGLASS") || n.equals("COMPASS") || n.equals("CLOCK")) return "tools";
        if (m.isEdible()) return "food";
        if (MOB_DROPS.contains(n)) return "mobs";
        for (String w : REDSTONE_WORDS) if (n.contains(w)) return "redstone";
        if (n.endsWith("_INGOT") || n.endsWith("_NUGGET") || n.startsWith("RAW_") || n.endsWith("_ORE")
                || n.equals("DIAMOND") || n.equals("EMERALD") || n.equals("COAL") || n.equals("CHARCOAL")
                || n.equals("LAPIS_LAZULI") || n.equals("QUARTZ") || n.equals("AMETHYST_SHARD")
                || n.equals("NETHERITE_SCRAP") || n.equals("ANCIENT_DEBRIS")
                || n.matches("(IRON|GOLD|COPPER|DIAMOND|EMERALD|LAPIS|COAL|NETHERITE|REDSTONE|QUARTZ|AMETHYST)_BLOCK")
                || n.equals("RAW_IRON_BLOCK")) return "ores";
        for (String c : COLORS) {
            if (n.startsWith(c + "_")) return "colors";
        }
        if (n.contains("GLASS") || n.equals("DYE") || n.endsWith("_DYE")) return "colors";
        for (String f : FARM) if (n.equals(f) || n.endsWith("_" + f) || n.endsWith(f)) return "farming";
        if (n.endsWith("_SAPLING") || n.endsWith("_LEAVES") || n.endsWith("_TULIP") || n.endsWith("_ORCHID")) return "farming";
        for (String w : WOODS) {
            if (n.startsWith(w + "_")) return "wood";
        }
        if (n.contains("STONE") || n.contains("DEEPSLATE") || n.contains("BRICK") || n.contains("COBBLE")
                || n.contains("TUFF") || n.contains("BASALT") || n.contains("BLACKSTONE") || n.contains("ANDESITE")
                || n.contains("DIORITE") || n.contains("GRANITE") || n.contains("CALCITE") || n.contains("PRISMARINE")
                || n.contains("QUARTZ") || n.contains("SANDSTONE") || n.contains("PURPUR") || n.contains("TERRACOTTA")
                || n.contains("DRIPSTONE") || n.contains("OBSIDIAN")) return "stone";
        if (m.isBlock() && !n.contains("_") ) return "blocks";
        if (n.equals("DIRT") || n.equals("SAND") || n.equals("GRAVEL") || n.equals("CLAY") || n.endsWith("_DIRT")
                || n.equals("GRASS_BLOCK") || n.equals("NETHERRACK") || n.equals("SOUL_SAND") || n.equals("SNOW_BLOCK")
                || n.equals("ICE") || n.endsWith("_ICE") || n.equals("MUD") || n.equals("PODZOL") || n.equals("MYCELIUM")
                || n.equals("RED_SAND") || n.equals("MOSS_BLOCK") || n.equals("SPONGE") || n.equals("GLOWSTONE")) return "blocks";
        return "misc";
    }

    static Cat find(String id) {
        for (Cat c : ALL) if (c.id().equals(id)) return c;
        return ALL.get(ALL.size() - 1);
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
