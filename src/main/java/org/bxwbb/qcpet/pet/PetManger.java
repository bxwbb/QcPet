package org.bxwbb.qcpet.pet;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.Attributable;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
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
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.math.MathExpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final double TELEPORT_DISTANCE_SQUARED = 144.0D;
    private static final double FOLLOW_STOP_DISTANCE_SQUARED = 4.0D;
    private static final double FOLLOW_SLOT_REACHED_DISTANCE_SQUARED = 0.36D;
    private static final double FOLLOW_SLOT_RADIUS = 1.75D;
    private static final double FLYING_FOLLOW_HEIGHT = 3.0D;
    private static final double GROUND_SLOT_TELEPORT_DISTANCE_SQUARED = 16.0D;
    private static final long BLIND_BOX_REVEAL_DURATION_TICKS = 40L;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static boolean playerPetFallbackLogged;

    private final QcPet plugin;
    public final Map<UUID, List<Pet>> pets = new HashMap<>();
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
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (petConfig == null) {
            throw new IllegalArgumentException("petConfig cannot be null");
        }
        Pet pet = petConfig.toPet(plugin.getPetUtil().nextPetId(), player);
        addPet(player, pet);
        executePetEvent(player, pet, "on-give");
        return pet;
    }

    public CompletableFuture<Pet> givePetAsync(Player player, PetConfig petConfig) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (petConfig == null) {
            throw new IllegalArgumentException("petConfig cannot be null");
        }

        CompletableFuture<Pet> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Pet pet = petConfig.toPet(plugin.getPetUtil().nextPetId(), player);
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
        followTask.cancel();
    }

    public String getDisplayName(Pet pet, Player viewer) {
        if (isBlindBoxPet(pet)) {
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

    public boolean needsBath(Pet pet) {
        if (isBlindBoxPet(pet)) {
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
        if (isBlindBoxPet(pet)) {
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
                Entity entity = pet.entity();
                if (entity == null || !entity.isValid() || entity.isDead()) {
                    try {
                        showPetInternal(player, pet, false, false);
                    } catch (Exception exception) {
                        handlePetSpawnFailure(player, pet, exception, false);
                    }
                    continue;
                }
                Pet updatedPet = applyEntityState(player, pet, entity);
                if (updatedPet != pet) {
                    replacePet(player, updatedPet);
                    pet = updatedPet;
                    entity = updatedPet.entity();
                }
                followOwner(player, pet, entity);
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
            textDisplay.text(LEGACY_SERIALIZER.deserialize("&l???"));
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setSeeThrough(true);
            textDisplay.setShadowed(false);
            textDisplay.setPersistent(false);
            textDisplay.setDefaultBackground(false);
            textDisplay.setInterpolationDelay(0);
            textDisplay.setInterpolationDuration(1);
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
        if (entity instanceof Wither wither) {
            wither.setInvulnerableTicks(0);
            wither.setGlowing(false);
        }
        applyPetDataToEntity(updatedPet, entity);
        return captureEntityStateIfNecessary(updatedPet, entity);
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
        if (player == null || pet == null || !player.isOnline()) {
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
        if (player == null || pet == null || !player.isOnline()) {
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
        boolean flyingPet = isFlyingPet(pet, entity);
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

        Location targetLocation = getFollowLocation(player, pet, flyingPet);
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
                NmsPetAiController.moveFlyingPet(mob, targetLocation);
                return;
            }
            if (targetHorizontalDistanceSquared >= GROUND_SLOT_TELEPORT_DISTANCE_SQUARED) {
                entity.teleport(targetLocation);
                return;
            }
            NmsPetAiController.moveGroundPet(mob, targetLocation);
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

    private Location getFollowLocation(Player player, Pet pet, boolean flyingPet) {
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

        double angle = (Math.PI * 2D * slotIndex) / visibleCount;
        double xOffset = Math.cos(angle) * FOLLOW_SLOT_RADIUS;
        double zOffset = Math.sin(angle) * FOLLOW_SLOT_RADIUS;
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

    private boolean isFlyingPet(Pet pet, Entity entity) {
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
        if (isBlindBoxPet(pet)) {
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
        if (NmsPlayerPetController.isAvailable()) {
            return NmsPlayerPetController.spawnPlayerPet(player, getDisplayName(pet, player), player.getLocation());
        }

        if (!playerPetFallbackLogged) {
            playerPetFallbackLogged = true;
            Throwable error = NmsPlayerPetController.getInitializationError();
            if (error == null) {
                plugin.getLogger().warning("玩家宠物 NMS 适配器不可用，已回退到盔甲架展示实体。");
            } else {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "玩家宠物 NMS 适配器初始化失败，已回退到盔甲架展示实体。", error);
            }
        }

        Entity fallbackEntity = player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
        fallbackEntity.customName(LEGACY_SERIALIZER.deserialize(getDisplayName(pet, player)));
        return fallbackEntity;
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
            playBlindBoxRevealEffect(player, oldPet, newPet);
        }
        if (newPet.level() > previousLevel) {
            executePetEvent(player, newPet, "on-level-up", Map.of(
                    "%qcpet_old_level%", String.valueOf(previousLevel),
                    "%qcpet_new_level%", String.valueOf(newPet.level())
            ));
        }
    }

    private void playBlindBoxRevealEffect(Player player, Pet oldPet, Pet newPet) {
        if (player == null || !player.isOnline() || !newPet.show()) {
            return;
        }
        Entity revealEntity = oldPet.entity();
        if (revealEntity == null || !revealEntity.isValid() || revealEntity.isDead()) {
            showPetInternal(player, newPet, false, false);
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
                removeEntity(oldPet);
                return;
            }
            Pet currentPet = findPet(player, newPet.id()).orElse(null);
            if (currentPet == null || !currentPet.show()) {
                removeEntity(oldPet);
                return;
            }
            Pet shownPet = showPetInternal(player, currentPet, false, false);
            if (shownPet != null && shownPet.entity() != null) {
                playLoveEffect(shownPet.entity(), 12L);
            }
        }, BLIND_BOX_REVEAL_DURATION_TICKS + 2L);
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
}
