package tree.modid.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tree.modid.CustomTreeDefinition;
import tree.modid.CustomTreeRegistry;
import tree.modid.NbtTreePlacer;

/**
 * Intercepts {@link AbstractHugeMushroomFeature#place} calls during
 * <b>world generation only</b>.
 *
 * <p>Bone-meal growth (a small red/brown mushroom block at the feature origin)
 * is detected and passed through to vanilla immediately so players always get
 * the standard vanilla huge-mushroom when they bone-meal a planted mushroom.
 *
 * <p>World-gen placements that match a registered
 * {@link tree.modid.CustomTreeDefinition} will have a custom NBT structure
 * placed instead of the vanilla huge mushroom.
 *
 * <h3>Adding a new mushroom variant</h3>
 * Register a {@link tree.modid.CustomTreeDefinition} in
 * {@code CustomTree.onInitialize()} using the {@code mushroomWorldGen()}
 * builder method with {@link tree.modid.TreeMatchers#RED_MUSHROOM} or
 * {@link tree.modid.TreeMatchers#BROWN_MUSHROOM}. No changes to this mixin
 * are ever needed for new variants.
 */
@Mixin(AbstractHugeMushroomFeature.class)
public class HugeMushroomFeatureMixin {

    /** Fires a one-time WARN on the very first interception so the log confirms
     *  the mixin is loaded and active. */
    private static volatile boolean firedOnce = false;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(
        FeaturePlaceContext<HugeMushroomFeatureConfiguration> context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        HugeMushroomFeatureConfiguration config = context.config();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        // One-time confirmation that the mixin is alive.
        if (!firedOnce) {
            firedOnce = true;
            tree.modid.CustomTree.LOGGER.warn(
                "[CustomTree] HugeMushroomFeatureMixin active – capProvider={} origin={}",
                config.capProvider(),
                origin
            );
        }

        // Bone-meal vs world-gen detection: a small mushroom at origin means bone-meal.
        // Bone-meal on a planted mushroom → always use vanilla growth.
        // Only world-gen placements get custom NBTs.
        BlockState originState = level.getBlockState(origin);
        boolean isBoneMeal =
            originState.is(Blocks.RED_MUSHROOM) ||
            originState.is(Blocks.BROWN_MUSHROOM);
        if (isBoneMeal) return; // let vanilla grow the mushroom

        // Resolve biome so per-biome definitions are respected.
        Holder<Biome> biome = level.getBiome(origin);

        // Weighted random pick from all definitions that match this mushroom
        // configuration AND this biome.
        Optional<CustomTreeDefinition> defOpt =
            CustomTreeRegistry.pickByMushroomWorldGen(
                config,
                biome,
                context.random()
            );
        if (defOpt.isEmpty()) return; // no match → vanilla runs

        CustomTreeDefinition def = defOpt.get();

        // The ground block is at origin.below(): HeightmapPlacement puts the
        // feature origin in the first AIR cell above the topmost surface block.
        BlockState groundState = level.getBlockState(origin.below());

        // --- World-gen ground validation -------------------------------------
        // dark_forest_vegetation uses the OCEAN_FLOOR heightmap which can land
        // on tree canopy tops or structure roofs.  Only accept #dirt surfaces
        // (grass_block, dirt, podzol, mycelium, etc.) so mushrooms never float
        // on top of oak canopies or woodland mansion roofs.
        // (1) Reject liquid surfaces.
        if (groundState.liquid()) {
            cir.setReturnValue(false);
            return;
        }
        // (2) Natural ground only: dirt-family blocks OR mycelium.
        // NOTE: mycelium is NOT in #minecraft:dirt, but it IS the primary
        // surface of mushroom_fields and must be accepted here.
        if (
            !groundState.is(BlockTags.DIRT) && !groundState.is(Blocks.MYCELIUM)
        ) {
            cir.setReturnValue(false);
            return;
        }

        // --- Floor check (optional per-definition) ---------------------------
        if (
            def.getValidFloor() != null &&
            !def.getValidFloor().test(groundState)
        ) {
            // Custom floor predicate rejected this position – let vanilla try.
            return;
        }

        // --- Spacing check ---------------------------------------------------
        int spacing = def.getMinSpacing();
        if (spacing > 0) {
            if (
                NbtTreePlacer.hasNearbyPlacement(
                    def.getNbtResourcePath(),
                    origin,
                    spacing
                )
            ) {
                cir.setReturnValue(false);
                return;
            }
        }

        // --- Load NBT --------------------------------------------------------
        StructureTemplate template = NbtTreePlacer.getOrLoad(def, level);
        if (template == null) {
            return; // file missing – fall back to vanilla silently
        }

        // Wall-overlap clearance: skip if footprint overlaps significantly
        // with solid non-natural blocks (village buildings, etc.).
        if (
            !NbtTreePlacer.isPlacementClear(level, origin, template.getSize())
        ) {
            return; // let vanilla try instead
        }

        // --- Place structure -------------------------------------------------
        NbtTreePlacer.place(template, level, origin, context.random());

        // Track this placement so future spacing checks can find it.
        if (def.getMinSpacing() > 0) {
            NbtTreePlacer.markPlaced(def.getNbtResourcePath(), origin);
        }

        cir.setReturnValue(true);
    }
}
