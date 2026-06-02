package org.bxwbb.qcpet.pet;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Predicate;

public final class NmsPetAiController {

    private static final String AI_STRIPPED_TAG = "qcpet_ai_stripped";
    private static final double GROUND_SPEED = 1.05D;
    private static final double FLYING_SPEED_NEAR = 1.2D;
    private static final double FLYING_SPEED_MID = 1.8D;
    private static final double FLYING_SPEED_FAR = 2.5D;

    private static final Method CRAFT_MOB_GET_HANDLE;
    private static final Method MOB_REMOVE_ALL_GOALS;
    private static final Method MOB_SET_TARGET;
    private static final Method MOB_GET_NAVIGATION;
    private static final Method MOB_GET_MOVE_CONTROL;
    private static final Method MOB_STOP_IN_PLACE;
    private static final Method NAVIGATION_MOVE_TO;
    private static final Method NAVIGATION_STOP;
    private static final Method MOVE_CONTROL_SET_WANTED_POSITION;
    private static final Method MOVE_CONTROL_SET_WAIT;

    static {
        try {
            Class<?> craftMobClass = Class.forName("org.bukkit.craftbukkit.entity.CraftMob");
            Class<?> nmsMobClass = Class.forName("net.minecraft.world.entity.Mob");
            Class<?> navigationClass = Class.forName("net.minecraft.world.entity.ai.navigation.PathNavigation");
            Class<?> moveControlClass = Class.forName("net.minecraft.world.entity.ai.control.MoveControl");

            CRAFT_MOB_GET_HANDLE = craftMobClass.getMethod("getHandle");
            MOB_REMOVE_ALL_GOALS = nmsMobClass.getMethod("removeAllGoals", Predicate.class);
            MOB_SET_TARGET = nmsMobClass.getMethod("setTarget", Class.forName("net.minecraft.world.entity.LivingEntity"));
            MOB_GET_NAVIGATION = nmsMobClass.getMethod("getNavigation");
            MOB_GET_MOVE_CONTROL = nmsMobClass.getMethod("getMoveControl");
            MOB_STOP_IN_PLACE = nmsMobClass.getMethod("stopInPlace");
            NAVIGATION_MOVE_TO = navigationClass.getMethod("moveTo", double.class, double.class, double.class, double.class);
            NAVIGATION_STOP = navigationClass.getMethod("stop");
            MOVE_CONTROL_SET_WANTED_POSITION = moveControlClass.getMethod("setWantedPosition", double.class, double.class, double.class, double.class);
            MOVE_CONTROL_SET_WAIT = moveControlClass.getMethod("setWait");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize 1.21.8 NMS AI adapter", exception);
        }
    }

    private NmsPetAiController() {
    }

    public static void stripMobAi(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        Set<String> tags = mob.getScoreboardTags();
        if (tags.contains(AI_STRIPPED_TAG)) {
            return;
        }

        try {
            Object handle = CRAFT_MOB_GET_HANDLE.invoke(mob);
            Predicate<Object> removeAll = ignored -> true;

            MOB_REMOVE_ALL_GOALS.invoke(handle, removeAll);
            MOB_SET_TARGET.invoke(handle, new Object[]{null});
            mob.addScoreboardTag(AI_STRIPPED_TAG);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to strip mob AI via NMS", exception);
        }
    }

    public static void moveGroundPet(Mob mob, Location targetLocation) {
        try {
            Object handle = CRAFT_MOB_GET_HANDLE.invoke(mob);
            Object navigation = MOB_GET_NAVIGATION.invoke(handle);
            MOB_SET_TARGET.invoke(handle, new Object[]{null});
            NAVIGATION_MOVE_TO.invoke(
                    navigation,
                    targetLocation.getX(),
                    targetLocation.getY(),
                    targetLocation.getZ(),
                    GROUND_SPEED
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to move ground pet via NMS", exception);
        }
    }

    public static void moveFlyingPet(Mob mob, Location targetLocation) {
        try {
            Object handle = CRAFT_MOB_GET_HANDLE.invoke(mob);
            Object navigation = MOB_GET_NAVIGATION.invoke(handle);
            Object moveControl = MOB_GET_MOVE_CONTROL.invoke(handle);
            Location currentLocation = mob.getLocation();
            double distanceSquared = currentLocation.distanceSquared(targetLocation);
            MOB_SET_TARGET.invoke(handle, new Object[]{null});
            NAVIGATION_STOP.invoke(navigation);
            MOVE_CONTROL_SET_WANTED_POSITION.invoke(
                    moveControl,
                    targetLocation.getX(),
                    targetLocation.getY(),
                    targetLocation.getZ(),
                    resolveFlyingSpeed(distanceSquared)
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to move flying pet via NMS", exception);
        }
    }

    public static void stop(Mob mob) {
        try {
            Object handle = CRAFT_MOB_GET_HANDLE.invoke(mob);
            Object navigation = MOB_GET_NAVIGATION.invoke(handle);
            Object moveControl = MOB_GET_MOVE_CONTROL.invoke(handle);
            NAVIGATION_STOP.invoke(navigation);
            MOVE_CONTROL_SET_WAIT.invoke(moveControl);
            MOB_STOP_IN_PLACE.invoke(handle);
            MOB_SET_TARGET.invoke(handle, new Object[]{null});
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to stop pet movement via NMS", exception);
        }
    }

    private static double resolveFlyingSpeed(double distanceSquared) {
        if (distanceSquared >= 36.0D) {
            return FLYING_SPEED_FAR;
        }
        if (distanceSquared >= 9.0D) {
            return FLYING_SPEED_MID;
        }
        return FLYING_SPEED_NEAR;
    }
}
