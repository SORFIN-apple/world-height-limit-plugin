package ru.sorfin.worldheightlimit;

import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class WorldHeightLimitPlugin extends JavaPlugin implements Listener {

    private static final String PACK_NAME = "high-build-limit-generated";
    private static final String DEFAULT_BYPASS_PERMISSION = "highbuildlimit.bypass";
    private static final String DEFAULT_RELOAD_PERMISSION = "highbuildlimit.reload";
    private static final String DEFAULT_STATUS_PERMISSION = "highbuildlimit.status";
    private static final String DEFAULT_FILL_PERMISSION = "highbuildlimit.fill";

    private final Map<UUID, Long> lastDeniedNoticeAt = new ConcurrentHashMap<>();

    private PluginSettings settings;
    private boolean heightmapWarningFilterInstalled;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String serverVersion = resolveMinecraftVersion();
        CompatibilityProfile compatibilityProfile = CompatibilityProfile.detect(serverVersion);
        if (compatibilityProfile == null) {
            getLogger().severe("Unsupported Minecraft version: " + serverVersion + ". Supported versions: 1.19 through 1.19.4.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            settings = reloadPluginSettings(compatibilityProfile);
        } catch (IllegalArgumentException ex) {
            getLogger().log(Level.SEVERE, "Invalid config.yml: " + ex.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        installOptionalLogFilters();
        Bukkit.getPluginManager().registerEvents(this, this);
        applyDatapackChanges(settings, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        RestrictionRule rule = settings.restrictions().forWorld(event.getBlockPlaced().getWorld());
        if (!rule.enabled() || !rule.blockPlace()) {
            return;
        }
        denyIfNeeded(event.getPlayer(), event.getBlockPlaced().getWorld(), event.getBlockPlaced().getY(), DenyReason.BUILD);
        if (!canBuildAt(event.getPlayer(), event.getBlockPlaced().getWorld(), event.getBlockPlaced().getY())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        RestrictionRule rule = settings.restrictions().forWorld(event.getBlock().getWorld());
        if (!rule.enabled() || !rule.blockBreak()) {
            return;
        }
        denyIfNeeded(event.getPlayer(), event.getBlock().getWorld(), event.getBlock().getY(), DenyReason.BREAK);
        if (!canBuildAt(event.getPlayer(), event.getBlock().getWorld(), event.getBlock().getY())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        World world = event.getBlockClicked().getWorld();
        RestrictionRule rule = settings.restrictions().forWorld(world);
        if (!rule.enabled() || !rule.buckets()) {
            return;
        }

        int targetY = event.getBlockClicked().getRelative(event.getBlockFace()).getY();
        denyIfNeeded(event.getPlayer(), world, targetY, DenyReason.BUILD);
        if (!canBuildAt(event.getPlayer(), world, targetY)) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("worldheightlimit") && !command.getName().equalsIgnoreCase("highbuildlimit")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }

        if (args[0].equalsIgnoreCase("status")) {
            return handleStatus(sender);
        }

        if (args[0].equalsIgnoreCase("debug")) {
            return handleDebug(sender);
        }

        if (args[0].equalsIgnoreCase("worlds")) {
            return handleWorlds(sender);
        }

        if (args[0].equalsIgnoreCase("fill")) {
            return handleFill(sender, args);
        }

        if (args[0].equalsIgnoreCase("platform")) {
            return handlePlatform(sender, args);
        }

        sendMessage(sender, "<yellow>Usage: /" + label + " <reload|status|fill|platform|help>");
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(settings.messages().reloadPermission())) {
            sendMessage(sender, settings.messages().noPermissionMessage());
            return true;
        }

        reloadConfig();
        try {
            settings = reloadPluginSettings(settings.compatibilityProfile());
        } catch (IllegalArgumentException ex) {
            sendMessage(sender, "<red>Config reload failed: " + ex.getMessage());
            return true;
        }

        installOptionalLogFilters();
        boolean changedAny = applyDatapackChanges(settings, false);
        sendMessage(sender, settings.messages().reloadSuccessMessage());
        if (changedAny) {
            sendMessage(sender, settings.messages().restartRequiredMessage());
        } else {
            sendMessage(sender, settings.messages().reloadNoDatapackChangesMessage());
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission(settings.messages().statusPermission())) {
            sendMessage(sender, settings.messages().noPermissionMessage());
            return true;
        }

        sendMessage(sender, "<gold><bold>WorldHeightLimit status");
        sendMessage(sender, "<gray>Server version:</gray> <white>" + resolveMinecraftVersion());
        sendMessage(sender, "<gray>Compatibility profile:</gray> <white>" + settings.compatibilityProfile().displayName());
        sendMessage(sender, "<gray>Target max Y:</gray> <white>" + settings.targetMaxY());
        sendMessage(sender, "<gray>Effective Overworld technical max Y:</gray> <white>" + settings.overworld().effectiveMaxY() + " (height " + settings.overworld().height() + ")");
        sendMessage(sender, "<gray>Effective Nether technical max Y:</gray> <white>" + settings.nether().effectiveMaxY() + " (height " + settings.nether().height() + ")");
        sendMessage(sender, "<gray>Effective End technical max Y:</gray> <white>" + settings.end().effectiveMaxY() + " (height " + settings.end().height() + ")");
        sendMessage(sender, "<gray>Bypass permission:</gray> <white>" + settings.messages().bypassPermission());
        sendMessage(sender, "<gray>Reload permission:</gray> <white>" + settings.messages().reloadPermission());
        sendMessage(sender, "<gray>Status permission:</gray> <white>" + settings.messages().statusPermission());
        sendMessage(sender, "<gray>Fill permission:</gray> <white>" + settings.messages().fillPermission());
        sendMessage(sender, "<gray>Notification mode:</gray> <white>" + settings.messages().notificationMode().name());
        sendMessage(sender, "<gray>Notification cooldown:</gray> <white>" + settings.messages().notificationCooldownMillis() + " ms");
        sendMessage(sender, "<gray>Suppress heightmap warnings:</gray> <white>" + settings.loggingSettings().suppressHeightmapWarnings());
        sendMessage(sender, "<gray>Max admin fill blocks:</gray> <white>" + settings.adminTools().maxFillBlocks());
        sendMessage(sender, describeRestriction("Overworld", settings.restrictions().overworld()));
        sendMessage(sender, describeRestriction("Nether", settings.restrictions().nether()));
        sendMessage(sender, describeRestriction("End", settings.restrictions().end()));
        sendMessage(sender, "<yellow>Datapack height changes still require a full server restart to take effect.");
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission(settings.messages().statusPermission())) {
            sendMessage(sender, settings.messages().noPermissionMessage());
            return true;
        }

        World world = resolveWorld(sender);
        if (world == null) {
            sendMessage(sender, "<red>No loaded worlds were found.");
            return true;
        }

        DimensionSettings dimension = settings.dimensionForWorld(world);
        RestrictionRule rule = settings.restrictions().forWorld(world);
        boolean datapackActive = isDatapackActive(world);
        String status = datapackActive ? "<green>OK" : "<yellow>Datapack not found";

        sendMessage(sender, "<gold><bold>World Height Limit Info");
        sendMessage(sender, "");
        sendMessage(sender, "<gray>Server:</gray> <white>" + getServerDisplayName());
        sendMessage(sender, "<gray>Plugin version:</gray> <white>" + getDescription().getVersion());
        sendMessage(sender, "<gray>World:</gray> <white>" + world.getName());
        sendMessage(sender, "");
        sendMessage(sender, "<gray>Configured target max Y:</gray> <white>" + settings.targetMaxY());
        sendMessage(sender, "<gray>Real generated max Y:</gray> <white>" + dimension.effectiveMaxY());
        sendMessage(sender, "<gray>Min Y:</gray> <white>" + dimension.minY());
        sendMessage(sender, "<gray>Total height:</gray> <white>" + dimension.height());
        sendMessage(sender, "");
        sendMessage(sender, "<gray>Vanilla player limit:</gray> <white>" + (rule.enabled() ? "enabled" : "disabled"));
        sendMessage(sender, "<gray>Build above vanilla limit:</gray> <white>" + (rule.enabled() ? "blocked" : "allowed"));
        sendMessage(sender, "<gray>Bypass permission:</gray> <white>" + settings.messages().bypassPermission());
        sendMessage(sender, "");
        sendMessage(sender, "<gray>Datapack:</gray> <white>" + (datapackActive ? "active" : "missing"));
        sendMessage(sender, "<gray>Dimension:</gray> <white>" + getDimensionName(world));
        sendMessage(sender, "<gray>Status:</gray> " + status);
        return true;
    }

    private boolean handleWorlds(CommandSender sender) {
        if (!sender.hasPermission(settings.messages().statusPermission())) {
            sendMessage(sender, settings.messages().noPermissionMessage());
            return true;
        }

        if (Bukkit.getWorlds().isEmpty()) {
            sendMessage(sender, "<red>No loaded worlds were found.");
            return true;
        }

        sendMessage(sender, "<gold><bold>Loaded worlds");
        for (World world : Bukkit.getWorlds()) {
            sendMessage(sender, "<gray>" + world.getName() + ":</gray> <white>" + getWorldMinY(world) + " to " + getWorldMaxYInclusive(world));
        }
        return true;
    }

    private String describeRestriction(String name, RestrictionRule rule) {
        return "<gray>" + name + ":</gray> <white>enabled=" + rule.enabled()
            + ", maxY=" + rule.vanillaMaxY()
            + ", place=" + rule.blockPlace()
            + ", break=" + rule.blockBreak()
            + ", buckets=" + rule.buckets() + "</white>";
    }

    private void sendHelp(CommandSender sender, String label) {
        sendMessage(sender, "<gold><bold>WorldHeightLimit");
        sendMessage(sender, "<yellow>/" + label + " reload <gray>- reload config and rewrite datapack files if needed");
        sendMessage(sender, "<yellow>/" + label + " status <gray>- show current settings");
        sendMessage(sender, "<yellow>/" + label + " debug <gray>- show detailed world height diagnostics");
        sendMessage(sender, "<yellow>/" + label + " worlds <gray>- list loaded world height ranges");
        sendMessage(sender, "<yellow>/" + label + " fill <x1> <y1> <z1> <x2> <y2> <z2> <block> <gray>- fill an area using the plugin");
        sendMessage(sender, "<yellow>/" + label + " platform <radius> <block> [y] <gray>- create a square platform around you");
        sendMessage(sender, "<yellow>/" + label + " help <gray>- show this help");
    }

    private PluginSettings reloadPluginSettings(CompatibilityProfile compatibilityProfile) {
        PluginMessages messages = loadMessages();
        AdminTools adminTools = loadAdminTools();
        LoggingSettings loggingSettings = loadLoggingSettings();

        int targetMaxY = getConfig().getInt("target-max-y", 2031);
        if (targetMaxY < 319) {
            throw new IllegalArgumentException("target-max-y must be at least 319.");
        }

        DimensionSettings overworld = loadDimensionSettings("overworld", targetMaxY, true);
        DimensionSettings nether = loadDimensionSettings("nether", targetMaxY, false);
        DimensionSettings end = loadDimensionSettings("end", targetMaxY, false);

        RestrictionSettings restrictions = loadRestrictions();
        lastDeniedNoticeAt.clear();

        return new PluginSettings(targetMaxY, overworld, nether, end, restrictions, messages, adminTools, loggingSettings, compatibilityProfile);
    }

    private PluginMessages loadMessages() {
        String bypassPermission = getConfig().getString("bypass-permission", DEFAULT_BYPASS_PERMISSION);
        String reloadPermission = getConfig().getString("reload-permission", DEFAULT_RELOAD_PERMISSION);
        String statusPermission = getConfig().getString("status-permission", DEFAULT_STATUS_PERMISSION);
        String fillPermission = getConfig().getString("fill-permission", DEFAULT_FILL_PERMISSION);

        String deniedBuild = getConfig().getString("messages.denied-build", "<red>Only admins can build above the normal height limit.");
        String deniedBreak = getConfig().getString("messages.denied-break", "<red>Only admins can break blocks above the normal height limit.");
        String noPermission = getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command.");
        String reloadSuccess = getConfig().getString("messages.reload-success", "<green>WorldHeightLimit config reloaded.");
        String reloadNoDatapackChanges = getConfig().getString("messages.reload-no-datapack-changes", "<yellow>Player restrictions were updated. World height changes still need a full restart.");
        String restartRequired = getConfig().getString("messages.restart-required", "<yellow>Datapack files changed. Fully restart the server to apply world height changes.");
        String fillSuccess = getConfig().getString("messages.fill-success", "<green>Filled <white><count></white> blocks.");
        String platformSuccess = getConfig().getString("messages.platform-success", "<green>Platform created with <white><count></white> blocks.");
        String fillTooLarge = getConfig().getString("messages.fill-too-large", "<red>That area is too large. Limit: <white><limit></white> blocks.");
        String invalidBlock = getConfig().getString("messages.invalid-block", "<red>Unknown or unsupported block: <white><input></white>");
        String playersOnly = getConfig().getString("messages.players-only", "<red>This command can only be used by a player.");
        String invalidNumber = getConfig().getString("messages.invalid-number", "<red>Invalid number: <white><input></white>");

        NotificationMode mode = NotificationMode.fromString(getConfig().getString("messages.notification-mode", "CHAT"));
        long cooldownMillis = getConfig().getLong("messages.notification-cooldown-millis", 1500L);
        if (cooldownMillis < 0L) {
            throw new IllegalArgumentException("messages.notification-cooldown-millis must be 0 or greater.");
        }

        return new PluginMessages(
            bypassPermission,
            reloadPermission,
            statusPermission,
            fillPermission,
            deniedBuild,
            deniedBreak,
            noPermission,
            reloadSuccess,
            reloadNoDatapackChanges,
            restartRequired,
            fillSuccess,
            platformSuccess,
            fillTooLarge,
            invalidBlock,
            playersOnly,
            invalidNumber,
            mode,
            cooldownMillis
        );
    }

    private AdminTools loadAdminTools() {
        ConfigurationSection section = getConfig().getConfigurationSection("admin-tools");
        if (section == null) {
            throw new IllegalArgumentException("Missing admin-tools section.");
        }

        int maxFillBlocks = section.getInt("max-fill-blocks", 200000);
        if (maxFillBlocks <= 0) {
            throw new IllegalArgumentException("admin-tools.max-fill-blocks must be positive.");
        }

        return new AdminTools(maxFillBlocks);
    }

    private LoggingSettings loadLoggingSettings() {
        ConfigurationSection section = getConfig().getConfigurationSection("logging");
        if (section == null) {
            throw new IllegalArgumentException("Missing logging section.");
        }

        boolean suppressHeightmapWarnings = section.getBoolean("suppress-heightmap-warnings", true);
        return new LoggingSettings(suppressHeightmapWarnings);
    }

    private RestrictionSettings loadRestrictions() {
        ConfigurationSection root = getConfig().getConfigurationSection("build-restrictions");
        if (root == null) {
            throw new IllegalArgumentException("Missing build-restrictions section.");
        }

        return new RestrictionSettings(
            loadRestrictionRule(root, "overworld", 319),
            loadRestrictionRule(root, "nether", 255),
            loadRestrictionRule(root, "end", 255)
        );
    }

    private RestrictionRule loadRestrictionRule(ConfigurationSection root, String key, int defaultVanillaMaxY) {
        ConfigurationSection section = root.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("Missing build-restrictions." + key + " section.");
        }

        boolean enabled = section.getBoolean("enabled", true);
        int vanillaMaxY = section.getInt("vanilla-max-y", defaultVanillaMaxY);
        boolean blockPlace = section.getBoolean("block-place", true);
        boolean blockBreak = section.getBoolean("block-break", true);
        boolean buckets = section.getBoolean("buckets", true);

        return new RestrictionRule(enabled, vanillaMaxY, blockPlace, blockBreak, buckets);
    }

    private DimensionSettings loadDimensionSettings(String key, int targetMaxY, boolean supportsCloudHeight) {
        ConfigurationSection section = getConfig().getConfigurationSection("dimensions." + key);
        if (section == null) {
            throw new IllegalArgumentException("Missing dimensions." + key + " section.");
        }

        boolean enabled = section.getBoolean("enabled", true);
        int minY = section.getInt("min-y");
        int requestedLogicalHeight = section.getInt("logical-height");
        int requestedHeight = targetMaxY - minY + 1;
        int height = alignToSectionHeight(requestedHeight);
        int logicalHeight = Math.min(requestedLogicalHeight, height);

        validateMultipleOf16("dimensions." + key + ".min-y", minY);
        validatePositive("dimensions." + key + ".logical-height", requestedLogicalHeight);
        if (height != requestedHeight) {
            getLogger().warning("Adjusted " + key + " datapack height from " + requestedHeight + " to " + height + " to match 16-block world sections.");
        }
        if (logicalHeight != requestedLogicalHeight) {
            getLogger().warning("Clamped " + key + " logical-height from " + requestedLogicalHeight + " to " + logicalHeight + ".");
        }

        Double cloudHeight = supportsCloudHeight && section.contains("cloud-height")
            ? section.getDouble("cloud-height")
            : null;

        return new DimensionSettings(enabled, minY, height, logicalHeight, cloudHeight, minY + height - 1);
    }

    private int alignToSectionHeight(int requestedHeight) {
        int remainder = requestedHeight % 16;
        if (remainder == 0) {
            return requestedHeight;
        }
        return requestedHeight + (16 - remainder);
    }

    private void validateMultipleOf16(String name, int value) {
        if (value % 16 != 0) {
            throw new IllegalArgumentException(name + " must be divisible by 16, but was " + value + ".");
        }
    }

    private void validatePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, but was " + value + ".");
        }
    }

    private boolean canBuildAt(Player player, World world, int y) {
        RestrictionRule rule = settings.restrictions().forWorld(world);
        return !rule.enabled()
            || y <= rule.vanillaMaxY()
            || (player.hasPermission(settings.messages().bypassPermission()) && y <= settings.targetMaxY());
    }

    private void denyIfNeeded(Player player, World world, int y, DenyReason denyReason) {
        if (canBuildAt(player, world, y)) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastDeniedNoticeAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < settings.messages().notificationCooldownMillis()) {
            return;
        }
        lastDeniedNoticeAt.put(player.getUniqueId(), now);

        String rawMessage = denyReason == DenyReason.BREAK
            ? settings.messages().deniedBreakMessage()
            : settings.messages().deniedBuildMessage();
        String message = formatMessage(rawMessage);

        switch (settings.messages().notificationMode()) {
            case OFF -> {
            }
            case CHAT -> player.sendMessage(message);
            case ACTION_BAR -> sendActionBar(player, message);
            case TITLE -> player.sendTitle("", message, 0, 40, 10);
            case BOTH -> {
                player.sendMessage(message);
                sendActionBar(player, message);
            }
        }
    }

    private void sendMessage(CommandSender sender, String miniMessageText) {
        sender.sendMessage(formatMessage(miniMessageText));
    }

    private void sendActionBar(Player player, String message) {
        try {
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = Enum.valueOf(chatMessageTypeClass.asSubclass(Enum.class), "ACTION_BAR");
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object component = textComponentClass.getConstructor(String.class).newInstance(message);
            Object components = Array.newInstance(baseComponentClass, 1);
            Array.set(components, 0, component);
            Method sendMessage = spigot.getClass().getMethod("sendMessage", chatMessageTypeClass, components.getClass());
            sendMessage.invoke(spigot, actionBar, components);
        } catch (ReflectiveOperationException | LinkageError ex) {
            player.sendMessage(message);
        }
    }

    private String formatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        String formatted = message
            .replace("<black>", "\u00a70")
            .replace("<dark_blue>", "\u00a71")
            .replace("<dark_green>", "\u00a72")
            .replace("<dark_aqua>", "\u00a73")
            .replace("<dark_red>", "\u00a74")
            .replace("<dark_purple>", "\u00a75")
            .replace("<gold>", "\u00a76")
            .replace("<gray>", "\u00a77")
            .replace("<dark_gray>", "\u00a78")
            .replace("<blue>", "\u00a79")
            .replace("<green>", "\u00a7a")
            .replace("<aqua>", "\u00a7b")
            .replace("<red>", "\u00a7c")
            .replace("<light_purple>", "\u00a7d")
            .replace("<yellow>", "\u00a7e")
            .replace("<white>", "\u00a7f")
            .replace("<bold>", "\u00a7l")
            .replace("<italic>", "\u00a7o")
            .replace("<underlined>", "\u00a7n")
            .replace("<underline>", "\u00a7n")
            .replace("<strikethrough>", "\u00a7m")
            .replace("<obfuscated>", "\u00a7k")
            .replace("<reset>", "\u00a7r")
            .replace("</black>", "\u00a7r")
            .replace("</dark_blue>", "\u00a7r")
            .replace("</dark_green>", "\u00a7r")
            .replace("</dark_aqua>", "\u00a7r")
            .replace("</dark_red>", "\u00a7r")
            .replace("</dark_purple>", "\u00a7r")
            .replace("</gold>", "\u00a7r")
            .replace("</gray>", "\u00a7r")
            .replace("</dark_gray>", "\u00a7r")
            .replace("</blue>", "\u00a7r")
            .replace("</green>", "\u00a7r")
            .replace("</aqua>", "\u00a7r")
            .replace("</red>", "\u00a7r")
            .replace("</light_purple>", "\u00a7r")
            .replace("</yellow>", "\u00a7r")
            .replace("</white>", "\u00a7r")
            .replace("</bold>", "\u00a7r")
            .replace("</italic>", "\u00a7r")
            .replace("</underlined>", "\u00a7r")
            .replace("</underline>", "\u00a7r")
            .replace("</strikethrough>", "\u00a7r")
            .replace("</obfuscated>", "\u00a7r");
        return formatted.replaceAll("<[^>]+>", "");
    }

    private void installOptionalLogFilters() {
        if (!settings.loggingSettings().suppressHeightmapWarnings() || heightmapWarningFilterInstalled) {
            return;
        }

        try {
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
            Object context = logManagerClass.getMethod("getContext", boolean.class).invoke(null, false);
            Method getRootLogger = context.getClass().getMethod("getRootLogger");
            Object rootLogger = getRootLogger.invoke(context);

            Class<?> filterClass = Class.forName("org.apache.logging.log4j.core.Filter");
            @SuppressWarnings("unchecked")
            Class<Enum> resultClass = (Class<Enum>) Class.forName("org.apache.logging.log4j.core.Filter$Result");
            Object deny = Enum.valueOf(resultClass, "DENY");
            Object neutral = Enum.valueOf(resultClass, "NEUTRAL");

            Class<?> regexFilterClass = Class.forName("org.apache.logging.log4j.core.filter.RegexFilter");
            Method createFilter = regexFilterClass.getMethod("createFilter", String.class, String[].class, Boolean.class, resultClass, resultClass);
            Object filter = createFilter.invoke(null, ".*Ignoring heightmap data for chunk.*", null, Boolean.FALSE, deny, neutral);

            Method addFilter = rootLogger.getClass().getMethod("addFilter", filterClass);
            addFilter.invoke(rootLogger, filter);

            Method updateLoggers = context.getClass().getMethod("updateLoggers");
            updateLoggers.invoke(context);

            heightmapWarningFilterInstalled = true;
            getLogger().info("Installed filter for heightmap warning spam.");
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("Could not install heightmap warning filter automatically: " + ex.getMessage());
        }
    }

    private boolean handleFill(CommandSender sender, String[] args) {
        if (!sender.hasPermission(settings.messages().fillPermission())) {
            sendMessage(sender, settings.messages().noPermissionMessage());
            return true;
        }
        if (args.length != 8) {
            sendMessage(sender, "<yellow>Usage: /worldheightlimit fill <x1> <y1> <z1> <x2> <y2> <z2> <block>");
            return true;
        }

        World world = resolveWorld(sender);
        if (world == null) {
            sendMessage(sender, settings.messages().playersOnlyMessage());
            return true;
        }

        Integer x1 = parseInt(sender, args[1]);
        Integer y1 = parseInt(sender, args[2]);
        Integer z1 = parseInt(sender, args[3]);
        Integer x2 = parseInt(sender, args[4]);
        Integer y2 = parseInt(sender, args[5]);
        Integer z2 = parseInt(sender, args[6]);
        if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) {
            return true;
        }

        BlockData blockData = parseBlockData(sender, args[7]);
        if (blockData == null) {
            return true;
        }

        FillSelection selection = FillSelection.of(x1, y1, z1, x2, y2, z2);
        if (selection.volume() > settings.adminTools().maxFillBlocks()) {
            sendMessage(sender, settings.messages().fillTooLargeMessage()
                .replace("<limit>", String.valueOf(settings.adminTools().maxFillBlocks())));
            return true;
        }
        if (selection.maxY() > settings.targetMaxY()) {
            sendMessage(sender, "<red>Selection exceeds target-max-y <white>" + settings.targetMaxY() + "</white>.");
            return true;
        }

        long changed = fillArea(world, selection, blockData);
        sendMessage(sender, settings.messages().fillSuccessMessage().replace("<count>", String.valueOf(changed)));
        return true;
    }

    private boolean handlePlatform(CommandSender sender, String[] args) {
        if (!sender.hasPermission(settings.messages().fillPermission())) {
            sendMessage(sender, settings.messages().noPermissionMessage());
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendMessage(sender, settings.messages().playersOnlyMessage());
            return true;
        }
        if (args.length != 3 && args.length != 4) {
            sendMessage(sender, "<yellow>Usage: /worldheightlimit platform <radius> <block> [y]");
            return true;
        }

        Integer radius = parseInt(sender, args[1]);
        if (radius == null) {
            return true;
        }
        if (radius < 0) {
            sendMessage(sender, settings.messages().invalidNumberMessage().replace("<input>", args[1]));
            return true;
        }

        BlockData blockData = parseBlockData(sender, args[2]);
        if (blockData == null) {
            return true;
        }

        int y = args.length == 4 ? Objects.requireNonNullElse(parseInt(sender, args[3]), Integer.MIN_VALUE) : player.getLocation().getBlockY() - 1;
        if (y == Integer.MIN_VALUE) {
            return true;
        }

        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        FillSelection selection = FillSelection.of(x - radius, y, z - radius, x + radius, y, z + radius);
        if (selection.volume() > settings.adminTools().maxFillBlocks()) {
            sendMessage(sender, settings.messages().fillTooLargeMessage()
                .replace("<limit>", String.valueOf(settings.adminTools().maxFillBlocks())));
            return true;
        }
        if (selection.maxY() > settings.targetMaxY()) {
            sendMessage(sender, "<red>Platform exceeds target-max-y <white>" + settings.targetMaxY() + "</white>.");
            return true;
        }

        long changed = fillArea(player.getWorld(), selection, blockData);
        sendMessage(sender, settings.messages().platformSuccessMessage().replace("<count>", String.valueOf(changed)));
        return true;
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private String getServerDisplayName() {
        return Bukkit.getName() + " " + resolveMinecraftVersion();
    }

    private String getDimensionName(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "overworld";
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            default -> world.getEnvironment().name().toLowerCase(Locale.ROOT);
        };
    }

    private int getWorldMinY(World world) {
        try {
            Object value = world.getClass().getMethod("getMinHeight").invoke(world);
            if (value instanceof Integer minY) {
                return minY;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0;
    }

    private int getWorldMaxYInclusive(World world) {
        return world.getMaxHeight() - 1;
    }

    private boolean isDatapackActive(World world) {
        Path saveRoot = findSaveRoot(world.getWorldFolder().toPath());
        return Files.isDirectory(saveRoot.resolve("datapacks").resolve(PACK_NAME));
    }

    private String resolveMinecraftVersion() {
        try {
            Method method = Bukkit.class.getMethod("getMinecraftVersion");
            Object value = method.invoke(null);
            if (value instanceof String version && !version.isBlank()) {
                return version;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        String bukkitVersion = Bukkit.getBukkitVersion();
        int dashIndex = bukkitVersion.indexOf('-');
        return dashIndex >= 0 ? bukkitVersion.substring(0, dashIndex) : bukkitVersion;
    }

    private Integer parseInt(CommandSender sender, String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            sendMessage(sender, settings.messages().invalidNumberMessage().replace("<input>", input));
            return null;
        }
    }

    private BlockData parseBlockData(CommandSender sender, String input) {
        Material material = Material.matchMaterial(input);
        if (material == null || !material.isBlock()) {
            sendMessage(sender, settings.messages().invalidBlockMessage().replace("<input>", input));
            return null;
        }
        return material.createBlockData();
    }

    private long fillArea(World world, FillSelection selection, BlockData blockData) {
        long changed = 0L;
        for (int x = selection.minX(); x <= selection.maxX(); x++) {
            for (int y = selection.minY(); y <= selection.maxY(); y++) {
                for (int z = selection.minZ(); z <= selection.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getBlockData().matches(blockData)) {
                        continue;
                    }
                    block.setBlockData(blockData, false);
                    changed++;
                }
            }
        }
        return changed;
    }

    private boolean applyDatapackChanges(PluginSettings settings, boolean logNoChangesMessage) {
        Set<Path> saveRoots = discoverSaveRoots();
        if (saveRoots.isEmpty()) {
            getLogger().warning("No world save folders were found. Nothing to update.");
            return false;
        }

        boolean changedAny = false;
        for (Path saveRoot : saveRoots) {
            try {
                if (installDatapack(saveRoot, settings)) {
                    changedAny = true;
                    getLogger().info("Updated datapack in " + saveRoot);
                } else if (logNoChangesMessage) {
                    getLogger().info("Datapack is already up to date in " + saveRoot);
                }
            } catch (IOException ex) {
                getLogger().log(Level.SEVERE, "Failed to write datapack into " + saveRoot, ex);
            }
        }

        if (changedAny) {
            getLogger().warning("Build height files were created or changed. Fully restart the server to apply them.");
        } else if (logNoChangesMessage) {
            getLogger().info("No changes were needed. If you just installed the plugin, a normal server restart is still required.");
        }
        return changedAny;
    }

    private Set<Path> discoverSaveRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        for (World world : Bukkit.getWorlds()) {
            Path root = findSaveRoot(world.getWorldFolder().toPath());
            roots.add(root);
        }
        return roots;
    }

    private Path findSaveRoot(Path worldFolder) {
        Path current = worldFolder.toAbsolutePath().normalize();
        for (int i = 0; i < 4 && current != null; i++) {
            if (Files.exists(current.resolve("level.dat"))) {
                return current;
            }
            current = current.getParent();
        }
        return worldFolder.toAbsolutePath().normalize();
    }

    private boolean installDatapack(Path saveRoot, PluginSettings settings) throws IOException {
        Path packRoot = saveRoot.resolve("datapacks").resolve(PACK_NAME);
        Path dimensionTypeRoot = packRoot.resolve("data").resolve("minecraft").resolve("dimension_type");

        List<FileWrite> writes = new ArrayList<>();
        writes.add(new FileWrite(packRoot.resolve("pack.mcmeta"), settings.compatibilityProfile().packMcmetaJson()));

        if (settings.overworld().enabled()) {
            writes.add(new FileWrite(dimensionTypeRoot.resolve("overworld.json"), settings.compatibilityProfile().buildOverworldJson(settings.overworld())));
        }
        if (settings.nether().enabled()) {
            writes.add(new FileWrite(dimensionTypeRoot.resolve("the_nether.json"), settings.compatibilityProfile().buildNetherJson(settings.nether())));
        }
        if (settings.end().enabled()) {
            writes.add(new FileWrite(dimensionTypeRoot.resolve("the_end.json"), settings.compatibilityProfile().buildEndJson(settings.end())));
        }

        boolean changed = false;
        for (FileWrite write : writes) {
            Files.createDirectories(write.path().getParent());
            String existing = Files.exists(write.path()) ? Files.readString(write.path(), StandardCharsets.UTF_8) : null;
            if (!write.content().equals(existing)) {
                Files.writeString(write.path(), write.content(), StandardCharsets.UTF_8);
                changed = true;
            }
        }
        return changed;
    }

    private enum DenyReason {
        BUILD,
        BREAK
    }

    private enum NotificationMode {
        OFF,
        CHAT,
        ACTION_BAR,
        TITLE,
        BOTH;

        private static NotificationMode fromString(String value) {
            try {
                return NotificationMode.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("messages.notification-mode must be one of OFF, CHAT, ACTION_BAR, TITLE, BOTH.");
            }
        }
    }

    private record PluginSettings(
        int targetMaxY,
        DimensionSettings overworld,
        DimensionSettings nether,
        DimensionSettings end,
        RestrictionSettings restrictions,
        PluginMessages messages,
        AdminTools adminTools,
        LoggingSettings loggingSettings,
        CompatibilityProfile compatibilityProfile
    ) {
        private DimensionSettings dimensionForWorld(World world) {
            return switch (world.getEnvironment()) {
                case NORMAL -> overworld;
                case NETHER -> nether;
                case THE_END -> end;
                default -> overworld;
            };
        }
    }

    private record RestrictionSettings(
        RestrictionRule overworld,
        RestrictionRule nether,
        RestrictionRule end
    ) {
        private RestrictionRule forWorld(World world) {
            return switch (world.getEnvironment()) {
                case NORMAL -> overworld;
                case NETHER -> nether;
                case THE_END -> end;
                default -> new RestrictionRule(false, Integer.MAX_VALUE, false, false, false);
            };
        }
    }

    private record RestrictionRule(
        boolean enabled,
        int vanillaMaxY,
        boolean blockPlace,
        boolean blockBreak,
        boolean buckets
    ) {
    }

    private record PluginMessages(
        String bypassPermission,
        String reloadPermission,
        String statusPermission,
        String fillPermission,
        String deniedBuildMessage,
        String deniedBreakMessage,
        String noPermissionMessage,
        String reloadSuccessMessage,
        String reloadNoDatapackChangesMessage,
        String restartRequiredMessage,
        String fillSuccessMessage,
        String platformSuccessMessage,
        String fillTooLargeMessage,
        String invalidBlockMessage,
        String playersOnlyMessage,
        String invalidNumberMessage,
        NotificationMode notificationMode,
        long notificationCooldownMillis
    ) {
    }

    private record AdminTools(int maxFillBlocks) {
    }

    private record LoggingSettings(boolean suppressHeightmapWarnings) {
    }

    private record DimensionSettings(boolean enabled, int minY, int height, int logicalHeight, Double cloudHeight, int effectiveMaxY) {
    }

    private record FileWrite(Path path, String content) {
    }

    private record FillSelection(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static FillSelection of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new FillSelection(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
            );
        }

        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private record McVersion(int major, int minor, int patch) implements Comparable<McVersion> {
        private static McVersion parse(String value) {
            String[] rawParts = value.split("\\.");
            int major = rawParts.length > 0 ? parsePart(rawParts[0]) : 0;
            int minor = rawParts.length > 1 ? parsePart(rawParts[1]) : 0;
            int patch = rawParts.length > 2 ? parsePart(rawParts[2]) : 0;
            return new McVersion(major, minor, patch);
        }

        private static int parsePart(String raw) {
            String digits = raw.replaceAll("[^0-9].*$", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }

        @Override
        public int compareTo(McVersion other) {
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

    private enum CompatibilityProfile {
        V1_19_TO_1_19_3("1.19-1.19.3", McVersion.parse("1.19.0"), McVersion.parse("1.19.3"), 10, false, false),
        V1_19_4("1.19.4", McVersion.parse("1.19.4"), McVersion.parse("1.19.4"), 12, false, false);

        private final String displayName;
        private final McVersion minVersion;
        private final McVersion maxVersion;
        private final int dataPackFormat;
        private final boolean usesMinMaxPackFormat;
        private final boolean usesModernDimensionSchema;

        CompatibilityProfile(String displayName, McVersion minVersion, McVersion maxVersion, int dataPackFormat, boolean usesMinMaxPackFormat, boolean usesModernDimensionSchema) {
            this.displayName = displayName;
            this.minVersion = minVersion;
            this.maxVersion = maxVersion;
            this.dataPackFormat = dataPackFormat;
            this.usesMinMaxPackFormat = usesMinMaxPackFormat;
            this.usesModernDimensionSchema = usesModernDimensionSchema;
        }

        private static CompatibilityProfile detect(String versionText) {
            McVersion version = McVersion.parse(versionText);
            for (CompatibilityProfile profile : values()) {
                if (version.compareTo(profile.minVersion) >= 0 && version.compareTo(profile.maxVersion) <= 0) {
                    return profile;
                }
            }
            return null;
        }

        private String displayName() {
            return displayName;
        }

        private String packMcmetaJson() {
            return """
                {
                  "pack": {
                    "description": "Generated by WorldHeightLimit",
                    "pack_format": %d
                  }
                }
                """.formatted(dataPackFormat);
        }

        private String buildOverworldJson(DimensionSettings settings) {
            String cloudHeight = settings.cloudHeight() == null
                ? "192.33"
                : String.format(Locale.US, "%.2f", settings.cloudHeight());
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
                  "monster_spawn_light_level": {
                    "type": "minecraft:uniform",
                    "value": {
                      "max_inclusive": 7,
                      "min_inclusive": 0
                    }
                  },
                  "natural": true,
                  "piglin_safe": false,
                  "respawn_anchor_works": false,
                  "ultrawarm": false
                }
                """.formatted(settings.height(), settings.logicalHeight(), settings.minY());
        }

        private String buildNetherJson(DimensionSettings settings) {
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
                """.formatted(settings.height(), settings.logicalHeight(), settings.minY());
        }

        private String buildEndJson(DimensionSettings settings) {
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
                  "monster_spawn_light_level": {
                    "type": "minecraft:uniform",
                    "value": {
                      "max_inclusive": 7,
                      "min_inclusive": 0
                    }
                  },
                  "natural": false,
                  "piglin_safe": false,
                  "respawn_anchor_works": false,
                  "ultrawarm": false
                }
                """.formatted(settings.height(), settings.logicalHeight(), settings.minY());
        }
    }
}
