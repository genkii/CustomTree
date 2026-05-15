package tree.modid.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tree.modid.CustomTree;
import tree.modid.CustomTreeDefinition;
import tree.modid.CustomTreeRegistry;
import tree.modid.NbtTreePlacer;

/**
 * Intercepts every {@link TreeFeature#place} call during world generation.
 *
 * <p>For each tree the game tries to generate, we ask {@link CustomTreeRegistry}
 * whether a custom definition matches the {@link TreeConfiguration}.  If one
 * does, we load (or return the cached) NBT structure and place it instead of
 * the vanilla tree, then cancel the original placement.
 *
 * <p>If no definition matches, or the NBT file is missing/broken, the mixin
 * does nothing and vanilla generation proceeds normally.
 */
@Mixin(TreeFeature.class)
public class TreeFeatureMixin {

    /** Fires a one-time WARN on the very first interception so the user can
     *  confirm in any log viewer that the mixin is active. */
    private static volatile boolean firedOnce = false;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(
        FeaturePlaceContext<TreeConfiguration> context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        TreeConfiguration config = context.config();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        // One-time confirmation that the mixin is alive.
        if (!firedOnce) {
            firedOnce = true;
            CustomTree.LOGGER.warn(
                "[CustomTree] TreeFeatureMixin is active – first TreeFeature.place() intercepted."
            );
        }

        // Ask the registry for all definitions matching this tree config AND
        // the current biome, then do a weighted random pick so multiple variants
        // get a fair chance.  Biome-filtered definitions are excluded here.
        Holder<Biome> biome = level.getBiome(origin);
        Optional<CustomTreeDefinition> defOpt =
            CustomTreeRegistry.pickByWorldGen(config, biome, context.random());
        if (defOpt.isEmpty()) {
            // Uncomment the line below to log every unmatched tree (very verbose):
            // CustomTree.LOGGER.debug("[CustomTree] No match for tree at {}", origin);
            return; // no match → let vanilla handle it
        }

        CustomTreeDefinition def = defOpt.get();

        // --- Origin fluid check ---------------------------------------------
        // Two cases handled separately:
        //
        // 1. LAVA – hard block regardless of biome.  Placing wood (logs, leaves,
        //    roots) into or next to lava causes fire; always fall through to
        //    vanilla which will also reject the placement cleanly.
        //
        // 2. WATER – biome-conditional.
        //    • mangrove_swamp / swamp: water at origin is expected and must be
        //      allowed.  HeightmapPlacement adds +1 to the OCEAN_FLOOR_WG value,
        //      so the feature origin sits in the water cell above the mud/grass
        //      surface.  Blocking water here would silently fall back to vanilla
        //      for every in-water tree in these biomes.
        //    • All other biomes: a watery origin means the heightmap landed on a
        //      pond, river, or ocean fringe.  We do NOT want a custom oak/birch/
        //      jungle tree spawning inside a lake, so fall through to vanilla
        //      (which rejects the placement for the same reason).
        BlockState originState = level.getBlockState(origin);
        if (originState.getFluidState().is(FluidTags.LAVA)) {
            return; // never place wood into lava
        }
        if (originState.getFluidState().is(FluidTags.WATER)) {
            if (!biome.is(Biomes.MANGROVE_SWAMP) && !biome.is(Biomes.SWAMP)) {
                return; // non-aquatic biome + watery origin → let vanilla decide
            }
        }

        // Optional per-definition floor check.
        if (
            def.getValidFloor() != null &&
            !def.getValidFloor().test(originState)
        ) {
            return;
        }

        // --- Ground alignment -----------------------------------------------
        // Some heightmap types (MOTION_BLOCKING_NO_LEAVES, WORLD_SURFACE_WG)
        // include thin replaceable surface blocks – snow layers, flowers, ferns,
        // tall grass – in their scan, placing the feature origin one block
        // above the solid ground.  TerrainPreservingProcessor never removes
        // those thin blocks, so the trunk would appear to float.
        // Shift origin down through any such blocks before placement.
        origin = NbtTreePlacer.groundAdjust(level, origin, 2);

        // --- Spacing check --------------------------------------------------
        // Hybrid: fast in-session position set first, then a world-block scan
        // as a cross-session fallback so restarts don't reset spacing.
        //
        // The world scan uses the #minecraft:logs block tag rather than an
        // exact block match.  Most custom tree NBTs use the all-sides "wood"
        // variant (birch_wood, oak_wood, …) rather than the directional
        // "log" variant (birch_log, oak_log, …) that the vanilla
        // TreeConfiguration trunk provider exposes.  A tag-based scan catches
        // both variants – as well as stripped variants – so previously placed
        // custom trees of a different NBT variant are reliably detected.
        // Without this fix all birch variants except birchtree1 used
        // birch_wood, which the old exact-match scan for birch_log missed,
        // allowing trees from different variants to spawn only 2 blocks apart.
        int spacing = def.getMinSpacing();
        if (spacing > 0) {
            boolean tooClose = NbtTreePlacer.hasNearbyPlacement(
                def.getNbtResourcePath(),
                origin,
                spacing
            );
            if (!tooClose) {
                // Cross-session / cross-variant fallback: scan world for any
                // log or wood block within the spacing radius.
                tooClose = NbtTreePlacer.hasNearbyLog(level, origin, spacing);
            }
            if (tooClose) {
                cir.setReturnValue(false);
                return;
            }
        }

        // Load (or retrieve cached) NBT structure template.
        StructureTemplate template = NbtTreePlacer.getOrLoad(def, level);
        if (template == null) {
            // NBT file is missing – warn once (NbtTreePlacer logs on first miss)
            // and fall back to vanilla so the game keeps running.
            return;
        }

        CustomTree.LOGGER.debug(
            "[CustomTree] Placing {} at {}",
            def.getNbtResourcePath(),
            origin
        );

        // Trunk-column scan: abort if any block in the vertical centre column
        // from origin to origin+height is a liquid or bedrock.  This prevents
        // a partial structure where TerrainPreservingProcessor silently skips
        // those blocks, leaving floating canopy pieces.
        if (
            !NbtTreePlacer.isTrunkClear(
                level,
                origin,
                template.getSize().getY()
            )
        ) {
            return;
        }

        // Wall-overlap check: skip NBT placement if the footprint overlaps
        // significantly with solid non-natural blocks (fortress walls, etc.).
        // Fall through to vanilla – it skips solid blocks natively, no holes.
        if (
            !NbtTreePlacer.isPlacementClear(level, origin, template.getSize())
        ) {
            return;
        }

        // Place the custom structure centred on the feature origin.
        NbtTreePlacer.place(template, level, origin, context.random());

        // Record the placement so future spacing checks can find this tree.
        NbtTreePlacer.markPlaced(def.getNbtResourcePath(), origin);

        // Signal success and cancel vanilla tree generation.
        cir.setReturnValue(true);
    }
}
