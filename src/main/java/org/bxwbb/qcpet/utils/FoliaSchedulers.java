package org.bxwbb.qcpet.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bxwbb.qcpet.QcPet;

public final class FoliaSchedulers {

    private FoliaSchedulers() {
    }

    public static TaskHandle runTimer(Plugin plugin, long delayTicks, long periodTicks, Runnable task) {
        if (QcPet.isFolia()) {
            ScheduledTask scheduledTask = plugin.getServer().getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, ignored -> task.run(), delayTicks, periodTicks);
            return scheduledTask::cancel;
        }
        BukkitTask scheduledTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, delayTicks, periodTicks);
        return scheduledTask::cancel;
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (QcPet.isFolia()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (QcPet.isFolia()) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, ignored -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runPlayer(Plugin plugin, Player player, Runnable task) {
        if (player == null) {
            return;
        }
        if (QcPet.isFolia()) {
            player.getScheduler().run(plugin, ignored -> task.run(), null);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runPlayerLater(Plugin plugin, Player player, long delayTicks, Runnable task) {
        if (player == null) {
            return;
        }
        if (delayTicks <= 0L) {
            runPlayer(plugin, player, task);
            return;
        }
        if (QcPet.isFolia()) {
            player.getScheduler().runDelayed(plugin, ignored -> task.run(), null, delayTicks);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runEntity(Plugin plugin, Entity entity, Runnable task) {
        if (entity == null) {
            return;
        }
        if (QcPet.isFolia()) {
            entity.getScheduler().run(plugin, ignored -> task.run(), null);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runEntityLater(Plugin plugin, Entity entity, long delayTicks, Runnable task) {
        if (entity == null) {
            return;
        }
        if (delayTicks <= 0L) {
            runEntity(plugin, entity, task);
            return;
        }
        if (QcPet.isFolia()) {
            entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, delayTicks);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void teleport(Entity entity, Location location) {
        if (QcPet.isFolia()) {
            entity.teleportAsync(location);
            return;
        }
        entity.teleport(location);
    }

    @FunctionalInterface
    public interface TaskHandle {
        void cancel();
    }
}
