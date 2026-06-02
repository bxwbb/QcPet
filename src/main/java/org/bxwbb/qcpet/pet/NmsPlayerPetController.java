package org.bxwbb.qcpet.pet;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
        Adapter currentAdapter = getRequiredAdapter();
        try {
            return currentAdapter.spawnPlayerPet(owner, displayName, location);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to spawn fake player pet via NMS", exception);
        }
    }

    public static void teleport(Player playerPet, Location location) {
        Adapter currentAdapter = getRequiredAdapter();
        try {
            currentAdapter.teleport(playerPet, location);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to teleport fake player pet via NMS", exception);
        }
    }

    public static void remove(Player playerPet) {
        Adapter currentAdapter = getRequiredAdapter();
        try {
            currentAdapter.remove(playerPet);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to remove fake player pet via NMS", exception);
        }
    }

    private static Adapter getRequiredAdapter() {
        Adapter currentAdapter = getAdapter();
        if (currentAdapter == null) {
            throw new IllegalStateException("Fake player pet adapter is unavailable", initializationError);
        }
        return currentAdapter;
    }

    private static Adapter getAdapter() {
        Adapter currentAdapter = adapter;
        if (currentAdapter != null) {
            return currentAdapter;
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
            } catch (ReflectiveOperationException exception) {
                initializationError = exception;
                return null;
            } catch (RuntimeException exception) {
                initializationError = exception;
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

        private final Method craftServerGetServer;
        private final Method craftWorldGetHandle;
        private final Method craftPlayerGetHandle;
        private final Constructor<?> gameProfileConstructor;
        private final Method clientInformationCreateDefault;
        private final Constructor<?> serverPlayerConstructor;
        private final Method serverLevelAddFreshEntity;
        private final Method entitySetPos;
        private final Method entitySetYRot;
        private final Method entitySetXRot;
        private final Method entitySetInvulnerable;
        private final Method entitySetNoGravity;
        private final Method entitySetInvisible;
        private final Method entityRemove;
        private final Method entityGetBukkitEntity;
        private final Method serverPlayerSetYHeadRot;
        private final Method serverPlayerSetNoPhysics;
        private final Method serverPlayerSetCustomNameVisible;
        private final Method serverPlayerGetUuid;
        private final Field serverPlayerConnection;
        private final Method serverConnectionSend;
        private final Constructor<?> serverEntityConstructor;
        private final Method serverEntitySendPairingData;
        private final Method playerInfoCreateInitializing;
        private final Constructor<?> playerInfoRemovePacketConstructor;
        private final Method entityPositionSyncPacketOf;
        private final Object removalReasonDiscarded;
        private final Class<?> serverEntitySynchronizerClass;

        private Adapter() throws ReflectiveOperationException {
            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> serverEntityClass = Class.forName("net.minecraft.server.level.ServerEntity");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> playerInfoUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> playerInfoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Class<?> entityPositionSyncPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket");
            Class<?> removalReasonClass = Class.forName("net.minecraft.world.entity.Entity$RemovalReason");
            Class<?> serverCommonPacketListenerClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            serverEntitySynchronizerClass = Class.forName("net.minecraft.server.level.ServerEntity$Synchronizer");

            craftServerGetServer = craftServerClass.getMethod("getServer");
            craftWorldGetHandle = craftWorldClass.getMethod("getHandle");
            craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");

            gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            clientInformationCreateDefault = clientInformationClass.getMethod("createDefault");
            serverPlayerConstructor = serverPlayerClass.getConstructor(minecraftServerClass, serverLevelClass, gameProfileClass, clientInformationClass);
            serverLevelAddFreshEntity = serverLevelClass.getMethod("addFreshEntity", entityClass);

            entitySetPos = entityClass.getMethod("setPos", double.class, double.class, double.class);
            entitySetYRot = entityClass.getMethod("setYRot", float.class);
            entitySetXRot = entityClass.getMethod("setXRot", float.class);
            entitySetInvulnerable = entityClass.getMethod("setInvulnerable", boolean.class);
            entitySetNoGravity = entityClass.getMethod("setNoGravity", boolean.class);
            entitySetInvisible = entityClass.getMethod("setInvisible", boolean.class);
            entityRemove = entityClass.getMethod("remove", removalReasonClass);
            entityGetBukkitEntity = entityClass.getMethod("getBukkitEntity");

            serverPlayerSetYHeadRot = serverPlayerClass.getMethod("setYHeadRot", float.class);
            serverPlayerSetNoPhysics = serverPlayerClass.getMethod("setNoPhysics", boolean.class);
            serverPlayerSetCustomNameVisible = serverPlayerClass.getMethod("setCustomNameVisible", boolean.class);
            serverPlayerGetUuid = serverPlayerClass.getMethod("getUUID");
            serverPlayerConnection = serverPlayerClass.getField("connection");

            serverConnectionSend = serverCommonPacketListenerClass.getMethod("send", packetClass);

            serverEntityConstructor = serverEntityClass.getConstructor(
                    serverLevelClass,
                    entityClass,
                    int.class,
                    boolean.class,
                    serverEntitySynchronizerClass
            );
            serverEntitySendPairingData = serverEntityClass.getMethod("sendPairingData", serverPlayerClass, Consumer.class);

            playerInfoCreateInitializing = playerInfoUpdatePacketClass.getMethod("createPlayerInitializing", java.util.Collection.class);
            playerInfoRemovePacketConstructor = playerInfoRemovePacketClass.getConstructor(List.class);
            entityPositionSyncPacketOf = entityPositionSyncPacketClass.getMethod("of", entityClass);

            @SuppressWarnings({"rawtypes", "unchecked"})
            Enum<?> discarded = Enum.valueOf((Class<Enum>) removalReasonClass, "DISCARDED");
            removalReasonDiscarded = discarded;
        }

        private Player spawnPlayerPet(Player owner, String displayName, Location location) throws ReflectiveOperationException {
            Object server = craftServerGetServer.invoke(owner.getServer());
            Object level = craftWorldGetHandle.invoke(location.getWorld());
            Object profile = gameProfileConstructor.newInstance(UUID.randomUUID(), sanitizeProfileName(displayName, owner.getName()));
            Object clientInformation = clientInformationCreateDefault.invoke(null);
            Object fakePlayer = serverPlayerConstructor.newInstance(server, level, profile, clientInformation);

            applyLocation(fakePlayer, location);
            entitySetInvisible.invoke(fakePlayer, false);
            entitySetInvulnerable.invoke(fakePlayer, true);
            entitySetNoGravity.invoke(fakePlayer, true);
            serverPlayerSetNoPhysics.invoke(fakePlayer, true);
            serverPlayerSetCustomNameVisible.invoke(fakePlayer, true);

            serverLevelAddFreshEntity.invoke(level, fakePlayer);

            Object tracker = serverEntityConstructor.newInstance(
                    level,
                    fakePlayer,
                    0,
                    false,
                    createSynchronizer(owner.getWorld())
            );

            Object initializingPacket = playerInfoCreateInitializing.invoke(null, List.of(fakePlayer));
            broadcast(owner.getWorld(), initializingPacket);

            for (Player viewer : owner.getWorld().getPlayers()) {
                Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
                serverEntitySendPairingData.invoke(tracker, viewerHandle, (Consumer<Object>) packet -> {
                    try {
                        sendPacket(viewer, packet);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("Failed to send fake player spawn packet", exception);
                    }
                });
            }

            Object fakeUuid = serverPlayerGetUuid.invoke(fakePlayer);
            Object removePacket = playerInfoRemovePacketConstructor.newInstance(List.of(fakeUuid));
            broadcast(owner.getWorld(), removePacket);

            return (Player) entityGetBukkitEntity.invoke(fakePlayer);
        }

        private void teleport(Player playerPet, Location location) throws ReflectiveOperationException {
            Object fakePlayer = craftPlayerGetHandle.invoke(playerPet);
            applyLocation(fakePlayer, location);
            Object packet = entityPositionSyncPacketOf.invoke(null, fakePlayer);
            broadcast(location.getWorld(), packet);
        }

        private void remove(Player playerPet) throws ReflectiveOperationException {
            Object fakePlayer = craftPlayerGetHandle.invoke(playerPet);
            Object uuid = serverPlayerGetUuid.invoke(fakePlayer);
            Object removePacket = playerInfoRemovePacketConstructor.newInstance(List.of(uuid));
            broadcast(playerPet.getWorld(), removePacket);
            entityRemove.invoke(fakePlayer, removalReasonDiscarded);
        }

        private void applyLocation(Object fakePlayer, Location location) throws ReflectiveOperationException {
            entitySetPos.invoke(fakePlayer, location.getX(), location.getY(), location.getZ());
            entitySetYRot.invoke(fakePlayer, location.getYaw());
            entitySetXRot.invoke(fakePlayer, location.getPitch());
            serverPlayerSetYHeadRot.invoke(fakePlayer, location.getYaw());
        }

        private void broadcast(World world, Object packet) throws ReflectiveOperationException {
            if (world == null || packet == null) {
                return;
            }
            for (Player viewer : world.getPlayers()) {
                sendPacket(viewer, packet);
            }
        }

        private void sendPacket(Player viewer, Object packet) throws ReflectiveOperationException {
            Object viewerHandle = craftPlayerGetHandle.invoke(viewer);
            Object connection = serverPlayerConnection.get(viewerHandle);
            if (connection == null) {
                return;
            }
            serverConnectionSend.invoke(connection, packet);
        }

        private Object createSynchronizer(World world) {
            InvocationHandler handler = (proxy, method, args) -> {
                if (args == null || args.length == 0 || args[0] == null) {
                    return null;
                }
                Object packet = args[0];
                if (args.length >= 2 && args[1] instanceof Predicate<?> rawPredicate) {
                    @SuppressWarnings("unchecked")
                    Predicate<Player> predicate = (Predicate<Player>) rawPredicate;
                    for (Player viewer : world.getPlayers()) {
                        if (predicate.test(viewer)) {
                            sendPacket(viewer, packet);
                        }
                    }
                    return null;
                }
                broadcast(world, packet);
                return null;
            };
            return Proxy.newProxyInstance(
                    serverEntitySynchronizerClass.getClassLoader(),
                    new Class[]{serverEntitySynchronizerClass},
                    handler
            );
        }
    }
}
