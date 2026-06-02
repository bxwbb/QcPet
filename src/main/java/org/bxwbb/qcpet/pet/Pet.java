package org.bxwbb.qcpet.pet;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.Map;

public record Pet(long id, String name, String type, int level, int exp, double times, Map<String, Object> data, boolean show, Player owner, Entity entity) {

    public String request(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        String ownerName = owner == null ? "" : owner.getName();
        str = str.replace("%qcpet_key%", type)
                .replace("%qcpet_type%", type)
                .replace("%qcpet_id%", String.valueOf(id))
                .replace("%qcpet_name%", name)
                .replace("%qcpet_level%", String.valueOf(level))
                .replace("%qcpet_exp%", String.valueOf(exp))
                .replace("%qcpet_times%", String.valueOf(times))
                .replace("%qcpet_owner_name%", ownerName)
                .replace("%player%", ownerName)
                .replace("%player_name%", ownerName);

        str = replaceMetadataKey(str);
        str = replaceMetadataValue(str);

        return str;
    }

    private String replaceMetadataKey(String str) {
        if (data == null || data.isEmpty()) return str;

        final String regex = "%qcpet_metadata_key_(.+?)%";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(str);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.get(key);

            if (isBasicTypeOrString(value)) {
                matcher.appendReplacement(sb, String.valueOf(value));
            } else {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replaceMetadataValue(String str) {
        if (data == null || data.isEmpty()) return str;

        final String regex = "%qcpet_metadata_value_(.+?)%";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(str);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(1);
            String findKey = null;

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object v = entry.getValue();
                if (isBasicTypeOrString(v) && String.valueOf(v).equals(target)) {
                    findKey = entry.getKey();
                    break;
                }
            }
            matcher.appendReplacement(sb, findKey == null ? "" : findKey);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isBasicTypeOrString(Object o) {
        if (o == null) return false;
        return o instanceof String ||
                o instanceof Integer ||
                o instanceof Long ||
                o instanceof Double ||
                o instanceof Float ||
                o instanceof Boolean ||
                o instanceof Byte ||
                o instanceof Short ||
                o instanceof Character;
    }

}
