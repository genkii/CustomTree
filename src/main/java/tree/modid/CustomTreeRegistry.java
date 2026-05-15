package tree.modid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/**
 * Central registry for all custom tree definitions.
 *
 * <p>Call {@link #register(CustomTreeDefinition)} inside
 * {@link CustomTree#onInitialize()} before any world is loaded.
 *
 * <h3>Multiple variants for the same tree type</h3>
 * Registering several definitions that share the same world-gen matcher or
 * the same sapling block is fully supported.  When more than one definition
 * matches (after biome filtering), one is chosen at random using a
 * <em>weighted</em> lottery:
 *
 * <pre>
 *   // Equal 50 / 50 across the whole world:
 *   registerTree("oak_tree_a", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
 *   registerTree("oak_tree_b", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
 *
 *   // Different trees per biome – effectively infinite unique looks:
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("forest_oak")
 *           .sapling(Blocks.OAK_SAPLING)
 *           .worldGen(TreeMatchers.OAK)
 *           .biomes(BiomeMatchers.IS_FOREST)
 *           .minSpacing(10)
 *           .build());
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("plains_oak")
 *           .sapling(Blocks.OAK_SAPLING)
 *           .worldGen(TreeMatchers.OAK)
 *           .biomes(BiomeMatchers.PLAINS)
 *           .minSpacing(12)
 *           .build());
 *
 *   // Weighted mix within one biome:
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("oak_common")
 *           .sapling(Blocks.OAK_SAPLING)
 *           .worldGen(TreeMatchers.OAK)
 *           .biomes(BiomeMatchers.IS_FOREST)
 *           .weight(3)   // 75 %
 *           .build());
 *   CustomTreeRegistry.register(
 *       CustomTreeDefinition.forTree("oak_rare")
 *           .sapling(Blocks.OAK_SAPLING)
 *           .worldGen(TreeMatchers.OAK)
 *           .biomes(BiomeMatchers.IS_FOREST)
 *           .weight(1)   // 25 %
 *           .build());
 * </pre>
 */
public final class CustomTreeRegistry {

    private CustomTreeRegistry() {}

    /**
     * Probability of drawing from the rare pool instead of the normal pool
     * when both pools contain at least one matching definition.
     *
     * At 0.025 (2.5%) exactly 1 in 40 placements picks a rare tree;
     * the other 39 in 40 pick a normal tree.
     */
    public static final float RARE_POOL_PROBABILITY = 0.025f;

    private static final List<CustomTreeDefinition> DEFINITIONS =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void register(CustomTreeDefinition definition) {
        DEFINITIONS.add(definition);
    }

    /** Read-only view of every registered definition. */
    public static List<CustomTreeDefinition> getAll() {
        return Collections.unmodifiableList(DEFINITIONS);
    }

    // -------------------------------------------------------------------------
    // Lookup – all matches (tree-type + biome)
    // -------------------------------------------------------------------------

    /**
     * Returns every definition whose sapling block matches {@code block} AND
     * whose biome filter accepts {@code biome}.
     *
     * <p>Empty when no definition covers this sapling in this biome.
     */
    public static List<CustomTreeDefinition> findAllBySapling(
        Block block,
        Holder<Biome> biome
    ) {
        List<CustomTreeDefinition> result = new ArrayList<>();
        for (CustomTreeDefinition def : DEFINITIONS) {
            if (def.matchesSapling(block) && def.matchesBiome(biome)) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Returns every definition whose world-gen predicate accepts {@code config}
     * AND whose biome filter accepts {@code biome}.
     *
     * <p>Empty when no definition covers this tree configuration in this biome.
     */
    public static List<CustomTreeDefinition> findAllByTreeConfig(
        TreeConfiguration config,
        Holder<Biome> biome
    ) {
        List<CustomTreeDefinition> result = new ArrayList<>();
        for (CustomTreeDefinition def : DEFINITIONS) {
            if (def.matchesWorldGen(config) && def.matchesBiome(biome)) {
                result.add(def);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Lookup – weighted random pick (tree-type + biome)
    // -------------------------------------------------------------------------

    /**
     * Randomly selects one definition whose sapling matches {@code block} and
     * whose biome filter accepts {@code biome}, weighted by each definition's
     * {@link CustomTreeDefinition#getWeight()}.
     *
     * <p>Returns {@link Optional#empty()} when no definition covers this
     * sapling in this biome, in which case vanilla sapling growth proceeds.
     *
     * @param block  The sapling block that just grew.
     * @param biome  The biome at the growth position.
     * @param random The {@link RandomSource} from the grow event – keeps results
     *               reproducible for a given world seed.
     */
    public static Optional<CustomTreeDefinition> pickBySapling(
        Block block,
        Holder<Biome> biome,
        RandomSource random
    ) {
        return pickWeighted(findAllBySapling(block, biome), random);
    }

    /**
     * Randomly selects one definition whose world-gen predicate accepts
     * {@code config} and whose biome filter accepts {@code biome}, weighted by
     * each definition's {@link CustomTreeDefinition#getWeight()}.
     *
     * <p>Returns {@link Optional#empty()} when no definition covers this tree
     * in this biome, in which case the vanilla tree is generated normally.
     *
     * @param config The {@link TreeConfiguration} of the tree being placed.
     * @param biome  The biome at the placement position.
     * @param random The {@link RandomSource} from the feature context – keeps
     *               results reproducible for a given world seed.
     */
    public static Optional<CustomTreeDefinition> pickByWorldGen(
        TreeConfiguration config,
        Holder<Biome> biome,
        RandomSource random
    ) {
        return pickWeighted(findAllByTreeConfig(config, biome), random);
    }

    // -------------------------------------------------------------------------
    // Lookup – fungus world-gen (HugeFungusFeature, covers world-gen + bone-meal)
    // -------------------------------------------------------------------------

    /**
     * Returns every definition whose {@link CustomTreeDefinition#matchesFungusWorldGen}
     * predicate accepts {@code config} AND whose biome filter accepts {@code biome}.
     *
     * <p>Empty when no definition covers this fungus configuration in this biome.
     */
    public static List<CustomTreeDefinition> findAllByFungusConfig(
        HugeFungusConfiguration config,
        Holder<Biome> biome
    ) {
        List<CustomTreeDefinition> result = new ArrayList<>();
        for (CustomTreeDefinition def : DEFINITIONS) {
            if (def.matchesFungusWorldGen(config) && def.matchesBiome(biome)) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Randomly selects one definition whose fungus world-gen predicate accepts
     * {@code config} and whose biome filter accepts {@code biome}, weighted by
     * each definition's {@link CustomTreeDefinition#getWeight()}.
     *
     * <p>Returns {@link Optional#empty()} when no definition covers this fungus
     * configuration in this biome, in which case the vanilla huge fungus is
     * generated normally.
     *
     * <p>This method covers <em>both</em> world-generation and bone-meal growth
     * because bone-meal on a planted fungus also calls
     * {@code HugeFungusFeature.place()} internally.
     *
     * @param config The {@link HugeFungusConfiguration} of the fungus being placed.
     * @param biome  The biome at the placement position.
     * @param random The {@link RandomSource} from the feature context.
     */
    public static Optional<CustomTreeDefinition> pickByFungusWorldGen(
        HugeFungusConfiguration config,
        Holder<Biome> biome,
        RandomSource random
    ) {
        return pickWeighted(findAllByFungusConfig(config, biome), random);
    }

    // -------------------------------------------------------------------------
    // Lookup – mushroom world-gen (HugeMushroomFeature, covers world-gen + bone-meal)
    // -------------------------------------------------------------------------

    /**
     * Returns every definition whose {@link CustomTreeDefinition#matchesMushroomWorldGen}
     * predicate accepts {@code config} AND whose biome filter accepts {@code biome}.
     *
     * <p>Empty when no definition covers this mushroom configuration in this biome.
     */
    public static List<CustomTreeDefinition> findAllByMushroomConfig(
        HugeMushroomFeatureConfiguration config,
        Holder<Biome> biome
    ) {
        List<CustomTreeDefinition> result = new ArrayList<>();
        for (CustomTreeDefinition def : DEFINITIONS) {
            if (
                def.matchesMushroomWorldGen(config) && def.matchesBiome(biome)
            ) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * Randomly selects one definition whose mushroom world-gen predicate accepts
     * {@code config} and whose biome filter accepts {@code biome}, weighted by
     * each definition's {@link CustomTreeDefinition#getWeight()}.
     *
     * <p>Returns {@link Optional#empty()} when no definition covers this mushroom
     * configuration in this biome, in which case the vanilla huge mushroom is
     * generated normally.
     *
     * <p>This method covers <em>both</em> world-generation and bone-meal growth
     * because bone-meal on a planted red/brown mushroom also calls
     * {@code HugeMushroomFeature.place()} internally.
     *
     * @param config The {@link HugeMushroomFeatureConfiguration} of the mushroom being placed.
     * @param biome  The biome at the placement position.
     * @param random The {@link RandomSource} from the feature context.
     */
    public static Optional<CustomTreeDefinition> pickByMushroomWorldGen(
        HugeMushroomFeatureConfiguration config,
        Holder<Biome> biome,
        RandomSource random
    ) {
        return pickWeighted(findAllByMushroomConfig(config, biome), random);
    }

    // -------------------------------------------------------------------------
    // Internal weighted lottery
    // -------------------------------------------------------------------------

    /**
     * Picks one entry from {@code pool} using a two-phase weighted lottery.
     *
     * <p>Unlike the previous implementation this method performs zero heap
     * allocations: instead of splitting the pool into two sub-lists it does
     * two linear passes — one to accumulate weights per kind (rare / normal)
     * and one to select the winner — keeping GC pressure low during the
     * heavy world-generation phase.
     *
     * <p>Phase 1 – rare gate: when the pool contains both rare and normal
     * entries a random float is drawn.  If it is below
     * {@link #RARE_POOL_PROBABILITY} (2.5%) the rare sub-set is used;
     * otherwise the normal sub-set is used.  When only one kind exists the
     * gate roll is skipped entirely.
     *
     * <p>Phase 2 – weighted draw: within the chosen kind one entry is
     * selected proportional to {@link CustomTreeDefinition#getWeight()}.
     */
    static Optional<CustomTreeDefinition> pickWeighted(
        List<CustomTreeDefinition> pool,
        RandomSource random
    ) {
        if (pool.isEmpty()) return Optional.empty();
        if (pool.size() == 1) return Optional.of(pool.get(0));

        // Pass 1: accumulate total weight for each kind in one scan.
        boolean hasRare = false,
            hasNormal = false;
        int rareTotalWeight = 0,
            normalTotalWeight = 0;
        for (int i = 0, n = pool.size(); i < n; i++) {
            CustomTreeDefinition def = pool.get(i);
            if (def.isRare()) {
                hasRare = true;
                rareTotalWeight += def.getWeight();
            } else {
                hasNormal = true;
                normalTotalWeight += def.getWeight();
            }
        }

        // Decide which kind to draw from.
        final boolean useRare =
            hasRare &&
            (!hasNormal || random.nextFloat() < RARE_POOL_PROBABILITY);
        int totalWeight = useRare ? rareTotalWeight : normalTotalWeight;

        // Edge case: every entry of the chosen kind has weight ≤ 0.
        if (totalWeight <= 0) {
            for (int i = pool.size() - 1; i >= 0; i--) {
                if (pool.get(i).isRare() == useRare) return Optional.of(
                    pool.get(i)
                );
            }
            return Optional.of(pool.get(pool.size() - 1));
        }

        // Pass 2: weighted draw within the chosen kind.
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0, n = pool.size(); i < n; i++) {
            CustomTreeDefinition def = pool.get(i);
            if (def.isRare() != useRare) continue;
            cumulative += def.getWeight();
            if (roll < cumulative) return Optional.of(def);
        }

        // Safety fallback — should be unreachable.
        for (int i = pool.size() - 1; i >= 0; i--) {
            if (pool.get(i).isRare() == useRare) return Optional.of(
                pool.get(i)
            );
        }
        return Optional.of(pool.get(pool.size() - 1));
    }
}
