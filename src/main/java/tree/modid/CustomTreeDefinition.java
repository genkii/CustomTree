package tree.modid;

import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

/**
 * Describes one custom tree replacement:
 *   - which NBT structure file to place
 *   - which sapling block (if any) triggers it when grown
 *   - how to recognise it during world-generation (optional predicate)
 *
 * Create instances with the fluent builder:
 * <pre>
 *   CustomTreeDefinition.forTree("oak_tree")
 *       .sapling(Blocks.OAK_SAPLING)
 *       .worldGen(TreeMatchers.OAK)
 *       .build();
 * </pre>
 *
 * The NBT file must be placed at:
 *   src/main/resources/data/custom-tree/structures/<nbtName>.nbt
 */
public final class CustomTreeDefinition {

    private final String nbtResourcePath; // "/data/custom-tree/structures/<name>.nbt"
    private final Block saplingBlock; // null → not triggered by sapling growth
    private final Predicate<TreeConfiguration> worldGenMatcher; // null → not triggered during world-gen

    /**
     * Optional biome filter.  When non-null, this definition only applies in
     * biomes where the predicate returns {@code true} – for both world-gen
     * placement <em>and</em> sapling growth.
     * {@code null} = match every biome (default).
     */
    private final Predicate<Holder<Biome>> biomeMatcher;

    /**
     * World-gen matcher for nether trees ({@code HugeFungusFeature}).
     * Separate from {@link #worldGenMatcher} because nether fungi use a
     * completely different feature class and configuration type.
     * {@code null} = this definition does not replace any huge fungus.
     */
    private final Predicate<HugeFungusConfiguration> hugeFungusWorldGenMatcher;

    /**
     * World-gen matcher for overworld large mushrooms ({@code HugeMushroomFeature}).
     * Covers both world-gen placement (Mushroom Fields, Dark Forest) and
     * bone-meal growth on a planted red or brown mushroom.
     * {@code null} = this definition does not replace any huge mushroom.
     */
    private final Predicate<
        HugeMushroomFeatureConfiguration
    > hugeMushroomWorldGenMatcher;

    /**
     * Minimum distance in blocks (radius) that must separate two trees of this
     * type during world generation.  0 = disabled (vanilla spacing applies).
     */
    private final int minSpacing;

    /**
     * Relative weight used when multiple definitions match the same tree type.
     * A definition with {@code weight = 2} is twice as likely to be chosen as
     * one with {@code weight = 1}.  Defaults to {@code 1}.
     */
    private final int weight;

    /**
     * When {@code true} this definition is placed in the <em>rare pool</em>.
     * The rare pool is only drawn from 2.5 % of the time (see
     * {@link CustomTreeRegistry#RARE_POOL_PROBABILITY}); the remaining 97.5 %
     * of selections come from the normal pool.
     *
     * <p>Set via {@link Builder#rare()}.
     */
    private final boolean rare;

    /**
     * Optional predicate that validates the ground block at the placement
     * origin before the structure is placed.  {@code null} = no floor check.
     *
     * <p>For nether fungi the tested position is {@code origin} (which IS the
     * nylium/base block in vanilla {@code HugeFungusFeature}).
     * For overworld trees it is {@code origin} (the surface block at the
     * feature origin – typically grass/dirt).
     */
    private final Predicate<BlockState> validFloor;

    private CustomTreeDefinition(Builder b) {
        this.nbtResourcePath = b.nbtResourcePath;
        this.saplingBlock = b.saplingBlock;
        this.worldGenMatcher = b.worldGenMatcher;
        this.minSpacing = b.minSpacing;
        this.weight = b.weight;
        this.validFloor = b.validFloor;
        this.biomeMatcher = b.biomeMatcher;
        this.hugeFungusWorldGenMatcher = b.hugeFungusWorldGenMatcher;
        this.hugeMushroomWorldGenMatcher = b.hugeMushroomWorldGenMatcher;
        this.rare = b.rare;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getNbtResourcePath() {
        return nbtResourcePath;
    }

    public Block getSaplingBlock() {
        return saplingBlock;
    }

    /**
     * Minimum block radius between two trees of this type during world-gen.
     * {@code 0} means no extra spacing check is performed.
     */
    public int getMinSpacing() {
        return minSpacing;
    }

    /**
     * Relative selection weight.  When several definitions match the same tree
     * type, the registry performs a weighted random draw so that higher-weight
     * variants appear more often.  Always {@code >= 1}.
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Whether this definition belongs to the <em>rare pool</em>.
     * Rare-pool trees are only chosen 2.5 % of the time (controlled by
     * {@link CustomTreeRegistry#RARE_POOL_PROBABILITY}); the other 97.5 %
     * of selections draw from the normal pool.
     */
    public boolean isRare() {
        return rare;
    }

    /**
     * Returns the floor-validation predicate, or {@code null} when no floor
     * check has been configured for this definition.
     */
    public Predicate<BlockState> getValidFloor() {
        return validFloor;
    }

    /** True when the sapling block at the growth site should trigger this tree. */
    public boolean matchesSapling(Block block) {
        return saplingBlock != null && saplingBlock == block;
    }

    /** True when the given world-gen TreeConfiguration should be replaced by this tree. */
    public boolean matchesWorldGen(TreeConfiguration config) {
        return worldGenMatcher != null && worldGenMatcher.test(config);
    }

    /**
     * True when the given {@link HugeFungusConfiguration} (nether tree world-gen
     * or bone-meal growth) should be replaced by this custom tree.
     */
    public boolean matchesFungusWorldGen(HugeFungusConfiguration config) {
        return (
            hugeFungusWorldGenMatcher != null &&
            hugeFungusWorldGenMatcher.test(config)
        );
    }

    /**
     * True when the given {@link HugeMushroomFeatureConfiguration} (large mushroom
     * world-gen or bone-meal growth on a red/brown mushroom) should be
     * replaced by this custom tree.
     */
    public boolean matchesMushroomWorldGen(
        HugeMushroomFeatureConfiguration config
    ) {
        return (
            hugeMushroomWorldGenMatcher != null &&
            hugeMushroomWorldGenMatcher.test(config)
        );
    }

    /**
     * True when this definition should be active in the given biome.
     * Always returns {@code true} when no biome filter has been set.
     */
    public boolean matchesBiome(Holder<Biome> biome) {
        return biomeMatcher == null || biomeMatcher.test(biome);
    }

    @Override
    public String toString() {
        return "CustomTreeDefinition{" + nbtResourcePath + "}";
    }

    // -------------------------------------------------------------------------
    // Builder entry-point
    // -------------------------------------------------------------------------

    /**
     * @param nbtName  Filename without extension, e.g. {@code "oak_tree"}.
     *                 Resolves to {@code /data/custom-tree/structures/oak_tree.nbt}.
     */
    public static Builder forTree(String nbtName) {
        return new Builder(nbtName);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {

        private final String nbtResourcePath;
        private Block saplingBlock;
        private Predicate<TreeConfiguration> worldGenMatcher;
        private int minSpacing = 0; // 0 = no extra spacing check
        private int weight = 1; // relative selection weight
        private boolean rare = false; // false = normal pool
        private Predicate<Holder<Biome>> biomeMatcher = null; // null = any biome
        private Predicate<HugeFungusConfiguration> hugeFungusWorldGenMatcher =
            null;
        private Predicate<
            HugeMushroomFeatureConfiguration
        > hugeMushroomWorldGenMatcher = null;
        private Predicate<BlockState> validFloor = null; // null = no floor check

        private Builder(String nbtName) {
            if (nbtName == null || nbtName.isBlank()) {
                throw new IllegalArgumentException(
                    "CustomTreeDefinition nbtName must not be null or blank"
                );
            }
            this.nbtResourcePath =
                "/data/custom-tree/structures/" + nbtName + ".nbt";
        }

        /** Sapling that, when grown (random tick or bone meal), places this custom tree. */
        public Builder sapling(Block sapling) {
            this.saplingBlock = sapling;
            return this;
        }

        /**
         * Predicate applied to the {@link TreeConfiguration} of every world-gen tree.
         * Return {@code true} to replace that tree with this custom one.
         * Use {@link TreeMatchers} for the pre-built vanilla matchers.
         */
        public Builder worldGen(Predicate<TreeConfiguration> matcher) {
            this.worldGenMatcher = matcher;
            return this;
        }

        /**
         * Minimum distance in blocks (radius) that must separate two world-gen
         * trees of this type.  When a placement attempt falls within this radius
         * of an already-placed tree of the same type, the whole tree is skipped
         * (no vanilla fallback either) so the forest feels less dense.
         *
         * <p>Examples:
         * <ul>
         *   <li>{@code 0}  – disabled; vanilla frequency/spacing applies (default)</li>
         *   <li>{@code 8}  – trees must be at least 8 blocks apart</li>
         *   <li>{@code 16} – trees must be at least 16 blocks apart (noticeably sparse)</li>
         * </ul>
         *
         * <p>Only affects world generation; sapling growth always proceeds regardless
         * of spacing so players can plant trees wherever they like.
         */
        public Builder minSpacing(int blocks) {
            if (blocks < 0) throw new IllegalArgumentException(
                "minSpacing must be >= 0, got: " + blocks
            );
            this.minSpacing = blocks;
            return this;
        }

        /**
         * Relative likelihood of this variant being chosen when multiple
         * definitions match the same tree type or sapling.
         *
         * <p>Examples:
         * <ul>
         *   <li>Two definitions both with {@code weight(1)} → 50 % / 50 %</li>
         *   <li>Weights {@code 3} and {@code 1} → 75 % / 25 %</li>
         * </ul>
         *
         * <p>The default is {@code 1}.  Must be {@code >= 1}.
         */
        public Builder weight(int w) {
            if (w < 1) throw new IllegalArgumentException(
                "weight must be >= 1, got: " + w
            );
            this.weight = w;
            return this;
        }

        /**
         * Restricts this definition to biomes where {@code matcher} returns
         * {@code true} – applies to both world-gen placement and sapling growth.
         *
         * <p>Use {@link BiomeMatchers} for the pre-built vanilla predicates:
         * <pre>
         *   .biomes(BiomeMatchers.IS_FOREST)
         *   .biomes(BiomeMatchers.any(BiomeMatchers.PLAINS, BiomeMatchers.MEADOW))
         * </pre>
         *
         * <p>Omit this call (or pass {@code null}) to match every biome.
         */
        public Builder biomes(Predicate<Holder<Biome>> matcher) {
            this.biomeMatcher = matcher;
            return this;
        }

        /**
         * Shorthand for restricting to a single specific biome.
         *
         * <pre>
         *   .inBiome(Biomes.FOREST)
         *   .inBiome(Biomes.PLAINS)
         * </pre>
         */
        public Builder inBiome(ResourceKey<Biome> key) {
            return biomes(BiomeMatchers.is(key));
        }

        /**
         * Shorthand for restricting to any of several specific biomes.
         *
         * <pre>
         *   .inBiomes(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST)
         * </pre>
         */
        @SafeVarargs
        public final Builder inBiomes(ResourceKey<Biome>... keys) {
            return biomes(BiomeMatchers.isAny(keys));
        }

        /**
         * Predicate applied to the {@link HugeFungusConfiguration} of every
         * nether huge-fungus feature placement (world-gen <em>and</em> bone-meal
         * growth – both route through {@code HugeFungusFeature.place()}).
         *
         * <p>Use {@link TreeMatchers#WARPED_FUNGUS} or
         * {@link TreeMatchers#CRIMSON_FUNGUS} for the pre-built matchers:
         * <pre>
         *   CustomTreeDefinition.forTree("warped_tree")
         *       .fungusWorldGen(TreeMatchers.WARPED_FUNGUS)
         *       .biomes(BiomeMatchers.WARPED_FOREST)
         *       .build();
         * </pre>
         *
         * <p>Omit this call (or pass {@code null}) if this definition is not a
         * nether tree.
         */
        public Builder fungusWorldGen(
            Predicate<HugeFungusConfiguration> matcher
        ) {
            this.hugeFungusWorldGenMatcher = matcher;
            return this;
        }

        /**
         * Predicate applied to the {@link HugeMushroomFeatureConfiguration} of every
         * huge-mushroom feature placement (world-gen <em>and</em> bone-meal
         * growth – both route through {@code AbstractHugeMushroomFeature.place()}).
         *
         * <p>Use {@link TreeMatchers#RED_MUSHROOM}, {@link TreeMatchers#BROWN_MUSHROOM},
         * or {@link TreeMatchers#ANY_MUSHROOM} for the pre-built matchers:
         * <pre>
         *   // Red mushroom in Mushroom Fields and Dark Forest:
         *   CustomTreeDefinition.forTree("redmushroom1")
         *       .mushroomWorldGen(TreeMatchers.RED_MUSHROOM)
         *       .biomes(BiomeMatchers.any(
         *           BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST))
         *       .build();
         * </pre>
         *
         * <p>Omit this call (or pass {@code null}) if this definition is not a
         * large mushroom.
         */
        public Builder mushroomWorldGen(
            Predicate<HugeMushroomFeatureConfiguration> matcher
        ) {
            this.hugeMushroomWorldGenMatcher = matcher;
            return this;
        }

        /**
         * Marks this definition as a <em>rare</em> tree that belongs to the rare
         * selection pool.  Only 2.5 % of placements (controlled by
         * {@link CustomTreeRegistry#RARE_POOL_PROBABILITY}) draw from the rare
         * pool; all other placements use the normal pool.
         *
         * <p>Rare trees within the rare pool are still selected by their
         * {@link #weight(int)} value, so you can make one rare variant twice
         * as common as another with {@code .rare().weight(2)}.
         *
         * <p>If no normal-pool trees exist for a given tree type, the rare pool
         * is used unconditionally (no gate roll).  Conversely, if no rare-pool
         * trees exist, the normal pool is always used.
         */
        public Builder rare() {
            this.rare = true;
            return this;
        }

        /**
         * Restricts placement to positions where the ground block satisfies
         * {@code predicate}.
         *
         * <p>For nether fungi the checked block is at {@code origin} (the
         * nylium position).  For overworld trees it is also {@code origin}
         * (the surface block – typically grass or dirt).
         *
         * <p>Use the shorthand helpers {@link #onNylium()} and {@link #onDirt()}
         * for the most common cases, or supply any {@link Predicate}:
         * <pre>
         *   // Only on warped nylium:
         *   .validFloor(state -> state.is(Blocks.WARPED_NYLIUM))
         *   // Only on grass block:
         *   .validFloor(state -> state.is(Blocks.GRASS_BLOCK))
         * </pre>
         *
         * <p>This check is applied during world generation (both
         * {@code TreeFeatureMixin} and {@code HugeFungusFeatureMixin}).
         * Sapling growth is intentionally unaffected.
         */
        public Builder validFloor(Predicate<BlockState> predicate) {
            this.validFloor = predicate;
            return this;
        }

        /**
         * Shorthand: only place on nylium (warped or crimson).
         * Equivalent to {@code validFloor(state -> state.is(BlockTags.NYLIUM))}.
         */
        public Builder onNylium() {
            return validFloor(state -> state.is(BlockTags.NYLIUM));
        }

        /**
         * Shorthand: only place on dirt-like surface blocks
         * (grass block, dirt, coarse dirt, podzol, rooted dirt, …).
         * Equivalent to {@code validFloor(state -> state.is(BlockTags.DIRT))}.
         */
        public Builder onDirt() {
            return validFloor(state -> state.is(BlockTags.DIRT));
        }

        public CustomTreeDefinition build() {
            return new CustomTreeDefinition(this);
        }
    }
}
