package tree.modid;

import java.util.function.Predicate;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomTree implements ModInitializer {

    public static final String MOD_ID = "custom-tree";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public record GiveItemPayload(
        Identifier itemId
    ) implements CustomPacketPayload {
        public static final Type<GiveItemPayload> TYPE = new Type<>(
            Identifier.parse("custom-tree:give_item")
        );

        public static final StreamCodec<
            RegistryFriendlyByteBuf,
            GiveItemPayload
        > CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeIdentifier(payload.itemId),
            buf -> new GiveItemPayload(buf.readIdentifier())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @Override
    public void onInitialize() {
        // =====================================================================
        // CUSTOM TREE REGISTRATIONS
        // =====================================================================
        // Each entry links:
        //   1. An NBT file name  (drop the file in
        //        src/main/resources/data/custom-tree/structures/<name>.nbt)
        //   2. The sapling block that grows this tree (bone meal + random tick)
        //   3. A world-gen matcher so naturally-spawned trees are also replaced
        //   4. (optional) minSpacing – minimum block radius between two world-gen
        //      trees of this type.  0 = vanilla spacing (default).
        //   5. (optional) biomes – restrict to specific biomes (default = all biomes).
        //      Use BiomeMatchers constants or the builder's .biomes() / .inBiome().
        //
        // ── Per-biome trees (the key to infinite variety) ────────────────────
        //
        //   registerTree("forest_oak",  Blocks.OAK_SAPLING, TreeMatchers.OAK, 10,
        //                BiomeMatchers.IS_FOREST);
        //   registerTree("plains_oak",  Blocks.OAK_SAPLING, TreeMatchers.OAK, 14,
        //                BiomeMatchers.PLAINS);
        //   // → totally different oak in each biome; no interference.
        //
        // ── Multiple variants inside one biome ───────────────────────────────
        //
        //   registerTree("oak_a", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10,
        //                BiomeMatchers.IS_FOREST);
        //   registerTree("oak_b", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10,
        //                BiomeMatchers.IS_FOREST);
        //   // → 50 / 50 random mix inside every forest biome.
        //   // There is no limit – register 10, 20, 100 variants per biome.
        //
        // ── Unequal rarity (weighted chance) ─────────────────────────────────
        //
        //   CustomTreeRegistry.register(
        //       CustomTreeDefinition.forTree("oak_common")
        //           .sapling(Blocks.OAK_SAPLING)
        //           .worldGen(TreeMatchers.OAK)
        //           .biomes(BiomeMatchers.IS_FOREST)
        //           .minSpacing(10)
        //           .weight(3)   // 75 %
        //           .build());
        //   CustomTreeRegistry.register(
        //       CustomTreeDefinition.forTree("oak_rare")
        //           .sapling(Blocks.OAK_SAPLING)
        //           .worldGen(TreeMatchers.OAK)
        //           .biomes(BiomeMatchers.IS_FOREST)
        //           .minSpacing(10)
        //           .weight(1)   // 25 %
        //           .build());
        //
        // ── Quick reference ──────────────────────────────────────────────────
        //
        //   registerTree("oak_tree", Blocks.OAK_SAPLING, TreeMatchers.OAK);
        //   registerTree("oak_tree", Blocks.OAK_SAPLING, TreeMatchers.OAK, 12);
        //   registerTree("oak_tree", Blocks.OAK_SAPLING, TreeMatchers.OAK, 12,
        //                BiomeMatchers.IS_FOREST);
        //
        // ── Nether trees ─────────────────────────────────────────────────────
        //
        //   registerFungus("warped_tree", TreeMatchers.WARPED_FUNGUS,
        //                  BiomeMatchers.WARPED_FOREST);
        //   registerFungus("crimson_tree", TreeMatchers.CRIMSON_FUNGUS,
        //                  BiomeMatchers.CRIMSON_FOREST);
        //   // Bone-meal growth is caught automatically – no sapling registration needed.
        //   // minSpacing of 6 prevents direct overlap; increase for sparser forests.
        //
        // ── BiomeMatchers quick reference ────────────────────────────────────
        //   Tags:  IS_FOREST  IS_TAIGA  IS_JUNGLE  IS_SAVANNA  IS_NETHER
        //   Biomes: PLAINS  FOREST  BIRCH_FOREST  DARK_FOREST  TAIGA  JUNGLE
        //           SAVANNA  CHERRY_GROVE  PALE_GARDEN
        //           WARPED_FOREST  CRIMSON_FOREST
        //
        // ── Spacing guide ────────────────────────────────────────────────────
        //   0      vanilla density
        //   6-8    slightly less dense
        //   10-14  noticeably more open
        //   16-24  sparse / parkland
        //   32+    very isolated
        // =====================================================================

        // ---- Oak ------------------------------------------------------------
        // Replace both regular and fancy oaks with one custom NBT.
        // To enable a 50/50 mix of two oaks, duplicate the line and change
        // the first argument to your second NBT file name.
        /*registerTree(
            "oak_tree",
            Blocks.OAK_SAPLING,
            TreeMatchers.any(TreeMatchers.OAK, TreeMatchers.FANCY_OAK),
            10
        );
        // registerTree("oak_tree_b", Blocks.OAK_SAPLING,
        //     TreeMatchers.any(TreeMatchers.OAK, TreeMatchers.FANCY_OAK), 10);

        // ---- Birch ----------------------------------------------------------
        registerTree(
            "birch_tree",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            10
        );

        // ---- Spruce ---------------------------------------------------------
        registerTree(
            "spruce_tree",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE,
            8
        );
        // Uncomment for separate pine / mega-spruce NBT files:
        // registerTree("pine_tree",        Blocks.SPRUCE_SAPLING, TreeMatchers.PINE,        8);
        // registerTree("mega_spruce_tree", Blocks.SPRUCE_SAPLING, TreeMatchers.MEGA_SPRUCE, 16);

        // ---- Jungle ---------------------------------------------------------
        registerTree(
            "jungle_tree",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.any(
                TreeMatchers.JUNGLE_SMALL,
                TreeMatchers.JUNGLE_MEGA
            ),
            12
        );

        // ---- Acacia ---------------------------------------------------------
        registerTree(
            "acacia_tree",
            Blocks.ACACIA_SAPLING,
            TreeMatchers.ACACIA,
            14
        );

        // ---- Dark Oak -------------------------------------------------------
        registerTree(
            "dark_oak_tree",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10
        );

        // ---- Cherry ---------------------------------------------------------
        registerTree(
            "cherry_tree",
            Blocks.CHERRY_SAPLING,
            TreeMatchers.CHERRY,
            12
        );

        // ---- Pale Oak (Pale Garden biome) -----------------------------------
        // Pale oak shares DarkOakTrunkPlacer + DarkOakFoliagePlacer with dark
        // oak; it is distinguished by its pale_oak_log trunk block.
        registerTree(
            "pale_oak_tree",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            12,
            BiomeMatchers.PALE_GARDEN
        );

        // ---- Warped Fungus (Nether – Warped Forest) -------------------------
        // Covers world-gen AND bone-meal growth automatically via
        // HugeFungusFeatureMixin.  Drop warped_tree.nbt in structures/.
        registerFungus(
            "warped_tree",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );

        // ---- Crimson Fungus (Nether – Crimson Forest) -----------------------
        registerFungus(
            "crimson_tree",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );*/

        // =====================================================================
        // Register the /customtrees debug command.
        // Usage in-game (requires op):
        //   /customtrees status          – lists registered trees & checks NBT files
        //   /customtrees test <name>     – force-places the NBT at your feet
        DebugCommand.register();

        // =====================================================================
        // Warped Tree
        registerFungus(
            "warpedtree1",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree2",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree3",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree4",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree5",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree6",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree7",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );
        registerFungus(
            "warpedtree8",
            TreeMatchers.WARPED_FUNGUS,
            6,
            BiomeMatchers.WARPED_FOREST
        );

        // =====================================================================
        // Pale Garden

        registerTree(
            "pale1",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        registerTree(
            "pale2",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        registerTree(
            "pale3",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        registerTree(
            "pale4",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        registerTree(
            "pale5",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        registerTree(
            "pale6",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        registerTree(
            "pale7",
            Blocks.PALE_OAK_SAPLING,
            TreeMatchers.PALE_OAK,
            6,
            BiomeMatchers.PALE_GARDEN
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking1")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking2")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking3")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking4")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking5")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking6")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("palecreeking7")
                .sapling(Blocks.PALE_OAK_SAPLING)
                .worldGen(TreeMatchers.PALE_OAK)
                .biomes(BiomeMatchers.PALE_GARDEN)
                .minSpacing(6)
                .rare() // 2.5 % rare pool
                .build()
        );

        // =====================================================================
        // Oak normal (oak1–oak10) – replaces only regular oak world-gen

        registerTree("oak1", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak2", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak3", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak4", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak5", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak6", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak7", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak8", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak9", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);
        registerTree("oak10", Blocks.OAK_SAPLING, TreeMatchers.OAK, 10);

        // =====================================================================
        // Fancy oak – oak11 + bigoak1-5 replace fancy-oak (large canopy) world-gen

        registerTree("oak11", Blocks.OAK_SAPLING, TreeMatchers.FANCY_OAK, 10);
        registerTree("bigoak1", Blocks.OAK_SAPLING, TreeMatchers.FANCY_OAK, 10);
        registerTree("bigoak2", Blocks.OAK_SAPLING, TreeMatchers.FANCY_OAK, 10);
        registerTree("bigoak3", Blocks.OAK_SAPLING, TreeMatchers.FANCY_OAK, 10);
        registerTree("bigoak4", Blocks.OAK_SAPLING, TreeMatchers.FANCY_OAK, 10);
        registerTree("bigoak5", Blocks.OAK_SAPLING, TreeMatchers.FANCY_OAK, 10);

        // =====================================================================
        // Swamp Oak

        registerTree("swampoak1", Blocks.OAK_SAPLING, TreeMatchers.SWAMP, 10);

        registerTree("swampoak2", Blocks.OAK_SAPLING, TreeMatchers.SWAMP, 10);

        registerTree("swampoak3", Blocks.OAK_SAPLING, TreeMatchers.SWAMP, 10);

        registerTree("swampoak5", Blocks.OAK_SAPLING, TreeMatchers.SWAMP, 10);

        registerTree("swampoak6", Blocks.OAK_SAPLING, TreeMatchers.SWAMP, 10);

        registerTree("swampoak7", Blocks.OAK_SAPLING, TreeMatchers.SWAMP, 10);

        // =====================================================================
        // Birch (regular birch forest, flower forest, windswept forest, etc.)
        // Excluded from old_growth_birch_forest so the taller old-birch variants
        // are used there instead – see the "Old Birch" section below.

        registerTree(
            "birchtree1",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree2",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );

        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("birchtree3")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(
                    BiomeMatchers.any(
                        BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                        BiomeMatchers.FOREST,
                        BiomeMatchers.FLOWER_FOREST
                    ).negate()
                )
                .minSpacing(9)
                .rare() // 2.5 % rare pool
                .build()
        );

        registerTree(
            "birchtree4",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree5",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree6",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("birchtree7")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(
                    BiomeMatchers.any(
                        BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                        BiomeMatchers.FOREST,
                        BiomeMatchers.FLOWER_FOREST
                    ).negate()
                )
                .minSpacing(9)
                .rare() // 2.5 % rare pool
                .build()
        );
        registerTree(
            "birchtree8",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree9",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree10",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("birchtree11")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(
                    BiomeMatchers.any(
                        BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                        BiomeMatchers.FOREST,
                        BiomeMatchers.FLOWER_FOREST
                    ).negate()
                )
                .minSpacing(9)
                .rare() // 2.5 % rare pool
                .build()
        );
        registerTree(
            "birchtree12",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree13",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree14",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree15",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("birchtree16")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(
                    BiomeMatchers.any(
                        BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                        BiomeMatchers.FOREST,
                        BiomeMatchers.FLOWER_FOREST
                    ).negate()
                )
                .minSpacing(9)
                .rare() // 2.5 % rare pool
                .build()
        );
        registerTree(
            "birchtree17",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );
        registerTree(
            "birchtree18",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(
                BiomeMatchers.OLD_GROWTH_BIRCH_FOREST,
                BiomeMatchers.FOREST,
                BiomeMatchers.FLOWER_FOREST
            ).negate()
        );

        // =====================================================================
        // Old Birch (Old Growth Birch Forest only – minecraft:old_growth_birch_forest)
        // These tall birch NBTs replace BOTH world-gen trees AND sapling growth
        // inside the Old Growth Birch Forest biome.
        // NBT files: oldbirch1.nbt – oldbirch12.nbt in data/custom-tree/structures/
        // Spacing of 12 keeps the canopy open enough to feel like the vanilla biome.

        registerTree(
            "oldbirch1",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        registerTree(
            "oldbirch2",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        registerTree(
            "oldbirch3",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        registerTree(
            "oldbirch4",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        registerTree(
            "oldbirch5",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("oldbirch6")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(BiomeMatchers.OLD_GROWTH_BIRCH_FOREST)
                .minSpacing(10)
                .rare() // 2.5 % rare pool
                .build()
        );
        registerTree(
            "oldbirch7",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        registerTree(
            "oldbirch8",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        registerTree(
            "oldbirch9",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            12,
            BiomeMatchers.OLD_GROWTH_BIRCH_FOREST
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("oldbirch10")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(BiomeMatchers.OLD_GROWTH_BIRCH_FOREST)
                .minSpacing(10)
                .rare() // 2.5 % rare pool
                .build()
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("oldbirch11")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(BiomeMatchers.OLD_GROWTH_BIRCH_FOREST)
                .minSpacing(10)
                .rare() // 2.5 % rare pool
                .build()
        );
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree("oldbirch12")
                .sapling(Blocks.BIRCH_SAPLING)
                .worldGen(TreeMatchers.BIRCH)
                .biomes(BiomeMatchers.OLD_GROWTH_BIRCH_FOREST)
                .minSpacing(10)
                .rare() // 2.5 % rare pool
                .build()
        );

        // =====================================================================
        // Forest Birch (forest + flower_forest only)
        // A stockier, more "forest-y" birch variant that replaces the standard
        // birch in forest and flower_forest biomes.  Normal birch (birchtree1-18)
        // is excluded from those two biomes, so these are the sole birch trees
        // that appear there during world-gen and sapling growth.
        // NBT files: forestbirch1.nbt, forestbirch2.nbt, forestbirch3.nbt, forestbirch4.nbt

        registerTree(
            "forestbirch1",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(BiomeMatchers.FOREST, BiomeMatchers.FLOWER_FOREST)
        );
        registerTree(
            "forestbirch2",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(BiomeMatchers.FOREST, BiomeMatchers.FLOWER_FOREST)
        );
        registerTree(
            "forestbirch3",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(BiomeMatchers.FOREST, BiomeMatchers.FLOWER_FOREST)
        );
        registerTree(
            "forestbirch4",
            Blocks.BIRCH_SAPLING,
            TreeMatchers.BIRCH,
            9,
            BiomeMatchers.any(BiomeMatchers.FOREST, BiomeMatchers.FLOWER_FOREST)
        );

        // =====================================================================
        // Brown / Red Mushroom
        // =====================================================================

        registerMushroom(
            "brownmushroom1",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom2",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom3",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom4",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom5",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom6",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom7",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom8",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom9",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );
        registerMushroom(
            "brownmushroom10",
            TreeMatchers.BROWN_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom1",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom2",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom3",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom4",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom5",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom6",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom7",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom8",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom9",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        registerMushroom(
            "redmushroom10",
            TreeMatchers.RED_MUSHROOM,
            BiomeMatchers.any(
                BiomeMatchers.MUSHROOM_FIELDS,
                BiomeMatchers.DARK_FOREST
            )
        );

        // Mangrove

        registerTree(
            "mangrove1",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove2",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove3",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove4",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove5",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove6",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove7",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );
        registerTree(
            "mangrove8",
            Blocks.MANGROVE_PROPAGULE,
            TreeMatchers.MANGROVE,
            10,
            BiomeMatchers.MANGROVE_SWAMP
        );

        // Dark Oak
        registerTree(
            "schwarzeiche1.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche2.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche3.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche4.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche5.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche6.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche7.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );
        registerTree(
            "schwarzeiche8.1",
            Blocks.DARK_OAK_SAPLING,
            TreeMatchers.DARK_OAK,
            10,
            BiomeMatchers.DARK_FOREST
        );

        // Crimson Forest

        registerFungus(
            "crimsontree1",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree2",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree3",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree4",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree5",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree6",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree7",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree8",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree9",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        registerFungus(
            "crimsontree10",
            TreeMatchers.CRIMSON_FUNGUS,
            6,
            BiomeMatchers.CRIMSON_FOREST
        );

        // Cherry
        registerTree("cherry1", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry2", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry3", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry4", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry5", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry6", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry7", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        registerTree("cherry8", Blocks.CHERRY_SAPLING, TreeMatchers.CHERRY, 12);

        // Azalea // TODO funktioniert nicht

        registerTreeWorldGenOnly("azalea1", TreeMatchers.AZALEA, 10);
        registerTreeWorldGenOnly("azalea2", TreeMatchers.AZALEA, 10);
        registerTreeWorldGenOnly("azalea3", TreeMatchers.AZALEA, 10);
        registerTreeWorldGenOnly("azalea4", TreeMatchers.AZALEA, 10);
        registerTreeWorldGenOnly("azalea5", TreeMatchers.AZALEA, 10);
        registerTreeWorldGenOnly("azalea6", TreeMatchers.AZALEA, 10);
        registerTreeWorldGenOnly("azalea7", TreeMatchers.AZALEA, 10);

        // Acacia

        registerTree("acacia1", Blocks.ACACIA_SAPLING, TreeMatchers.ACACIA, 14);

        registerTree("acacia2", Blocks.ACACIA_SAPLING, TreeMatchers.ACACIA, 14);

        registerTree("acacia3", Blocks.ACACIA_SAPLING, TreeMatchers.ACACIA, 14);

        registerTree("acacia4", Blocks.ACACIA_SAPLING, TreeMatchers.ACACIA, 14);

        // =====================================================================
        // Snowy Spruce
        // Biomes: snowy_taiga, snowy_plains, windswept_hills,
        //         windswept_forest, windswept_gravelly_hills
        // =====================================================================

        registerTree(
            "snowyspruce1",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce2",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce3",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce4",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce5",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce6",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        // snowyspruce7.nbt is missing – registration removed to avoid vanilla fallback
        registerTree(
            "snowyspruce8",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce9",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );
        registerTree(
            "snowyspruce10",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.SNOWY_SPRUCE_BIOMES
        );

        // Normal Taiga

        registerTree(
            "sprucetree2",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree3",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree4",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree5",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree6",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree7",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree8",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree9",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );
        registerTree(
            "sprucetree10",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.SPRUCE_ONLY,
            8,
            BiomeMatchers.NON_SNOWY_TAIGA
        );

        // Pine + Mega Pine + Mega Spruce
        // bigspruce NBTs cover all three pine/mega variants across every
        // taiga biome and grove (the only places these tree types generate).

        registerTree(
            "bigspruce1",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.any(
                TreeMatchers.PINE,
                TreeMatchers.MEGA_PINE,
                TreeMatchers.MEGA_SPRUCE
            ),
            8,
            BiomeMatchers.PINE_BIOMES
        );
        registerTree(
            "bigspruce2",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.any(
                TreeMatchers.PINE,
                TreeMatchers.MEGA_PINE,
                TreeMatchers.MEGA_SPRUCE
            ),
            8,
            BiomeMatchers.PINE_BIOMES
        );
        registerTree(
            "bigspruce3",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.any(
                TreeMatchers.PINE,
                TreeMatchers.MEGA_PINE,
                TreeMatchers.MEGA_SPRUCE
            ),
            8,
            BiomeMatchers.PINE_BIOMES
        );
        registerTree(
            "bigspruce4",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.any(
                TreeMatchers.PINE,
                TreeMatchers.MEGA_PINE,
                TreeMatchers.MEGA_SPRUCE
            ),
            8,
            BiomeMatchers.PINE_BIOMES
        );
        registerTree(
            "bigspruce5",
            Blocks.SPRUCE_SAPLING,
            TreeMatchers.any(
                TreeMatchers.PINE,
                TreeMatchers.MEGA_PINE,
                TreeMatchers.MEGA_SPRUCE
            ),
            8,
            BiomeMatchers.PINE_BIOMES
        );

        // Jungle Big

        registerTree(
            "bigjungletree1",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "bigjungletree2",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "bigjungletree3",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "bigjungletree4",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "bigjungletree5",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "bigjungletree6",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "bigjungletree7",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_MEGA,
            8,
            BiomeMatchers.IS_JUNGLE
        );

        // Jungle Small

        registerTree(
            "jungletree1",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree2",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree3",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree4",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree5",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree6",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree7",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree8",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );
        registerTree(
            "jungletree9",
            Blocks.JUNGLE_SAPLING,
            TreeMatchers.JUNGLE_SMALL,
            8,
            BiomeMatchers.IS_JUNGLE
        );

        // Bone-meal on a red or brown mushroom AND world-gen in Mushroom Fields
        // and Dark Forest are both intercepted by HugeMushroomFeatureMixin.
        // No sapling block is needed – bone-meal growth is detected
        // automatically when a small RED_MUSHROOM / BROWN_MUSHROOM is at origin.
        //
        // Drop your NBT files in:
        //   src/main/resources/data/custom-tree/structures/redmushroom1.nbt
        //   src/main/resources/data/custom-tree/structures/brownmushroom1.nbt
        // etc. then uncomment (or add more) registrations below.
        //
        // NBT authoring tips for mushrooms:
        //   • Place the mushroom STEM base at Y = 0, horizontally centred.
        //   • The mixin removes the small mushroom block before placing the NBT,
        //     so you do NOT need to leave an air block at Y = 0 in the NBT.
        //   • TerrainPreservingProcessor will not overwrite liquid or bedrock.
        //
        // ── Red Mushroom ─────────────────────────────────────────────────────
        // registerMushroom("redmushroom1", TreeMatchers.RED_MUSHROOM,
        //     BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST));
        // registerMushroom("redmushroom2", TreeMatchers.RED_MUSHROOM,
        //     BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST));
        //
        // ── Brown Mushroom ────────────────────────────────────────────────────
        // registerMushroom("brownmushroom1", TreeMatchers.BROWN_MUSHROOM,
        //     BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST));
        // registerMushroom("brownmushroom2", TreeMatchers.BROWN_MUSHROOM,
        //     BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST));
        //
        // ── Any mushroom (one NBT for both colours) ───────────────────────────
        // registerMushroom("mushroom1", TreeMatchers.ANY_MUSHROOM,
        //     BiomeMatchers.any(BiomeMatchers.MUSHROOM_FIELDS, BiomeMatchers.DARK_FOREST));
        //
        // ── Mushroom Fields only ──────────────────────────────────────────────
        // registerMushroom("mycelium_red1",   TreeMatchers.RED_MUSHROOM,
        //     BiomeMatchers.MUSHROOM_FIELDS);
        // registerMushroom("mycelium_brown1", TreeMatchers.BROWN_MUSHROOM,
        //     BiomeMatchers.MUSHROOM_FIELDS);
        //
        // ── Dark Forest only ──────────────────────────────────────────────────
        // registerMushroom("darkforest_red1",   TreeMatchers.RED_MUSHROOM,
        //     BiomeMatchers.DARK_FOREST);
        // registerMushroom("darkforest_brown1", TreeMatchers.BROWN_MUSHROOM,
        //     BiomeMatchers.DARK_FOREST);

        // =====================================================================
        // Mangrove Swamp trees
        // =====================================================================
        // Mangrove trees grow from MANGROVE_PROPAGULE (bone-meal + random tick)
        // and appear naturally in the Mangrove Swamp biome.
        // Both sapling growth AND world-gen are intercepted:
        //   • SaplingGrowthMixin  catches propagule growth (sapling = MANGROVE_PROPAGULE)
        //   • TreeFeatureMixin    catches world-gen (worldGen  = TreeMatchers.MANGROVE)
        //
        // Drop your NBT files in:
        //   src/main/resources/data/custom-tree/structures/mangrove1.nbt
        // etc. then uncomment (or add more) registrations below.
        //
        // NBT authoring tips for mangroves:
        //   • The propagule is removed before the NBT is placed, so do NOT
        //     leave a hanging propagule at Y = 0 in the NBT.
        //   • Mangroves generate OVER water; TerrainPreservingProcessor will
        //     NOT overwrite water blocks – the roots will "float" above the
        //     surface, which matches the vanilla look.
        //   • Mangrove roots (the knobbly lower trunk) are part of the NBT.
        //   • Horizontally centre the trunk; place the root-base at Y = 0.
        //
        // registerTree("mangrove1", Blocks.MANGROVE_PROPAGULE,
        //     TreeMatchers.MANGROVE, 10, BiomeMatchers.MANGROVE_SWAMP);
        // registerTree("mangrove2", Blocks.MANGROVE_PROPAGULE,
        //     TreeMatchers.MANGROVE, 10, BiomeMatchers.MANGROVE_SWAMP);
        // registerTree("mangrove3", Blocks.MANGROVE_PROPAGULE,
        //     TreeMatchers.MANGROVE, 10, BiomeMatchers.MANGROVE_SWAMP);

        // =====================================================================
        // Spruce  (taiga, old-growth taiga, grove, windswept hills, …)
        // =====================================================================
        // TreeMatchers.SPRUCE  – standard spruce (SpruceFoliagePlacer)
        // TreeMatchers.PINE    – pine variant   (PineFoliagePlacer) – sparser crown
        // Both use SPRUCE_SAPLING for sapling growth.
        //
        // Biomes that naturally generate spruce (add a biome filter if you want
        // different trees in different biomes):
        //   BiomeMatchers.TAIGA                 – classic taiga
        //   BiomeMatchers.SNOWY_TAIGA           – snow-covered taiga (see section below)
        //   BiomeMatchers.OLD_GROWTH_PINE_TAIGA – old-growth pine taiga
        //   BiomeMatchers.OLD_GROWTH_SPRUCE_TAIGA
        //   BiomeMatchers.GROVE                 – snowy mountain grove
        //   BiomeMatchers.IS_TAIGA              – all taiga biomes at once (tag)
        //
        // ── Regular taiga (no snow cover) ────────────────────────────────────
        // registerTree("spruce1", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.SPRUCE, 8,
        //     BiomeMatchers.all(BiomeMatchers.IS_TAIGA,
        //                       BiomeMatchers.SNOWY_TAIGA.negate()));
        // registerTree("spruce2", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.SPRUCE, 8,
        //     BiomeMatchers.all(BiomeMatchers.IS_TAIGA,
        //                       BiomeMatchers.SNOWY_TAIGA.negate()));
        //
        // ── Pine variant (e.g. old-growth pine taiga) ─────────────────────────
        // registerTree("pine1", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.PINE, 10, BiomeMatchers.OLD_GROWTH_PINE_TAIGA);
        // registerTree("pine2", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.PINE, 10, BiomeMatchers.OLD_GROWTH_PINE_TAIGA);

        // =====================================================================
        // Snowy Spruce  (snowy_taiga · snowy_plains · windswept_hills ·
        //                windswept_forest · windswept_gravelly_hills)
        // =====================================================================
        // All five biomes share the same SpruceFoliagePlacer + spruce_log
        // TreeConfiguration.  BiomeMatchers.SNOWY_SPRUCE_BIOMES covers all of
        // them with a single predicate, so one line per NBT file is enough.
        //
        // If you also register regular taiga spruce (sprucetree* section above),
        // add BiomeMatchers.SNOWY_SPRUCE_BIOMES.negate() to those registrations
        // so snowy and non-snowy variants don’t mix.
        //
        // ── Snowy spruce ─────────────────────────────────────────────────────
        // registerTree("snowyspruce1", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.SPRUCE, 8, BiomeMatchers.SNOWY_SPRUCE_BIOMES);
        // registerTree("snowyspruce2", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.SPRUCE, 8, BiomeMatchers.SNOWY_SPRUCE_BIOMES);
        //
        // ── Snowy pine variant ──────────────────────────────────────────────
        // registerTree("snowypine1", Blocks.SPRUCE_SAPLING,
        //     TreeMatchers.PINE, 10, BiomeMatchers.SNOWY_SPRUCE_BIOMES);

        // =====================================================================
        // Acacia  (savanna biomes)
        // =====================================================================
        // Acacia is FULLY SUPPORTED – TreeMatchers.ACACIA uses AcaciaFoliagePlacer
        // which is unique to acacia trees.  BiomeMatchers has three savanna
        // constants ready to use:
        //   BiomeMatchers.SAVANNA           – plains savanna
        //   BiomeMatchers.SAVANNA_PLATEAU   – elevated savanna
        //   BiomeMatchers.WINDSWEPT_SAVANNA – windswept variant
        //   BiomeMatchers.IS_SAVANNA        – all savanna biomes at once (tag)
        //
        // Drop your NBT files in:
        //   src/main/resources/data/custom-tree/structures/acacia1.nbt
        // etc., then uncomment below.
        //
        // NBT authoring tips:
        //   • Use acacia_log as the trunk block.
        //   • Acacia trunks fork – centre the fork point, not the base.
        //   • Place the trunk base at Y = 0.
        //
        // registerTree("acacia1", Blocks.ACACIA_SAPLING,
        //     TreeMatchers.ACACIA, 14, BiomeMatchers.IS_SAVANNA);
        // registerTree("acacia2", Blocks.ACACIA_SAPLING,
        //     TreeMatchers.ACACIA, 14, BiomeMatchers.IS_SAVANNA);
        // registerTree("acacia3", Blocks.ACACIA_SAPLING,
        //     TreeMatchers.ACACIA, 14, BiomeMatchers.IS_SAVANNA);
        //
        // ── Per-biome acacia (different looks per biome) ──────────────────────
        // registerTree("acacia_plains1", Blocks.ACACIA_SAPLING,
        //     TreeMatchers.ACACIA, 14, BiomeMatchers.SAVANNA);
        // registerTree("acacia_plateau1", Blocks.ACACIA_SAPLING,
        //     TreeMatchers.ACACIA, 14, BiomeMatchers.SAVANNA_PLATEAU);

        // =====================================================================
        // Azalea  (any biome – surface above lush caves OR underground)
        // =====================================================================
        // Azalea trees grow from Blocks.AZALEA and Blocks.FLOWERING_AZALEA
        // (NOT a SaplingBlock), so SaplingGrowthMixin does NOT fire for them.
        // Instead, bone-mealing an azalea bush calls TreeFeature.place()
        // directly, which IS intercepted by TreeFeatureMixin.
        // → Use registerTreeWorldGenOnly (no sapling block) to cover both
        //   world-gen placement AND player bone-meal growth.
        //
        // ⚠️  DO NOT use a biome filter here.
        // Azalea trees spawn in TWO places:
        //   1. Underground inside minecraft:lush_caves.
        //   2. On the SURFACE above a lush cave, in whatever overworld biome
        //      sits on top (forest, plains, jungle, …).  The biome at that
        //      surface position is NOT lush_caves – it’s the surface biome.
        // Using BiomeMatchers.LUSH_CAVES would miss all surface-generated
        // azalea trees (which is the majority of visible ones).
        //
        // Identified by: RandomSpreadFoliagePlacer (unique to azalea in 1.21)
        //
        // Drop your NBT files in:
        //   src/main/resources/data/custom-tree/structures/azalea1.nbt
        // etc., then uncomment below.
        //
        // NBT authoring tips for azalea:
        //   • Use oak_log as the trunk block.
        //   • Use a mix of azalea_leaves and flowering_azalea_leaves in the crown.
        //   • Roots below the tree use rooted_dirt – bake them into the NBT.
        //   • Centre the trunk; place the trunk base at Y = 0.
        //
        // registerTreeWorldGenOnly("azalea1", TreeMatchers.AZALEA, 10);
        // registerTreeWorldGenOnly("azalea2", TreeMatchers.AZALEA, 10);
        // registerTreeWorldGenOnly("azalea3", TreeMatchers.AZALEA, 10);

        // ── Startup diagnostics (visible at WARN level in all launchers) ─────
        int count = CustomTreeRegistry.getAll().size();
        LOGGER.info("[CustomTree] ========================================");
        LOGGER.info(
            "[CustomTree] Mod initialised – {} definition(s) registered.",
            count
        );

        // Check every registered NBT file and report missing ones immediately.
        boolean anyMissing = false;
        for (CustomTreeDefinition def : CustomTreeRegistry.getAll()) {
            String path = def.getNbtResourcePath();
            try (
                java.io.InputStream in = CustomTree.class.getResourceAsStream(
                    path
                )
            ) {
                if (in == null) {
                    LOGGER.warn("[CustomTree]   MISSING  {}", path);
                    LOGGER.warn(
                        "[CustomTree]            → put the file at: src/main/resources{}",
                        path
                    );
                    anyMissing = true;
                } else {
                    LOGGER.info("[CustomTree]   found    {}", path);
                }
            } catch (Exception e) {
                LOGGER.warn(
                    "[CustomTree]   ERROR checking {}: {}",
                    path,
                    e.getMessage()
                );
                anyMissing = true;
            }
        }

        if (anyMissing) {
            LOGGER.warn("[CustomTree] One or more NBT files are missing.");
            LOGGER.warn(
                "[CustomTree] Missing trees fall back to vanilla silently."
            );
            LOGGER.warn(
                "[CustomTree] Rebuild with ./gradlew build after adding files."
            );
        }

        LOGGER.info("[CustomTree] In-game command: /customtrees status");
        LOGGER.info("[CustomTree] ========================================");

        PayloadTypeRegistry.serverboundPlay().register(
            GiveItemPayload.TYPE,
            GiveItemPayload.CODEC
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Server fully started!");

            // Pre-warm the NBT template cache so all structure files are loaded
            // before world generation begins, avoiding I/O stutter mid-chunk-gen.
            NbtTreePlacer.preWarmAll(server.overworld());

            // safe to access worlds, players, etc.

            ServerPlayNetworking.registerGlobalReceiver(
                GiveItemPayload.TYPE,
                (payload, context) -> {
                    context
                        .server()
                        .execute(() -> {
                            // BUG-02 fix: require operator level 2 so any non-op client
                            // cannot send this packet to give themselves arbitrary items.
                            if (
                                !context
                                    .player()
                                    .createCommandSourceStack()
                                    .permissions()
                                    .hasPermission(
                                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER
                                    )
                            ) {
                                return;
                            }

                            var optional = BuiltInRegistries.ITEM.get(
                                payload.itemId
                            );

                            if (optional.isEmpty()) {
                                return;
                            }

                            var item = optional.get().value();

                            ItemStack stack = new ItemStack(item, 1);

                            var player = context.player();

                            boolean inserted = player.getInventory().add(stack);

                            if (!inserted) {
                                player.drop(stack, false); // fallback if inventory is full
                            }
                        });
                }
            );

            // BUG-06 fix: removed forced "logAdminCommands = false" that was here.
            // Silently disabling admin-command logging on every server start overrides
            // server-operator configuration and removes the audit trail for admin commands.
            // Server operators are free to manage this gamerule through normal means.
        });
    }

    // -------------------------------------------------------------------------
    // Helpers – register a tree that hooks both sapling growth AND world-gen
    // -------------------------------------------------------------------------

    /**
     * World-gen-only registration: no sapling block.
     *
     * <p>Use this for trees that do <em>not</em> use a
     * {@link net.minecraft.world.level.block.SaplingBlock SaplingBlock} (e.g.
     * azalea, which grows from {@link Blocks#AZALEA} / {@link Blocks#FLOWERING_AZALEA}
     * via bone-meal on a bush block).  Bone-mealing those blocks calls
     * {@code TreeFeature.place()} directly, which is already intercepted by
     * {@code TreeFeatureMixin} through the {@code worldGenMatcher}, so no
     * explicit sapling registration is needed.
     *
     * @param nbtName         Filename without extension.
     * @param worldGenMatcher Predicate from {@link TreeMatchers}.
     * @param minSpacing      Minimum block radius between two world-gen trees.
     * @param biomes          Biome filter ({@code null} = all biomes).
     */
    private static void registerTreeWorldGenOnly(
        String nbtName,
        Predicate<TreeConfiguration> worldGenMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .worldGen(worldGenMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .build()
        );
    }

    /** World-gen-only, custom spacing, all biomes. */
    private static void registerTreeWorldGenOnly(
        String nbtName,
        Predicate<TreeConfiguration> worldGenMatcher,
        int minSpacing
    ) {
        registerTreeWorldGenOnly(nbtName, worldGenMatcher, minSpacing, null);
    }

    /** World-gen-only, no spacing, all biomes. */
    private static void registerTreeWorldGenOnly(
        String nbtName,
        Predicate<TreeConfiguration> worldGenMatcher
    ) {
        registerTreeWorldGenOnly(nbtName, worldGenMatcher, 0, null);
    }

    /** No spacing, all biomes. */
    private static void registerTree(
        String nbtName,
        Block sapling,
        Predicate<TreeConfiguration> worldGenMatcher
    ) {
        registerTree(nbtName, sapling, worldGenMatcher, 0, null);
    }

    /** Custom spacing, all biomes. */
    private static void registerTree(
        String nbtName,
        Block sapling,
        Predicate<TreeConfiguration> worldGenMatcher,
        int minSpacing
    ) {
        registerTree(nbtName, sapling, worldGenMatcher, minSpacing, null);
    }

    /**
     * Full registration: custom spacing + biome filter.
     *
     * @param nbtName         Filename without extension – must exist as
     *                        {@code data/custom-tree/structures/<nbtName>.nbt}.
     * @param sapling         Sapling block that grows this tree.
     * @param worldGenMatcher Predicate from {@link TreeMatchers} identifying
     *                        which vanilla tree configs to replace.
     * @param minSpacing      Minimum block radius between two trees of this type
     *                        during world generation.  {@code 0} = vanilla density.
     * @param biomes          Biome filter – only this biome (or group of biomes)
     *                        will have this tree.  {@code null} = all biomes.
     *                        Use {@link BiomeMatchers} for pre-built predicates.
     */
    private static void registerTree(
        String nbtName,
        Block sapling,
        Predicate<TreeConfiguration> worldGenMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .sapling(sapling)
                .worldGen(worldGenMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .build()
        );
    }

    /** Full registration: custom spacing + biome filter + weight. */
    private static void registerTree(
        String nbtName,
        Block sapling,
        Predicate<TreeConfiguration> worldGenMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes,
        int weight
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .sapling(sapling)
                .worldGen(worldGenMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .weight(weight)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Helpers – register a nether tree (HugeFungusFeature – no sapling block)
    // -------------------------------------------------------------------------

    /**
     * Register a nether tree with no spacing check, restricted to a biome.
     *
     * <p>Both world-gen placement and bone-meal growth are caught
     * automatically by {@code HugeFungusFeatureMixin}.
     */
    private static void registerFungus(
        String nbtName,
        Predicate<HugeFungusConfiguration> fungusMatcher,
        Predicate<Holder<Biome>> biomes
    ) {
        registerFungus(nbtName, fungusMatcher, 0, biomes);
    }

    /**
     * Register a nether tree with a minimum spacing between placements,
     * restricted to a biome.
     *
     * @param nbtName       Filename without extension.
     * @param fungusMatcher Use {@link TreeMatchers#WARPED_FUNGUS} or
     *                      {@link TreeMatchers#CRIMSON_FUNGUS}.
     * @param minSpacing    Minimum block radius between two fungi of this type.
     *                      {@code 0} = vanilla density.
     * @param biomes        Biome filter.  Use {@link BiomeMatchers} constants.
     */
    private static void registerFungus(
        String nbtName,
        Predicate<HugeFungusConfiguration> fungusMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .fungusWorldGen(fungusMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .build()
        );
    }

    /** Register a nether tree with spacing, biome filter, and weight. */
    private static void registerFungus(
        String nbtName,
        Predicate<HugeFungusConfiguration> fungusMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes,
        int weight
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .fungusWorldGen(fungusMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .weight(weight)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Helpers – register a large mushroom (HugeMushroomFeature)
    // Covers both world-gen and bone-meal on a planted red/brown mushroom.
    // -------------------------------------------------------------------------

    /** No spacing, restricted to a biome. */
    private static void registerMushroom(
        String nbtName,
        Predicate<HugeMushroomFeatureConfiguration> mushroomMatcher,
        Predicate<Holder<Biome>> biomes
    ) {
        registerMushroom(nbtName, mushroomMatcher, 0, biomes);
    }

    /**
     * Register a large mushroom with a minimum spacing between placements,
     * restricted to a biome.
     *
     * @param nbtName         Filename without extension – must exist as
     *                        {@code data/custom-tree/structures/<nbtName>.nbt}.
     * @param mushroomMatcher Use {@link TreeMatchers#RED_MUSHROOM},
     *                        {@link TreeMatchers#BROWN_MUSHROOM}, or
     *                        {@link TreeMatchers#ANY_MUSHROOM}.
     * @param minSpacing      Minimum block radius between two mushrooms of this
     *                        type during world generation.  {@code 0} = vanilla
     *                        density.  Bone-meal growth is never blocked.
     * @param biomes          Biome filter.  Use {@link BiomeMatchers} constants.
     */
    private static void registerMushroom(
        String nbtName,
        Predicate<HugeMushroomFeatureConfiguration> mushroomMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .mushroomWorldGen(mushroomMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .build()
        );
    }

    /** Register a large mushroom with spacing, biome filter, and weight. */
    private static void registerMushroom(
        String nbtName,
        Predicate<HugeMushroomFeatureConfiguration> mushroomMatcher,
        int minSpacing,
        Predicate<Holder<Biome>> biomes,
        int weight
    ) {
        CustomTreeRegistry.register(
            CustomTreeDefinition.forTree(nbtName)
                .mushroomWorldGen(mushroomMatcher)
                .minSpacing(minSpacing)
                .biomes(biomes)
                .weight(weight)
                .build()
        );
    }
}
