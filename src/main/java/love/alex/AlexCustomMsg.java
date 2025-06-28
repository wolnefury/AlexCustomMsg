package love.alex;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AlexCustomMsg extends JavaPlugin implements TabExecutor, TabCompleter {

    private File ignoreFile;
    private FileConfiguration ignoreConfig;
    private boolean debugMode;

    private final Map<UUID, UUID> lastMessaged = new HashMap<>();
    private String uuidResolutionMode;
    private final Map<UUID, Set<UUID>> ignoreMap = new HashMap<>();

    public void sendMessage(Player player, String path, String... placeholders) {
        String singleMessage = getConfig().getString(path);
        if (singleMessage != null) {
            String processedMessage = replacePlaceholders(player, singleMessage, placeholders);
            player.sendMessage(colorize(processedMessage));
            return;
        }

        List<String> messageList = getConfig().getStringList(path);
        if (!messageList.isEmpty()) {
            for (String line : messageList) {
                String processedLine = replacePlaceholders(player, line, placeholders);
                player.sendMessage(colorize(processedLine));
            }
            return;
        }

        debug.warning("Missing message path: " + path);
        player.sendMessage(colorize("&cMissing message path: " + path + "&c. Contact an admin and send them this message."));
    }

    private String replacePlaceholders(Player context, String message, String... values) {
        for (int i = 0; i < values.length; i++) {
            String placeholder = "{" + i + "}";
            message = message.replace(placeholder, values[i]);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            message = PlaceholderAPI.setPlaceholders(context, message);
        }

        return message;
    }

    private @NotNull Component colorize(String message) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.debugMode = getConfig().getBoolean("debug", false);
        this.uuidResolutionMode = getConfig().getString("settings.uuid-resolution", "hybrid").toLowerCase();

        if (!uuidResolutionMode.equals("hybrid") && !uuidResolutionMode.equals("online-only")) {
            getLogger().warning("Unknown settings.uuid-resolution: '" + uuidResolutionMode + "'. Defaulting to 'hybrid'.");
            uuidResolutionMode = "hybrid";
        }

        ignoreFile = new File(getDataFolder(), "ignored.yml");
        if (!ignoreFile.exists()) {
            saveResource("ignored.yml", false); // Creates an empty one if needed
        }
        ignoreConfig = YamlConfiguration.loadConfiguration(ignoreFile);

        for (String uuidStr : ignoreConfig.getKeys(false)) {
            UUID playerUUID = UUID.fromString(uuidStr);
            List<String> ignoredList = ignoreConfig.getStringList(uuidStr);
            Set<UUID> ignoredUUIDs = new HashSet<>();
            for (String id : ignoredList) {
                ignoredUUIDs.add(UUID.fromString(id));
            }
            ignoreMap.put(playerUUID, ignoredUUIDs);
        }

        Objects.requireNonNull(getCommand("msg")).setExecutor(this);
        Objects.requireNonNull(getCommand("r")).setExecutor(this);
        Objects.requireNonNull(getCommand("msg")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("r")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("acmsgreload")).setExecutor(this);
        Objects.requireNonNull(getCommand("acmsgreload")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("ignore")).setExecutor(this);
        Objects.requireNonNull(getCommand("unignore")).setExecutor(this);
        Objects.requireNonNull(getCommand("ignorelist")).setExecutor(this);
        Objects.requireNonNull(getCommand("ignore")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("unignore")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("ignorelist")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("forceunignore")).setExecutor(this);
        Objects.requireNonNull(getCommand("forceunignore")).setTabCompleter(this);
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("Enabling AlexCustomMSG...");
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("Plugin version: " + (getPluginMeta().getVersion()));
        getLogger().info("Plugin API version: " + (getPluginMeta().getAPIVersion()));
        getLogger().info("Authors: " + (getPluginMeta().getAuthors()));
        getLogger().info("Website: " + (getPluginMeta().getWebsite()));
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("Searching for dependency plugin PlaceholderAPI...");
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("Successfully hooked to PlaceholderAPI!");
        } else {
            getLogger().warning("Couldn't find dependency plugin PlaceholderAPI, ignoring it...");
        }
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("AlexCustomMSG successfully enabled!");
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
    }

    @Override
    public void onDisable() {
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        getLogger().info("Disabling AlexCustomMSG...");
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");

        ignoreConfig = new YamlConfiguration();
        for (UUID playerUUID : ignoreMap.keySet()) {
            Set<UUID> ignored = ignoreMap.get(playerUUID);
            List<String> ignoredList = ignored.stream().map(UUID::toString).toList();
            ignoreConfig.set(playerUUID.toString(), ignoredList);
        }

        try {
            ignoreConfig.save(ignoreFile);
            getLogger().info("Ignore list saved successfully!");
        } catch (IOException e) {
            getLogger().severe("Failed to save ignore list: " + e.getMessage());
        }
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");


        getLogger().info("Successfully disabled AlexCustomMSG");
        getLogger().info("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) return false;

        switch (command.getName().toLowerCase()) {
            case "ignore" -> {
                if (!sender.hasPermission("acmsg.ignore")) {
                    sendMessage(player, "error.no-permission");
                    return true;
                }
                if (args.length < 1) {
                    sendMessage(player, "usage.ignore"); // Show proper usage if no argument
                    return true;
                }

                String targetName = args[0];

                UUID targetUUID = resolveUUID(targetName);
                if (targetUUID == null) {
                    sendMessage(player, "error.uuid-not-found", targetName);
                    return true;
                }


                if (player.getUniqueId().equals(targetUUID)) {
                    sendMessage(player, "error.cant-ignore-yourself");
                    return true;
                }

                // Already ignoring?
                if (ignoreMap.getOrDefault(player.getUniqueId(), new HashSet<>()).contains(targetUUID)) {
                    sendMessage(player, "error.already-ignoring", targetName);
                    return true;
                }

                ignoreMap.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(targetUUID);
                sendMessage(player, "ignore.now-ignoring", targetName);

                ignoreConfig = new YamlConfiguration();
                for (UUID playerUUID : ignoreMap.keySet()) {
                    Set<UUID> ignored = ignoreMap.get(playerUUID);
                    List<String> ignoredList = ignored.stream().map(UUID::toString).toList();
                    ignoreConfig.set(playerUUID.toString(), ignoredList);
                }

                try {
                    ignoreConfig.save(ignoreFile);
                } catch (IOException e) {
                    debug.severe("Failed to save ignore list: " + e.getMessage());
                }
                return true;
            }

            case "unignore" -> {
                if (!sender.hasPermission("acmsg.unignore")) {
                    sendMessage(player, "error.no-permission");
                    return true;
                }
                if (args.length < 1) {
                    sendMessage(player, "usage.unignore");
                    return true;
                }

                String targetName = args[0];

                UUID targetUUID = resolveUUID(targetName);
                if (targetUUID == null) {
                    sendMessage(player, "error.uuid-not-found", targetName);
                    return true;
                }

                Set<UUID> ignored = ignoreMap.get(player.getUniqueId());
                if (ignored != null && ignored.remove(targetUUID)) {
                    sendMessage(player, "ignore.no-longer-ignoring", targetName);

                    ignoreConfig = new YamlConfiguration();
                    for (UUID playerUUID : ignoreMap.keySet()) {
                        Set<UUID> ignoredSave = ignoreMap.get(playerUUID);
                        List<String> ignoredList = ignoredSave.stream().map(UUID::toString).toList();
                        ignoreConfig.set(playerUUID.toString(), ignoredList);
                    }

                    try {
                        ignoreConfig.save(ignoreFile);
                    } catch (IOException e) {
                        debug.severe("Failed to save ignore list: " + e.getMessage());
                    }

                } else {
                    sendMessage(player, "error.werent-ignoring", targetName);
                }
                return true;
            }


            case "ignorelist" -> {
                if (!sender.hasPermission("acmsg.ignorelist")) {
                    sendMessage(player, "error.no-permission");
                    return true;
                }

                Set<UUID> ignored = ignoreMap.getOrDefault(player.getUniqueId(), Collections.emptySet());
                if (ignored.isEmpty()) {
                    sendMessage(player, "ignore.not-ignoring-anyone");
                    return true;
                }

                // Collect and sort names alphabetically
                List<String> names = ignored.stream()
                        .map(id -> Bukkit.getOfflinePlayer(id))
                        .map(p -> p.getName() != null ? p.getName() : "???")
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

                sendMessage(player, "ignore.ignorelist-header");
                for (String name : names) {
                    sendMessage(player, "ignore.ignorelist-entry", name);
                }

                return true;
            }
            case "forceunignore" -> {
                if (!sender.hasPermission("acmsg.forceunignore")) {
                    sendMessage(player, "error.no-permission");
                    return true;
                }

                if (args.length < 1) {
                    sendMessage(player, "usage.forceunignore");
                    return true;
                }

                try {
                    UUID targetUUID = UUID.fromString(args[0]);
                    boolean removed = false;

                    for (Set<UUID> ignoredSet : ignoreMap.values()) {
                        if (ignoredSet.remove(targetUUID)) {
                            removed = true;
                        }
                    }

                    if (removed) {
                        ignoreConfig = new YamlConfiguration();
                        for (UUID playerUUID : ignoreMap.keySet()) {
                            Set<UUID> ignoredSave = ignoreMap.get(playerUUID);
                            List<String> ignoredList = ignoredSave.stream().map(UUID::toString).toList();
                            ignoreConfig.set(playerUUID.toString(), ignoredList);
                        }

                        try {
                            ignoreConfig.save(ignoreFile);
                            sendMessage(player, "ignore.forceunignore-success", args[0]);
                        } catch (IOException e) {
                            debug.severe("Failed to save ignore list: " + e.getMessage());
                            sendMessage(player, "reload.ignored-reload-fail");
                        }

                    } else {
                        sendMessage(player, "ignore.forceunignore-notfound", args[0]);
                    }

                } catch (IllegalArgumentException e) {
                    sendMessage(player, "error.invalid-uuid", args[0]);
                }

                return true;
            }

        }

        if (command.getName().equalsIgnoreCase("msg")) {
            if (!sender.hasPermission("acmsg.msg")) {
                sendMessage(player, "error.no-permission");
                return true;
            }

            if (args.length < 2) {
                sendMessage(player, "usage.msg");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sendMessage(player, "error.not-online");
                return true;
            }

            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            sendPrivateMessage(player, target, message);
        }

        if (command.getName().equalsIgnoreCase("r")) {
            if (!sender.hasPermission("acmsg.reply")) {
                sendMessage(player, "error.no-permission");
                return true;
            }

            UUID last = lastMessaged.get(player.getUniqueId());
            if (last == null) {
                sendMessage(player, "error.no-one-to-reply-to");
                return true;
            }

            Player target = Bukkit.getPlayer(last);
            if (target == null || !target.isOnline()) {
                sendMessage(player, "error.no-longer-online");
                return true;
            }

            if (args.length < 1) {
                sendMessage(player, "usage.reply");
                return true;
            }

            String message = String.join(" ", args);
            sendPrivateMessage(player, target, message);
        }

        if (command.getName().equalsIgnoreCase("acmsgreload")) {
            if (!sender.hasPermission("acmsg.reload")) {
                sendMessage(player, "error.no-permission");
                return true;
            }

            reloadConfig();
            this.debugMode = getConfig().getBoolean("debug", false);
            this.uuidResolutionMode = getConfig().getString("settings.uuid-resolution", "hybrid").toLowerCase();
            debug.info("Config file saved successfully!");
            sendMessage(player, "reload.config-reload-success");
            ignoreConfig = new YamlConfiguration();
            for (UUID playerUUID : ignoreMap.keySet()) {
                Set<UUID> ignored = ignoreMap.get(playerUUID);
                List<String> ignoredList = ignored.stream().map(UUID::toString).toList();
                ignoreConfig.set(playerUUID.toString(), ignoredList);
            }

            try {
                ignoreConfig.save(ignoreFile);
                debug.info("Ignore list saved successfully!");
                sendMessage(player, "reload.ignored-reload-success");
            } catch (IOException e) {
                debug.severe("Failed to save ignore list: " + e.getMessage());
                sendMessage(player, "reload.ignored-reload-fail");
            }
            if (debugMode) {
                debug.info("Debug mode: ENABLED");
                sendMessage(player, "debug-enabled");
            } else {
                sendMessage(player, "debug-disabled");
            }
            sendMessage(player, "reload.reload-success");
        }

        return true;
    }

    private void sendPrivateMessage(Player sender, Player receiver, String message) {
        lastMessaged.put(sender.getUniqueId(), receiver.getUniqueId());
        lastMessaged.put(receiver.getUniqueId(), sender.getUniqueId());

        Set<UUID> ignored = ignoreMap.get(receiver.getUniqueId());
        if (ignored != null && ignored.contains(sender.getUniqueId())) {
            sendMessage(sender, "ignore.is-ignoring-you");
            return;
        }

        String receiverFormat = getConfig().getString("format.receiver-format");
        String hoverText = getConfig().getString("format.hover-text", "&7Click to reply to {0}");
        String clickCommand = getConfig().getString("format.click-template", "/msg {0}");

        String receiverMessage = replacePlaceholders(receiver, receiverFormat, sender.getName(), message);
        String processedHover = replacePlaceholders(receiver, hoverText, sender.getName());
        String processedClickCommand = clickCommand.replace("{0}", sender.getName());

        Component receiverMsg = colorize(receiverMessage)
                .hoverEvent(HoverEvent.showText(colorize(processedHover)))
                .clickEvent(ClickEvent.suggestCommand(processedClickCommand));

        sendMessage(sender, "format.sender-format", receiver.getName(), message);
        receiver.sendMessage(receiverMsg);

        // Configurable sound
        String soundName = getConfig().getString("sound.name", "BLOCK_NOTE_BLOCK_BELL");
        float volume = (float) getConfig().getDouble("sound.volume", 1.0);
        float pitch = (float) getConfig().getDouble("sound.pitch", 1.2);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase()); // Ok, shut the fuck up; I know it's deprecated?
            receiver.playSound(receiver.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            debug.warning("Invalid sound name in config: " + soundName);
        }
    }


    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("msg") && args.length == 1) {

            if (!sender.hasPermission("acmsg.msg") || !(sender instanceof Player)) {
                return completions;
            }
            for (Player onlinePlayer : getServer().getOnlinePlayers()) {
                completions.add(onlinePlayer.getName());
            }
            return completions;
        }

        if (command.getName().equalsIgnoreCase("ignore") && args.length == 1) {
            if (!sender.hasPermission("acmsg.ignore") || !(sender instanceof Player)) {
                return completions;
            }
            for (Player onlinePlayer : getServer().getOnlinePlayers()) {
                completions.add(onlinePlayer.getName());
            }
            return completions;
        }

        if (command.getName().equalsIgnoreCase("unignore") && args.length == 1) {
            if (!sender.hasPermission("acmsg.unignore") || !(sender instanceof Player player)) {
                return completions;
            }

            Set<UUID> ignored = ignoreMap.getOrDefault(player.getUniqueId(), Collections.emptySet());
            if (ignored.isEmpty()) {
                return completions;
            }

            // Collect names of ignored players (as long as their names are known)
            for (UUID id : ignored) {
                OfflinePlayer ignoredPlayer = Bukkit.getOfflinePlayer(id);
                if (ignoredPlayer.getName() != null) {
                    completions.add(ignoredPlayer.getName());
                }
            }

            return completions;
        }
        return completions;
    }

    private UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) return offline.getUniqueId();

        if (uuidResolutionMode.equals("online-only")) {
            debug.info("Couldn't resolve UUID for '" + name + "' in online-only mode!");
            return null;
        }

        // If hybrid mode: fallback denied
        debug.info("Player '" + name + "' has never joined before - ignoring request.");
        return null;
    }

    private final DebugLogger debug = new DebugLogger();

    private class DebugLogger {
        void info(String message) {
            if (debugMode) getLogger().info("[Debug] " + message);
        }

        void warning(String message) {
            if (debugMode) getLogger().warning("[Debug] " + message);
        }

        void severe(String message) {
            if (debugMode) getLogger().severe("[Debug] " + message);
        }
    }

}