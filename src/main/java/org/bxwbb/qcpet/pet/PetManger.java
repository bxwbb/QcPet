package org.bxwbb.qcpet.pet;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attributable;
import org.bukkit.boss.BossBar;
import org.bukkit.Location;
import org.bukkit.Input;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Flying;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Llama;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.math.MathExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PetManger {

    private static final String LAST_BATH_TIME_KEY = "lastBathTime";
    private static final String LAST_FEED_TIME_KEY = "lastFeedTime";
    private static final String LAST_PASSIVE_EXP_MINUTE_KEY = "lastPassiveExpMinute";
    private static final String TUTORIAL_SHOWN_KEY = "tutorialShown";
    private static final String LAST_BATH_REMINDER_TIME_KEY = "lastBathReminderTime";
    private static final String LAST_FEED_REMINDER_TIME_KEY = "lastFeedReminderTime";
    private static final String ENTITY_STATE_KEY = "entityState";
    private static final String MUTED_KEY = "muted";
    private static final String BLIND_BOX_REVEAL_PENDING_KEY = "blindBoxRevealPending";
    private static final double TELEPORT_DISTANCE_SQUARED = 144.0D;
    private static final double FOLLOW_STOP_DISTANCE_SQUARED = 4.0D;
    private static final double FOLLOW_SLOT_REACHED_DISTANCE_SQUARED = 0.36D;
    private static final double FOLLOW_SLOT_RADIUS = 1.75D;
    private static final double FLYING_FOLLOW_HEIGHT = 3.0D;
    private static final double GROUND_SLOT_TELEPORT_DISTANCE_SQUARED = 16.0D;
    private static final long BLIND_BOX_REVEAL_DURATION_TICKS = 40L;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private final QcPet plugin;
    public final Map<UUID, List<Pet>> pets = new HashMap<>();
    private final Map<UUID, List<Long>> temporarilyHiddenPets = new HashMap<>();
    private final Map<UUID, BlindBoxRevealInteraction> blindBoxRevealInteractions = new HashMap<>();
    private final Map<UUID, AutoTravelState> autoTravelStates = new HashMap<>();
    private final BukkitTask followTask;
    private int internalSpawnDepth;

    public PetManger(QcPet plugin) {
        this.plugin = plugin;
        this.followTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVisiblePets, 1L, 1L);
        registerQcLevelExpBoostProvider();
    }

    private void registerQcLevelExpBoostProvider() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("QcLevel")) {
            return;
        }

        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("cn.qcrealm.qclevel.api.QcLevelAPI", true, classLoader);
            Class<?> providerClass = Class.forName("cn.qcrealm.qclevel.api.ExpBoostProvider", true, classLoader);
            Method registerMethod = apiClass.getMethod("registerExpBoostProvider", String.class, providerClass);

            InvocationHandler handler = (proxy, method, args) -> {
                if ("getExpBoost".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Player player) {
                    double ret = 1D;
                    for (Pet pet : pets.getOrDefault(player.getUniqueId(), List.of())) {
                        ret *= pet.times();
                    }
                    return (ret - 1D) > 0D ? ret - 1D : 0D;
                }
                return defaultValue(method.getReturnType());
            };
            Object provider = Proxy.newProxyInstance(classLoader, new Class<?>[]{providerClass}, handler);
            registerMethod.invoke(null, "qc-pet", provider);
            plugin.getLogger().info("已注册 QcLevel 经验加成提供器。");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("检测到 QcLevel，但其 API 与当前版本不兼容，已跳过经验加成集成。");
            plugin.getLogger().fine("QcLevel 集成失败: " + exception.getMessage());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        return null;
    }

    public Pet givePet(Player player, PetConfig petConfig) {
        return givePet(player, petConfig, 0);
    }

    public Pet givePet(Player player, PetConfig petConfig, int initialLevel) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (petConfig == null) {
            throw new IllegalArgumentException("petConfig cannot be null");
        }
        Pet pet = createPetForGive(player, petConfig, initialLevel);
        addPet(player, pet);
        executePetEvent(player, pet, "on-give");
        return pet;
    }

    public CompletableFuture<Pet> givePetAsync(Player player, PetConfig petConfig) {
        return givePetAsync(player, petConfig, 0);
    }

    public CompletableFuture<Pet> givePetAsync(Player player, PetConfig petConfig, int initialLevel) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (petConfig == null) {
            throw new IllegalArgumentException("petConfig cannot be null");
        }
        if (initialLevel < 0) {
            throw new IllegalArgumentException("initialLevel cannot be less than 0");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Pet pet = createPetForGive(player, petConfig, initialLevel);
                plugin.getPetUtil().savePet(pet);
                plugin.getPetUtil().bindPetToPlayer(player, pet.id());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    addPetToMemory(player, pet);
                    executePetEvent(player, pet, "on-give");
                    future.complete(pet);
                });
            } catch (Exception exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> future.completeExceptionally(exception));
            }
        });
        return future;
    }

    private Pet createPetForGive(Player player, PetConfig petConfig, int initialLevel) {
        if (initialLevel < 0) {
            throw new IllegalArgumentException("initialLevel cannot be less than 0");
        }
        return petConfig.toPet(plugin.getPetUtil().nextPetId(), player, initialLevel);
    }

    public void addPet(Player player, Pet pet) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (pet == null) {
            throw new IllegalArgumentException("pet cannot be null");
        }
        addPetToMemory(player, pet);
        plugin.getPetUtil().savePet(pet);
        plugin.getPetUtil().bindPetToPlayer(player, pet.id());
    }

    public boolean removePet(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        findPet(player, petId).ifPresent(this::removeEntity);
        clearTemporaryHidden(player.getUniqueId(), petId);
        boolean removed = plugin.getPetUtil().deletePet(player, petId);
        if (removed) {
            getPets(player).removeIf(pet -> pet.id() == petId);
        }
        return removed;
    }

    public CompletableFuture<Boolean> removePetAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean removed = plugin.getPetUtil().deletePet(player, petId);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (removed) {
                        findPet(player, petId).ifPresent(this::removeEntity);
                        clearTemporaryHidden(player.getUniqueId(), petId);
                        getPets(player).removeIf(pet -> pet.id() == petId);
                    }
                    future.complete(removed);
                });
            } catch (Exception exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> future.completeExceptionally(exception));
            }
        });
        return future;
    }

    public CompletableFuture<Pet> renamePetAsync(Player player, long petId, String newName) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        String normalizedName = normalizePetName(newName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("newName cannot be blank");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(null);
                    return;
                }
                PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
                String resolvedName = resolveRenamedTemplate(pet, petConfig, normalizedName);

                Pet updated = new Pet(
                        pet.id(),
                        resolvedName,
                        pet.type(),
                        pet.level(),
                        pet.exp(),
                        pet.times(),
                        pet.data(),
                        pet.show(),
                        pet.owner(),
                        pet.entity()
                );
                if (updated.entity() != null && updated.entity().isValid()) {
                    updated = applyEntityState(player, updated, updated.entity());
                }
                replacePet(player, updated);
                executePetEvent(player, updated, "on-rename");
                Pet resultPet = updated;
                plugin.getPetUtil().savePetAsync(resultPet)
                        .whenComplete((ignored, throwable) -> completePetFuture(future, resultPet, throwable));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Pet> bathPetAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(null);
                    return;
                }

                Pet cleaned = markBathTime(
                        plugin.getPetProgressService().addExperience(pet, getBathRewardExp(pet)),
                        System.currentTimeMillis()
                );
                if (cleaned.entity() != null && cleaned.entity().isValid()) {
                    cleaned = applyEntityState(player, cleaned, cleaned.entity());
                    playBathEffect(cleaned.entity());
                    playLoveEffect(cleaned.entity(), 20L);
                }
                replacePet(player, cleaned);
                executePetEvent(player, cleaned, "on-bath");
                Pet resultPet = cleaned;
                plugin.getPetUtil().savePetAsync(resultPet)
                        .whenComplete((ignored, throwable) -> completePetFuture(future, resultPet, throwable));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Pet> feedPetAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(null);
                    return;
                }

                Pet fed = markFeedTime(
                        plugin.getPetProgressService().addExperience(pet, getFeedRewardExp(pet)),
                        System.currentTimeMillis()
                );
                if (fed.entity() != null && fed.entity().isValid()) {
                    fed = applyEntityState(player, fed, fed.entity());
                    playFeedEffect(fed.entity());
                    playLoveEffect(fed.entity(), 16L);
                }
                replacePet(player, fed);
                executePetEvent(player, fed, "on-feed");
                Pet resultPet = fed;
                plugin.getPetUtil().savePetAsync(resultPet)
                        .whenComplete((ignored, throwable) -> completePetFuture(future, resultPet, throwable));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Pet> setPetMutedAsync(Player player, long petId, boolean muted) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(null);
                    return;
                }

                Pet updated = withDataValue(pet, MUTED_KEY, muted);
                if (updated.entity() != null && updated.entity().isValid()) {
                    updated = applyEntityState(player, updated, updated.entity());
                }
                replacePet(player, updated);
                Pet resultPet = updated;
                plugin.getPetUtil().savePetAsync(resultPet)
                        .whenComplete((ignored, throwable) -> completePetFuture(future, resultPet, throwable));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Pet> togglePetMutedAsync(Player player, long petId) {
        Pet pet = getPet(player, petId);
        if (pet == null) {
            return CompletableFuture.completedFuture(null);
        }
        return setPetMutedAsync(player, petId, !isPetMuted(pet));
    }

    public CompletableFuture<Pet> addPetExperienceAsync(Player player, long petId, int amount) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(null);
                    return;
                }
                Pet updated = plugin.getPetProgressService().addExperience(pet, amount);
                int previousLevel = pet.level();
                if (updated.entity() != null && updated.entity().isValid()) {
                    updated = applyEntityState(player, updated, updated.entity());
                }
                replacePet(player, updated);
                executePetEvent(player, updated, "on-add-exp");
                executeLevelChangeEvents(player, pet, updated, previousLevel);
                Pet resultPet = updated;
                plugin.getPetUtil().savePetAsync(resultPet)
                        .whenComplete((ignored, throwable) -> completePetFuture(future, resultPet, throwable));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Pet> addPetLevelsAsync(Player player, long petId, int amount) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(null);
                    return;
                }
                Pet updated = plugin.getPetProgressService().addLevels(pet, amount);
                int previousLevel = pet.level();
                if (updated.entity() != null && updated.entity().isValid()) {
                    updated = applyEntityState(player, updated, updated.entity());
                }
                replacePet(player, updated);
                executePetEvent(player, updated, "on-add-level");
                executeLevelChangeEvents(player, pet, updated, previousLevel);
                Pet resultPet = updated;
                plugin.getPetUtil().savePetAsync(resultPet)
                        .whenComplete((ignored, throwable) -> completePetFuture(future, resultPet, throwable));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public boolean showPet(Player player, long petId) {
        Pet pet = findPet(player, petId).orElse(null);
        if (pet == null) {
            return false;
        }
        clearTemporaryHidden(player.getUniqueId(), petId);
        if (!canShowAdditionalPet(player, petId)) {
            return false;
        }
        try {
            return showPetInternal(player, pet, true, true) != null;
        } catch (Exception exception) {
            handlePetSpawnFailure(player, pet, exception, true);
            return false;
        }
    }

    public CompletableFuture<Boolean> showPetAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                clearTemporaryHidden(player.getUniqueId(), petId);
                if (pet != null && !canShowAdditionalPet(player, petId)) {
                    future.complete(false);
                    return;
                }
                Pet updated;
                try {
                    updated = pet == null ? null : showPetInternal(player, pet, false, true);
                } catch (Exception exception) {
                    if (pet != null) {
                        handlePetSpawnFailure(player, pet, exception, false);
                    }
                    future.complete(false);
                    return;
                }
                if (updated == null) {
                    future.complete(false);
                    return;
                }
                plugin.getPetUtil().savePetAsync(updated)
                        .whenComplete((ignored, throwable) -> completeBooleanFuture(future, throwable, true));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public boolean hidePet(Player player, long petId) {
        Pet pet = findPet(player, petId).orElse(null);
        if (pet == null) {
            return false;
        }
        clearTemporaryHidden(player.getUniqueId(), petId);
        removeEntity(pet);
        Pet updated = copyPet(pet, false, null);
        replacePet(player, updated);
        executePetEvent(player, updated, "on-hide");
        plugin.getPetUtil().savePet(updated);
        return true;
    }

    public CompletableFuture<Boolean> hidePetAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null) {
                    future.complete(false);
                    return;
                }
                clearTemporaryHidden(player.getUniqueId(), petId);
                removeEntity(pet);
                Pet updated = copyPet(pet, false, null);
                replacePet(player, updated);
                executePetEvent(player, updated, "on-hide");
                plugin.getPetUtil().savePetAsync(updated)
                        .whenComplete((ignored, throwable) -> completeBooleanFuture(future, throwable, true));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> hidePetDisplayAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null || !pet.show()) {
                    future.complete(false);
                    return;
                }
                if (isTemporarilyHidden(player.getUniqueId(), petId)) {
                    future.complete(false);
                    return;
                }
                removeEntity(pet);
                replacePet(player, withEntity(pet, null));
                markTemporaryHidden(player.getUniqueId(), petId);
                future.complete(true);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> showPetDisplayAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet pet = findPet(player, petId).orElse(null);
                if (pet == null || !pet.show()) {
                    future.complete(false);
                    return;
                }
                if (!isTemporarilyHidden(player.getUniqueId(), petId)) {
                    future.complete(false);
                    return;
                }
                if (!canShowAdditionalPet(player, petId)) {
                    future.complete(false);
                    return;
                }
                clearTemporaryHidden(player.getUniqueId(), petId);
                try {
                    future.complete(showPetInternal(player, pet, false, false) != null);
                } catch (Exception exception) {
                    markTemporaryHidden(player.getUniqueId(), petId);
                    handlePetSpawnFailure(player, pet, exception, false);
                    future.complete(false);
                }
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public boolean selectPet(Player player, long petId) {
        if (findPet(player, petId).isEmpty()) {
            return false;
        }
        int maxDisplayCount = getMaxDisplayCount(player);
        if (maxDisplayCount <= 1) {
            for (Pet pet : new ArrayList<>(getPets(player))) {
                if (pet.id() != petId && pet.show()) {
                    hidePet(player, pet.id());
                }
            }
        }
        return showPet(player, petId);
    }

    public CompletableFuture<Boolean> selectPetAsync(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Pet selectedPet = findPet(player, petId).orElse(null);
                if (selectedPet == null) {
                    future.complete(false);
                    return;
                }

                List<Pet> changedPets = new ArrayList<>();
                int maxDisplayCount = getMaxDisplayCount(player);
                if (maxDisplayCount <= 1) {
                    for (Pet pet : new ArrayList<>(getPets(player))) {
                        if (pet.id() == petId || !pet.show()) {
                            continue;
                        }
                        removeEntity(pet);
                        Pet hidden = copyPet(pet, false, null);
                        replacePet(player, hidden);
                        changedPets.add(hidden);
                    }
                }

                Pet latestSelectedPet = findPet(player, petId).orElse(selectedPet);
                if (!canShowAdditionalPet(player, latestSelectedPet.id())) {
                    future.complete(false);
                    return;
                }
                Pet shown;
                try {
                    shown = showPetInternal(player, latestSelectedPet, false, true);
                } catch (Exception exception) {
                    handlePetSpawnFailure(player, latestSelectedPet, exception, false);
                    future.complete(false);
                    return;
                }
                if (shown == null) {
                    future.complete(false);
                    return;
                }
                changedPets.add(shown);
                persistPetsAsync(changedPets)
                        .whenComplete((ignored, throwable) -> completeBooleanFuture(future, throwable, true));
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    public List<Pet> getPets(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        return getPets(player.getUniqueId());
    }

    public List<Pet> getPets(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        return pets.computeIfAbsent(playerUuid, ignored -> new ArrayList<>());
    }

    public void loadPets(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        temporarilyHiddenPets.remove(player.getUniqueId());
        List<Pet> loadedPets = new ArrayList<>(plugin.getPetUtil().getPets(player));
        pets.put(player.getUniqueId(), loadedPets);
        for (Pet pet : new ArrayList<>(loadedPets)) {
            if (pet.show()) {
                Pet shownPet;
                try {
                    shownPet = showPetInternal(player, pet, false, true);
                } catch (Exception exception) {
                    handlePetSpawnFailure(player, pet, exception, false);
                    continue;
                }
                if (shownPet != null && shownPet.entity() != null) {
                    playLoveEffect(shownPet.entity(), 12L);
                }
                if (shownPet != null) {
                    executePetEvent(player, shownPet, "on-join-show");
                }
            }
        }
    }

    public CompletableFuture<Void> loadPetsAsync(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getPetUtil().getPetsAsync(player)
                .whenComplete((loadedPets, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        return;
                    }
                    if (!player.isOnline()) {
                        future.complete(null);
                        return;
                    }
                    List<Pet> petsToLoad = new ArrayList<>(loadedPets);
                    pets.put(player.getUniqueId(), petsToLoad);
                    for (Pet pet : new ArrayList<>(petsToLoad)) {
                        if (pet.show()) {
                            Pet shownPet;
                            try {
                                shownPet = showPetInternal(player, pet, false, true);
                            } catch (Exception exception) {
                                handlePetSpawnFailure(player, pet, exception, false);
                                continue;
                            }
                            if (shownPet != null && shownPet.entity() != null) {
                                playLoveEffect(shownPet.entity(), 12L);
                            }
                            if (shownPet != null) {
                                executePetEvent(player, shownPet, "on-join-show");
                            }
                        }
                    }
                    future.complete(null);
                });
        return future;
    }

    public void unloadPets(Player player) {
        if (player == null) {
            return;
        }
        List<Pet> playerPets = pets.remove(player.getUniqueId());
        if (playerPets != null) {
            playerPets.forEach(this::removeEntity);
        }
    }

    public void clear() {
        pets.values().forEach(playerPets -> playerPets.forEach(this::removeEntity));
        pets.clear();
        blindBoxRevealInteractions.clear();
        followTask.cancel();
    }

    public String getDisplayName(Pet pet, Player viewer) {
        if (shouldDisplayAsBlindBox(pet)) {
            return "???";
        }
        String name = pet.request(pet.name());
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig != null) {
            String modelId = petConfig.modelId();
            if (modelId != null && !modelId.isBlank()) {
                name = "@cet_" + modelId.trim() + "@" + name;
            }
        }
        if (viewer != null && plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            name = PlaceholderAPI.setPlaceholders(viewer, name);
        }
        if (needsBath(pet)) {
            name = applyStateDecoration(pet, name, "bathNeedPrefix", "bathNeedSuffix");
        }
        if (needsFeed(pet)) {
            name = applyStateDecoration(pet, name, "feedNeedPrefix", "feedNeedSuffix");
        }
        return name;
    }

    public Pet getPet(Player player, long petId) {
        return findPet(player, petId).orElse(null);
    }

    public Pet getPetByEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        for (List<Pet> playerPets : pets.values()) {
            for (Pet pet : playerPets) {
                Entity petEntity = pet.entity();
                if (petEntity != null && petEntity.getUniqueId().equals(entity.getUniqueId())) {
                    return pet;
                }
            }
        }
        return null;
    }

    public boolean handleBlindBoxRevealInteract(Player player, Entity clickedEntity) {
        if (player == null || clickedEntity == null) {
            return false;
        }
        BlindBoxRevealInteraction interaction = blindBoxRevealInteractions.get(clickedEntity.getUniqueId());
        if (interaction == null) {
            return false;
        }
        if (!interaction.ownerUuid().equals(player.getUniqueId())) {
            return true;
        }
        Pet pet = findPet(player, interaction.petId()).orElse(null);
        if (pet == null || !pet.show()) {
            cleanupBlindBoxRevealInteraction(interaction.ownerUuid(), interaction.petId());
            return true;
        }
        continueBlindBoxReveal(player, pet);
        return true;
    }

    public String normalizePetName(String input) {
        if (input == null) {
            return "";
        }

        String normalized = input.trim();
        String invalidPattern = plugin.getConfig().getString("pet.rename.invalid-pattern", "[\\r\\n\\t]");
        try {
            normalized = Pattern.compile(invalidPattern).matcher(normalized).replaceAll("");
        } catch (PatternSyntaxException ignored) {
        }
        if (!plugin.getConfig().getBoolean("pet.rename.allow-color-codes", true)) {
            normalized = normalized.replace("&", "");
        }
        return normalized.trim();
    }

    public boolean isBlindBoxPet(Pet pet) {
        return pet != null && pet.level() <= 0;
    }

    public boolean isAwaitingBlindBoxReveal(Pet pet) {
        return pet != null && getBooleanDataValue(pet, BLIND_BOX_REVEAL_PENDING_KEY);
    }

    public boolean shouldDisplayAsBlindBox(Pet pet) {
        return isBlindBoxPet(pet) || isAwaitingBlindBoxReveal(pet);
    }

    public boolean isPetRideable(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return petConfig == null || petConfig.rideable();
    }

    public boolean isPetMovable(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return petConfig == null || petConfig.movable();
    }

    public boolean canPetFloatOnWater(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return petConfig != null && petConfig.waterFloat();
    }

    public boolean canPetFly(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return petConfig != null && petConfig.flyable();
    }

    public boolean canPetDefendOwnerDuringAutoTravel(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return petConfig != null && petConfig.autoTravelDefendOwner();
    }

    public double getPetMovementMultiplier(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return Math.max(0.1D, petConfig == null ? 1.0D : petConfig.movementMultiplier());
    }

    public boolean scheduleAutoTravel(Player player, long petId, double x, double z) {
        if (player == null || findPet(player, petId).isEmpty()) {
            return false;
        }
        autoTravelStates.put(player.getUniqueId(), new AutoTravelState(petId, x, z));
        return true;
    }

    public void clearAutoTravel(Player player, long petId) {
        if (player == null) {
            return;
        }
        AutoTravelState state = autoTravelStates.get(player.getUniqueId());
        if (state != null && state.petId() == petId) {
            autoTravelStates.remove(player.getUniqueId());
        }
    }

    public boolean needsBath(Pet pet) {
        if (shouldDisplayAsBlindBox(pet)) {
            return false;
        }
        long intervalMillis = plugin.getConfig().getLong("pet.bath.interval-hours", 24L) * 60L * 60L * 1000L;
        return System.currentTimeMillis() - getLastBathTime(pet) >= intervalMillis;
    }

    public int getBathRewardExp(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return Math.max(0, petConfig == null ? 25 : petConfig.bathRewardExp());
    }

    public int getFeedRewardExp(Pet pet) {
        PetConfig petConfig = pet == null ? null : plugin.getPetConfigManger().pets.get(pet.type());
        return Math.max(0, petConfig == null ? 15 : petConfig.feedRewardExp());
    }

    public boolean needsFeed(Pet pet) {
        if (shouldDisplayAsBlindBox(pet)) {
            return false;
        }
        int minTimes = Math.max(1, plugin.getConfig().getInt("pet.feed.min-times-per-day", 3));
        int maxTimes = Math.max(minTimes, plugin.getConfig().getInt("pet.feed.max-times-per-day", 5));
        int feedTimesPerDay = minTimes + (int) (Math.abs(pet.id()) % (maxTimes - minTimes + 1));
        long intervalMillis = (24L * 60L * 60L * 1000L) / feedTimesPerDay;
        return System.currentTimeMillis() - getLastFeedTime(pet) >= intervalMillis;
    }

    private Optional<Pet> findPet(Player player, long petId) {
        return getPets(player).stream()
                .filter(pet -> pet.id() == petId)
                .findFirst();
    }

    private boolean canShowAdditionalPet(Player player, long targetPetId) {
        if (player == null || player.hasPermission("qcpet.bypass.limit")) {
            return true;
        }
        int maxDisplayCount = getMaxDisplayCount(player);
        if (maxDisplayCount < 1) {
            maxDisplayCount = 1;
        }
        long currentShownCount = getPets(player).stream()
                .filter(Pet::show)
                .filter(pet -> pet.id() != targetPetId)
                .count();
        return currentShownCount < maxDisplayCount;
    }

    private int getMaxDisplayCount(Player player) {
        int configuredLimit = plugin.getConfig().getInt("player.max-display-count", 1);
        return Math.max(1, configuredLimit);
    }

    private void replacePet(Player player, Pet updated) {
        List<Pet> playerPets = getPets(player);
        for (int index = 0; index < playerPets.size(); index++) {
            if (playerPets.get(index).id() == updated.id()) {
                playerPets.set(index, updated);
                return;
            }
        }
        playerPets.add(updated);
    }

    private void addPetToMemory(Player player, Pet pet) {
        UUID playerUuid = player.getUniqueId();
        List<Pet> playerPets = pets.computeIfAbsent(playerUuid, ignored -> new ArrayList<>());
        if (!(playerPets instanceof ArrayList<?>)) {
            playerPets = new ArrayList<>(playerPets);
            pets.put(playerUuid, playerPets);
        }
        playerPets.add(pet);
    }

    private void removeEntity(Pet pet) {
        cleanupBlindBoxRevealInteraction(pet == null || pet.owner() == null ? null : pet.owner().getUniqueId(), pet == null ? 0L : pet.id());
        clearAutoTravel(pet == null ? null : pet.owner(), pet == null ? 0L : pet.id());
        Entity entity = pet.entity();
        if (entity != null && !entity.isDead()) {
            if (entity instanceof Player playerEntity && playerEntity != pet.owner()) {
                if (NmsPlayerPetController.isAvailable()) {
                    NmsPlayerPetController.remove(playerEntity);
                } else {
                    entity.remove();
                }
                return;
            }
            entity.remove();
        }
    }

    private void tickVisiblePets() {
        for (Map.Entry<UUID, List<Pet>> entry : pets.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            for (Pet pet : new ArrayList<>(entry.getValue())) {
                if (!pet.show()) {
                    continue;
                }
                if (isTemporarilyHidden(entry.getKey(), pet.id())) {
                    if (plugin.getServer().getCurrentTick() % (20L * 60L) == 0L) {
                        applyPassiveExperience(player, pet);
                    }
                    continue;
                }
                Entity entity = pet.entity();
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    try {
                        showPetInternal(player, pet, false, false);
                    } catch (Exception exception) {
                        handlePetSpawnFailure(player, pet, exception, false);
                    }
                    continue;
                }
                syncBossEntityState(entity);
                Pet updatedPet = applyEntityState(player, pet, entity);
                if (updatedPet != pet) {
                    replacePet(player, updatedPet);
                    pet = updatedPet;
                    entity = updatedPet.entity();
                }
                followOwner(player, pet, entity);
                syncBlindBoxRevealInteraction(pet);
                Pet currentPet = executeTickEvent(player, pet);
                if (currentPet != null && currentPet != pet) {
                    pet = currentPet;
                    entity = currentPet.entity();
                    if (entity == null || !entity.isValid() || entity.isDead()) {
                        continue;
                    }
                }
                if (needsBath(pet)) {
                    spawnDirtyParticles(entity);
                }
                if (needsFeed(pet)) {
                    spawnHungryParticles(entity);
                }
                Pet remindedPet = maybeSendCareReminder(player, pet);
                if (remindedPet != pet) {
                    replacePet(player, remindedPet);
                }
            }
        }
    }

    private Pet applyEntityState(Player player, Pet pet, Entity entity) {
        Pet updatedPet = pet;
        entity.setInvulnerable(true);
        entity.setPersistent(false);
        applyEntityScale(updatedPet, entity);
        if (entity instanceof TextDisplay textDisplay) {
            entity.customName(null);
            entity.setCustomNameVisible(false);
            textDisplay.text(LEGACY_SERIALIZER.deserialize(isAwaitingBlindBoxReveal(pet) ? "&e&l点击我！" : "&l???"));
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setShadowed(false);
            textDisplay.setPersistent(false);
            textDisplay.setDefaultBackground(false);
            textDisplay.setInterpolationDelay(0);
            textDisplay.setInterpolationDuration(1);
            textDisplay.setTeleportDuration(1);
            return updatedPet;
        }
        entity.customName(LEGACY_SERIALIZER.deserialize(getDisplayName(updatedPet, player)));
        entity.setCustomNameVisible(true);
        if (entity instanceof ArmorStand armorStand) {
            armorStand.setVisible(false);
            armorStand.setMarker(true);
            armorStand.setSmall(true);
            armorStand.setGravity(false);
        }
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setCollidable(false);
        }
        syncBossEntityState(entity);
        if (entity instanceof Player playerEntity && playerEntity != player) {
            playerEntity.setInvulnerable(true);
            playerEntity.setCollidable(false);
            playerEntity.setSilent(true);
        }
        if (entity instanceof Mob mob) {
            NmsPetAiController.stripMobAi(mob);
            mob.setTarget(null);
        }
        if (entity instanceof Wolf wolf) {
            wolf.setAngry(false);
            wolf.setSitting(false);
        }
        if (entity instanceof Creeper creeper) {
            creeper.setPowered(false);
            creeper.setMaxFuseTicks(Integer.MAX_VALUE);
            creeper.setExplosionRadius(0);
        }
        applyPetDataToEntity(updatedPet, entity);
        return captureEntityStateIfNecessary(updatedPet, entity);
    }

    private void syncBossEntityState(Entity entity) {
        if (!(entity instanceof Boss boss)) {
            return;
        }
        hideBossBar(boss);
        if (entity instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
            wither.setGlowing(false);
        }
    }

    private void hideBossBar(Boss boss) {
        if (boss == null) {
            return;
        }
        BossBar bossBar = boss.getBossBar();
        if (bossBar == null) {
            return;
        }
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            bossBar.removePlayer(onlinePlayer);
        }
        bossBar.removeAll();
        bossBar.setVisible(false);
        bossBar.hide();
    }

    private void applyEntityScale(Pet pet, Entity entity) {
        if (!(entity instanceof Attributable attributable)) {
            return;
        }
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig == null) {
            return;
        }
        AttributeInstance scaleAttribute = attributable.getAttribute(Attribute.SCALE);
        if (scaleAttribute == null) {
            return;
        }
        scaleAttribute.setBaseValue(resolvePetScale(pet, petConfig));
    }

    private double resolvePetScale(Pet pet, PetConfig petConfig) {
        String expressionText = petConfig.scaleRequirement();
        if (expressionText == null || expressionText.isBlank()) {
            return 1.0D;
        }
        try {
            MathExpression expression = new MathExpression();
            expression.parse(expressionText);
            double value = expression.calculateDouble(pet.level() + 1, pet.level(), pet.exp());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return 1.0D;
            }
            return Math.max(0.01D, value);
        } catch (Exception ignored) {
            return 1.0D;
        }
    }

    private void applyPetDataToEntity(Pet pet, Entity entity) {
        if (pet == null || entity == null) {
            return;
        }
        entity.setSilent(isPetMuted(pet));
        applyConfiguredEntityData(entity, pet.data());
        applyConfiguredEntityData(entity, getEntityStateData(pet));
    }

    private Pet captureEntityStateIfNecessary(Pet pet, Entity entity) {
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig == null || !petConfig.saveEntityData()) {
            return pet;
        }
        Map<String, Object> entityState = extractEntityState(entity);
        if (entityState.isEmpty()) {
            return pet;
        }
        Map<String, Object> currentState = getEntityStateData(pet);
        if (entityState.equals(currentState)) {
            return pet;
        }
        return withDataValue(pet, ENTITY_STATE_KEY, entityState);
    }

    private Pet maybeSendCareReminder(Player player, Pet pet) {
        if (player == null || pet == null || !player.isOnline() || isAwaitingBlindBoxReveal(pet)) {
            return pet;
        }
        if (!plugin.getConfig().getBoolean("pet.tutorial.enabled", true)) {
            return pet;
        }
        long now = System.currentTimeMillis();
        long reminderIntervalMillis = Math.max(30L, plugin.getConfig().getLong("pet.tutorial.reminder-interval-seconds", 300L)) * 1000L;
        boolean needsBath = needsBath(pet);
        boolean needsFeed = needsFeed(pet);
        boolean shouldRemindBath = needsBath && now - getLongDataValue(pet, LAST_BATH_REMINDER_TIME_KEY) >= reminderIntervalMillis;
        boolean shouldRemindFeed = needsFeed && now - getLongDataValue(pet, LAST_FEED_REMINDER_TIME_KEY) >= reminderIntervalMillis;
        if (!shouldRemindBath && !shouldRemindFeed) {
            return pet;
        }

        if (shouldRemindBath && shouldRemindFeed) {
            player.sendActionBar(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                    "&e宠物提醒：&r" + getDisplayName(pet, player) + " &e现在又脏又饿。"
                            + " &7潜行右键宠物打开菜单，然后点洗澡和喂食即可"
            ));
        } else if (shouldRemindBath) {
            player.sendActionBar(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                    "&e宠物提醒：&r" + getDisplayName(pet, player) + " &e该洗澡了。"
                            + " &7潜行右键宠物打开菜单，然后点洗澡即可"
            ));
        } else {
            player.sendActionBar(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                    "&e宠物提醒：&r" + getDisplayName(pet, player) + " &e肚子饿了。"
                            + " &7潜行右键宠物打开菜单，然后点喂食即可"
            ));
        }

        Pet updated = pet;
        if (shouldRemindBath) {
            updated = withDataValue(updated, LAST_BATH_REMINDER_TIME_KEY, now);
        }
        if (shouldRemindFeed) {
            updated = withDataValue(updated, LAST_FEED_REMINDER_TIME_KEY, now);
        }
        return updated;
    }

    private Pet maybeShowSpawnTutorial(Player player, Pet pet) {
        if (player == null || pet == null || !player.isOnline() || isAwaitingBlindBoxReveal(pet)) {
            return pet;
        }
        if (!plugin.getConfig().getBoolean("pet.tutorial.enabled", true)) {
            return pet;
        }
        if (getBooleanDataValue(pet, TUTORIAL_SHOWN_KEY)) {
            return pet;
        }
        player.sendMessage(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                "&6宠物教程：&e右键宠物可骑乘，&e潜行右键可打开宠物菜单。"
        ));
        player.sendMessage(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                "&6宠物教程：&e菜单里可以查看状态、改名、洗澡和喂食。"
        ));
        player.sendMessage(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                "&6宠物教程：&e宠物冒烟说明该洗澡了，收到饥饿提醒时记得打开菜单喂食。"
        ));
        return withDataValue(pet, TUTORIAL_SHOWN_KEY, true);
    }

    private static void applyConfiguredEntityData(Entity entity, Map<String, Object> data) {
        if (entity == null || data == null || data.isEmpty()) {
            return;
        }
        if (entity instanceof Ageable ageable) {
            Boolean isBaby = getBooleanValue(data.get("isBaby"));
            if (isBaby != null) {
                if (isBaby) {
                    ageable.setBaby();
                } else {
                    ageable.setAdult();
                }
            }
        }
        if (entity instanceof Wolf wolf) {
            Boolean angry = getBooleanValue(data.get("angry"));
            if (angry != null) {
                wolf.setAngry(angry);
            }
        }
        if (entity instanceof Creeper creeper) {
            Boolean powered = getBooleanValue(data.get("powered"));
            if (powered != null) {
                creeper.setPowered(powered);
            }
        }
        if (entity instanceof Llama llama) {
            Llama.Color color = getEnumValue(data.get("llamaColor"), Llama.Color.class);
            if (color != null) {
                llama.setColor(color);
            }
        }
        if (entity instanceof Cat cat) {
            Cat.Type catType = getEnumValue(data.get("catType"), Cat.Type.class);
            if (catType != null) {
                cat.setCatType(catType);
            }
        }
        if (entity instanceof Rabbit rabbit) {
            Rabbit.Type rabbitType = getEnumValue(data.get("rabbitType"), Rabbit.Type.class);
            if (rabbitType != null) {
                rabbit.setRabbitType(rabbitType);
            }
        }
        if (entity instanceof Fox fox) {
            Fox.Type foxType = getEnumValue(data.get("foxType"), Fox.Type.class);
            if (foxType != null) {
                fox.setFoxType(foxType);
            }
        }
        if (entity instanceof Frog frog) {
            Frog.Variant frogVariant = getEnumValue(data.get("frogVariant"), Frog.Variant.class);
            if (frogVariant != null) {
                frog.setVariant(frogVariant);
            }
        }
        if (entity instanceof Panda panda) {
            Panda.Gene mainGene = getEnumValue(data.get("pandaMainGene"), Panda.Gene.class);
            if (mainGene != null) {
                panda.setMainGene(mainGene);
            }
            Panda.Gene hiddenGene = getEnumValue(data.get("pandaHiddenGene"), Panda.Gene.class);
            if (hiddenGene != null) {
                panda.setHiddenGene(hiddenGene);
            }
        }
        if (entity instanceof Parrot parrot) {
            Parrot.Variant parrotVariant = getEnumValue(data.get("parrotVariant"), Parrot.Variant.class);
            if (parrotVariant != null) {
                parrot.setVariant(parrotVariant);
            }
        }
        if (entity instanceof Sheep sheep) {
            org.bukkit.DyeColor sheepColor = getEnumValue(data.get("sheepColor"), org.bukkit.DyeColor.class);
            if (sheepColor != null) {
                sheep.setColor(sheepColor);
            }
        }
        if (entity instanceof Axolotl axolotl) {
            Axolotl.Variant axolotlVariant = getEnumValue(data.get("axolotlVariant"), Axolotl.Variant.class);
            if (axolotlVariant != null) {
                axolotl.setVariant(axolotlVariant);
            }
        }
    }

    private static Map<String, Object> extractEntityState(Entity entity) {
        Map<String, Object> entityState = new LinkedHashMap<>();
        if (entity instanceof Llama llama) {
            entityState.put("llamaColor", llama.getColor().name());
        }
        if (entity instanceof Cat cat) {
            entityState.put("catType", cat.getCatType().name());
        }
        if (entity instanceof Rabbit rabbit) {
            entityState.put("rabbitType", rabbit.getRabbitType().name());
        }
        if (entity instanceof Fox fox) {
            entityState.put("foxType", fox.getFoxType().name());
        }
        if (entity instanceof Frog frog) {
            entityState.put("frogVariant", frog.getVariant().name());
        }
        if (entity instanceof Panda panda) {
            entityState.put("pandaMainGene", panda.getMainGene().name());
            entityState.put("pandaHiddenGene", panda.getHiddenGene().name());
        }
        if (entity instanceof Parrot parrot) {
            entityState.put("parrotVariant", parrot.getVariant().name());
        }
        if (entity instanceof Sheep sheep) {
            entityState.put("sheepColor", sheep.getColor().name());
        }
        if (entity instanceof Axolotl axolotl) {
            entityState.put("axolotlVariant", axolotl.getVariant().name());
        }
        return entityState;
    }

    private static Map<String, Object> getEntityStateData(Pet pet) {
        Object entityState = pet.data() == null ? null : pet.data().get(ENTITY_STATE_KEY);
        if (!(entityState instanceof Map<?, ?> stateMap)) {
            return Map.of();
        }
        Map<String, Object> normalizedState = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : stateMap.entrySet()) {
            normalizedState.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalizedState;
    }

    private static Boolean getBooleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <E extends Enum<?>> E getEnumValue(Object value, Class<?> enumClass) {
        if (value == null) {
            return null;
        }
        if (enumClass == null || !Enum.class.isAssignableFrom(enumClass)) {
            return null;
        }
        try {
            Class<? extends Enum> resolvedEnumClass = enumClass.asSubclass(Enum.class);
            return (E) Enum.valueOf(resolvedEnumClass, String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void followOwner(Player player, Pet pet, Entity entity) {
        boolean flyingPet = canPetFly(pet);
        if (handleAutoTravel(player, pet, entity, flyingPet)) {
            return;
        }
        if (handleMountedMovement(player, pet, entity, flyingPet)) {
            return;
        }
        if (!entity.getPassengers().isEmpty()) {
            if (entity instanceof Mob mob) {
                NmsPetAiController.stop(mob);
            }
            return;
        }
        if (!entity.getWorld().equals(player.getWorld())) {
            entity.teleport(player.getLocation());
            return;
        }

        Location targetLocation = getFollowLocation(player, pet, entity, flyingPet);
        double ownerHorizontalDistanceSquared = getHorizontalDistanceSquared(entity.getLocation(), player.getLocation());
        double targetHorizontalDistanceSquared = getHorizontalDistanceSquared(entity.getLocation(), targetLocation);
        if (ownerHorizontalDistanceSquared >= TELEPORT_DISTANCE_SQUARED) {
            if (entity instanceof Player playerEntity && playerEntity != player) {
                if (NmsPlayerPetController.isAvailable()) {
                    NmsPlayerPetController.teleport(playerEntity, targetLocation);
                } else {
                    entity.teleport(targetLocation);
                }
                return;
            }
            entity.teleport(targetLocation);
            return;
        }

        if (ownerHorizontalDistanceSquared <= FOLLOW_STOP_DISTANCE_SQUARED
                && targetHorizontalDistanceSquared <= FOLLOW_SLOT_REACHED_DISTANCE_SQUARED) {
            if (entity instanceof Mob mob) {
                NmsPetAiController.stop(mob);
            }
            return;
        }
        if (entity instanceof Mob mob) {
            if (flyingPet) {
                if (targetHorizontalDistanceSquared >= GROUND_SLOT_TELEPORT_DISTANCE_SQUARED) {
                    entity.teleport(targetLocation);
                    return;
                }
                NmsPetAiController.moveFlyingPet(mob, targetLocation, getFollowFlyingSpeed(pet));
                return;
            }
            if (targetHorizontalDistanceSquared >= GROUND_SLOT_TELEPORT_DISTANCE_SQUARED) {
                entity.teleport(targetLocation);
                return;
            }
            NmsPetAiController.moveGroundPet(mob, targetLocation, getFollowGroundSpeed(pet));
            return;
        }
        if (entity instanceof Player playerEntity && playerEntity != player) {
            if (NmsPlayerPetController.isAvailable()) {
                NmsPlayerPetController.teleport(playerEntity, targetLocation);
            } else {
                entity.teleport(targetLocation);
            }
            return;
        }
        entity.teleport(targetLocation);
    }

    private boolean handleMountedMovement(Player owner, Pet pet, Entity entity, boolean flyingPet) {
        if (entity.getPassengers().isEmpty()) {
            return false;
        }
        Entity firstPassenger = entity.getPassengers().getFirst();
        if (!(firstPassenger instanceof Player rider) || !rider.getUniqueId().equals(owner.getUniqueId())) {
            if (entity instanceof Mob mob) {
                NmsPetAiController.stop(mob);
            }
            return true;
        }
        if (!isPetMovable(pet)) {
            if (entity instanceof Mob mob) {
                NmsPetAiController.stop(mob);
            }
            return true;
        }

        entity.setRotation(rider.getLocation().getYaw(), entity.getLocation().getPitch());
        Vector movementVector = resolveMountedMovementVector(rider, pet, entity, flyingPet);
        if (movementVector == null) {
            if (entity instanceof Mob mob) {
                NmsPetAiController.stop(mob);
            }
            if (flyingPet) {
                entity.setVelocity(new Vector(0D, 0D, 0D));
            } else {
                Vector currentVelocity = entity.getVelocity();
                entity.setVelocity(new Vector(0D, Math.min(currentVelocity.getY(), 0D), 0D));
            }
            entity.setFallDistance(0F);
            return true;
        }

        if (entity instanceof Mob mob) {
            NmsPetAiController.stop(mob);
        }
        applyMountedGroundStep(entity, movementVector);
        if (!flyingPet) {
            applyMountedWaterFloat(pet, entity, movementVector);
        }
        entity.setVelocity(movementVector);
        entity.setFallDistance(0F);
        return true;
    }

    private Vector resolveMountedMovementVector(Player rider, Pet pet, Entity entity, boolean flyingPet) {
        Input input = rider.getCurrentInput();
        if (input == null) {
            return null;
        }

        double forward = (input.isForward() ? 1D : 0D) - (input.isBackward() ? 1D : 0D);
        double strafe = (input.isRight() ? 1D : 0D) - (input.isLeft() ? 1D : 0D);
        double vertical = 0D;
        if (flyingPet) {
            vertical = isRiderTryingToAscend(rider, input) ? 1D : -getRiddenFlyingGlideFactor();
        }
        if (forward == 0D && strafe == 0D && !flyingPet) {
            return null;
        }

        Vector forwardVector = rider.getLocation().getDirection().setY(0D);
        if (forwardVector.lengthSquared() <= 1.0E-6D) {
            forwardVector = new Vector(0D, 0D, 1D);
        } else {
            forwardVector.normalize();
        }
        Vector rightVector = new Vector(-forwardVector.getZ(), 0D, forwardVector.getX());
        Vector movement = forwardVector.multiply(forward).add(rightVector.multiply(strafe));
        if (movement.lengthSquared() > 1.0E-6D) {
            movement.normalize().multiply(flyingPet ? getRiddenFlyingSpeed(pet) : getRiddenGroundSpeed(pet));
        } else {
            movement = new Vector(0D, 0D, 0D);
        }
        if (flyingPet) {
            movement.setY(vertical * getRiddenFlyingVerticalSpeed(pet));
        } else {
            movement.setY(entity.getVelocity().getY());
        }
        return movement;
    }

    private boolean isRiderTryingToAscend(Player rider, Input input) {
        if (input != null && input.isJump()) {
            return true;
        }
        return rider != null && rider.getVelocity().getY() > 0.08D;
    }

    private double getRiddenGroundSpeed(Pet pet) {
        return Math.max(0.05D, plugin.getConfig().getDouble("pet.ride.ground-speed", 0.55D) * getPetMovementMultiplier(pet));
    }

    private double getRiddenFlyingSpeed(Pet pet) {
        return Math.max(0.05D, plugin.getConfig().getDouble("pet.ride.flying-speed", 0.45D) * getPetMovementMultiplier(pet));
    }

    private double getRiddenFlyingVerticalSpeed(Pet pet) {
        return Math.max(0.02D, plugin.getConfig().getDouble("pet.ride.flying-vertical-speed", 0.2D) * getPetMovementMultiplier(pet));
    }

    private double getRiddenFlyingGlideFactor() {
        return Math.max(0D, plugin.getConfig().getDouble("pet.ride.flying-glide-factor", 0.35D));
    }

    private double getFollowGroundSpeed(Pet pet) {
        return Math.max(0.05D, 1.05D * getPetMovementMultiplier(pet));
    }

    private double getFollowFlyingSpeed(Pet pet) {
        return Math.max(0.05D, 1.2D * getPetMovementMultiplier(pet));
    }

    private void applyMountedGroundStep(Entity entity, Vector movementVector) {
        if (!entity.isOnGround() && !isEntityNearGround(entity)) {
            return;
        }
        Vector horizontalMovement = movementVector.clone().setY(0D);
        if (horizontalMovement.lengthSquared() <= 1.0E-6D) {
            return;
        }

        int maxStepBlocks = resolveMountedStepBlocks(entity);
        if (maxStepBlocks < 1) {
            return;
        }

        Vector direction = horizontalMovement.clone().normalize();
        double probeDistance = Math.max(entity.getWidth() * 0.5D + 0.35D, Math.min(1.0D, horizontalMovement.length() + entity.getWidth() * 0.5D));
        if (!collidesAtOffset(entity, direction.getX() * probeDistance, 0D, direction.getZ() * probeDistance)) {
            return;
        }

        for (int step = 1; step <= maxStepBlocks; step++) {
            double offsetX = direction.getX() * probeDistance;
            double offsetY = step;
            double offsetZ = direction.getZ() * probeDistance;
            if (collidesAtOffset(entity, offsetX, offsetY, offsetZ)) {
                continue;
            }
            if (!hasSupportAtOffset(entity, offsetX, offsetY, offsetZ)) {
                continue;
            }
            movementVector.setY(Math.max(movementVector.getY(), step));
            return;
        }
    }

    private boolean isEntityNearGround(Entity entity) {
        BoundingBox box = entity.getBoundingBox();
        World world = entity.getWorld();
        double centerX = (box.getMinX() + box.getMaxX()) * 0.5D;
        double centerZ = (box.getMinZ() + box.getMaxZ()) * 0.5D;
        int blockX = (int) Math.floor(centerX);
        int blockY = (int) Math.floor(box.getMinY() - 0.2D);
        int blockZ = (int) Math.floor(centerZ);
        Block support = world.getBlockAt(blockX, blockY, blockZ);
        return !support.isPassable() || isWaterSurfaceBlock(support);
    }

    private int resolveMountedStepBlocks(Entity entity) {
        double size = Math.max(entity.getHeight(), entity.getWidth());
        return Math.max(1, (int) Math.floor(size));
    }

    private void applyMountedWaterFloat(Pet pet, Entity entity, Vector movementVector) {
        if (!canPetFloatOnWater(pet)) {
            return;
        }
        if (movementVector.getY() < 0D) {
            movementVector.setY(0D);
        }

        Location location = entity.getLocation();
        Vector horizontalDirection = movementVector.clone().setY(0D);
        if (horizontalDirection.lengthSquared() <= 1.0E-6D) {
            horizontalDirection = location.getDirection().setY(0D);
        }
        if (horizontalDirection.lengthSquared() <= 1.0E-6D) {
            horizontalDirection = new Vector(0D, 0D, 1D);
        } else {
            horizontalDirection.normalize();
        }

        double probeDistance = Math.max(entity.getWidth() * 0.5D + 0.35D, 0.6D);
        Block supportBlock = getWaterSurfaceSupportBlock(entity, horizontalDirection, probeDistance);
        if (supportBlock == null) {
            return;
        }

        double targetY = supportBlock.getY() + 1.0D;
        double deltaY = targetY - location.getY();
        if (deltaY > 0D) {
            movementVector.setY(Math.max(movementVector.getY(), Math.min(deltaY, 0.6D)));
        } else if (Math.abs(deltaY) <= 0.25D) {
            movementVector.setY(Math.max(movementVector.getY(), deltaY));
        }
        entity.setFallDistance(0F);
    }

    private Block getWaterSurfaceSupportBlock(Entity entity, Vector direction, double probeDistance) {
        BoundingBox box = entity.getBoundingBox();
        World world = entity.getWorld();
        double sampleX = ((box.getMinX() + box.getMaxX()) * 0.5D) + direction.getX() * probeDistance;
        double sampleZ = ((box.getMinZ() + box.getMaxZ()) * 0.5D) + direction.getZ() * probeDistance;
        int blockX = (int) Math.floor(sampleX);
        int blockZ = (int) Math.floor(sampleZ);
        int minY = (int) Math.floor(box.getMinY()) - 1;
        int maxY = (int) Math.floor(box.getMinY()) + 1;
        for (int y = maxY; y >= minY; y--) {
            Block block = world.getBlockAt(blockX, y, blockZ);
            if (block.getType() == Material.BUBBLE_COLUMN) {
                continue;
            }
            if (isWaterSurfaceBlock(block)) {
                return block;
            }
            if (!block.isPassable()) {
                return null;
            }
        }
        return null;
    }

    private boolean handleAutoTravel(Player owner, Pet pet, Entity entity, boolean flyingPet) {
        AutoTravelState state = autoTravelStates.get(owner.getUniqueId());
        if (state == null || state.petId() != pet.id()) {
            return false;
        }
        if (entity.getPassengers().isEmpty() || !(entity.getPassengers().getFirst() instanceof Player rider)
                || !rider.getUniqueId().equals(owner.getUniqueId())) {
            return false;
        }
        Location targetLocation = resolveAutoTravelTargetLocation(entity, state, flyingPet);
        double horizontalDistanceSquared = getHorizontalDistanceSquared(entity.getLocation(), targetLocation);
        if (horizontalDistanceSquared <= 2.25D) {
            autoTravelStates.remove(owner.getUniqueId());
            if (entity instanceof Mob mob) {
                NmsPetAiController.stop(mob);
            }
            entity.setVelocity(new Vector(0D, 0D, 0D));
            owner.sendMessage(org.bxwbb.qcpet.utils.TextComponentUtil.legacy("&a宠物已到达目标点附近。"));
            return true;
        }
        if (!flyingPet) {
            Location pathTarget = resolveAutoTravelPathTarget(owner, entity, state);
            if (pathTarget != null) {
                targetLocation = pathTarget;
            } else if (isAutoTravelBlocked(owner, entity, state, horizontalDistanceSquared, false)) {
                if (entity instanceof Mob mob) {
                    NmsPetAiController.stop(mob);
                }
                entity.setVelocity(new Vector(0D, Math.min(entity.getVelocity().getY(), 0D), 0D));
                entity.setFallDistance(0F);
                return true;
            }
        }
        faceTowards(entity, targetLocation);
        Vector movementVector = resolveAutoTravelMovementVector(pet, entity, targetLocation, flyingPet);
        applyMountedGroundStep(entity, movementVector);
        if (!flyingPet) {
            applyMountedWaterFloat(pet, entity, movementVector);
        }
        entity.setVelocity(movementVector);
        entity.setFallDistance(0F);
        handleAutoTravelDefendOwner(owner, pet, entity, state);
        return true;
    }

    private Vector resolveAutoTravelMovementVector(Pet pet, Entity entity, Location targetLocation, boolean flyingPet) {
        Location current = entity.getLocation();
        Vector delta = targetLocation.toVector().subtract(current.toVector());
        Vector movement = delta.clone().setY(0D);
        if (movement.lengthSquared() > 1.0E-6D) {
            movement.normalize().multiply(flyingPet ? getRiddenFlyingSpeed(pet) : getRiddenGroundSpeed(pet));
        } else {
            movement = new Vector(0D, 0D, 0D);
        }
        if (flyingPet) {
            double verticalInput = current.getY() + 0.35D < targetLocation.getY() ? 1D : -getRiddenFlyingGlideFactor();
            if (collidesAhead(entity, movement, 0D)) {
                verticalInput = 1D;
            }
            movement.setY(verticalInput * getRiddenFlyingVerticalSpeed(pet));
        } else {
            movement.setY(entity.getVelocity().getY());
        }
        return movement;
    }

    private boolean isAutoTravelBlocked(Player owner, Entity entity, AutoTravelState state, double horizontalDistanceSquared, boolean flyingPet) {
        Location current = entity.getLocation();
        double movedDistanceSquared = state.lastCheckedLocation == null
                ? Double.MAX_VALUE
                : getHorizontalDistanceSquared(current, state.lastCheckedLocation);
        state.lastCheckedLocation = current.clone();
        if (horizontalDistanceSquared <= 4.0D) {
            state.stuckTicks = 0L;
            return false;
        }
        if (movedDistanceSquared <= 0.01D) {
            state.stuckTicks++;
        } else {
            state.stuckTicks = 0L;
        }
        if (state.stuckTicks < 20L) {
            return false;
        }
        if (flyingPet) {
            return false;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        if (currentTick - state.lastBlockedNotifyTick >= 20L) {
            owner.sendActionBar(org.bxwbb.qcpet.utils.TextComponentUtil.legacy("&e宠物被地形挡住了，无法从陆地到达目标点。"));
            spawnAutoTravelConfusedParticles(entity);
            state.lastBlockedNotifyTick = currentTick;
        }
        return true;
    }

    private Location resolveAutoTravelPathTarget(Player owner, Entity entity, AutoTravelState state) {
        World world = entity.getWorld();
        int targetBlockX = (int) Math.floor(state.targetX());
        int targetBlockZ = (int) Math.floor(state.targetZ());
        if (state.pathNodes == null
                || state.pathNodes.isEmpty()
                || state.pathWorldUid == null
                || !state.pathWorldUid.equals(world.getUID())
                || state.pathTargetBlockX != targetBlockX
                || state.pathTargetBlockZ != targetBlockZ) {
            rebuildAutoTravelPath(owner, entity, state, targetBlockX, targetBlockZ);
        }
        if (state.pathNodes == null || state.pathNodes.isEmpty()) {
            return null;
        }
        while (state.pathIndex < state.pathNodes.size()) {
            PathNode node = state.pathNodes.get(state.pathIndex);
            Location nodeLocation = toAutoTravelNodeLocation(world, node);
            if (getHorizontalDistanceSquared(entity.getLocation(), nodeLocation) <= 1.0D) {
                state.pathIndex++;
                continue;
            }
            return nodeLocation;
        }
        rebuildAutoTravelPath(owner, entity, state, targetBlockX, targetBlockZ);
        if (state.pathNodes == null || state.pathNodes.isEmpty() || state.pathIndex >= state.pathNodes.size()) {
            return null;
        }
        return toAutoTravelNodeLocation(world, state.pathNodes.get(state.pathIndex));
    }

    private void rebuildAutoTravelPath(Player owner, Entity entity, AutoTravelState state, int targetBlockX, int targetBlockZ) {
        state.pathWorldUid = entity.getWorld().getUID();
        state.pathTargetBlockX = targetBlockX;
        state.pathTargetBlockZ = targetBlockZ;
        state.pathIndex = 0;
        state.pathNodes = findAutoTravelPath(entity, targetBlockX, targetBlockZ);
        if ((state.pathNodes == null || state.pathNodes.isEmpty())
                && plugin.getServer().getCurrentTick() - state.lastBlockedNotifyTick >= 20L) {
            owner.sendActionBar(org.bxwbb.qcpet.utils.TextComponentUtil.legacy("&e宠物被地形挡住了，无法从陆地到达目标点。"));
            spawnAutoTravelConfusedParticles(entity);
            state.lastBlockedNotifyTick = plugin.getServer().getCurrentTick();
        }
    }

    private List<PathNode> findAutoTravelPath(Entity entity, int targetBlockX, int targetBlockZ) {
        World world = entity.getWorld();
        PathNode start = resolveAutoTravelStartNode(entity);
        PathNode goal = resolveAutoTravelGoalNode(entity, targetBlockX, targetBlockZ);
        if (start == null || goal == null) {
            return List.of();
        }
        if (start.equals(goal)) {
            return List.of(goal);
        }

        int maxSearchRadius = Math.max(8, plugin.getConfig().getInt("pet.auto-travel.path-search-radius", 24));
        PriorityQueue<PathRecord> openSet = new PriorityQueue<>((left, right) -> Double.compare(left.fScore, right.fScore));
        Map<PathNode, PathRecord> records = new HashMap<>();
        PathRecord startRecord = new PathRecord(start, null, 0D, estimatePathCost(start, goal));
        records.put(start, startRecord);
        openSet.add(startRecord);

        while (!openSet.isEmpty()) {
            PathRecord current = openSet.poll();
            if (current.closed) {
                continue;
            }
            current.closed = true;
            if (current.node.equals(goal)) {
                return buildPathNodes(current);
            }
            for (PathNode neighbor : findPathNeighbors(entity, current.node, start, maxSearchRadius)) {
                double tentativeG = current.gScore + movementCost(current.node, neighbor);
                PathRecord existing = records.get(neighbor);
                if (existing != null && tentativeG >= existing.gScore) {
                    continue;
                }
                PathRecord updated = new PathRecord(neighbor, current, tentativeG, tentativeG + estimatePathCost(neighbor, goal));
                records.put(neighbor, updated);
                openSet.add(updated);
            }
        }
        return List.of();
    }

    private PathNode resolveAutoTravelStartNode(Entity entity) {
        Location location = entity.getLocation();
        return findNearestWalkableNode(entity, location.getBlockX(), location.getBlockY(), location.getBlockZ(), 2);
    }

    private PathNode resolveAutoTravelGoalNode(Entity entity, int blockX, int blockZ) {
        World world = entity.getWorld();
        return findNearestWalkableNode(entity, blockX, world.getHighestBlockYAt(blockX, blockZ) + 1, blockZ, 2);
    }

    private PathNode findNearestWalkableNode(Entity entity, int blockX, int baseY, int blockZ, int verticalRadius) {
        for (int offsetY = 0; offsetY <= verticalRadius; offsetY++) {
            int upY = baseY + offsetY;
            if (isWalkableNode(entity, blockX, upY, blockZ)) {
                return new PathNode(blockX, upY, blockZ);
            }
            int downY = baseY - offsetY;
            if (offsetY > 0 && isWalkableNode(entity, blockX, downY, blockZ)) {
                return new PathNode(blockX, downY, blockZ);
            }
        }
        return null;
    }

    private boolean isWalkableNode(Entity entity, int blockX, int blockY, int blockZ) {
        World world = entity.getWorld();
        Block feet = world.getBlockAt(blockX, blockY, blockZ);
        Block head = world.getBlockAt(blockX, blockY + 1, blockZ);
        Block below = world.getBlockAt(blockX, blockY - 1, blockZ);
        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (below.isPassable() && !isWaterSurfaceBlock(below)) {
            return false;
        }

        Location current = entity.getLocation();
        double targetCenterX = blockX + 0.5D;
        double targetCenterZ = blockZ + 0.5D;
        double offsetX = targetCenterX - current.getX();
        double offsetY = blockY - current.getY();
        double offsetZ = targetCenterZ - current.getZ();
        if (collidesAtOffset(entity, offsetX, offsetY, offsetZ)) {
            return false;
        }
        return hasSupportAtOffset(entity, offsetX, offsetY, offsetZ) || isWaterSurfaceBlock(below);
    }

    private List<PathNode> findPathNeighbors(Entity entity, PathNode node, PathNode start, int maxSearchRadius) {
        List<PathNode> neighbors = new ArrayList<>(8);
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                int nextX = node.x + offsetX;
                int nextZ = node.z + offsetZ;
                if (Math.abs(nextX - start.x) > maxSearchRadius || Math.abs(nextZ - start.z) > maxSearchRadius) {
                    continue;
                }
                PathNode neighbor = findNearestWalkableNode(entity, nextX, node.y, nextZ, 1);
                if (neighbor == null || Math.abs(neighbor.y - node.y) > 1) {
                    continue;
                }
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    private double movementCost(PathNode from, PathNode to) {
        boolean diagonal = from.x != to.x && from.z != to.z;
        return (diagonal ? 1.41421356237D : 1D) + Math.abs(to.y - from.y) * 0.35D;
    }

    private double estimatePathCost(PathNode current, PathNode goal) {
        double deltaX = goal.x - current.x;
        double deltaZ = goal.z - current.z;
        double deltaY = Math.abs(goal.y - current.y);
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) + deltaY * 0.35D;
    }

    private List<PathNode> buildPathNodes(PathRecord end) {
        List<PathNode> nodes = new ArrayList<>();
        PathRecord current = end;
        while (current != null) {
            nodes.add(0, current.node);
            current = current.parent;
        }
        if (!nodes.isEmpty()) {
            nodes.remove(0);
        }
        return nodes;
    }

    private Location toAutoTravelNodeLocation(World world, PathNode node) {
        return new Location(world, node.x + 0.5D, node.y, node.z + 0.5D);
    }

    private Location resolveAutoTravelTargetLocation(Entity entity, AutoTravelState state, boolean flyingPet) {
        World world = entity.getWorld();
        int blockX = (int) Math.floor(state.targetX());
        int blockZ = (int) Math.floor(state.targetZ());
        double groundY = world.getHighestBlockYAt(blockX, blockZ) + 1.0D;
        double targetY = flyingPet ? resolveFlyingAutoTravelTargetY(entity, state, groundY) : groundY;
        return new Location(world, state.targetX(), targetY, state.targetZ());
    }

    private double resolveFlyingAutoTravelTargetY(Entity entity, AutoTravelState state, double groundY) {
        Location current = entity.getLocation();
        double highestAlongRoute = sampleHighestBlockYAlongRoute(entity.getWorld(), current.getX(), current.getZ(), state.targetX(), state.targetZ());
        double safetyClearance = Math.max(3.0D, entity.getHeight() + 2.0D);
        return Math.max(current.getY(), highestAlongRoute + safetyClearance);
    }

    private double sampleHighestBlockYAlongRoute(World world, double startX, double startZ, double targetX, double targetZ) {
        double deltaX = targetX - startX;
        double deltaZ = targetZ - startZ;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaZ))));
        double highest = Double.NEGATIVE_INFINITY;
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            double sampleX = startX + deltaX * progress;
            double sampleZ = startZ + deltaZ * progress;
            highest = Math.max(highest, world.getHighestBlockYAt((int) Math.floor(sampleX), (int) Math.floor(sampleZ)));
        }
        return highest == Double.NEGATIVE_INFINITY ? world.getMinHeight() : highest + 1.0D;
    }

    private void faceTowards(Entity entity, Location targetLocation) {
        Vector direction = targetLocation.toVector().subtract(entity.getLocation().toVector()).setY(0D);
        if (direction.lengthSquared() <= 1.0E-6D) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        entity.setRotation(yaw, entity.getLocation().getPitch());
    }

    private void handleAutoTravelDefendOwner(Player owner, Pet pet, Entity entity, AutoTravelState state) {
        if (!canPetDefendOwnerDuringAutoTravel(pet)) {
            return;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        if (currentTick - state.lastAttackTick < 20L) {
            return;
        }
        Mob threat = findNearestThreateningMob(owner, entity, 8.0D);
        if (threat == null || entity.getLocation().distanceSquared(threat.getLocation()) > 6.25D) {
            return;
        }
        threat.damage(resolveAutoTravelAttackDamage(entity), entity);
        state.lastAttackTick = currentTick;
    }

    private Mob findNearestThreateningMob(Player owner, Entity entity, double radius) {
        Mob nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Entity nearby : owner.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Mob mob) || mob.isDead() || !mob.isValid()) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target == null || !target.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            double distanceSquared = entity.getLocation().distanceSquared(mob.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearest = mob;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private double resolveAutoTravelAttackDamage(Entity entity) {
        if (entity instanceof Wither) {
            return 8.0D;
        }
        if (entity instanceof Llama) {
            return 4.0D;
        }
        return 4.0D;
    }

    private boolean isWaterSurfaceBlock(Block block) {
        Material material = block.getType();
        return material == Material.WATER || material == Material.KELP
                || material == Material.KELP_PLANT || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS;
    }

    private boolean collidesAtOffset(Entity entity, double offsetX, double offsetY, double offsetZ) {
        BoundingBox movedBox = entity.getBoundingBox().shift(offsetX, offsetY, offsetZ);
        World world = entity.getWorld();
        int minX = (int) Math.floor(movedBox.getMinX());
        int maxX = (int) Math.floor(movedBox.getMaxX() - 1.0E-6D);
        int minY = (int) Math.floor(movedBox.getMinY());
        int maxY = (int) Math.floor(movedBox.getMaxY() - 1.0E-6D);
        int minZ = (int) Math.floor(movedBox.getMinZ());
        int maxZ = (int) Math.floor(movedBox.getMaxZ() - 1.0E-6D);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean collidesAhead(Entity entity, Vector horizontalMovement, double offsetY) {
        Vector horizontal = horizontalMovement.clone().setY(0D);
        if (horizontal.lengthSquared() <= 1.0E-6D) {
            return false;
        }
        Vector direction = horizontal.normalize();
        double probeDistance = Math.max(entity.getWidth() * 0.5D + 0.45D, Math.min(1.25D, horizontalMovement.length() + entity.getWidth() * 0.5D));
        return collidesAtOffset(entity, direction.getX() * probeDistance, offsetY, direction.getZ() * probeDistance);
    }

    private boolean hasSupportAtOffset(Entity entity, double offsetX, double offsetY, double offsetZ) {
        BoundingBox movedBox = entity.getBoundingBox().shift(offsetX, offsetY, offsetZ);
        World world = entity.getWorld();
        double centerX = (movedBox.getMinX() + movedBox.getMaxX()) * 0.5D;
        double centerZ = (movedBox.getMinZ() + movedBox.getMaxZ()) * 0.5D;
        int blockX = (int) Math.floor(centerX);
        int blockY = (int) Math.floor(movedBox.getMinY() - 0.05D);
        int blockZ = (int) Math.floor(centerZ);
        return !world.getBlockAt(blockX, blockY, blockZ).isPassable();
    }

    private Location getFollowLocation(Player player, Pet pet, Entity entity, boolean flyingPet) {
        Location baseLocation = player.getLocation().clone();
        List<Pet> visiblePets = getPets(player).stream()
                .filter(Pet::show)
                .sorted((left, right) -> Long.compare(left.id(), right.id()))
                .toList();
        int visibleCount = Math.max(1, visiblePets.size());
        int slotIndex = 0;
        for (int index = 0; index < visiblePets.size(); index++) {
            if (visiblePets.get(index).id() == pet.id()) {
                slotIndex = index;
                break;
            }
        }

        Vector moveDirection = player.getVelocity().clone().setY(0D);
        double xOffset;
        double zOffset;
        if (moveDirection.lengthSquared() > 1.0E-4D) {
            moveDirection.normalize();
            Vector backDirection = moveDirection.clone().multiply(-1D);
            Vector sideDirection = new Vector(-moveDirection.getZ(), 0D, moveDirection.getX());
            double lateralOffset = visibleCount <= 1 ? 0D : (slotIndex - (visibleCount - 1) / 2.0D) * 0.9D;
            Vector combinedOffset = backDirection.multiply(FOLLOW_SLOT_RADIUS).add(sideDirection.multiply(lateralOffset));
            xOffset = combinedOffset.getX();
            zOffset = combinedOffset.getZ();
        } else {
            Vector fallbackDirection = entity == null
                    ? new Vector(0D, 0D, 1D)
                    : entity.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0D);
            if (fallbackDirection.lengthSquared() <= 1.0E-6D) {
                fallbackDirection = new Vector(0D, 0D, 1D);
            } else {
                fallbackDirection.normalize();
            }
            Vector sideDirection = new Vector(-fallbackDirection.getZ(), 0D, fallbackDirection.getX());
            double lateralOffset = visibleCount <= 1 ? 0D : (slotIndex - (visibleCount - 1) / 2.0D) * 0.8D;
            Vector combinedOffset = fallbackDirection.multiply(FOLLOW_SLOT_RADIUS).add(sideDirection.multiply(lateralOffset));
            xOffset = combinedOffset.getX();
            zOffset = combinedOffset.getZ();
        }
        baseLocation.add(xOffset, 0D, zOffset);
        if (flyingPet) {
            baseLocation.setY(player.getLocation().getY() + FLYING_FOLLOW_HEIGHT);
        }
        return baseLocation;
    }

    private static double getHorizontalDistanceSquared(Location first, Location second) {
        double deltaX = first.getX() - second.getX();
        double deltaZ = first.getZ() - second.getZ();
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private void spawnDirtyParticles(Entity entity) {
        Location location = entity.getLocation().clone().add(0, Math.max(0.5D, entity.getHeight() * 0.5D), 0);
        entity.getWorld().spawnParticle(Particle.SMOKE, location, 1, 0.18D, 0.18D, 0.18D, 0.01D);
    }

    private void spawnHungryParticles(Entity entity) {
        Location location = entity.getLocation().clone().add(0, Math.max(0.7D, entity.getHeight() * 0.7D), 0);
        entity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, location, 1, 0.08D, 0.08D, 0.08D, 0D);
        entity.getWorld().spawnParticle(Particle.SMOKE, location, 1, 0.15D, 0.15D, 0.15D, 0.01D);
    }

    private void spawnAutoTravelConfusedParticles(Entity entity) {
        Location location = entity.getLocation().clone().add(0, Math.max(0.8D, entity.getHeight() * 0.8D), 0);
        entity.getWorld().spawnParticle(Particle.CLOUD, location, 6, 0.18D, 0.12D, 0.18D, 0.01D);
        entity.getWorld().spawnParticle(Particle.SMOKE, location, 3, 0.12D, 0.08D, 0.12D, 0.01D);
    }

    private void playBathEffect(Entity entity) {
        Location location = entity.getLocation().clone().add(0, Math.max(0.5D, entity.getHeight() * 0.5D), 0);
        entity.getWorld().playSound(location, Sound.ENTITY_PLAYER_SPLASH, 0.8F, 1.1F);
        entity.getWorld().playSound(location, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.7F, 1.3F);
        for (int tick = 0; tick < 20; tick += 5) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!entity.isValid() || entity.isDead()) {
                    return;
                }
                Location current = entity.getLocation().clone().add(0, Math.max(0.5D, entity.getHeight() * 0.5D), 0);
                entity.getWorld().spawnParticle(Particle.BUBBLE, current, 18, 0.3D, 0.3D, 0.3D, 0.03D);
                entity.getWorld().spawnParticle(Particle.CLOUD, current, 10, 0.25D, 0.25D, 0.25D, 0.01D);
            }, tick);
        }
    }

    private void playFeedEffect(Entity entity) {
        Location location = entity.getLocation().clone().add(0, Math.max(0.5D, entity.getHeight() * 0.5D), 0);
        entity.getWorld().playSound(location, Sound.ENTITY_GENERIC_EAT, 0.9F, 1.0F);
        entity.getWorld().playSound(location, Sound.ENTITY_PLAYER_BURP, 0.4F, 1.2F);
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 8, 0.25D, 0.25D, 0.25D, 0D);
    }

    private void playLoveEffect(Entity entity, long durationTicks) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return;
        }
        long safeDuration = Math.max(1L, durationTicks);
        for (long tick = 0L; tick <= safeDuration; tick += 4L) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!entity.isValid() || entity.isDead()) {
                    return;
                }
                Location current = entity.getLocation().clone().add(0, Math.max(0.8D, entity.getHeight() * 0.8D), 0);
                entity.getWorld().spawnParticle(Particle.HEART, current, 2, 0.28D, 0.22D, 0.28D, 0D);
            }, tick);
        }
    }

    private static long getLastBathTime(Pet pet) {
        return getLongDataValue(pet, LAST_BATH_TIME_KEY);
    }

    private static long getLastFeedTime(Pet pet) {
        return getLongDataValue(pet, LAST_FEED_TIME_KEY);
    }

    private static long getLastPassiveExpMinute(Pet pet) {
        return getLongDataValue(pet, LAST_PASSIVE_EXP_MINUTE_KEY);
    }

    private static long getLongDataValue(Pet pet, String key) {
        Object value = pet.data() == null ? null : pet.data().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean getBooleanDataValue(Pet pet, String key) {
        Object value = pet.data() == null ? null : pet.data().get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private static Pet markBathTime(Pet pet, long timestamp) {
        return withDataValue(pet, LAST_BATH_TIME_KEY, timestamp);
    }

    private static Pet markFeedTime(Pet pet, long timestamp) {
        return withDataValue(pet, LAST_FEED_TIME_KEY, timestamp);
    }

    private static Pet markPassiveExpMinute(Pet pet, long minute) {
        return withDataValue(pet, LAST_PASSIVE_EXP_MINUTE_KEY, minute);
    }

    private static Pet withDataValue(Pet pet, String key, long value) {
        return withDataValue(pet, key, Long.valueOf(value));
    }

    private static Pet withDataValue(Pet pet, String key, Object value) {
        Map<String, Object> data = pet.data() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(pet.data());
        data.put(key, value);
        return new Pet(
                pet.id(),
                pet.name(),
                pet.type(),
                pet.level(),
                pet.exp(),
                pet.times(),
                data,
                pet.show(),
                pet.owner(),
                pet.entity()
        );
    }

    private static Pet markBlindBoxRevealPending(Pet pet, boolean pending) {
        return withDataValue(pet, BLIND_BOX_REVEAL_PENDING_KEY, pending);
    }

    private boolean isFlyingPet(Pet pet, Entity entity) {
        if (canPetFly(pet)) {
            return true;
        }
        if (entity instanceof TextDisplay && pet != null) {
            PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
            if (petConfig != null) {
                return isFlyingType(petConfig.entityType());
            }
        }
        return isFlyingEntity(entity);
    }

    private static boolean isFlyingEntity(Entity entity) {
        return entity instanceof Flying
                || entity instanceof Ambient
                || entity instanceof Phantom
                || entity instanceof Bee
                || entity instanceof Bat
                || entity instanceof Parrot
                || entity instanceof Allay
                || entity instanceof Vex;
    }

    private static boolean isFlyingType(EntityType entityType) {
        return entityType == EntityType.ALLAY
                || entityType == EntityType.BAT
                || entityType == EntityType.BEE
                || entityType == EntityType.BLAZE
                || entityType == EntityType.ENDER_DRAGON
                || entityType == EntityType.GHAST
                || entityType == EntityType.HAPPY_GHAST
                || entityType == EntityType.PARROT
                || entityType == EntityType.PHANTOM
                || entityType == EntityType.VEX;
    }

    private static String applyStateDecoration(Pet pet, String baseName, String prefixKey, String suffixKey) {
        Map<String, Object> data = pet.data();
        String prefix = data == null ? "" : String.valueOf(data.getOrDefault(prefixKey, ""));
        String suffix = data == null ? "" : String.valueOf(data.getOrDefault(suffixKey, ""));
        return prefix + baseName + suffix;
    }

    public boolean isPetMuted(Pet pet) {
        return getBooleanDataValue(pet, MUTED_KEY);
    }

    private Pet showPetInternal(Player player, Pet pet, boolean persist, boolean fireSpawnEvent) {
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig == null) {
            throw new IllegalStateException("Missing pet config for type " + pet.type());
        }
        removeEntity(pet);
        Entity entity;
        if (shouldDisplayAsBlindBox(pet)) {
            entity = player.getWorld().spawnEntity(player.getLocation(), EntityType.TEXT_DISPLAY);
        } else if (petConfig.entityType() == EntityType.PLAYER) {
            entity = spawnPlayerPetEntity(player, pet);
        } else {
            internalSpawnDepth++;
            try {
                entity = player.getWorld().spawnEntity(player.getLocation(), petConfig.entityType());
            } finally {
                internalSpawnDepth = Math.max(0, internalSpawnDepth - 1);
            }
        }
        Pet appliedPet = applyEntityState(player, pet, entity);
        Pet updated = copyPet(appliedPet, true, entity);
        if (fireSpawnEvent) {
            updated = maybeShowSpawnTutorial(player, updated);
        }
        replacePet(player, updated);
        if (fireSpawnEvent) {
            executePetEvent(player, updated, "on-spawn");
        }
        if (persist) {
            plugin.getPetUtil().savePet(updated);
        } else if (!appliedPet.data().equals(pet.data())) {
            plugin.getPetUtil().savePetAsync(updated);
        }
        return updated;
    }

    public boolean isInternalPetSpawnInProgress() {
        return internalSpawnDepth > 0;
    }

    private void handlePetSpawnFailure(Player player, Pet pet, Exception exception, boolean persistImmediately) {
        if (player == null || pet == null) {
            return;
        }

        removeEntity(pet);
        Pet hiddenPet = copyPet(pet, false, null);
        replacePet(player, hiddenPet);

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("%qcpet_spawn_fail_reason%", exception.getClass().getSimpleName());
        placeholders.put("%qcpet_spawn_fail_message%", exception.getMessage() == null ? "" : exception.getMessage());
        executePetEvent(player, hiddenPet, "on-spawn-fail", placeholders);

        if (player.isOnline()) {
            player.sendMessage(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(
                    "&c宠物生成失败，已自动收起。ID: &e" + hiddenPet.id() + " &7原因: &f" + placeholders.get("%qcpet_spawn_fail_reason%")
            ));
        }

        plugin.getLogger().log(
                java.util.logging.Level.SEVERE,
                "宠物生成失败: player=" + player.getName()
                        + ", petId=" + hiddenPet.id()
                        + ", petType=" + hiddenPet.type()
                        + ", petName=" + getDisplayName(hiddenPet, player)
                        + ", world=" + player.getWorld().getName(),
                exception
        );

        if (persistImmediately) {
            plugin.getPetUtil().savePet(hiddenPet);
        } else {
            plugin.getPetUtil().savePetAsync(hiddenPet);
        }
    }

    private Entity spawnPlayerPetEntity(Player player, Pet pet) {
        if (!NmsPlayerPetController.isAvailable()) {
            Throwable error = NmsPlayerPetController.getInitializationError();
            if (error == null) {
                throw new IllegalStateException("玩家宠物 NMS 适配器不可用: owner="
                        + player.getName()
                        + ", petId=" + pet.id()
                        + ", petType=" + pet.type()
                        + ", world=" + player.getWorld().getName());
            }
            throw new IllegalStateException("玩家宠物 NMS 适配器初始化失败: owner="
                    + player.getName()
                    + ", petId=" + pet.id()
                    + ", petType=" + pet.type()
                    + ", world=" + player.getWorld().getName(), error);
        }
        return NmsPlayerPetController.spawnPlayerPet(player, getDisplayName(pet, player), player.getLocation());
    }

    public void handlePetDamaged(Pet pet, Entity damager) {
        if (pet == null) {
            return;
        }
        Player owner = pet.owner();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        executePetEvent(owner, pet, "on-damage", buildEventContext(owner, pet, damager));
    }

    private void executeLevelChangeEvents(Player player, Pet oldPet, Pet newPet, int previousLevel) {
        if (isBlindBoxPet(oldPet) && !isBlindBoxPet(newPet)) {
            prepareBlindBoxReveal(player, oldPet, newPet);
        }
        if (newPet.level() > previousLevel) {
            executePetEvent(player, newPet, "on-level-up", Map.of(
                    "%qcpet_old_level%", String.valueOf(previousLevel),
                    "%qcpet_new_level%", String.valueOf(newPet.level())
            ));
        }
    }

    private void prepareBlindBoxReveal(Player player, Pet oldPet, Pet newPet) {
        if (player == null || !player.isOnline() || !newPet.show()) {
            return;
        }
        Pet pendingRevealPet = markBlindBoxRevealPending(newPet, true);
        replacePet(player, pendingRevealPet);
        Entity revealEntity = oldPet.entity();
        if (revealEntity == null || !revealEntity.isValid() || revealEntity.isDead()) {
            showPetInternal(player, pendingRevealPet, false, false);
            return;
        }
        if (revealEntity instanceof TextDisplay textDisplay) {
            textDisplay.text(LEGACY_SERIALIZER.deserialize("&e&l点击我！"));
        }
        spawnBlindBoxRevealInteraction(player, pendingRevealPet, revealEntity);
    }

    private void continueBlindBoxReveal(Player player, Pet pet) {
        if (player == null || pet == null || !player.isOnline()) {
            return;
        }
        Entity revealEntity = pet.entity();
        cleanupBlindBoxRevealInteraction(player.getUniqueId(), pet.id());
        if (revealEntity == null || !revealEntity.isValid() || revealEntity.isDead()) {
            showPetInternal(player, pet, false, false);
            return;
        }

        for (long tick = 0L; tick <= BLIND_BOX_REVEAL_DURATION_TICKS; tick += 4L) {
            long currentTick = tick;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!revealEntity.isValid() || revealEntity.isDead()) {
                    return;
                }
                Location location = revealEntity.getLocation().clone();
                location.setYaw(location.getYaw() + 24F);
                revealEntity.teleport(location);
                Location effectLocation = revealEntity.getLocation().clone().add(0, Math.max(0.8D, revealEntity.getHeight() * 0.8D), 0);
                revealEntity.getWorld().spawnParticle(Particle.CLOUD, effectLocation, 14, 0.35D, 0.45D, 0.35D, 0.01D);
                revealEntity.getWorld().spawnParticle(Particle.POOF, effectLocation, 8, 0.25D, 0.3D, 0.25D, 0.01D);
                revealEntity.getWorld().playSound(effectLocation, Sound.BLOCK_CHEST_OPEN, 0.7F, 1.0F + (currentTick * 0.005F));
            }, tick);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                removeEntity(pet);
                return;
            }
            Pet currentPet = findPet(player, pet.id()).orElse(null);
            if (currentPet == null || !currentPet.show()) {
                removeEntity(pet);
                return;
            }
            Pet shownPet = showPetInternal(player, markBlindBoxRevealPending(currentPet, false), false, false);
            if (shownPet != null && shownPet.entity() != null) {
                playLoveEffect(shownPet.entity(), 12L);
            }
        }, BLIND_BOX_REVEAL_DURATION_TICKS + 2L);
    }

    private void spawnBlindBoxRevealInteraction(Player player, Pet pet, Entity revealEntity) {
        cleanupBlindBoxRevealInteraction(player.getUniqueId(), pet.id());
        Interaction interaction = (Interaction) player.getWorld().spawnEntity(revealEntity.getLocation(), EntityType.INTERACTION);
        interaction.setPersistent(false);
        interaction.setInvulnerable(true);
        interaction.setResponsive(true);
        interaction.setInteractionWidth(Math.max(1.2F, (float) revealEntity.getWidth() + 0.6F));
        interaction.setInteractionHeight(Math.max(1.4F, (float) revealEntity.getHeight() + 0.8F));
        blindBoxRevealInteractions.put(interaction.getUniqueId(),
                new BlindBoxRevealInteraction(player.getUniqueId(), pet.id(), interaction.getUniqueId()));
    }

    private BlindBoxRevealInteraction getBlindBoxRevealInteraction(UUID ownerUuid, long petId) {
        if (ownerUuid == null) {
            return null;
        }
        for (BlindBoxRevealInteraction interaction : blindBoxRevealInteractions.values()) {
            if (interaction.ownerUuid().equals(ownerUuid) && interaction.petId() == petId) {
                return interaction;
            }
        }
        return null;
    }

    private void cleanupBlindBoxRevealInteraction(UUID ownerUuid, long petId) {
        BlindBoxRevealInteraction interaction = getBlindBoxRevealInteraction(ownerUuid, petId);
        if (interaction == null) {
            return;
        }
        blindBoxRevealInteractions.remove(interaction.interactionEntityUuid());
        Entity interactionEntity = plugin.getServer().getEntity(interaction.interactionEntityUuid());
        if (interactionEntity != null && interactionEntity.isValid() && !interactionEntity.isDead()) {
            interactionEntity.remove();
        }
    }

    private void syncBlindBoxRevealInteraction(Pet pet) {
        if (pet == null || pet.owner() == null) {
            return;
        }
        BlindBoxRevealInteraction interaction = getBlindBoxRevealInteraction(pet.owner().getUniqueId(), pet.id());
        if (interaction == null) {
            return;
        }
        Entity petEntity = pet.entity();
        Entity interactionEntity = plugin.getServer().getEntity(interaction.interactionEntityUuid());
        if (petEntity == null || !petEntity.isValid() || petEntity.isDead() || interactionEntity == null || !interactionEntity.isValid() || interactionEntity.isDead()) {
            cleanupBlindBoxRevealInteraction(pet.owner().getUniqueId(), pet.id());
            return;
        }
        interactionEntity.teleport(petEntity.getLocation());
    }

    private Pet executeTickEvent(Player player, Pet pet) {
        if (pet == null || pet.entity() == null) {
            return pet;
        }
        long currentTick = plugin.getServer().getCurrentTick();
        if (currentTick % 20L == 0L) {
            executePetEvent(player, pet, "on-second");
        }
        if (currentTick % (20L * 60L) == 0L) {
            pet = applyPassiveExperience(player, pet);
        }
        executePetEvent(player, pet, "on-tick");
        return pet;
    }

    private Pet applyPassiveExperience(Player player, Pet pet) {
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig == null || petConfig.expPerMinute() <= 0) {
            return pet;
        }

        long currentMinute = System.currentTimeMillis() / 60000L;
        if (getLastPassiveExpMinute(pet) == currentMinute) {
            return pet;
        }

        Pet expUpdated = plugin.getPetProgressService().addExperience(pet, petConfig.expPerMinute());
        Pet updated = markPassiveExpMinute(expUpdated, currentMinute);
        if (updated.entity() != null && updated.entity().isValid()) {
            updated = applyEntityState(player, updated, updated.entity());
        }
        replacePet(player, updated);
        executePetEvent(player, updated, "on-passive-exp", Map.of(
                "%qcpet_passive_exp%", String.valueOf(petConfig.expPerMinute())
        ));
        executeLevelChangeEvents(player, pet, updated, pet.level());
        plugin.getPetUtil().savePetAsync(updated);
        return updated;
    }

    private void executePetEvent(Player player, Pet pet, String eventName) {
        executePetEvent(player, pet, eventName, Map.of());
    }

    private void executePetEvent(Player player, Pet pet, String eventName, Map<String, String> extraPlaceholders) {
        PetConfig petConfig = plugin.getPetConfigManger().pets.get(pet.type());
        if (petConfig == null) {
            return;
        }
        List<String> commands = isBlindBoxPet(pet)
                ? petConfig.getBlindBoxEventCommands(eventName)
                : petConfig.getEventCommands(eventName);
        for (String rawCommand : commands) {
            String command = applyEventPlaceholders(rawCommand, player, pet, extraPlaceholders);
            if (command.isBlank()) {
                continue;
            }
            if (command.regionMatches(true, 0, "[message] ", 0, 10)) {
                if (player != null) {
                    player.sendMessage(org.bxwbb.qcpet.utils.TextComponentUtil.legacy(command.substring(10).trim()));
                }
                continue;
            }
            plugin.getServer().dispatchCommand(resolveCommandSender(command, player), stripCommandPrefix(command));
        }
    }

    private Map<String, String> buildEventContext(Player player, Pet pet, Entity relatedEntity) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("%qcpet_event_random%", String.valueOf(ThreadLocalRandom.current().nextInt(1000)));
        placeholders.put("%qcpet_event_player%", player == null ? "" : player.getName());
        placeholders.put("%qcpet_event_owner%", player == null ? "" : player.getName());
        placeholders.put("%qcpet_event_pet_id%", String.valueOf(pet.id()));
        placeholders.put("%qcpet_event_pet_type%", pet.type());
        placeholders.put("%qcpet_event_pet_level%", String.valueOf(pet.level()));
        placeholders.put("%qcpet_event_pet_exp%", String.valueOf(pet.exp()));
        placeholders.put("%qcpet_event_pet_name%", getDisplayName(pet, player));
        if (pet.entity() != null) {
            placeholders.put("%qcpet_event_world%", pet.entity().getWorld().getName());
            placeholders.put("%qcpet_event_x%", String.format("%.2f", pet.entity().getLocation().getX()));
            placeholders.put("%qcpet_event_y%", String.format("%.2f", pet.entity().getLocation().getY()));
            placeholders.put("%qcpet_event_z%", String.format("%.2f", pet.entity().getLocation().getZ()));
            placeholders.put("%qcpet_event_uuid%", pet.entity().getUniqueId().toString());
        }
        if (relatedEntity != null) {
            placeholders.put("%qcpet_event_target_uuid%", relatedEntity.getUniqueId().toString());
            placeholders.put("%qcpet_event_target_type%", relatedEntity.getType().name());
            if (relatedEntity instanceof Player targetPlayer) {
                placeholders.put("%player%", targetPlayer.getName());
                placeholders.put("%target_player%", targetPlayer.getName());
                placeholders.put("%owner%", player == null ? "" : player.getName());
            }
        } else {
            placeholders.put("%player%", player == null ? "" : player.getName());
            placeholders.put("%owner%", player == null ? "" : player.getName());
        }
        return placeholders;
    }

    private String applyEventPlaceholders(String rawCommand, Player player, Pet pet, Map<String, String> extraPlaceholders) {
        String command = rawCommand == null ? "" : rawCommand.trim();
        if (command.isBlank()) {
            return "";
        }
        command = pet.request(command);
        Map<String, String> context = buildEventContext(player, pet, null);
        context.putAll(extraPlaceholders);
        for (Map.Entry<String, String> entry : context.entrySet()) {
            command = command.replace(entry.getKey(), entry.getValue());
        }
        return command;
    }

    private CommandSender resolveCommandSender(String command, Player player) {
        if (command.regionMatches(true, 0, "[player] ", 0, 9) && player != null) {
            return player;
        }
        return plugin.getServer().getConsoleSender();
    }

    private String stripCommandPrefix(String command) {
        if (command.regionMatches(true, 0, "[player] ", 0, 9)) {
            return command.substring(9).trim();
        }
        if (command.regionMatches(true, 0, "[console] ", 0, 10)) {
            return command.substring(10).trim();
        }
        return command;
    }

    private CompletableFuture<Void> persistPetsAsync(List<Pet> petsToPersist) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        for (Pet pet : petsToPersist) {
            future = future.thenCompose(ignored -> plugin.getPetUtil().savePetAsync(pet));
        }
        return future;
    }

    private static void completeBooleanFuture(CompletableFuture<Boolean> future, Throwable throwable, boolean value) {
        if (throwable != null) {
            future.completeExceptionally(throwable);
            return;
        }
        future.complete(value);
    }

    private static void completePetFuture(CompletableFuture<Pet> future, Pet pet, Throwable throwable) {
        if (throwable != null) {
            future.completeExceptionally(throwable);
            return;
        }
        future.complete(pet);
    }

    private static String resolveRenamedTemplate(Pet pet, PetConfig petConfig, String newName) {
        if (petConfig == null) {
            return newName;
        }

        String configuredTemplate = petConfig.name();
        String baseName = petConfig.baseName();
        if (configuredTemplate == null || configuredTemplate.isBlank() || baseName == null || baseName.isBlank()) {
            return newName;
        }
        if (configuredTemplate.contains(baseName)) {
            return configuredTemplate.replace(baseName, newName);
        }
        String currentTemplate = pet.name();
        if (currentTemplate != null && !currentTemplate.isBlank() && currentTemplate.contains(baseName)) {
            return currentTemplate.replace(baseName, newName);
        }
        return newName;
    }

    private static Pet copyPet(Pet pet, boolean show, Entity entity) {
        return new Pet(
                pet.id(),
                pet.name(),
                pet.type(),
                pet.level(),
                pet.exp(),
                pet.times(),
                pet.data(),
                show,
                pet.owner(),
                entity
        );
    }

    private static Pet withEntity(Pet pet, Entity entity) {
        return new Pet(
                pet.id(),
                pet.name(),
                pet.type(),
                pet.level(),
                pet.exp(),
                pet.times(),
                pet.data(),
                pet.show(),
                pet.owner(),
                entity
        );
    }

    private static final class AutoTravelState {

        private final long petId;
        private final double targetX;
        private final double targetZ;
        private long lastAttackTick;
        private long stuckTicks;
        private long lastBlockedNotifyTick;
        private Location lastCheckedLocation;
        private UUID pathWorldUid;
        private int pathTargetBlockX = Integer.MIN_VALUE;
        private int pathTargetBlockZ = Integer.MIN_VALUE;
        private int pathIndex;
        private List<PathNode> pathNodes = List.of();

        private AutoTravelState(long petId, double targetX, double targetZ) {
            this.petId = petId;
            this.targetX = targetX;
            this.targetZ = targetZ;
        }

        private long petId() {
            return petId;
        }

        private double targetX() {
            return targetX;
        }

        private double targetZ() {
            return targetZ;
        }
    }

    private record PathNode(int x, int y, int z) {
    }

    private static final class PathRecord {

        private final PathNode node;
        private final PathRecord parent;
        private final double gScore;
        private final double fScore;
        private boolean closed;

        private PathRecord(PathNode node, PathRecord parent, double gScore, double fScore) {
            this.node = node;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }

    private record BlindBoxRevealInteraction(UUID ownerUuid, long petId, UUID interactionEntityUuid) {
    }

    private boolean isTemporarilyHidden(UUID playerUuid, long petId) {
        return temporarilyHiddenPets.getOrDefault(playerUuid, List.of()).contains(petId);
    }

    private void markTemporaryHidden(UUID playerUuid, long petId) {
        List<Long> hiddenIds = temporarilyHiddenPets.computeIfAbsent(playerUuid, ignored -> new ArrayList<>());
        if (!hiddenIds.contains(petId)) {
            hiddenIds.add(petId);
        }
    }

    private void clearTemporaryHidden(UUID playerUuid, long petId) {
        List<Long> hiddenIds = temporarilyHiddenPets.get(playerUuid);
        if (hiddenIds == null) {
            return;
        }
        hiddenIds.remove(petId);
        if (hiddenIds.isEmpty()) {
            temporarilyHiddenPets.remove(playerUuid);
        }
    }
}
