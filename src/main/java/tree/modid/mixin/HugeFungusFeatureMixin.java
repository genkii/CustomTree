package tree.modid.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.HugeFungusConfiguration;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tree.modid.CustomTreeDefinition;
import tree.modid.CustomTreeRegistry;
import tree.modid.NbtTreePlacer;

/**
 * Intercepts every {@link HugeFungusFeature#place} call, which covers both:
 * <ul>
 *   <li><b>World generation</b> – when the nether biome feature list plants a
 *       huge warped or crimson fungus during chunk population.</li>
 *   <li><b>Bone-meal growth</b> – when a player uses bone meal on a planted
 *       warped or crimson fungus; the game internally calls the
 *       {@code warped_fungus_planted} / {@code crimson_fungus_planted}
 *       configured feature, which also goes through
 *       {@link HugeFungusFeature#place}.</li>
 * </ul>
 *
 * <p>Because a single mixin handles both cases, no separate bone-meal mixin
 * for {@code FungusBlock} or {@code NetherFungusBlock} is required.
 */
@Mixin(HugeFungusFeature.class)
public class HugeFungusFeatureMixin {

    /** Fires a one-time WARN on the very first interception. */
    private static volatile boolean firedOnce = false;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(
        FeaturePlaceContext<HugeFungusConfiguration> context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        HugeFungusConfiguration config = context.config();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        // One-time confirmation that the mixin is alive.
        if (!firedOnce) {
            firedOnce = true;
            tree.modid.CustomTree.LOGGER.warn(
                "[CustomTree] HugeFungusFeatureMixin active – stem={} validBase={} origin={}",
                config.stemState.getBlock().getDescriptionId(),
                config.validBaseState.getBlock().getDescriptionId(),
                origin
            );
        }

        // Bone-meal on a planted fungus → always use vanilla growth.
        // Only world-gen placements (config.planted == false) get custom NBTs.
        if (config.planted) return;

        // Resolve biome so per-biome definitions are respected.
        Holder<Biome> biome = level.getBiome(origin);

        // Weighted random pick from all definitions that match this fungus
        // configuration AND this biome.
        Optional<CustomTreeDefinition> defOpt =
            CustomTreeRegistry.pickByFungusWorldGen(
                config,
                biome,
                context.random()
            );
        if (defOpt.isEmpty()) return; // no match → vanilla runs

        CustomTreeDefinition def = defOpt.get();

        // --- Ground-block validation ----------------------------------------
        // The valid base (warped/crimson nylium) can be at two different
        // positions depending on the call site:
        //
        //   • World-gen  – origin is the stem-base position (the block the
        //     stem will occupy).  Vanilla may set this to the nylium block
        //     itself OR to one block above it depending on the heightmap
        //     decorator used by the placed feature.
        //
        //   • Bone-meal  – origin is the planted fungus block (e.g.
        //     warped_fungus sitting on nylium).  The nylium is always one
        //     block BELOW origin.
        //
        // → Accept placement if EITHER origin OR origin.below() is the
        //   valid base block.  This handles every combination without
        //   cancelling vanilla for legitimate lava/air positions.
        Block validBase = config.validBaseState.getBlock();
        BlockState originState = level.getBlockState(origin);
        BlockState belowState = level.getBlockState(origin.below());

        if (!originState.is(validBase) && !belowState.is(validBase)) {
            cir.setReturnValue(false);
            return;
        }

        // Resolve the actual nylium state for the optional floor predicate.
        // Prefer origin itself when it is already the nylium (world-gen),
        // otherwise fall back to the block below (bone-meal).
        BlockState baseState = originState.is(validBase)
            ? originState
            : belowState;

        // Optional per-definition floor check.
        // Use a bare return (not setReturnValue) so the vanilla fungus feature
        // can still run when the custom floor predicate rejects this spot.
        if (
            def.getValidFloor() != null && !def.getValidFloor().test(baseState)
        ) {
            return;
        }

        // --- Spacing check --------------------------------------------------
        // All variants of the same fungus type share one spacing key (derived
        // from the stem block) so that after ANY variant is placed, ALL
        // variants within the required radius are blocked.
        //
        // Spacing is a world-gen concern only – bone-meal growth (config.planted
        // == true) must never be blocked by nearby world-gen trees, otherwise
        // the player cannot grow a manually planted fungus in their own forest.
        //
        // The cross-session world-block scan is intentionally omitted: warped/
        // crimson stems appear everywhere in the biome and would block almost
        // every placement. World-gen runs once per chunk, so the in-session
        // set is sufficient.
        String spacingKey = config.stemState.getBlock().getDescriptionId();
        if (!config.planted) {
            int spacing = def.getMinSpacing();
            if (spacing > 0) {
                if (
                    NbtTreePlacer.hasNearbyPlacement(
                        spacingKey,
                        origin,
                        spacing
                    )
                ) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        // --- Load and place NBT structure -----------------------------------
        StructureTemplate template = NbtTreePlacer.getOrLoad(def, level);
        if (template == null) {
            return; // file missing – fall back to vanilla
        }

        // Wall-overlap clearance check: skip if footprint overlaps significantly
        // with solid non-natural blocks (e.g. Nether fortress walls).
        if (
            !NbtTreePlacer.isPlacementClear(level, origin, template.getSize())
        ) {
            return; // let vanilla try instead
        }

        NbtTreePlacer.place(template, level, origin, context.random());
        // Only record world-gen placements in the spacing tracker.
        // Bone-meal placements should not count against the radius so the
        // player can always grow a fungus wherever they plant one.
        if (!config.planted) {
            NbtTreePlacer.markPlaced(spacingKey, origin);
        }
        cir.setReturnValue(true);
    }
}
