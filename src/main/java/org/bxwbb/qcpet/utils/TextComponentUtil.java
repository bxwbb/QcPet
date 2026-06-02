package org.bxwbb.qcpet.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class TextComponentUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private TextComponentUtil() {
    }

    public static Component legacy(String value) {
        return LEGACY_SERIALIZER.deserialize(value == null ? "" : value)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component plain(String value, NamedTextColor color) {
        return Component.text(value == null ? "" : value, color)
                .decoration(TextDecoration.ITALIC, false);
    }
}
