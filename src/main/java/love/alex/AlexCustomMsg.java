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

        getLogger().warning("Missing message path: " + path);
        player.sendMessage(colorize("&cMissing message path: " + path));
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.uuidResolutionMode = getConfig().getString("uuid-resolution.mode", "hybrid").toLowerCase();

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

    private @NotNull Component colorize(String message) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) return false;

        switch (command.getName().toLowerCase()) {
            case "ignore" -> {
                if (!sender.hasPermission("acmsg.ignore")) {
                    sendMessage(player, "no-permission");
                    return true;
                }
                if (args.length < 1) {
                    sendMessage(player, "ignore"); // Show proper usage if no argument
                    return true;
                }

                String targetName = args[0];

                UUID targetUUID = resolveUUID(targetName);
                if (targetUUID == null) {
                    sendMessage(player, "could-not-find-uuid", targetName);
                    return true;
                }


                if (player.getUniqueId().equals(targetUUID)) {
                    sendMessage(player, "cant-ignore-yourself");
                    return true;
                }

                // Already ignoring?
                if (ignoreMap.getOrDefault(player.getUniqueId(), new HashSet<>()).contains(targetUUID)) {
                    sendMessage(player, "already-ignoring", targetName);
                    return true;
                }

                ignoreMap.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(targetUUID);
                sendMessage(player, "now-ignoring", targetName);

                ignoreConfig = new YamlConfiguration();
                for (UUID playerUUID : ignoreMap.keySet()) {
                    Set<UUID> ignored = ignoreMap.get(playerUUID);
                    List<String> ignoredList = ignored.stream().map(UUID::toString).toList();
                    ignoreConfig.set(playerUUID.toString(), ignoredList);
                }

                try {
                    ignoreConfig.save(ignoreFile);
                } catch (IOException e) {
                    getLogger().severe("Failed to save ignore list: " + e.getMessage());
                }
                return true;
            }

            case "unignore" -> {
                if (!sender.hasPermission("acmsg.unignore")) {
                    sendMessage(player, "no-permission");
                    return true;
                }
                if (args.length < 1) {
                    sendMessage(player, "usage.unignore");
                    return true;
                }

                String targetName = args[0];

                UUID targetUUID = resolveUUID(targetName);
                if (targetUUID == null) {
                    sendMessage(player, "could-not-find-uuid", targetName);
                    return true;
                }

                Set<UUID> ignored = ignoreMap.get(player.getUniqueId());
                if (ignored != null && ignored.remove(targetUUID)) {
                    sendMessage(player, "no-longer-ignoring", targetName);

                    ignoreConfig = new YamlConfiguration();
                    for (UUID playerUUID : ignoreMap.keySet()) {
                        Set<UUID> ignoredSave = ignoreMap.get(playerUUID);
                        List<String> ignoredList = ignoredSave.stream().map(UUID::toString).toList();
                        ignoreConfig.set(playerUUID.toString(), ignoredList);
                    }

                    try {
                        ignoreConfig.save(ignoreFile);
                    } catch (IOException e) {
                        getLogger().severe("Failed to save ignore list: " + e.getMessage());
                    }

                } else {
                    sendMessage(player, "werent-ignoring", targetName);
                }
                return true;
            }


            case "ignorelist" -> {
                if (!sender.hasPermission("acmsg.ignorelist")) {
                    sendMessage(player, "no-permission");
                    return true;
                }

                Set<UUID> ignored = ignoreMap.getOrDefault(player.getUniqueId(), Collections.emptySet());
                if (ignored.isEmpty()) {
                    sendMessage(player, "not-ignoring-anyone");
                    return true;
                }

                // Collect and sort names alphabetically
                List<String> names = ignored.stream()
                        .map(id -> Bukkit.getOfflinePlayer(id))
                        .map(p -> p.getName() != null ? p.getName() : "???")
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

                sendMessage(player, "ignorelist-header");
                for (String name : names) {
                    sendMessage(player, "ignorelist-entry", name);
                }

                return true;
            }
        }

        if (command.getName().equalsIgnoreCase("msg")) {
            if (!sender.hasPermission("acmsg.msg")) {
                sendMessage(player, "no-permission");
                return true;
            }

            if (args.length < 2) {
                sendMessage(player, "msg");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !target.isOnline()) {
                sendMessage(player, "not-online");
                return true;
            }

            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            sendPrivateMessage(player, target, message);
        }

        if (command.getName().equalsIgnoreCase("r")) {
            if (!sender.hasPermission("acmsg.reply")) {
                sendMessage(player, "no-permission");
                return true;
            }

            UUID last = lastMessaged.get(player.getUniqueId());
            if (last == null) {
                sendMessage(player, "no-one-to-reply-to");
                return true;
            }

            Player target = Bukkit.getPlayer(last);
            if (target == null || !target.isOnline()) {
                sendMessage(player, "no-longer-online");
                return true;
            }

            if (args.length < 1) {
                sendMessage(player, "r");
                return true;
            }

            String message = String.join(" ", args);
            sendPrivateMessage(player, target, message);
        }

        if (command.getName().equalsIgnoreCase("acmsgreload")) {
            if (!sender.hasPermission("acmsg.reload")) {
                sendMessage(player, "no-permission");
                return true;
            }

            reloadConfig();
            this.uuidResolutionMode = getConfig().getString("uuid-resolution.mode", "hybrid").toLowerCase();
            sendMessage(player, "config-reload-success");
            ignoreConfig = new YamlConfiguration();
            for (UUID playerUUID : ignoreMap.keySet()) {
                Set<UUID> ignored = ignoreMap.get(playerUUID);
                List<String> ignoredList = ignored.stream().map(UUID::toString).toList();
                ignoreConfig.set(playerUUID.toString(), ignoredList);
            }

            try {
                ignoreConfig.save(ignoreFile);
                getLogger().info("Ignore list saved successfully!");
                sendMessage(player, "ignored-reload-success");
            } catch (IOException e) {
                getLogger().severe("Failed to save ignore list: " + e.getMessage());
                sendMessage(player, "ignored-reload-fail");
            }
            sendMessage(player, "reload-success");
        }

        return true;
    }

    private void sendPrivateMessage(Player sender, Player receiver, String message) {
        lastMessaged.put(sender.getUniqueId(), receiver.getUniqueId());
        lastMessaged.put(receiver.getUniqueId(), sender.getUniqueId());

        Set<UUID> ignored = ignoreMap.get(receiver.getUniqueId());
        if (ignored != null && ignored.contains(sender.getUniqueId())) {
            sendMessage(sender, "is-ignoring-you");
            return;
        }

        String receiverFormat = getConfig().getString("receiver-format");
        String hoverText = getConfig().getString("hover-text", "&7Click to reply to {0}");
        String clickCommand = getConfig().getString("click-template", "/msg {0}");

        String receiverMessage = replacePlaceholders(receiver, receiverFormat, sender.getName(), message);
        String processedHover = replacePlaceholders(receiver, hoverText, sender.getName());
        String processedClickCommand = clickCommand.replace("{0}", sender.getName());

        Component receiverMsg = colorize(receiverMessage)
                .hoverEvent(HoverEvent.showText(colorize(processedHover)))
                .clickEvent(ClickEvent.suggestCommand(processedClickCommand));

        sendMessage(sender, "sender-format", receiver.getName(), message);
        receiver.sendMessage(receiverMsg);

        // Configurable sound
        String soundName = getConfig().getString("sound.name", "BLOCK_NOTE_BLOCK_BELL");
        float volume = (float) getConfig().getDouble("sound.volume", 1.0);
        float pitch = (float) getConfig().getDouble("sound.pitch", 1.2);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase()); // Ok, shut the fuck up; I know it's deprecated?
            receiver.playSound(receiver.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid sound name in config: " + soundName);
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
        switch (uuidResolutionMode) {
            case "online-only" -> {
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    return online.getUniqueId();
                }

                OfflinePlayer known = Bukkit.getOfflinePlayer(name);
                if (known.hasPlayedBefore()) {
                    return known.getUniqueId();
                }

                getLogger().warning("Couldn't resolve UUID for '" + name + "' in online-only mode!");
                return null;
            }

            case "offline-only" -> {
                return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            }

            case "hybrid" -> {
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    return online.getUniqueId();
                }

                OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
                if (offline.hasPlayedBefore()) {
                    return offline.getUniqueId();
                }

                getLogger().warning("Player '" + name + "' has never joined before — using fallback offline UUID.");
                return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            }

            default -> {
                getLogger().warning("Unknown uuid-resolution.mode: " + uuidResolutionMode + ". Using offline-only.");
                return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

}