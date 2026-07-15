package ru.sorfin.worldheightlimit.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Mod(WorldHeightLimitNeoForge.MOD_ID)
public final class WorldHeightLimitNeoForge {
    public static final String MOD_ID = "worldheightlimit";

    private static final String PACK_NAME = "world-height-limit";
    private static final String CONFIG_FILE = "world-height-limit.json";
    private static final int VANILLA_MIN_TARGET_MAX_Y = 319;
    private static final int MINECRAFT_MAX_DIMENSION_BOUNDARY = 2032;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldHeightLimit");

    private static MinecraftServer server;
    private static ModConfig config;
    private static CompatibilityProfile compatibilityProfile;
    private static Path lastPackRoot;

    public WorldHeightLimitNeoForge() {
        reloadConfig();
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerCommand(event.getDispatcher(), "worldheightlimit");
        registerCommand(event.getDispatcher(), "highbuildlimit");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        ensureConfig();
        compatibilityProfile = CompatibilityProfile.detect(server.getServerVersion());
        boolean changed = writeDatapack(server, config, compatibilityProfile);
        if (changed) {
            LOGGER.warn("Datapack files changed. Fully restart the server to apply world height changes.");
        } else {
            LOGGER.info("Datapack is already up to date.");
        }
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .then(Commands.literal("reload").executes(context -> handleReload(context.getSource())))
            .then(Commands.literal("status").executes(context -> handleStatus(context.getSource())))
            .then(Commands.literal("debug").executes(context -> handleDebug(context.getSource())))
            .then(Commands.literal("worlds").executes(context -> handleWorlds(context.getSource())))
            .then(Commands.literal("config").executes(context -> handleConfig(context.getSource())))
            .then(Commands.literal("set")
                .then(Commands.argument("maxY", IntegerArgumentType.integer(VANILLA_MIN_TARGET_MAX_Y, MINECRAFT_MAX_DIMENSION_BOUNDARY - 1))
                    .executes(context -> handleSet(context.getSource(), IntegerArgumentType.getInteger(context, "maxY"), false))
                    .then(Commands.literal("confirm")
                        .executes(context -> handleSet(context.getSource(), IntegerArgumentType.getInteger(context, "maxY"), true)))))
            .then(Commands.literal("help").executes(context -> handleHelp(context.getSource())))
            .executes(context -> handleHelp(context.getSource()));
        dispatcher.register(root);
    }

    private static int handleReload(CommandSourceStack source) {
        if (!hasAdminPermission(source)) {
            error(source, "You need operator permissions to use this command.");
            return 0;
        }
        reloadConfig();
        compatibilityProfile = server == null ? CompatibilityProfile.V1_21_11 : CompatibilityProfile.detect(server.getServerVersion());
        boolean changed = server != null && writeDatapack(server, config, compatibilityProfile);
        success(source, "World Height Limit reloaded.");
        if (changed) {
            warn(source, "Datapack files changed. Fully restart the server to apply world height changes.");
        }
        return 1;
    }

    private static int handleStatus(CommandSourceStack source) {
        ensureConfig();
        DimensionConfig overworld = config.overworld.effective(config.targetMaxY);
        title(source, "World Height Limit");
        info(source, "Server", serverName());
        info(source, "Mod version", "1.1.1");
        info(source, "Configured target max Y", String.valueOf(config.targetMaxY));
        info(source, "Supported max Y range", VANILLA_MIN_TARGET_MAX_Y + "-" + (MINECRAFT_MAX_DIMENSION_BOUNDARY - 1));
        info(source, "Real generated max Y", String.valueOf(overworld.effectiveMaxY()));
        info(source, "Lowering protection", "confirm required");
        state(source, "Datapack", isDatapackActive() ? "active" : "missing", isDatapackActive());
        info(source, "Compatibility", currentProfile().displayName);
        return 1;
    }

    private static int handleDebug(CommandSourceStack source) {
        ensureConfig();
        ServerLevel level = source.getLevel();
        DimensionConfig dimension = config.forLevel(level).effective(config.targetMaxY);
        title(source, "World Height Limit Info");
        send(source, "");
        info(source, "Server", serverName());
        info(source, "Mod version", "1.1.1");
        info(source, "World", worldDisplayName(level));
        send(source, "");
        info(source, "Configured target max Y", String.valueOf(config.targetMaxY));
        info(source, "Supported max Y range", VANILLA_MIN_TARGET_MAX_Y + "-" + (MINECRAFT_MAX_DIMENSION_BOUNDARY - 1));
        info(source, "Real generated max Y", String.valueOf(dimension.effectiveMaxY()));
        info(source, "Min Y", String.valueOf(dimension.minY));
        info(source, "Total height", String.valueOf(dimension.height()));
        info(source, "Lowering protection", "confirm required");
        send(source, "");
        state(source, "Datapack", isDatapackActive() ? "active" : "missing", isDatapackActive());
        info(source, "Dimension", dimensionKey(level));
        info(source, "Compatibility", currentProfile().displayName);
        state(source, "Status", "OK", true);
        return 1;
    }

    private static int handleWorlds(CommandSourceStack source) {
        ensureConfig();
        if (server == null) {
            error(source, "Server is not ready yet.");
            return 0;
        }
        title(source, "World Height Limit Worlds");
        for (ServerLevel level : server.getAllLevels()) {
            int minY = readInt(level, config.forLevel(level).minY, "getMinY", "getMinBuildHeight");
            int height = readInt(level, config.forLevel(level).effective(config.targetMaxY).height(), "getHeight");
            int maxY = minY + height - 1;
            info(source, worldDisplayName(level), minY + " to " + maxY);
        }
        return 1;
    }

    private static int handleSet(CommandSourceStack source, int maxY, boolean confirmed) {
        if (!hasAdminPermission(source)) {
            error(source, "You need operator permissions to use this command.");
            return 0;
        }
        ensureConfig();
        boolean lowering = maxY < config.targetMaxY;
        if (lowering && !confirmed) {
            error(source, "Refusing to lower world height from " + config.targetMaxY + " to " + maxY + ".");
            warn(source, "Lowering a live world can hide or break blocks above the new limit.");
            warn(source, "Run /worldheightlimit set " + maxY + " confirm if you made a backup.");
            return 0;
        }
        int previousMaxY = config.targetMaxY;
        config.targetMaxY = maxY;
        try {
            validateConfig(config);
            saveConfig();
        } catch (IllegalArgumentException | IOException ex) {
            config.targetMaxY = previousMaxY;
            error(source, "Could not save height: " + ex.getMessage());
            return 0;
        }

        compatibilityProfile = server == null ? CompatibilityProfile.V1_21_11 : CompatibilityProfile.detect(server.getServerVersion());
        boolean changed = server != null && writeDatapack(server, config, compatibilityProfile);
        DimensionConfig overworld = config.overworld.effective(config.targetMaxY);

        success(source, "World height target updated.");
        if (lowering) {
            warn(source, "Height was lowered. Blocks above the new generated max Y may become inaccessible.");
        }
        info(source, "Configured target max Y", String.valueOf(config.targetMaxY));
        info(source, "Real generated max Y", String.valueOf(overworld.effectiveMaxY()));
        info(source, "Total generated height", String.valueOf(overworld.height()));
        if (changed) {
            warn(source, "Datapack files changed. Fully restart the server to apply world height changes.");
        } else {
            warn(source, "Datapack already matched this height. Restart is still required if the world is not using it yet.");
        }
        return 1;
    }

    private static int handleConfig(CommandSourceStack source) {
        ensureConfig();
        title(source, "World Height Limit Config");
        info(source, "Config file", String.valueOf(configPath()));
        return 1;
    }

    private static int handleHelp(CommandSourceStack source) {
        title(source, "World Height Limit Commands");
        info(source, "Height range", VANILLA_MIN_TARGET_MAX_Y + "-" + (MINECRAFT_MAX_DIMENSION_BOUNDARY - 1));
        command(source, "/worldheightlimit status", "Show current height status");
        command(source, "/worldheightlimit debug", "Show detailed world info");
        command(source, "/worldheightlimit worlds", "List loaded world heights");
        command(source, "/worldheightlimit config", "Show config path");
        command(source, "/worldheightlimit reload", "Reload config and datapack");
        command(source, "/worldheightlimit set <maxY>", "Change target height");
        command(source, "/worldheightlimit set <maxY> confirm", "Confirm lowering height");
        return 1;
    }

    private static void ensureConfig() {
        if (config == null) {
            reloadConfig();
        }
    }

    private static void reloadConfig() {
        Path configPath = configPath();
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                config = ModConfig.defaults();
                Files.writeString(configPath, GSON.toJson(config), StandardCharsets.UTF_8);
                LOGGER.info("Created default config at {}", configPath);
                return;
            }
            config = Objects.requireNonNullElse(GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8), ModConfig.class), ModConfig.defaults());
            config.fillMissing();
            validateConfig(config);
            LOGGER.info("Loaded config from {}", configPath);
        } catch (IOException | JsonSyntaxException | IllegalArgumentException ex) {
            LOGGER.error("Failed to load config. Falling back to defaults.", ex);
            config = ModConfig.defaults();
            try {
                saveConfig();
            } catch (IOException saveEx) {
                LOGGER.error("Failed to save repaired default config.", saveEx);
            }
        }
    }

    private static void saveConfig() throws IOException {
        Path configPath = configPath();
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, GSON.toJson(config), StandardCharsets.UTF_8);
        LOGGER.info("Saved config to {}", configPath);
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
    }

    private static void validateConfig(ModConfig loaded) {
        if (loaded.targetMaxY < VANILLA_MIN_TARGET_MAX_Y) {
            throw new IllegalArgumentException("targetMaxY must be at least " + VANILLA_MIN_TARGET_MAX_Y + ".");
        }
        for (DimensionConfig dimension : List.of(loaded.overworld, loaded.nether, loaded.end)) {
            if (!dimension.enabled) {
                continue;
            }
            if (dimension.minY % 16 != 0) {
                throw new IllegalArgumentException("dimension minY must be a multiple of 16.");
            }
            if (dimension.logicalHeight < 16) {
                throw new IllegalArgumentException("dimension logicalHeight must be at least 16.");
            }
            if (loaded.targetMaxY < dimension.minY) {
                throw new IllegalArgumentException("targetMaxY must be greater than or equal to dimension minY.");
            }
            DimensionConfig effective = dimension.effective(loaded.targetMaxY);
            if (effective.upperBoundary() > MINECRAFT_MAX_DIMENSION_BOUNDARY) {
                throw new IllegalArgumentException("targetMaxY " + loaded.targetMaxY + " is too high for Minecraft 1.21.11. Maximum for this config is " + dimension.maxAllowedTargetY() + ".");
            }
        }
    }

    private static boolean writeDatapack(MinecraftServer minecraftServer, ModConfig loaded, CompatibilityProfile profile) {
        Path saveRoot = minecraftServer.getWorldPath(LevelResource.ROOT);
        lastPackRoot = saveRoot.resolve("datapacks").resolve(PACK_NAME);
        Path dimensionRoot = lastPackRoot.resolve("data").resolve("minecraft").resolve("dimension_type");
        List<FileWrite> writes = new ArrayList<>();
        writes.add(new FileWrite(lastPackRoot.resolve("pack.mcmeta"), profile.packMcmetaJson()));
        if (loaded.overworld.enabled) {
            writes.add(new FileWrite(dimensionRoot.resolve("overworld.json"), profile.buildOverworldJson(loaded.overworld.effective(loaded.targetMaxY))));
        }
        if (loaded.nether.enabled) {
            writes.add(new FileWrite(dimensionRoot.resolve("the_nether.json"), profile.buildNetherJson(loaded.nether.effective(loaded.targetMaxY))));
        }
        if (loaded.end.enabled) {
            writes.add(new FileWrite(dimensionRoot.resolve("the_end.json"), profile.buildEndJson(loaded.end.effective(loaded.targetMaxY))));
        }

        boolean changed = false;
        for (FileWrite write : writes) {
            try {
                Files.createDirectories(write.path().getParent());
                String existing = Files.exists(write.path()) ? Files.readString(write.path(), StandardCharsets.UTF_8) : null;
                if (!write.content().equals(existing)) {
                    Files.writeString(write.path(), write.content(), StandardCharsets.UTF_8);
                    changed = true;
                }
            } catch (IOException ex) {
                LOGGER.error("Failed to write datapack file {}", write.path(), ex);
            }
        }
        return changed;
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text));
    }

    private static void title(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void info(CommandSourceStack source, String key, String value) {
        source.sendSystemMessage(Component.literal(key + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
    }

    private static void state(CommandSourceStack source, String key, String value, boolean good) {
        source.sendSystemMessage(Component.literal(key + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(value).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED)));
    }

    private static void command(CommandSourceStack source, String command, String description) {
        source.sendSystemMessage(Component.literal(command).withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(description).withStyle(ChatFormatting.GRAY)));
    }

    private static void success(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private static void warn(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    private static void error(CommandSourceStack source, String text) {
        source.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    private static boolean isDatapackActive() {
        return lastPackRoot != null && Files.exists(lastPackRoot.resolve("pack.mcmeta"));
    }

    private static CompatibilityProfile currentProfile() {
        return compatibilityProfile == null ? CompatibilityProfile.V1_21_11 : compatibilityProfile;
    }

    private static String serverName() {
        return server == null ? "unknown" : "NeoForge " + server.getServerVersion();
    }

    private static String worldDisplayName(ServerLevel level) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            return "world";
        }
        if (level.dimension().equals(Level.NETHER)) {
            return "world_nether";
        }
        if (level.dimension().equals(Level.END)) {
            return "world_the_end";
        }
        return resourceKeyId(level.dimension());
    }

    private static String dimensionKey(ServerLevel level) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            return "overworld";
        }
        if (level.dimension().equals(Level.NETHER)) {
            return "the_nether";
        }
        if (level.dimension().equals(Level.END)) {
            return "the_end";
        }
        return resourceKeyId(level.dimension());
    }

    private static int readInt(Object target, int fallback, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object result = method.invoke(target);
                if (result instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping-era method name.
            }
        }
        return fallback;
    }

    private static String resourceKeyId(Object resourceKey) {
        for (String methodName : List.of("identifier", "location")) {
            try {
                Method method = resourceKey.getClass().getMethod(methodName);
                return String.valueOf(method.invoke(resourceKey));
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping-era method name.
            }
        }
        return String.valueOf(resourceKey);
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        if (source.getEntity() == null) {
            return true;
        }
        if (source.getServer().isSingleplayer()) {
            return true;
        }
        for (String methodName : List.of("hasPermission", "hasPermissionLevel")) {
            try {
                Method method = source.getClass().getMethod(methodName, int.class);
                Object result = method.invoke(source, 2);
                if (result instanceof Boolean allowed) {
                    return allowed;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next command permission API shape.
            }
        }
        try {
            Object permissionSet = source.getClass().getMethod("permissions").invoke(source);
            Class<?> permissionsClass = Class.forName("net.minecraft.server.permissions.Permissions");
            Object gameMasterPermission = permissionsClass.getField("COMMANDS_GAMEMASTER").get(null);
            Method hasPermission = permissionSet.getClass().getMethod("hasPermission", Class.forName("net.minecraft.server.permissions.Permission"));
            Object result = hasPermission.invoke(permissionSet, gameMasterPermission);
            if (result instanceof Boolean allowed) {
                return allowed;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to deny.
        }
        return false;
    }

    private record FileWrite(Path path, String content) {
    }

    private static final class ModConfig {
        int targetMaxY = 2031;
        DimensionConfig overworld = new DimensionConfig(true, -64, 2096, 192.33);
        DimensionConfig nether = new DimensionConfig(true, 0, 2032, null);
        DimensionConfig end = new DimensionConfig(true, 0, 2032, null);

        static ModConfig defaults() {
            return new ModConfig();
        }

        void fillMissing() {
            if (overworld == null) {
                overworld = new DimensionConfig(true, -64, 2096, 192.33);
            }
            if (nether == null) {
                nether = new DimensionConfig(true, 0, 2032, null);
            }
            if (end == null) {
                end = new DimensionConfig(true, 0, 2032, null);
            }
        }

        DimensionConfig forLevel(ServerLevel level) {
            if (level.dimension().equals(Level.NETHER)) {
                return nether;
            }
            if (level.dimension().equals(Level.END)) {
                return end;
            }
            return overworld;
        }
    }

    private static final class DimensionConfig {
        boolean enabled;
        int minY;
        transient int height;
        int logicalHeight;
        Double cloudHeight;

        DimensionConfig(boolean enabled, int minY, int logicalHeight, Double cloudHeight) {
            this(enabled, minY, logicalHeight, logicalHeight, cloudHeight);
        }

        DimensionConfig(boolean enabled, int minY, int height, int logicalHeight, Double cloudHeight) {
            this.enabled = enabled;
            this.minY = minY;
            this.height = height;
            this.logicalHeight = logicalHeight;
            this.cloudHeight = cloudHeight;
        }

        DimensionConfig effective(int targetMaxY) {
            int requestedHeight = targetMaxY - minY + 1;
            int generatedHeight = Math.max(16, roundUpToSection(requestedHeight));
            int generatedLogicalHeight = Math.min(generatedHeight, roundUpToSection(logicalHeight));
            return new DimensionConfig(enabled, minY, generatedHeight, generatedLogicalHeight, cloudHeight);
        }

        int height() {
            return height;
        }

        int effectiveMaxY() {
            return minY + height - 1;
        }

        int upperBoundary() {
            return minY + height;
        }

        int maxAllowedTargetY() {
            return MINECRAFT_MAX_DIMENSION_BOUNDARY - 1;
        }

        private static int roundUpToSection(int value) {
            return ((value + 15) / 16) * 16;
        }
    }

    private enum CompatibilityProfile {
        V1_20_2("1.20.2", "1.20.2", "1.20.2", 18, false, false),
        V1_20_3_TO_1_20_4("1.20.3-1.20.4", "1.20.3", "1.20.4", 26, false, false),
        V1_20_5_TO_1_20_6("1.20.5-1.20.6", "1.20.5", "1.20.6", 41, false, false),
        V1_21_TO_1_21_1("1.21-1.21.1", "1.21.0", "1.21.1", 48, false, false),
        V1_21_2_TO_1_21_3("1.21.2-1.21.3", "1.21.2", "1.21.3", 57, false, false),
        V1_21_4("1.21.4", "1.21.4", "1.21.4", 61, false, false),
        V1_21_5("1.21.5", "1.21.5", "1.21.5", 71, false, false),
        V1_21_6("1.21.6", "1.21.6", "1.21.6", 80, false, false),
        V1_21_7_TO_1_21_8("1.21.7-1.21.8", "1.21.7", "1.21.8", 81, false, false),
        V1_21_9_TO_1_21_10("1.21.9-1.21.10", "1.21.9", "1.21.10", 88, true, false),
        V1_21_11("1.21.11", "1.21.11", "1.21.11", 94, true, true);

        private final String displayName;
        private final Version minVersion;
        private final Version maxVersion;
        private final int dataPackFormat;
        private final boolean usesMinMaxPackFormat;
        private final boolean usesModernDimensionSchema;

        CompatibilityProfile(String displayName, String minVersion, String maxVersion, int dataPackFormat, boolean usesMinMaxPackFormat, boolean usesModernDimensionSchema) {
            this.displayName = displayName;
            this.minVersion = Version.parse(minVersion);
            this.maxVersion = Version.parse(maxVersion);
            this.dataPackFormat = dataPackFormat;
            this.usesMinMaxPackFormat = usesMinMaxPackFormat;
            this.usesModernDimensionSchema = usesModernDimensionSchema;
        }

        static CompatibilityProfile detect(String versionText) {
            Version version = Version.parse(versionText);
            for (CompatibilityProfile profile : values()) {
                if (version.compareTo(profile.minVersion) >= 0 && version.compareTo(profile.maxVersion) <= 0) {
                    return profile;
                }
            }
            return V1_21_11;
        }

        String packMcmetaJson() {
            if (this == V1_21_11) {
                return """
                    {
                      "pack": {
                        "description": "Generated by WorldHeightLimit",
                        "max_format": 94,
                        "min_format": [94, 1]
                      }
                    }
                    """;
            }
            if (usesMinMaxPackFormat) {
                return """
                    {
                      "pack": {
                        "description": "Generated by WorldHeightLimit",
                        "max_format": %d,
                        "min_format": %d
                      }
                    }
                    """.formatted(dataPackFormat, dataPackFormat);
            }
            return """
                {
                  "pack": {
                    "description": "Generated by WorldHeightLimit",
                    "pack_format": %d
                  }
                }
                """.formatted(dataPackFormat);
        }

        String buildOverworldJson(DimensionConfig settings) {
            String cloudHeight = settings.cloudHeight == null ? "192.33" : String.format(Locale.US, "%.2f", settings.cloudHeight);
            if (usesModernDimensionSchema) {
                return """
                    {
                      "ambient_light": 0.0,
                      "attributes": {
                        "minecraft:audio/ambient_sounds": {
                          "mood": {
                            "block_search_extent": 8,
                            "offset": 2.0,
                            "sound": "minecraft:ambient.cave",
                            "tick_delay": 6000
                          }
                        },
                        "minecraft:audio/background_music": {
                          "creative": {
                            "max_delay": 24000,
                            "min_delay": 12000,
                            "sound": "minecraft:music.creative"
                          },
                          "default": {
                            "max_delay": 24000,
                            "min_delay": 12000,
                            "sound": "minecraft:music.game"
                          }
                        },
                        "minecraft:gameplay/bed_rule": {
                          "can_set_spawn": "always",
                          "can_sleep": "when_dark",
                          "error_message": {
                            "translate": "block.minecraft.bed.no_sleep"
                          }
                        },
                        "minecraft:gameplay/nether_portal_spawns_piglin": true,
                        "minecraft:gameplay/respawn_anchor_works": false,
                        "minecraft:visual/cloud_color": "#ccffffff",
                        "minecraft:visual/cloud_height": %s,
                        "minecraft:visual/fog_color": "#c0d8ff",
                        "minecraft:visual/sky_color": "#78a7ff"
                      },
                      "coordinate_scale": 1.0,
                      "has_ceiling": false,
                      "has_skylight": true,
                      "height": %d,
                      "infiniburn": "#minecraft:infiniburn_overworld",
                      "logical_height": %d,
                      "min_y": %d,
                      "monster_spawn_block_light_limit": 0,
                      "monster_spawn_light_level": {
                        "type": "minecraft:uniform",
                        "max_inclusive": 7,
                        "min_inclusive": 0
                      },
                      "timelines": "#minecraft:in_overworld"
                    }
                    """.formatted(cloudHeight, settings.height(), settings.logicalHeight, settings.minY);
            }
            return """
                {
                  "ambient_light": 0.0,
                  "bed_works": true,
                  "coordinate_scale": 1.0,
                  "effects": "minecraft:overworld",
                  "has_ceiling": false,
                  "has_raids": true,
                  "has_skylight": true,
                  "height": %d,
                  "infiniburn": "#minecraft:infiniburn_overworld",
                  "logical_height": %d,
                  "min_y": %d,
                  "monster_spawn_block_light_limit": 0,
                  "monster_spawn_light_level": %s,
                  "natural": true,
                  "piglin_safe": false,
                  "respawn_anchor_works": false,
                  "ultrawarm": false
                }
                """.formatted(settings.height(), settings.logicalHeight, settings.minY, uniformMonsterSpawnLightLevelJson());
        }

        String buildNetherJson(DimensionConfig settings) {
            if (usesModernDimensionSchema) {
                return """
                    {
                      "ambient_light": 0.1,
                      "attributes": {
                        "minecraft:gameplay/bed_rule": {
                          "can_set_spawn": "never",
                          "can_sleep": "never",
                          "explodes": true
                        },
                        "minecraft:gameplay/can_start_raid": false,
                        "minecraft:gameplay/fast_lava": true,
                        "minecraft:gameplay/piglins_zombify": false,
                        "minecraft:gameplay/respawn_anchor_works": true,
                        "minecraft:gameplay/sky_light_level": 4.0,
                        "minecraft:gameplay/snow_golem_melts": true,
                        "minecraft:gameplay/water_evaporates": true,
                        "minecraft:visual/default_dripstone_particle": {
                          "type": "minecraft:dripping_dripstone_lava"
                        },
                        "minecraft:visual/fog_end_distance": 96.0,
                        "minecraft:visual/fog_start_distance": 10.0,
                        "minecraft:visual/sky_light_color": "#7a7aff",
                        "minecraft:visual/sky_light_factor": 0.0
                      },
                      "cardinal_light": "nether",
                      "coordinate_scale": 8.0,
                      "has_ceiling": true,
                      "has_fixed_time": true,
                      "has_skylight": false,
                      "height": %d,
                      "infiniburn": "#minecraft:infiniburn_nether",
                      "logical_height": %d,
                      "min_y": %d,
                      "monster_spawn_block_light_limit": 15,
                      "monster_spawn_light_level": 7,
                      "skybox": "none",
                      "timelines": "#minecraft:in_nether"
                    }
                    """.formatted(settings.height(), settings.logicalHeight, settings.minY);
            }
            return """
                {
                  "ambient_light": 0.1,
                  "bed_works": false,
                  "coordinate_scale": 8.0,
                  "effects": "minecraft:the_nether",
                  "fixed_time": 18000,
                  "has_ceiling": true,
                  "has_raids": false,
                  "has_skylight": false,
                  "height": %d,
                  "infiniburn": "#minecraft:infiniburn_nether",
                  "logical_height": %d,
                  "min_y": %d,
                  "monster_spawn_block_light_limit": 15,
                  "monster_spawn_light_level": 7,
                  "natural": false,
                  "piglin_safe": true,
                  "respawn_anchor_works": true,
                  "ultrawarm": true
                }
                """.formatted(settings.height(), settings.logicalHeight, settings.minY);
        }

        String buildEndJson(DimensionConfig settings) {
            if (usesModernDimensionSchema) {
                return """
                    {
                      "ambient_light": 0.25,
                      "attributes": {
                        "minecraft:audio/ambient_sounds": {
                          "mood": {
                            "block_search_extent": 8,
                            "offset": 2.0,
                            "sound": "minecraft:ambient.cave",
                            "tick_delay": 6000
                          }
                        },
                        "minecraft:audio/background_music": {
                          "default": {
                            "max_delay": 24000,
                            "min_delay": 6000,
                            "replace_current_music": true,
                            "sound": "minecraft:music.end"
                          }
                        },
                        "minecraft:gameplay/bed_rule": {
                          "can_set_spawn": "never",
                          "can_sleep": "never",
                          "explodes": true
                        },
                        "minecraft:gameplay/respawn_anchor_works": false,
                        "minecraft:visual/fog_color": "#181318",
                        "minecraft:visual/sky_color": "#000000",
                        "minecraft:visual/sky_light_color": "#e580ff",
                        "minecraft:visual/sky_light_factor": 0.0
                      },
                      "coordinate_scale": 1.0,
                      "has_ceiling": false,
                      "has_fixed_time": true,
                      "has_skylight": true,
                      "height": %d,
                      "infiniburn": "#minecraft:infiniburn_end",
                      "logical_height": %d,
                      "min_y": %d,
                      "monster_spawn_block_light_limit": 0,
                      "monster_spawn_light_level": 15,
                      "skybox": "end",
                      "timelines": "#minecraft:in_end"
                    }
                    """.formatted(settings.height(), settings.logicalHeight, settings.minY);
            }
            return """
                {
                  "ambient_light": 0.0,
                  "bed_works": false,
                  "coordinate_scale": 1.0,
                  "effects": "minecraft:the_end",
                  "fixed_time": 6000,
                  "has_ceiling": false,
                  "has_raids": true,
                  "has_skylight": false,
                  "height": %d,
                  "infiniburn": "#minecraft:infiniburn_end",
                  "logical_height": %d,
                  "min_y": %d,
                  "monster_spawn_block_light_limit": 0,
                  "monster_spawn_light_level": %s,
                  "natural": false,
                  "piglin_safe": false,
                  "respawn_anchor_works": false,
                  "ultrawarm": false
                }
                """.formatted(settings.height(), settings.logicalHeight, settings.minY, uniformMonsterSpawnLightLevelJson());
        }

        private String uniformMonsterSpawnLightLevelJson() {
            if (ordinal() <= V1_20_3_TO_1_20_4.ordinal()) {
                return """
                    {
                        "type": "minecraft:uniform",
                        "value": {
                          "max_inclusive": 7,
                          "min_inclusive": 0
                        }
                      }""";
            }
            return """
                {
                    "type": "minecraft:uniform",
                    "max_inclusive": 7,
                    "min_inclusive": 0
                  }""";
        }
    }

    private record Version(int major, int minor, int patch) implements Comparable<Version> {
        static Version parse(String value) {
            String[] parts = value.split("\\.");
            int major = parts.length > 0 ? parsePart(parts[0]) : 0;
            int minor = parts.length > 1 ? parsePart(parts[1]) : 0;
            int patch = parts.length > 2 ? parsePart(parts[2]) : 0;
            return new Version(major, minor, patch);
        }

        private static int parsePart(String raw) {
            String digits = raw.replaceAll("[^0-9].*$", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }

        @Override
        public int compareTo(Version other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            return Integer.compare(patch, other.patch);
        }
    }
}
