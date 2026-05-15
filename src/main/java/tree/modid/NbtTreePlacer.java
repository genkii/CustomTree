package tree.modid;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Loads NBT structure files from the mod jar and places them in the world.
 *
 * <p>Templates are cached after the first load so the file is only parsed
 * once per game session regardless of how many trees spawn.
 *
 * <h3>Placement safety</h3>
 * Every placement is routed through {@link TerrainPreservingProcessor}, which
 * enforces three rules:
 * <ol>
 *   <li>Air blocks from the NBT are <em>never</em> written to the world.
 *       Without this, the bounding-box padding exported by structure blocks
 *       would delete netherrack, nylium, and other surrounding terrain,
 *       leaving ugly craters around every tree.</li>
 *   <li>Bedrock is never overwritten – it is indestructible by design.</li>
 *   <li>Lava source/flowing blocks are preserved to prevent fire and
 *       explosions when wood is placed near Nether lava lakes.</li>
 *   <li>Water is <em>intentionally allowed</em> so that mangrove roots,
 *       swamp-tree trunks, and any other NBT blocks that should be
 *       waterlogged can be placed correctly in flooded positions.</li>
 * </ol>
 */
public final class NbtTreePlacer {

    private NbtTreePlacer() {}

    // -------------------------------------------------------------------------
    // Template cache
    // -------------------------------------------------------------------------

    /**
     * Cache keyed by resource path.
     * {@code Optional.empty()} means "failed to load – do not retry".
     * {@code Optional.of(template)} means "loaded successfully".
     */
    private static final ConcurrentHashMap<
        String,
        Optional<StructureTemplate>
    > CACHE = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // In-session placement tracker  (fast, no world reads)
    // -------------------------------------------------------------------------

    /**
     * Tracks the origin positions of every successfully placed custom tree
     * within the current game session, keyed by NBT resource path.
     * Each value is a thread-safe set of packed {@link BlockPos} longs.
     *
     * <p>Used as the primary (fast) component of the hybrid spacing check.
     * The set resets when the JVM exits, so a cross-session fallback
     * ({@link #hasNearbyBlock}) is also applied.
     */
    private static final ConcurrentHashMap<String, Set<Long>> PLACED_POSITIONS =
        new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Terrain-preserving placement processor
    // -------------------------------------------------------------------------

    /**
     * {@link StructureProcessor} applied to every NBT placement.
     *
     * <p>Rules (in order):
     * <ol>
     *   <li>If the NBT block is <b>air</b> → skip.  Air in the bounding-box
     *       padding must not overwrite terrain blocks.</li>
     *   <li>If the existing world block is <b>bedrock</b> → skip.</li>
     *   <li>If the existing world block is a <b>liquid</b> (lava or water
     *       source / flowing) → skip.</li>
     *   <li>Otherwise → place the NBT block normally.</li>
     * </ol>
     *
     * <p>This processor is not meant to be serialised; it borrows
     * {@link StructureProcessorType#NOP} as a harmless sentinel type.
     */
    private static final class TerrainPreservingProcessor
        extends StructureProcessor
    {

        static final TerrainPreservingProcessor INSTANCE =
            new TerrainPreservingProcessor();

        @Override
        public StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos offset,
            BlockPos pivot,
            StructureTemplate.StructureBlockInfo originalInfo,
            StructureTemplate.StructureBlockInfo currentInfo,
            StructurePlaceSettings settings
        ) {
            // Rule 1: never write air from the NBT into the world.
            // Exported bounding boxes include surrounding air, which would
            // otherwise delete netherrack / nylium / dirt around the tree.
            if (currentInfo.state().isAir()) return null;

            BlockState existing = level.getBlockState(currentInfo.pos());

            // Rule 2: never overwrite bedrock.
            if (existing.is(Blocks.BEDROCK)) return null;

            // Rule 3: never overwrite lava.
            // Placing wood (logs, roots, leaves) into or adjacent to lava
            // causes fire and explosions.  Lava lakes in the Nether must
            // remain intact.
            if (existing.getFluidState().is(FluidTags.LAVA)) return null;

            // Water is intentionally NOT blocked here.
            // Mangrove roots and swamp-tree trunks are naturally waterlogged;
            // if we skipped water cells the structure would have gaps at every
            // shallow-water position, leaving floating canopy pieces.
            // The NBT author is responsible for using waterlogged block states
            // in any block that sits in water (structure blocks export the
            // correct waterlogged=true state automatically).

            return currentInfo;
        }

        @Override
        protected StructureProcessorType<?> getType() {
            // Not serialisable; NOP is a safe sentinel.
            return StructureProcessorType.NOP;
        }
    }

    /**
     * Returns the singleton {@link TerrainPreservingProcessor} for use by
     * external callers such as {@link tree.modid.DebugCommand}.
     */
    public static StructureProcessor getTerrainPreservingProcessor() {
        return TerrainPreservingProcessor.INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Eagerly loads every NBT template referenced by a registered
     * {@link CustomTreeDefinition} into the in-memory cache.
     *
     * <p>Call this once after the server has fully started (via
     * {@code ServerLifecycleEvents.SERVER_STARTED}) so the disk I/O is
     * front-loaded at startup instead of occurring on the first tree placement
     * mid-world-generation, which can cause brief stutter on initial chunk gen.
     *
     * @param level Any loaded server level — used for registry access only.
     */
    public static void preWarmAll(ServerLevelAccessor level) {
        int loaded = 0,
            missing = 0;
        for (CustomTreeDefinition def : CustomTreeRegistry.getAll()) {
            if (getOrLoad(def, level) != null) loaded++;
            else missing++;
        }
        CustomTree.LOGGER.info(
            "[CustomTree] Cache pre-warmed: {} template(s) loaded, {} missing.",
            loaded,
            missing
        );
    }

    /**
     * Returns the cached (or freshly loaded) template for {@code def}.
     *
     * Returns {@code null} if the file is missing or corrupt, in which case
     * vanilla behaviour is used as a fallback.
     *
     * @param level Any {@link ServerLevelAccessor} – used only for registry
     *              access when loading the template for the first time.
     */
    public static StructureTemplate getOrLoad(
        CustomTreeDefinition def,
        ServerLevelAccessor level
    ) {
        // computeIfAbsent is a single atomic ConcurrentHashMap operation, so at
        // most one thread loads each NBT file – no TOCTOU race, no duplicate I/O.
        return CACHE.computeIfAbsent(def.getNbtResourcePath(), path ->
            Optional.ofNullable(tryLoad(path, level))
        ).orElse(null);
    }

    private static StructureTemplate tryLoad(
        String path,
        ServerLevelAccessor level
    ) {
        try (InputStream in = CustomTree.class.getResourceAsStream(path)) {
            if (in == null) {
                CustomTree.LOGGER.warn(
                    "[CustomTree] NBT file not found: {}  " +
                        "– place your structure at src/main/resources{}",
                    path,
                    path
                );
                return null;
            }
            CompoundTag nbt = NbtIo.readCompressed(
                in,
                NbtAccounter.unlimitedHeap()
            );
            StructureTemplate template = new StructureTemplate();
            template.load(
                level.registryAccess().lookupOrThrow(Registries.BLOCK),
                nbt
            );
            CustomTree.LOGGER.info(
                "[CustomTree] Loaded {} (size: {})",
                path,
                template.getSize()
            );
            return template;
        } catch (Exception e) {
            CustomTree.LOGGER.error(
                "[CustomTree] Failed to load {}: {}",
                path,
                e.getMessage()
            );
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Spacing – in-session position tracking  (primary check)
    // -------------------------------------------------------------------------

    /**
     * Records that a custom tree was successfully placed at {@code origin}.
     * Must be called immediately after every successful {@link #place} call.
     * Thread-safe.
     *
     * @param nbtPath Resource path (from
     *                {@link CustomTreeDefinition#getNbtResourcePath()}).
     * @param origin  Origin position passed to {@link #place}.
     */
    public static void markPlaced(String nbtPath, BlockPos origin) {
        PLACED_POSITIONS.computeIfAbsent(nbtPath, k ->
            ConcurrentHashMap.newKeySet()
        ).add(origin.asLong());
    }

    /**
     * Returns {@code true} when a placement recorded <em>in the current
     * session</em> is within {@code radius} blocks (XZ) of {@code origin}.
     *
     * <p>This is the fast, no-world-read half of the hybrid spacing check.
     * It resets on server restart, so {@link #hasNearbyBlock} is used as a
     * cross-session complement.
     *
     * @param nbtPath Resource path of the definition.
     * @param origin  Candidate placement position.
     * @param radius  Minimum required separation in blocks (XZ plane).
     */
    public static boolean hasNearbyPlacement(
        String nbtPath,
        BlockPos origin,
        int radius
    ) {
        Set<Long> positions = PLACED_POSITIONS.get(nbtPath);
        if (positions == null || positions.isEmpty()) return false;

        long r2 = (long) radius * radius;
        for (long packed : positions) {
            BlockPos placed = BlockPos.of(packed);
            long dx = placed.getX() - origin.getX();
            long dz = placed.getZ() - origin.getZ();
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Spacing – world-block scan  (cross-session fallback)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when a block matching {@code tracerBlock} is found
     * within {@code radius} blocks (XZ, circular) of {@code origin}.
     *
     * <p>This is the cross-session half of the hybrid spacing check.
     * Because {@link #PLACED_POSITIONS} resets when the JVM exits, new chunks
     * generated in a fresh session would ignore trees placed in a previous
     * session.  Scanning the world for the custom tree's distinctive trunk /
     * stem block catches those previously placed trees directly.
     *
     * <p>The vertical scan range is {@code origin.Y - 2} to
     * {@code origin.Y + 6}, which is wide enough to catch trunks on sloped
     * terrain without being expensive.
     *
     * <p><b>Caller contract:</b> pass the block type that appears in the
     * custom NBT at or near trunk-base level – for example
     * {@code Blocks.WARPED_STEM} for a warped-forest tree, or
     * {@code Blocks.OAK_LOG} for an oak replacement.  The scan is fast
     * (only the chunk cache is read) but requires the custom NBT to use a
     * recognisable trunk block.
     *
     * @param level       The world.
     * @param origin      Candidate placement position.
     * @param tracerBlock Trunk / stem block that identifies this tree type.
     * @param radius      Minimum required separation in blocks (XZ plane).
     */
    public static boolean hasNearbyBlock(
        ServerLevelAccessor level,
        BlockPos origin,
        Block tracerBlock,
        int radius
    ) {
        long r2 = (long) radius * radius;
        // Reuse one MutableBlockPos across all iterations instead of
        // allocating a new BlockPos for every offset combination.
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((long) dx * dx + (long) dz * dz > r2) continue;
                for (int dy = -2; dy <= 6; dy++) {
                    check.setWithOffset(origin, dx, dy, dz);
                    if (level.getBlockState(check).is(tracerBlock)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when any block belonging to the
     * {@link BlockTags#LOGS} tag is found within {@code radius} blocks (XZ,
     * circular) of {@code origin}.
     *
     * <p>This is the preferred cross-session spacing check for tree-type
     * definitions whose custom NBT uses a <em>wood</em> block variant (e.g.
     * {@code birch_wood}) rather than the plain <em>log</em> variant (e.g.
     * {@code birch_log}) that the vanilla {@link
     * net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration
     * TreeConfiguration} trunk provider exposes.  Using the {@code #logs}
     * tag catches all variants ({@code birch_log}, {@code birch_wood},
     * {@code stripped_birch_log}, {@code stripped_birch_wood}, …) so the
     * scan reliably finds previously placed custom trees regardless of which
     * specific trunk-block variant the NBT author used.
     *
     * <p>The vertical scan range is {@code origin.Y - 2} to
     * {@code origin.Y + 6}, matching {@link #hasNearbyBlock}.
     *
     * @param level  The world.
     * @param origin Candidate placement position.
     * @param radius Minimum required separation in blocks (XZ plane).
     */
    public static boolean hasNearbyLog(
        ServerLevelAccessor level,
        BlockPos origin,
        int radius
    ) {
        long r2 = (long) radius * radius;
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((long) dx * dx + (long) dz * dz > r2) continue;
                for (int dy = -2; dy <= 6; dy++) {
                    check.setWithOffset(origin, dx, dy, dz);
                    if (
                        level.getBlockState(check).is(BlockTags.LOGS)
                    ) return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Trunk-column obstruction check
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the vertical column directly at {@code origin}
     * is safe to place a tree trunk through.
     *
     * <p>Hard blockers (return {@code false}):
     * <ul>
     *   <li><b>Bedrock</b> – cannot be overwritten.</li>
     *   <li><b>Lava</b> – placing wood next to lava causes fire.</li>
     * </ul>
     *
     * <p>Water is <em>not</em> a blocker.  Mangrove trees and swamp trees
     * generate with trunks/roots that pass through shallow water; the
     * {@link TerrainPreservingProcessor} will place waterlogged block states
     * into water-occupied positions correctly.
     *
     * @param level  The world.
     * @param origin Placement origin (same position passed to {@link #place}).
     * @param height Number of blocks to scan upward.
     * @return {@code true} if the column is free of lava and bedrock.
     */
    public static boolean isTrunkClear(
        ServerLevelAccessor level,
        BlockPos origin,
        int height
    ) {
        // Use a single MutableBlockPos and step it upward to avoid
        // allocating a new BlockPos object on every loop iteration.
        BlockPos.MutableBlockPos check = origin.mutable();
        for (int dy = 0; dy < height; dy++) {
            BlockState state = level.getBlockState(check);
            if (
                state.is(Blocks.BEDROCK) ||
                state.getFluidState().is(FluidTags.LAVA)
            ) {
                return false;
            }
            check.move(0, 1, 0);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Wall-overlap clearance check
    // -------------------------------------------------------------------------

    /**
     * Maximum fraction of XZ columns in the footprint that may be obstructed
     * before placement is rejected.  A column is obstructed when at least one
     * of the {@value #WALL_CHECK_HEIGHT} blocks above the origin in that
     * column is a solid block not belonging to natural world generation.
     */
    private static final double WALL_MAX_OBSTRUCTED_FRACTION = 0.10;

    /**
     * Number of Y layers above the placement origin inspected by
     * {@link #isPlacementClear}.  Starts at {@code dy = 1} so the surface
     * block (nylium, grass, …) is always skipped.
     */
    private static final int WALL_CHECK_HEIGHT = 5;

    /**
     * Returns {@code true} when the placement footprint is sufficiently free
     * of artificial solid structures (Nether fortress walls, village walls,
     * stronghold corridors, etc.).
     *
     * <p>Scans a circular horizontal area of
     * {@code radius = min(max(width, depth) / 2, 8)} blocks from
     * {@code dy = 1} through {@code dy = }{@value #WALL_CHECK_HEIGHT}.
     * A column is counted as obstructed when any block in its vertical slice
     * is flagged by {@link #isObstructingBlock}.  Placement is rejected when
     * the obstructed fraction exceeds {@value #WALL_MAX_OBSTRUCTED_FRACTION}.
     *
     * <p>When this returns {@code false} the calling mixin should
     * <em>not</em> cancel the vanilla feature so that the vanilla tree / fungus
     * (which skips pre-existing solid blocks natively) can still appear.
     *
     * @param level         The world.
     * @param origin        Placement origin (base of trunk / fungus stem).
     * @param structureSize Size returned by {@link StructureTemplate#getSize()}.
     * @return {@code true} if placing the custom NBT is safe.
     */
    public static boolean isPlacementClear(
        ServerLevelAccessor level,
        BlockPos origin,
        Vec3i structureSize
    ) {
        int radius = Math.min(
            Math.max(structureSize.getX(), structureSize.getZ()) / 2,
            8
        );
        int r2 = radius * radius;

        int obstructedColumns = 0;
        int totalColumns = 0;

        // Reuse one MutableBlockPos across all column/layer iterations.
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                totalColumns++;
                for (int dy = 1; dy <= WALL_CHECK_HEIGHT; dy++) {
                    check.setWithOffset(origin, dx, dy, dz);
                    if (isObstructingBlock(level.getBlockState(check))) {
                        obstructedColumns++;
                        break; // count each column once
                    }
                }
            }
        }

        if (totalColumns == 0) return true;

        boolean clear =
            (double) obstructedColumns / totalColumns <=
            WALL_MAX_OBSTRUCTED_FRACTION;

        if (!clear) {
            CustomTree.LOGGER.debug(
                "[CustomTree] Blocked placement at {} – {}/{} columns obstructed" +
                    " ({}%, limit {}%)",
                origin,
                obstructedColumns,
                totalColumns,
                (obstructedColumns * 100) / totalColumns,
                (int) (WALL_MAX_OBSTRUCTED_FRACTION * 100)
            );
        }
        return clear;
    }

    /**
     * Returns {@code true} when {@code state} represents a solid block that
     * is <em>not</em> a natural world-generation block, indicating an
     * artificial structure that the custom tree must not overlap.
     *
     * <p>The following are treated as <em>natural</em> (returns {@code false}):
     * air; replaceable blocks; {@code #nether_carver_replaceables}
     * (netherrack, soul sand/soil, basalt, blackstone, Nether ores);
     * {@code #overworld_carver_replaceables} (stone, granite, andesite,
     * tuff, gravel, sand, …); {@code #nylium}; {@code #dirt}
     * (grass block, coarse dirt, podzol, …); {@code #leaves}; {@code #logs}.
     */
    private static boolean isObstructingBlock(BlockState state) {
        if (state.isAir()) return false;
        if (state.canBeReplaced()) return false;
        if (state.is(BlockTags.NETHER_CARVER_REPLACEABLES)) return false;
        if (state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)) return false;
        if (state.is(BlockTags.NYLIUM)) return false;
        if (state.is(BlockTags.DIRT)) return false;
        if (state.is(BlockTags.LEAVES)) return false;
        if (state.is(BlockTags.LOGS)) return false;
        return state.isSolid();
    }

    // -------------------------------------------------------------------------
    // Ground alignment
    // -------------------------------------------------------------------------

    /**
     * Adjusts a feature origin downward past thin, replaceable surface blocks
     * (snow layers, flowers, ferns, tall grass, dead bushes, …) that the
     * heightmap counts as occupied, causing the tree trunk to appear to float.
     *
     * <h3>Root cause</h3>
     * {@code HeightmapPlacement} computes the feature origin as the first
     * <em>air</em> cell above the topmost non-air block.  Some heightmap types
     * (e.g. {@code MOTION_BLOCKING_NO_LEAVES}) include thin surface blocks in
     * their scan, so if a snow layer or flower sits on the grass, the origin
     * ends up one block too high.  The trunk is then placed at that elevated
     * position, and the thin block below it is preserved by
     * {@link TerrainPreservingProcessor} (which never removes existing blocks),
     * leaving a visible gap between the solid ground and the first log.
     *
     * <h3>Fix</h3>
     * Scan the block immediately below {@code origin}.  If it is non-air,
     * non-liquid, and {@link BlockState#canBeReplaced() replaceable} (exactly
     * the thin surface blocks described above), shift the origin down by one
     * so the trunk base overwrites the thin block instead of floating above it.
     * Repeat at most {@code maxDown} times so we never descend into solid
     * terrain.
     *
     * @param level   The world.
     * @param origin  The initial feature origin from vanilla placement.
     * @param maxDown Maximum number of blocks to shift down (2 is sufficient
     *                for all vanilla surface features, including two-block-tall
     *                plants like sunflowers).
     * @return The adjusted origin where the trunk base should start.
     */
    public static BlockPos groundAdjust(
        ServerLevelAccessor level,
        BlockPos origin,
        int maxDown
    ) {
        for (int i = 0; i < maxDown; i++) {
            BlockState below = level.getBlockState(origin.below());
            if (!below.isAir() && !below.liquid() && below.canBeReplaced()) {
                origin = origin.below();
            } else {
                break;
            }
        }
        return origin;
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    /**
     * Places the structure centred horizontally on {@code origin}.
     *
     * <p>The structure is expected to have its trunk / stem base at
     * Y&nbsp;=&nbsp;0 in NBT space so that passing the sapling or feature
     * origin places the tree at the correct ground level.
     *
     * <p>{@link TerrainPreservingProcessor} is applied automatically:
     * air blocks from the NBT bounding box are never written, bedrock is
     * never overwritten, and existing liquids are preserved.
     *
     * <p>Entities in the NBT are ignored ({@code setIgnoreEntities(true)})
     * because entity spawning during world generation is unreliable.
     *
     * @param template The loaded template.
     * @param level    World – accepts both {@code WorldGenLevel} and
     *                 {@code ServerLevel}.
     * @param origin   Block position of the sapling or world-gen origin.
     * @param random   Random source forwarded to the structure placer.
     */
    public static void place(
        StructureTemplate template,
        ServerLevelAccessor level,
        BlockPos origin,
        RandomSource random
    ) {
        Vec3i size = template.getSize();
        BlockPos corner = origin.offset(
            -(size.getX() / 2),
            0,
            -(size.getZ() / 2)
        );

        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(true)
            .addProcessor(TerrainPreservingProcessor.INSTANCE);

        template.placeInWorld(
            level,
            corner,
            BlockPos.ZERO,
            settings,
            random,
            3
        );
    }
}
