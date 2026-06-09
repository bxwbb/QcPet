package org.bxwbb.qcpet.pet;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class NmsPlayerPetController {

    private static volatile Adapter adapter;
    private static volatile Throwable initializationError;

    private NmsPlayerPetController() {
    }

    public static boolean isAvailable() {
        return getAdapter() != null;
    }

    public static Throwable getInitializationError() {
        getAdapter();
        return initializationError;
    }

    public static Player spawnPlayerPet(Player owner, String displayName, Location location) {
        try {
            return getRequiredAdapter().spawnPlayerPet(owner, displayName, location);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("玩家宠物 NMS 生成失败", exception);
        }
    }

    public static void teleport(Player playerPet, Location location) {
        try {
            getRequiredAdapter().teleport(playerPet, location);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("玩家宠物 NMS 传送失败", exception);
        }
    }

    public static void remove(Player playerPet) {
        try {
            getRequiredAdapter().remove(playerPet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("玩家宠物 NMS 移除失败", exception);
        }
    }

    private static Adapter getRequiredAdapter() {
        Adapter current = getAdapter();
        if (current == null) {
            throw new IllegalStateException("玩家宠物 NMS 适配器不可用", initializationError);
        }
        return current;
    }

    private static Adapter getAdapter() {
        Adapter current = adapter;
        if (current != null) {
            return current;
        }
        if (initializationError != null) {
            return null;
        }
        synchronized (NmsPlayerPetController.class) {
            if (adapter != null) {
                return adapter;
            }
            if (initializationError != null) {
                return null;
            }
            try {
                adapter = new Adapter();
                return adapter;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                initializationError = new IllegalStateException("玩家宠物 NMS 适配器初始化失败，请确认当前服务端为 1.21.11。", exception);
                return null;
            }
        }
    }

    private static String sanitizeProfileName(String displayName, String fallbackName) {
        String stripped = displayName == null ? "" : displayName.replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("[^A-Za-z0-9_]", "");
        if (stripped.isBlank()) {
            stripped = fallbackName == null ? "QcPetNPC" : fallbackName.replaceAll("[^A-Za-z0-9_]", "");
        }
        if (stripped.isBlank()) {
            stripped = "QcPetNPC";
        }
        return stripped.length() > 16 ? stripped.substring(0, 16) : stripped;
    }

    private static final class Adapter {

        private final JavaPlugin plugin;
        private final Method craftServerGetServer;
        private final Method craftWorldGetHandle;
        private final Method craftPlayerGetHandle;
        private final Constructor<?> gameProfileConstructor;
        private final Method clientInformationCreateDefault;
        private final Constructor<?> serverPlayerConstructor;
        private final Field serverPlayerGameProfileField;
        private final Method entitySetPos;
        private final Method entitySetYRot;
        private final Method entitySetXRot;
        private final Method entitySetYHeadRot;
        private final Method entitySetInvulnerable;
        private final Method entitySetNoGravity;
        private final Method entitySetInvisible;
        private final Method entitySetCustomNameVisible;
        private final Method entityGetId;
        private final Method entityGetUuid;
        private final Method entityGetType;
        private final Method entityGetBukkitEntity;
        private final Method entityRemove;
        private final Field serverPlayerConnection;
        private final Method connectionSend;
        private final Constructor<?> playerInfoEntryConstructor;
        private final Constructor<?> playerInfoUpdatePacketConstructor;
        private final Constructor<?> addEntityPacketConstructor;
        private final Constructor<?> bundlePacketConstructor;
        private final Constructor<?> playerInfoRemovePacketConstructor;
        private final Constructor<?> removeEntitiesPacketConstructor;
        private final Constructor<?> teleportEntityPacketConstructor;
        private final Constructor<?> positionMoveRotationConstructor;
        private final Field vec3ZeroField;
        private final Constructor<?> vec3Constructor;
        private final Class<?> playerInfoActionClass;
        private final Object addPlayerAction;
        private final Object updateDisplayNameAction;
        private final Object gameTypeCreative;
        private final Object removalReasonDiscarded;

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Adapter() throws ReflectiveOperationException {
            plugin = JavaPlugin.getProvidingPlugin(NmsPlayerPetController.class);

            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> clientGamePacketListenerClass = Class.forName("net.minecraft.network.protocol.game.ClientGamePacketListener");
            Class<?> playerInfoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> playerInfoEntryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> playerInfoActionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> addEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> bundlePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundBundlePacket");
            Class<?> playerInfoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Class<?> removeEntitiesPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            Class<?> teleportEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket");
            Class<?> positionMoveRotationClass = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> remoteChatSessionDataClass = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
            Class<?> removalReasonClass = Class.forName("net.minecraft.world.entity.Entity$RemovalReason");
            Class<?> serverCommonPacketListenerClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");

            this.playerInfoActionClass = playerInfoActionClass;
            craftServerGetServer = craftServerClass.getMethod("getServer");
            craftWorldGetHandle = craftWorldClass.getMethod("getHandle");
            craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");
            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            clientInformationCreateDefault = clientInformationClass.getMethod("createDefault");
            serverPlayerConstructor = serverPlayerClass.getConstructor(minecraftServerClass, serverLevelClass, gameProfileClass, clientInformationClass);
            serverPlayerGameProfileField = serverPlayerClass.getField("gameProfile");
            entitySetPos = entityClass.getMethod("setPos", double.class, double.class, double.class);
            entitySetYRot = entityClass.getMethod("setYRot", float.class);
            entitySetXRot = entityClass.getMethod("setXRot", float.class);
            entitySetYHeadRot = entityClass.getMethod("setYHeadRot", float.class);
            entitySetInvulnerable = entityClass.getMethod("setInvulnerable", boolean.class);
            entitySetNoGravity = entityClass.getMethod("setNoGravity", boolean.class);
            entitySetInvisible = entityClass.getMethod("setInvisible", boolean.class);
            entitySetCustomNameVisible = findOptionalMethod(entityClass, "setCustomNameVisible", boolean.class);
            entityGetId = entityClass.getMethod("getId");
            entityGetUuid = entityClass.getMethod("getUUID");
            entityGetType = entityClass.getMethod("getType");
            entityGetBukkitEntity = entityClass.getMethod("getBukkitEntity");
            entityRemove = entityClass.getMethod("remove", removalReasonClass);
            serverPlayerConnection = serverPlayerClass.getField("connection");
            connectionSend = serverCommonPacketListenerClass.getMethod("send", packetClass);
            playerInfoEntryConstructor = playerInfoEntryClass.getConstructor(
                    UUID.class, gameProfileClass, boolean.class, int.class, gameTypeClass, componentClass, boolean.class, int.class, remoteChatSessionDataClass
            );
            playerInfoUpdatePacketConstructor = playerInfoPacketClass.getConstructor(EnumSet.class, List.class);
            addEntityPacketConstructor = addEntityPacketClass.getConstructor(
                    int.class, UUID.class, double.class, double.class, double.class, float.class, float.class,
                    Class.forName("net.minecraft.world.entity.EntityType"), int.class, vec3Class, double.class
            );
            bundlePacketConstructor = bundlePacketClass.getConstructor(Iterable.class);
            playerInfoRemovePacketConstructor = playerInfoRemovePacketClass.getConstructor(List.class);
            removeEntitiesPacketConstructor = removeEntitiesPacketClass.getConstructor(int[].class);
            teleportEntityPacketConstructor = teleportEntityPacketClass.getConstructor(int.class, positionMoveRotationClass, Set.class, boolean.class);
            positionMoveRotationConstructor = positionMoveRotationClass.getConstructor(vec3Class, vec3Class, float.class, float.class);
            vec3ZeroField = vec3Class.getField("ZERO");
            vec3Constructor = vec3Class.getConstructor(double.class, double.class, double.class);
            addPlayerAction = Enum.valueOf((Class<Enum>) playerInfoActionClass, "ADD_PLAYER");
            updateDisplayNameAction = Enum.valueOf((Class<Enum>) playerInfoActionClass, "UPDATE_DISPLAY_NAME");
            gameTypeCreative = gameTypeClass.getMethod("byId", int.class).invoke(null, 1);
            removalReasonDiscarded = Enum.valueOf((Class<Enum>) removalReasonClass, "DISCARDED");
        }

        private Player spawnPlayerPet(Player owner, String displayName, Location location) throws ReflectiveOperationException {
            Object server = craftServerGetServer.invoke(owner.getServer());
            Object level = craftWorldGetHandle.invoke(location.getWorld());
            UUID uuid = UUID.randomUUID();
            String localName = sanitizeProfileName(displayName, owner.getName());
            Object profile = gameProfileConstructor.newInstance(uuid, localName);
            Object fakePlayer = serverPlayerConstructor.newInstance(server, level, gameProfileConstructor.newInstance(uuid, ""), clientInformationCreateDefault.invoke(null));
            serverPlayerGameProfileField.set(fakePlayer, profile);

            applyLocation(fakePlayer, location);
            entitySetInvisible.invoke(fakePlayer, false);
            entitySetInvulnerable.invoke(fakePlayer, true);
            entitySetNoGravity.invoke(fakePlayer, true);
            invokeOptional(entitySetCustomNameVisible, fakePlayer, true);

            int entityId = (int) entityGetId.invoke(fakePlayer);
            Object playerInfoPacket = createPlayerInfoPacket(fakePlayer, owner);
            Object addEntityPacket = createAddEntityPacket(fakePlayer, location);
            Object bundlePacket = bundlePacketConstructor.newInstance(List.of(playerInfoPacket, addEntityPacket));

            for (Player viewer : owner.getWorld().getPlayers()) {
                sendOnPlayerScheduler(viewer, bundlePacket);
                scheduleTabRemove(viewer, uuid);
            }
            return (Player) entityGetBukkitEntity.invoke(fakePlayer);
        }

        private void teleport(Player playerPet, Location location) throws ReflectiveOperationException {
            Object fakePlayer = craftPlayerGetHandle.invoke(playerPet);
            applyLocation(fakePlayer, location);
            int entityId = (int) entityGetId.invoke(fakePlayer);
            Object teleportPacket = createTeleportPacket(entityId, location);
            Object viewerWorld = location.getWorld();
            if (!(viewerWorld instanceof World world)) {
                return;
            }
            for (Player viewer : world.getPlayers()) {
                sendOnPlayerScheduler(viewer, teleportPacket);
            }
        }

        private void remove(Player playerPet) throws ReflectiveOperationException {
            Object fakePlayer = craftPlayerGetHandle.invoke(playerPet);
            UUID uuid = (UUID) entityGetUuid.invoke(fakePlayer);
            int entityId = (int) entityGetId.invoke(fakePlayer);
            Object playerInfoRemovePacket = playerInfoRemovePacketConstructor.newInstance(List.of(uuid));
            Object removeEntitiesPacket = removeEntitiesPacketConstructor.newInstance((Object) new int[]{entityId});
            for (Player viewer : playerPet.getWorld().getPlayers()) {
                sendOnPlayerScheduler(viewer, playerInfoRemovePacket);
                sendOnPlayerScheduler(viewer, removeEntitiesPacket);
            }
            entityRemove.invoke(fakePlayer, removalReasonDiscarded);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object createPlayerInfoPacket(Object fakePlayer, Player viewer) throws ReflectiveOperationException {
            EnumSet actions = EnumSet.noneOf((Class<Enum>) playerInfoActionClass);
            actions.add(addPlayerAction);
            actions.add(updateDisplayNameAction);
            Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
            Object listName = null;
            Object entry = playerInfoEntryConstructor.newInstance(
                    entityGetUuid.invoke(fakePlayer),
                    serverPlayerGameProfileField.get(fakePlayer),
                    false,
                    0,
                    gameTypeCreative,
                    listName,
                    true,
                    -1,
                    null
            );
            return playerInfoUpdatePacketConstructor.newInstance(actions, List.of(entry));
        }

        private Object createAddEntityPacket(Object fakePlayer, Location location) throws ReflectiveOperationException {
            return addEntityPacketConstructor.newInstance(
                    entityGetId.invoke(fakePlayer),
                    entityGetUuid.invoke(fakePlayer),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getPitch(),
                    location.getYaw(),
                    entityGetType.invoke(fakePlayer),
                    0,
                    vec3ZeroField.get(null),
                    (double) location.getYaw()
            );
        }

        private Object createTeleportPacket(int entityId, Location location) throws ReflectiveOperationException {
            Object position = vec3Constructor.newInstance(location.getX(), location.getY(), location.getZ());
            Object positionMoveRotation = positionMoveRotationConstructor.newInstance(
                    position,
                    vec3ZeroField.get(null),
                    location.getYaw(),
                    location.getPitch()
            );
            return teleportEntityPacketConstructor.newInstance(entityId, positionMoveRotation, Set.of(), false);
        }

        private void applyLocation(Object fakePlayer, Location location) throws ReflectiveOperationException {
            entitySetPos.invoke(fakePlayer, location.getX(), location.getY(), location.getZ());
            entitySetYRot.invoke(fakePlayer, location.getYaw());
            entitySetXRot.invoke(fakePlayer, location.getPitch());
            entitySetYHeadRot.invoke(fakePlayer, location.getYaw());
        }

        private void scheduleTabRemove(Player viewer, UUID uuid) throws ReflectiveOperationException {
            Object packet = playerInfoRemovePacketConstructor.newInstance(List.of(uuid));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (viewer.isOnline()) {
                        sendOnPlayerScheduler(viewer, packet);
                    }
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("玩家宠物延迟移出 tab 失败", exception);
                }
            }, 10L);
        }

        private void sendOnPlayerScheduler(Player viewer, Object packet) throws ReflectiveOperationException {
            if (viewer == null || !viewer.isOnline()) {
                return;
            }
            Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
            Object connection = serverPlayerConnection.get(viewerHandle);
            if (connection == null) {
                return;
            }
            connectionSend.invoke(connection, packet);
        }

        private static Method findOptionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            try {
                return type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        private static void invokeOptional(Method method, Object target, Object... args) throws ReflectiveOperationException {
            if (method != null) {
                method.invoke(target, args);
            }
        }
    }
}
