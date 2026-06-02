package org.bxwbb.qcpet.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bxwbb.qcpet.QcPet;
import org.bxwbb.qcpet.pet.Pet;
import org.bxwbb.qcpet.utils.saveUtil.MySqlSaveUtil;
import org.bxwbb.qcpet.utils.saveUtil.SaveException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PetUtil {

    private static final Gson GSON = new Gson();
    private static final TypeToken<Map<String, Object>> DATA_TYPE = new TypeToken<>() {
    };

    private final QcPet plugin;

    public PetUtil(QcPet plugin) {
        this.plugin = plugin;
    }

    public Pet getPet(long id) {
        return plugin.getMySqlSaveUtil()
                .findPet(id)
                .map(this::toPet)
                .orElse(null);
    }

    public CompletableFuture<Pet> getPetAsync(long id) {
        return supplyAsync(() -> getPet(id));
    }

    public List<Pet> getPets(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        return plugin.getMySqlSaveUtil()
                .findPetsByPlayer(player.getUniqueId())
                .stream()
                .map(record -> toPet(record, player))
                .toList();
    }

    public CompletableFuture<List<Pet>> getPetsAsync(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        return supplyAsync(() -> getPets(player));
    }

    public List<Pet> getPets(UUID playerUuid) {
        return plugin.getMySqlSaveUtil()
                .findPetsByPlayer(playerUuid)
                .stream()
                .map(this::toPet)
                .toList();
    }

    public void savePet(Pet pet) {
        if (pet == null) {
            throw new IllegalArgumentException("pet cannot be null");
        }
        plugin.getMySqlSaveUtil().savePet(new MySqlSaveUtil.PetRecord(
                pet.id(),
                pet.name(),
                pet.type(),
                pet.level(),
                pet.exp(),
                pet.times(),
                GSON.toJson(sanitizeData(pet.data())),
                pet.show()
        ));
    }

    public CompletableFuture<Void> savePetAsync(Pet pet) {
        if (pet == null) {
            throw new IllegalArgumentException("pet cannot be null");
        }
        return runAsync(() -> savePet(pet));
    }

    public long nextPetId() {
        return plugin.getMySqlSaveUtil().nextPetId();
    }

    public void bindPetToPlayer(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        bindPetToPlayer(player.getUniqueId(), petId);
    }

    public void bindPetToPlayer(UUID playerUuid, long petId) {
        plugin.getMySqlSaveUtil().bindPetToPlayer(playerUuid, petId);
    }

    public boolean deletePet(Player player, long petId) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        return deletePet(player.getUniqueId(), petId);
    }

    public boolean deletePet(UUID playerUuid, long petId) {
        return plugin.getMySqlSaveUtil().deletePet(playerUuid, petId);
    }

    private Pet toPet(MySqlSaveUtil.PetRecord record) {
        return toPet(record, null);
    }

    private Pet toPet(MySqlSaveUtil.PetRecord record, Player owner) {
        return new Pet(
                record.id(),
                record.name(),
                record.type(),
                record.level(),
                record.exp(),
                record.times(),
                parseData(record.id(), record.data()),
                record.show(),
                owner,
                null
        );
    }

    private <T> CompletableFuture<T> supplyAsync(UnsafeSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T value = supplier.get();
                plugin.getServer().getScheduler().runTask(plugin, () -> future.complete(value));
            } catch (Exception exception) {
                plugin.getServer().getScheduler().runTask(plugin, () -> future.completeExceptionally(exception));
            }
        });
        return future;
    }

    private CompletableFuture<Void> runAsync(UnsafeRunnable runnable) {
        return supplyAsync(() -> {
            runnable.run();
            return null;
        });
    }

    private static Map<String, Object> parseData(long petId, String data) {
        if (data == null || data.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JsonElement dataElement = GSON.fromJson(data, JsonElement.class);
            if (dataElement == null || dataElement.isJsonNull()) {
                return Collections.emptyMap();
            }
            if (!dataElement.isJsonObject()) {
                throw new SaveException("Pet data must be a JSON object: " + petId);
            }
            JsonObject jsonObject = dataElement.getAsJsonObject();
            return new LinkedHashMap<>(GSON.fromJson(jsonObject, DATA_TYPE));
        } catch (JsonSyntaxException exception) {
            throw new SaveException("Invalid pet data: " + petId, exception);
        }
    }

    private static Map<String, Object> sanitizeData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue(), new LinkedHashSet<>()));
        }
        return sanitized;
    }

    private static Object sanitizeValue(Object value, Set<Object> visiting) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            return normalizeNumber(number);
        }
        if (value instanceof Character character) {
            return String.valueOf(character);
        }
        if (value instanceof Map<?, ?> map) {
            if (!visiting.add(value)) {
                return String.valueOf(value);
            }
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sanitized.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue(), visiting));
            }
            visiting.remove(value);
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            if (!visiting.add(value)) {
                return String.valueOf(value);
            }
            List<Object> sanitized = new ArrayList<>(collection.size());
            for (Object element : collection) {
                sanitized.add(sanitizeValue(element, visiting));
            }
            visiting.remove(value);
            return sanitized;
        }
        return convertJsonElement(GSON.toJsonTree(value));
    }

    private static Object convertJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return normalizeNumber(primitive.getAsNumber());
            }
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> values = new ArrayList<>();
            element.getAsJsonArray().forEach(item -> values.add(convertJsonElement(item)));
            return values;
        }
        if (element.isJsonObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                values.put(entry.getKey(), convertJsonElement(entry.getValue()));
            }
            return values;
        }
        return element.toString();
    }

    private static Number normalizeNumber(Number number) {
        double doubleValue = number.doubleValue();
        if (doubleValue == Math.rint(doubleValue) && doubleValue >= Long.MIN_VALUE && doubleValue <= Long.MAX_VALUE) {
            return (long) doubleValue;
        }
        return doubleValue;
    }

    @FunctionalInterface
    private interface UnsafeSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface UnsafeRunnable {
        void run() throws Exception;
    }
}
