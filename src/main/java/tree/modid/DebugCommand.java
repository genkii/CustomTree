package tree.modid;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.InputStream;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * In-game debug command for the Custom Tree mod.
 *
 * <h3>Commands</h3>
 * <ul>
 *   <li>{@code /customtrees status} – lists every registered definition,
 *       shows whether its NBT file can be found in the jar, and reports the
 *       current biome at the caller's position.</li>
 *   <li>{@code /customtrees test <nbt_name>} – immediately loads and places
 *       the named NBT structure at the caller's feet so you can verify the
 *       file without waiting for world generation.</li>
 * </ul>
 *
 * <h3>Permission</h3>
 * Both sub-commands require operator level 2 ({@code /op}).
 *
 * <h3>Registration</h3>
 * Call {@link #register()} once inside {@link CustomTree#onInitialize()}.
 */
public final class DebugCommand {

    private DebugCommand() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers the {@code /customtrees} command tree with the Fabric
     * command-registration event.  Must be called during
     * {@link CustomTree#onInitialize()}.
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) ->
                dispatcher.register(
                    Commands.literal("customtrees")
                        .requires(src ->
                            src
                                .permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER)
                        )
                        // /customtrees status
                        .then(
                            Commands.literal("status").executes(
                                DebugCommand::runStatus
                            )
                        )
                        // /customtrees test <nbt_name>
                        .then(
                            Commands.literal("test").then(
                                Commands.argument(
                                    "name",
                                    StringArgumentType.word()
                                ).executes(DebugCommand::runTest)
                            )
                        )
                )
        );
    }

    // -------------------------------------------------------------------------
    // /customtrees status
    // -------------------------------------------------------------------------

    private static int runStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<CustomTreeDefinition> defs = CustomTreeRegistry.getAll();

        send(src, "§6=== Custom Tree Status ===");

        if (defs.isEmpty()) {
            send(src, "§cNo tree definitions registered!");
            send(
                src,
                "§7Make sure registerTree() / registerFungus() are called in onInitialize()."
            );
            return 0;
        }

        send(src, "§aRegistered definitions: §f" + defs.size());

        // Per-definition NBT file check
        for (CustomTreeDefinition def : defs) {
            String path = def.getNbtResourcePath();
            boolean found = resourceExists(path);
            String icon = found ? "§a✔" : "§c✘";
            String label = found ? "§afound" : "§cMISSING";
            send(src, icon + " §f" + path + " §7– " + label);
            if (!found) {
                send(
                    src,
                    "  §7→ place the file at: §esrc/main/resources" + path
                );
                send(src, "  §7→ then rebuild the mod with: §e./gradlew build");
            }
        }

        // Current biome (if called by a player)
        if (src.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = player.blockPosition();
            String biomeName = level
                .getBiome(pos)
                .unwrapKey()
                .<String>map(k -> k.toString())
                .orElse("unknown");
            send(src, "§6Current biome: §f" + biomeName);
            send(
                src,
                "§6Position: §f" +
                    pos.getX() +
                    " " +
                    pos.getY() +
                    " " +
                    pos.getZ()
            );
        }

        send(src, "§6=========================");
        return defs.size();
    }

    // -------------------------------------------------------------------------
    // /customtrees test <nbt_name>
    // -------------------------------------------------------------------------

    private static int runTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        // Must be run by a player
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            send(src, "§cThis command must be run by a player.");
            return 0;
        }

        String name = StringArgumentType.getString(ctx, "name");

        // Build the resource path from the short name the user typed.
        // Strip a trailing ".nbt" the user may have included, then re-add it
        // so both "/customtrees test oak_tree" and "/customtrees test oak_tree.nbt" work.
        String baseName = name.endsWith(".nbt")
            ? name.substring(0, name.length() - 4)
            : name;
        String path = baseName.startsWith("/data/")
            ? baseName + ".nbt"
            : "/data/custom-tree/structures/" + baseName + ".nbt";

        send(src, "§6Looking for: §f" + path);

        // Check existence first so the error message is clear
        if (!resourceExists(path)) {
            send(src, "§c✘ File not found: " + path);
            send(src, "§7Place the file at: §esrc/main/resources" + path);
            send(src, "§7Then rebuild: §e./gradlew build");
            return 0;
        }

        // Try to load the template
        ServerLevel level = (ServerLevel) player.level();
        StructureTemplate template;
        try (InputStream in = CustomTree.class.getResourceAsStream(path)) {
            if (in == null) {
                send(
                    src,
                    "§cStream was null even after existence check – unexpected error."
                );
                return 0;
            }
            CompoundTag nbt = NbtIo.readCompressed(
                in,
                NbtAccounter.unlimitedHeap()
            );
            template = new StructureTemplate();
            template.load(
                level.registryAccess().lookupOrThrow(Registries.BLOCK),
                nbt
            );
        } catch (Exception e) {
            send(src, "§cFailed to load NBT: " + e.getMessage());
            CustomTree.LOGGER.error(
                "[CustomTree] /customtrees test failed for {}: {}",
                path,
                e.getMessage()
            );
            return 0;
        }

        // Place at the player's feet, centred horizontally
        BlockPos origin = player.blockPosition();
        net.minecraft.core.Vec3i size = template.getSize();
        BlockPos corner = origin.offset(
            -(size.getX() / 2),
            0,
            -(size.getZ() / 2)
        );

        // Use the same TerrainPreservingProcessor as world-gen so the test
        // command does not overwrite bedrock, lava, or existing terrain blocks.
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(true)
            .addProcessor(
                tree.modid.NbtTreePlacer.getTerrainPreservingProcessor()
            );

        template.placeInWorld(
            level,
            corner,
            BlockPos.ZERO,
            settings,
            level.getRandom(),
            3
        );

        send(src, "§a✔ Placed §f" + path);
        send(
            src,
            "§7Size: " +
                size.getX() +
                "×" +
                size.getY() +
                "×" +
                size.getZ() +
                " at " +
                origin.getX() +
                " " +
                origin.getY() +
                " " +
                origin.getZ()
        );

        CustomTree.LOGGER.info(
            "[CustomTree] /customtrees test placed {} at {}",
            path,
            origin
        );
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a styled chat message to the command source.
     * Uses legacy §-codes because they are already embedded in all call sites;
     * the vanilla client renders them correctly.
     */
    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), true);
    }

    /**
     * Returns {@code true} when the resource at {@code path} can be opened
     * from the mod's class loader.  Used to give the user early feedback
     * without actually loading the NBT (which requires registry access).
     */
    private static boolean resourceExists(String path) {
        try (InputStream in = CustomTree.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }
}
