package org.bxwbb.qcpet.event;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.pet.NmsPetAiController;

public class PetProtectionListener implements Listener {

    private final QcPet plugin;

    public PetProtectionListener(QcPet plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        Pet pet = plugin.getPetManger().getPetByEntity(damager);
        if (pet == null && !NmsPetAiController.isTaggedPetMob(damager)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPetDamage(EntityDamageEvent event) {
        Pet pet = plugin.getPetManger().getPetByEntity(event.getEntity());
        if (pet == null && !NmsPetAiController.isTaggedPetMob(event.getEntity())) {
            return;
        }
        if (pet != null && event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            plugin.getPetManger().handlePetDamaged(pet, damageByEntityEvent.getDamager());
        } else if (pet != null) {
            plugin.getPetManger().handlePetDamaged(pet, null);
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetTarget(EntityTargetEvent event) {
        Pet pet = plugin.getPetManger().getPetByEntity(event.getEntity());
        if (pet == null && !NmsPetAiController.isTaggedPetMob(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        event.setTarget(null);
    }

    @EventHandler
    public void onPetPrimeExplosion(ExplosionPrimeEvent event) {
        if (!isPetMob(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        event.setRadius(0F);
        event.setFire(false);
    }

    @EventHandler
    public void onPetExplode(EntityExplodeEvent event) {
        if (!isPetMob(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        event.blockList().clear();
        event.setYield(0F);
    }

    @EventHandler
    public void onPetProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Entity shooter)) {
            return;
        }
        if (!isPetMob(shooter)) {
            return;
        }
        event.setCancelled(true);
        projectile.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPetPickupItem(EntityPickupItemEvent event) {
        if (!isPetMob(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPetEntityBlockForm(EntityBlockFormEvent event) {
        if (plugin.getPetManger().getPetByEntity(event.getEntity()) == null) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPetSpawnByPet(CreatureSpawnEvent event) {
        if (plugin.getPetManger().isInternalPetSpawnInProgress()) {
            return;
        }
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPELL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.EXPLOSION
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.getWorld() == null) {
            return;
        }
        for (Entity nearby : entity.getNearbyEntities(8, 8, 8)) {
            if (plugin.getPetManger().getPetByEntity(nearby) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPetMount(EntityMountEvent event) {
        Pet pet = plugin.getPetManger().getPetByEntity(event.getMount());
        if (pet == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (pet.owner() == null || !pet.owner().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean isPetMob(Entity entity) {
        return plugin.getPetManger().getPetByEntity(entity) != null
                || NmsPetAiController.isTaggedPetMob(entity);
    }
}
