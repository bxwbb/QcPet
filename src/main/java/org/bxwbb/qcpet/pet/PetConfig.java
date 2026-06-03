package org.bxwbb.qcpet.pet;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PetConfig(
        String name,
        String baseName,
        String type,
        double times,
        String scaleRequirement,
        int expPerMinute,
        int bathRewardExp,
        int feedRewardExp,
        boolean saveEntityData,
        Map<String, Object> metaData,
        EntityType entityType,
        String levelExpRequirement,
        Map<String, List<String>> events,
        Map<String, List<String>> blindBoxEvents
) {

    public Pet toPet(long id, Player owner) {
        return new Pet(
                id,
                name,
                type,
                0,
                0,
                times,
                metaData == null ? Map.of() : new LinkedHashMap<>(metaData),
                false,
                owner,
                null
        );
    }

    public List<String> getEventCommands(String eventName) {
        if (events == null || eventName == null) {
            return List.of();
        }
        List<String> commands = events.get(eventName);
        return commands == null ? List.of() : new ArrayList<>(commands);
    }

    public List<String> getBlindBoxEventCommands(String eventName) {
        if (blindBoxEvents == null || eventName == null) {
            return List.of();
        }
        List<String> commands = blindBoxEvents.get(eventName);
        return commands == null ? List.of() : new ArrayList<>(commands);
    }

}
