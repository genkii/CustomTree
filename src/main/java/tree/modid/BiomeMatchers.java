package tree.modid;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Ready-made {@link Predicate} constants that identify vanilla biomes.
 *
 * <p>Pass one of these to {@link CustomTreeDefinition.Builder#biomes}
 * (or the shorthand {@link CustomTreeDefinition.Builder#inBiome}) to
 * restrict a custom tree to specific biomes.  Leaving the biome filter unset means
 * the tree can appear in <em>any</em> biome.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Forest-only oak:
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("forest_oak")
 *           .sapling(Blocks.OAK_SAPLING)
 *           .worldGen(TreeMatchers.OAK)
 *           .biomes(BiomeMatchers.IS_FOREST)
 *           .minSpacing(10)
 *           .build());
 *
 *   // Plains + meadow oak:
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("open_oak")
 *           .sapling(Blocks.OAK_SAPLING)
 *           .worldGen(TreeMatchers.OAK)
 *           .biomes(BiomeMatchers.any(BiomeMatchers.PLAINS, BiomeMatchers.MEADOW))
 *           .minSpacing(12)
 *           .build());
 *
 *   // All taiga biomes at once via tag:
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("taiga_spruce")
 *           .sapling(Blocks.SPRUCE_SAPLING)
 *           .worldGen(TreeMatchers.SPRUCE)
 *           .biomes(BiomeMatchers.IS_TAIGA)
 *           .build());
 * </pre>
 *
 * <h3>Adding a custom matcher</h3>
 * Use {@link #is(ResourceKey)} for a single specific biome,
 * {@link #isAny} for several specific biomes, or
 * {@link #hasTag(TagKey)} for tag-based matching.  Combine anything with
 * {@link #any}.
 */
public final class BiomeMatchers {

    private BiomeMatchers() {}

    // =========================================================================
    // Tag-based constants  (match entire categories of biomes at once)
    // =========================================================================

    /**
     * Any biome tagged {@code #minecraft:is_forest}.
     * Includes: forest, flower forest, birch forest, old-growth birch forest,
     * dark forest, windswept forest.
     */
    public static final Predicate<Holder<Biome>> IS_FOREST = hasTag(
        BiomeTags.IS_FOREST
    );

    /**
     * Any biome tagged {@code #minecraft:is_taiga}.
     * Includes: taiga, snowy taiga, old-growth pine taiga,
     * old-growth spruce taiga.
     */
    public static final Predicate<Holder<Biome>> IS_TAIGA = hasTag(
        BiomeTags.IS_TAIGA
    );

    /**
     * Any biome tagged {@code #minecraft:is_jungle}.
     * Includes: jungle, sparse jungle, bamboo jungle.
     */
    public static final Predicate<Holder<Biome>> IS_JUNGLE = hasTag(
        BiomeTags.IS_JUNGLE
    );

    /**
     * Any biome tagged {@code #minecraft:is_savanna}.
     * Includes: savanna, savanna plateau, windswept savanna.
     */
    public static final Predicate<Holder<Biome>> IS_SAVANNA = hasTag(
        BiomeTags.IS_SAVANNA
    );

    /**
     * Any biome tagged {@code #minecraft:is_badlands}.
     * Includes: badlands, eroded badlands, wooded badlands.
     */
    public static final Predicate<Holder<Biome>> IS_BADLANDS = hasTag(
        BiomeTags.IS_BADLANDS
    );

    /**
     * Any biome tagged {@code #minecraft:is_ocean}.
     */
    public static final Predicate<Holder<Biome>> IS_OCEAN = hasTag(
        BiomeTags.IS_OCEAN
    );

    /**
     * Any biome tagged {@code #minecraft:is_river}.
     */
    public static final Predicate<Holder<Biome>> IS_RIVER = hasTag(
        BiomeTags.IS_RIVER
    );

    /**
     * Any biome tagged {@code #minecraft:is_beach}.
     */
    public static final Predicate<Holder<Biome>> IS_BEACH = hasTag(
        BiomeTags.IS_BEACH
    );

    /**
     * Any overworld biome (tagged {@code #minecraft:is_overworld}).
     * Useful as a "not Nether / not End" guard.
     */
    public static final Predicate<Holder<Biome>> IS_OVERWORLD = hasTag(
        BiomeTags.IS_OVERWORLD
    );

    /**
     * Any Nether biome (tagged {@code #minecraft:is_nether}).
     */
    public static final Predicate<Holder<Biome>> IS_NETHER = hasTag(
        BiomeTags.IS_NETHER
    );

    /**
     * Any End biome (tagged {@code #minecraft:is_end}).
     */
    public static final Predicate<Holder<Biome>> IS_END = hasTag(
        BiomeTags.IS_END
    );

    // =========================================================================
    // Specific biome constants  (exact biome match)
    // =========================================================================

    // ---- Plains / open land -------------------------------------------------
    /** {@code minecraft:plains} */
    public static final Predicate<Holder<Biome>> PLAINS = is(Biomes.PLAINS);

    /** {@code minecraft:sunflower_plains} */
    public static final Predicate<Holder<Biome>> SUNFLOWER_PLAINS = is(
        Biomes.SUNFLOWER_PLAINS
    );

    /** {@code minecraft:meadow} */
    public static final Predicate<Holder<Biome>> MEADOW = is(Biomes.MEADOW);

    /** {@code minecraft:snowy_plains} */
    public static final Predicate<Holder<Biome>> SNOWY_PLAINS = is(
        Biomes.SNOWY_PLAINS
    );

    // ---- Forest biomes ------------------------------------------------------
    /** {@code minecraft:forest} */
    public static final Predicate<Holder<Biome>> FOREST = is(Biomes.FOREST);

    /** {@code minecraft:flower_forest} */
    public static final Predicate<Holder<Biome>> FLOWER_FOREST = is(
        Biomes.FLOWER_FOREST
    );

    /** {@code minecraft:birch_forest} */
    public static final Predicate<Holder<Biome>> BIRCH_FOREST = is(
        Biomes.BIRCH_FOREST
    );

    /** {@code minecraft:old_growth_birch_forest} */
    public static final Predicate<Holder<Biome>> OLD_GROWTH_BIRCH_FOREST = is(
        Biomes.OLD_GROWTH_BIRCH_FOREST
    );

    /** {@code minecraft:dark_forest} */
    public static final Predicate<Holder<Biome>> DARK_FOREST = is(
        Biomes.DARK_FOREST
    );

    /** {@code minecraft:windswept_forest} */
    public static final Predicate<Holder<Biome>> WINDSWEPT_FOREST = is(
        Biomes.WINDSWEPT_FOREST
    );

    // ---- Taiga biomes -------------------------------------------------------
    /** {@code minecraft:taiga} */
    public static final Predicate<Holder<Biome>> TAIGA = is(Biomes.TAIGA);

    /** {@code minecraft:snowy_taiga} */
    public static final Predicate<Holder<Biome>> SNOWY_TAIGA = is(
        Biomes.SNOWY_TAIGA
    );

    /** {@code minecraft:old_growth_pine_taiga} */
    public static final Predicate<Holder<Biome>> OLD_GROWTH_PINE_TAIGA = is(
        Biomes.OLD_GROWTH_PINE_TAIGA
    );

    /** {@code minecraft:old_growth_spruce_taiga} */
    public static final Predicate<Holder<Biome>> OLD_GROWTH_SPRUCE_TAIGA = is(
        Biomes.OLD_GROWTH_SPRUCE_TAIGA
    );

    // ---- Jungle biomes ------------------------------------------------------
    /** {@code minecraft:jungle} */
    public static final Predicate<Holder<Biome>> JUNGLE = is(Biomes.JUNGLE);

    /** {@code minecraft:sparse_jungle} */
    public static final Predicate<Holder<Biome>> SPARSE_JUNGLE = is(
        Biomes.SPARSE_JUNGLE
    );

    /** {@code minecraft:bamboo_jungle} */
    public static final Predicate<Holder<Biome>> BAMBOO_JUNGLE = is(
        Biomes.BAMBOO_JUNGLE
    );

    // ---- Savanna biomes -----------------------------------------------------
    /** {@code minecraft:savanna} */
    public static final Predicate<Holder<Biome>> SAVANNA = is(Biomes.SAVANNA);

    /** {@code minecraft:savanna_plateau} */
    public static final Predicate<Holder<Biome>> SAVANNA_PLATEAU = is(
        Biomes.SAVANNA_PLATEAU
    );

    /** {@code minecraft:windswept_savanna} */
    public static final Predicate<Holder<Biome>> WINDSWEPT_SAVANNA = is(
        Biomes.WINDSWEPT_SAVANNA
    );

    // ---- Windswept / mountains ----------------------------------------------
    /** {@code minecraft:windswept_hills} */
    public static final Predicate<Holder<Biome>> WINDSWEPT_HILLS = is(
        Biomes.WINDSWEPT_HILLS
    );

    /** {@code minecraft:windswept_gravelly_hills} */
    public static final Predicate<Holder<Biome>> WINDSWEPT_GRAVELLY_HILLS = is(
        Biomes.WINDSWEPT_GRAVELLY_HILLS
    );

    /** {@code minecraft:grove} – snowy conifer grove in mountain peaks */
    public static final Predicate<Holder<Biome>> GROVE = is(Biomes.GROVE);

    // ---- Swamp / wet --------------------------------------------------------
    /** {@code minecraft:swamp} */
    public static final Predicate<Holder<Biome>> SWAMP = is(Biomes.SWAMP);

    /** {@code minecraft:mangrove_swamp} */
    public static final Predicate<Holder<Biome>> MANGROVE_SWAMP = is(
        Biomes.MANGROVE_SWAMP
    );

    // ---- Special biomes -----------------------------------------------------
    /** {@code minecraft:cherry_grove} */
    public static final Predicate<Holder<Biome>> CHERRY_GROVE = is(
        Biomes.CHERRY_GROVE
    );

    /** {@code minecraft:mushroom_fields} */
    public static final Predicate<Holder<Biome>> MUSHROOM_FIELDS = is(
        Biomes.MUSHROOM_FIELDS
    );

    /** {@code minecraft:lush_caves} */
    public static final Predicate<Holder<Biome>> LUSH_CAVES = is(
        Biomes.LUSH_CAVES
    );

    // ---- Pale Garden (1.21.4+) ----------------------------------------------
    /** {@code minecraft:pale_garden} – the eerie biome with pale oak trees. */
    public static final Predicate<Holder<Biome>> PALE_GARDEN = is(
        Biomes.PALE_GARDEN
    );

    // ---- Nether biomes ------------------------------------------------------
    /** {@code minecraft:crimson_forest} – the crimson fungus biome. */
    public static final Predicate<Holder<Biome>> CRIMSON_FOREST = is(
        Biomes.CRIMSON_FOREST
    );

    /** {@code minecraft:warped_forest} – the warped fungus biome. */
    public static final Predicate<Holder<Biome>> WARPED_FOREST = is(
        Biomes.WARPED_FOREST
    );

    /** {@code minecraft:nether_wastes} */
    public static final Predicate<Holder<Biome>> NETHER_WASTES = is(
        Biomes.NETHER_WASTES
    );

    /** {@code minecraft:soul_sand_valley} */
    public static final Predicate<Holder<Biome>> SOUL_SAND_VALLEY = is(
        Biomes.SOUL_SAND_VALLEY
    );

    /** {@code minecraft:basalt_deltas} */
    public static final Predicate<Holder<Biome>> BASALT_DELTAS = is(
        Biomes.BASALT_DELTAS
    );

    // =========================================================================
    // Combined biome filters
    // =========================================================================

    /**
     * All biomes where snowy/conifer spruce trees naturally generate:
     * {@code snowy_taiga}, {@code snowy_plains}, {@code windswept_hills},
     * {@code windswept_forest}, {@code windswept_gravelly_hills}, and
     * {@code grove} (snowy conifer mountain grove).
     *
     * <p>Pass this to {@code registerTree} so one line per NBT file covers
     * every snowy / windswept / mountain-conifer biome at once:
     * <pre>
     *   registerTree("snowyspruce1", Blocks.SPRUCE_SAPLING,
     *       TreeMatchers.SPRUCE_ONLY, 8, BiomeMatchers.SNOWY_SPRUCE_BIOMES);
     * </pre>
     */
    public static final Predicate<Holder<Biome>> SNOWY_SPRUCE_BIOMES = any(
        SNOWY_TAIGA,
        SNOWY_PLAINS,
        WINDSWEPT_HILLS,
        WINDSWEPT_FOREST,
        WINDSWEPT_GRAVELLY_HILLS,
        GROVE
    );

    /**
     * The three non-snowy taiga biomes: {@code taiga},
     * {@code old_growth_pine_taiga}, and {@code old_growth_spruce_taiga}.
     *
     * <p>Used for regular sprucetree NBT registrations so that
     * {@code snowy_taiga} (which is in {@link #IS_TAIGA}) exclusively uses
     * the snowy-spruce NBTs from {@link #SNOWY_SPRUCE_BIOMES}.
     * <pre>
     *   registerTree("sprucetree2", Blocks.SPRUCE_SAPLING,
     *       TreeMatchers.SPRUCE_ONLY, 8, BiomeMatchers.NON_SNOWY_TAIGA);
     * </pre>
     */
    public static final Predicate<Holder<Biome>> NON_SNOWY_TAIGA = any(
        TAIGA,
        OLD_GROWTH_PINE_TAIGA,
        OLD_GROWTH_SPRUCE_TAIGA
    );

    /**
     * All biomes where pine-type or mega-spruce trees generate:
     * every {@link #IS_TAIGA} biome plus {@code grove}.
     *
     * <p>Pass to bigspruce-style registrations that cover
     * {@code PINE}, {@code MEGA_PINE}, and {@code MEGA_SPRUCE}:
     * <pre>
     *   registerTree("bigspruce1", Blocks.SPRUCE_SAPLING,
     *       TreeMatchers.any(TreeMatchers.PINE, TreeMatchers.MEGA_PINE,
     *                        TreeMatchers.MEGA_SPRUCE),
     *       8, BiomeMatchers.PINE_BIOMES);
     * </pre>
     */
    public static final Predicate<Holder<Biome>> PINE_BIOMES = any(
        IS_TAIGA,
        GROVE
    );

    // =========================================================================
    // Combinators
    // =========================================================================

    /**
     * Returns a predicate that is {@code true} when ANY of the supplied
     * matchers is {@code true}.
     *
     * <pre>
     *   BiomeMatchers.any(BiomeMatchers.FOREST, BiomeMatchers.FLOWER_FOREST)
     * </pre>
     */
    @SafeVarargs
    public static Predicate<Holder<Biome>> any(
        Predicate<Holder<Biome>>... matchers
    ) {
        return biome -> {
            for (Predicate<Holder<Biome>> m : matchers) {
                if (m.test(biome)) return true;
            }
            return false;
        };
    }

    /**
     * Returns a predicate that is {@code true} only when ALL of the supplied
     * matchers are {@code true}.  Useful for combining a tag check with an
     * exclusion:
     *
     * <pre>
     *   // All forest biomes except dark forest:
     *   BiomeMatchers.all(BiomeMatchers.IS_FOREST,
     *                     BiomeMatchers.DARK_FOREST.negate())
     * </pre>
     */
    @SafeVarargs
    public static Predicate<Holder<Biome>> all(
        Predicate<Holder<Biome>>... matchers
    ) {
        return biome -> {
            for (Predicate<Holder<Biome>> m : matchers) {
                if (!m.test(biome)) return false;
            }
            return true;
        };
    }

    // =========================================================================
    // Low-level helpers – build your own matchers with these
    // =========================================================================

    /**
     * Matches exactly one specific biome by its {@link ResourceKey}.
     *
     * <pre>
     *   BiomeMatchers.is(Biomes.FOREST)
     *   // or with a modded biome:
     *   BiomeMatchers.is(ResourceKey.create(Registries.BIOME,
     *       ResourceLocation.parse("mymod:crystal_forest")))
     * </pre>
     */
    public static Predicate<Holder<Biome>> is(ResourceKey<Biome> key) {
        return biome -> biome.is(key);
    }

    /**
     * Matches any of the supplied specific biomes.
     *
     * <pre>
     *   BiomeMatchers.isAny(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST)
     * </pre>
     */
    @SafeVarargs
    public static Predicate<Holder<Biome>> isAny(ResourceKey<Biome>... keys) {
        return biome -> {
            for (ResourceKey<Biome> key : keys) {
                if (biome.is(key)) return true;
            }
            return false;
        };
    }

    /**
     * Matches any biome that carries the supplied {@link TagKey}.
     *
     * <pre>
     *   // All forest-tagged biomes (same as BiomeMatchers.IS_FOREST):
     *   BiomeMatchers.hasTag(BiomeTags.IS_FOREST)
     * </pre>
     */
    public static Predicate<Holder<Biome>> hasTag(TagKey<Biome> tag) {
        return biome -> biome.is(tag);
    }
}
