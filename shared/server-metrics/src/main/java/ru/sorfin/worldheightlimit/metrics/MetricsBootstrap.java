package ru.sorfin.worldheightlimit.metrics;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class MetricsBootstrap {
    private MetricsBootstrap() {
    }

    public static void start(JavaPlugin plugin) {
        int pluginId = plugin.getConfig().getInt("metrics.bstats-plugin-id", 0);
        if (pluginId <= 0) {
            return;
        }
        try {
            new Metrics(plugin, pluginId);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not start bStats metrics", exception);
        }
    }
}
