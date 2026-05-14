package net.duart.virtualstorage.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Messages {
    private static FileConfiguration config;
    private static final Map<String, String> cache = new HashMap<>();

    public static void init(FileConfiguration config) {
        Messages.config = config;
        reload();
    }

    public static void reload() {
        cache.clear();

        if (config != null && config.isConfigurationSection("texts")) {
            org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("texts");
            if (section != null) {
                Set<String> keys = section.getKeys(false);
                for (String key : keys) {
                    String raw = config.getString("texts." + key);
                    if (raw != null) {
                        cache.put(key, ChatColor.translateAlternateColorCodes('&', raw));
                    }
                }
            }
        }
    }

    public static String get(String path, Object... replacements) {
        String raw = cache.get(path);

        if (raw == null) {
            raw = ChatColor.translateAlternateColorCodes('&', getDefault(path));
            cache.put(path, raw);
        }

        for (int i = 0; i < replacements.length; i += 2) {
            raw = raw.replace(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return raw;
    }

    private static String getDefault(String path) {
        return switch (path) {
            case "title" -> "&9◆ Backpack - Page %page% of %maxpages% ◆";
            case "prevArrow" -> "&c<< Previous page";
            case "nextArrow" -> "&aNext page >>";
            case "waitToOpen" -> "&eWait a second to open your backpack again.";
            case "noPermission" -> "&cYou do not have permissions to open backpacks.";
            case "noCommandPermission" -> "&cYou do not have permissions to use this command.";
            case "noPermissionOther" -> "&cThis player does not have any permissions.";
            case "backpackInUse" -> "&cThat player's backpack is currently in use. Try again in a moment.";
            case "itemsRecovered" -> "&aYour previous stored items were recovered to your backpack!";
            case "itemsOverflowed" -> "&eOh no! You lost permission to access some pages in your backpack, so some items were safely stored until you can access them again.";
            case "reloadDone" -> "&aLanguage file reloaded.";
            default -> "";
        };
    }
}
