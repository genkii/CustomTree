package tree.modid;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.AcaciaFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BushFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.DarkOakFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaJungleFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaPineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.RandomSpreadFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.SpruceFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.DarkOakTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.ForkingTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.MegaJungleTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.UpwardsBranchingTrunkPlacer;

/**
 * Ready-made {@link Predicate} constants that identify every vanilla tree type
 * by inspecting the {@link TreeConfiguration} used during world generation.
 *
 * <h3>Why trunk-block discrimination is needed</h3>
 * In Minecraft 1.21, {@code BirchFoliagePlacer} and {@code JungleFoliagePlacer}
 * no longer exist.  Oak, birch, and small jungle trees all share
 * {@link BlobFoliagePlacer}, so we fall back to inspecting the log block stored
 * in the tree's {@code trunk_provider} to tell them apart.
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Single matcher
 *   CustomTreeDefinition.forTree("birch_tree")
 *       .worldGen(TreeMatchers.BIRCH)
 *       .build();
 *
 *   // Combine variants → one NBT file handles both regular and fancy oak
 *   CustomTreeDefinition.forTree("oak_tree")
 *       .worldGen(TreeMatchers.any(TreeMatchers.OAK, TreeMatchers.FANCY_OAK))
 *       .build();
 * </pre>
 *
 * <h3>Adding a custom matcher</h3>
 * Use the public helpers {@link #byFoliage(Class)}, {@link #byTrunk(Class)},
 * or {@link #byTrunkBlock(Block)} and combine them with {@link Predicate#and}.
 */
public final class TreeMatchers {

    private TreeMatchers() {}

    // =========================================================================
    // Vanilla tree matchers
    // =========================================================================

    /**
     * Regular (short) oak – {@link BlobFoliagePlacer} + {@code oak_log} trunk.
     * Does NOT match the fancy/large oak variant; see {@link #FANCY_OAK}.
     */
    public static final Predicate<TreeConfiguration> OAK = byFoliage(
        BlobFoliagePlacer.class
    ).and(byTrunkBlock(Blocks.OAK_LOG));

    /**
     * Fancy / tall oak – {@link FancyFoliagePlacer} uniquely identifies this
     * variant; no trunk-block check required.
     */
    public static final Predicate<TreeConfiguration> FANCY_OAK = byFoliage(
        FancyFoliagePlacer.class
    );

    /**
     * Birch – in 1.21 birch shares {@link BlobFoliagePlacer} with oak; the
     * {@code birch_log} trunk block is used to disambiguate.
     */
    public static final Predicate<TreeConfiguration> BIRCH = byFoliage(
        BlobFoliagePlacer.class
    ).and(byTrunkBlock(Blocks.BIRCH_LOG));

    /** Spruce – {@link SpruceFoliagePlacer} is unique to spruce trees.
     * Note: this also matches mega-spruce (2x2) because mega-spruce shares
     * {@link SpruceFoliagePlacer}.  Use {@link #SPRUCE_ONLY} to exclude it.
     */
    public static final Predicate<TreeConfiguration> SPRUCE = byFoliage(
        SpruceFoliagePlacer.class
    );

    /**
     * Regular (single-trunk) spruce only – {@link SpruceFoliagePlacer}
     * combined with a NOT-{@link GiantTrunkPlacer} guard so that the 2x2
     * mega-spruce variant (which also uses {@link SpruceFoliagePlacer} but
     * with {@link GiantTrunkPlacer}) is excluded.
     *
     * <p>Use this in registrations where you want the normal snowyspruce /
     * sprucetree NBTs placed for single-trunk world-gen trees, while
     * {@link #MEGA_SPRUCE} routes to the bigger bigspruce-style NBTs.
     */
    public static final Predicate<TreeConfiguration> SPRUCE_ONLY = byFoliage(
        SpruceFoliagePlacer.class
    ).and(byTrunk(GiantTrunkPlacer.class).negate());

    /**
     * Pine (a spruce sub-variant that generates in taiga biomes) –
     * uses {@link PineFoliagePlacer} instead of {@link SpruceFoliagePlacer}.
     */
    public static final Predicate<TreeConfiguration> PINE = byFoliage(
        PineFoliagePlacer.class
    );

    /**
     * Mega pine (2×2 spruce sapling) –
     * uses {@link MegaPineFoliagePlacer} + {@link GiantTrunkPlacer}.
     */
    public static final Predicate<TreeConfiguration> MEGA_PINE = byFoliage(
        MegaPineFoliagePlacer.class
    );

    /**
     * Mega spruce (2×2 spruce sapling, old-growth taiga) –
     * {@link GiantTrunkPlacer} + {@link SpruceFoliagePlacer}.
     * Combine with {@link #MEGA_PINE} if you want one file for all 2×2 conifers.
     */
    public static final Predicate<TreeConfiguration> MEGA_SPRUCE = byTrunk(
        GiantTrunkPlacer.class
    ).and(byFoliage(SpruceFoliagePlacer.class));

    /**
     * Small jungle tree – in 1.21 it shares {@link BlobFoliagePlacer} with oak
     * and birch; the {@code jungle_log} trunk block is used to disambiguate.
     */
    public static final Predicate<TreeConfiguration> JUNGLE_SMALL = byFoliage(
        BlobFoliagePlacer.class
    ).and(byTrunkBlock(Blocks.JUNGLE_LOG));

    /**
     * Mega jungle tree (2×2 jungle sapling) –
     * {@link MegaJungleTrunkPlacer} + {@link MegaJungleFoliagePlacer}.
     */
    public static final Predicate<TreeConfiguration> JUNGLE_MEGA = byFoliage(
        MegaJungleFoliagePlacer.class
    );

    /**
     * Jungle bush – {@link BushFoliagePlacer} uniquely identifies it.
     */
    public static final Predicate<TreeConfiguration> JUNGLE_BUSH = byFoliage(
        BushFoliagePlacer.class
    );

    /**
     * Acacia – {@link ForkingTrunkPlacer} uniquely identifies it; the foliage
     * check via {@link AcaciaFoliagePlacer} is added for extra safety.
     */
    public static final Predicate<TreeConfiguration> ACACIA = byFoliage(
        AcaciaFoliagePlacer.class
    );

    /**
     * Dark oak – {@link DarkOakFoliagePlacer} + {@code dark_oak_log} trunk.
     * Must use trunk-block discrimination because Pale Oak (added in 1.21.4)
     * also uses {@link DarkOakFoliagePlacer} and {@link DarkOakTrunkPlacer}.
     */
    public static final Predicate<TreeConfiguration> DARK_OAK = byFoliage(
        DarkOakFoliagePlacer.class
    ).and(byTrunkBlock(Blocks.DARK_OAK_LOG));

    /**
     * Pale Oak (Pale Garden biome, 1.21.4+) – also uses
     * {@link DarkOakFoliagePlacer} and {@link DarkOakTrunkPlacer}, but with
     * {@code pale_oak_log} as the trunk block.
     */
    public static final Predicate<TreeConfiguration> PALE_OAK = byFoliage(
        DarkOakFoliagePlacer.class
    ).and(byTrunkBlock(Blocks.PALE_OAK_LOG));

    /**
     * Cherry – {@link CherryFoliagePlacer} uniquely identifies it (added in 1.20.3).
     */
    public static final Predicate<TreeConfiguration> CHERRY = byFoliage(
        CherryFoliagePlacer.class
    );

    /**
     * Swamp oak – structurally similar to regular oak (uses
     * {@link BlobFoliagePlacer} + {@code oak_log}), but distinguished by
     * vine generation (i.e. {@code ignoreVines == false}).
     */
    public static final Predicate<TreeConfiguration> SWAMP = byFoliage(
        BlobFoliagePlacer.class
    )
        .and(byTrunkBlock(Blocks.OAK_LOG))
        .and(config -> !config.ignoreVines);

    /**
     * Azalea tree – identified by {@link RandomSpreadFoliagePlacer} AND an
     * {@code oak_log} trunk block.
     *
     * <p>The {@link RandomSpreadFoliagePlacer} check alone is NOT sufficient:
     * vanilla mangrove trees also use {@link RandomSpreadFoliagePlacer} for
     * their canopy.  Without the {@code oak_log} guard, every mangrove in the
     * mangrove swamp would match this predicate and get an azalea NBT placed
     * instead.  The trunk-block check disambiguates the two cleanly:
     * <ul>
     *   <li>Azalea  → {@code oak_log}     trunk + {@link RandomSpreadFoliagePlacer}</li>
     *   <li>Mangrove → {@code mangrove_log} trunk + {@link RandomSpreadFoliagePlacer}</li>
     * </ul>
     *
     * <p>Azalea ({@link Blocks#AZALEA}) and Flowering Azalea
     * ({@link Blocks#FLOWERING_AZALEA}) are <em>not</em>
     * {@link net.minecraft.world.level.block.SaplingBlock SaplingBlock}s, so
     * {@code SaplingGrowthMixin} never fires for them.  However, when a player
     * bone-meals an azalea bush the game calls {@code TreeFeature.place()}
     * directly, which <em>is</em> intercepted by {@code TreeFeatureMixin}.
     * Register with {@code registerTreeWorldGenOnly} (no sapling block needed)
     * and both world-gen and bone-meal growth are covered.
     *
     * <h3>Where azalea trees spawn – do NOT use a biome filter</h3>
     * Azalea trees are generated in <em>two</em> distinct locations:
     * <ol>
     *   <li>Underground, inside the {@code minecraft:lush_caves} biome.</li>
     *   <li>On the surface directly above a lush cave, in <em>whatever
     *       overworld biome occupies that surface column</em> – forest, plains,
     *       jungle, river, anything.  The biome at the surface position is
     *       <em>not</em> {@code lush_caves}.</li>
     * </ol>
     * Restricting to {@code BiomeMatchers.LUSH_CAVES} would therefore block all
     * surface-generated azalea trees (which is most of them).
     * The correct approach is to register with <strong>no biome filter</strong>
     * so both locations are covered:
     * <pre>
     *   registerTreeWorldGenOnly("azalea1", TreeMatchers.AZALEA, 10);
     * </pre>
     */
    public static final Predicate<TreeConfiguration> AZALEA = byFoliage(
        RandomSpreadFoliagePlacer.class
    ).and(byTrunkBlock(Blocks.OAK_LOG));

    /**
     * Mangrove tree – uniquely identified by {@link MangroveRootPlacer} as its
     * trunk placer (added in 1.19).  Grows from
     * {@link Blocks#MANGROVE_PROPAGULE} and appears naturally only in
     * {@code minecraft:mangrove_swamp}.
     *
     * <pre>
     *   CustomTreeDefinition.forTree("mangrove1")
     *       .sapling(Blocks.MANGROVE_PROPAGULE)
     *       .worldGen(TreeMatchers.MANGROVE)
     *       .inBiome(Biomes.MANGROVE_SWAMP)
     *       .build();
     * </pre>
     */
    public static final Predicate<TreeConfiguration> MANGROVE = byTrunk(
        UpwardsBranchingTrunkPlacer.class
    ).and(byTrunkBlock(Blocks.MANGROVE_LOG));

    // =========================================================================
    // Nether tree matchers  (HugeFungusFeature, NOT TreeFeature)
    // Use these with CustomTreeDefinition.Builder.fungusWorldGen(), not .worldGen().
    // Both world-gen placement and bone-meal growth route through
    // HugeFungusFeature.place(), so a single registration covers both.
    // =========================================================================

    /**
     * Warped huge fungus – identified by {@code warped_stem} in the
     * {@link HugeFungusConfiguration#stemState}.
     *
     * <pre>
     *   CustomTreeDefinition.forTree("warped_tree")
     *       .fungusWorldGen(TreeMatchers.WARPED_FUNGUS)
     *       .biomes(BiomeMatchers.WARPED_FOREST)
     *       .build();
     * </pre>
     */
    public static final Predicate<HugeFungusConfiguration> WARPED_FUNGUS =
        config -> config.stemState.is(Blocks.WARPED_STEM);

    /**
     * Crimson huge fungus – identified by {@code crimson_stem} in the
     * {@link HugeFungusConfiguration#stemState}.
     *
     * <pre>
     *   CustomTreeDefinition.forTree("crimson_tree")
     *       .fungusWorldGen(TreeMatchers.CRIMSON_FUNGUS)
     *       .biomes(BiomeMatchers.CRIMSON_FOREST)
     *       .build();
     * </pre>
     */
    public static final Predicate<HugeFungusConfiguration> CRIMSON_FUNGUS =
        config -> config.stemState.is(Blocks.CRIMSON_STEM);

    /**
     * Matches any huge fungus – both warped and crimson.
     * Useful if you want one NBT file to replace all nether trees regardless
     * of type (pair with a biome filter to limit scope).
     */
    public static final Predicate<HugeFungusConfiguration> ANY_FUNGUS =
        config ->
            config.stemState.is(Blocks.WARPED_STEM) ||
            config.stemState.is(Blocks.CRIMSON_STEM);

    // =========================================================================
    // Mushroom matchers  (HugeMushroomFeature, covers world-gen + bone-meal)
    // Use these with CustomTreeDefinition.Builder.mushroomWorldGen(), not .worldGen().
    // Both world-gen placement and bone-meal growth on a red/brown mushroom
    // route through AbstractHugeMushroomFeature.place(), so one registration covers both.
    // =========================================================================

    /**
     * Large red mushroom – identified by {@link Blocks#RED_MUSHROOM_BLOCK} in
     * the {@link HugeMushroomFeatureConfiguration#capProvider}.
     *
     * <p>Appears naturally in {@code minecraft:mushroom_fields} and
     * {@code minecraft:dark_forest}.  Also grown by bone-mealing a planted
     * {@link Blocks#RED_MUSHROOM}.
     *
     * <pre>
     *   CustomTreeDefinition.forTree("redmushroom1")
     *       .mushroomWorldGen(TreeMatchers.RED_MUSHROOM)
     *       .biomes(BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST))
     *       .build();
     * </pre>
     */
    public static final Predicate<
        HugeMushroomFeatureConfiguration
    > RED_MUSHROOM = config -> {
        try {
            return config
                .capProvider()
                .getState(null, RandomSource.create(0L), BlockPos.ZERO)
                .is(Blocks.RED_MUSHROOM_BLOCK);
        } catch (Exception e) {
            return false;
        }
    };

    /**
     * Large brown mushroom – identified by {@link Blocks#BROWN_MUSHROOM_BLOCK}
     * in the {@link HugeMushroomFeatureConfiguration#capProvider}.
     *
     * <p>Appears naturally in {@code minecraft:mushroom_fields} and
     * {@code minecraft:dark_forest}.  Also grown by bone-mealing a planted
     * {@link Blocks#BROWN_MUSHROOM}.
     *
     * <pre>
     *   CustomTreeDefinition.forTree("brownmushroom1")
     *       .mushroomWorldGen(TreeMatchers.BROWN_MUSHROOM)
     *       .biomes(BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST))
     *       .build();
     * </pre>
     */
    public static final Predicate<
        HugeMushroomFeatureConfiguration
    > BROWN_MUSHROOM = config -> {
        try {
            return config
                .capProvider()
                .getState(null, RandomSource.create(0L), BlockPos.ZERO)
                .is(Blocks.BROWN_MUSHROOM_BLOCK);
        } catch (Exception e) {
            return false;
        }
    };

    /**
     * Matches any large mushroom – either red or brown.
     * Useful when one NBT file should replace both types (combine with a biome
     * filter to limit scope).
     */
    public static final Predicate<
        HugeMushroomFeatureConfiguration
    > ANY_MUSHROOM = config ->
        RED_MUSHROOM.test(config) || BROWN_MUSHROOM.test(config);

    // =========================================================================
    // Combinators
    // =========================================================================

    /**
     * Returns a predicate that is {@code true} when ANY of the supplied
     * matchers is {@code true}.  Useful to map multiple vanilla variants to
     * a single NBT file:
     * <pre>
     *   TreeMatchers.any(TreeMatchers.OAK, TreeMatchers.FANCY_OAK)
     * </pre>
     */
    @SafeVarargs
    public static Predicate<TreeConfiguration> any(
        Predicate<TreeConfiguration>... matchers
    ) {
        return config -> {
            for (Predicate<TreeConfiguration> m : matchers) {
                if (m.test(config)) return true;
            }
            return false;
        };
    }

    // =========================================================================
    // Low-level helpers – build your own matchers with these
    // =========================================================================

    /**
     * Matches trees whose {@link FoliagePlacer} is an instance of {@code cls}.
     */
    public static Predicate<TreeConfiguration> byFoliage(
        Class<? extends FoliagePlacer> cls
    ) {
        return config -> cls.isInstance(config.foliagePlacer);
    }

    /**
     * Matches trees whose {@link TrunkPlacer} is an instance of {@code cls}.
     */
    public static Predicate<TreeConfiguration> byTrunk(
        Class<? extends TrunkPlacer> cls
    ) {
        return config -> cls.isInstance(config.trunkPlacer);
    }

    /**
     * Matches trees whose trunk provider yields the given {@link Block}.
     *
     * <p>This is the primary way to distinguish oak, birch, and small jungle
     * trees in 1.21 because they all share {@link BlobFoliagePlacer}.
     *
     * <p>Implementation note: {@code SimpleStateProvider} ignores both the
     * {@link RandomSource} and the {@link BlockPos} arguments, so passing
     * {@link BlockPos#ZERO} and a freshly created source is safe and free of
     * side-effects.
     */
    public static Predicate<TreeConfiguration> byTrunkBlock(Block block) {
        return config -> {
            try {
                return config.trunkProvider
                    .getState(null, RandomSource.create(0L), BlockPos.ZERO)
                    .is(block);
            } catch (Exception e) {
                // Exotic modded providers that throw on a null-like call –
                // just don't match rather than crashing worldgen.
                return false;
            }
        };
    }
}
