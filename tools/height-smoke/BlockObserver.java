package ru.sorfin.heightprobe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockObserver extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getY() >= 1900) {
            getLogger().info("HEIGHT_PROBE_PLACE player=" + event.getPlayer().getName()
                + " y=" + event.getBlockPlaced().getY()
                + " cancelled=" + event.isCancelled()
                + " bypass=" + event.getPlayer().hasPermission("highbuildlimit.bypass"));
        }
    }
}
